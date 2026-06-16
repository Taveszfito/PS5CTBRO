package com.DueBoysenberry1226.ps5ctbro.ui.mictest

enum class MicPlaybackTarget {
    PHONE,
    CONTROLLER
}

data class MicTestUiState(
    val durationSeconds: Int = 5,
    val playbackTarget: MicPlaybackTarget = MicPlaybackTarget.PHONE,
    val isRecording: Boolean = false,
    val isPlaying: Boolean = false,
    val hasRecording: Boolean = false,
    val recordedDurationMs: Long = 0L,
    val level: Float = 0f,
    val playbackProgress: Float = 0f,
    val waveform: List<Float> = emptyList(),
    val logText: String = ""
)
