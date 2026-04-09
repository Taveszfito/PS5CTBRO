package com.DueBoysenberry1226.ps5ctbro.ui.adaptive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggerConfig
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggerController
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggerControllerImpl
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggersUiState
import com.DueBoysenberry1226.ps5ctbro.adaptive.TriggerSide
import kotlinx.coroutines.flow.StateFlow

class AdaptiveTriggersViewModel(application: Application) : AndroidViewModel(application) {

    private val controller: AdaptiveTriggerController = AdaptiveTriggerControllerImpl(application)

    val uiState: StateFlow<AdaptiveTriggersUiState> = controller.uiState

    fun onScreenVisible() {
        controller.onScreenVisible()
    }

    fun onScreenHidden() {
        controller.onScreenHidden()
    }

    fun updateTriggerConfig(side: TriggerSide, config: AdaptiveTriggerConfig) {
        controller.updateTriggerConfig(side, config)
    }

    fun applyCurrentState() {
        controller.applyCurrentState()
    }

    fun refreshConnection() {
        controller.refreshConnection()
    }

    fun resetTriggers() {
        controller.resetTriggers()
    }

    override fun onCleared() {
        super.onCleared()
        controller.release()
    }
}