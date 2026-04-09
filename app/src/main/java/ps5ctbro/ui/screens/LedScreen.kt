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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
            currentEffect = uiState.config.effect.title
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
    currentEffect: String
) {
    SectionCard(title = "Állapot") {
        StatusRow(
            label = "Kontroller",
            value = if (controllerConnected) "Csatlakoztatva" else "Nincs csatlakoztatva"
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        StatusRow(
            label = "Aktív mód",
            value = currentEffect
        )
    }
}

@Composable
private fun LightbarCard(
    config: LedConfig,
    onConfigChanged: (LedConfig) -> Unit
) {
    SectionCard(title = "Lightbar") {
        Text(
            text = "Effekt",
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
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

        SliderRow(
            label = "Lightbar fényerő",
            value = config.lightbarBrightnessPercent,
            valueText = "${config.lightbarBrightnessPercent}%",
            onValueChange = { newValue ->
                onConfigChanged(config.copy(lightbarBrightnessPercent = newValue))
            },
            valueRange = 0..100
        )

        if (config.effect == LedEffect.BREATH || config.effect == LedEffect.COLOR_CYCLE) {
            Spacer(modifier = Modifier.height(12.dp))

            SliderRow(
                label = "Effekt sebesség",
                value = config.animationSpeedPercent,
                valueText = "${config.animationSpeedPercent}%",
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(animationSpeedPercent = newValue))
                },
                valueRange = 0..100
            )
        }

        if (config.effect == LedEffect.STATIC || config.effect == LedEffect.BREATH) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            SliderRow(
                label = "Piros",
                value = config.color.red,
                valueText = "${config.color.red}",
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(color = config.color.copy(red = newValue)))
                },
                valueRange = 0..255
            )

            Spacer(modifier = Modifier.height(12.dp))

            SliderRow(
                label = "Zöld",
                value = config.color.green,
                valueText = "${config.color.green}",
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(color = config.color.copy(green = newValue)))
                },
                valueRange = 0..255
            )

            Spacer(modifier = Modifier.height(12.dp))

            SliderRow(
                label = "Kék",
                value = config.color.blue,
                valueText = "${config.color.blue}",
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(color = config.color.copy(blue = newValue)))
                },
                valueRange = 0..255
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
    SectionCard(title = "Player LED-ek") {
        Text(
            text = "Az állítás azonnal kimegy. Néhány DualSense revízión az 1/5 és 2/4 LED hardveresen tükrözött.",
            style = MaterialTheme.typography.bodyMedium
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
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Player LED fényerő",
            style = MaterialTheme.typography.titleMedium
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
                    label = { Text(brightness.title) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Mic LED",
            style = MaterialTheme.typography.titleMedium
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
                label = { Text("Ki") },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = config.micLedEnabled,
                onClick = {
                    onConfigChanged(config.copy(micLedEnabled = true))
                },
                label = { Text("Be") },
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
    SectionCard(title = "Műveletek") {
        Text(
            text = "Amennyiben nem frissülne automatikusan a beállítás itt tudsz frissíteni, vagy vissza állítani mindent az alapértelmezett értékekre.",
            style = MaterialTheme.typography.bodyMedium
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
                Text("FRISSÍTÉS")
            }

            Button(
                onClick = onResetClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("RESET")
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
                label = { Text("Ki") },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = selectedEffect == LedEffect.STATIC,
                onClick = { onEffectSelected(LedEffect.STATIC) },
                label = { Text("Statikus") },
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
                label = { Text("Breath") },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = selectedEffect == LedEffect.COLOR_CYCLE,
                onClick = { onEffectSelected(LedEffect.COLOR_CYCLE) },
                label = { Text("Color Cycle") },
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
            PresetColorChip("Kék", LedColor(0, 114, 255), onColorPicked, Modifier.weight(1f))
            PresetColorChip("Piros", LedColor(255, 40, 40), onColorPicked, Modifier.weight(1f))
            PresetColorChip("Zöld", LedColor(40, 255, 120), onColorPicked, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetColorChip("Lila", LedColor(180, 70, 255), onColorPicked, Modifier.weight(1f))
            PresetColorChip("Fehér", LedColor(255, 255, 255), onColorPicked, Modifier.weight(1f))
            PresetColorChip("Narancs", LedColor(255, 140, 0), onColorPicked, Modifier.weight(1f))
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
private fun SliderRow(
    label: String,
    value: Int,
    valueText: String,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange
) {
    Text(
        text = "$label: $valueText",
        style = MaterialTheme.typography.bodyLarge
    )

    Spacer(modifier = Modifier.height(6.dp))

    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
        modifier = Modifier.fillMaxWidth()
    )
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )

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