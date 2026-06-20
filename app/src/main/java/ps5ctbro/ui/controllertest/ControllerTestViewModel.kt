package com.DueBoysenberry1226.ps5ctbro.ui.controllertest

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class ControllerTestViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val controller = ControllerTestUsbController(application.applicationContext)

    val uiState = controller.uiState

    fun onScreenVisible() {
        controller.onScreenVisible()
    }

    fun onScreenHidden() {
        controller.onScreenHidden()
    }

    fun refreshConnection() {
        controller.refreshConnection()
    }

    fun setTestRunning(running: Boolean) {
        controller.setTestRunning(running)
    }

    fun setLightEnabled(enabled: Boolean) {
        controller.setLightEnabled(enabled)
    }

    fun setRumbleEnabled(enabled: Boolean) {
        controller.setRumbleEnabled(enabled)
    }

    fun setMicLedEnabled(enabled: Boolean) {
        controller.setMicLedEnabled(enabled)
    }

    fun setPlayerLedEnabled(index: Int, enabled: Boolean) {
        controller.setPlayerLedEnabled(index, enabled)
    }

    fun setRed(value: Int) {
        controller.setRed(value)
    }

    fun setGreen(value: Int) {
        controller.setGreen(value)
    }

    fun setBlue(value: Int) {
        controller.setBlue(value)
    }

    fun setLeftRumblePercent(value: Int) {
        controller.setLeftRumblePercent(value)
    }

    fun setRightRumblePercent(value: Int) {
        controller.setRightRumblePercent(value)
    }

    fun setSendIntervalMs(value: Int) {
        controller.setSendIntervalMs(value)
    }

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}
