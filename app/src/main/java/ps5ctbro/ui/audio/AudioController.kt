package com.DueBoysenberry1226.ps5ctbro.audio

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

interface AudioController {
    val uiState: StateFlow<AudioUiState>

    fun createScreenCaptureIntent(): Intent
    suspend fun applySpeakerRoute()
    suspend fun startSystemAudioStreaming(resultCode: Int, data: Intent)
    fun stopSystemAudioStreaming()
    fun onCapturePermissionDenied()
    fun onRecordAudioPermissionDenied()
    fun onUnsupportedAndroidVersion()
    fun setVolumeStep(step: Int)
    fun setChannelEnabled(channel: Int, enabled: Boolean)

    fun setMutePhoneWhileStreaming(enabled: Boolean)
    fun setHardwareVolumeButtonsControlController(enabled: Boolean)
    fun handleHardwareVolumeButton(direction: Int): Boolean

    fun release()
}