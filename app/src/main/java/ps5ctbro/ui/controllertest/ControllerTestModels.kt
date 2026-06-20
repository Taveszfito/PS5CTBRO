package com.DueBoysenberry1226.ps5ctbro.ui.controllertest

data class ControllerTestUiState(
    val controllerConnected: Boolean = false,
    val testRunning: Boolean = false,
    val lightEnabled: Boolean = false,
    val rumbleEnabled: Boolean = false,
    val micLedEnabled: Boolean = false,
    val playerLedMask: Int = 0b11111,
    val red: Int = 0,
    val green: Int = 64,
    val blue: Int = 255,
    val leftRumblePercent: Int = 45,
    val rightRumblePercent: Int = 45,
    val sendIntervalMs: Int = 30,
    val outputReportsSent: Long = 0,
    val outputErrors: Long = 0,
    val inputReportsRead: Long = 0,
    val lastInputAgeMs: Long? = null,
    val leftStick: ControllerTestStickState = ControllerTestStickState(),
    val rightStick: ControllerTestStickState = ControllerTestStickState(),
    val l2Percent: Int = 0,
    val r2Percent: Int = 0,
    val pressedButtons: List<String> = emptyList(),
    val rawReportInfo: String = "",
    val logText: String = ""
)

data class ControllerTestStickState(
    val xPercent: Int = 0,
    val yPercent: Int = 0
)
