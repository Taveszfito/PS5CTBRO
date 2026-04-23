package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.audio.AudioUiState
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import kotlin.math.roundToInt

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
    onHardwareVolumeButtonsControlControllerChanged: (Boolean) -> Unit,
    showLogs: Boolean
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

        if (showLogs) {
            LogCard(logText = uiState.logText)
        }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.SportsEsports,
                shortLabel = stringResource(R.string.label_controller),
                value = if (controllerConnected) {
                    stringResource(R.string.status_connected)
                } else {
                    stringResource(R.string.status_disconnected)
                }
            )

            StatusTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.WifiTethering,
                shortLabel = stringResource(R.string.label_stream),
                value = if (isStreaming) {
                    stringResource(R.string.status_running)
                } else {
                    stringResource(R.string.status_stopped)
                }
            )

            StatusTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.VolumeUp,
                shortLabel = stringResource(R.string.label_volume),
                value = "$volumeStep/10"
            )
        }
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
private fun StatusTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    shortLabel: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF183055),
        border = BorderStroke(1.dp, Color(0xFF2B4C7E)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFB7D1FF),
                modifier = Modifier.size(21.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionTileButton(
                modifier = Modifier.weight(1f),
                icon = if (isStreaming) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                text = if (isStreaming) {
                    stringResource(R.string.button_stop_stream)
                } else {
                    stringResource(R.string.button_start_stream)
                },
                onClick = {
                    if (isStreaming) onStopStreamClick() else onStartStreamClick()
                }
            )

            ActionTileButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.SwapHoriz,
                text = stringResource(R.string.button_apply_speaker_route),
                onClick = onApplySpeakerRouteClick
            )
        }
    }
}

@Composable
private fun ActionTileButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
    SectionCard(
        title = stringResource(R.string.card_title_audio_channels),
        titleTrailing = {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChannelTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.GraphicEq,
                line1 = "Channel",
                line2 = "1",
                checked = routeCh1,
                onCheckedChange = onRouteCh1Changed
            )
            ChannelTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.VolumeUp,
                line1 = "Speaker",
                line2 = "",
                checked = routeCh2,
                onCheckedChange = onRouteCh2Changed
            )
            ChannelTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Waves,
                line1 = "Left Vib",
                line2 = "",
                checked = routeCh3,
                onCheckedChange = onRouteCh3Changed
            )
            ChannelTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Waves,
                line1 = "Right Vib",
                line2 = "",
                checked = routeCh4,
                onCheckedChange = onRouteCh4Changed
            )
        }
    }
}

@Composable
private fun ChannelTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    line1: String,
    line2: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1B345C),
        border = BorderStroke(1.dp, Color(0xFF315487)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(146.dp)
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFB7D1FF),
                modifier = Modifier.size(23.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier.height(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = line1,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (line2.isNotEmpty()) line2 else " ",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.74f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
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

    SectionCard(title = stringResource(R.string.label_volume)) {
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
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }

            Button(
                onClick = { onVolumeStepChanged((volumeStep + 1).coerceAtMost(10)) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BottomToggleCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.VolumeOff,
            line1 = "Mute",
            line2 = "phone",
            checked = mutePhoneWhileStreaming,
            onCheckedChange = onMutePhoneWhileStreamingChanged
        )

        BottomToggleCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Settings,
            line1 = "HW vol.",
            line2 = "buttons",
            checked = hardwareVolumeButtonsControlController,
            onCheckedChange = onHardwareVolumeButtonsControlControllerChanged
        )
    }
}

@Composable
private fun BottomToggleCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    line1: String,
    line2: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF9BC0FF),
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = line1,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = line2,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.74f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
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