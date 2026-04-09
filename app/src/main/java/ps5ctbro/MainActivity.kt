package com.DueBoysenberry1226.ps5ctbro

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.DueBoysenberry1226.ps5ctbro.service.MediaProjectionForegroundService
import com.DueBoysenberry1226.ps5ctbro.ui.AppRoot
import com.DueBoysenberry1226.ps5ctbro.ui.speaker.SpeakerViewModel
import com.DueBoysenberry1226.ps5ctbro.ui.theme.PS5CTBroTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SpeakerViewModel by viewModels()

    private val recordAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCapturePermission()
            } else {
                viewModel.onRecordAudioPermissionDenied()
            }
        }

    private val capturePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                viewModel.onCapturePermissionGranted(result.resultCode, result.data!!)
            } else {
                stopService(Intent(this, MediaProjectionForegroundService::class.java))
                viewModel.onCapturePermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PS5CTBroTheme {
                val speakerUiState by viewModel.uiState.collectAsStateWithLifecycle()

                AppRoot(
                    speakerUiState = speakerUiState,
                    onStartStreamClick = ::handleStartStreamClick,
                    onStopStreamClick = viewModel::stopStreaming,
                    onApplySpeakerRouteClick = viewModel::applySpeakerRoute,
                    onVolumeStepChanged = viewModel::setVolumeStep,
                    onRouteCh1Changed = { viewModel.setChannelEnabled(channel = 1, enabled = it) },
                    onRouteCh2Changed = { viewModel.setChannelEnabled(channel = 2, enabled = it) },
                    onRouteCh3Changed = { viewModel.setChannelEnabled(channel = 3, enabled = it) },
                    onRouteCh4Changed = { viewModel.setChannelEnabled(channel = 4, enabled = it) },
                    onMutePhoneWhileStreamingChanged = viewModel::setMutePhoneWhileStreaming,
                    onHardwareVolumeButtonsControlControllerChanged =
                        viewModel::setHardwareVolumeButtonsControlController
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (viewModel.handleHardwareVolumeButton(+1)) return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (viewModel.handleHardwareVolumeButton(-1)) return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

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
}