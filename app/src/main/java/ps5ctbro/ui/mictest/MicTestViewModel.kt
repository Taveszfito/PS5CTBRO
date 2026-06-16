package com.DueBoysenberry1226.ps5ctbro.ui.mictest

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.DueBoysenberry1226.ps5ctbro.audio.AudioController
import com.DueBoysenberry1226.ps5ctbro.audio.AudioControllerImpl
import com.DueBoysenberry1226.ps5ctbro.audio.NativeAudioBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

class MicTestViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val audioController: AudioController = AudioControllerImpl.getInstance(application)

    private val _uiState = MutableStateFlow(MicTestUiState())
    val uiState: StateFlow<MicTestUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var playbackJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var controllerRoute: AudioRouteSession? = null
    private var recordedPcm: ShortArray = ShortArray(0)

    private data class AudioIsoTarget(
        val interfaceNumber: Int,
        val altSetting: Int,
        val endpointAddress: Int,
        val packetSize: Int
    )

    private data class AudioRouteSession(
        val connection: UsbDeviceConnection,
        val target: AudioIsoTarget,
        val claimedInterface: UsbInterface
    )

    fun setDurationSeconds(seconds: Int) {
        _uiState.update { it.copy(durationSeconds = seconds.coerceIn(1, MAX_DURATION_SECONDS)) }
    }

    fun setPlaybackTarget(target: MicPlaybackTarget) {
        _uiState.update { it.copy(playbackTarget = target) }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _uiState.update { it.copy(logText = "Microphone permission is required.") }
            return
        }
        if (_uiState.value.isRecording) return

        stopPlayback()
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            val durationSeconds = _uiState.value.durationSeconds.coerceIn(1, MAX_DURATION_SECONDS)
            val maxSamples = SAMPLE_RATE * durationSeconds
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val readBufferSize = max(minBuffer / BYTES_PER_SAMPLE, SAMPLE_RATE / 20)
            val readBuffer = ShortArray(readBufferSize)
            val samples = ShortArray(maxSamples)
            var sampleCount = 0

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuffer, readBufferSize * BYTES_PER_SAMPLE)
                )
            } catch (t: Throwable) {
                _uiState.update { it.copy(logText = "AudioRecord error: ${t.message.orEmpty()}") }
                return@launch
            }

            audioRecord = record
            try {
                record.startRecording()
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        hasRecording = false,
                        recordedDurationMs = 0L,
                        playbackProgress = 0f,
                        waveform = emptyList(),
                        logText = "Recording..."
                    )
                }

                while (sampleCount < maxSamples && recordingJob?.isActive == true) {
                    val toRead = minOf(readBuffer.size, maxSamples - sampleCount)
                    val read = record.read(readBuffer, 0, toRead, AudioRecord.READ_BLOCKING)
                    if (read <= 0) break

                    System.arraycopy(readBuffer, 0, samples, sampleCount, read)
                    sampleCount += read

                    val level = calculateLevel(readBuffer, read)

                    _uiState.update {
                        it.copy(
                            level = level,
                            recordedDurationMs = sampleCount * 1000L / SAMPLE_RATE,
                            waveform = buildInstantBars(readBuffer, 0, read)
                        )
                    }
                }
            } finally {
                try { record.stop() } catch (_: Throwable) {}
                try { record.release() } catch (_: Throwable) {}
                audioRecord = null

                recordedPcm = samples.copyOf(sampleCount)
                val lastWindowSize = minOf(sampleCount, SAMPLE_RATE / 20)
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        hasRecording = sampleCount > 0,
                        recordedDurationMs = sampleCount * 1000L / SAMPLE_RATE,
                        level = 0f,
                        playbackProgress = 0f,
                        waveform = if (sampleCount > 0) {
                            buildInstantBars(recordedPcm, sampleCount - lastWindowSize, lastWindowSize)
                        } else {
                            emptyList()
                        },
                        logText = if (sampleCount > 0) "Recording ready." else "Recording stopped."
                    )
                }
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try { audioRecord?.stop() } catch (_: Throwable) {}
    }

    fun playRecording() {
        if (recordedPcm.isEmpty() || _uiState.value.isPlaying) return

        stopRecording()
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch(Dispatchers.IO) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

            val target = _uiState.value.playbackTarget
            if (target == MicPlaybackTarget.CONTROLLER) {
                playControllerRecordingOnIso()
                return@launch
            }

            val outputDevice = findOutputDevice(target)

            val minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuffer, SAMPLE_RATE / 10 * BYTES_PER_SAMPLE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            outputDevice?.let { track.preferredDevice = it }

            try {
                track.play()
                _uiState.update {
                    it.copy(
                        isPlaying = true,
                        playbackProgress = 0f,
                        logText = if (target == MicPlaybackTarget.CONTROLLER) {
                            "Playing on controller output..."
                        } else {
                            "Playing on phone speaker..."
                        }
                    )
                }

                var offset = 0
                val chunk = SAMPLE_RATE / 20
                while (offset < recordedPcm.size && playbackJob?.isActive == true) {
                    val count = minOf(chunk, recordedPcm.size - offset)
                    val written = track.write(recordedPcm, offset, count, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                    val level = calculateLevel(recordedPcm, offset, written)
                    offset += written
                    _uiState.update {
                        it.copy(
                            level = level,
                            waveform = buildInstantBars(recordedPcm, offset - written, written),
                            playbackProgress = offset.toFloat() / recordedPcm.size.toFloat()
                        )
                    }
                }
            } finally {
                try { track.stop() } catch (_: Throwable) {}
                try { track.release() } catch (_: Throwable) {}
                audioTrack = null
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        level = 0f,
                        playbackProgress = 0f,
                        logText = "Playback stopped."
                    )
                }
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try { audioTrack?.stop() } catch (_: Throwable) {}
        try { NativeAudioBridge.nativeIsoStreamStop() } catch (_: Throwable) {}
        closeControllerRoute()
    }

    fun onRecordAudioPermissionDenied() {
        _uiState.update { it.copy(logText = "Microphone permission denied.") }
    }

    override fun onCleared() {
        stopRecording()
        stopPlayback()
        super.onCleared()
    }

    private fun findOutputDevice(target: MicPlaybackTarget): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return when (target) {
            MicPlaybackTarget.PHONE -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            MicPlaybackTarget.CONTROLLER -> {
                devices.firstOrNull { device ->
                    device.type == AudioDeviceInfo.TYPE_USB_DEVICE &&
                            (device.productName?.contains("Wireless", ignoreCase = true) == true ||
                                    device.productName?.contains("DualSense", ignoreCase = true) == true ||
                                    device.productName?.contains("Controller", ignoreCase = true) == true)
                } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
            }
        }
    }

    private suspend fun playControllerRecordingOnIso() {
        val device = findDualSenseDevice()
        if (device == null) {
            _uiState.update { it.copy(logText = "No USB DualSense connected.") }
            return
        }
        if (!usbManager.hasPermission(device)) {
            _uiState.update { it.copy(logText = "USB permission is required for controller playback.") }
            return
        }

        try {
            audioController.applySpeakerRoute()
            delay(120)
        } catch (_: Throwable) {
        }

        val route = openAudioRoute(device)
        if (route == null) {
            _uiState.update { it.copy(logText = "Controller ISO audio route could not be opened.") }
            return
        }
        controllerRoute = route

        val startRc = try {
            NativeAudioBridge.nativeIsoStreamStop()
            NativeAudioBridge.nativeIsoSetLowLatencyMode(false)
            NativeAudioBridge.nativeIsoSetQueueLimit(20)
            NativeAudioBridge.nativeIsoStreamStart(
                route.connection.fileDescriptor,
                route.target.interfaceNumber,
                route.target.altSetting,
                route.target.endpointAddress
            )
        } catch (t: Throwable) {
            closeControllerRoute()
            _uiState.update { it.copy(logText = "Controller ISO start failed: ${t.message.orEmpty()}") }
            return
        }

        if (startRc != 0) {
            closeControllerRoute()
            _uiState.update { it.copy(logText = "Controller ISO start failed: $startRc") }
            return
        }

        _uiState.update {
            it.copy(
                isPlaying = true,
                playbackProgress = 0f,
                logText = "Playing on controller ISO speaker route..."
            )
        }

        try {
            var offset = 0
            val framesPerChunk = ISO_PAYLOAD_SIZE * ISO_PACKETS_PER_CHUNK / OUTPUT_FRAME_BYTES
            val outputBytes = ByteArray(framesPerChunk * OUTPUT_FRAME_BYTES)
            val chunkDurationMs = (framesPerChunk * 1000L / SAMPLE_RATE).coerceAtLeast(1L)
            val prebufferChunks = 6
            var queuedChunks = 0

            while (
                offset < recordedPcm.size &&
                playbackJob?.isActive == true &&
                queuedChunks < prebufferChunks
            ) {
                val count = minOf(framesPerChunk, recordedPcm.size - offset)
                monoToControllerSpeakerFrame(recordedPcm, offset, count, outputBytes)
                NativeAudioBridge.nativeIsoPushPcm(outputBytes)

                val level = calculateLevel(recordedPcm, offset, count)
                offset += count
                queuedChunks += 1
                _uiState.update {
                    it.copy(
                        level = level,
                        waveform = buildInstantBars(recordedPcm, offset - count, count),
                        playbackProgress = offset.toFloat() / recordedPcm.size.toFloat()
                    )
                }
            }

            val playbackStartMs = SystemClock.uptimeMillis()

            while (offset < recordedPcm.size && playbackJob?.isActive == true) {
                val targetSendTimeMs = playbackStartMs + (queuedChunks - prebufferChunks) * chunkDurationMs
                val waitMs = targetSendTimeMs - SystemClock.uptimeMillis()
                if (waitMs > 1L) {
                    delay(waitMs)
                }

                val count = minOf(framesPerChunk, recordedPcm.size - offset)
                monoToControllerSpeakerFrame(recordedPcm, offset, count, outputBytes)
                NativeAudioBridge.nativeIsoPushPcm(outputBytes)

                val level = calculateLevel(recordedPcm, offset, count)
                offset += count
                queuedChunks += 1
                _uiState.update {
                    it.copy(
                        level = level,
                        waveform = buildInstantBars(recordedPcm, offset - count, count),
                        playbackProgress = offset.toFloat() / recordedPcm.size.toFloat()
                    )
                }
            }
        } finally {
            try { NativeAudioBridge.nativeIsoStreamStop() } catch (_: Throwable) {}
            NativeAudioBridge.nativeIsoSetQueueLimit(20)
            closeControllerRoute()
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    level = 0f,
                    playbackProgress = 0f,
                    logText = "Playback stopped."
                )
            }
        }
    }

    private fun monoToControllerSpeakerFrame(
        input: ShortArray,
        offset: Int,
        count: Int,
        output: ByteArray
    ) {
        output.fill(0)
        var out = 0
        for (i in 0 until count) {
            val sample = input[offset + i]

            writeShortLe(output, out, 0)
            out += 2
            writeShortLe(output, out, sample)
            out += 2
            writeShortLe(output, out, 0)
            out += 2
            writeShortLe(output, out, 0)
            out += 2
        }
    }

    private fun writeShortLe(buffer: ByteArray, offset: Int, value: Short) {
        buffer[offset] = (value.toInt() and 0xFF).toByte()
        buffer[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    private fun findDualSenseDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull {
            it.vendorId == SONY_VENDOR_ID && it.productId == DUALSENSE_PRODUCT_ID
        }
    }

    private fun openAudioRoute(device: UsbDevice): AudioRouteSession? {
        val connection = try {
            usbManager.openDevice(device)
        } catch (_: SecurityException) {
            return null
        } ?: return null

        val target = findAudioIsoTarget(device)
        if (target == null) {
            connection.close()
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
            connection.close()
            return null
        }

        val claimed = try {
            connection.claimInterface(targetInterface, true)
        } catch (_: Throwable) {
            false
        }
        if (!claimed) {
            connection.close()
            return null
        }

        return AudioRouteSession(
            connection = connection,
            target = target,
            claimedInterface = targetInterface
        )
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

        return candidates
            .filter { it.altSetting != 0 }
            .maxByOrNull { it.packetSize }
            ?: candidates.maxByOrNull { it.packetSize }
    }

    private fun closeControllerRoute() {
        val route = controllerRoute ?: return
        try {
            route.connection.releaseInterface(route.claimedInterface)
        } catch (_: Throwable) {
        }
        try {
            route.connection.close()
        } catch (_: Throwable) {
        }
        controllerRoute = null
    }

    private fun calculateLevel(samples: ShortArray, count: Int): Float {
        return calculateLevel(samples, 0, count)
    }

    private fun calculateLevel(samples: ShortArray, offset: Int, count: Int): Float {
        if (count <= 0) return 0f
        var peak = 0
        for (i in offset until offset + count) {
            peak = max(peak, abs(samples[i].toInt()))
        }
        return (peak / Short.MAX_VALUE.toFloat()).coerceIn(0f, 1f)
    }

    private fun buildInstantBars(samples: ShortArray, offset: Int, count: Int): List<Float> {
        if (count <= 0 || samples.isEmpty()) return emptyList()
        val safeOffset = offset.coerceIn(0, samples.size)
        val safeCount = count.coerceAtMost(samples.size - safeOffset)
        if (safeCount <= 0) return emptyList()

        val bucketSize = (safeCount / VISUALIZER_BARS).coerceAtLeast(1)
        val peaks = MutableList(VISUALIZER_BARS) { 0f }
        for (bar in 0 until VISUALIZER_BARS) {
            val start = safeOffset + bar * bucketSize
            if (start >= safeOffset + safeCount) break
            val end = minOf(start + bucketSize, safeOffset + safeCount)
            peaks[bar] = calculateLevel(samples, start, end - start)
        }
        return peaks
    }

    private companion object {
        private const val SAMPLE_RATE = 48_000
        private const val BYTES_PER_SAMPLE = 2
        private const val MAX_DURATION_SECONDS = 30
        private const val VISUALIZER_BARS = 32
        private const val SONY_VENDOR_ID = 0x054c
        private const val DUALSENSE_PRODUCT_ID = 0x0ce6
        private const val USB_SUBCLASS_AUDIOSTREAMING = 0x02
        private const val ISO_PAYLOAD_SIZE = 384
        private const val ISO_PACKETS_PER_CHUNK = 32
        private const val OUTPUT_FRAME_BYTES = 8
    }
}
