package com.DueBoysenberry1226.ps5ctbro.audio

data class GameModeTuning(
    val adaptiveStrengthEnabled: Boolean = true,
    val preciseReactionEnabled: Boolean = true,
    val bassFollow: Float = 0.055f,
    val transientFastFollow: Float = 0.24f,
    val transientSlowFollow: Float = 0.022f,
    val adaptiveFloor: Float = 0.05f,
    val adaptiveCeiling: Float = 1.15f,
    val lowThreshold: Float = 2600f,
    val transientThreshold: Float = 850f,
    val strongLowMix: Float = 0.62f,
    val transientLowMix: Float = 0.34f,
    val quietLowMix: Float = 0.03f,
    val precisePunch: Float = 1.15f,
    val softPunch: Float = 0.32f,
    val punchPolarityScale: Float = 0.95f,
    val softClipDenominator: Float = 18_000f
) {
    companion object {
        val DEFAULT = GameModeTuning()
    }
}

data class GameModePreset(
    val id: String,
    val appPackageName: String,
    val appLabel: String,
    val tuning: GameModeTuning
)

