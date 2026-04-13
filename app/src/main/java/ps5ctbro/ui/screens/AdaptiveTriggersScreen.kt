package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggerConfig
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggerEffect
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggersUiState
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRow

@Composable
fun AdaptiveTriggersScreen(
    uiState: AdaptiveTriggersUiState,
    onLeftTriggerChanged: (AdaptiveTriggerConfig) -> Unit,
    onRightTriggerChanged: (AdaptiveTriggerConfig) -> Unit,
    onApplyClick: () -> Unit,
    onRefreshConnectionClick: () -> Unit,
    onResetClick: () -> Unit
) {
    var localConfig by remember { mutableStateOf(uiState.leftTrigger) }

    LaunchedEffect(uiState.leftTrigger) {
        localConfig = uiState.leftTrigger
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TriggerStatusCard(
            controllerConnected = uiState.controllerConnected
        )

        TriggerConfigCard(
            config = localConfig,
            uiState = uiState,
            onConfigChanged = { updated ->
                localConfig = updated
            },
            onApplyL2 = { config ->
                onLeftTriggerChanged(config)
                onApplyClick()
            },
            onApplyR2 = { config ->
                onRightTriggerChanged(config)
                onApplyClick()
            }
        )

        TriggerActionsCard(
            onRefreshConnectionClick = onRefreshConnectionClick,
            onResetClick = onResetClick
        )

        TriggerLogCard(
            logText = uiState.logText
        )
    }
}

@Composable
private fun TriggerStatusCard(
    controllerConnected: Boolean
) {
    SectionCard(title = stringResource(R.string.card_title_status)) {
        StatusRow(
            label = stringResource(R.string.label_controller),
            value = if (controllerConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected)
        )
    }
}

@Composable
private fun TriggerConfigCard(
    config: AdaptiveTriggerConfig,
    uiState: AdaptiveTriggersUiState,
    onConfigChanged: (AdaptiveTriggerConfig) -> Unit,
    onApplyL2: (AdaptiveTriggerConfig) -> Unit,
    onApplyR2: (AdaptiveTriggerConfig) -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_settings)) {
        Text(
            text = stringResource(R.string.label_effect_type),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        EffectSelectorRow(
            selectedEffect = config.effect,
            onEffectSelected = { effect ->
                onConfigChanged(config.copy(effect = effect))
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        TriggerCompactBar(
            label = "L2",
            pressedPercent = uiState.leftTriggerPressedPercent,
            appliedConfig = uiState.leftTrigger
        )

        Spacer(modifier = Modifier.height(12.dp))

        TriggerCompactBar(
            label = "R2",
            pressedPercent = uiState.rightTriggerPressedPercent,
            appliedConfig = uiState.rightTrigger
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        AppSliderRow(
            label = stringResource(R.string.label_start_point),
            value = config.startPercent.toFloat(),
            onValueChange = { newValue ->
                onConfigChanged(config.copy(startPercent = newValue.toInt()))
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        AppSliderRow(
            label = stringResource(R.string.label_end_point),
            value = config.endPercent.toFloat(),
            onValueChange = { newValue ->
                onConfigChanged(config.copy(endPercent = newValue.toInt()))
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (config.effect) {
            AdaptiveTriggerEffect.RESISTANCE -> {
                AppSliderRow(
                    label = stringResource(R.string.label_resistance_strength),
                    value = config.strengthPercent.toFloat(),
                    onValueChange = { newValue ->
                        onConfigChanged(config.copy(strengthPercent = newValue.toInt()))
                    }
                )
            }

            AdaptiveTriggerEffect.VIBRATION -> {
                AppSliderRow(
                    label = stringResource(R.string.label_vibration_speed),
                    value = config.speedPercent.toFloat(),
                    onValueChange = { newValue ->
                        onConfigChanged(config.copy(speedPercent = newValue.toInt()))
                    }
                )
            }

            AdaptiveTriggerEffect.OFF -> {
                // Keep UI consistent but disabled or shown as neutral if needed
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { onApplyL2(config) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.button_apply_l2))
            }

            Button(
                onClick = { onApplyR2(config) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.button_apply_r2))
            }
        }
    }
}

@Composable
private fun TriggerActionsCard(
    onRefreshConnectionClick: () -> Unit,
    onResetClick: () -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_other_actions)) {
        Button(
            onClick = onRefreshConnectionClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.button_refresh_connection))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onResetClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.button_reset_triggers))
        }
    }
}

@Composable
private fun EffectSelectorRow(
    selectedEffect: AdaptiveTriggerEffect,
    onEffectSelected: (AdaptiveTriggerEffect) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedEffect == AdaptiveTriggerEffect.RESISTANCE,
            onClick = { onEffectSelected(AdaptiveTriggerEffect.RESISTANCE) },
            label = { Text(stringResource(AdaptiveTriggerEffect.RESISTANCE.titleRes)) },
            modifier = Modifier.weight(1f)
        )

        FilterChip(
            selected = selectedEffect == AdaptiveTriggerEffect.VIBRATION,
            onClick = { onEffectSelected(AdaptiveTriggerEffect.VIBRATION) },
            label = { Text(stringResource(AdaptiveTriggerEffect.VIBRATION.titleRes)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TriggerLogCard(
    logText: String
) {
    SectionCard(title = stringResource(R.string.card_title_log)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = logText,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TriggerCompactBar(
    label: String,
    pressedPercent: Int,
    appliedConfig: AdaptiveTriggerConfig
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "$pressedPercent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        CompactRangeBar(
            pressedPercent = pressedPercent,
            startPercent = appliedConfig.startPercent,
            endPercent = appliedConfig.endPercent
        )
    }
}

@Composable
private fun CompactRangeBar(
    pressedPercent: Int,
    startPercent: Int,
    endPercent: Int
) {
    val clampedPressed = pressedPercent.coerceIn(0, 100)
    val clampedStart = startPercent.coerceIn(0, 100)
    val clampedEnd = endPercent.coerceIn(clampedStart, 100)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedPressed / 100f)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth((clampedEnd - clampedStart).coerceAtLeast(1) / 100f)
                .height(24.dp)
                .offset(x = (clampedStart * 3).dp) // This multiplier might be problematic on different screen widths, but keeping logic
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "$clampedPressed%   |   $clampedStart% → $clampedEnd%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
