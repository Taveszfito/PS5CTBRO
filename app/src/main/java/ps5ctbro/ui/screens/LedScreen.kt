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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRow
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedColor
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedConfig
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedEffect
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedUiState
import com.DueBoysenberry1226.ps5ctbro.ui.led.PlayerLedBrightness

@Composable
fun LedScreen(
    uiState: LedUiState,
    onConfigChanged: (LedConfig) -> Unit,
    onApplyClick: () -> Unit,
    onRefreshConnectionClick: () -> Unit,
    onResetClick: () -> Unit
) {
    var localConfig by remember { mutableStateOf(uiState.config) }

    LaunchedEffect(uiState.config) {
        localConfig = uiState.config
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LedStatusCard(
            controllerConnected = uiState.controllerConnected,
            currentEffectRes = uiState.config.effect.titleRes
        )

        LightbarCard(
            config = localConfig,
            onConfigChanged = { updated ->
                localConfig = updated
                onConfigChanged(updated)
            }
        )

        PlayerLedsCard(
            config = localConfig,
            onConfigChanged = { updated ->
                localConfig = updated
                onConfigChanged(updated)
            }
        )

        ActionsCard(
            onRefreshConnectionClick = onRefreshConnectionClick,
            onResetClick = onResetClick
        )

        LogCard(logText = uiState.logText)
    }
}

@Composable
private fun LedStatusCard(
    controllerConnected: Boolean,
    currentEffectRes: Int
) {
    SectionCard(title = stringResource(R.string.card_title_status)) {
        StatusRow(
            label = stringResource(R.string.label_controller),
            value = if (controllerConnected) stringResource(R.string.status_connected) else stringResource(R.string.status_disconnected)
        )

        StatusRow(
            label = stringResource(R.string.label_active_mode),
            value = stringResource(currentEffectRes)
        )
    }
}

@Composable
private fun LightbarCard(
    config: LedConfig,
    onConfigChanged: (LedConfig) -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_lightbar)) {
        Text(
            text = stringResource(R.string.label_effect),
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    Color(
                        red = config.color.red,
                        green = config.color.green,
                        blue = config.color.blue
                    )
                )
        )

        Spacer(modifier = Modifier.height(16.dp))

        AppSliderRow(
            label = stringResource(R.string.label_lightbar_brightness),
            value = config.lightbarBrightnessPercent.toFloat(),
            onValueChange = { newValue ->
                onConfigChanged(config.copy(lightbarBrightnessPercent = newValue.toInt()))
            },
            valueRange = 0f..100f
        )

        if (config.effect == LedEffect.BREATH || config.effect == LedEffect.COLOR_CYCLE) {
            Spacer(modifier = Modifier.height(12.dp))

            AppSliderRow(
                label = stringResource(R.string.label_effect_speed),
                value = config.animationSpeedPercent.toFloat(),
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(animationSpeedPercent = newValue.toInt()))
                },
                valueRange = 0f..100f
            )
        }

        if (config.effect == LedEffect.STATIC || config.effect == LedEffect.BREATH) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            AppSliderRow(
                label = stringResource(R.string.label_red),
                value = config.color.red.toFloat(),
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(color = config.color.copy(red = newValue.toInt())))
                },
                valueRange = 0f..255f,
                valueDisplay = config.color.red.toString()
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppSliderRow(
                label = stringResource(R.string.label_green),
                value = config.color.green.toFloat(),
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(color = config.color.copy(green = newValue.toInt())))
                },
                valueRange = 0f..255f,
                valueDisplay = config.color.green.toString()
            )

            Spacer(modifier = Modifier.height(12.dp))

            AppSliderRow(
                label = stringResource(R.string.label_blue),
                value = config.color.blue.toFloat(),
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(color = config.color.copy(blue = newValue.toInt())))
                },
                valueRange = 0f..255f,
                valueDisplay = config.color.blue.toString()
            )

            Spacer(modifier = Modifier.height(16.dp))

            PresetColorRow(
                onColorPicked = { color ->
                    onConfigChanged(config.copy(color = color))
                }
            )
        }
    }
}

@Composable
private fun PlayerLedsCard(
    config: LedConfig,
    onConfigChanged: (LedConfig) -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_player_leds)) {
        Text(
            text = stringResource(R.string.player_leds_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (index in 0 until 5) {
                val bit = 1 shl index
                val checked = (config.playerLedMask and bit) != 0

                FilterChip(
                    selected = checked,
                    onClick = {
                        val newMask = if (checked) {
                            config.playerLedMask and bit.inv()
                        } else {
                            config.playerLedMask or bit
                        }

                        onConfigChanged(config.copy(playerLedMask = newMask))
                    },
                    label = { Text("${index + 1}") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.label_player_led_brightness),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerLedBrightness.entries.forEach { brightness ->
                FilterChip(
                    selected = config.playerLedBrightness == brightness,
                    onClick = {
                        onConfigChanged(config.copy(playerLedBrightness = brightness))
                    },
                    label = { Text(stringResource(brightness.titleRes)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.label_mic_led),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !config.micLedEnabled,
                onClick = {
                    onConfigChanged(config.copy(micLedEnabled = false))
                },
                label = { Text(stringResource(R.string.off)) },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = config.micLedEnabled,
                onClick = {
                    onConfigChanged(config.copy(micLedEnabled = true))
                },
                label = { Text(stringResource(R.string.on)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionsCard(
    onRefreshConnectionClick: () -> Unit,
    onResetClick: () -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_actions)) {
        Text(
            text = stringResource(R.string.actions_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRefreshConnectionClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.button_refresh))
            }

            Button(
                onClick = onResetClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.button_reset))
            }
        }
    }
}

@Composable
private fun EffectSelectorRow(
    selectedEffect: LedEffect,
    onEffectSelected: (LedEffect) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedEffect == LedEffect.OFF,
                onClick = { onEffectSelected(LedEffect.OFF) },
                label = { Text(stringResource(LedEffect.OFF.titleRes)) },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = selectedEffect == LedEffect.STATIC,
                onClick = { onEffectSelected(LedEffect.STATIC) },
                label = { Text(stringResource(LedEffect.STATIC.titleRes)) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedEffect == LedEffect.BREATH,
                onClick = { onEffectSelected(LedEffect.BREATH) },
                label = { Text(stringResource(LedEffect.BREATH.titleRes)) },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = selectedEffect == LedEffect.COLOR_CYCLE,
                onClick = { onEffectSelected(LedEffect.COLOR_CYCLE) },
                label = { Text(stringResource(LedEffect.COLOR_CYCLE.titleRes)) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedEffect == LedEffect.MUSIC_REACTIVE,
                onClick = { onEffectSelected(LedEffect.MUSIC_REACTIVE) },
                label = { Text(stringResource(LedEffect.MUSIC_REACTIVE.titleRes)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PresetColorRow(
    onColorPicked: (LedColor) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetColorChip(stringResource(R.string.color_blue), LedColor(0, 114, 255), onColorPicked, Modifier.weight(1f))
            PresetColorChip(stringResource(R.string.color_red), LedColor(255, 40, 40), onColorPicked, Modifier.weight(1f))
            PresetColorChip(stringResource(R.string.color_green), LedColor(40, 255, 120), onColorPicked, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetColorChip(stringResource(R.string.color_purple), LedColor(180, 70, 255), onColorPicked, Modifier.weight(1f))
            PresetColorChip(stringResource(R.string.color_white), LedColor(255, 255, 255), onColorPicked, Modifier.weight(1f))
            PresetColorChip(stringResource(R.string.color_orange), LedColor(255, 140, 0), onColorPicked, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PresetColorChip(
    title: String,
    color: LedColor,
    onColorPicked: (LedColor) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = false,
        onClick = { onColorPicked(color) },
        label = { Text(title) },
        modifier = modifier
    )
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
