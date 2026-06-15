package com.DueBoysenberry1226.ps5ctbro.ui.settings

import com.DueBoysenberry1226.ps5ctbro.ui.connection.ControllerConnectionType

data class SettingsUiState(
    val currentLanguage: String = "auto", // "auto", "en", "hu"
    val appVersion: String = "",
    val audioGain: Float = 1.0f,
    val showLogWindows: Boolean = false,
    val byteTestUnlocked: Boolean = false,
    val byteTestUnlockTapCount: Int = 0,
    val byteTestNotes: Map<String, String> = emptyMap(),
    val byteTestSendValues: Map<String, String> = emptyMap(),
    val byteTestSendLog: String = "",
    val byteTestSequences: List<ByteTestSequence> = emptyList(),
    val byteTestPlayingSequenceId: String? = null,
    val controllerInfo: ControllerInfo? = null
)

data class ByteTestSequence(
    val id: String,
    val name: String,
    val mode: ByteTestSequenceMode = ByteTestSequenceMode.SERIES,
    val commands: List<ByteTestCommand>
)

enum class ByteTestSequenceMode {
    SERIES,
    FULL_REPORT
}

data class ByteTestCommand(
    val index: Int,
    val value: String
)

data class ControllerInfo(
    val isConnected: Boolean = false,
    val connectionType: ControllerConnectionType = ControllerConnectionType.NONE,
    val deviceName: String? = null,
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
