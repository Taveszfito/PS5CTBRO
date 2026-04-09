package com.DueBoysenberry1226.ps5ctbro.ui

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.DueBoysenberry1226.ps5ctbro.audio.AudioUiState
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppDrawerContent
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppTopBar
import com.DueBoysenberry1226.ps5ctbro.ui.screens.PlaceholderScreen
import com.DueBoysenberry1226.ps5ctbro.ui.screens.SpeakerScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    speakerUiState: AudioUiState,
    onStartStreamClick: () -> Unit,
    onStopStreamClick: () -> Unit,
    onApplySpeakerRouteClick: () -> Unit,
    onVolumeStepChanged: (Int) -> Unit,
    onRouteCh1Changed: (Boolean) -> Unit,
    onRouteCh2Changed: (Boolean) -> Unit,
    onRouteCh3Changed: (Boolean) -> Unit,
    onRouteCh4Changed: (Boolean) -> Unit,
    onMutePhoneWhileStreamingChanged: (Boolean) -> Unit,
    onHardwareVolumeButtonsControlControllerChanged: (Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var currentSection by rememberSaveable {
        mutableStateOf(AppSection.SPEAKER)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentSection = currentSection,
                onSectionSelected = { selected ->
                    currentSection = selected
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = currentSection.title,
                    onMenuClick = {
                        scope.launch {
                            if (drawerState.isClosed) {
                                drawerState.open()
                            } else {
                                drawerState.close()
                            }
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
                                onHardwareVolumeButtonsControlControllerChanged =
                                    onHardwareVolumeButtonsControlControllerChanged
                            )
                        }

                        AppSection.SETTINGS -> {
                            PlaceholderScreen(
                                title = "Beállítások",
                                subtitle = "Ez később kerül ide."
                            )
                        }

                        AppSection.DEBUG -> {
                            PlaceholderScreen(
                                title = "Debug",
                                subtitle = "Ez később kerül ide."
                            )
                        }
                    }
                }
            }
        }
    }
}