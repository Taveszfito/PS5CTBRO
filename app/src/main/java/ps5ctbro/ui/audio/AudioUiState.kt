package com.DueBoysenberry1226.ps5ctbro.audio

import android.media.session.MediaSession

data class TouchpadPoint(
    val x: Int = 0,
    val y: Int = 0,
    val isActive: Boolean = false,
    val id: Int = 0
)

data class AudioUiState(
    val logText: String = "Ready | Hangerő: 3/10",
    val isStreaming: Boolean = false,
    val controllerConnected: Boolean = false,
    val isWired: Boolean = true,
    val batteryLevel: Int = 0,
    val serialNumber: String = "Unknown",
    val btAddress: String = "Unknown",
    val firmwareVersion: String = "Unknown",
    val firmwareType: String = "Unknown",
    val softwareSeries: String = "Unknown",
    val hardwareInfo: String = "Unknown",
    val updateVersion: String = "Unknown",
    val buildDate: String = "Unknown",
    val buildTime: String = "Unknown",
    val deviceInfo: String = "Unknown",
    val controllerColor: String = "Unknown",
    val volumeStep: Int = 3,
    val audioGain: Float = 0.3f,
    val routeCh1: Boolean = false,
    val routeCh2: Boolean = true,
    val routeCh3: Boolean = false,
    val routeCh4: Boolean = false,
    val mutePhoneWhileStreaming: Boolean = true,
    val hardwareVolumeButtonsControlController: Boolean = true,
    val sessionToken: MediaSession.Token? = null,
    val touch1: TouchpadPoint = TouchpadPoint(),
    val touch2: TouchpadPoint = TouchpadPoint()
)