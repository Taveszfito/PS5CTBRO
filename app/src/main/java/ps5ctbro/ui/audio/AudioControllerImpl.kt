package com.DueBoysenberry1226.ps5ctbro.audio


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
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
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

        private val CONTROLLER_VOLUME_STEPS = intArrayOf(
            0x45, 0x4F, 0x59, 0x63, 0x6D,
            0x77, 0x81, 0x8B, 0x95, 0x9F
        )

        private val PCM_GAIN_STEPS = floatArrayOf(
            0.2f, 0.4f, 0.6f, 0.8f, 1.0f,
            1.2f, 1.4f, 1.6f, 1.8f, 2.0f
        )
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(AudioUiState())
    override val uiState: StateFlow<AudioUiState> = _uiState

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
                    setLog("USB engedély megadva, kontroller nyitva ✔")
                } else {
                    setLog("USB engedély megvan, de a kontroller megnyitása sikertelen.")
                }
            } else {
                setLog("USB engedély elutasítva.")
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
        setLog("Streaming leállítva.")
    }

    override fun onCapturePermissionDenied() {
        stopPhoneMuteForStreaming()
        setLog("A rendszerhang capture engedélyt elutasítottad.")
    }

    override fun onRecordAudioPermissionDenied() {
        stopPhoneMuteForStreaming()
        setLog("A mikrofon engedély kell a playback capture-höz.")
    }

    override fun onUnsupportedAndroidVersion() {
        stopPhoneMuteForStreaming()
        setLog("Playback capture-hez Android 10+ kell.")
    }

    override fun setVolumeStep(step: Int) {
        val clamped = step.coerceIn(0, CONTROLLER_VOLUME_STEPS.lastIndex)
        _uiState.update { current ->
            current.copy(
                volumeStep = clamped,
                logText = "Hangerő: ${clamped + 1}/10"
            )
        }

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
    }

    override fun handleHardwareVolumeButton(direction: Int): Boolean {
        val state = _uiState.value

        if (!state.hardwareVolumeButtonsControlController) return false
        if (!state.controllerConnected) return false

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

        val device = findDualSenseDevice() ?: return "Nem találok USB-s DualSense kontrollert."

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return "USB engedély kérve. Add meg, majd nyomd meg újra a Startot."
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
            return "USB engedély kell az audio streamhez."
        }

        val route = openAudioRoute(device) ?: return buildString {
            appendLine("Nem tudtam megnyitni az audio route-ot.")
            appendLine("=== SPK HID ===")
            append(hidLogs)
        }

        val fd = try {
            route.connection.fileDescriptor
        } catch (_: SecurityException) {
            closeAudioRoute(route)
            return "USB permission hiányzik a file descriptorhoz."
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
            return "USB permission hiányzik (SecurityException)"
        }

        if (startRc != 0) {
            closeAudioRoute(route)
            return "nativeIsoStreamStart hiba, rc=$startRc"
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            projectionManager.getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            Log.e(TAG, "MediaProjection error", t)
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(route)
            return "MediaProjection hiba: ${t.message}"
        } ?: run {
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(route)
            return "MediaProjection létrehozása sikertelen."
        }

        // Add callback to detect when it stops
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection STOPPED!")
                scope.launch {
                    stopStreamingInternal()
                    setLog("Stream megszakadt (Rendszer leállította)")
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
            return "AudioRecord létrehozása sikertelen: ${t.message}"
        } ?: run {
            projection.stop()
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(route)
            return "AudioRecord létrehozása sikertelen."
        }

        return try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("AudioRecord nem indult el (State: ${record.recordingState})")
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

            if (_uiState.value.mutePhoneWhileStreaming) {
                schedulePhoneMuteForStreaming()
            }

            "STREAM STARTED"
        } catch (t: Throwable) {
            Log.e(TAG, "Streaming start failed", t)
            try { record.release() } catch (_: Throwable) {}
            try { projection.stop() } catch (_: Throwable) {}
            NativeAudioBridge.nativeIsoStreamStop()
            closeAudioRoute(route)
            stopPhoneMuteForStreaming()
            _uiState.update { it.copy(isStreaming = false) }
            "STREAM START hiba: ${t.message}"
        }
    }

    private fun startNewStreamingPipeline(record: AudioRecord) {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            val payloadSize = 384
            val chunkBytes = payloadSize * 48
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

        val gain = currentPcmGain()

        val state = _uiState.value
        val ch1Enabled = state.routeCh1
        val ch2Enabled = state.routeCh2
        val ch3Enabled = state.routeCh3
        val ch4Enabled = state.routeCh4

        repeat(frameCount) {
            val left = input[inputIndex++].toInt()
            val right = input[inputIndex++].toInt()

            val monoBase = (left + right) / 2
            val amplified = (monoBase * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()

            writeShortLe(output, outputIndex, if (ch1Enabled) amplified else 0)
            outputIndex += 2
            writeShortLe(output, outputIndex, if (ch2Enabled) amplified else 0)
            outputIndex += 2
            writeShortLe(output, outputIndex, if (ch3Enabled) amplified else 0)
            outputIndex += 2
            writeShortLe(output, outputIndex, if (ch4Enabled) amplified else 0)
            outputIndex += 2
        }
    }

    private fun writeShortLe(buffer: ByteArray, offset: Int, value: Short) {
        buffer[offset] = (value.toInt() and 0xFF).toByte()
        buffer[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    private fun currentPcmGain(): Float {
        val step = _uiState.value.volumeStep.coerceIn(0, PCM_GAIN_STEPS.lastIndex)
        return PCM_GAIN_STEPS[step]
    }

    private fun currentControllerVolume(): Int {
        val step = _uiState.value.volumeStep.coerceIn(0, CONTROLLER_VOLUME_STEPS.lastIndex)
        return CONTROLLER_VOLUME_STEPS[step]
    }

    private fun startSpeakerKeepAlive() {
        speakerKeepAliveJob?.cancel()
        speakerKeepAliveJob = scope.launch(Dispatchers.IO) {
            while (speakerKeepAliveJob?.isActive == true) {
                runSpeakerHid()
                SystemClock.sleep(90)
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

        // Using a slightly smaller buffer for background stability
        val desiredBufferBytes = maxOf(minBufferBytes, 32 * 1024)

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(captureConfig)
            .setAudioFormat(format)
            .setBufferSizeInBytes(desiredBufferBytes)
            .build()
    }

    private fun runSpeakerHid(): String {
        val controller = ensureController() ?: return "Nem találok USB-s DualSense kontrollert."

        val controllerVolume = currentControllerVolume()

        val report = ByteArray(DS_OUTPUT_REPORT_USB_SIZE)

        report[0] = DS_OUTPUT_REPORT_USB.toByte()
        report[1] = 0xB0.toByte()
        report[2] = 0x82.toByte()
        report[3] = 0x00
        report[4] = 0x00
        report[5] = 0x7F.toByte()
        report[6] = controllerVolume.toByte()
        report[7] = 0x40.toByte()
        report[8] = 0x30.toByte()
        report[9] = 0x00
        report[10] = 0x00
        report[39] = 0x03

        val ok1 = controller.send(report)
        SystemClock.sleep(20)
        val ok2 = controller.send(report)

        val connected = ok1 && ok2
        setControllerConnected(connected)

        return if (connected) {
            "SPK HID OK | vol=0x${controllerVolume.toString(16)}"
        } else {
            "SPK HID fail"
        }
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
        setControllerConnected(controller != null)
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
            setLog("Nem találok USB-s DualSense kontrollert.")
            return
        }

        if (usbManager.hasPermission(device)) {
            val controller = connectToDualSense(device)
            if (controller != null) {
                setControllerConnected(true)
                setLog("Kontroller automatikusan csatlakoztatva ✔")
            } else {
                setControllerConnected(false)
                setLog("A kontroller megvan, de az auto-connect sikertelen.")
            }
        } else {
            requestPermission(device)
            setControllerConnected(false)
            setLog("USB engedély kérve a kontrollerhez...")
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

    private fun setLog(text: String) {
        _uiState.update { it.copy(logText = text) }
    }

    private fun setControllerConnected(connected: Boolean) {
        _uiState.update { it.copy(controllerConnected = connected) }
    }
}