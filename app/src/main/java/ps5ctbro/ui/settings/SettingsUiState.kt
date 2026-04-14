package com.DueBoysenberry1226.ps5ctbro.ui.settings

data class SettingsUiState(
    val currentLanguage: String = "auto", // "auto", "en", "hu"
    val appVersion: String = "",
    val audioGain: Float = 1.0f,
    val showLogWindows: Boolean = false
)
