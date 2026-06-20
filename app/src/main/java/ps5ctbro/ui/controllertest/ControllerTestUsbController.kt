package com.DueBoysenberry1226.ps5ctbro.ui.controllertest

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
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.DueBoysenberry1226.ps5ctbro.ui.hid.DualSenseOutputReportMerger
import com.DueBoysenberry1226.ps5ctbro.ui.hid.DualSenseUsbHidManager
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
import kotlinx.coroutines.cancel
import kotlin.math.abs

class ControllerTestUsbController(context: Context) {

    companion object {
        private const val ACTION_USB_PERMISSION =
            "com.DueBoysenberry1226.ps5ctbro.CONTROLLER_TEST_USB_PERMISSION"

        private const val SONY_VENDOR_ID = 0x054c
        private const val DUALSENSE_PRODUCT_ID = 0x0ce6
        private const val DUALSENSE_EDGE_PRODUCT_ID = 0x0df2

        private const val OUTPUT_REPORT_ID_USB = 0x02
        private const val OUTPUT_REPORT_SIZE_USB = 63
        private const val INPUT_BUFFER_SIZE = 64

        private const val INPUT_OFFSET_LEFT_STICK_X = 1
        private const val INPUT_OFFSET_LEFT_STICK_Y = 2
        private const val INPUT_OFFSET_RIGHT_STICK_X = 3
        private const val INPUT_OFFSET_RIGHT_STICK_Y = 4
        private const val INPUT_OFFSET_L2 = 5
        private const val INPUT_OFFSET_R2 = 6
        private const val INPUT_OFFSET_BUTTONS_0 = 8
        private const val INPUT_OFFSET_BUTTONS_1 = 9
        private const val INPUT_OFFSET_BUTTONS_2 = 10

        private const val VALID_FLAG0_INDEX = 1
        private const val VALID_FLAG1_INDEX = 2
        private const val RIGHT_RUMBLE_INDEX = 3
        private const val LEFT_RUMBLE_INDEX = 4
        private const val MIC_LED_INDEX = 9
        private const val VALID_FLAG2_INDEX = 39
        private const val LIGHTBAR_SETUP_INDEX = 42
        private const val LED_BRIGHTNESS_INDEX = 43
        private const val PLAYER_LEDS_INDEX = 44
        private const val LIGHTBAR_RED_INDEX = 45
        private const val LIGHTBAR_GREEN_INDEX = 46
        private const val LIGHTBAR_BLUE_INDEX = 47

        private const val VALID_FLAG1_MIC_MUTE_LED_CONTROL_ENABLE = 0x01
        private const val VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE = 0x04
        private const val VALID_FLAG1_PLAYER_INDICATOR_CONTROL_ENABLE = 0x10
        private const val VALID_FLAG2_LED_BRIGHTNESS_CONTROL_ENABLE = 0x01
        private const val VALID_FLAG2_LIGHTBAR_SETUP_CONTROL_ENABLE = 0x02
        private const val LIGHTBAR_SETUP_LIGHT_OUT = 0x01
        private const val LIGHTBAR_SETUP_LIGHT_ON = 0x02
        private const val PLAYER_LED_INSTANT_BIT = 0x20
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val hidManager = DualSenseUsbHidManager.get(appContext)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _uiState = MutableStateFlow(
        ControllerTestUiState(logText = "Kontroller teszt keszen all.")
    )
    val uiState: StateFlow<ControllerTestUiState> = _uiState

    private var receiverRegistered = false
    private var hidHandle: HidHandle? = null
    private var outputJob: Job? = null
    private var ageJob: Job? = null
    private var sharedInputJob: Job? = null
    private var inputReaderThread: Thread? = null

    @Volatile
    private var screenVisible = false

    @Volatile
    private var inputReaderRunning = false

    @Volatile
    private var lastInputAtMs = 0L

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

            val device =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                reopenHandle(device)
            } else {
                setConnectionState(false, "USB engedely elutasitva.")
            }
        }
    }

    init {
        // USB HID permission and connection ownership lives in DualSenseUsbHidManager.
    }

    fun onScreenVisible() {
        screenVisible = true
        refreshConnection()
        startInputAgeTicker()
    }

    fun onScreenHidden() {
        screenVisible = false
        setTestRunning(false)
        ageJob?.cancel()
        ageJob = null
        closeHandle()
        _uiState.update {
            it.copy(
                controllerConnected = false,
                lastInputAgeMs = null,
                logText = "Kontroller teszt kepernyo elhagyva."
            )
        }
    }

    fun refreshConnection() {
        if (hidManager.refreshConnection()) {
            startSharedInputReader()
            setConnectionState(true, "HID IN/OUT kapcsolat aktiv. Indithato az egyesitett report loop.")
        } else {
            setConnectionState(false, hidManager.state.value.logText)
        }
    }

    fun setTestRunning(running: Boolean) {
        _uiState.update { it.copy(testRunning = running) }
        if (running) {
        if (!hidManager.state.value.connected) refreshConnection()
            startOutputLoop()
        } else {
            stopOutputLoop()
            sendOutputReport(forceOff = true)
        }
    }

    fun setLightEnabled(enabled: Boolean) {
        _uiState.update { it.copy(lightEnabled = enabled) }
        sendOutputReport()
    }

    fun setRumbleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(rumbleEnabled = enabled) }
        sendOutputReport()
    }

    fun setMicLedEnabled(enabled: Boolean) {
        _uiState.update { it.copy(micLedEnabled = enabled) }
        sendOutputReport()
    }

    fun setPlayerLedEnabled(index: Int, enabled: Boolean) {
        val bit = 1 shl index.coerceIn(0, 4)
        _uiState.update {
            val mask = if (enabled) {
                it.playerLedMask or bit
            } else {
                it.playerLedMask and bit.inv()
            }
            it.copy(playerLedMask = mask and 0x1F)
        }
        sendOutputReport()
    }

    fun setRed(value: Int) {
        _uiState.update { it.copy(red = value.coerceIn(0, 255)) }
        sendOutputReport()
    }

    fun setGreen(value: Int) {
        _uiState.update { it.copy(green = value.coerceIn(0, 255)) }
        sendOutputReport()
    }

    fun setBlue(value: Int) {
        _uiState.update { it.copy(blue = value.coerceIn(0, 255)) }
        sendOutputReport()
    }

    fun setLeftRumblePercent(value: Int) {
        _uiState.update { it.copy(leftRumblePercent = value.coerceIn(0, 100)) }
        sendOutputReport()
    }

    fun setRightRumblePercent(value: Int) {
        _uiState.update { it.copy(rightRumblePercent = value.coerceIn(0, 100)) }
        sendOutputReport()
    }

    fun setSendIntervalMs(value: Int) {
        _uiState.update { it.copy(sendIntervalMs = value.coerceIn(8, 250)) }
    }

    fun release() {
        setTestRunning(false)
        closeHandle()
        unregisterReceiver()
        scope.cancel()
    }

    private fun startOutputLoop() {
        if (outputJob?.isActive == true) return
        outputJob = scope.launch {
            while (isActive && _uiState.value.testRunning) {
                sendOutputReport()
                delay(_uiState.value.sendIntervalMs.toLong())
            }
        }
    }

    private fun stopOutputLoop() {
        outputJob?.cancel()
        outputJob = null
    }

    private fun startInputAgeTicker() {
        if (ageJob?.isActive == true) return
        ageJob = scope.launch {
            while (isActive) {
                val age = if (lastInputAtMs > 0L) {
                    SystemClock.elapsedRealtime() - lastInputAtMs
                } else {
                    null
                }
                _uiState.update { it.copy(lastInputAgeMs = age) }
                delay(250)
            }
        }
    }

    private fun sendOutputReport(forceOff: Boolean = false) {
        val state = _uiState.value
        val report = DualSenseOutputReportMerger.merge(buildMergedReport(state, forceOff))
        val ok = hidManager.send(report, 250)

        if (ok) {
            _uiState.update {
                it.copy(
                    controllerConnected = true,
                    outputReportsSent = it.outputReportsSent + 1,
                    logText = "Egyesitett report kuldve: rumble + LED + input olvasas parhuzamosan."
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    outputErrors = it.outputErrors + 1,
                    logText = hidManager.state.value.logText
                )
            }
        }
    }

    private fun startSharedInputReader() {
        if (sharedInputJob?.isActive == true) return
        sharedInputJob = scope.launch {
            hidManager.inputReports.collect { report ->
                if (screenVisible) {
                    parseInputReport(report, report.size)
                }
            }
        }
    }

    private fun buildMergedReport(state: ControllerTestUiState, forceOff: Boolean): ByteArray {
        val report = ByteArray(OUTPUT_REPORT_SIZE_USB)

        val rumbleEnabled = state.rumbleEnabled && !forceOff
        val lightEnabled = state.lightEnabled && !forceOff
        val micLedEnabled = state.micLedEnabled && !forceOff

        report[0] = OUTPUT_REPORT_ID_USB.toByte()

        // 0xFF/0xF7 keeps the native rumble path active while the same report
        // also carries LED validity bits, so LED state is not overwritten.
        report[VALID_FLAG0_INDEX] = 0xFF.toByte()
        report[VALID_FLAG1_INDEX] =
            (0xF7 or
                    VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE or
                    VALID_FLAG1_PLAYER_INDICATOR_CONTROL_ENABLE or
                    VALID_FLAG1_MIC_MUTE_LED_CONTROL_ENABLE).toByte()

        report[RIGHT_RUMBLE_INDEX] =
            if (rumbleEnabled) percentToByte(state.rightRumblePercent) else 0
        report[LEFT_RUMBLE_INDEX] =
            if (rumbleEnabled) percentToByte(state.leftRumblePercent) else 0

        report[MIC_LED_INDEX] = if (micLedEnabled) 1.toByte() else 0

        report[VALID_FLAG2_INDEX] =
            (VALID_FLAG2_LED_BRIGHTNESS_CONTROL_ENABLE or
                    VALID_FLAG2_LIGHTBAR_SETUP_CONTROL_ENABLE).toByte()

        report[LIGHTBAR_SETUP_INDEX] =
            if (lightEnabled) LIGHTBAR_SETUP_LIGHT_ON.toByte() else LIGHTBAR_SETUP_LIGHT_OUT.toByte()
        report[LED_BRIGHTNESS_INDEX] = 0.toByte()
        report[PLAYER_LEDS_INDEX] =
            (((if (lightEnabled) state.playerLedMask else 0) and 0x1F) or PLAYER_LED_INSTANT_BIT).toByte()
        report[LIGHTBAR_RED_INDEX] = if (lightEnabled) state.red.toByte() else 0
        report[LIGHTBAR_GREEN_INDEX] = if (lightEnabled) state.green.toByte() else 0
        report[LIGHTBAR_BLUE_INDEX] = if (lightEnabled) state.blue.toByte() else 0

        return report
    }

    private fun percentToByte(value: Int): Byte {
        return ((value.coerceIn(0, 100) / 100f) * 255f).toInt().coerceIn(0, 255).toByte()
    }

    private fun reopenHandle(device: UsbDevice) {
        closeHandle()
        hidHandle = openHandle(device)
        if (hidHandle != null) {
            lastInputAtMs = 0L
            startInputReaderIfPossible()
            setConnectionState(true, "DualSense HID teszt kapcsolat aktiv.")
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

            return HidHandle(connection, targetInterface, outEndpoint, inEndpoint)
        } catch (_: Throwable) {
            try {
                connection.close()
            } catch (_: Throwable) {
            }
            return null
        }
    }

    private fun startInputReaderIfPossible() {
        val handle = hidHandle ?: return
        if (inputReaderRunning) return

        inputReaderRunning = true
        inputReaderThread = Thread {
            val buffer = ByteArray(INPUT_BUFFER_SIZE)

            while (inputReaderRunning && screenVisible) {
                val read = try {
                    handle.connection.bulkTransfer(handle.inEndpoint, buffer, buffer.size, 250)
                } catch (_: Throwable) {
                    -1
                }

                if (!inputReaderRunning || !screenVisible) break
                if (read > INPUT_OFFSET_BUTTONS_2) {
                    parseInputReport(buffer, read)
                }
            }
        }.apply {
            name = "DualSenseControllerTestInputReader"
            isDaemon = true
            start()
        }
    }

    private fun stopInputReader() {
        inputReaderRunning = false
        inputReaderThread?.interrupt()
        inputReaderThread = null
        sharedInputJob?.cancel()
        sharedInputJob = null
    }

    private var lastUiUpdateTime = 0L

    private fun parseInputReport(buffer: ByteArray, size: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastUiUpdateTime < 16) return
        lastUiUpdateTime = now
        lastInputAtMs = now

        val buttons0 = buffer[INPUT_OFFSET_BUTTONS_0].toUByte().toInt()
        val buttons1 = buffer[INPUT_OFFSET_BUTTONS_1].toUByte().toInt()
        val buttons2 = buffer[INPUT_OFFSET_BUTTONS_2].toUByte().toInt()

        val rawReportInfo = "size=$size | b0=${hexByte(buttons0)} b1=${hexByte(buttons1)} b2=${hexByte(buttons2)}"

        _uiState.update {
            it.copy(
                controllerConnected = true,
                inputReportsRead = it.inputReportsRead + 1,
                lastInputAgeMs = 0,
                leftStick = ControllerTestStickState(
                    xPercent = axisByteToPercent(buffer[INPUT_OFFSET_LEFT_STICK_X].toUByte().toInt()),
                    yPercent = axisByteToPercent(buffer[INPUT_OFFSET_LEFT_STICK_Y].toUByte().toInt())
                ),
                rightStick = ControllerTestStickState(
                    xPercent = axisByteToPercent(buffer[INPUT_OFFSET_RIGHT_STICK_X].toUByte().toInt()),
                    yPercent = axisByteToPercent(buffer[INPUT_OFFSET_RIGHT_STICK_Y].toUByte().toInt())
                ),
                l2Percent = triggerByteToPercent(buffer[INPUT_OFFSET_L2].toUByte().toInt()),
                r2Percent = triggerByteToPercent(buffer[INPUT_OFFSET_R2].toUByte().toInt()),
                pressedButtons = buildPressedButtons(buttons0, buttons1, buttons2),
                rawReportInfo = rawReportInfo
            )
        }
    }

    private fun buildPressedButtons(buttons0: Int, buttons1: Int, buttons2: Int): List<String> {
        val pressed = mutableListOf<String>()

        when (buttons0 and 0x0F) {
            0 -> pressed += "D-pad fel"
            1 -> pressed += listOf("D-pad fel", "D-pad jobb")
            2 -> pressed += "D-pad jobb"
            3 -> pressed += listOf("D-pad jobb", "D-pad le")
            4 -> pressed += "D-pad le"
            5 -> pressed += listOf("D-pad le", "D-pad bal")
            6 -> pressed += "D-pad bal"
            7 -> pressed += listOf("D-pad bal", "D-pad fel")
        }

        if ((buttons0 and 0x10) != 0) pressed += "Negyzet"
        if ((buttons0 and 0x20) != 0) pressed += "X"
        if ((buttons0 and 0x40) != 0) pressed += "Kor"
        if ((buttons0 and 0x80) != 0) pressed += "Haromszog"
        if ((buttons1 and 0x01) != 0) pressed += "L1"
        if ((buttons1 and 0x02) != 0) pressed += "R1"
        if ((buttons1 and 0x04) != 0) pressed += "L2 click"
        if ((buttons1 and 0x08) != 0) pressed += "R2 click"
        if ((buttons1 and 0x10) != 0) pressed += "Create"
        if ((buttons1 and 0x20) != 0) pressed += "Options"
        if ((buttons1 and 0x40) != 0) pressed += "L3"
        if ((buttons1 and 0x80) != 0) pressed += "R3"
        if ((buttons2 and 0x01) != 0) pressed += "PS"
        if ((buttons2 and 0x02) != 0) pressed += "Touchpad click"
        if ((buttons2 and 0x04) != 0) pressed += "Mic mute"

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

    private fun setConnectionState(connected: Boolean, log: String) {
        _uiState.update { it.copy(controllerConnected = connected, logText = log) }
    }

    private fun findDualSenseDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            device.vendorId == SONY_VENDOR_ID &&
                    (device.productId == DUALSENSE_PRODUCT_ID ||
                            device.productId == DUALSENSE_EDGE_PRODUCT_ID)
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
            if (endpoint.direction == UsbConstants.USB_DIR_OUT) return endpoint
        }
        return null
    }

    private fun findInEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.direction == UsbConstants.USB_DIR_IN) return endpoint
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

    private fun closeHandle() {
        stopInputReader()
        hidHandle?.close()
        hidHandle = null
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
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
}
