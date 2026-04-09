package com.DueBoysenberry1226.ps5ctbro.adaptive

enum class AdaptiveTriggerEffect {
    OFF,
    RESISTANCE,
    VIBRATION
}

enum class TriggerSide {
    LEFT,
    RIGHT
}

data class AdaptiveTriggerConfig(
    val effect: AdaptiveTriggerEffect = AdaptiveTriggerEffect.OFF,
    val startPercent: Int = 20,
    val endPercent: Int = 80,
    val strengthPercent: Int = 70,
    val speedPercent: Int = 50
)