package com.DueBoysenberry1226.ps5ctbro.audio

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import kotlinx.coroutines.flow.StateFlow

interface AudioController {
    val uiState: StateFlow<AudioUiState>
    val sessionToken: MediaSession.Token?

    fun createScreenCaptureIntent(): Intent
    suspend fun applySpeakerRoute()
    suspend fun startSystemAudioStreaming(context: Context, resultCode: Int, data: Intent)
    fun stopSystemAudioStreaming()
    fun onCapturePermissionDenied()
    fun onRecordAudioPermissionDenied()
    fun onUnsupportedAndroidVersion()
    fun setVolumeStep(step: Int)
    fun setAudioGain(gain: Float)
    fun setChannelEnabled(channel: Int, enabled: Boolean)
    fun setGameMode(enabled: Boolean)
    fun updateGameModeTuning(tuning: GameModeTuning)
    fun resetGameModeTuning()
    fun setGameModeAdaptiveStrength(enabled: Boolean)
    fun setGameModePreciseReaction(enabled: Boolean)
    fun saveGameModePreset(
        presetId: String?,
        appPackageName: String,
        appLabel: String,
        tuning: GameModeTuning
    )
    fun deleteGameModePreset(id: String)
    fun applyGameModePreset(id: String)
    fun importGameModePresetFromClipboard()
    fun copyGameModePresetToClipboard(id: String)

    fun setMutePhoneWhileStreaming(enabled: Boolean)
    fun setHardwareVolumeButtonsControlController(enabled: Boolean)
    fun handleHardwareVolumeButton(direction: Int): Boolean

    fun onScreenVisible()
    fun onScreenHidden()

    fun release()
}
