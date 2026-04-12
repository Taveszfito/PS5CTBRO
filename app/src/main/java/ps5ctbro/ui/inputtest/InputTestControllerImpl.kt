package com.DueBoysenberry1226.ps5ctbro.ui.inputtest

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.DueBoysenberry1226.ps5ctbro.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

class InputTestControllerImpl(
    context: Context
) : InputTestController {

    companion object {
        private const val ACTION_USB_PERMISSION =
            "com.DueBoysenberry1226.ps5ctbro.INPUT_TEST_USB_PERMISSION"

        private const val SONY_VENDOR_ID = 0x054c
        private const val DUALSENSE_PRODUCT_ID = 0x0ce6
        private const val DUALSENSE_EDGE_PRODUCT_ID = 0x0df2

        private const val INPUT_BUFFER_SIZE = 64

        // Standard USB DualSense input report layout
        private const val INPUT_OFFSET_LEFT_STICK_X = 1
        private const val INPUT_OFFSET_LEFT_STICK_Y = 2
        private const val INPUT_OFFSET_RIGHT_STICK_X = 3
        private const val INPUT_OFFSET_RIGHT_STICK_Y = 4
        private const val INPUT_OFFSET_L2 = 5
        private const val INPUT_OFFSET_R2 = 6
        private const val INPUT_OFFSET_BUTTONS_0 = 8
        private const val INPUT_OFFSET_BUTTONS_1 = 9
        private const val INPUT_OFFSET_BUTTONS_2 = 10
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _uiState = MutableStateFlow(InputTestUiState())
    override val uiState: StateFlow<InputTestUiState> = _uiState

    private var receiverRegistered = false
    private var hidHandle: HidHandle? = null

    @Volatile
    private var screenVisible = false

    @Volatile
    private var inputReaderRunning = false

    private var inputReaderThread: Thread? = null

    private data class HidHandle(
        val connection: UsbDeviceConnection,
        val usbInterface: UsbInterface,
        val outEndpoint: UsbEndpoint,
        val inEndpoint: UsbEndpoint
    ) {
        fun close() {
            try {
                connection.releaseInterface(usbInterface)
            } catch (_: Throwable) {
            }

            try {
                connection.close()
            } catch (_: Throwable) {
            }
        }
    }

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return

            val device: UsbDevice? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            if (!granted || device == null) {
                setConnectionState(false, appContext.getString(R.string.log_usb_permission_denied))
                return
            }

            if (!screenVisible) {
                closeHandle()
                _uiState.update {
                    it.copy(
                        controllerConnected = false,
                        logText = appContext.getString(R.string.log_usb_permission_not_active)
                    )
                }
                return
            }

            reopenHandle(device)

            if (hidHandle != null) {
                setConnectionState(true, appContext.getString(R.string.log_usb_permission_granted_ready))
            } else {
                setConnectionState(false, appContext.getString(R.string.log_usb_permission_hid_failed))
            }
        }
    }

    init {
        registerReceiver()
    }

    override fun onScreenVisible() {
        screenVisible = true
        refreshConnection()
    }

    override fun onScreenHidden() {
        screenVisible = false
        closeHandle()
        _uiState.update {
            it.copy(
                controllerConnected = false,
                logText = appContext.getString(R.string.log_screen_hidden_released)
            )
        }
    }

    override fun refreshConnection() {
        if (!screenVisible) {
            closeHandle()
            _uiState.update {
                it.copy(
                    controllerConnected = false,
                    logText = appContext.getString(R.string.log_screen_not_active_no_hid)
                )
            }
            return
        }

        val device = findDualSenseDevice()
        if (device == null) {
            closeHandle()
            setConnectionState(false, appContext.getString(R.string.log_dualsense_not_connected_usb))
            return
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            setConnectionState(false, appContext.getString(R.string.log_usb_permission_required))
            return
        }

        reopenHandle(device)

        if (hidHandle != null) {
            setConnectionState(true, appContext.getString(R.string.log_dualsense_connection_active))
        } else {
            setConnectionState(false, appContext.getString(R.string.log_hid_interface_not_found))
        }
    }

    override fun release() {
        unregisterReceiver()
        closeHandle()
    }

    private fun setConnectionState(
        connected: Boolean,
        log: String
    ) {
        _uiState.update {
            it.copy(
                controllerConnected = connected,
                logText = log
            )
        }
    }

    private fun findDualSenseDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            device.vendorId == SONY_VENDOR_ID &&
                    (device.productId == DUALSENSE_PRODUCT_ID ||
                            device.productId == DUALSENSE_EDGE_PRODUCT_ID)
        }
    }

    private fun reopenHandle(device: UsbDevice) {
        closeHandle()

        if (!screenVisible) return

        hidHandle = openHandle(device)

        if (screenVisible) {
            startInputReaderIfPossible()
        } else {
            closeHandle()
        }
    }

    private fun openHandle(device: UsbDevice): HidHandle? {
        if (!usbManager.hasPermission(device)) return null

        val connection = try {
            usbManager.openDevice(device)
        } catch (_: SecurityException) {
            return null
        } ?: return null

        try {
            val targetInterface = findHidInterfaceWithInAndOutEndpoint(device) ?: run {
                connection.close()
                return null
            }

            if (!connection.claimInterface(targetInterface, true)) {
                connection.close()
                return null
            }

            val outEndpoint = findOutEndpoint(targetInterface) ?: run {
                connection.releaseInterface(targetInterface)
                connection.close()
                return null
            }

            val inEndpoint = findInEndpoint(targetInterface) ?: run {
                connection.releaseInterface(targetInterface)
                connection.close()
                return null
            }

            return HidHandle(
                connection = connection,
                usbInterface = targetInterface,
                outEndpoint = outEndpoint,
                inEndpoint = inEndpoint
            )
        } catch (_: Throwable) {
            try {
                connection.close()
            } catch (_: Throwable) {
            }
            return null
        }
    }

    private fun findHidInterfaceWithInAndOutEndpoint(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_HID &&
                findOutEndpoint(usbInterface) != null &&
                findInEndpoint(usbInterface) != null
            ) {
                return usbInterface
            }
        }
        return null
    }

    private fun findOutEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                return endpoint
            }
        }
        return null
    }

    private fun findInEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                return endpoint
            }
        }
        return null
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        val permissionIntent = PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )

        usbManager.requestPermission(device, permissionIntent)
    }

    private fun startInputReaderIfPossible() {
        if (!screenVisible) return

        val handle = hidHandle ?: return
        if (inputReaderRunning) return

        inputReaderRunning = true
        inputReaderThread = Thread {
            val buffer = ByteArray(INPUT_BUFFER_SIZE)

            while (inputReaderRunning && screenVisible) {
                val read = try {
                    handle.connection.bulkTransfer(
                        handle.inEndpoint,
                        buffer,
                        buffer.size,
                        250
                    )
                } catch (_: Throwable) {
                    -1
                }

                if (!inputReaderRunning || !screenVisible) break

                if (read > INPUT_OFFSET_BUTTONS_2) {
                    parseInputReport(buffer, read)
                }
            }
        }.apply {
            name = "DualSenseInputTestReader"
            isDaemon = true
            start()
        }
    }

    private fun stopInputReader() {
        inputReaderRunning = false
        inputReaderThread?.interrupt()
        inputReaderThread = null
    }

    private fun closeHandle() {
        stopInputReader()
        hidHandle?.close()
        hidHandle = null
    }

    private fun registerReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            appContext,
            permissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            appContext.unregisterReceiver(permissionReceiver)
        } catch (_: Throwable) {
        }
        receiverRegistered = false
    }

    private fun parseInputReport(buffer: ByteArray, size: Int) {
        if (size <= INPUT_OFFSET_BUTTONS_2) return

        val leftStick = StickState(
            rawX = buffer[INPUT_OFFSET_LEFT_STICK_X].toUByte().toInt(),
            rawY = buffer[INPUT_OFFSET_LEFT_STICK_Y].toUByte().toInt(),
            percentX = axisByteToPercent(buffer[INPUT_OFFSET_LEFT_STICK_X].toUByte().toInt()),
            percentY = axisByteToPercent(buffer[INPUT_OFFSET_LEFT_STICK_Y].toUByte().toInt())
        )

        val rightStick = StickState(
            rawX = buffer[INPUT_OFFSET_RIGHT_STICK_X].toUByte().toInt(),
            rawY = buffer[INPUT_OFFSET_RIGHT_STICK_Y].toUByte().toInt(),
            percentX = axisByteToPercent(buffer[INPUT_OFFSET_RIGHT_STICK_X].toUByte().toInt()),
            percentY = axisByteToPercent(buffer[INPUT_OFFSET_RIGHT_STICK_Y].toUByte().toInt())
        )

        val l2Raw = buffer[INPUT_OFFSET_L2].toUByte().toInt()
        val r2Raw = buffer[INPUT_OFFSET_R2].toUByte().toInt()

        val buttons0 = buffer[INPUT_OFFSET_BUTTONS_0].toUByte().toInt()
        val buttons1 = buffer[INPUT_OFFSET_BUTTONS_1].toUByte().toInt()
        val buttons2 = buffer[INPUT_OFFSET_BUTTONS_2].toUByte().toInt()

        val pressedButtons = buildPressedButtons(
            buttons0 = buttons0,
            buttons1 = buttons1,
            buttons2 = buttons2
        )

        val rawReportInfo = buildString {
            append("size=")
            append(size)
            append(" | b0=")
            append(hexByte(buttons0))
            append(" b1=")
            append(hexByte(buttons1))
            append(" b2=")
            append(hexByte(buttons2))
        }

        _uiState.update {
            it.copy(
                controllerConnected = true,
                leftStick = leftStick,
                rightStick = rightStick,
                l2 = TriggerState(
                    rawValue = l2Raw,
                    percent = triggerByteToPercent(l2Raw)
                ),
                r2 = TriggerState(
                    rawValue = r2Raw,
                    percent = triggerByteToPercent(r2Raw)
                ),
                pressedButtons = pressedButtons,
                rawReportInfo = rawReportInfo,
                logText = appContext.getString(R.string.log_input_stream_active)
            )
        }
    }

    private fun buildPressedButtons(
        buttons0: Int,
        buttons1: Int,
        buttons2: Int
    ): List<String> {
        val pressed = mutableListOf<String>()

        when (buttons0 and 0x0F) {
            0 -> pressed += appContext.getString(R.string.btn_dpad_up)
            1 -> {
                pressed += appContext.getString(R.string.btn_dpad_up)
                pressed += appContext.getString(R.string.btn_dpad_right)
            }
            2 -> pressed += appContext.getString(R.string.btn_dpad_right)
            3 -> {
                pressed += appContext.getString(R.string.btn_dpad_right)
                pressed += appContext.getString(R.string.btn_dpad_down)
            }
            4 -> pressed += appContext.getString(R.string.btn_dpad_down)
            5 -> {
                pressed += appContext.getString(R.string.btn_dpad_down)
                pressed += appContext.getString(R.string.btn_dpad_left)
            }
            6 -> pressed += appContext.getString(R.string.btn_dpad_left)
            7 -> {
                pressed += appContext.getString(R.string.btn_dpad_left)
                pressed += appContext.getString(R.string.btn_dpad_up)
            }
        }

        if ((buttons0 and 0x10) != 0) pressed += appContext.getString(R.string.btn_square)
        if ((buttons0 and 0x20) != 0) pressed += appContext.getString(R.string.btn_cross)
        if ((buttons0 and 0x40) != 0) pressed += appContext.getString(R.string.btn_circle)
        if ((buttons0 and 0x80) != 0) pressed += appContext.getString(R.string.btn_triangle)

        if ((buttons1 and 0x01) != 0) pressed += appContext.getString(R.string.btn_l1)
        if ((buttons1 and 0x02) != 0) pressed += appContext.getString(R.string.btn_r1)
        if ((buttons1 and 0x04) != 0) pressed += appContext.getString(R.string.btn_l2_click)
        if ((buttons1 and 0x08) != 0) pressed += appContext.getString(R.string.btn_r2_click)
        if ((buttons1 and 0x10) != 0) pressed += appContext.getString(R.string.btn_create)
        if ((buttons1 and 0x20) != 0) pressed += appContext.getString(R.string.btn_options)
        if ((buttons1 and 0x40) != 0) pressed += appContext.getString(R.string.btn_l3)
        if ((buttons1 and 0x80) != 0) pressed += appContext.getString(R.string.btn_r3)

        if ((buttons2 and 0x01) != 0) pressed += appContext.getString(R.string.btn_ps)
        if ((buttons2 and 0x02) != 0) pressed += appContext.getString(R.string.btn_touchpad_click)
        if ((buttons2 and 0x04) != 0) pressed += appContext.getString(R.string.btn_mic_mute)

        return pressed.distinct()
    }

    private fun axisByteToPercent(value: Int): Int {
        val centered = value - 128f
        val normalized = centered / 127f
        val percent = (normalized * 100f).toInt()

        return when {
            abs(percent) <= 1 -> 0
            percent < -100 -> -100
            percent > 100 -> 100
            else -> percent
        }
    }

    private fun triggerByteToPercent(value: Int): Int {
        return ((value / 255f) * 100f).toInt().coerceIn(0, 100)
    }

    private fun hexByte(value: Int): String {
        return "0x" + value.toString(16).uppercase().padStart(2, '0')
    }
}