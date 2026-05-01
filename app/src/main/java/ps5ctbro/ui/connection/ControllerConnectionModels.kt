package com.DueBoysenberry1226.ps5ctbro.ui.connection

enum class ControllerConnectionType {
    NONE,
    USB,
    BLUETOOTH
}

data class ControllerConnectionUiState(
    val type: ControllerConnectionType = ControllerConnectionType.NONE,
    val deviceName: String? = null
) {
    val isConnected: Boolean
        get() = type != ControllerConnectionType.NONE

    val shortLabel: String
        get() = when (type) {
            ControllerConnectionType.USB -> "● USB"
            ControllerConnectionType.BLUETOOTH -> "● BT"
            ControllerConnectionType.NONE -> "● Offline"
        }
}