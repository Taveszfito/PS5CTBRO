package com.DueBoysenberry1226.ps5ctbro.ui.inputtest

import com.DueBoysenberry1226.ps5ctbro.audio.TouchpadPoint
import kotlinx.coroutines.flow.StateFlow

data class StickState(
    val rawX: Int = 128,
    val rawY: Int = 128,
    val percentX: Int = 0,
    val percentY: Int = 0
)

data class TriggerState(
    val rawValue: Int = 0,
    val percent: Int = 0
)

data class InputTestUiState(
    val controllerConnected: Boolean = false,
    val leftStick: StickState = StickState(),
    val rightStick: StickState = StickState(),
    val l2: TriggerState = TriggerState(),
    val r2: TriggerState = TriggerState(),
    val pressedButtons: List<String> = emptyList(),
    val logText: String = "Input Test készen áll.",
    val rawReportInfo: String = "-",
    val touch1: TouchpadPoint = TouchpadPoint(),
    val touch2: TouchpadPoint = TouchpadPoint()
)

interface InputTestController {
    val uiState: StateFlow<InputTestUiState>

    fun onScreenVisible()
    fun onScreenHidden()
    fun refreshConnection()
    fun release()
}