package com.DueBoysenberry1226.ps5ctbro.audio

data class AudioUiState(
    val logText: String = "Ready | Hangerő: 7/10",
    val isStreaming: Boolean = false,
    val controllerConnected: Boolean = false,
    val volumeStep: Int = 6,
    val audioGain: Float = 1.0f,
    val routeCh1: Boolean = false,
    val routeCh2: Boolean = true,
    val routeCh3: Boolean = false,
    val routeCh4: Boolean = false,
    val mutePhoneWhileStreaming: Boolean = false,
    val hardwareVolumeButtonsControlController: Boolean = false
)