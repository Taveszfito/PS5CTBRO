package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSwitchRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRow
import com.DueBoysenberry1226.ps5ctbro.ui.vibrate.VibrationUiState

@Composable
fun VibrationScreen(
    uiState: VibrationUiState,
    onStrengthLeftChanged: (Int) -> Unit,
    onStrengthRightChanged: (Int) -> Unit,
    onDurationChanged: (Int) -> Unit,
    onInfiniteChanged: (Boolean) -> Unit,
    onApplyLeft: () -> Unit,
    onApplyRight: () -> Unit,
    onStopClick: () -> Unit,
    onRefreshConnectionClick: () -> Unit,
    showLogs: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(title = stringResource(R.string.card_title_status)) {
            StatusRow(
                label = stringResource(R.string.label_controller),
                value = if (uiState.controllerConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected)
            )
        }

        SectionCard(title = stringResource(R.string.card_title_settings)) {
            val durationEnabled = !uiState.isInfinite

            // Bal Rezgés Erőssége
            AppSliderRow(
                label = stringResource(R.string.label_vibration_strength_left),
                value = uiState.strengthLeftPercent.toFloat(),
                onValueChange = { onStrengthLeftChanged(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Jobb Rezgés Erőssége
            AppSliderRow(
                label = stringResource(R.string.label_vibration_strength_right),
                value = uiState.strengthRightPercent.toFloat(),
                onValueChange = { onStrengthRightChanged(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.label_vibration_duration),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (durationEnabled) 1f else 0.38f)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 5, 10).forEach { seconds ->
                    FilterChip(
                        selected = uiState.durationSeconds == seconds && !uiState.isInfinite,
                        onClick = { if (durationEnabled) onDurationChanged(seconds) },
                        label = { Text(stringResource(when(seconds) {
                            1 -> R.string.duration_1s
                            5 -> R.string.duration_5s
                            else -> R.string.duration_10s
                        })) },
                        enabled = durationEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AppSliderRow(
                label = "",
                value = uiState.durationSeconds.toFloat(),
                onValueChange = { onDurationChanged(it.toInt()) },
                valueRange = 1f..60f,
                valueDisplay = stringResource(R.string.duration_custom, uiState.durationSeconds),
                modifier = Modifier.alphaIf(!durationEnabled)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            AppSwitchRow(
                label = stringResource(R.string.label_infinite_vibration),
                checked = uiState.isInfinite,
                onCheckedChange = onInfiniteChanged
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onApplyLeft,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.controllerConnected,
                    colors = if (uiState.isLeftActive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors()
                ) {
                    Text(stringResource(R.string.button_apply_left))
                }

                Button(
                    onClick = onApplyRight,
                    modifier = Modifier.weight(1f),
                    enabled = uiState.controllerConnected,
                    colors = if (uiState.isRightActive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) else ButtonDefaults.buttonColors()
                ) {
                    Text(stringResource(R.string.button_apply_right))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStopClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = uiState.controllerConnected
            ) {
                Text(stringResource(R.string.button_stop_vibration))
            }
        }

        SectionCard(title = stringResource(R.string.card_title_other_actions)) {
            Button(
                onClick = onRefreshConnectionClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.button_refresh_connection))
            }
        }

        if (showLogs && uiState.logText.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.card_title_log)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = uiState.logText,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun Modifier.alphaIf(condition: Boolean): Modifier = if (condition) this.then(Modifier.graphicsLayer { alpha = 0.38f }) else this
