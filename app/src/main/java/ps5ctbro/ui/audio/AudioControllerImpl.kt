package com.DueBoysenberry1226.ps5ctbro.audio

import com.DueBoysenberry1226.ps5ctbro.ui.led.LedControllerImpl
import com.DueBoysenberry1226.ps5ctbro.audio.AudioControllerImpl
import kotlinx.coroutines.isActive
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
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
import android.util.Log
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
import kotlinx.coroutines.withContext

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

        // A kontroller belső erősítése mostantól fixen maximumon (0xFF) van.
        private const val MAX_CONTROLLER_VOLUME = 0xFF
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(AudioUiState())
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
    private var receiverRegistered = false

    private var savedMusicVolumeBeforeMute: Int? = null
    private var phoneMutedByApp = false

    private var mediaSession: MediaSession? = null
    private var volumeProvider: VolumeProvider? = null
    private var dominanceJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null

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
        val text = withContext(Dispatchers.IO) { runSpeakerHid() }
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

        // Update VolumeProvider immediately
        volumeProvider?.currentVolume = clamped
        
        // Push update to system
        mediaSession?.let { session ->
            if (session.isActive) {
                val state = PlaybackState.Builder()
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build()
                session.setPlaybackState(state)
            }
        }

        scope.launch(Dispatchers.IO) {
            runSpeakerHid()
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
        
        scope.launch(Dispatchers.IO) {
            runSpeakerHid()
        }
    }

    override fun setChannelEnabled(channel: Int, enabled: Boolean) {
        _uiState.update { current ->
            when (channel) {
                1 -> current.copy(routeCh1 = enabled)
                2 -> current.copy(routeCh2 = enabled)
                3 -> current.copy(routeCh3 = enabled)
                4 -> current.copy(routeCh4 = enabled)
                else -> current
            }
        }
    }

    override fun setMutePhoneWhileStreaming(enabled: Boolean) {
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
        _uiState.update {
            it.copy(hardwareVolumeButtonsControlController = enabled)
        }
        updateMediaSession()
    }

    override fun handleHardwareVolumeButton(direction: Int): Boolean {
        val state = _uiState.value

        if (!state.hardwareVolumeButtonsControlController) return false

        when {
            direction > 0 -> setVolumeStep(state.volumeStep + 1)
            direction < 0 -> setVolumeStep(state.volumeStep - 1)
            else -> return false
        }

        return true
    }

    override fun release() {
        stopStreamingInternal()
        stopPhoneMuteForStreaming()
        closeController()
        unregisterUsbReceiver()
    }

    private fun startStreamingInternal(context: Context, resultCode: Int, data: Intent): String {
        stopStreamingInternal(stopNative = true)

        val device = findDualSenseDevice() ?: return appContext.getString(R.string.log_no_usb_dualsense)

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return appContext.getString(R.string.log_usb_permission_start)
        }

        val hidLogs = buildString {
            repeat(6) { index ->
                appendLine("HID ${index + 1}: ${runSpeakerHid()}")
                SystemClock.sleep(40)
            }
        }

        SystemClock.sleep(120)

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return appContext.getString(R.string.log_usb_permission_audio_required)
        }

        val route = openAudioRoute(device) ?: return buildString {
            appendLine(appContext.getString(R.string.log_audio_route_open_failed))
            appendLine("=== SPK HID ===")
            append(hidLogs)
        }

        val fd = try {
            route.connection.fileDescriptor
        } catch (_: SecurityException) {
            closeAudioRoute(route)
            return appContext.getString(R.string.log_usb_fd_permission_missing)
        }

        val startRc = try {
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

        // Add callback to detect when it stops
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

            startSpeakerKeepAlive()
            startNewStreamingPipeline(record)
            LedControllerImpl.getInstance(appContext).sendWakeKick()

            if (_uiState.value.mutePhoneWhileStreaming) {
                schedulePhoneMuteForStreaming()
            }

            updateMediaSession()

            appContext.getString(R.string.log_stream_started)
        } catch (t: Throwable) {
            Log.e(TAG, "Streaming start failed", t)
            try { record.release() } catch (_: Throwable) {}
            try { projection.stop() } catch (_: Throwable) {}
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(route)
            stopPhoneMuteForStreaming()
            _uiState.update { it.copy(isStreaming = false) }
            appContext.getString(R.string.log_stream_start_error, t.message ?: "")
        }
    }

    private fun startNewStreamingPipeline(record: AudioRecord) {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            val payloadSize = 384
            val packetsPerUrb = 32 // Megemeltük 12-ről a stabilitásért
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

    private fun sendStaticBlueLed() {
        val device = findDualSenseDevice() ?: return
        if (!usbManager.hasPermission(device)) return

        val controller = ensureController() ?: return

        val report = ByteArray(DS_OUTPUT_REPORT_USB_SIZE)
        report[0] = DS_OUTPUT_REPORT_USB.toByte()

        report[2] = (0x04 or 0x10 or 0x01).toByte()
        report[39] = (0x01 or 0x02).toByte()

        report[42] = 0x02.toByte()
        report[43] = 0xFF.toByte()
        report[44] = (0x20 or 0b00100).toByte()

        report[45] = 0x00.toByte()
        report[46] = 0x00.toByte()
        report[47] = 0xFF.toByte()

        try {
            controller.send(report)
        } catch (_: SecurityException) {
        }
    }

    private fun stopStreamingInternal(stopNative: Boolean = true) {
        stopSpeakerKeepAlive()

        captureJob?.cancel()
        captureJob = null

        if (stopNative) {
            try {
                NativeAudioBridge.nativeIsoStreamStop()
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

        val ch1Enabled = state.routeCh1
        val ch2Enabled = state.routeCh2
        val ch3Enabled = state.routeCh3
        val ch4Enabled = state.routeCh4

        repeat(frameCount) {
            val left = input[inputIndex++].toInt()
            val right = input[inputIndex++].toInt()

            val monoBase = (left + right) / 2
            val ampMono = (monoBase * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            // 1-es csatorna: Engedélyezzük a mono jelet a hangerő növeléséhez, ha az UI-ban be van kapcsolva
            writeShortLe(output, outputIndex, if (ch1Enabled) ampMono else 0)
            outputIndex += 2

            // 2-es csatorna: beépített hangszóró
            writeShortLe(output, outputIndex, if (ch2Enabled) ampMono else 0)
            outputIndex += 2

            // 3-4-es csatorna: motorok -> vissza mono jelre
            writeShortLe(output, outputIndex, if (ch3Enabled) ampMono else 0)
            outputIndex += 2

            writeShortLe(output, outputIndex, if (ch4Enabled) ampMono else 0)
            outputIndex += 2
        }
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
        // Mostantól fixen 0xFF (MAX)
        return MAX_CONTROLLER_VOLUME
    }

    private fun startSpeakerKeepAlive() {
        speakerKeepAliveJob?.cancel()
        speakerKeepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runSpeakerHid()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun stopSpeakerKeepAlive() {
        speakerKeepAliveJob?.cancel()
        speakerKeepAliveJob = null
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

        // Using a larger buffer for streaming stability
        val desiredBufferBytes = maxOf(minBufferBytes, 64 * 1024)

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(format)
            .setBufferSizeInBytes(desiredBufferBytes)
            .build()
    }

    private fun runSpeakerHid(): String {
        val controller = ensureController() ?: return appContext.getString(R.string.log_no_usb_dualsense)
        val controllerVolume = currentControllerVolume()
        val report = ByteArray(DS_OUTPUT_REPORT_USB_SIZE)
        report[0] = DS_OUTPUT_REPORT_USB.toByte()

        report[1] = 0xF3.toByte()
        report[2] = 0x86.toByte()
        
        report[3] = 0x00
        report[4] = 0x00
        report[5] = 0xFF.toByte()
        report[6] = controllerVolume.toByte()
        report[7] = 0xFF.toByte()
        report[8] = 0xFF.toByte()

        report[39] = 0x03.toByte() 
        report[42] = 0x02.toByte()
        report[44] = 0x24.toByte()

        val ok = controller.send(report)
        
        // JAVÍTÁS: Csak akkor állítsuk le a kapcsolatot, ha NEM streamelünk.
        // Streamelés közben a HID hívás gyakran hibára fut (foglalt USB busz), 
        // de ettől még a kontroller csatlakozva van.
        if (!ok && !_uiState.value.isStreaming) {
            setControllerConnected(false)
        }

        return if (ok) "OK" else "Fail"
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
        dualSense?.close()
        dualSense = null
        setControllerConnected(false)
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
        val state = _uiState.value
        val shouldBeActive = state.hardwareVolumeButtonsControlController && state.controllerConnected

        if (shouldBeActive) {
            if (mediaSession == null) {
                mediaSession = MediaSession(appContext, "PS5CTBroVolumeControl").apply {
                    setCallback(object : MediaSession.Callback() {
                        override fun onPlay() { isActive = true }
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
                    .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
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
            // Reduced frequency to avoid fighting with system during projection start
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

    private fun startDominanceJob() {
        if (dominanceJob?.isActive == true) return
        dominanceJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val state = _uiState.value
                if (state.hardwareVolumeButtonsControlController && state.controllerConnected) {
                    mediaSession?.let { session ->
                        if (!session.isActive) session.isActive = true
                        
                        // Only re-request focus if NOT currently streaming
                        // When streaming, MediaProjection already holds a high priority session
                        if (!state.isStreaming) {
                            requestAudioFocus()
                        }
                    }
                }
                kotlinx.coroutines.delay(2000) // Lower frequency
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
                            if (focusChange != AudioManager.AUDIOFOCUS_GAIN) {
                                // Lost focus? Grab it back next second in dominance job
                            }
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
    }
}