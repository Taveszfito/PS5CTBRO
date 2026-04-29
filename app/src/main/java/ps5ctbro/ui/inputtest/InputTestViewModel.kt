package com.DueBoysenberry1226.ps5ctbro.ui.inputtest

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.app.Application
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.DueBoysenberry1226.ps5ctbro.ui.bt.BtInputTestController
import com.DueBoysenberry1226.ps5ctbro.ui.connection.ControllerConnectionRepository
import com.DueBoysenberry1226.ps5ctbro.ui.connection.ControllerConnectionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class InputTestViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val connectionRepository =
        ControllerConnectionRepository.get(application.applicationContext)

    private val usbController: InputTestController =
        InputTestControllerImpl(application.applicationContext)

    private val btController =
        BtInputTestController(application.applicationContext)

    private val _btTouchpadSensitivity = MutableStateFlow(6f)
    val btTouchpadSensitivity: StateFlow<Float> = _btTouchpadSensitivity.asStateFlow()

    fun setBtTouchpadSensitivity(value: Float) {
        val fixedValue = value.coerceIn(1f, 20f)
        _btTouchpadSensitivity.value = fixedValue
        btController.setTouchpadSensitivity(fixedValue)
    }

    val uiState = connectionRepository.uiState
        .map { it.type }
        .flatMapLatest { type ->
            when (type) {
                ControllerConnectionType.BLUETOOTH -> btController.uiState
                ControllerConnectionType.USB -> usbController.uiState
                ControllerConnectionType.NONE -> btController.uiState
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InputTestUiState()
        )

    fun onScreenVisible() {
        when (connectionRepository.uiState.value.type) {
            ControllerConnectionType.BLUETOOTH -> {
                usbController.onScreenHidden()
                btController.onScreenVisible()
            }

            ControllerConnectionType.USB -> {
                btController.onScreenHidden()
                usbController.onScreenVisible()
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

    fun refreshConnection() {
        connectionRepository.refresh()

        when (connectionRepository.uiState.value.type) {
            ControllerConnectionType.BLUETOOTH -> {
                usbController.onScreenHidden()
                btController.refreshConnection()
            }

            ControllerConnectionType.USB -> {
                btController.onScreenHidden()
                usbController.refreshConnection()
            }

            ControllerConnectionType.NONE -> {
                usbController.onScreenHidden()
                btController.refreshConnection()
            }
        }
    }

    fun onBluetoothMotionEvent(event: MotionEvent): Boolean {
        if (connectionRepository.uiState.value.type != ControllerConnectionType.BLUETOOTH) {
            return false
        }

        return btController.onMotionEvent(event) ||
                btController.onTouchpadLikeMotionEvent(event)
    }

    fun onBluetoothKeyEvent(event: KeyEvent): Boolean {
        if (connectionRepository.uiState.value.type != ControllerConnectionType.BLUETOOTH) {
            return false
        }

        return btController.onKeyEvent(event)
    }

    override fun onCleared() {
        usbController.release()
        btController.release()
        super.onCleared()
    }
}