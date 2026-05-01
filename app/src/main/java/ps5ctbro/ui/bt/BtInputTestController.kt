package com.DueBoysenberry1226.ps5ctbro.ui.bt

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.DueBoysenberry1226.ps5ctbro.audio.TouchpadPoint
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.InputTestController
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.InputTestUiState
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.StickState
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.TriggerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt

class BtInputTestController(
    context: Context
) : InputTestController {

    private val appContext = context.applicationContext
    private val inputManager =
        appContext.getSystemService(Context.INPUT_SERVICE) as InputManager

    private val _uiState = MutableStateFlow(
        InputTestUiState(
            logText = "BT input test készen áll."
        )
    )

    override val uiState: StateFlow<InputTestUiState> = _uiState

    private var screenVisible = false
    private val pressedKeys = linkedSetOf<String>()

    private var virtualTouchX = 960f
    private var virtualTouchY = 540f

    private var touchpadSensitivity = 6f

    fun setTouchpadSensitivity(value: Float) {
        touchpadSensitivity = value.coerceIn(1f, 20f)
    }

    override fun onScreenVisible() {
        screenVisible = true
        refreshConnection()
    }

    override fun onScreenHidden() {
        screenVisible = false
        pressedKeys.clear()
        _uiState.update {
            it.copy(
                controllerConnected = false,
                pressedButtons = emptyList(),
                logText = "BT input test screen elrejtve."
            )
        }
    }

    override fun refreshConnection() {
        val connected = findBtDualSense() != null

        _uiState.update {
            it.copy(
                controllerConnected = connected,
                logText = if (connected) {
                    "BT DualSense aktív. Android input API-ról olvasva."
                } else {
                    "Nincs aktív BT DualSense input eszköz."
                },
                rawReportInfo = if (connected) "source=Android InputDevice / BT" else "-"
            )
        }
    }

    override fun release() {
        pressedKeys.clear()
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (!screenVisible) return false
        if (!isDualSenseEventDevice(event.device)) return false
        if (!isGamepadOrJoystick(event)) return false

        val leftX = axis(event, MotionEvent.AXIS_X)
        val leftY = axis(event, MotionEvent.AXIS_Y)

        val rightX = firstNonZeroAxis(
            event,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RX
        )

        val rightY = firstNonZeroAxis(
            event,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_RY
        )

        val l2 = firstNonZeroAxis(
            event,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_BRAKE
        )

        val r2 = firstNonZeroAxis(
            event,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_GAS
        )

        val dpadX = axis(event, MotionEvent.AXIS_HAT_X)
        val dpadY = axis(event, MotionEvent.AXIS_HAT_Y)

        rebuildDpadButtons(dpadX, dpadY)

        _uiState.update {
            it.copy(
                controllerConnected = true,
                leftStick = stickFromAxis(leftX, leftY),
                rightStick = stickFromAxis(rightX, rightY),
                l2 = triggerFromAxis(l2),
                r2 = triggerFromAxis(r2),
                pressedButtons = pressedKeys.toList(),
                logText = "BT input aktív.",
                rawReportInfo = buildString {
                    appendLine(
                        "BT motion | lx=${fmt(leftX)} ly=${fmt(leftY)} " +
                                "rx=${fmt(rightX)} ry=${fmt(rightY)} " +
                                "l2=${fmt(l2)} r2=${fmt(r2)}"
                    )
                    append(buildBtAxisDebug(event))
                }
            )
        }

        return true
    }

    fun onTouchpadLikeMotionEvent(event: MotionEvent): Boolean {
        if (!screenVisible) return false
        if (!isDualSenseEventDevice(event.device)) return false

        val isTouchLike =
            event.source and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD ||
                    event.source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
                    event.source and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN

        if (!isTouchLike) return false

        val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)

        val hasRelative =
            abs(dx) > 0.001f || abs(dy) > 0.001f

        if (hasRelative) {
            virtualTouchX = (virtualTouchX + dx * touchpadSensitivity).coerceIn(0f, 1919f)
            virtualTouchY = (virtualTouchY + dy * touchpadSensitivity).coerceIn(0f, 1079f)
        } else {
            virtualTouchX = event.x.coerceIn(0f, 1919f)
            virtualTouchY = event.y.coerceIn(0f, 1079f)
        }

        val active = event.action != MotionEvent.ACTION_UP &&
                event.action != MotionEvent.ACTION_CANCEL

        _uiState.update {
            it.copy(
                controllerConnected = true,
                touch1 = TouchpadPoint(
                    x = virtualTouchX.roundToInt(),
                    y = virtualTouchY.roundToInt(),
                    isActive = active
                ),
                touch2 = TouchpadPoint(),
                logText = "BT touchpad relatív mozgás érzékelve.",
                rawReportInfo = "BT touch-like | dx=${fmt(dx)} dy=${fmt(dy)} " +
                        "x=${virtualTouchX.roundToInt()} y=${virtualTouchY.roundToInt()} active=$active"
            )
        }

        return true
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!screenVisible) return false
        if (!isDualSenseEventDevice(event.device)) return false
        if (!isGamepadKey(event.keyCode)) return false

        val label = keyLabel(event.keyCode) ?: return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> pressedKeys += label
            KeyEvent.ACTION_UP -> pressedKeys -= label
            else -> return false
        }

        _uiState.update {
            it.copy(
                controllerConnected = true,
                pressedButtons = pressedKeys.toList(),
                logText = "BT gomb input aktív.",
                rawReportInfo = "BT key | keyCode=${event.keyCode} ${KeyEvent.keyCodeToString(event.keyCode)}"
            )
        }

        return true
    }

    private fun findBtDualSense(): InputDevice? {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            if (isDualSenseEventDevice(device)) return device
        }

        return null
    }

    private fun isDualSenseEventDevice(device: InputDevice?): Boolean {
        if (device == null) return false

        val name = device.name.orEmpty().lowercase()

        val isGamepad =
            device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                    device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
                    device.sources and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD ||
                    device.sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE

        if (!isGamepad) return false

        return device.vendorId == SONY_VENDOR_ID ||
                name.contains("dualsense") ||
                name.contains("wireless controller") ||
                name.contains("dualshock")
    }

    private fun isGamepadOrJoystick(event: MotionEvent): Boolean {
        return event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
                event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
    }

    private fun axis(event: MotionEvent, axis: Int): Float {
        return event.getAxisValue(axis).coerceIn(-1f, 1f)
    }

    private fun firstNonZeroAxis(
        event: MotionEvent,
        primary: Int,
        fallback: Int
    ): Float {
        val a = axis(event, primary)
        if (abs(a) > 0.001f) return a
        return axis(event, fallback)
    }

    private fun stickFromAxis(
        x: Float,
        y: Float
    ): StickState {
        val percentX = axisFloatToPercent(x)
        val percentY = axisFloatToPercent(y)

        return StickState(
            rawX = axisFloatToRawByte(x),
            rawY = axisFloatToRawByte(y),
            percentX = percentX,
            percentY = percentY
        )
    }

    private fun triggerFromAxis(value: Float): TriggerState {
        val normalized = value.coerceIn(0f, 1f)
        val raw = (normalized * 255f).roundToInt().coerceIn(0, 255)

        return TriggerState(
            rawValue = raw,
            percent = (normalized * 100f).roundToInt().coerceIn(0, 100)
        )
    }

    private fun axisFloatToRawByte(value: Float): Int {
        return ((value.coerceIn(-1f, 1f) + 1f) * 127.5f)
            .roundToInt()
            .coerceIn(0, 255)
    }

    private fun axisFloatToPercent(value: Float): Int {
        val percent = (value.coerceIn(-1f, 1f) * 100f).roundToInt()

        return when {
            abs(percent) <= 1 -> 0
            else -> percent.coerceIn(-100, 100)
        }
    }

    private fun rebuildDpadButtons(
        dpadX: Float,
        dpadY: Float
    ) {
        pressedKeys.remove("D-pad Up")
        pressedKeys.remove("D-pad Down")
        pressedKeys.remove("D-pad Left")
        pressedKeys.remove("D-pad Right")

        if (dpadY < -0.5f) pressedKeys += "D-pad Up"
        if (dpadY > 0.5f) pressedKeys += "D-pad Down"
        if (dpadX < -0.5f) pressedKeys += "D-pad Left"
        if (dpadX > 0.5f) pressedKeys += "D-pad Right"
    }

    private fun isGamepadKey(keyCode: Int): Boolean {
        return keyLabel(keyCode) != null
    }

    private fun keyLabel(keyCode: Int): String? {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "Cross"
            KeyEvent.KEYCODE_BUTTON_B -> "Circle"
            KeyEvent.KEYCODE_BUTTON_X -> "Square"
            KeyEvent.KEYCODE_BUTTON_Y -> "Triangle"

            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "L2 click"
            KeyEvent.KEYCODE_BUTTON_R2 -> "R2 click"

            KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"

            KeyEvent.KEYCODE_BUTTON_START -> "Options"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "Create"
            KeyEvent.KEYCODE_BUTTON_MODE -> "PS"

            KeyEvent.KEYCODE_BUTTON_1 -> "Button 1"
            KeyEvent.KEYCODE_BUTTON_2 -> "Button 2"
            KeyEvent.KEYCODE_BUTTON_3 -> "Button 3"
            KeyEvent.KEYCODE_BUTTON_4 -> "Button 4"

            else -> null
        }
    }

    private fun buildBtAxisDebug(event: MotionEvent): String {
        val axes = listOf(
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RX,
            MotionEvent.AXIS_RY,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_GAS,
            MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_DISTANCE,
            MotionEvent.AXIS_TILT,
            MotionEvent.AXIS_SCROLL,
            MotionEvent.AXIS_RELATIVE_X,
            MotionEvent.AXIS_RELATIVE_Y
        )

        return axes.joinToString(
            separator = "\n",
            prefix = "BT axes:\n"
        ) { axis ->
            "axis $axis = ${fmt(event.getAxisValue(axis))}"
        }
    }

    private fun fmt(value: Float): String {
        return "%.3f".format(value)
    }

    companion object {
        private const val SONY_VENDOR_ID = 0x054C
    }
}