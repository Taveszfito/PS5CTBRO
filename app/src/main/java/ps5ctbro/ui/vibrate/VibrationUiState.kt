package com.DueBoysenberry1226.ps5ctbro.ui.vibrate

data class VibrationUiState(
    val controllerConnected: Boolean = false,
    val strengthLeftPercent: Int = 100,
    val strengthRightPercent: Int = 100,
    val durationSeconds: Int = 1,
    val isInfinite: Boolean = false,
    val isLeftActive: Boolean = false,
    val isRightActive: Boolean = false,
    val logText: String = ""
)
