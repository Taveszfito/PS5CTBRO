package com.DueBoysenberry1226.ps5ctbro.ui.vibrate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.DueBoysenberry1226.ps5ctbro.ui.bt.BtVibrationController
import com.DueBoysenberry1226.ps5ctbro.ui.connection.ControllerConnectionRepository
import com.DueBoysenberry1226.ps5ctbro.ui.connection.ControllerConnectionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class VibrationViewModel(application: Application) : AndroidViewModel(application) {

    private val connectionRepository =
        ControllerConnectionRepository.get(application.applicationContext)

    private val usbController = VibrationController(application)
    private val btController = BtVibrationController(application)

    val uiState = connectionRepository.uiState
        .map { it.type }
        .flatMapLatest { type ->
            when (type) {
                ControllerConnectionType.USB -> usbController.uiState
                ControllerConnectionType.BLUETOOTH -> btController.uiState
                ControllerConnectionType.NONE -> btController.uiState
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VibrationUiState()
        )

    fun onScreenVisible() {
        when (connectionRepository.uiState.value.type) {
            ControllerConnectionType.USB -> {
                btController.onScreenHidden()
                usbController.onScreenVisible()
            }

            ControllerConnectionType.BLUETOOTH -> {
                usbController.onScreenHidden()
                btController.onScreenVisible()
            }

            ControllerConnectionType.NONE -> {
                usbController.onScreenHidden()
                btController.onScreenVisible()
            }
        }
    }

    fun onScreenHidden() {
        usbController.onScreenHidden()
        btController.onScreenHidden()
    }

    fun setStrengthLeft(strength: Int) {
        usbController.setStrengthLeft(strength)
        btController.setStrengthLeft(strength)
    }

    fun setStrengthRight(strength: Int) {
        usbController.setStrengthRight(strength)
        btController.setStrengthRight(strength)
    }

    fun setDuration(seconds: Int) {
        usbController.setDuration(seconds)
        btController.setDuration(seconds)
    }

    fun setInfinite(infinite: Boolean) {
        usbController.setInfinite(infinite)
        btController.setInfinite(infinite)
    }

    fun applyVibration(left: Boolean, right: Boolean) {
        when (connectionRepository.uiState.value.type) {
            ControllerConnectionType.USB -> usbController.applyVibration(left, right)
            ControllerConnectionType.BLUETOOTH -> btController.applyVibration(left, right)
            ControllerConnectionType.NONE -> btController.applyVibration(left, right)
        }
    }

    fun stopVibration() {
        usbController.stopVibration()
        btController.stopVibration()
    }

    fun refreshConnection() {
        connectionRepository.refresh()

        when (connectionRepository.uiState.value.type) {
            ControllerConnectionType.USB -> usbController.refreshConnection()
            ControllerConnectionType.BLUETOOTH -> btController.refreshConnection()
            ControllerConnectionType.NONE -> btController.refreshConnection()
        }
    }

    override fun onCleared() {
        usbController.release()
        btController.release()
        super.onCleared()
    }
}