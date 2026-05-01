package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggerConfig
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggerEffect
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggersUiState
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.SmallActionButton
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRowModern
import kotlin.math.roundToInt

@Composable
fun AdaptiveTriggersScreen(
    uiState: AdaptiveTriggersUiState,
    onLeftTriggerChanged: (AdaptiveTriggerConfig) -> Unit,
    onRightTriggerChanged: (AdaptiveTriggerConfig) -> Unit,
    onApplyClick: () -> Unit,
    onRefreshConnectionClick: () -> Unit,
    onResetClick: () -> Unit,
    showLogs: Boolean = false,
    isBtMode: Boolean = false
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
            controllerConnected = uiState.controllerConnected,
            onRefreshConnectionClick = onRefreshConnectionClick,
            onResetClick = onResetClick
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
            },
            enabled = !isBtMode
        )

        if (showLogs) {
            TriggerLogCard(
                logText = uiState.logText
            )
        }
    }
}

@Composable
private fun TriggerStatusCard(
    controllerConnected: Boolean,
    onRefreshConnectionClick: () -> Unit,
    onResetClick: () -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_status)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF102345),
                border = BorderStroke(1.dp, Color(0xFF2B4C7E))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    StatusRowModern(
                        icon = Icons.Outlined.SportsEsports,
                        title = stringResource(R.string.label_controller),
                        value = if (controllerConnected) {
                            stringResource(R.string.status_connected)
                        } else {
                            stringResource(R.string.status_disconnected)
                        },
                        isOnline = controllerConnected,
                        onClick = onRefreshConnectionClick
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    )

                    StatusRowModern(
                        icon = Icons.Outlined.Settings,
                        title = stringResource(R.string.label_adaptive_triggers_status),
                        value = stringResource(R.string.status_active_configuration),
                        isOnline = null,
                        onClick = onResetClick
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SmallActionButton(
                    text = stringResource(R.string.button_refresh),
                    icon = Icons.Outlined.Refresh,
                    onClick = onRefreshConnectionClick
                )

                SmallActionButton(
                    text = stringResource(R.string.button_reset),
                    icon = Icons.Outlined.RestartAlt,
                    onClick = onResetClick
                )
            }
        }
    }
}

@Composable
private fun TriggerConfigCard(
    config: AdaptiveTriggerConfig,
    uiState: AdaptiveTriggersUiState,
    onConfigChanged: (AdaptiveTriggerConfig) -> Unit,
    onApplyL2: (AdaptiveTriggerConfig) -> Unit,
    onApplyR2: (AdaptiveTriggerConfig) -> Unit,
    enabled: Boolean = true
) {
    SectionCard(
        title = stringResource(R.string.card_title_settings),
        enabled = enabled
    ) {
        Text(
            text = stringResource(R.string.label_effect_type),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        EffectSelectorRow(
            selectedEffect = config.effect,
            onEffectSelected = { effect ->
                onConfigChanged(config.copy(effect = effect))
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF14284A),
            border = BorderStroke(1.dp, Color(0xFF2B4C7E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                TriggerCompactBar(
                    label = "L2",
                    pressedPercent = uiState.leftTriggerPressedPercent,
                    appliedConfig = uiState.leftTrigger
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )

                TriggerCompactBar(
                    label = "R2",
                    pressedPercent = uiState.rightTriggerPressedPercent,
                    appliedConfig = uiState.rightTrigger
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF14284A),
            border = BorderStroke(1.dp, Color(0xFF2B4C7E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                AppSliderRow(
                    label = stringResource(R.string.label_start_point),
                    value = config.startPercent.toFloat(),
                    onValueChange = { newValue ->
                        onConfigChanged(config.copy(startPercent = newValue.roundToInt()))
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                AppSliderRow(
                    label = stringResource(R.string.label_end_point),
                    value = config.endPercent.toFloat(),
                    onValueChange = { newValue ->
                        onConfigChanged(config.copy(endPercent = newValue.roundToInt()))
                    }
                )
            }
        }

        if (config.effect != AdaptiveTriggerEffect.OFF) {
            Spacer(modifier = Modifier.height(10.dp))

            when (config.effect) {
                AdaptiveTriggerEffect.RESISTANCE -> {
                    AppSliderRow(
                        label = stringResource(R.string.label_resistance_strength),
                        value = config.strengthPercent.toFloat(),
                        onValueChange = { newValue ->
                            onConfigChanged(config.copy(strengthPercent = newValue.roundToInt()))
                        }
                    )
                }

                AdaptiveTriggerEffect.VIBRATION -> {
                    AppSliderRow(
                        label = stringResource(R.string.label_vibration_speed),
                        value = config.speedPercent.toFloat(),
                        onValueChange = { newValue ->
                            onConfigChanged(config.copy(speedPercent = newValue.roundToInt()))
                        }
                    )
                }

                AdaptiveTriggerEffect.OFF -> Unit
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onApplyL2(config) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.button_apply_l2),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Button(
                onClick = { onApplyR2(config) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.button_apply_r2),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
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
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterChip(
            selected = selectedEffect == AdaptiveTriggerEffect.RESISTANCE,
            onClick = { onEffectSelected(AdaptiveTriggerEffect.RESISTANCE) },
            label = {
                Text(
                    text = stringResource(AdaptiveTriggerEffect.RESISTANCE.titleRes),
                    fontWeight = FontWeight.SemiBold
                )
            },
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2B4C7E)),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF2B4C7E),
                selectedLabelColor = Color.White,
                containerColor = Color(0xFF14284A),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        FilterChip(
            selected = selectedEffect == AdaptiveTriggerEffect.VIBRATION,
            onClick = { onEffectSelected(AdaptiveTriggerEffect.VIBRATION) },
            label = {
                Text(
                    text = stringResource(AdaptiveTriggerEffect.VIBRATION.titleRes),
                    fontWeight = FontWeight.SemiBold
                )
            },
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2B4C7E)),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF2B4C7E),
                selectedLabelColor = Color.White,
                containerColor = Color(0xFF14284A),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "$pressedPercent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

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
            .height(18.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF0C1D3A))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedPressed / 100f)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        )

        Box(
            modifier = Modifier
                .fillMaxWidth((clampedEnd - clampedStart).coerceAtLeast(1) / 100f)
                .height(18.dp)
                .offset(x = (clampedStart * 3).dp)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.24f))
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "0%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
        Text(
            text = "100%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}