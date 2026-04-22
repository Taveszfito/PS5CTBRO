package com.DueBoysenberry1226.ps5ctbro.ui.settings

data class SettingsUiState(
    val currentLanguage: String = "auto", // "auto", "en", "hu"
    val appVersion: String = "",
    val audioGain: Float = 1.0f,
    val showLogWindows: Boolean = false,
    val controllerInfo: ControllerInfo? = null
)

data class ControllerInfo(
    val isConnected: Boolean = false,
    val isWired: Boolean = true,
    val batteryLevel: Int = 0,
    val serialNumber: String = "Not queried yet",
    val btAddress: String = "Not queried yet",
    val firmwareVersion: String = "Not queried yet",
    val firmwareType: String = "Not queried yet",
    val softwareSeries: String = "Not queried yet",
    val hardwareInfo: String = "Not queried yet",
    val updateVersion: String = "Not queried yet",
    val buildDate: String = "Not queried yet",
    val buildTime: String = "Not queried yet",
    val deviceInfo: String = "Not queried yet",
    val controllerColor: String = "Not queried yet"
)