package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.mictest.MicPlaybackTarget
import com.DueBoysenberry1226.ps5ctbro.ui.mictest.MicTestUiState
import kotlin.math.roundToInt

@Composable
fun MicTestScreen(
    uiState: MicTestUiState,
    onDurationChanged: (Int) -> Unit,
    onPlaybackTargetChanged: (MicPlaybackTarget) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlay: () -> Unit,
    onStopPlayback: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(title = stringResource(R.string.card_title_mic_recording)) {
            AppSliderRow(
                label = stringResource(R.string.label_recording_duration),
                value = uiState.durationSeconds.toFloat(),
                onValueChange = { onDurationChanged(it.roundToInt()) },
                valueRange = 1f..30f,
                steps = 28,
                valueDisplay = stringResource(R.string.label_seconds_value, uiState.durationSeconds)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MicActionButton(
                    text = if (uiState.isRecording) {
                        stringResource(R.string.button_stop_recording)
                    } else {
                        stringResource(R.string.button_record)
                    },
                    icon = if (uiState.isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    onClick = {
                        if (uiState.isRecording) onStopRecording() else onStartRecording()
                    },
                    modifier = Modifier.weight(1f),
                    danger = uiState.isRecording,
                    enabled = !uiState.isPlaying
                )

                MicActionButton(
                    text = if (uiState.isPlaying) {
                        stringResource(R.string.button_stop_playback)
                    } else {
                        stringResource(R.string.button_play)
                    },
                    icon = if (uiState.isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                    onClick = {
                        if (uiState.isPlaying) onStopPlayback() else onPlay()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.hasRecording && !uiState.isRecording
                )
            }
        }

        SectionCard(title = stringResource(R.string.card_title_playback_target)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlaybackTargetChip(
                    selected = uiState.playbackTarget == MicPlaybackTarget.PHONE,
                    text = stringResource(R.string.label_phone_speaker),
                    icon = Icons.Outlined.PhoneAndroid,
                    onClick = { onPlaybackTargetChanged(MicPlaybackTarget.PHONE) },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRecording && !uiState.isPlaying
                )

                PlaybackTargetChip(
                    selected = uiState.playbackTarget == MicPlaybackTarget.CONTROLLER,
                    text = stringResource(R.string.label_controller_speaker),
                    icon = Icons.Outlined.Headset,
                    onClick = { onPlaybackTargetChanged(MicPlaybackTarget.CONTROLLER) },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRecording && !uiState.isPlaying
                )
            }
        }

        SectionCard(title = stringResource(R.string.card_title_audio_visualizer)) {
            WaveformView(
                waveform = uiState.waveform,
                level = uiState.level,
                active = uiState.isRecording || uiState.isPlaying
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.label_recorded_duration, uiState.recordedDurationMs / 1000f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.logText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.logText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MicActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) Color(0xFFFF6B7C) else MaterialTheme.colorScheme.primary,
            contentColor = if (danger) Color.White else MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PlaybackTargetChip(
    selected: Boolean,
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text = text, fontWeight = FontWeight.SemiBold) },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF2B4C7E)),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF2B4C7E),
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White,
            containerColor = Color(0xFF14284A),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun WaveformView(
    waveform: List<Float>,
    level: Float,
    active: Boolean
) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val background = Color(0xFF102345)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = BorderStroke(1.dp, Color(0xFF2B4C7E))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (waveform.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = muted,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = stringResource(R.string.msg_no_mic_recording),
                        color = muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Canvas(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                val centerY = size.height / 2f
                val barCount = waveform.size.coerceAtLeast(1)
                val gap = 5.dp.toPx()
                val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(2.dp.toPx())

                waveform.forEachIndexed { index, peak ->
                    val x = index * (barWidth + gap) + barWidth / 2f
                    val shapedPeak = peak.coerceIn(0f, 1f)
                    val barHeight = (size.height * (0.08f + shapedPeak * 0.92f)).coerceAtLeast(6.dp.toPx())
                    drawLine(
                        color = if (active) {
                            primary
                        } else {
                            primary.copy(alpha = 0.55f)
                        },
                        start = Offset(x, centerY - barHeight / 2f),
                        end = Offset(x, centerY + barHeight / 2f),
                        strokeWidth = barWidth,
                        cap = StrokeCap.Round
                    )
                }

                val meterWidth = size.width * level.coerceIn(0f, 1f)
                drawLine(
                    color = Color(0xFF67D98B),
                    start = Offset(0f, size.height - 4.dp.toPx()),
                    end = Offset(meterWidth, size.height - 4.dp.toPx()),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
