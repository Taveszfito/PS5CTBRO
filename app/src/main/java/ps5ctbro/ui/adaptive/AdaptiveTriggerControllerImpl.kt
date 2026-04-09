package com.DueBoysenberry1226.ps5ctbro.adaptive

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

class AdaptiveTriggerControllerImpl(
    context: Context
) : AdaptiveTriggerController {

    companion object {
        private const val ACTION_USB_PERMISSION =
            "com.DueBoysenberry1226.ps5ctbro.ADAPTIVE_TRIGGER_USB_PERMISSION"

        private const val SONY_VENDOR_ID = 0x054c
        private const val DUALSENSE_PRODUCT_ID = 0x0ce6

        private const val INPUT_BUFFER_SIZE = 64

        // Feltételezett standard USB DualSense input layout.
        // Ha a live csík nem jó, ezeket kell majd finomhangolni.
        private const val INPUT_OFFSET_L2 = 5
        private const val INPUT_OFFSET_R2 = 6
        private const val INPUT_OFFSET_BUTTONS_2 = 8

        private const val BUTTON_MASK_L1 = 0x01
        private const val BUTTON_MASK_R1 = 0x02
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _uiState = MutableStateFlow(AdaptiveTriggersUiState())
    override val uiState: StateFlow<AdaptiveTriggersUiState> = _uiState

    private var receiverRegistered = false
    private var hidHandle: HidHandle? = null

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
                setConnectionState(connected = false, log = "USB engedély elutasítva.")
                return
            }

            reopenHandle(device)
            if (hidHandle != null) {
                setConnectionState(
                    connected = true,
                    log = "USB engedély megadva, trigger vezérlés kész."
                )
            } else {
                setConnectionState(
                    connected = false,
                    log = "USB engedély megvan, de a kontroller megnyitása sikertelen."
                )
            }
        }
    }

    init {
        registerReceiver()
    }

    override fun onScreenVisible() {
        refreshConnection()
    }

    override fun onScreenHidden() {
        closeHandle()
        _uiState.update {
            it.copy(
                controllerConnected = false,
                logText = "Adaptive trigger képernyő elhagyva, HID kapcsolat elengedve."
            )
        }
    }

    override fun updateTriggerConfig(side: TriggerSide, config: AdaptiveTriggerConfig) {
        _uiState.update { current ->
            when (side) {
                TriggerSide.LEFT -> current.copy(leftTrigger = normalizeConfig(config))
                TriggerSide.RIGHT -> current.copy(rightTrigger = normalizeConfig(config))
            }
        }
    }

    override fun applyCurrentState() {
        val device = findDualSenseDevice()
        if (device == null) {
            setConnectionState(connected = false, log = "Nem találok USB-s DualSense kontrollert.")
            closeHandle()
            return
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            setConnectionState(
                connected = false,
                log = "USB engedély kérve. Engedélyezd, majd nyomd meg újra az Alkalmazást."
            )
            return
        }

        if (hidHandle == null) {
            reopenHandle(device)
        }

        val handle = hidHandle
        if (handle == null) {
            setConnectionState(
                connected = false,
                log = "A kontroller HID kapcsolata nem nyitható meg."
            )
            return
        }

        val current = _uiState.value

        val clearReport = DualSenseTriggerReportBuilder.buildReport(
            left = current.leftTrigger.copy(effect = AdaptiveTriggerEffect.OFF),
            right = current.rightTrigger.copy(effect = AdaptiveTriggerEffect.OFF)
        )

        val clearSent = try {
            handle.connection.bulkTransfer(
                handle.outEndpoint,
                clearReport,
                clearReport.size,
                1000
            )
        } catch (_: SecurityException) {
            -1
        }

        if (clearSent != clearReport.size) {
            setConnectionState(false, "CLEAR report hiba: sent=$clearSent")
            closeHandle()
            return
        }

        Thread.sleep(10)

        val newReport = DualSenseTriggerReportBuilder.buildReport(
            left = current.leftTrigger,
            right = current.rightTrigger
        )

        val sent = try {
            handle.connection.bulkTransfer(
                handle.outEndpoint,
                newReport,
                newReport.size,
                1000
            )
        } catch (_: SecurityException) {
            -1
        }

        if (sent == newReport.size) {
            _uiState.update {
                it.copy(
                    controllerConnected = true,
                    logText = "Trigger beállítás elküldve (clear+apply). (${sent} byte)"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    controllerConnected = false,
                    logText = "Trigger report küldése sikertelen. sent=$sent / expected=${newReport.size}"
                )
            }
            closeHandle()
        }
    }

    override fun refreshConnection() {
        val device = findDualSenseDevice()
        if (device == null) {
            closeHandle()
            setConnectionState(connected = false, log = "DualSense nincs csatlakoztatva USB-n.")
            return
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            setConnectionState(
                connected = false,
                log = "USB engedély szükséges a trigger vezérléshez."
            )
            return
        }

        reopenHandle(device)

        if (hidHandle != null) {
            setConnectionState(connected = true, log = "DualSense trigger kapcsolat aktív.")
        } else {
            setConnectionState(
                connected = false,
                log = "A trigger HID interfész nem található vagy nem nyitható meg."
            )
        }
    }

    override fun resetTriggers() {
        _uiState.update {
            it.copy(
                leftTrigger = AdaptiveTriggerConfig(effect = AdaptiveTriggerEffect.OFF),
                rightTrigger = AdaptiveTriggerConfig(effect = AdaptiveTriggerEffect.OFF),
                logText = "Trigger beállítások nullázva."
            )
        }
        applyCurrentState()
    }

    override fun release() {
        unregisterReceiver()
        closeHandle()
    }

    private fun normalizeConfig(config: AdaptiveTriggerConfig): AdaptiveTriggerConfig {
        val start = config.startPercent.coerceIn(0, 100)
        val end = config.endPercent.coerceIn(0, 100)
        val normalizedEnd = if (end <= start) (start + 1).coerceAtMost(100) else end

        return config.copy(
            startPercent = start,
            endPercent = normalizedEnd,
            strengthPercent = config.strengthPercent.coerceIn(0, 100),
            speedPercent = config.speedPercent.coerceIn(0, 100)
        )
    }

    private fun setConnectionState(connected: Boolean, log: String) {
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
                    device.productId == DUALSENSE_PRODUCT_ID
        }
    }

    private fun reopenHandle(device: UsbDevice) {
        closeHandle()
        hidHandle = openHidHandle(device)
        startInputReaderIfPossible()
    }

    private fun openHidHandle(device: UsbDevice): HidHandle? {
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

    private fun startInputReaderIfPossible() {
        val handle = hidHandle ?: return
        if (inputReaderRunning) return

        inputReaderRunning = true
        inputReaderThread = Thread {
            val buffer = ByteArray(INPUT_BUFFER_SIZE)

            while (inputReaderRunning) {
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

                if (!inputReaderRunning) break

                if (read > 0) {
                    parseInputReport(buffer, read)
                }
            }
        }.apply {
            name = "DualSenseInputReader"
            isDaemon = true
            start()
        }
    }

    private fun stopInputReader() {
        inputReaderRunning = false
        inputReaderThread?.interrupt()
        inputReaderThread = null
    }

    private fun parseInputReport(buffer: ByteArray, size: Int) {
        if (size <= INPUT_OFFSET_BUTTONS_2) return

        val l2Raw = buffer[INPUT_OFFSET_L2].toUByte().toInt()
        val r2Raw = buffer[INPUT_OFFSET_R2].toUByte().toInt()
        val buttons2 = buffer[INPUT_OFFSET_BUTTONS_2].toUByte().toInt()

        val l2Percent = ((l2Raw / 255f) * 100f).toInt().coerceIn(0, 100)
        val r2Percent = ((r2Raw / 255f) * 100f).toInt().coerceIn(0, 100)

        val l1Pressed = (buttons2 and BUTTON_MASK_L1) != 0
        val r1Pressed = (buttons2 and BUTTON_MASK_R1) != 0

        _uiState.update { current ->
            current.copy(
                leftShoulderPressed = l1Pressed,
                rightShoulderPressed = r1Pressed,
                leftTriggerPressedPercent = l2Percent,
                rightTriggerPressedPercent = r2Percent
            )
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

    private fun closeHandle() {
        stopInputReader()
        hidHandle?.close()
        hidHandle = null

        _uiState.update {
            it.copy(
                leftShoulderPressed = false,
                rightShoulderPressed = false,
                leftTriggerPressedPercent = 0,
                rightTriggerPressedPercent = 0
            )
        }
    }
}