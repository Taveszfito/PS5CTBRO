package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRowModern
import com.DueBoysenberry1226.ps5ctbro.ui.components.SmallActionButton
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedColor
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedConfig
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedEffect
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedUiState
import com.DueBoysenberry1226.ps5ctbro.ui.led.PlayerLedBrightness

private val LedCardBorder = Color(0xFF2B4C7E)
private val LedPillBg = Color(0xFF14284A)
private val LedSelectedPillBg = Color(0xFF365A97)
private val LedLightText = Color(0xFFEAF2FF)
private val LedAccentText = Color(0xFF7FB0FF)

@Composable
fun LedScreen(
    uiState: LedUiState,
    isBtMode: Boolean,
    onConfigChanged: (LedConfig) -> Unit,
    onApplyClick: () -> Unit,
    onRefreshConnectionClick: () -> Unit,
    onResetClick: () -> Unit,
    showLogs: Boolean = false
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
            currentEffectRes = localConfig.effect.titleRes,
            onRefreshConnectionClick = onRefreshConnectionClick,
            onResetClick = {
                onResetClick()
            }
        )

        LightbarCard(
            config = localConfig,
            enabled = !isBtMode,
            onConfigChanged = { updated ->
                localConfig = updated
                onConfigChanged(updated)
            },
            onApplyClick = onApplyClick
        )

        PlayerLedsCard(
            config = localConfig,
            enabled = !isBtMode,
            onConfigChanged = { updated ->
                localConfig = updated
                onConfigChanged(updated)
            }
        )

        if (showLogs) {
            LogCard(logText = uiState.logText)
        }
    }
}

@Composable
private fun LedStatusCard(
    controllerConnected: Boolean,
    currentEffectRes: Int,
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
                        icon = Icons.Outlined.GraphicEq,
                        title = stringResource(R.string.label_active_mode),
                        value = stringResource(currentEffectRes),
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
private fun LightbarCard(
    config: LedConfig,
    enabled: Boolean,
    onConfigChanged: (LedConfig) -> Unit,
    onApplyClick: () -> Unit
) {
    SectionCard(
        title = stringResource(R.string.card_title_lightbar),
        enabled = enabled
    ) {
        Text(
            text = stringResource(R.string.label_effect),
            style = MaterialTheme.typography.titleMedium,
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Color(
                        red = config.color.red,
                        green = config.color.green,
                        blue = config.color.blue
                    )
                )
        )

        Spacer(modifier = Modifier.height(12.dp))

        AppSliderRow(
            label = stringResource(R.string.label_lightbar_brightness),
            value = config.lightbarBrightnessPercent.toFloat(),
            onValueChange = { newValue ->
                onConfigChanged(config.copy(lightbarBrightnessPercent = newValue.toInt()))
            },
            valueRange = 0f..100f
        )

        if (config.effect == LedEffect.BREATH || config.effect == LedEffect.COLOR_CYCLE) {
            Spacer(modifier = Modifier.height(8.dp))

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
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(10.dp))

            AppSliderRow(
                label = stringResource(R.string.label_red),
                value = config.color.red.toFloat(),
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(color = config.color.copy(red = newValue.toInt())))
                },
                valueRange = 0f..255f,
                valueDisplay = config.color.red.toString()
            )

            Spacer(modifier = Modifier.height(6.dp))

            AppSliderRow(
                label = stringResource(R.string.label_green),
                value = config.color.green.toFloat(),
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(color = config.color.copy(green = newValue.toInt())))
                },
                valueRange = 0f..255f,
                valueDisplay = config.color.green.toString()
            )

            Spacer(modifier = Modifier.height(6.dp))

            AppSliderRow(
                label = stringResource(R.string.label_blue),
                value = config.color.blue.toFloat(),
                onValueChange = { newValue ->
                    onConfigChanged(config.copy(color = config.color.copy(blue = newValue.toInt())))
                },
                valueRange = 0f..255f,
                valueDisplay = config.color.blue.toString()
            )

            Spacer(modifier = Modifier.height(10.dp))

            PresetColorRow(
                onColorPicked = { color ->
                    onConfigChanged(config.copy(color = color))
                    onApplyClick()
                }
            )
        }
    }
}

@Composable
private fun PlayerLedsCard(
    config: LedConfig,
    enabled: Boolean,
    onConfigChanged: (LedConfig) -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    SectionCard(
        title = stringResource(R.string.card_title_player_leds),
        enabled = enabled,
        titleTrailing = {
            Surface(
                modifier = Modifier.clickable { showInfoDialog = true },
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF102345),
                border = BorderStroke(1.dp, LedCardBorder)
            ) {
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = LedAccentText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (index in 0 until 5) {
                val bit = 1 shl index
                val checked = (config.playerLedMask and bit) != 0

                StyledFilterChip(
                    selected = checked,
                    text = "${index + 1}",
                    onClick = {
                        val newMask = if (checked) {
                            config.playerLedMask and bit.inv()
                        } else {
                            config.playerLedMask or bit
                        }
                        onConfigChanged(config.copy(playerLedMask = newMask))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Highlight,
                contentDescription = null,
                tint = LedAccentText,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.label_player_led_brightness),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerLedBrightness.entries.forEach { brightness ->
                val chipIcon = when (brightness) {
                    PlayerLedBrightness.LOW -> Icons.Outlined.Lightbulb
                    PlayerLedBrightness.MEDIUM -> Icons.Outlined.WbSunny
                    PlayerLedBrightness.HIGH -> Icons.Outlined.Highlight
                }

                StyledFilterChip(
                    selected = config.playerLedBrightness == brightness,
                    text = stringResource(brightness.titleRes),
                    icon = chipIcon,
                    onClick = {
                        onConfigChanged(config.copy(playerLedBrightness = brightness))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = LedAccentText,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.label_mic_led),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StyledFilterChip(
                selected = !config.micLedEnabled,
                text = stringResource(R.string.off),
                icon = Icons.Outlined.ToggleOff,
                onClick = {
                    onConfigChanged(config.copy(micLedEnabled = false))
                },
                modifier = Modifier.weight(1f)
            )

            StyledFilterChip(
                selected = config.micLedEnabled,
                text = stringResource(R.string.on),
                icon = Icons.Outlined.ToggleOn,
                onClick = {
                    onConfigChanged(config.copy(micLedEnabled = true))
                },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK")
                }
            },
            title = {
                Text(text = stringResource(R.string.card_title_player_leds))
            },
            text = {
                Text(
                    text = stringResource(R.string.player_leds_description),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }
}

@Composable
private fun EffectSelectorRow(
    selectedEffect: LedEffect,
    onEffectSelected: (LedEffect) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StyledFilterChip(
                selected = selectedEffect == LedEffect.OFF,
                text = stringResource(LedEffect.OFF.titleRes),
                icon = Icons.Outlined.DoNotDisturbOn,
                onClick = { onEffectSelected(LedEffect.OFF) },
                modifier = Modifier.weight(1f)
            )

            StyledFilterChip(
                selected = selectedEffect == LedEffect.STATIC,
                text = stringResource(LedEffect.STATIC.titleRes),
                icon = Icons.Outlined.Circle,
                onClick = { onEffectSelected(LedEffect.STATIC) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StyledFilterChip(
                selected = selectedEffect == LedEffect.BREATH,
                text = stringResource(LedEffect.BREATH.titleRes),
                icon = Icons.Outlined.Gradient,
                onClick = { onEffectSelected(LedEffect.BREATH) },
                modifier = Modifier.weight(1f)
            )

            StyledFilterChip(
                selected = selectedEffect == LedEffect.COLOR_CYCLE,
                text = stringResource(LedEffect.COLOR_CYCLE.titleRes),
                icon = Icons.Outlined.Autorenew,
                onClick = { onEffectSelected(LedEffect.COLOR_CYCLE) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            StyledFilterChip(
                selected = selectedEffect == LedEffect.MUSIC_REACTIVE,
                text = stringResource(LedEffect.MUSIC_REACTIVE.titleRes),
                icon = Icons.Outlined.Audiotrack,
                onClick = { onEffectSelected(LedEffect.MUSIC_REACTIVE) },
                modifier = Modifier.fillMaxWidth()
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
            PresetColorChip(
                stringResource(R.string.color_blue),
                LedColor(0, 114, 255),
                onColorPicked,
                Modifier.weight(1f)
            )
            PresetColorChip(
                stringResource(R.string.color_red),
                LedColor(255, 40, 40),
                onColorPicked,
                Modifier.weight(1f)
            )
            PresetColorChip(
                stringResource(R.string.color_green),
                LedColor(40, 255, 120),
                onColorPicked,
                Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetColorChip(
                stringResource(R.string.color_purple),
                LedColor(180, 70, 255),
                onColorPicked,
                Modifier.weight(1f)
            )
            PresetColorChip(
                stringResource(R.string.color_white),
                LedColor(255, 255, 255),
                onColorPicked,
                Modifier.weight(1f)
            )
            PresetColorChip(
                stringResource(R.string.color_orange),
                LedColor(255, 140, 0),
                onColorPicked,
                Modifier.weight(1f)
            )
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
    StyledFilterChip(
        selected = false,
        text = title,
        dotColor = Color(
            red = color.red,
            green = color.green,
            blue = color.blue
        ),
        onClick = { onColorPicked(color) },
        modifier = modifier
    )
}

@Composable
private fun StyledFilterChip(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dotColor: Color? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                when {
                    icon != null -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (selected) Color.White else LedAccentText,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    dotColor != null -> {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = dotColor,
                                    shape = RoundedCornerShape(999.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, LedCardBorder),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LedSelectedPillBg,
            selectedLabelColor = Color.White,
            containerColor = LedPillBg,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
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