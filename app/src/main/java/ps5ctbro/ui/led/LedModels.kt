package com.DueBoysenberry1226.ps5ctbro.ui.led

import kotlinx.coroutines.flow.StateFlow

enum class LedEffect(val title: String) {
    OFF("Ki"),
    STATIC("Statikus"),
    BREATH("Breath"),
    COLOR_CYCLE("Color Cycle")
}

enum class PlayerLedBrightness(
    val title: String,
    val rawValue: Int
) {
    LOW("Alacsony", 2),
    MEDIUM("Közepes", 1),
    HIGH("Magas", 0)
}

data class LedColor(
    val red: Int = 0,
    val green: Int = 114,
    val blue: Int = 255
)

data class LedConfig(
    val effect: LedEffect = LedEffect.STATIC,
    val color: LedColor = LedColor(),
    val lightbarBrightnessPercent: Int = 100,
    val animationSpeedPercent: Int = 50,
    val playerLedBrightness: PlayerLedBrightness = PlayerLedBrightness.HIGH,
    val playerLedMask: Int = 0b00100,
    val micLedEnabled: Boolean = false
)

data class LedUiState(
    val controllerConnected: Boolean = false,
    val config: LedConfig = LedConfig(),
    val logText: String = "LED vezérlés készen áll."
)

interface LedController {
    val uiState: StateFlow<LedUiState>

    fun onScreenVisible()
    fun onScreenHidden()
    fun updateConfig(config: LedConfig)
    fun applyCurrentState()
    fun refreshConnection()
    fun resetToDefault()
    fun release()
}