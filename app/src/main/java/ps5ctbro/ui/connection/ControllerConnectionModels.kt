package com.DueBoysenberry1226.ps5ctbro.ui.connection

enum class ControllerConnectionType {
    NONE,
    USB,
    BLUETOOTH
}

data class ControllerConnectionUiState(
    val type: ControllerConnectionType = ControllerConnectionType.NONE,
    val deviceName: String? = null,
    val btAddress: String? = null,
    val batteryLevel: Int = -1
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