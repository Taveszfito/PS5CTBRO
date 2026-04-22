package com.DueBoysenberry1226.ps5ctbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggerConfig
import com.DueBoysenberry1226.ps5ctbro.adaptive.AdaptiveTriggersUiState
import com.DueBoysenberry1226.ps5ctbro.audio.AudioUiState
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppDrawerContent
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppTopBar
import com.DueBoysenberry1226.ps5ctbro.ui.components.MiniInfoPill
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.InputTestUiState
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedConfig
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedUiState
import com.DueBoysenberry1226.ps5ctbro.ui.screens.AdaptiveTriggersScreen
import com.DueBoysenberry1226.ps5ctbro.ui.screens.InputTestScreen
import com.DueBoysenberry1226.ps5ctbro.ui.screens.LedScreen
import com.DueBoysenberry1226.ps5ctbro.ui.screens.MotionSensorsScreen
import com.DueBoysenberry1226.ps5ctbro.ui.screens.SettingsScreen
import com.DueBoysenberry1226.ps5ctbro.ui.screens.SpeakerScreen
import com.DueBoysenberry1226.ps5ctbro.ui.screens.VibrationScreen
import com.DueBoysenberry1226.ps5ctbro.ui.settings.SettingsUiState
import com.DueBoysenberry1226.ps5ctbro.ui.theme.BlueBright
import com.DueBoysenberry1226.ps5ctbro.ui.theme.Midnight700
import com.DueBoysenberry1226.ps5ctbro.ui.theme.Midnight800
import com.DueBoysenberry1226.ps5ctbro.ui.vibrate.VibrationUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    speakerUiState: AudioUiState,
    adaptiveTriggersUiState: AdaptiveTriggersUiState,
    ledUiState: LedUiState,
    inputTestUiState: InputTestUiState,
    vibrationUiState: VibrationUiState,
    settingsUiState: SettingsUiState,
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
    onSpeakerScreenVisible: () -> Unit,
    onSpeakerScreenHidden: () -> Unit,
    onLeftTriggerChanged: (AdaptiveTriggerConfig) -> Unit,
    onRightTriggerChanged: (AdaptiveTriggerConfig) -> Unit,
    onAdaptiveTriggersScreenVisible: () -> Unit,
    onAdaptiveTriggersScreenHidden: () -> Unit,
    onApplyTriggersClick: () -> Unit,
    onRefreshTriggerConnectionClick: () -> Unit,
    onResetTriggersClick: () -> Unit,
    onLedConfigChanged: (LedConfig) -> Unit,
    onLedScreenVisible: () -> Unit,
    onLedScreenHidden: () -> Unit,
    onApplyLedClick: () -> Unit,
    onRefreshLedConnectionClick: () -> Unit,
    onResetLedClick: () -> Unit,
    onInputTestScreenVisible: () -> Unit,
    onInputTestScreenHidden: () -> Unit,
    onRefreshInputTestConnectionClick: () -> Unit,
    onVibrationScreenVisible: () -> Unit,
    onVibrationScreenHidden: () -> Unit,
    onVibrationStrengthLeftChanged: (Int) -> Unit,
    onVibrationStrengthRightChanged: (Int) -> Unit,
    onVibrationDurationChanged: (Int) -> Unit,
    onVibrationInfiniteChanged: (Boolean) -> Unit,
    onApplyVibrationLeft: () -> Unit,
    onApplyVibrationRight: () -> Unit,
    onStopVibrationClick: () -> Unit,
    onRefreshVibrationConnectionClick: () -> Unit,
    onLanguageSelected: (String) -> Unit,
    onGainChanged: (Float) -> Unit,
    onShowLogWindowsChanged: (Boolean) -> Unit
) {
    val showLogs = settingsUiState.showLogWindows

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var currentSection by rememberSaveable {
        mutableStateOf(AppSection.SPEAKER)
    }

    val adaptiveScreenVisible = currentSection == AppSection.ADAPTIVE_TRIGGERS
    val ledScreenVisible = currentSection == AppSection.LEDS
    val inputTestVisible = currentSection == AppSection.INPUT_TEST || currentSection == AppSection.MOTION_SENSORS
    val vibrationScreenVisible = currentSection == AppSection.VIBRATE_TEST
    val speakerScreenVisible = currentSection == AppSection.SPEAKER

    DisposableEffect(speakerScreenVisible) {
        if (speakerScreenVisible) onSpeakerScreenVisible() else onSpeakerScreenHidden()
        onDispose {
            if (speakerScreenVisible) onSpeakerScreenHidden()
        }
    }

    DisposableEffect(adaptiveScreenVisible) {
        if (adaptiveScreenVisible) onAdaptiveTriggersScreenVisible() else onAdaptiveTriggersScreenHidden()
        onDispose {
            if (adaptiveScreenVisible) onAdaptiveTriggersScreenHidden()
        }
    }

    DisposableEffect(ledScreenVisible) {
        if (ledScreenVisible) onLedScreenVisible() else onLedScreenHidden()
        onDispose {
            if (ledScreenVisible) onLedScreenHidden()
        }
    }

    DisposableEffect(inputTestVisible) {
        if (inputTestVisible) onInputTestScreenVisible() else onInputTestScreenHidden()
        onDispose {
            if (inputTestVisible) onInputTestScreenHidden()
        }
    }

    DisposableEffect(vibrationScreenVisible) {
        if (vibrationScreenVisible) onVibrationScreenVisible() else onVibrationScreenHidden()
        onDispose {
            if (vibrationScreenVisible) onVibrationScreenHidden()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        BlueBright.copy(alpha = 0.18f),
                        Midnight700.copy(alpha = 0.45f),
                        Midnight800,
                        Color(0xFF040714)
                    )
                )
            )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                AppDrawerContent(
                    currentSection = currentSection,
                    onSectionSelected = { selected ->
                        currentSection = selected
                        scope.launch { drawerState.close() }
                    },
                    onCloseClick = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    AppTopBar(
                        title = stringResource(currentSection.titleRes),
                        onMenuClick = {
                            scope.launch {
                                if (drawerState.isClosed) drawerState.open() else drawerState.close()
                            }
                        },
                        actions = {
                            if (currentSection == AppSection.MOTION_SENSORS) {
                                MiniInfoPill(
                                    text = if (inputTestUiState.controllerConnected) "● Aktív" else "● Offline",
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .clickable(onClick = onRefreshInputTestConnectionClick)
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentSection) {
                            AppSection.SPEAKER -> {
                                SpeakerScreen(
                                    uiState = speakerUiState,
                                    onStartStreamClick = onStartStreamClick,
                                    onStopStreamClick = onStopStreamClick,
                                    onApplySpeakerRouteClick = onApplySpeakerRouteClick,
                                    onVolumeStepChanged = onVolumeStepChanged,
                                    onRouteCh1Changed = onRouteCh1Changed,
                                    onRouteCh2Changed = onRouteCh2Changed,
                                    onRouteCh3Changed = onRouteCh3Changed,
                                    onRouteCh4Changed = onRouteCh4Changed,
                                    onMutePhoneWhileStreamingChanged = onMutePhoneWhileStreamingChanged,
                                    onHardwareVolumeButtonsControlControllerChanged = onHardwareVolumeButtonsControlControllerChanged,
                                    showLogs = showLogs
                                )
                            }

                            AppSection.ADAPTIVE_TRIGGERS -> {
                                AdaptiveTriggersScreen(
                                    uiState = adaptiveTriggersUiState,
                                    onLeftTriggerChanged = onLeftTriggerChanged,
                                    onRightTriggerChanged = onRightTriggerChanged,
                                    onApplyClick = onApplyTriggersClick,
                                    onRefreshConnectionClick = onRefreshTriggerConnectionClick,
                                    onResetClick = onResetTriggersClick,
                                    showLogs = showLogs
                                )
                            }

                            AppSection.LEDS -> {
                                LedScreen(
                                    uiState = ledUiState,
                                    onConfigChanged = onLedConfigChanged,
                                    onApplyClick = onApplyLedClick,
                                    onRefreshConnectionClick = onRefreshLedConnectionClick,
                                    onResetClick = onResetLedClick
                                )
                            }

                            AppSection.INPUT_TEST -> {
                                InputTestScreen(
                                    uiState = inputTestUiState,
                                    onRefreshConnectionClick = onRefreshInputTestConnectionClick,
                                    showLogs = showLogs
                                )
                            }

                            AppSection.MOTION_SENSORS -> {
                                MotionSensorsScreen(
                                    uiState = inputTestUiState,
                                    onRefreshConnectionClick = onRefreshInputTestConnectionClick,
                                    showLogs = showLogs
                                )
                            }

                            AppSection.VIBRATE_TEST -> {
                                VibrationScreen(
                                    uiState = vibrationUiState,
                                    onStrengthLeftChanged = onVibrationStrengthLeftChanged,
                                    onStrengthRightChanged = onVibrationStrengthRightChanged,
                                    onDurationChanged = onVibrationDurationChanged,
                                    onInfiniteChanged = onVibrationInfiniteChanged,
                                    onApplyLeft = onApplyVibrationLeft,
                                    onApplyRight = onApplyVibrationRight,
                                    onStopClick = onStopVibrationClick,
                                    onRefreshConnectionClick = onRefreshVibrationConnectionClick,
                                    showLogs = showLogs
                                )
                            }

                            AppSection.SETTINGS -> {
                                SettingsScreen(
                                    uiState = settingsUiState,
                                    onLanguageSelected = onLanguageSelected,
                                    onGainChanged = onGainChanged,
                                    onShowLogWindowsChanged = onShowLogWindowsChanged
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}