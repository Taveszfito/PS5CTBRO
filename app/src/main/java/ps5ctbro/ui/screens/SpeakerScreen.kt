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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.audio.AudioUiState

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
    SectionCard(title = "Állapot") {
        StatusRow(
            label = "Kontroller",
            value = if (controllerConnected) "Csatlakoztatva" else "Nincs csatlakoztatva"
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
        StatusRow(
            label = "Stream",
            value = if (isStreaming) "Fut" else "Áll"
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
        StatusRow(
            label = "Hangerő",
            value = "${volumeStep + 1}/10"
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
    SectionCard(title = "Fő műveletek") {
        Button(
            onClick = {
                if (isStreaming) onStopStreamClick() else onStartStreamClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isStreaming) "STREAM LEÁLLÍTÁSA" else "STREAM INDÍTÁSA")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onApplySpeakerRouteClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("SPEAKER ROUTE ALKALMAZÁSA")
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
    SectionCard(title = "Audio csatornák") {
        ChannelToggleRow(
            title = "1-es csatorna.",
            checked = routeCh1,
            onCheckedChange = onRouteCh1Changed
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        ChannelToggleRow(
            title = "Hangszóró",
            checked = routeCh2,
            onCheckedChange = onRouteCh2Changed
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        ChannelToggleRow(
            title = "Bal rezgőmotor",
            checked = routeCh3,
            onCheckedChange = onRouteCh3Changed
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        ChannelToggleRow(
            title = "Jobb rezgőmotor",
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
    var showInfoDialog by remember { mutableStateOf(false) }

    SectionCard(
        title = "Hangerő",
        titleTrailing = {
            IconButton(
                onClick = { showInfoDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Hangerő információ"
                )
            }
        }
    ) {
        Text(
            text = "Szint: ${volumeStep + 1}/10",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(12.dp))

        Slider(
            value = volumeStep.toFloat(),
            onValueChange = { onVolumeStepChanged(it.toInt()) },
            valueRange = 0f..9f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onVolumeStepChanged(volumeStep - 1) },
                modifier = Modifier.weight(1f)
            ) {
                Text("VOL -")
            }

            Button(
                onClick = { onVolumeStepChanged(volumeStep + 1) },
                modifier = Modifier.weight(1f)
            ) {
                Text("VOL +")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        ChannelToggleRow(
            title = "Telefon némítása stream közben",
            checked = mutePhoneWhileStreaming,
            onCheckedChange = onMutePhoneWhileStreamingChanged
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        ChannelToggleRow(
            title = "Hangerőgombok a kontrollert állítsák",
            checked = hardwareVolumeButtonsControlController,
            onCheckedChange = onHardwareVolumeButtonsControlControllerChanged
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text("Infó")
            },
            text = {
                Text("Ha egy általános jack csatlakozót dugsz a kontroller csatlakozójába a lejátszott hangerő a külső hangszórón sokkal hangosabb lesz.")
            },
            confirmButton = {
                TextButton(
                    onClick = { showInfoDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun LogCard(logText: String) {
    SectionCard(title = "Log") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = logText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    titleTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )

                if (titleTrailing != null) {
                    titleTrailing()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        AssistChip(
            onClick = {},
            label = { Text(value) },
            colors = AssistChipDefaults.assistChipColors()
        )
    }
}

@Composable
private fun ChannelToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}