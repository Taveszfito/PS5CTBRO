package com.DueBoysenberry1226.ps5ctbro.adaptive

data class AdaptiveTriggersUiState(
    val controllerConnected: Boolean = false,

    val leftTrigger: AdaptiveTriggerConfig = AdaptiveTriggerConfig(),
    val rightTrigger: AdaptiveTriggerConfig = AdaptiveTriggerConfig(),

    val leftShoulderPressed: Boolean = false,
    val rightShoulderPressed: Boolean = false,

    val leftTriggerPressedPercent: Int = 0,
    val rightTriggerPressedPercent: Int = 0,

    val logText: String = "Adaptív trigger modul készen áll."
)