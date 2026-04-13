package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.audio.AudioUiState
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSwitchRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRow

@Composable
fun SpeakerScreen(
    uiState: AudioUiState,
    onStartStreamClick: () -> Unit,
    onStopStreamClick: () -> Unit,
    onApplySpeakerRouteClick: () -> Unit,
    onVolumeStepChanged: (Int) -> Unit,
    onRouteCh1Changed: (Boolean) -> Unit,
    onRouteCh2Changed: (Boolean) -> Unit,
    onRouteCh3Changed: (Boolean) -> Unit,
    onRouteCh4Changed: (Boolean) -> Unit,
    onMutePhoneWhileStreamingChanged: (Boolean) -> Unit,
    onHardwareVolumeButtonsControlControllerChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(
            controllerConnected = uiState.controllerConnected,
            isStreaming = uiState.isStreaming,
            volumeStep = uiState.volumeStep
        )

        ActionsCard(
            isStreaming = uiState.isStreaming,
            onStartStreamClick = onStartStreamClick,
            onStopStreamClick = onStopStreamClick,
            onApplySpeakerRouteClick = onApplySpeakerRouteClick
        )

        ChannelRoutesCard(
            routeCh1 = uiState.routeCh1,
            routeCh2 = uiState.routeCh2,
            routeCh3 = uiState.routeCh3,
            routeCh4 = uiState.routeCh4,
            onRouteCh1Changed = onRouteCh1Changed,
            onRouteCh2Changed = onRouteCh2Changed,
            onRouteCh3Changed = onRouteCh3Changed,
            onRouteCh4Changed = onRouteCh4Changed
        )

        VolumeCard(
            volumeStep = uiState.volumeStep,
            mutePhoneWhileStreaming = uiState.mutePhoneWhileStreaming,
            hardwareVolumeButtonsControlController = uiState.hardwareVolumeButtonsControlController,
            onVolumeStepChanged = onVolumeStepChanged,
            onMutePhoneWhileStreamingChanged = onMutePhoneWhileStreamingChanged,
            onHardwareVolumeButtonsControlControllerChanged =
                onHardwareVolumeButtonsControlControllerChanged
        )

        LogCard(logText = uiState.logText)
    }
}

@Composable
private fun StatusCard(
    controllerConnected: Boolean,
    isStreaming: Boolean,
    volumeStep: Int
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    SectionCard(
        title = stringResource(R.string.card_title_status),
        titleTrailing = {
            IconButton(onClick = { showInfoDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Status info",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        StatusRow(
            label = stringResource(R.string.label_controller),
            value = if (controllerConnected) {
                stringResource(R.string.status_connected)
            } else {
                stringResource(R.string.status_disconnected)
            }
        )
        StatusRow(
            label = stringResource(R.string.label_stream),
            value = if (isStreaming) {
                stringResource(R.string.status_running)
            } else {
                stringResource(R.string.status_stopped)
            }
        )
        StatusRow(
            label = stringResource(R.string.label_volume),
            value = stringResource(R.string.label_volume_level, volumeStep)
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(text = "OK")
                }
            },
            title = {
                Text(text = stringResource(R.string.dialog_info_title))
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_info_body),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        )
    }
}

@Composable
private fun ActionsCard(
    isStreaming: Boolean,
    onStartStreamClick: () -> Unit,
    onStopStreamClick: () -> Unit,
    onApplySpeakerRouteClick: () -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_main_actions)) {
        Button(
            onClick = {
                if (isStreaming) onStopStreamClick() else onStartStreamClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isStreaming) {
                    stringResource(R.string.button_stop_stream)
                } else {
                    stringResource(R.string.button_start_stream)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onApplySpeakerRouteClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.button_apply_speaker_route))
        }
    }
}

@Composable
private fun ChannelRoutesCard(
    routeCh1: Boolean,
    routeCh2: Boolean,
    routeCh3: Boolean,
    routeCh4: Boolean,
    onRouteCh1Changed: (Boolean) -> Unit,
    onRouteCh2Changed: (Boolean) -> Unit,
    onRouteCh3Changed: (Boolean) -> Unit,
    onRouteCh4Changed: (Boolean) -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_audio_channels)) {
        AppSwitchRow(
            label = stringResource(R.string.channel_1),
            checked = routeCh1,
            onCheckedChange = onRouteCh1Changed
        )

        AppSwitchRow(
            label = stringResource(R.string.channel_speaker),
            checked = routeCh2,
            onCheckedChange = onRouteCh2Changed
        )

        AppSwitchRow(
            label = stringResource(R.string.channel_vibration_left),
            checked = routeCh3,
            onCheckedChange = onRouteCh3Changed
        )

        AppSwitchRow(
            label = stringResource(R.string.channel_vibration_right),
            checked = routeCh4,
            onCheckedChange = onRouteCh4Changed
        )
    }
}

@Composable
private fun VolumeCard(
    volumeStep: Int,
    mutePhoneWhileStreaming: Boolean,
    hardwareVolumeButtonsControlController: Boolean,
    onVolumeStepChanged: (Int) -> Unit,
    onMutePhoneWhileStreamingChanged: (Boolean) -> Unit,
    onHardwareVolumeButtonsControlControllerChanged: (Boolean) -> Unit
) {
    val isDistorted = volumeStep >= 8

    SectionCard(
        title = stringResource(R.string.label_volume)
    ) {
        AppSliderRow(
            label = stringResource(R.string.label_volume),
            value = volumeStep.toFloat(),
            onValueChange = { rawValue ->
                onVolumeStepChanged(rawValue.roundToInt().coerceIn(0, 10))
            },
            valueRange = 0f..10f,
            steps = 9,
            valueDisplay = volumeStep.toString(),
            isError = isDistorted
        )

        if (isDistorted) {
            Text(
                text = stringResource(R.string.msg_volume_distortion),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onVolumeStepChanged((volumeStep - 1).coerceAtLeast(0)) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.button_vol_minus))
            }

            Button(
                onClick = { onVolumeStepChanged((volumeStep + 1).coerceAtMost(10)) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.button_vol_plus))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(8.dp))

        AppSwitchRow(
            label = stringResource(R.string.label_mute_phone),
            checked = mutePhoneWhileStreaming,
            onCheckedChange = onMutePhoneWhileStreamingChanged
        )

        AppSwitchRow(
            label = stringResource(R.string.label_hw_volume_control),
            checked = hardwareVolumeButtonsControlController,
            onCheckedChange = onHardwareVolumeButtonsControlControllerChanged
        )
    }
}

@Composable
private fun LogCard(logText: String) {
    SectionCard(title = stringResource(R.string.card_title_log)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = logText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
