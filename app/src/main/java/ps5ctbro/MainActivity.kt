package com.DueBoysenberry1226.ps5ctbro

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.media.session.MediaController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.DueBoysenberry1226.ps5ctbro.adaptive.TriggerSide
import com.DueBoysenberry1226.ps5ctbro.service.MediaProjectionForegroundService
import com.DueBoysenberry1226.ps5ctbro.ui.AppRoot
import com.DueBoysenberry1226.ps5ctbro.ui.adaptive.AdaptiveTriggersViewModel
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.InputTestViewModel
import com.DueBoysenberry1226.ps5ctbro.ui.led.LedViewModel
import com.DueBoysenberry1226.ps5ctbro.ui.settings.SettingsViewModel
import com.DueBoysenberry1226.ps5ctbro.ui.speaker.SpeakerViewModel
import com.DueBoysenberry1226.ps5ctbro.ui.vibrate.VibrationViewModel
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.DueBoysenberry1226.ps5ctbro.ui.theme.PS5CTBroTheme

class MainActivity : AppCompatActivity() {

    private val viewModel: SpeakerViewModel by viewModels()
    private val adaptiveTriggersViewModel: AdaptiveTriggersViewModel by viewModels()
    private val ledViewModel: LedViewModel by viewModels()
    private val inputTestViewModel: InputTestViewModel by viewModels()
    private val vibrationViewModel: VibrationViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                checkNotificationPermission()
            } else {
                viewModel.onRecordAudioPermissionDenied()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            launchCapturePermission()
        }

    private val capturePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                startStreamingInService(result.resultCode, result.data!!)
            } else {
                stopService(Intent(this, MediaProjectionForegroundService::class.java))
                viewModel.onCapturePermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Sync system volume UI with our MediaSession
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                state.sessionToken?.let { token ->
                    val controller = MediaController(this@MainActivity, token)
                    setMediaController(controller)
                } ?: run {
                    setMediaController(null)
                }
            }
        }

        setContent {
            PS5CTBroTheme {
                val speakerUiState by viewModel.uiState.collectAsStateWithLifecycle()
                val adaptiveTriggersUiState by adaptiveTriggersViewModel.uiState.collectAsStateWithLifecycle()
                val ledUiState by ledViewModel.uiState.collectAsStateWithLifecycle()
                val inputTestUiState by inputTestViewModel.uiState.collectAsStateWithLifecycle()
                val vibrationUiState by vibrationViewModel.uiState.collectAsStateWithLifecycle()
                val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

                AppRoot(
                    speakerUiState = speakerUiState,
                    adaptiveTriggersUiState = adaptiveTriggersUiState,
                    ledUiState = ledUiState,
                    inputTestUiState = inputTestUiState,
                    vibrationUiState = vibrationUiState,
                    settingsUiState = settingsUiState,
                    onStartStreamClick = ::handleStartStreamClick,
                    onStopStreamClick = ::handleStopStreamClick,
                    onApplySpeakerRouteClick = viewModel::applySpeakerRoute,
                    onVolumeStepChanged = viewModel::setVolumeStep,
                    onRouteCh1Changed = { enabled ->
                        viewModel.setChannelEnabled(channel = 1, enabled = enabled)
                    },
                    onRouteCh2Changed = { enabled ->
                        viewModel.setChannelEnabled(channel = 2, enabled = enabled)
                    },
                    onRouteCh3Changed = { enabled ->
                        viewModel.setChannelEnabled(channel = 3, enabled = enabled)
                    },
                    onRouteCh4Changed = { enabled ->
                        viewModel.setChannelEnabled(channel = 4, enabled = enabled)
                    },
                    onMutePhoneWhileStreamingChanged = viewModel::setMutePhoneWhileStreaming,
                    onHardwareVolumeButtonsControlControllerChanged =
                        viewModel::setHardwareVolumeButtonsControlController,
                    onLeftTriggerChanged = { config ->
                        adaptiveTriggersViewModel.updateTriggerConfig(TriggerSide.LEFT, config)
                    },
                    onRightTriggerChanged = { config ->
                        adaptiveTriggersViewModel.updateTriggerConfig(TriggerSide.RIGHT, config)
                    },
                    onAdaptiveTriggersScreenVisible = adaptiveTriggersViewModel::onScreenVisible,
                    onAdaptiveTriggersScreenHidden = adaptiveTriggersViewModel::onScreenHidden,
                    onApplyTriggersClick = adaptiveTriggersViewModel::applyCurrentState,
                    onRefreshTriggerConnectionClick = adaptiveTriggersViewModel::refreshConnection,
                    onResetTriggersClick = adaptiveTriggersViewModel::resetTriggers,
                    onLedConfigChanged = ledViewModel::updateConfig,
                    onLedScreenVisible = ledViewModel::onScreenVisible,
                    onLedScreenHidden = ledViewModel::onScreenHidden,
                    onApplyLedClick = ledViewModel::applyCurrentState,
                    onRefreshLedConnectionClick = ledViewModel::refreshConnection,
                    onResetLedClick = ledViewModel::resetToDefault,
                    onInputTestScreenVisible = inputTestViewModel::onScreenVisible,
                    onInputTestScreenHidden = inputTestViewModel::onScreenHidden,
                    onRefreshInputTestConnectionClick = inputTestViewModel::refreshConnection,
                    onVibrationScreenVisible = vibrationViewModel::onScreenVisible,
                    onVibrationScreenHidden = vibrationViewModel::onScreenHidden,
                    onVibrationStrengthLeftChanged = vibrationViewModel::setStrengthLeft,
                    onVibrationStrengthRightChanged = vibrationViewModel::setStrengthRight,
                    onVibrationDurationChanged = vibrationViewModel::setDuration,
                    onVibrationInfiniteChanged = vibrationViewModel::setInfinite,
                    onApplyVibrationLeft = { vibrationViewModel.applyVibration(left = true, right = false) },
                    onApplyVibrationRight = { vibrationViewModel.applyVibration(left = false, right = true) },
                    onStopVibrationClick = vibrationViewModel::stopVibration,
                    onRefreshVibrationConnectionClick = vibrationViewModel::refreshConnection,
                    onLanguageSelected = settingsViewModel::setLanguage,
                    onGainChanged = settingsViewModel::setAudioGain
                )
            }
        }
    }

    // Removed manual volume key interception to allow system to show the custom MediaSession volume slider

    private fun handleStartStreamClick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            viewModel.onUnsupportedAndroidVersion()
            return
        }

        val hasRecordAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasRecordAudioPermission) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        launchCapturePermission()
    }

    private fun launchCapturePermission() {
        startMediaProjectionService()
        capturePermissionLauncher.launch(viewModel.createScreenCaptureIntent())
    }

    private fun startMediaProjectionService() {
        val serviceIntent = Intent(this, MediaProjectionForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun startStreamingInService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, MediaProjectionForegroundService::class.java).apply {
            action = MediaProjectionForegroundService.ACTION_START
            putExtra(MediaProjectionForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MediaProjectionForegroundService.EXTRA_DATA, data)
        }
        startService(serviceIntent)
    }

    // Removed manual volume key interception to allow system to show the custom MediaSession volume slider

    private fun handleStopStreamClick() {
        val serviceIntent = Intent(this, MediaProjectionForegroundService::class.java).apply {
            action = MediaProjectionForegroundService.ACTION_STOP
        }
        startService(serviceIntent)
        viewModel.stopStreaming()
    }
}
