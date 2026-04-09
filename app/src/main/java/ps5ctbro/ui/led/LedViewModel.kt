package com.DueBoysenberry1226.ps5ctbro.ui.led

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class LedViewModel(application: Application) : AndroidViewModel(application) {

    private val controller: LedController = LedControllerImpl(application)

    val uiState: StateFlow<LedUiState> = controller.uiState

    fun onScreenVisible() {
        controller.onScreenVisible()
    }

    fun onScreenHidden() {
        controller.onScreenHidden()
    }

    fun updateConfig(config: LedConfig) {
        controller.updateConfig(config)
    }

    fun applyCurrentState() {
        controller.applyCurrentState()
    }

    fun refreshConnection() {
        controller.refreshConnection()
    }

    fun resetToDefault() {
        controller.resetToDefault()
    }

    override fun onCleared() {
        super.onCleared()
        controller.release()
    }
}