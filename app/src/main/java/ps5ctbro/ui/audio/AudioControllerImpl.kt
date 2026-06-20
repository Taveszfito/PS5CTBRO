package com.DueBoysenberry1226.ps5ctbro.audio

import com.DueBoysenberry1226.ps5ctbro.audio.AudioUiState
import com.DueBoysenberry1226.ps5ctbro.audio.TouchpadPoint
import kotlinx.coroutines.isActive
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioFocusRequest
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.DueBoysenberry1226.ps5ctbro.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

class AudioControllerImpl private constructor(
    context: Context
) : AudioController {

    companion object {
        private const val TAG = "AudioControllerImpl"

        @Volatile
        private var INSTANCE: AudioControllerImpl? = null

        fun getInstance(context: Context): AudioControllerImpl {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioControllerImpl(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val ACTION_USB_PERMISSION =
            "com.DueBoysenberry1226.ps5ctbro.USB_PERMISSION"

        private const val SONY_VENDOR_ID = 0x054c
        private const val DUALSENSE_PRODUCT_ID = 0x0ce6

        private const val DS_OUTPUT_REPORT_USB = 0x02
        private const val DS_OUTPUT_REPORT_USB_SIZE = 63

        private const val USB_SUBCLASS_AUDIOSTREAMING = 2

        private const val SAMPLE_RATE = 48_000
        private const val INPUT_CHANNELS = 2
        private const val OUTPUT_CHANNELS = 4
        private const val BYTES_PER_SAMPLE = 2

        private const val OUTPUT_FRAME_BYTES = OUTPUT_CHANNELS * BYTES_PER_SAMPLE

        private const val MAX_CONTROLLER_VOLUME = 0xFF
        private const val PREFERENCES_NAME = "settings"
        private const val KEY_GAME_MODE_PRESETS = "game_mode_presets"
    }

    private data class ParsedControllerDetails(
        val serialNumber: String,
        val btAddress: String,
        val firmwareVersion: String,
        val firmwareType: String,
        val softwareSeries: String,
        val hardwareInfo: String,
        val updateVersion: String,
        val buildDate: String,
        val buildTime: String,
        val deviceInfo: String,
        val controllerColor: String
    )

    private data class HidOutTarget(
        val usbInterface: UsbInterface,
        val endpoint: UsbEndpoint
    )

    private fun ByteArray.readLe16(offset: Int): Int {
        if (offset + 1 >= size) return 0
        return (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.readLe32(offset: Int): Long {
        if (offset + 3 >= size) return 0L
        return (
                (this[offset].toLong() and 0xFF) or
                        ((this[offset + 1].toLong() and 0xFF) shl 8) or
                        ((this[offset + 2].toLong() and 0xFF) shl 16) or
                        ((this[offset + 3].toLong() and 0xFF) shl 24)
                ) and 0xFFFFFFFFL
    }

    private fun ByteArray.readAscii(offset: Int, length: Int): String {
        if (offset >= size || length <= 0) return "Unknown"
        val end = minOf(size, offset + length)
        val raw = copyOfRange(offset, end)
        val text = String(raw, Charsets.US_ASCII)
            .trim { it == '\u0000' || it.isWhitespace() }
        return text.ifBlank { "Unknown" }
    }

    private fun formatFirmwareVersion(raw: Long): String {
        val a = ((raw shr 24) and 0xFF).toInt()
        val b = ((raw shr 16) and 0xFF).toInt()
        val c = (raw and 0xFFFF).toInt()
        return "%d.%d.%04d".format(a, b, c)
    }

    private fun formatBtAddressLittleEndian(report: ByteArray): String {
        if (report.size < 7) return "Not available"

        val mac = listOf(
            report[6].toInt() and 0xFF,
            report[5].toInt() and 0xFF,
            report[4].toInt() and 0xFF,
            report[3].toInt() and 0xFF,
            report[2].toInt() and 0xFF,
            report[1].toInt() and 0xFF
        )

        if (mac.all { it == 0x00 } || mac.all { it == 0xFF }) {
            return "Not available"
        }

        return mac.joinToString(":") { "%02X".format(it) }
    }

    private fun parseControllerDetails(
        pairingReport: ByteArray?,
        firmwareReport: ByteArray?,
        device: UsbDevice
    ): ParsedControllerDetails {
        val serial = device.serialNumber?.takeIf { it.isNotBlank() }
            ?: "Not available over Android USB"

        val btAddress = if (
            pairingReport != null &&
            pairingReport.size >= 20 &&
            (pairingReport[0].toInt() and 0xFF) == 0x09
        ) {
            formatBtAddressLittleEndian(pairingReport)
        } else {
            "Not available"
        }

        if (
            firmwareReport == null ||
            firmwareReport.size < 64 ||
            (firmwareReport[0].toInt() and 0xFF) != 0x20
        ) {
            return ParsedControllerDetails(
                serialNumber = serial,
                btAddress = btAddress,
                firmwareVersion = "Not available",
                firmwareType = "Not available",
                softwareSeries = "Not available",
                hardwareInfo = "Not available",
                updateVersion = "Not available",
                buildDate = "Not available",
                buildTime = "Not available",
                deviceInfo = "Not available",
                controllerColor = "Not available over current USB reports"
            )
        }

        val firmwareType = firmwareReport.readLe16(20)
        val softwareSeries = firmwareReport.readLe16(22)
        val hardwareInfo = firmwareReport.readLe32(24)
        val firmwareVersionRaw = firmwareReport.readLe32(28)
        val updateVersion = firmwareReport.readLe16(44)

        return ParsedControllerDetails(
            serialNumber = serial,
            btAddress = btAddress,
            firmwareVersion = formatFirmwareVersion(firmwareVersionRaw),
            firmwareType = firmwareType.toString(),
            softwareSeries = softwareSeries.toString(),
            hardwareInfo = "0x%08X".format(hardwareInfo),
            updateVersion = "0x%04X".format(updateVersion),
            buildDate = firmwareReport.readAscii(1, 11),
            buildTime = firmwareReport.readAscii(12, 8),
            deviceInfo = firmwareReport.readAscii(32, 12),
            controllerColor = "Not available over current USB reports"
        )
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val appOpsManager = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val clipboardManager =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(
        AudioUiState(
            gameModePresets = loadGameModePresets()
        )
    )
    override val uiState: StateFlow<AudioUiState> = _uiState
    val audioLevelFlow = MutableStateFlow(0f)

    override val sessionToken: MediaSession.Token?
        get() = mediaSession?.sessionToken

    private var dualSense: DualSenseUsbController? = null
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var activeRoute: AudioRouteSession? = null
    private var captureJob: Job? = null
    private var speakerKeepAliveJob: Job? = null
    private var phoneMuteDelayJob: Job? = null
    private var phoneMuteKeepAliveJob: Job? = null
    private var gamePresetAutoDetectJob: Job? = null
    private var receiverRegistered = false

    private var savedMusicVolumeBeforeMute: Int? = null
    private var phoneMutedByApp = false
    private var gamePresetToast: Toast? = null
    private var lastForegroundPresetPackage: String? = null
    private var usageAccessPromptShown = false

    private var mediaSession: MediaSession? = null
    private var volumeProvider: VolumeProvider? = null
    private var dominanceJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var inputJob: Job? = null

    private var routingUsbConnection: UsbDeviceConnection? = null
    private var routingHidOutEndpoint: UsbEndpoint? = null
    private var routingHidInterface: UsbInterface? = null
    private val routeHidMutex = Mutex()
    private var gameBassState = 0f
    private var gameTransientFast = 0f
    private var gameTransientSlow = 0f

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return

            val device: UsbDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            if (granted && device != null) {
                val controller = connectToDualSense(device)
                if (controller != null) {
                    setControllerConnected(true)
                    setLog(appContext.getString(R.string.log_usb_granted_open))
                } else {
                    setLog(appContext.getString(R.string.log_usb_open_failed))
                }
            } else {
                setLog(appContext.getString(R.string.log_usb_permission_denied))
            }
        }
    }

    init {
        registerUsbReceiver()
        tryAutoConnect()
    }

    override fun createScreenCaptureIntent(): Intent {
        val pm = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return pm.createScreenCaptureIntent()
    }

    override suspend fun applySpeakerRoute() {
        val text = withContext(Dispatchers.IO) {
            routeHidMutex.withLock {
                runAudioRoutingHid()
            }
        }
        setLog(text)
    }

    override suspend fun startSystemAudioStreaming(context: Context, resultCode: Int, data: Intent) {
        val text = withContext(Dispatchers.IO) {
            startStreamingInternal(context, resultCode, data)
        }
        setLog(text)
    }

    override fun stopSystemAudioStreaming() {
        stopStreamingInternal()
        setLog(appContext.getString(R.string.log_streaming_stopped))
    }

    override fun onCapturePermissionDenied() {
        stopPhoneMuteForStreaming()
        setLog(appContext.getString(R.string.log_capture_denied))
    }

    override fun onRecordAudioPermissionDenied() {
        stopPhoneMuteForStreaming()
        setLog(appContext.getString(R.string.log_mic_permission_required))
    }

    override fun onUnsupportedAndroidVersion() {
        stopPhoneMuteForStreaming()
        setLog(appContext.getString(R.string.log_android_10_required))
    }

    override fun setVolumeStep(step: Int) {
        val clamped = step.coerceIn(0, 10)
        val newGain = clamped / 10f

        _uiState.update { current ->
            current.copy(
                volumeStep = clamped,
                audioGain = newGain,
                logText = appContext.getString(R.string.label_volume_level, clamped)
            )
        }

        volumeProvider?.currentVolume = clamped

        mediaSession?.let { session ->
            if (session.isActive) {
                val state = PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build()
                session.setPlaybackState(state)
            }
        }

    }

    override fun setAudioGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 1.0f)
        val step = (clamped * 10).toInt()

        _uiState.update { current ->
            current.copy(
                audioGain = clamped,
                volumeStep = step,
                logText = appContext.getString(R.string.label_volume_level, step)
            )
        }

        volumeProvider?.currentVolume = step
    }

    override fun setChannelEnabled(channel: Int, enabled: Boolean) {
        if (_uiState.value.gameMode && (channel == 1 || channel == 2)) {
            return
        }

        if (channel == 1 || (channel == 2 && enabled)) {
            resetJackSwitchPath()
            SystemClock.sleep(120)
        }

        _uiState.update { current ->
            when (channel) {
                1 -> current.copy(
                    routeCh1 = enabled,
                    routeCh2 = if (enabled) false else true
                )

                2 -> current.copy(
                    routeCh1 = if (enabled) false else current.routeCh1,
                    routeCh2 = enabled
                )

                3 -> current.copy(routeCh3 = enabled)

                4 -> current.copy(routeCh4 = enabled)

                else -> current
            }
        }

        scope.launch(Dispatchers.IO) {
            routeHidMutex.withLock {
                runAudioRoutingHid()
            }
        }
    }

    override fun setGameMode(enabled: Boolean) {
        resetGameModeShaper()
        _uiState.update { current ->
            if (enabled) {
                current.copy(
                    gameMode = true,
                    routeCh1 = false,
                    routeCh2 = false,
                    routeCh3 = true,
                    routeCh4 = true,
                    mutePhoneWhileStreaming = false,
                    hardwareVolumeButtonsControlController = false,
                    logText = appContext.getString(R.string.log_game_mode_haptic_extract)
                )
            } else {
                current.copy(
                    gameMode = false,
                    routeCh1 = false,
                    routeCh2 = true,
                    routeCh3 = false,
                    routeCh4 = false,
                    logText = appContext.getString(R.string.log_game_mode_off)
                )
            }
        }

        if (_uiState.value.isStreaming) {
            stopPhoneMuteForStreaming()
            NativeAudioBridge.nativeIsoSetQueueLimit(if (enabled) 3 else 20)
        }

        updateMediaSession()
        updateGamePresetAutoDetect()

        scope.launch(Dispatchers.IO) {
            routeHidMutex.withLock {
                runAudioRoutingHid()
            }
        }
    }

    override fun updateGameModeTuning(tuning: GameModeTuning) {
        resetGameModeShaper()
        _uiState.update { current ->
            current.copy(
                gameModeTuning = tuning,
                activeGamePresetId = findMatchingGameModePresetId(tuning)
            )
        }
    }

    override fun resetGameModeTuning() {
        updateGameModeTuning(GameModeTuning.DEFAULT)
    }

    override fun setGameModeAdaptiveStrength(enabled: Boolean) {
        val tuning = _uiState.value.gameModeTuning.copy(adaptiveStrengthEnabled = enabled)
        updateGameModeTuning(tuning)
    }

    override fun setGameModePreciseReaction(enabled: Boolean) {
        val tuning = _uiState.value.gameModeTuning.copy(preciseReactionEnabled = enabled)
        updateGameModeTuning(tuning)
    }

    override fun saveGameModePreset(
        presetId: String?,
        appPackageName: String,
        appLabel: String,
        tuning: GameModeTuning
    ) {
        val cleanPackage = appPackageName.trim()
        val cleanLabel = appLabel.trim()

        if (cleanPackage.isBlank() || cleanLabel.isBlank()) {
            setLog(appContext.getString(R.string.log_game_preset_needs_app_target))
            return
        }

        val existingId = presetId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val preset = GameModePreset(
            id = existingId,
            appPackageName = cleanPackage,
            appLabel = cleanLabel,
            tuning = tuning
        )

        _uiState.update { current ->
            val updated = current.gameModePresets
                .filterNot { it.id == existingId } + preset
            current.copy(
                gameModeTuning = tuning,
                gameModePresets = updated.sortedBy { it.appLabel.lowercase() },
                activeGamePresetId = existingId
            )
        }

        saveGameModePresets(_uiState.value.gameModePresets)
        updateGamePresetAutoDetect()
        setLog(appContext.getString(R.string.log_game_preset_saved, cleanLabel))
    }

    override fun deleteGameModePreset(id: String) {
        val current = _uiState.value
        val preset = current.gameModePresets.firstOrNull { it.id == id } ?: return

        _uiState.update {
            it.copy(
                gameModePresets = it.gameModePresets.filterNot { preset -> preset.id == id },
                activeGamePresetId = if (it.activeGamePresetId == id) null else it.activeGamePresetId
            )
        }
        saveGameModePresets(_uiState.value.gameModePresets)
        updateGamePresetAutoDetect()
        setLog(appContext.getString(R.string.log_game_preset_deleted, preset.appLabel))
    }

    override fun applyGameModePreset(id: String) {
        val preset = _uiState.value.gameModePresets.firstOrNull { it.id == id } ?: return
        updateGameModeTuning(preset.tuning)
        _uiState.update {
            it.copy(activeGamePresetId = id)
        }
        setLog(appContext.getString(R.string.log_game_preset_applied, preset.appLabel))
    }

    override fun importGameModePresetFromClipboard() {
        val text = clipboardManager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(appContext)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (text.isBlank()) {
            setLog(appContext.getString(R.string.log_clipboard_empty))
            return
        }

        val preset = parseGameModePreset(text)
        if (preset == null) {
            setLog(appContext.getString(R.string.log_invalid_game_preset_format))
            return
        }

        saveGameModePreset(
            presetId = null,
            appPackageName = preset.appPackageName,
            appLabel = preset.appLabel,
            tuning = preset.tuning
        )
    }

    override fun copyGameModePresetToClipboard(id: String) {
        val preset = _uiState.value.gameModePresets.firstOrNull { it.id == id } ?: return
        val export = exportGameModePreset(preset)
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText("game_preset", export)
        )
        setLog(appContext.getString(R.string.log_game_preset_copied, preset.appLabel))
    }

    override fun setMutePhoneWhileStreaming(enabled: Boolean) {
        if (_uiState.value.gameMode && enabled) {
            _uiState.update { it.copy(mutePhoneWhileStreaming = false) }
            stopPhoneMuteForStreaming()
            return
        }

        _uiState.update { it.copy(mutePhoneWhileStreaming = enabled) }

        if (enabled) {
            if (_uiState.value.isStreaming) {
                schedulePhoneMuteForStreaming()
            }
        } else {
            stopPhoneMuteForStreaming()
        }
    }

    override fun setHardwareVolumeButtonsControlController(enabled: Boolean) {
        if (_uiState.value.gameMode && enabled) {
            _uiState.update {
                it.copy(hardwareVolumeButtonsControlController = false)
            }
            updateMediaSession()
            return
        }

        _uiState.update {
            it.copy(hardwareVolumeButtonsControlController = enabled)
        }
        updateMediaSession()
    }

    override fun handleHardwareVolumeButton(direction: Int): Boolean {
        val state = _uiState.value

        if (state.gameMode) return false
        if (!state.hardwareVolumeButtonsControlController) return false

        when {
            direction > 0 -> setVolumeStep(state.volumeStep + 1)
            direction < 0 -> setVolumeStep(state.volumeStep - 1)
            else -> return false
        }

        return true
    }

    override fun onScreenVisible() {
        scope.launch(Dispatchers.IO) {
            routeHidMutex.withLock {
                runAudioRoutingHid()
            }
        }
    }

    override fun onScreenHidden() {
        if (!_uiState.value.isStreaming) {
            closeController()
        }
    }

    override fun release() {
        stopStreamingInternal()
        stopPhoneMuteForStreaming()
        stopGamePresetAutoDetect()
        closeController()
        unregisterUsbReceiver()
    }

    private suspend fun startStreamingInternal(context: Context, resultCode: Int, data: Intent): String {
        stopStreamingInternal(stopNative = true)

        val device = findDualSenseDevice() ?: return appContext.getString(R.string.log_no_usb_dualsense)
        val gameMode = _uiState.value.gameMode

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return appContext.getString(R.string.log_usb_permission_start)
        }

        val jackMode = _uiState.value.routeCh1

        val hidLogs = routeHidMutex.withLock {
            buildString {
                appendLine(if (jackMode) "JACK HID: ${runAudioRoutingHid()}" else "SPK HID: ${runAudioRoutingHid()}")
            }
        }

        releaseDualSenseHidHandle()

        SystemClock.sleep(120)

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return appContext.getString(R.string.log_usb_permission_audio_required)
        }

        val route = openAudioRoute(device) ?: return buildString {
            appendLine(appContext.getString(R.string.log_audio_route_open_failed))
            appendLine(if (jackMode) "=== JACK HID ===" else "=== SPK HID ===")
            append(hidLogs)
        }

        val fd = try {
            route.connection.fileDescriptor
        } catch (_: SecurityException) {
            closeAudioRoute(route)
            return appContext.getString(R.string.log_usb_fd_permission_missing)
        }

        val startRc = try {
            NativeAudioBridge.nativeIsoSetLowLatencyMode(gameMode)
            NativeAudioBridge.nativeIsoSetQueueLimit(if (gameMode) 4 else 20)
            NativeAudioBridge.nativeIsoStreamStart(
                fd,
                route.target.interfaceNumber,
                route.target.altSetting,
                route.target.endpointAddress
            )
        } catch (_: SecurityException) {
            closeAudioRoute(route)
            return appContext.getString(R.string.log_usb_security_exception)
        }

        if (startRc != 0) {
            closeAudioRoute(route)
            return appContext.getString(R.string.log_native_stream_start_failed, startRc)
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            projectionManager.getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            Log.e(TAG, "MediaProjection error", t)
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(route)
            return appContext.getString(R.string.log_media_projection_failed, t.message ?: "")
        } ?: run {
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(route)
            return appContext.getString(R.string.log_media_projection_create_failed)
        }

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection STOPPED!")
                scope.launch {
                    stopStreamingInternal()
                    setLog(appContext.getString(R.string.log_stream_interrupted))
                }
            }
        }, null)

        val record = try {
            createPlaybackCaptureRecord(projection)
        } catch (t: Throwable) {
            Log.e(TAG, "AudioRecord creation error", t)
            projection.stop()
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(route)
            return appContext.getString(R.string.log_audio_record_failed, t.message ?: "")
        } ?: run {
            projection.stop()
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(route)
            return appContext.getString(R.string.log_audio_record_create_failed)
        }

        return try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException(appContext.getString(R.string.log_audio_record_start_failed, record.recordingState))
            }

            mediaProjection = projection
            audioRecord = record
            activeRoute = route

            _uiState.update {
                it.copy(
                    isStreaming = true,
                    controllerConnected = true
                )
            }

            startNewStreamingPipeline(record)

            if (_uiState.value.mutePhoneWhileStreaming) {
                schedulePhoneMuteForStreaming()
            }

            updateMediaSession()
            updateGamePresetAutoDetect()

            appContext.getString(R.string.log_stream_started)
        } catch (t: Throwable) {
            Log.e(TAG, "Streaming start failed", t)
            try { record.release() } catch (_: Throwable) {}
            try { projection.stop() } catch (_: Throwable) {}
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(activeRoute)
            activeRoute = null
            stopPhoneMuteForStreaming()
            _uiState.update { it.copy(isStreaming = false) }
            updateGamePresetAutoDetect()
            appContext.getString(R.string.log_stream_start_error, t.message ?: "")
        }
    }

    private fun startNewStreamingPipeline(record: AudioRecord) {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(
                if (_uiState.value.gameMode) {
                    Process.THREAD_PRIORITY_URGENT_AUDIO
                } else {
                    Process.THREAD_PRIORITY_AUDIO
                }
            )

            if (_uiState.value.gameMode) {
                resetGameModeShaper()
            }

            val payloadSize = 384
            val packetsPerUrb = if (_uiState.value.gameMode) 6 else 32
            val chunkBytes = payloadSize * packetsPerUrb
            val framesPerChunk = chunkBytes / OUTPUT_FRAME_BYTES
            val inputShortsPerChunk = framesPerChunk * INPUT_CHANNELS

            val inputShorts = ShortArray(inputShortsPerChunk)
            val outputBytes = ByteArray(chunkBytes)

            try {
                while (captureJob?.isActive == true) {
                    val read = readExactly(record, inputShorts, inputShortsPerChunk)
                    if (read <= 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                        break
                    }

                    audioLevelFlow.value = calculateAudioLevel(inputShorts, read)

                    stereoToQuadMono(inputShorts, outputBytes, framesPerChunk)
                    NativeAudioBridge.nativeIsoPushPcm(outputBytes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
            } finally {
                Log.i(TAG, "Streaming pipeline stopped")
            }
        }
    }

    private fun stopStreamingInternal(stopNative: Boolean = true) {
        stopSpeakerKeepAlive()
        stopGamePresetAutoDetect()
        NativeAudioBridge.nativeIsoSetQueueLimit(20)

        captureJob?.cancel()
        captureJob = null

        if (stopNative) {
            try {
                NativeAudioBridge.nativeIsoStreamStop()
                NativeAudioBridge.nativeIsoSetLowLatencyMode(false)
            } catch (_: Throwable) {
            }
        }

        try {
            audioRecord?.stop()
        } catch (_: Throwable) {
        }

        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }
        audioRecord = null

        try {
            mediaProjection?.stop()
        } catch (_: Throwable) {
        }
        mediaProjection = null

        closeAudioRoute(activeRoute)
        activeRoute = null

        stopPhoneMuteForStreaming()

        _uiState.update { current ->
            current.copy(isStreaming = false)
        }

        updateMediaSession()
        updateGamePresetAutoDetect()
    }

    private fun readExactly(record: AudioRecord, buffer: ShortArray, targetShortCount: Int): Int {
        var offset = 0
        while (offset < targetShortCount) {
            val rc = record.read(
                buffer,
                offset,
                targetShortCount - offset,
                AudioRecord.READ_BLOCKING
            )
            if (rc <= 0) return rc
            offset += rc
        }
        return offset
    }

    private fun stereoToQuadMono(input: ShortArray, output: ByteArray, frameCount: Int) {
        var inputIndex = 0
        var outputIndex = 0

        val gain = _uiState.value.audioGain
        val state = _uiState.value

        val jackMode = state.routeCh1

        val ch1Enabled = state.routeCh1
        val ch2Enabled = state.routeCh2
        val ch3Enabled = state.routeCh3
        val ch4Enabled = state.routeCh4

        repeat(frameCount) {
            val left = input[inputIndex++].toInt()
            val right = input[inputIndex++].toInt()

            val monoBase = (left + right) / 2
            val ampLeft = (left * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            val ampRight = (right * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
            val ampMono = (monoBase * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            if (state.gameMode) {
                val haptic = shapeGameModeHapticSample(monoBase)

                writeShortLe(output, outputIndex, 0)
                outputIndex += 2

                writeShortLe(output, outputIndex, 0)
                outputIndex += 2

                writeShortLe(output, outputIndex, if (ch3Enabled) haptic else 0)
                outputIndex += 2

                writeShortLe(output, outputIndex, if (ch4Enabled) haptic else 0)
                outputIndex += 2
            } else if (jackMode) {
                writeShortLe(output, outputIndex, ampLeft)
                outputIndex += 2

                writeShortLe(output, outputIndex, ampRight)
                outputIndex += 2

                writeShortLe(output, outputIndex, if (ch3Enabled) ampMono else 0)
                outputIndex += 2

                writeShortLe(output, outputIndex, if (ch4Enabled) ampMono else 0)
                outputIndex += 2
            } else {
                writeShortLe(output, outputIndex, if (ch1Enabled) ampMono else 0)
                outputIndex += 2

                writeShortLe(output, outputIndex, if (ch2Enabled) ampMono else 0)
                outputIndex += 2

                writeShortLe(output, outputIndex, if (ch3Enabled) ampMono else 0)
                outputIndex += 2

                writeShortLe(output, outputIndex, if (ch4Enabled) ampMono else 0)
                outputIndex += 2
            }
        }
    }

    private fun shapeGameModeHapticSample(monoSample: Int): Short {
        val tuning = _uiState.value.gameModeTuning
        val sample = monoSample.toFloat()

        // A narrower low bed keeps engine/explosion body, while the fast-vs-slow
        // envelope difference lifts impacts without turning the whole mix into mush.
        gameBassState += (sample - gameBassState) * tuning.bassFollow

        val lowMagnitude = abs(gameBassState)
        gameTransientFast += (lowMagnitude - gameTransientFast) * tuning.transientFastFollow
        gameTransientSlow += (lowMagnitude - gameTransientSlow) * tuning.transientSlowFollow

        val transient = (gameTransientFast - gameTransientSlow).coerceAtLeast(0f)
        val adaptiveAmount = if (tuning.adaptiveStrengthEnabled) {
            (lowMagnitude / 9_000f).coerceIn(tuning.adaptiveFloor, tuning.adaptiveCeiling)
        } else {
            1f
        }
        val preciseReactionAmount =
            if (tuning.preciseReactionEnabled) tuning.precisePunch else tuning.softPunch

        val gatedLow = when {
            lowMagnitude > tuning.lowThreshold -> gameBassState * tuning.strongLowMix * adaptiveAmount
            transient > tuning.transientThreshold -> gameBassState * tuning.transientLowMix * adaptiveAmount
            else -> gameBassState * tuning.quietLowMix * adaptiveAmount
        }

        val punch = transient * preciseReactionAmount *
            if (gameBassState >= 0f) tuning.punchPolarityScale else -tuning.punchPolarityScale
        val mixed = gatedLow + punch
        val softClipped = mixed / (1f + abs(mixed) / tuning.softClipDenominator.coerceAtLeast(1f))

        return softClipped
            .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
            .toInt()
            .toShort()
    }

    private fun resetGameModeShaper() {
        gameBassState = 0f
        gameTransientFast = 0f
        gameTransientSlow = 0f
    }

    private fun updateGamePresetAutoDetect() {
        val state = _uiState.value
        val shouldRun = state.gameMode && state.isStreaming && state.gameModePresets.isNotEmpty()

        if (!shouldRun) {
            stopGamePresetAutoDetect()
            usageAccessPromptShown = false
            lastForegroundPresetPackage = null
            return
        }

        if (!hasUsageAccessPermission()) {
            stopGamePresetAutoDetect()
            if (!usageAccessPromptShown) {
                usageAccessPromptShown = true
                showGamePresetToast(appContext.getString(R.string.toast_allow_usage_access))
                runCatching {
                    appContext.startActivity(
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            }
            return
        }

        usageAccessPromptShown = false
        startGamePresetAutoDetect()
    }

    private fun startGamePresetAutoDetect() {
        if (gamePresetAutoDetectJob?.isActive == true) return

        gamePresetAutoDetectJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val packageName = currentForegroundPackage()
                val matchingPreset = _uiState.value.gameModePresets.firstOrNull {
                    it.appPackageName == packageName
                }

                if (
                    packageName != null &&
                    packageName != appContext.packageName &&
                    matchingPreset != null &&
                    lastForegroundPresetPackage != packageName
                ) {
                    lastForegroundPresetPackage = packageName
                    withContext(Dispatchers.Main.immediate) {
                        updateGameModeTuning(matchingPreset.tuning)
                        _uiState.update { current ->
                            current.copy(activeGamePresetId = matchingPreset.id)
                        }
                        showGamePresetToast(
                            appContext.getString(R.string.toast_game_preset_active, matchingPreset.appLabel)
                        )
                        setLog(
                            appContext.getString(R.string.log_game_preset_auto_active, matchingPreset.appLabel)
                        )
                    }
                } else if (packageName != null && matchingPreset == null) {
                    lastForegroundPresetPackage = packageName
                }

                kotlinx.coroutines.delay(1200)
            }
        }
    }

    private fun stopGamePresetAutoDetect() {
        gamePresetAutoDetectJob?.cancel()
        gamePresetAutoDetectJob = null
    }

    private fun hasUsageAccessPermission(): Boolean {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }

        if (mode != AppOpsManager.MODE_ALLOWED) {
            return false
        }

        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60_000L,
            now
        )
        return !stats.isNullOrEmpty()
    }

    private fun currentForegroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - 10_000L
        val event = UsageEvents.Event()
        val events = usageStatsManager.queryEvents(begin, end)

        var latestPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestPackage = event.packageName
            }
        }

        if (!latestPackage.isNullOrBlank()) {
            return latestPackage
        }

        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            begin,
            end
        )?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun showGamePresetToast(message: String) {
        scope.launch(Dispatchers.Main.immediate) {
            gamePresetToast?.cancel()
            gamePresetToast = Toast.makeText(appContext, message, Toast.LENGTH_SHORT).also {
                it.show()
            }
        }
    }

    private fun findMatchingGameModePresetId(tuning: GameModeTuning): String? {
        return _uiState.value.gameModePresets.firstOrNull { it.tuning == tuning }?.id
    }

    private fun loadGameModePresets(): List<GameModePreset> {
        val raw = preferences.getString(KEY_GAME_MODE_PRESETS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val tuningJson = item.getJSONObject("tuning")
                    add(
                        GameModePreset(
                            id = item.getString("id"),
                            appPackageName = item.getString("appPackageName"),
                            appLabel = item.getString("appLabel"),
                            tuning = tuningJson.toGameModeTuning()
                        )
                    )
                }
            }.sortedBy { it.appLabel.lowercase() }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveGameModePresets(presets: List<GameModePreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("appPackageName", preset.appPackageName)
                    .put("appLabel", preset.appLabel)
                    .put("tuning", preset.tuning.toJson())
            )
        }
        preferences.edit().putString(KEY_GAME_MODE_PRESETS, array.toString()).apply()
    }

    private fun GameModeTuning.toJson(): JSONObject {
        return JSONObject()
            .put("adaptiveStrengthEnabled", adaptiveStrengthEnabled)
            .put("preciseReactionEnabled", preciseReactionEnabled)
            .put("bassFollow", bassFollow.toDouble())
            .put("transientFastFollow", transientFastFollow.toDouble())
            .put("transientSlowFollow", transientSlowFollow.toDouble())
            .put("adaptiveFloor", adaptiveFloor.toDouble())
            .put("adaptiveCeiling", adaptiveCeiling.toDouble())
            .put("lowThreshold", lowThreshold.toDouble())
            .put("transientThreshold", transientThreshold.toDouble())
            .put("strongLowMix", strongLowMix.toDouble())
            .put("transientLowMix", transientLowMix.toDouble())
            .put("quietLowMix", quietLowMix.toDouble())
            .put("precisePunch", precisePunch.toDouble())
            .put("softPunch", softPunch.toDouble())
            .put("punchPolarityScale", punchPolarityScale.toDouble())
            .put("softClipDenominator", softClipDenominator.toDouble())
    }

    private fun JSONObject.toGameModeTuning(): GameModeTuning {
        return GameModeTuning(
            adaptiveStrengthEnabled = optBoolean("adaptiveStrengthEnabled", true),
            preciseReactionEnabled = optBoolean("preciseReactionEnabled", true),
            bassFollow = optDouble("bassFollow", 0.055).toFloat(),
            transientFastFollow = optDouble("transientFastFollow", 0.24).toFloat(),
            transientSlowFollow = optDouble("transientSlowFollow", 0.022).toFloat(),
            adaptiveFloor = optDouble("adaptiveFloor", 0.05).toFloat(),
            adaptiveCeiling = optDouble("adaptiveCeiling", 1.15).toFloat(),
            lowThreshold = optDouble("lowThreshold", 2600.0).toFloat(),
            transientThreshold = optDouble("transientThreshold", 850.0).toFloat(),
            strongLowMix = optDouble("strongLowMix", 0.62).toFloat(),
            transientLowMix = optDouble("transientLowMix", 0.34).toFloat(),
            quietLowMix = optDouble("quietLowMix", 0.03).toFloat(),
            precisePunch = optDouble("precisePunch", 1.15).toFloat(),
            softPunch = optDouble("softPunch", 0.32).toFloat(),
            punchPolarityScale = optDouble("punchPolarityScale", 0.95).toFloat(),
            softClipDenominator = optDouble("softClipDenominator", 18_000.0).toFloat()
        )
    }

    private fun exportGameModePreset(preset: GameModePreset): String {
        fun formatFloat(value: Float): String = "%.4f".format(value)

        val tuning = preset.tuning
        return buildString {
            appendLine("mode = GAME_PRESET")
            appendLine("app_package = ${preset.appPackageName}")
            appendLine("app_label = ${preset.appLabel}")
            appendLine("adaptive_strength = ${tuning.adaptiveStrengthEnabled}")
            appendLine("precise_reaction = ${tuning.preciseReactionEnabled}")
            appendLine("bass_follow = ${formatFloat(tuning.bassFollow)}")
            appendLine("transient_fast_follow = ${formatFloat(tuning.transientFastFollow)}")
            appendLine("transient_slow_follow = ${formatFloat(tuning.transientSlowFollow)}")
            appendLine("adaptive_floor = ${formatFloat(tuning.adaptiveFloor)}")
            appendLine("adaptive_ceiling = ${formatFloat(tuning.adaptiveCeiling)}")
            appendLine("low_threshold = ${formatFloat(tuning.lowThreshold)}")
            appendLine("transient_threshold = ${formatFloat(tuning.transientThreshold)}")
            appendLine("strong_low_mix = ${formatFloat(tuning.strongLowMix)}")
            appendLine("transient_low_mix = ${formatFloat(tuning.transientLowMix)}")
            appendLine("quiet_low_mix = ${formatFloat(tuning.quietLowMix)}")
            appendLine("precise_punch = ${formatFloat(tuning.precisePunch)}")
            appendLine("soft_punch = ${formatFloat(tuning.softPunch)}")
            appendLine("punch_scale = ${formatFloat(tuning.punchPolarityScale)}")
            append("soft_clip = ${formatFloat(tuning.softClipDenominator)}")
        }
    }

    private fun parseGameModePreset(raw: String): GameModePreset? {
        val lines = raw
            .replace("\r", "\n")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val values = mutableMapOf<String, String>()
        for (line in lines) {
            val separatorIndex = line.indexOf('=')
            if (separatorIndex <= 0 || separatorIndex >= line.lastIndex) {
                return null
            }
            val key = line.substring(0, separatorIndex).trim().lowercase()
            val value = line.substring(separatorIndex + 1).trim()
            values[key] = value
        }

        if (values["mode"] != "GAME_PRESET") return null

        val appPackageName = values["app_package"].orEmpty()
        val appLabel = values["app_label"].orEmpty()
        if (appPackageName.isBlank() || appLabel.isBlank()) return null

        fun bool(name: String, fallback: Boolean): Boolean {
            return values[name]?.lowercase()?.let {
                when (it) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            } ?: fallback
        }

        fun float(name: String, fallback: Float): Float {
            return values[name]?.replace(',', '.')?.toFloatOrNull() ?: fallback
        }

        return GameModePreset(
            id = UUID.randomUUID().toString(),
            appPackageName = appPackageName,
            appLabel = appLabel,
            tuning = GameModeTuning(
                adaptiveStrengthEnabled = bool("adaptive_strength", GameModeTuning.DEFAULT.adaptiveStrengthEnabled),
                preciseReactionEnabled = bool("precise_reaction", GameModeTuning.DEFAULT.preciseReactionEnabled),
                bassFollow = float("bass_follow", GameModeTuning.DEFAULT.bassFollow),
                transientFastFollow = float("transient_fast_follow", GameModeTuning.DEFAULT.transientFastFollow),
                transientSlowFollow = float("transient_slow_follow", GameModeTuning.DEFAULT.transientSlowFollow),
                adaptiveFloor = float("adaptive_floor", GameModeTuning.DEFAULT.adaptiveFloor),
                adaptiveCeiling = float("adaptive_ceiling", GameModeTuning.DEFAULT.adaptiveCeiling),
                lowThreshold = float("low_threshold", GameModeTuning.DEFAULT.lowThreshold),
                transientThreshold = float("transient_threshold", GameModeTuning.DEFAULT.transientThreshold),
                strongLowMix = float("strong_low_mix", GameModeTuning.DEFAULT.strongLowMix),
                transientLowMix = float("transient_low_mix", GameModeTuning.DEFAULT.transientLowMix),
                quietLowMix = float("quiet_low_mix", GameModeTuning.DEFAULT.quietLowMix),
                precisePunch = float("precise_punch", GameModeTuning.DEFAULT.precisePunch),
                softPunch = float("soft_punch", GameModeTuning.DEFAULT.softPunch),
                punchPolarityScale = float("punch_scale", GameModeTuning.DEFAULT.punchPolarityScale),
                softClipDenominator = float("soft_clip", GameModeTuning.DEFAULT.softClipDenominator)
            )
        )
    }

    private fun writeShortLe(buffer: ByteArray, offset: Int, value: Short) {
        buffer[offset] = (value.toInt() and 0xFF).toByte()
        buffer[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    private fun calculateAudioLevel(buffer: ShortArray, sampleCount: Int): Float {
        if (sampleCount <= 0) return 0f

        var sum = 0.0
        for (i in 0 until sampleCount) {
            val s = buffer[i].toInt()
            sum += (s * s).toDouble()
        }

        val rms = kotlin.math.sqrt(sum / sampleCount)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    private fun currentControllerVolume(): Int {
        return MAX_CONTROLLER_VOLUME
    }

    private fun startSpeakerKeepAlive() {
        speakerKeepAliveJob?.cancel()
        speakerKeepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                routeHidMutex.withLock {
                    runAudioRoutingHid()
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun stopSpeakerKeepAlive() {
        speakerKeepAliveJob?.cancel()
        speakerKeepAliveJob = null
    }

    private fun startInputReading() {
        if (_uiState.value.isStreaming) return

        inputJob?.cancel()
        inputJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(64)
            while (isActive) {
                val controller = dualSense ?: break
                if (_uiState.value.isStreaming) break

                val read = controller.receive(buffer, 20)
                if (read > 0) {
                    parseInputReport(buffer)
                } else {
                    kotlinx.coroutines.delay(10)
                }
            }
        }
    }

    private fun stopInputReading() {
        inputJob?.cancel()
        inputJob = null
    }

    private fun parseInputReport(report: ByteArray) {
        if (report.size < 54) return

        val t1Active = (report[33].toInt() and 0x80) == 0
        val t1X = ((report[35].toInt() and 0x0F) shl 8) or (report[34].toInt() and 0xFF)
        val t1Y = ((report[36].toInt() and 0xFF) shl 4) or ((report[35].toInt() and 0xF0) shr 4)

        val t2Active = (report[37].toInt() and 0x80) == 0
        val t2X = ((report[39].toInt() and 0x0F) shl 8) or (report[38].toInt() and 0xFF)
        val t2Y = ((report[40].toInt() and 0xFF) shl 4) or ((report[39].toInt() and 0xF0) shr 4)

        val batteryByte = report[53].toInt()
        val capacity = (batteryByte and 0x0F).coerceIn(0, 8)
        val batteryPercent = (capacity * 100) / 8

        _uiState.update { current ->
            current.copy(
                touch1 = TouchpadPoint(x = t1X, y = t1Y, isActive = t1Active),
                touch2 = TouchpadPoint(x = t2X, y = t2Y, isActive = t2Active),
                batteryLevel = batteryPercent,
                isWired = true
            )
        }
    }

    private fun fetchControllerDetails(controller: DualSenseUsbController, device: UsbDevice) {
        scope.launch(Dispatchers.IO) {
            SystemClock.sleep(150)

            val pairingBuffer = ByteArray(20)
            val firmwareBuffer = ByteArray(64)

            val pairingLen = try {
                controller.getFeatureReport(0x09, pairingBuffer)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read pairing report 0x09", t)
                -1
            }

            val firmwareLen = try {
                controller.getFeatureReport(0x20, firmwareBuffer)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read firmware report 0x20", t)
                -1
            }

            Log.d(
                TAG,
                "Pairing report len=$pairingLen data=" +
                        pairingBuffer.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            )

            Log.d(
                TAG,
                "Firmware report len=$firmwareLen data=" +
                        firmwareBuffer.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            )

            val parsed = parseControllerDetails(
                pairingReport = pairingBuffer.takeIf { pairingLen > 0 },
                firmwareReport = firmwareBuffer.takeIf { firmwareLen > 0 },
                device = device
            )

            _uiState.update {
                it.copy(
                    serialNumber = parsed.serialNumber,
                    btAddress = parsed.btAddress,
                    firmwareVersion = parsed.firmwareVersion,
                    firmwareType = parsed.firmwareType,
                    softwareSeries = parsed.softwareSeries,
                    hardwareInfo = parsed.hardwareInfo,
                    updateVersion = parsed.updateVersion,
                    buildDate = parsed.buildDate,
                    buildTime = parsed.buildTime,
                    deviceInfo = parsed.deviceInfo,
                    controllerColor = parsed.controllerColor
                )
            }
        }
    }

    private fun createPlaybackCaptureRecord(projection: MediaProjection): AudioRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()

        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val gameMode = _uiState.value.gameMode
        val desiredBufferBytes = if (gameMode) {
            minBufferBytes
        } else {
            maxOf(minBufferBytes, 64 * 1024)
        }

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(format)
            .setBufferSizeInBytes(desiredBufferBytes)
            .build()
    }

    private fun runAudioRoutingHid(): String {
        try {
            val state = _uiState.value
            if (state.gameMode) {
                return "GAME haptic route / ${runMusicRumbleHid()}"
            }

            val routeText = if (state.routeCh1) {
                runJackRoutingHid()
            } else {
                val first = runSpeakerHid()
                SystemClock.sleep(40)
                val second = runSpeakerHid()
                "$first / SPEAKER confirm: $second"
            }

            return if (state.routeCh3 || state.routeCh4) {
                "$routeText / ${runMusicRumbleHid()}"
            } else {
                routeText
            }
        } finally {
            if (_uiState.value.isStreaming) {
                releaseDualSenseHidHandle()
            }
        }
    }

    private fun runJackRoutingHid(): String {
        val controller = ensureController() ?: return appContext.getString(R.string.log_no_usb_dualsense)
        val report = ByteArray(DS_OUTPUT_REPORT_USB_SIZE)
        report[0] = DS_OUTPUT_REPORT_USB.toByte()
        report[1] = 0xF3.toByte()
        report[5] = 0xFF.toByte()

        val ok = controller.send(report)

        if (!ok && !_uiState.value.isStreaming) {
            setControllerConnected(false)
        }

        return if (ok) "JACK route OK" else "JACK route Fail"
    }

    private fun sendJackRoutingCommand(device: UsbDevice): Boolean {
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return false
        }

        val connection = routingUsbConnection ?: usbManager.openDevice(device)?.also {
            routingUsbConnection = it
        } ?: return false

        val endpoint = routingHidOutEndpoint ?: findHidOutTarget(device)?.also { target ->
            try {
                connection.claimInterface(target.usbInterface, true)
            } catch (_: Throwable) {
            }

            routingHidInterface = target.usbInterface
            routingHidOutEndpoint = target.endpoint
        }?.endpoint ?: return false

        val report = ByteArray(DS_OUTPUT_REPORT_USB_SIZE) { 0 }

        // ==================== DUALSENSE HID AUDIO ROUTING REPORT ====================

        // 0x02 = USB output report ID.
        report[0] = DS_OUTPUT_REPORT_USB.toByte()

        // 0xE0:
        // Bit5 = speaker volume enable
        // Bit6 = mic volume enable
        // Bit7 = audio routing enable
        report[1] = 0xF3.toByte()

        // Rumble OFF.

        // 0x7F = headphone / JACK volume max.
        report[5] = 0xFF.toByte()

        val jackMode = true
        if (jackMode) {
            // JACK mód:
            // 0x00 = internal speaker volume muted
            // 0x00 = route to JACK / headset
            report[6] = 0x00.toByte()
            report[8] = 0x00.toByte()
        } else {
            // SPEAKER mód:
            // 0xFF = internal speaker volume max
            // 0x30 = route to internal speaker
            report[6] = 0xFF.toByte()
            report[8] = 0x30.toByte()
        }

        // 0x40 = mic volume placeholder.
        report[7] = 0x40.toByte()
        report[7] = 0x00.toByte()

        val result = try {
            connection.bulkTransfer(endpoint, report, report.size, 100)
        } catch (t: Throwable) {
            Log.e(TAG, "Routing HID bulkTransfer failed", t)
            -1
        }

        return result == report.size
    }

    private fun runSpeakerHid(): String {
        val controller = ensureController() ?: return appContext.getString(R.string.log_no_usb_dualsense)
        val controllerVolume = currentControllerVolume()
        val report = ByteArray(DS_OUTPUT_REPORT_USB_SIZE)
        report[0] = DS_OUTPUT_REPORT_USB.toByte()

        // Original speaker wake/routing magic bytes.
        // Ezek maradnak a régi működéshez.
        // JACK módban ezt nem közvetlenül hívjuk, hanem runAudioRoutingHid() választ útvonalat.
        report[1] = 0xF3.toByte()
        report[2] = 0x00.toByte()

        report[3] = 0x00
        report[4] = 0x00
        report[5] = 0xFF.toByte()
        report[6] = controllerVolume.toByte()
        report[7] = 0x00.toByte()
        report[8] = 0xFF.toByte()

        // These seem important for enabling the audio/vibration mix
        report[39] = 0x00.toByte()
        report[42] = 0x00.toByte()
        report[44] = 0x00.toByte()

        val ok = controller.send(report)

        if (!ok && !_uiState.value.isStreaming) {
            setControllerConnected(false)
        }

        return if (ok) "SPEAKER loud route OK" else "SPEAKER loud route Fail"
    }

    private fun runMusicRumbleHid(): String {
        val controller = ensureController() ?: return appContext.getString(R.string.log_no_usb_dualsense)
        val report = ByteArray(DS_OUTPUT_REPORT_USB_SIZE)
        report[0] = DS_OUTPUT_REPORT_USB.toByte()
        report[2] = 0x15.toByte()
        report[39] = 0x03.toByte()
        report[42] = 0x02.toByte()
        report[43] = 0x00.toByte()

        val ok = controller.send(report)

        if (!ok && !_uiState.value.isStreaming) {
            setControllerConnected(false)
        }

        return if (ok) "Music rumble OK" else "Music rumble Fail"
    }


    private fun findHidOutTarget(device: UsbDevice): HidOutTarget? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)

            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)

                if (
                    ep.direction == UsbConstants.USB_DIR_OUT &&
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_INT
                ) {
                    return HidOutTarget(
                        usbInterface = intf,
                        endpoint = ep
                    )
                }
            }
        }

        return null
    }

    private fun openAudioRoute(device: UsbDevice): AudioRouteSession? {
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return null
        }

        val connection = try {
            usbManager.openDevice(device)
        } catch (_: SecurityException) {
            return null
        } ?: return null

        val target = findAudioIsoTarget(device)
        if (target == null) {
            safeCloseConnection(connection)
            return null
        }

        val targetInterface = try {
            (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { intf ->
                    intf.id == target.interfaceNumber &&
                            intf.alternateSetting == target.altSetting
                }
        } catch (_: Throwable) {
            null
        }

        if (targetInterface == null) {
            safeCloseConnection(connection)
            return null
        }

        val claimed = try {
            connection.claimInterface(targetInterface, true)
        } catch (_: Throwable) {
            false
        }

        if (!claimed) {
            safeCloseConnection(connection)
            return null
        }

        return AudioRouteSession(
            device = device,
            connection = connection,
            target = target,
            claimedInterface = targetInterface
        )
    }

    private fun closeAudioRoute(route: AudioRouteSession?) {
        if (route == null) return

        try {
            route.connection.releaseInterface(route.claimedInterface)
        } catch (_: Throwable) {
        }

        safeCloseConnection(route.connection)
    }

    private fun findDualSenseDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull {
            it.vendorId == SONY_VENDOR_ID && it.productId == DUALSENSE_PRODUCT_ID
        }
    }

    private fun requestPermission(device: UsbDevice) {
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )

        usbManager.requestPermission(device, pendingIntent)
    }

    private fun connectToDualSense(device: UsbDevice): DualSenseUsbController? {
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return null
        }

        closeController()
        val controller = DualSenseUsbController.open(usbManager, device)
        dualSense = controller

        val success = controller != null
        setControllerConnected(success)
        if (success) {
            setLog("DualSense csatlakoztatva")
            fetchControllerDetails(controller, device)
        }

        return controller
    }

    private fun findAudioIsoTarget(device: UsbDevice): AudioIsoTarget? {
        val candidates = mutableListOf<AudioIsoTarget>()

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            if (intf.interfaceSubclass != USB_SUBCLASS_AUDIOSTREAMING) continue

            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (
                    ep.direction == UsbConstants.USB_DIR_OUT &&
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
                ) {
                    candidates += AudioIsoTarget(
                        interfaceNumber = intf.id,
                        altSetting = intf.alternateSetting,
                        endpointAddress = ep.address,
                        packetSize = ep.maxPacketSize
                    )
                }
            }
        }

        if (candidates.isEmpty()) return null

        val chosen = candidates
            .filter { it.altSetting != 0 }
            .maxByOrNull { it.packetSize }
            ?: candidates.maxByOrNull { it.packetSize }

        if (chosen != null) {
            setLog(
                buildString {
                    appendLine("Audio ISO jelöltek:")
                    candidates.forEach {
                        appendLine(
                            "IF=${it.interfaceNumber} ALT=${it.altSetting} EP=${it.endpointAddress} PACKET=${it.packetSize}"
                        )
                    }
                    appendLine(
                        "Kiválasztott: IF=${chosen.interfaceNumber} ALT=${chosen.altSetting} EP=${chosen.endpointAddress} PACKET=${chosen.packetSize}"
                    )
                }
            )
        }

        return chosen
    }

    private fun safeCloseConnection(connection: UsbDeviceConnection) {
        try {
            connection.close()
        } catch (_: Throwable) {
        }
    }

    private fun closeController() {
        try {
            routingHidInterface?.let { hidInterface ->
                routingUsbConnection?.releaseInterface(hidInterface)
            }
        } catch (_: Throwable) {
        }

        try {
            routingUsbConnection?.close()
        } catch (_: Throwable) {
        }

        routingUsbConnection = null
        routingHidOutEndpoint = null
        routingHidInterface = null

        dualSense?.close()
        dualSense = null
        setControllerConnected(false)
    }

    private fun releaseDualSenseHidHandle() {
        try {
            dualSense?.close()
        } catch (_: Throwable) {
        }
        dualSense = null
    }

    private fun ensureController(): DualSenseUsbController? {
        dualSense?.let { return it }
        val device = findDualSenseDevice() ?: return null
        return connectToDualSense(device)
    }

    private fun tryAutoConnect() {
        val device = findDualSenseDevice()

        if (device == null) {
            setControllerConnected(false)
            setLog(appContext.getString(R.string.log_no_usb_dualsense))
            return
        }

        if (usbManager.hasPermission(device)) {
            val controller = connectToDualSense(device)
            if (controller != null) {
                setControllerConnected(true)
                setLog(appContext.getString(R.string.log_auto_connected))
            } else {
                setControllerConnected(false)
                setLog(appContext.getString(R.string.log_auto_connect_failed))
            }
        } else {
            requestPermission(device)
            setControllerConnected(false)
            setLog(appContext.getString(R.string.log_usb_permission_requested))
        }
    }

    private fun registerUsbReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterUsbReceiver() {
        if (!receiverRegistered) return
        try {
            appContext.unregisterReceiver(permissionReceiver)
        } catch (_: Throwable) {
        }
        receiverRegistered = false
    }

    private fun schedulePhoneMuteForStreaming() {
        phoneMuteDelayJob?.cancel()
        phoneMuteDelayJob = scope.launch(Dispatchers.Main.immediate) {
            kotlinx.coroutines.delay(350)

            if (!_uiState.value.isStreaming) return@launch
            if (!_uiState.value.mutePhoneWhileStreaming) return@launch

            startPhoneMuteForStreaming()
        }
    }

    private fun startPhoneMuteForStreaming() {
        if (!_uiState.value.isStreaming) return
        if (!_uiState.value.mutePhoneWhileStreaming) return

        if (!phoneMutedByApp) {
            savedMusicVolumeBeforeMute = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            phoneMutedByApp = true
        }

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (_: Throwable) {
        }

        phoneMuteKeepAliveJob?.cancel()
        phoneMuteKeepAliveJob = scope.launch(Dispatchers.IO) {
            while (true) {
                if (!isActive) break
                if (!_uiState.value.isStreaming) break
                if (!_uiState.value.mutePhoneWhileStreaming) break

                try {
                    if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != 0) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    }
                } catch (_: Throwable) {
                }

                kotlinx.coroutines.delay(80)
            }
        }
    }

    private fun stopPhoneMuteForStreaming() {
        phoneMuteDelayJob?.cancel()
        phoneMuteDelayJob = null

        phoneMuteKeepAliveJob?.cancel()
        phoneMuteKeepAliveJob = null

        if (!phoneMutedByApp) {
            savedMusicVolumeBeforeMute = null
            return
        }

        val restoreVolume = savedMusicVolumeBeforeMute
        savedMusicVolumeBeforeMute = null
        phoneMutedByApp = false

        if (restoreVolume != null) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVolume, 0)
            } catch (_: Throwable) {
            }
        }
    }

    private fun resetRoutingHidConnection() {
        try {
            routingHidInterface?.let { hidInterface ->
                routingUsbConnection?.releaseInterface(hidInterface)
            }
        } catch (_: Throwable) {
        }

        try {
            routingUsbConnection?.close()
        } catch (_: Throwable) {
        }

        routingUsbConnection = null
        routingHidOutEndpoint = null
        routingHidInterface = null
    }

    private fun resetJackSwitchPath() {
        resetRoutingHidConnection()

        try {
            dualSense?.close()
        } catch (_: Throwable) {
        }

        dualSense = null
    }

    private fun initMediaSession() {
        if (mediaSession != null) return

        mediaSession = MediaSession(appContext, "PS5CTBro_Audio").apply {

            volumeProvider = object : VolumeProvider(
                VolumeProvider.VOLUME_CONTROL_RELATIVE,
                10,
                _uiState.value.volumeStep
            ) {
                override fun onAdjustVolume(direction: Int) {
                    handleHardwareVolumeButton(direction)
                }

                override fun onSetVolumeTo(volume: Int) {
                    setVolumeStep(volume)
                }
            }

            setPlaybackToRemote(volumeProvider!!)

            setActive(true)
        }
    }

    private fun updateMediaSession() {
        scope.launch(Dispatchers.Main.immediate) {
            val state = _uiState.value
            val shouldBeActive = state.hardwareVolumeButtonsControlController && state.controllerConnected

            if (shouldBeActive) {
                if (mediaSession == null) {
                    mediaSession = MediaSession(appContext, "PS5CTBroVolumeControl").apply {
                        setCallback(object : MediaSession.Callback() {
                            override fun onPlay() {
                                isActive = true
                            }
                        })
                    }
                }

                mediaSession?.setMetadata(
                    MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, "DualSense Speaker")
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, "PS5CTBro")
                        .build()
                )

                mediaSession?.setPlaybackState(
                    PlaybackState.Builder()
                        .setState(
                            PlaybackState.STATE_PLAYING,
                            PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                            1.0f
                        )
                        .setActions(PlaybackState.ACTION_PLAY)
                        .build()
                )

                if (volumeProvider == null) {
                    volumeProvider = object : VolumeProvider(
                        VOLUME_CONTROL_ABSOLUTE,
                        10,
                        state.volumeStep
                    ) {
                        override fun onAdjustVolume(direction: Int) {
                            if (direction != 0) {
                                handleHardwareVolumeButton(direction)
                            }
                        }

                        override fun onSetVolumeTo(volume: Int) {
                            setVolumeStep(volume.coerceIn(0, 10))
                        }
                    }
                    mediaSession?.setPlaybackToRemote(volumeProvider!!)
                } else {
                    volumeProvider?.currentVolume = state.volumeStep
                }

                mediaSession?.isActive = true

                if (_uiState.value.sessionToken != mediaSession?.sessionToken) {
                    _uiState.update { it.copy(sessionToken = mediaSession?.sessionToken) }
                }

                requestAudioFocus()
                startDominanceJob()
            } else {
                stopDominanceJob()
                abandonAudioFocus()
                if (mediaSession != null) {
                    mediaSession?.isActive = false
                    mediaSession?.release()
                    mediaSession = null
                    volumeProvider = null
                    _uiState.update { it.copy(sessionToken = null) }
                }
            }
        }
    }

    private fun startDominanceJob() {
        if (dominanceJob?.isActive == true) return
        dominanceJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val state = _uiState.value
                if (state.hardwareVolumeButtonsControlController && state.controllerConnected) {
                    mediaSession?.let { session ->
                        if (!session.isActive) session.isActive = true

                        if (!state.isStreaming) {
                            requestAudioFocus()
                        }
                    }
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    private fun stopDominanceJob() {
        dominanceJob?.cancel()
        dominanceJob = null
    }

    private fun requestAudioFocus() {
        if (!_uiState.value.hardwareVolumeButtonsControlController) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    val attr = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                    audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attr)
                        .setAcceptsDelayedFocusGain(true)
                        .setOnAudioFocusChangeListener { focusChange ->
                        }
                        .build()
                }
                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Focus request failed", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
    }

    private fun setLog(text: String) {
        _uiState.update { it.copy(logText = text) }
    }

    private fun setControllerConnected(connected: Boolean) {
        if (_uiState.value.controllerConnected == connected) return
        Log.d(TAG, "setControllerConnected: $connected")
        _uiState.update { it.copy(controllerConnected = connected) }
        updateMediaSession()

        if (connected) {
            startInputReading()
        } else {
            stopInputReading()
        }
    }
}
