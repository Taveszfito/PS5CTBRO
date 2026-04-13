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
import com.DueBoysenberry1226.ps5ctbro.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

class AdaptiveTriggerControllerImpl(
    context: Context
) : AdaptiveTriggerController {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.DueBoysenberry1226.ps5ctbro.ADAPTIVE_TRIGGER_USB_PERMISSION"
        private const val SONY_VENDOR_ID = 0x054c
        private const val DUALSENSE_PRODUCT_ID = 0x0ce6
        private const val INPUT_BUFFER_SIZE = 64
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

    @Volatile private var inputReaderRunning = false
    private var inputReaderThread: Thread? = null

    @Volatile private var lastLeftActive = false
    @Volatile private var lastRightActive = false

    private data class HidHandle(
        val connection: UsbDeviceConnection,
        val usbInterface: UsbInterface,
        val outEndpoint: UsbEndpoint,
        val inEndpoint: UsbEndpoint
    ) {
        fun close() {
            try { connection.releaseInterface(usbInterface) } catch (_: Throwable) {}
            try { connection.close() } catch (_: Throwable) {}
        }
    }

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                reopenHandle(device)
            }
        }
    }

    init { registerReceiver() }

    override fun onScreenVisible() { refreshConnection() }
    override fun onScreenHidden() { closeHandle() }

    override fun updateTriggerConfig(side: TriggerSide, config: AdaptiveTriggerConfig) {
        _uiState.update { current ->
            when (side) {
                TriggerSide.LEFT -> current.copy(leftTrigger = normalizeConfig(config))
                TriggerSide.RIGHT -> current.copy(rightTrigger = normalizeConfig(config))
            }
        }
    }

    override fun applyCurrentState() {
        lastLeftActive = false
        lastRightActive = false
        val current = _uiState.value
        // Azonnali alkalmazás nyers értékkel
        val l2Raw = (current.leftTriggerPressedPercent / 100f * 255f).toInt()
        val r2Raw = (current.rightTriggerPressedPercent / 100f * 255f).toInt()
        checkAndApplyActiveHelper(l2Raw, r2Raw)
    }

    private fun checkAndApplyActiveHelper(l2Raw: Int, r2Raw: Int) {
        val handle = hidHandle ?: return
        val state = _uiState.value
        
        // JAVÍTÁS: Minden effektnél (RESISTANCE is) használjuk a szoftveres tartományfigyelést.
        // Ezzel az app élőben, a nyers adatok alapján dönt az effekt bekapcsolásáról, 
        // ami megszünteti a hardveres és szoftveres értékek közötti eltérést.
        val leftActive = isInsideRaw(l2Raw, state.leftTrigger, lastLeftActive)
        val rightActive = isInsideRaw(r2Raw, state.rightTrigger, lastRightActive)

        if (leftActive != lastLeftActive || rightActive != lastRightActive) {
            lastLeftActive = leftActive
            lastRightActive = rightActive
            
            val report = DualSenseTriggerReportBuilder.buildReport(
                left = state.leftTrigger,
                right = state.rightTrigger,
                leftEnabled = leftActive,
                rightEnabled = rightActive
            )
            
            try {
                handle.connection.bulkTransfer(handle.outEndpoint, report, report.size, 30)
            } catch (_: Exception) {}
        }
    }

    private fun isInsideRaw(raw: Int, config: AdaptiveTriggerConfig, wasInside: Boolean): Boolean {
        if (config.effect == AdaptiveTriggerEffect.OFF) return false
        
        // Százalék konvertálása nyers 0-255 értékre
        val startRaw = (config.startPercent / 100f * 255f).roundToInt()
        val endRaw = (config.endPercent / 100f * 255f).roundToInt()
        
        // Minimális hiszterézis (kb 0.8% = 2 egység), hogy ne táncoljon a határon
        val margin = if (wasInside) 2 else 0
        
        return raw >= (startRaw - margin) && raw <= (endRaw + margin)
    }

    override fun refreshConnection() {
        val device = findDualSenseDevice() ?: return
        if (usbManager.hasPermission(device)) {
            reopenHandle(device)
            setConnectionState(true, appContext.getString(R.string.log_trigger_connection_active))
        } else {
            requestPermission(device)
        }
    }

    override fun resetTriggers() {
        _uiState.update {
            it.copy(
                leftTrigger = AdaptiveTriggerConfig(effect = AdaptiveTriggerEffect.OFF),
                rightTrigger = AdaptiveTriggerConfig(effect = AdaptiveTriggerEffect.OFF)
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
        val end = config.endPercent.coerceIn(start, 100)
        return config.copy(startPercent = start, endPercent = end)
    }

    private fun setConnectionState(connected: Boolean, log: String) {
        _uiState.update { it.copy(controllerConnected = connected, logText = log) }
    }

    private fun findDualSenseDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { it.vendorId == SONY_VENDOR_ID && it.productId == DUALSENSE_PRODUCT_ID }
    }

    private fun reopenHandle(device: UsbDevice) {
        closeHandle()
        hidHandle = openHidHandle(device)
        startInputReaderIfPossible()
    }

    private fun openHidHandle(device: UsbDevice): HidHandle? {
        val connection = usbManager.openDevice(device) ?: return null
        val targetInterface = findHidInterfaceWithInAndOutEndpoint(device) ?: return null
        connection.claimInterface(targetInterface, true)
        val outEndpoint = findOutEndpoint(targetInterface) ?: return null
        val inEndpoint = findInEndpoint(targetInterface) ?: return null
        return HidHandle(connection, targetInterface, outEndpoint, inEndpoint)
    }

    private fun startInputReaderIfPossible() {
        val handle = hidHandle ?: return
        if (inputReaderRunning) return
        inputReaderRunning = true
        inputReaderThread = Thread {
            val buffer = ByteArray(INPUT_BUFFER_SIZE)
            while (inputReaderRunning) {
                val read = try { handle.connection.bulkTransfer(handle.inEndpoint, buffer, buffer.size, 100) } catch (_: Throwable) { -1 }
                if (read > 0) parseInputReport(buffer, read)
            }
        }.apply { isDaemon = true; start() }
    }

    private fun parseInputReport(buffer: ByteArray, size: Int) {
        if (size <= INPUT_OFFSET_BUTTONS_2) return
        val l2Raw = buffer[INPUT_OFFSET_L2].toUByte().toInt()
        val r2Raw = buffer[INPUT_OFFSET_R2].toUByte().toInt()

        // Élő vezérlés nyers értékekkel
        checkAndApplyActiveHelper(l2Raw, r2Raw)

        val l2Percent = ((l2Raw / 255f) * 100f).roundToInt().coerceIn(0, 100)
        val r2Percent = ((r2Raw / 255f) * 100f).roundToInt().coerceIn(0, 100)

        val buttons2 = buffer[INPUT_OFFSET_BUTTONS_2].toUByte().toInt()
        _uiState.update { current ->
            current.copy(
                leftShoulderPressed = (buttons2 and BUTTON_MASK_L1) != 0,
                rightShoulderPressed = (buttons2 and BUTTON_MASK_R1) != 0,
                leftTriggerPressedPercent = l2Percent,
                rightTriggerPressedPercent = r2Percent
            )
        }
    }

    private fun findHidInterfaceWithInAndOutEndpoint(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val itf = device.getInterface(i)
            if (itf.interfaceClass == UsbConstants.USB_CLASS_HID && findOutEndpoint(itf) != null && findInEndpoint(itf) != null) return itf
        }
        return null
    }

    private fun findOutEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_OUT) return ep
        }
        return null
    }

    private fun findInEndpoint(usbInterface: UsbInterface): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val ep = usbInterface.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_IN) return ep
        }
        return null
    }

    private fun requestPermission(device: UsbDevice) {
        val intent = PendingIntent.getBroadcast(appContext, 0, Intent(ACTION_USB_PERMISSION), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        usbManager.requestPermission(device, intent)
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(appContext, permissionReceiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try { appContext.unregisterReceiver(permissionReceiver) } catch (_: Throwable) {}
        receiverRegistered = false
    }

    private fun closeHandle() {
        inputReaderRunning = false
        hidHandle?.close()
        hidHandle = null
        lastLeftActive = false
        lastRightActive = false
    }
}