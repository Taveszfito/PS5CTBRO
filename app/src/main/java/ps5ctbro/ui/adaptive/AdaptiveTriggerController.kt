package com.DueBoysenberry1226.ps5ctbro.adaptive

import kotlinx.coroutines.flow.StateFlow

interface AdaptiveTriggerController {
    val uiState: StateFlow<AdaptiveTriggersUiState>

    fun onScreenVisible()
    fun onScreenHidden()

    fun updateTriggerConfig(side: TriggerSide, config: AdaptiveTriggerConfig)
    fun applyCurrentState()
    fun refreshConnection()
    fun resetTriggers()
    fun release()
}