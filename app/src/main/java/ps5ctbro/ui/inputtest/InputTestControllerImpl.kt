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
                setConnectionState(false, "USB engedély elutasítva.")
                return
            }

            if (!screenVisible) {
                closeHandle()
                _uiState.update {
                    it.copy(
                        controllerConnected = false,
                        logText = "USB engedély megjött, de az Input Test oldal már nem aktív."
                    )
                }
                return
            }

            reopenHandle(device)

            if (hidHandle != null) {
                setConnectionState(true, "USB engedély megadva, Input Test kész.")
            } else {
                setConnectionState(false, "USB engedély megvan, de a HID kapcsolat nem nyitható meg.")
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
                logText = "Input Test képernyő elhagyva, HID kapcsolat elengedve."
            )
        }
    }

    override fun refreshConnection() {
        if (!screenVisible) {
            closeHandle()
            _uiState.update {
                it.copy(
                    controllerConnected = false,
                    logText = "Input Test nem aktív, HID kapcsolat nem nyitható."
                )
            }
            return
        }

        val device = findDualSenseDevice()
        if (device == null) {
            closeHandle()
            setConnectionState(false, "DualSense nincs csatlakoztatva USB-n.")
            return
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            setConnectionState(false, "USB engedély szükséges az Input Testhez.")
            return
        }

        reopenHandle(device)

        if (hidHandle != null) {
            setConnectionState(true, "DualSense Input Test kapcsolat aktív.")
        } else {
            setConnectionState(false, "A HID interfész nem található vagy nem nyitható meg.")
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
                logText = "Input stream aktív."
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
            0 -> pressed += "D-Pad Up"
            1 -> {
                pressed += "D-Pad Up"
                pressed += "D-Pad Right"
            }
            2 -> pressed += "D-Pad Right"
            3 -> {
                pressed += "D-Pad Right"
                pressed += "D-Pad Down"
            }
            4 -> pressed += "D-Pad Down"
            5 -> {
                pressed += "D-Pad Down"
                pressed += "D-Pad Left"
            }
            6 -> pressed += "D-Pad Left"
            7 -> {
                pressed += "D-Pad Left"
                pressed += "D-Pad Up"
            }
        }

        if ((buttons0 and 0x10) != 0) pressed += "Square"
        if ((buttons0 and 0x20) != 0) pressed += "Cross"
        if ((buttons0 and 0x40) != 0) pressed += "Circle"
        if ((buttons0 and 0x80) != 0) pressed += "Triangle"

        if ((buttons1 and 0x01) != 0) pressed += "L1"
        if ((buttons1 and 0x02) != 0) pressed += "R1"
        if ((buttons1 and 0x04) != 0) pressed += "L2 Click"
        if ((buttons1 and 0x08) != 0) pressed += "R2 Click"
        if ((buttons1 and 0x10) != 0) pressed += "Create"
        if ((buttons1 and 0x20) != 0) pressed += "Options"
        if ((buttons1 and 0x40) != 0) pressed += "L3"
        if ((buttons1 and 0x80) != 0) pressed += "R3"

        if ((buttons2 and 0x01) != 0) pressed += "PS"
        if ((buttons2 and 0x02) != 0) pressed += "Touchpad Click"
        if ((buttons2 and 0x04) != 0) pressed += "Mic Mute"

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