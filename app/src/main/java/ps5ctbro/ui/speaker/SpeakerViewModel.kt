package com.DueBoysenberry1226.ps5ctbro.ui.speaker

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.DueBoysenberry1226.ps5ctbro.audio.AudioController
import com.DueBoysenberry1226.ps5ctbro.audio.AudioControllerImpl
import com.DueBoysenberry1226.ps5ctbro.audio.AudioUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SpeakerViewModel(application: Application) : AndroidViewModel(application) {

    private val controller: AudioController = AudioControllerImpl.getInstance(application)

    val uiState: StateFlow<AudioUiState> = controller.uiState

    fun createScreenCaptureIntent(): Intent {
        return controller.createScreenCaptureIntent()
    }

    fun applySpeakerRoute() {
        viewModelScope.launch {
            controller.applySpeakerRoute()
        }
    }

    fun onCapturePermissionGranted(resultCode: Int, data: Intent) {
        viewModelScope.launch {
            controller.startSystemAudioStreaming(getApplication(), resultCode, data)
        }
    }

    fun onCapturePermissionDenied() {
        controller.onCapturePermissionDenied()
    }

    fun onRecordAudioPermissionDenied() {
        controller.onRecordAudioPermissionDenied()
    }

    fun onUnsupportedAndroidVersion() {
        controller.onUnsupportedAndroidVersion()
    }

    fun stopStreaming() {
        controller.stopSystemAudioStreaming()
    }

    fun setVolumeStep(step: Int) {
        controller.setVolumeStep(step)
    }

    fun setChannelEnabled(channel: Int, enabled: Boolean) {
        controller.setChannelEnabled(channel, enabled)
    }

    fun setMutePhoneWhileStreaming(enabled: Boolean) {
        controller.setMutePhoneWhileStreaming(enabled)
    }

    fun setHardwareVolumeButtonsControlController(enabled: Boolean) {
        controller.setHardwareVolumeButtonsControlController(enabled)
    }

    fun handleHardwareVolumeButton(direction: Int): Boolean {
        return controller.handleHardwareVolumeButton(direction)
    }

    fun onScreenVisible() {
        controller.onScreenVisible()
    }

    fun onScreenHidden() {
        controller.onScreenHidden()
    }

    override fun onCleared() {
        super.onCleared()
    }
}
