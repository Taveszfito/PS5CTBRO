package com.DueBoysenberry1226.ps5ctbro.ui.bt

import kotlinx.coroutines.cancel
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import com.DueBoysenberry1226.ps5ctbro.ui.vibrate.VibrationUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class BtVibrationController(
    context: Context
) {
    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(
        VibrationUiState(
            logText = "BT vibration controller készen áll."
        )
    )
    val uiState: StateFlow<VibrationUiState> = _uiState

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var leftJob: Job? = null
    private var rightJob: Job? = null
    private var heartbeatJob: Job? = null

    private var activeVibrator: Vibrator? = null

    init {
        startHeartbeat()
    }

    fun onScreenVisible() {
        refreshConnection()
    }

    fun onScreenHidden() {
        stopVibration()
        _uiState.update {
            it.copy(logText = "BT vibration screen elrejtve.")
        }
    }

    fun refreshConnection() {
        val device = findBtDualSense()

        if (device == null) {
            activeVibrator = null
            _uiState.update {
                it.copy(
                    controllerConnected = false,
                    logText = "Nincs BT DualSense input eszköz."
                )
            }
            return
        }

        val vibrator = getControllerVibrator(device)

        if (vibrator == null || !vibrator.hasVibrator()) {
            activeVibrator = null
            _uiState.update {
                it.copy(
                    controllerConnected = false,
                    logText = "BT DualSense megtalálva, de Android nem adott controller vibrátort."
                )
            }
            return
        }

        activeVibrator = vibrator

        _uiState.update {
            it.copy(
                controllerConnected = true,
                logText = "BT DualSense vibration készen áll. device=${device.name}"
            )
        }
    }

    fun setStrengthLeft(strength: Int) {
        _uiState.update {
            it.copy(strengthLeftPercent = strength.coerceIn(0, 100))
        }

        if (_uiState.value.isLeftActive) updateVibration()
    }

    fun setStrengthRight(strength: Int) {
        _uiState.update {
            it.copy(strengthRightPercent = strength.coerceIn(0, 100))
        }

        if (_uiState.value.isRightActive) updateVibration()
    }

    fun setDuration(seconds: Int) {
        _uiState.update {
            it.copy(durationSeconds = seconds.coerceIn(1, 60))
        }
    }

    fun setInfinite(infinite: Boolean) {
        _uiState.update {
            it.copy(isInfinite = infinite)
        }
    }

    fun applyVibration(left: Boolean, right: Boolean) {
        refreshConnection()

        if (activeVibrator == null) {
            _uiState.update {
                it.copy(logText = "BT vibration nem indítható: nincs elérhető controller vibrator.")
            }
            return
        }

        val state = _uiState.value

        if (left) {
            leftJob?.cancel()
            _uiState.update { it.copy(isLeftActive = true) }

            if (!state.isInfinite) {
                leftJob = scope.launch {
                    delay(state.durationSeconds * 1000L)
                    _uiState.update { it.copy(isLeftActive = false) }
                    updateVibration()
                }
            }
        }

        if (right) {
            rightJob?.cancel()
            _uiState.update { it.copy(isRightActive = true) }

            if (!state.isInfinite) {
                rightJob = scope.launch {
                    delay(state.durationSeconds * 1000L)
                    _uiState.update { it.copy(isRightActive = false) }
                    updateVibration()
                }
            }
        }

        updateVibration()

        _uiState.update {
            it.copy(logText = "BT vibration apply: left=$left right=$right")
        }
    }

    fun stopVibration() {
        leftJob?.cancel()
        rightJob?.cancel()

        _uiState.update {
            it.copy(
                isLeftActive = false,
                isRightActive = false
            )
        }

        activeVibrator?.cancel()

        _uiState.update {
            it.copy(logText = "BT vibration stopped.")
        }
    }

    private fun updateVibration() {
        val vibrator = activeVibrator ?: return
        val state = _uiState.value

        val leftAmp = if (state.isLeftActive) {
            percentToAmplitude(state.strengthLeftPercent)
        } else {
            0
        }

        val rightAmp = if (state.isRightActive) {
            percentToAmplitude(state.strengthRightPercent)
        } else {
            0
        }

        val amplitude = max(leftAmp, rightAmp)

        if (amplitude <= 0) {
            vibrator.cancel()
            return
        }

        val durationMs = if (state.isInfinite) {
            1000L
        } else {
            state.durationSeconds.coerceIn(1, 60) * 1000L
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    durationMs,
                    amplitude
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()

        heartbeatJob = scope.launch {
            while (isActive) {
                val state = _uiState.value

                if (state.isInfinite && (state.isLeftActive || state.isRightActive)) {
                    updateVibration()
                }

                delay(750L)
            }
        }
    }

    private fun getControllerVibrator(device: InputDevice): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val ids = device.vibratorManager.vibratorIds

            if (ids.isEmpty()) {
                null
            } else {
                device.vibratorManager.defaultVibrator
            }
        } else {
            @Suppress("DEPRECATION")
            device.vibrator
        }
    }

    private fun findBtDualSense(): InputDevice? {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue

            if (isBtDualSense(device)) {
                return device
            }
        }

        return null
    }

    private fun isBtDualSense(device: InputDevice): Boolean {
        val name = device.name.orEmpty().lowercase()

        val isGamepad =
            device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

        if (!isGamepad) return false

        return device.vendorId == SONY_VENDOR_ID ||
                name.contains("dualsense") ||
                name.contains("wireless controller") ||
                name.contains("dualshock")
    }

    private fun percentToAmplitude(percent: Int): Int {
        return ((percent.coerceIn(0, 100) / 100f) * 255f)
            .toInt()
            .coerceIn(1, 255)
    }

    fun release() {
        stopVibration()
        heartbeatJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val SONY_VENDOR_ID = 0x054C
    }
}