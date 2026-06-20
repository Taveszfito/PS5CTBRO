package com.DueBoysenberry1226.ps5ctbro.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Headset
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toBitmap
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.audio.AudioUiState
import com.DueBoysenberry1226.ps5ctbro.audio.GameModePreset
import com.DueBoysenberry1226.ps5ctbro.audio.GameModeTuning
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import kotlin.math.roundToInt

private data class LaunchableAppInfo(
    val packageName: String,
    val label: String
)

private data class GamePresetEditorState(
    val presetId: String?,
    val appPackageName: String,
    val appLabel: String,
    val tuning: GameModeTuning
)

private data class PremadeGamePresetTemplate(
    @StringRes val nameResId: Int,
    val tuning: GameModeTuning
)

private data class TuningHelp(
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    @StringRes val exampleResId: Int
)

private val premadeGamePresetTemplates = listOf(
    PremadeGamePresetTemplate(
        nameResId = R.string.game_preset_template_current,
        tuning = GameModeTuning.DEFAULT
    ),
    PremadeGamePresetTemplate(
        nameResId = R.string.game_preset_template_quiet_games,
        tuning = GameModeTuning.DEFAULT.copy(
            adaptiveStrengthEnabled = true,
            preciseReactionEnabled = true,
            bassFollow = 0.040f,
            transientFastFollow = 0.30f,
            transientSlowFollow = 0.014f,
            adaptiveFloor = 0.18f,
            adaptiveCeiling = 1.45f,
            lowThreshold = 1200f,
            transientThreshold = 340f,
            strongLowMix = 0.58f,
            transientLowMix = 0.42f,
            quietLowMix = 0.12f,
            precisePunch = 1.35f,
            softPunch = 0.42f,
            punchPolarityScale = 1.00f,
            softClipDenominator = 20000f
        )
    ),
    PremadeGamePresetTemplate(
        nameResId = R.string.game_preset_template_racing,
        tuning = GameModeTuning.DEFAULT.copy(
            adaptiveStrengthEnabled = true,
            preciseReactionEnabled = true,
            bassFollow = 0.078f,
            transientFastFollow = 0.18f,
            transientSlowFollow = 0.012f,
            adaptiveFloor = 0.04f,
            adaptiveCeiling = 1.30f,
            lowThreshold = 1900f,
            transientThreshold = 560f,
            strongLowMix = 0.80f,
            transientLowMix = 0.26f,
            quietLowMix = 0.05f,
            precisePunch = 1.00f,
            softPunch = 0.28f,
            punchPolarityScale = 0.88f,
            softClipDenominator = 22000f
        )
    ),
    PremadeGamePresetTemplate(
        nameResId = R.string.game_preset_template_fps,
        tuning = GameModeTuning.DEFAULT.copy(
            adaptiveStrengthEnabled = true,
            preciseReactionEnabled = true,
            bassFollow = 0.046f,
            transientFastFollow = 0.34f,
            transientSlowFollow = 0.016f,
            adaptiveFloor = 0.06f,
            adaptiveCeiling = 1.25f,
            lowThreshold = 2100f,
            transientThreshold = 300f,
            strongLowMix = 0.50f,
            transientLowMix = 0.16f,
            quietLowMix = 0.03f,
            precisePunch = 1.75f,
            softPunch = 0.46f,
            punchPolarityScale = 1.12f,
            softClipDenominator = 15000f
        )
    ),
    PremadeGamePresetTemplate(
        nameResId = R.string.game_preset_template_action,
        tuning = GameModeTuning.DEFAULT.copy(
            adaptiveStrengthEnabled = true,
            preciseReactionEnabled = true,
            bassFollow = 0.062f,
            transientFastFollow = 0.24f,
            transientSlowFollow = 0.017f,
            adaptiveFloor = 0.03f,
            adaptiveCeiling = 1.35f,
            lowThreshold = 2000f,
            transientThreshold = 480f,
            strongLowMix = 0.70f,
            transientLowMix = 0.30f,
            quietLowMix = 0.04f,
            precisePunch = 1.38f,
            softPunch = 0.38f,
            punchPolarityScale = 1.00f,
            softClipDenominator = 17000f
        )
    ),
    PremadeGamePresetTemplate(
        nameResId = R.string.game_preset_template_heavy_bass,
        tuning = GameModeTuning.DEFAULT.copy(
            adaptiveStrengthEnabled = true,
            preciseReactionEnabled = false,
            bassFollow = 0.095f,
            transientFastFollow = 0.16f,
            transientSlowFollow = 0.014f,
            adaptiveFloor = 0.12f,
            adaptiveCeiling = 1.55f,
            lowThreshold = 1400f,
            transientThreshold = 760f,
            strongLowMix = 0.96f,
            transientLowMix = 0.44f,
            quietLowMix = 0.10f,
            precisePunch = 0.92f,
            softPunch = 0.52f,
            punchPolarityScale = 0.86f,
            softClipDenominator = 24000f
        )
    )
)

@Composable
private fun tuningHelp(
    @StringRes titleResId: Int,
    @StringRes descriptionResId: Int,
    @StringRes exampleResId: Int
): TuningHelp = TuningHelp(titleResId, descriptionResId, exampleResId)

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
    onGameModeChanged: (Boolean) -> Unit,
    onGameModeTuningChanged: (GameModeTuning) -> Unit,
    onResetGameModeTuning: () -> Unit,
    onGameModeAdaptiveStrengthChanged: (Boolean) -> Unit,
    onGameModePreciseReactionChanged: (Boolean) -> Unit,
    onSaveGameModePreset: (String?, String, String, GameModeTuning) -> Unit,
    onDeleteGameModePreset: (String) -> Unit,
    onApplyGameModePreset: (String) -> Unit,
    onImportGameModePresetFromClipboard: () -> Unit,
    onCopyGameModePresetToClipboard: (String) -> Unit,
    onMutePhoneWhileStreamingChanged: (Boolean) -> Unit,
    onHardwareVolumeButtonsControlControllerChanged: (Boolean) -> Unit,
    showLogs: Boolean,
    isBtMode: Boolean = false
) {
    val activePreset = uiState.gameModePresets.firstOrNull { it.id == uiState.activeGamePresetId }
    var editorState by remember { mutableStateOf<GamePresetEditorState?>(null) }

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
            volumeStep = uiState.volumeStep,
            gameMode = uiState.gameMode,
            activePresetLabel = activePreset?.appLabel
        )

        ActionsCard(
            isStreaming = uiState.isStreaming,
            onStartStreamClick = onStartStreamClick,
            onStopStreamClick = onStopStreamClick,
            onApplySpeakerRouteClick = onApplySpeakerRouteClick,
            enabled = !isBtMode
        )

        ChannelRoutesCard(
            routeCh1 = uiState.routeCh1,
            routeCh2 = uiState.routeCh2,
            routeCh3 = uiState.routeCh3,
            routeCh4 = uiState.routeCh4,
            gameMode = uiState.gameMode,
            gameModeAdaptiveStrength = uiState.gameModeTuning.adaptiveStrengthEnabled,
            gameModePreciseReaction = uiState.gameModeTuning.preciseReactionEnabled,
            onRouteCh1Changed = onRouteCh1Changed,
            onRouteCh2Changed = onRouteCh2Changed,
            onRouteCh3Changed = onRouteCh3Changed,
            onRouteCh4Changed = onRouteCh4Changed,
            onGameModeChanged = onGameModeChanged,
            onGameModeAdaptiveStrengthChanged = onGameModeAdaptiveStrengthChanged,
            onGameModePreciseReactionChanged = onGameModePreciseReactionChanged,
            enabled = !isBtMode
        )

        if (uiState.gameMode) {
            GameModePresetCard(
                presets = uiState.gameModePresets,
                activePresetId = uiState.activeGamePresetId,
                currentTuning = uiState.gameModeTuning,
                onApplyPreset = onApplyGameModePreset,
                onImportPreset = onImportGameModePresetFromClipboard,
                onAddPreset = {
                    editorState = GamePresetEditorState(
                        presetId = null,
                        appPackageName = "",
                        appLabel = "",
                        tuning = uiState.gameModeTuning
                    )
                },
                onEditPreset = { preset ->
                    editorState = GamePresetEditorState(
                        presetId = preset.id,
                        appPackageName = preset.appPackageName,
                        appLabel = preset.appLabel,
                        tuning = preset.tuning
                    )
                },
                onMutePhoneWhileStreamingChanged = onMutePhoneWhileStreamingChanged,
                onHardwareVolumeButtonsControlControllerChanged =
                    onHardwareVolumeButtonsControlControllerChanged,
                mutePhoneWhileStreaming = uiState.mutePhoneWhileStreaming,
                hardwareVolumeButtonsControlController = uiState.hardwareVolumeButtonsControlController,
                enabled = !isBtMode
            )
        } else {
            VolumeCard(
                volumeStep = uiState.volumeStep,
                mutePhoneWhileStreaming = uiState.mutePhoneWhileStreaming,
                hardwareVolumeButtonsControlController = uiState.hardwareVolumeButtonsControlController,
                onVolumeStepChanged = onVolumeStepChanged,
                onMutePhoneWhileStreamingChanged = onMutePhoneWhileStreamingChanged,
                onHardwareVolumeButtonsControlControllerChanged =
                    onHardwareVolumeButtonsControlControllerChanged,
                enabled = !isBtMode
            )
        }

        if (showLogs) {
            LogCard(logText = uiState.logText)
        }
    }

    editorState?.let { state ->
        GamePresetEditorDialog(
            initialState = state,
            onDismiss = { editorState = null },
            onTuningPreviewChanged = onGameModeTuningChanged,
            onResetCurrentTuning = onResetGameModeTuning,
            onSave = { presetId, appPackageName, appLabel, tuning ->
                onSaveGameModePreset(presetId, appPackageName, appLabel, tuning)
                onGameModeTuningChanged(tuning)
                editorState = null
            },
            onDelete = { presetId ->
                if (presetId != null) {
                    onDeleteGameModePreset(presetId)
                }
                editorState = null
            },
            onCopySaved = { presetId ->
                if (presetId != null) {
                    onCopyGameModePresetToClipboard(presetId)
                }
            }
        )
    }
}

@Composable
private fun StatusCard(
    controllerConnected: Boolean,
    isStreaming: Boolean,
    volumeStep: Int,
    gameMode: Boolean,
    activePresetLabel: String?
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    SectionCard(
        title = stringResource(R.string.card_title_status),
        titleTrailing = {
            IconButton(onClick = { showInfoDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.content_desc_status_info),
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
                icon = if (gameMode) Icons.Outlined.SportsEsports else Icons.Outlined.VolumeUp,
                shortLabel = if (gameMode) stringResource(R.string.label_preset) else stringResource(R.string.label_volume),
                value = if (gameMode) {
                    activePresetLabel ?: stringResource(R.string.label_custom)
                } else {
                    stringResource(R.string.label_volume_level_short, volumeStep)
                }
            )
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(text = stringResource(R.string.button_ok))
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
    onApplySpeakerRouteClick: () -> Unit,
    enabled: Boolean
) {
    SectionCard(
        title = stringResource(R.string.card_title_main_actions),
        enabled = enabled
    ) {
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
    gameMode: Boolean,
    gameModeAdaptiveStrength: Boolean,
    gameModePreciseReaction: Boolean,
    onRouteCh1Changed: (Boolean) -> Unit,
    onRouteCh2Changed: (Boolean) -> Unit,
    onRouteCh3Changed: (Boolean) -> Unit,
    onRouteCh4Changed: (Boolean) -> Unit,
    onGameModeChanged: (Boolean) -> Unit,
    onGameModeAdaptiveStrengthChanged: (Boolean) -> Unit,
    onGameModePreciseReactionChanged: (Boolean) -> Unit,
    enabled: Boolean
) {
    SectionCard(
        title = stringResource(R.string.card_title_audio_channels),
        enabled = enabled,
        titleTrailing = {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChannelTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Headset,
                    text = stringResource(R.string.label_channel_abbr),
                    checked = routeCh1,
                    onCheckedChange = onRouteCh1Changed,
                    enabled = enabled && !gameMode
                )
                ChannelTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.VolumeUp,
                    text = stringResource(R.string.label_speaker_abbr),
                    checked = routeCh2,
                    onCheckedChange = onRouteCh2Changed,
                    enabled = enabled && !gameMode
                )
                ChannelTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Waves,
                    text = stringResource(R.string.label_left_vib_abbr),
                    checked = routeCh3,
                    onCheckedChange = onRouteCh3Changed
                )
                ChannelTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Waves,
                    text = stringResource(R.string.label_right_vib_abbr),
                    checked = routeCh4,
                    onCheckedChange = onRouteCh4Changed
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF183055))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SportsEsports,
                        contentDescription = null,
                        tint = Color(0xFFB7D1FF),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.label_game_mode),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Switch(
                    checked = gameMode,
                    onCheckedChange = onGameModeChanged,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            AnimatedVisibility(
                visible = gameMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF132743))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BottomToggleCard(
                        icon = Icons.Outlined.Waves,
                        line1 = stringResource(R.string.label_game_mode_adaptive_strength),
                        line2 = stringResource(R.string.label_game_mode_adaptive_strength_desc),
                        checked = gameModeAdaptiveStrength,
                        onCheckedChange = onGameModeAdaptiveStrengthChanged,
                        enabled = enabled
                    )

                    BottomToggleCard(
                        icon = Icons.Outlined.Tune,
                        line1 = stringResource(R.string.label_game_mode_precise_reaction),
                        line2 = stringResource(R.string.label_game_mode_precise_reaction_desc),
                        checked = gameModePreciseReaction,
                        onCheckedChange = onGameModePreciseReactionChanged,
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
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

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Clip,
                modifier = Modifier.height(40.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameModePresetCard(
    presets: List<GameModePreset>,
    activePresetId: String?,
    currentTuning: GameModeTuning,
    onApplyPreset: (String) -> Unit,
    onImportPreset: () -> Unit,
    onAddPreset: () -> Unit,
    onEditPreset: (GameModePreset) -> Unit,
    onMutePhoneWhileStreamingChanged: (Boolean) -> Unit,
    onHardwareVolumeButtonsControlControllerChanged: (Boolean) -> Unit,
    mutePhoneWhileStreaming: Boolean,
    hardwareVolumeButtonsControlController: Boolean,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(
            title = stringResource(R.string.card_title_preset_game),
            enabled = enabled
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAddPreset,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.button_add_preset))
                }

                Button(
                    onClick = onImportPreset,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1B345C),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.button_paste_import))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (presets.isEmpty()) {
                Text(
                    text = stringResource(R.string.msg_no_game_presets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    presets.forEach { preset ->
                        PresetBubble(
                            preset = preset,
                            selected = preset.id == activePresetId,
                            onClick = { onApplyPreset(preset.id) },
                            onLongClick = { onEditPreset(preset) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            TuningSummaryCard(tuning = currentTuning)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BottomToggleCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.VolumeOff,
                line1 = stringResource(R.string.label_mute_abbr),
                line2 = stringResource(R.string.label_phone_abbr),
                checked = mutePhoneWhileStreaming,
                onCheckedChange = onMutePhoneWhileStreamingChanged,
                enabled = enabled
            )

            BottomToggleCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Settings,
                line1 = stringResource(R.string.label_hw_vol_abbr),
                line2 = stringResource(R.string.label_buttons_abbr),
                checked = hardwareVolumeButtonsControlController,
                onCheckedChange = onHardwareVolumeButtonsControlControllerChanged,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun TuningSummaryCard(tuning: GameModeTuning) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF132743),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF27456F))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.label_current_tuning),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    R.string.label_current_tuning_summary,
                    if (tuning.adaptiveStrengthEnabled) stringResource(R.string.on) else stringResource(R.string.off),
                    if (tuning.preciseReactionEnabled) stringResource(R.string.on) else stringResource(R.string.off),
                    tuning.transientThreshold
                ),
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PresetBubble(
    preset: GameModePreset,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF183055),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else Color(0xFF315487)
        )
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(
                packageName = preset.appPackageName,
                fallbackIcon = Icons.Outlined.SportsEsports
            )
            Text(
                text = preset.appLabel,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
    onHardwareVolumeButtonsControlControllerChanged: (Boolean) -> Unit,
    enabled: Boolean
) {
    val isDistorted = volumeStep >= 8

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionCard(
            title = stringResource(R.string.label_volume),
            enabled = enabled
        ) {
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
                line1 = stringResource(R.string.label_mute_abbr),
                line2 = stringResource(R.string.label_phone_abbr),
                checked = mutePhoneWhileStreaming,
                onCheckedChange = onMutePhoneWhileStreamingChanged,
                enabled = enabled
            )

            BottomToggleCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Settings,
                line1 = stringResource(R.string.label_hw_vol_abbr),
                line2 = stringResource(R.string.label_buttons_abbr),
                checked = hardwareVolumeButtonsControlController,
                onCheckedChange = onHardwareVolumeButtonsControlControllerChanged,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun BottomToggleCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    line1: String,
    line2: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
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

        if (!enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
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

@Composable
private fun AppIcon(
    packageName: String,
    fallbackIcon: ImageVector
) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(width = 56, height = 56)
                .asImageBitmap()
        }.getOrNull()
    }

    if (iconBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = Color(0xFFB7D1FF),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun GamePresetEditorDialog(
    initialState: GamePresetEditorState,
    onDismiss: () -> Unit,
    onTuningPreviewChanged: (GameModeTuning) -> Unit,
    onResetCurrentTuning: () -> Unit,
    onSave: (String?, String, String, GameModeTuning) -> Unit,
    onDelete: (String?) -> Unit,
    onCopySaved: (String?) -> Unit
) {
    var appPackageName by rememberSaveable(initialState.presetId) { mutableStateOf(initialState.appPackageName) }
    var appLabel by rememberSaveable(initialState.presetId) { mutableStateOf(initialState.appLabel) }
    var tuning by remember(initialState.presetId) { mutableStateOf(initialState.tuning) }
    var showAppPicker by remember { mutableStateOf(false) }

    val adaptiveHelp = tuningHelp(
        R.string.help_adaptive_strength_title,
        R.string.help_adaptive_strength_description,
        R.string.help_adaptive_strength_example
    )
    val preciseHelp = tuningHelp(
        R.string.help_precise_reaction_title,
        R.string.help_precise_reaction_description,
        R.string.help_precise_reaction_example
    )
    val bassFollowHelp = tuningHelp(
        R.string.help_bass_follow_title,
        R.string.help_bass_follow_description,
        R.string.help_bass_follow_example
    )
    val fastReactionHelp = tuningHelp(
        R.string.help_fast_reaction_follow_title,
        R.string.help_fast_reaction_follow_description,
        R.string.help_fast_reaction_follow_example
    )
    val slowReactionHelp = tuningHelp(
        R.string.help_slow_reaction_follow_title,
        R.string.help_slow_reaction_follow_description,
        R.string.help_slow_reaction_follow_example
    )
    val adaptiveFloorHelp = tuningHelp(
        R.string.help_adaptive_floor_title,
        R.string.help_adaptive_floor_description,
        R.string.help_adaptive_floor_example
    )
    val adaptiveCeilingHelp = tuningHelp(
        R.string.help_adaptive_ceiling_title,
        R.string.help_adaptive_ceiling_description,
        R.string.help_adaptive_ceiling_example
    )
    val lowThresholdHelp = tuningHelp(
        R.string.help_low_threshold_title,
        R.string.help_low_threshold_description,
        R.string.help_low_threshold_example
    )
    val transientThresholdHelp = tuningHelp(
        R.string.help_transient_threshold_title,
        R.string.help_transient_threshold_description,
        R.string.help_transient_threshold_example
    )
    val strongLowMixHelp = tuningHelp(
        R.string.help_strong_low_mix_title,
        R.string.help_strong_low_mix_description,
        R.string.help_strong_low_mix_example
    )
    val transientLowMixHelp = tuningHelp(
        R.string.help_transient_low_mix_title,
        R.string.help_transient_low_mix_description,
        R.string.help_transient_low_mix_example
    )
    val quietLowMixHelp = tuningHelp(
        R.string.help_quiet_low_mix_title,
        R.string.help_quiet_low_mix_description,
        R.string.help_quiet_low_mix_example
    )
    val precisePunchHelp = tuningHelp(
        R.string.help_precise_punch_title,
        R.string.help_precise_punch_description,
        R.string.help_precise_punch_example
    )
    val softPunchHelp = tuningHelp(
        R.string.help_soft_punch_title,
        R.string.help_soft_punch_description,
        R.string.help_soft_punch_example
    )
    val punchPolarityHelp = tuningHelp(
        R.string.help_punch_polarity_scale_title,
        R.string.help_punch_polarity_scale_description,
        R.string.help_punch_polarity_scale_example
    )
    val softClipHelp = tuningHelp(
        R.string.help_soft_clip_title,
        R.string.help_soft_clip_description,
        R.string.help_soft_clip_example
    )

    LaunchedEffect(tuning) {
        onTuningPreviewChanged(tuning)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF0F1D33),
            border = BorderStroke(1.dp, Color(0xFF315487))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(
                            if (initialState.presetId == null) {
                                R.string.dialog_title_new_preset
                            } else {
                                R.string.dialog_title_edit_preset
                            }
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    if (initialState.presetId != null) {
                        TextButton(onClick = { onCopySaved(initialState.presetId) }) {
                            Text(stringResource(R.string.button_copy))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAppPicker = true },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF183055),
                    border = BorderStroke(1.dp, Color(0xFF315487))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIcon(
                            packageName = appPackageName,
                            fallbackIcon = Icons.Outlined.SportsEsports
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (appLabel.isBlank()) {
                                    stringResource(R.string.label_choose_app_or_game)
                                } else {
                                    appLabel
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (appPackageName.isBlank()) {
                                    stringResource(R.string.label_tap_to_select_target_app)
                                } else {
                                    appPackageName
                                },
                                color = Color.White.copy(alpha = 0.74f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.label_premade_presets),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    premadeGamePresetTemplates.forEach { template ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (tuning == template.tuning) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            } else {
                                Color(0xFF183055)
                            },
                            border = BorderStroke(1.dp, Color(0xFF315487))
                        ) {
                            Text(
                                text = stringResource(template.nameResId),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { tuning = template.tuning }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    EditorToggleCard(
                        title = stringResource(R.string.label_game_mode_adaptive_strength),
                        subtitle = stringResource(R.string.label_game_mode_adaptive_strength_desc),
                        help = adaptiveHelp,
                        checked = tuning.adaptiveStrengthEnabled,
                        onCheckedChange = { tuning = tuning.copy(adaptiveStrengthEnabled = it) }
                    )

                    EditorToggleCard(
                        title = stringResource(R.string.label_game_mode_precise_reaction),
                        subtitle = stringResource(R.string.label_game_mode_precise_reaction_desc),
                        help = preciseHelp,
                        checked = tuning.preciseReactionEnabled,
                        onCheckedChange = { tuning = tuning.copy(preciseReactionEnabled = it) }
                    )

                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_bass_follow),
                        help = bassFollowHelp,
                        value = tuning.bassFollow,
                        range = 0.005f..0.20f,
                        steps = 38,
                        defaultValue = GameModeTuning.DEFAULT.bassFollow,
                        onValueChange = { tuning = tuning.copy(bassFollow = it) },
                        onReset = { tuning = tuning.copy(bassFollow = GameModeTuning.DEFAULT.bassFollow) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_fast_reaction_follow),
                        help = fastReactionHelp,
                        value = tuning.transientFastFollow,
                        range = 0.05f..0.50f,
                        steps = 44,
                        defaultValue = GameModeTuning.DEFAULT.transientFastFollow,
                        onValueChange = { tuning = tuning.copy(transientFastFollow = it) },
                        onReset = { tuning = tuning.copy(transientFastFollow = GameModeTuning.DEFAULT.transientFastFollow) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_slow_reaction_follow),
                        help = slowReactionHelp,
                        value = tuning.transientSlowFollow,
                        range = 0.001f..0.10f,
                        steps = 48,
                        defaultValue = GameModeTuning.DEFAULT.transientSlowFollow,
                        onValueChange = { tuning = tuning.copy(transientSlowFollow = it) },
                        onReset = { tuning = tuning.copy(transientSlowFollow = GameModeTuning.DEFAULT.transientSlowFollow) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_adaptive_floor),
                        help = adaptiveFloorHelp,
                        value = tuning.adaptiveFloor,
                        range = 0f..0.50f,
                        steps = 49,
                        defaultValue = GameModeTuning.DEFAULT.adaptiveFloor,
                        onValueChange = { tuning = tuning.copy(adaptiveFloor = it) },
                        onReset = { tuning = tuning.copy(adaptiveFloor = GameModeTuning.DEFAULT.adaptiveFloor) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_adaptive_ceiling),
                        help = adaptiveCeilingHelp,
                        value = tuning.adaptiveCeiling,
                        range = 0.5f..2.0f,
                        steps = 59,
                        defaultValue = GameModeTuning.DEFAULT.adaptiveCeiling,
                        onValueChange = { tuning = tuning.copy(adaptiveCeiling = it) },
                        onReset = { tuning = tuning.copy(adaptiveCeiling = GameModeTuning.DEFAULT.adaptiveCeiling) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_low_threshold),
                        help = lowThresholdHelp,
                        value = tuning.lowThreshold,
                        range = 0f..8000f,
                        steps = 39,
                        defaultValue = GameModeTuning.DEFAULT.lowThreshold,
                        onValueChange = { tuning = tuning.copy(lowThreshold = it) },
                        onReset = { tuning = tuning.copy(lowThreshold = GameModeTuning.DEFAULT.lowThreshold) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_transient_threshold),
                        help = transientThresholdHelp,
                        value = tuning.transientThreshold,
                        range = 0f..3000f,
                        steps = 29,
                        defaultValue = GameModeTuning.DEFAULT.transientThreshold,
                        onValueChange = { tuning = tuning.copy(transientThreshold = it) },
                        onReset = { tuning = tuning.copy(transientThreshold = GameModeTuning.DEFAULT.transientThreshold) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_strong_low_mix),
                        help = strongLowMixHelp,
                        value = tuning.strongLowMix,
                        range = 0f..1.2f,
                        steps = 59,
                        defaultValue = GameModeTuning.DEFAULT.strongLowMix,
                        onValueChange = { tuning = tuning.copy(strongLowMix = it) },
                        onReset = { tuning = tuning.copy(strongLowMix = GameModeTuning.DEFAULT.strongLowMix) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_transient_low_mix),
                        help = transientLowMixHelp,
                        value = tuning.transientLowMix,
                        range = 0f..1.0f,
                        steps = 49,
                        defaultValue = GameModeTuning.DEFAULT.transientLowMix,
                        onValueChange = { tuning = tuning.copy(transientLowMix = it) },
                        onReset = { tuning = tuning.copy(transientLowMix = GameModeTuning.DEFAULT.transientLowMix) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_quiet_low_mix),
                        help = quietLowMixHelp,
                        value = tuning.quietLowMix,
                        range = 0f..0.15f,
                        steps = 29,
                        defaultValue = GameModeTuning.DEFAULT.quietLowMix,
                        onValueChange = { tuning = tuning.copy(quietLowMix = it) },
                        onReset = { tuning = tuning.copy(quietLowMix = GameModeTuning.DEFAULT.quietLowMix) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_precise_punch),
                        help = precisePunchHelp,
                        value = tuning.precisePunch,
                        range = 0f..2.0f,
                        steps = 39,
                        defaultValue = GameModeTuning.DEFAULT.precisePunch,
                        onValueChange = { tuning = tuning.copy(precisePunch = it) },
                        onReset = { tuning = tuning.copy(precisePunch = GameModeTuning.DEFAULT.precisePunch) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_soft_punch),
                        help = softPunchHelp,
                        value = tuning.softPunch,
                        range = 0f..1.0f,
                        steps = 39,
                        defaultValue = GameModeTuning.DEFAULT.softPunch,
                        onValueChange = { tuning = tuning.copy(softPunch = it) },
                        onReset = { tuning = tuning.copy(softPunch = GameModeTuning.DEFAULT.softPunch) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_punch_polarity_scale),
                        help = punchPolarityHelp,
                        value = tuning.punchPolarityScale,
                        range = 0f..1.5f,
                        steps = 39,
                        defaultValue = GameModeTuning.DEFAULT.punchPolarityScale,
                        onValueChange = { tuning = tuning.copy(punchPolarityScale = it) },
                        onReset = { tuning = tuning.copy(punchPolarityScale = GameModeTuning.DEFAULT.punchPolarityScale) }
                    )
                    TuningSliderCard(
                        title = stringResource(R.string.game_tuning_soft_clip),
                        help = softClipHelp,
                        value = tuning.softClipDenominator,
                        range = 2000f..30000f,
                        steps = 55,
                        defaultValue = GameModeTuning.DEFAULT.softClipDenominator,
                        onValueChange = { tuning = tuning.copy(softClipDenominator = it) },
                        onReset = { tuning = tuning.copy(softClipDenominator = GameModeTuning.DEFAULT.softClipDenominator) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = {
                            if (initialState.presetId == null) {
                                onDismiss()
                            } else {
                                onDelete(initialState.presetId)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (initialState.presetId == null) stringResource(R.string.button_cancel) else stringResource(R.string.button_delete))
                    }

                    TextButton(
                        onClick = {
                            tuning = GameModeTuning.DEFAULT
                            onResetCurrentTuning()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.button_reset_all))
                    }

                    Button(
                        onClick = {
                            if (appPackageName.isNotBlank() && appLabel.isNotBlank()) {
                                onSave(initialState.presetId, appPackageName, appLabel, tuning)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = appPackageName.isNotBlank() && appLabel.isNotBlank()
                    ) {
                        Text(stringResource(R.string.button_save))
                    }
                }
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            selectedPackageName = appPackageName,
            onDismiss = { showAppPicker = false },
            onAppSelected = { selected ->
                appPackageName = selected.packageName
                appLabel = selected.label
                showAppPicker = false
            }
        )
    }
}

@Composable
private fun EditorToggleCard(
    title: String,
    subtitle: String,
    help: TuningHelp,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF183055),
        border = BorderStroke(1.dp, Color(0xFF315487))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { showInfo = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color(0xFF9BC0FF)
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(text = stringResource(R.string.button_ok))
                }
            },
            title = { Text(stringResource(help.titleResId)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(help.descriptionResId))
                    Text(
                        text = stringResource(
                            R.string.label_help_example,
                            stringResource(help.exampleResId)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@Composable
private fun TuningSliderCard(
    title: String,
    help: TuningHelp,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    defaultValue: Float,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }
    SectionCard(title = title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { showInfo = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color(0xFF9BC0FF)
                )
            }
        }

        AppSliderRow(
            label = title,
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            valueDisplay = if (range.endInclusive <= 2.5f) {
                "%.3f".format(value)
            } else {
                value.roundToInt().toString()
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onReset) {
                Text(
                    stringResource(
                        R.string.button_reset_with_value,
                        if (range.endInclusive <= 2.5f) "%.3f".format(defaultValue) else defaultValue.roundToInt().toString()
                    )
                )
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(text = stringResource(R.string.button_ok))
                }
            },
            title = { Text(stringResource(help.titleResId)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(help.descriptionResId))
                    Text(
                        text = stringResource(
                            R.string.label_help_example,
                            stringResource(help.exampleResId)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@Composable
private fun AppPickerDialog(
    selectedPackageName: String,
    onDismiss: () -> Unit,
    onAppSelected: (LaunchableAppInfo) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val apps = remember {
        loadLaunchableApps(context.packageManager)
    }
    val filteredApps = remember(apps, query) {
        if (query.isBlank()) {
            apps
        } else {
            val needle = query.trim().lowercase()
            apps.filter {
                it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F1D33),
            border = BorderStroke(1.dp, Color(0xFF315487))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_title_choose_app),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_search_app)) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .height(420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredApps.forEach { app ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAppSelected(app) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (app.packageName == selectedPackageName) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            } else {
                                Color(0xFF183055)
                            },
                            border = BorderStroke(1.dp, Color(0xFF315487))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AppIcon(
                                    packageName = app.packageName,
                                    fallbackIcon = Icons.Outlined.SportsEsports
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = Color.White.copy(alpha = 0.72f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.button_cancel))
                    }
                }
            }
        }
    }
}

private fun loadLaunchableApps(packageManager: PackageManager): List<LaunchableAppInfo> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolves = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }
    return resolves
        .mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            if (label.isBlank()) return@mapNotNull null
            LaunchableAppInfo(packageName = packageName, label = label)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
