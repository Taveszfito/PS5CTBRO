package com.DueBoysenberry1226.ps5ctbro.ui.led

import androidx.annotation.StringRes
import com.DueBoysenberry1226.ps5ctbro.R
import kotlinx.coroutines.flow.StateFlow

enum class LedEffect(@StringRes val titleRes: Int) {
    OFF(R.string.led_effect_off),
    STATIC(R.string.led_effect_static),
    BREATH(R.string.led_effect_breath),
    COLOR_CYCLE(R.string.led_effect_color_cycle),
    MUSIC_REACTIVE(R.string.led_effect_music_reactive)
}

enum class PlayerLedBrightness(
    @StringRes val titleRes: Int,
    val rawValue: Int
) {
    LOW(R.string.brightness_low, 2),
    MEDIUM(R.string.brightness_medium, 1),
    HIGH(R.string.brightness_high, 0)
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
    val logText: String = "" // This should probably be localized in the ViewModel or just kept as is if it's dynamic
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