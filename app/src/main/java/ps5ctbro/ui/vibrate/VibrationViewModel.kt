package com.DueBoysenberry1226.ps5ctbro.ui.vibrate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class VibrationViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = VibrationController(application)
    val uiState: StateFlow<VibrationUiState> = controller.uiState

    fun onScreenVisible() {
        controller.onScreenVisible()
    }

    fun onScreenHidden() {
        controller.onScreenHidden()
    }

    fun setStrengthLeft(strength: Int) {
        controller.setStrengthLeft(strength)
    }

    fun setStrengthRight(strength: Int) {
        controller.setStrengthRight(strength)
    }

    fun setDuration(seconds: Int) {
        controller.setDuration(seconds)
    }

    fun setInfinite(infinite: Boolean) {
        controller.setInfinite(infinite)
    }

    fun applyVibration(left: Boolean, right: Boolean) {
        controller.applyVibration(left, right)
    }

    fun stopVibration() {
        controller.stopVibration()
    }

    fun refreshConnection() {
        controller.refreshConnection()
    }

    override fun onCleared() {
        super.onCleared()
        controller.release()
    }
}
