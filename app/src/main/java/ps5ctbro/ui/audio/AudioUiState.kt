package com.DueBoysenberry1226.ps5ctbro.audio

import android.media.session.MediaSession

data class AudioUiState(
    val logText: String = "Ready | Hangerő: 3/10",
    val isStreaming: Boolean = false,
    val controllerConnected: Boolean = false,
    val volumeStep: Int = 3,
    val audioGain: Float = 0.3f,
    val routeCh1: Boolean = false,
    val routeCh2: Boolean = true,
    val routeCh3: Boolean = false,
    val routeCh4: Boolean = false,
    val mutePhoneWhileStreaming: Boolean = true,
    val hardwareVolumeButtonsControlController: Boolean = true,
    val sessionToken: MediaSession.Token? = null
)
