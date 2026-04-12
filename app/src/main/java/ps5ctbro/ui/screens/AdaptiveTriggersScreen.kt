package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
            style = MaterialTheme.typography.titleMedium
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

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        TriggerSliderRow(
            label = stringResource(R.string.label_start_point),
            value = config.startPercent,
            valueText = "${config.startPercent}%",
            onValueChange = { newValue ->
                onConfigChanged(config.copy(startPercent = newValue))
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        TriggerSliderRow(
            label = stringResource(R.string.label_end_point),
            value = config.endPercent,
            valueText = "${config.endPercent}%",
            onValueChange = { newValue ->
                onConfigChanged(config.copy(endPercent = newValue))
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (config.effect) {
            AdaptiveTriggerEffect.RESISTANCE -> {
                TriggerSliderRow(
                    label = stringResource(R.string.label_resistance_strength),
                    value = config.strengthPercent,
                    valueText = "${config.strengthPercent}%",
                    onValueChange = { newValue ->
                        onConfigChanged(config.copy(strengthPercent = newValue))
                    }
                )
            }

            AdaptiveTriggerEffect.VIBRATION -> {
                TriggerSliderRow(
                    label = stringResource(R.string.label_vibration_speed),
                    value = config.speedPercent,
                    valueText = "${config.speedPercent}%",
                    onValueChange = { newValue ->
                        onConfigChanged(config.copy(speedPercent = newValue))
                    }
                )
            }

            AdaptiveTriggerEffect.OFF -> {
                TriggerSliderRow(
                    label = stringResource(R.string.label_resistance_strength),
                    value = config.strengthPercent,
                    valueText = "${config.strengthPercent}%",
                    onValueChange = { newValue ->
                        onConfigChanged(config.copy(strengthPercent = newValue))
                    }
                )
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
        EffectChip(
            title = stringResource(AdaptiveTriggerEffect.RESISTANCE.titleRes),
            selected = selectedEffect == AdaptiveTriggerEffect.RESISTANCE,
            onClick = { onEffectSelected(AdaptiveTriggerEffect.RESISTANCE) },
            modifier = Modifier.weight(1f)
        )

        EffectChip(
            title = stringResource(AdaptiveTriggerEffect.VIBRATION.titleRes),
            selected = selectedEffect == AdaptiveTriggerEffect.VIBRATION,
            onClick = { onEffectSelected(AdaptiveTriggerEffect.VIBRATION) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EffectChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(title)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TriggerSliderRow(
    label: String,
    value: Int,
    valueText: String,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f
        )
    }
}

@Composable
private fun TriggerLogCard(
    logText: String
) {
    SectionCard(title = stringResource(R.string.card_title_log)) {
        Surface(
            tonalElevation = 1.dp,
            shape = CardDefaults.shape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = logText,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )

        Surface(
            tonalElevation = 1.dp
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium
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
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "$pressedPercent%",
                style = MaterialTheme.typography.bodyLarge
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
            .clip(CardDefaults.shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedPressed / 100f)
                .height(24.dp)
                .clip(CardDefaults.shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth((clampedEnd - clampedStart).coerceAtLeast(1) / 100f)
                .height(24.dp)
                .offset(x = (clampedStart * 3).dp)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "$clampedPressed%   |   $clampedStart% → $clampedEnd%",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}