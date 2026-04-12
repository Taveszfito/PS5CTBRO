package com.DueBoysenberry1226.ps5ctbro.adaptive

import androidx.annotation.StringRes
import com.DueBoysenberry1226.ps5ctbro.R

enum class AdaptiveTriggerEffect(@StringRes val titleRes: Int) {
    OFF(R.string.led_effect_off),
    RESISTANCE(R.string.effect_resistance),
    VIBRATION(R.string.effect_vibration)
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