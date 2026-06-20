package com.DueBoysenberry1226.ps5ctbro.ui.hid

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DualSenseUsbHidState(
    val connected: Boolean = false,
    val logText: String = "DualSense HID manager keszen all."
)

private const val USB_RECIP_INTERFACE = 0x01

class DualSenseUsbHidManager private constructor(context: Context) {
    companion object {
        private const val ACTION_USB_PERMISSION =
            "com.DueBoysenberry1226.ps5ctbro.SHARED_HID_USB_PERMISSION"
        private const val SONY_VENDOR_ID = 0x054c
        private val DUALSENSE_PRODUCT_IDS = setOf(0x0ce6, 0x0df2)
        private const val INPUT_BUFFER_SIZE = 64

        @Volatile
        private var INSTANCE: DualSenseUsbHidManager? = null

        fun get(context: Context): DualSenseUsbHidManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DualSenseUsbHidManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(DualSenseUsbHidState())
    val state: StateFlow<DualSenseUsbHidState> = _state.asStateFlow()

    private val _inputReports = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val inputReports: SharedFlow<ByteArray> = _inputReports.asSharedFlow()

    private var receiverRegistered = false
    private var connection: UsbDeviceConnection? = null
    private var hidInterface: UsbInterface? = null
    private var outEndpoint: UsbEndpoint? = null
    private var inEndpoint: UsbEndpoint? = null

    @Volatile
    private var inputReaderRunning = false
    private var inputReaderThread: Thread? = null

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
                open(device)
            } else {
                setState(false, "USB HID engedely elutasitva.")
            }
        }
    }

    init {
        registerReceiver()
    }

    fun refreshConnection(): Boolean {
        if (isOpen()) {
            setState(true, "DualSense HID kapcsolat aktiv.")
            return true
        }

        val device = findDualSenseDevice()
        if (device == null) {
            close()
            setState(false, "Nincs USB DualSense csatlakoztatva.")
            return false
        }

        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            setState(false, "USB HID engedely keres folyamatban.")
            return false
        }

        return open(device)
    }

    fun send(report: ByteArray, timeoutMs: Int = 1000): Boolean {
        if (!isOpen() && !refreshConnection()) return false

        val conn = connection ?: return false
        val endpoint = outEndpoint ?: return false
        val mergedReport = DualSenseOutputReportMerger.merge(report)
        val sent = try {
            conn.bulkTransfer(endpoint, mergedReport, mergedReport.size, timeoutMs)
        } catch (_: Throwable) {
            -1
        }

        val ok = sent == mergedReport.size
        if (!ok) {
            setState(false, "HID output kuldes sikertelen: $sent byte.")
            return false
        }

        if (DualSenseOutputReportMerger.isMusicRumbleRouteActive()) {
            val wakeReport = DualSenseOutputReportMerger.musicRumbleWakeReport()
            try {
                conn.bulkTransfer(endpoint, wakeReport, wakeReport.size, timeoutMs)
            } catch (_: Throwable) {
            }
        }
        return true
    }

    fun sendRaw(report: ByteArray, timeoutMs: Int = 1000): Boolean {
        if (!isOpen() && !refreshConnection()) return false

        val conn = connection ?: return false
        val endpoint = outEndpoint ?: return false
        val sent = try {
            conn.bulkTransfer(endpoint, report, report.size, timeoutMs)
        } catch (_: Throwable) {
            -1
        }

        val ok = sent == report.size
        if (!ok) {
            setState(false, "Nyers HID output kuldes sikertelen: $sent byte.")
        }
        return ok
    }

    fun receive(buffer: ByteArray, timeoutMs: Int = 10): Int {
        if (!isOpen() && !refreshConnection()) return -1

        val conn = connection ?: return -1
        val endpoint = inEndpoint ?: return -1
        return try {
            conn.bulkTransfer(endpoint, buffer, buffer.size, timeoutMs)
        } catch (_: Throwable) {
            -1
        }
    }

    fun getFeatureReport(reportId: Int, buffer: ByteArray): Int {
        if (buffer.isEmpty()) return -1
        if (!isOpen() && !refreshConnection()) return -1

        val conn = connection ?: return -1
        val intf = hidInterface ?: return -1
        return try {
            buffer.fill(0)
            buffer[0] = reportId.toByte()
            conn.controlTransfer(
                UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
                0x01,
                (0x03 shl 8) or reportId,
                intf.id,
                buffer,
                buffer.size,
                2000
            )
        } catch (_: Throwable) {
            -1
        }
    }

    fun primeAndGetFeatureReport(reportId: Int, buffer: ByteArray): Int {
        if (buffer.isEmpty()) return -1
        if (!isOpen() && !refreshConnection()) return -1

        val conn = connection ?: return -1
        val intf = hidInterface ?: return -1
        return try {
            buffer.fill(0)
            buffer[0] = reportId.toByte()
            conn.controlTransfer(
                UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
                0x09,
                (0x03 shl 8) or reportId,
                intf.id,
                buffer,
                buffer.size,
                1000
            )
            conn.controlTransfer(
                UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
                0x01,
                (0x03 shl 8) or reportId,
                intf.id,
                buffer,
                buffer.size,
                2000
            )
        } catch (_: Throwable) {
            -1
        }
    }

    fun close() {
        stopInputReader()
        try {
            hidInterface?.let { connection?.releaseInterface(it) }
        } catch (_: Throwable) {
        }
        try {
            connection?.close()
        } catch (_: Throwable) {
        }
        connection = null
        hidInterface = null
        outEndpoint = null
        inEndpoint = null
        setState(false, "DualSense HID kapcsolat zarva.")
    }

    fun release() {
        close()
        unregisterReceiver()
        scope.cancel()
    }

    private fun open(device: UsbDevice): Boolean {
        close()

        val conn = try {
            usbManager.openDevice(device)
        } catch (_: SecurityException) {
            null
        } ?: run {
            setState(false, "USB HID openDevice sikertelen.")
            return false
        }

        val intf = findHidInterfaceWithInAndOutEndpoint(device)
        if (intf == null) {
            try { conn.close() } catch (_: Throwable) {}
            setState(false, "Nem talaltam HID IN/OUT interface-t.")
            return false
        }

        val claimed = try {
            conn.claimInterface(intf, true)
        } catch (_: Throwable) {
            false
        }

        if (!claimed) {
            try { conn.close() } catch (_: Throwable) {}
            setState(false, "HID interface claim sikertelen.")
            return false
        }

        connection = conn
        hidInterface = intf
        outEndpoint = findOutEndpoint(intf)
        inEndpoint = findInEndpoint(intf)

        if (outEndpoint == null || inEndpoint == null) {
            close()
            setState(false, "HID endpoint hianyzik.")
            return false
        }

        setState(true, "DualSense HID manager kapcsolat aktiv.")
        startInputReader()
        return true
    }

    private fun startInputReader() {
        if (inputReaderRunning) return
        val conn = connection ?: return
        val endpoint = inEndpoint ?: return

        inputReaderRunning = true
        inputReaderThread = Thread {
            val buffer = ByteArray(INPUT_BUFFER_SIZE)
            while (inputReaderRunning) {
                val read = try {
                    conn.bulkTransfer(endpoint, buffer, buffer.size, 100)
                } catch (_: Throwable) {
                    -1
                }

                if (!inputReaderRunning) break
                if (read > 0) {
                    _inputReports.tryEmit(buffer.copyOf(read))
                }
            }
        }.apply {
            name = "DualSenseSharedHidInputReader"
            isDaemon = true
            start()
        }
    }

    private fun stopInputReader() {
        inputReaderRunning = false
        inputReaderThread?.interrupt()
        inputReaderThread = null
    }

    private fun isOpen(): Boolean {
        return connection != null && hidInterface != null && outEndpoint != null && inEndpoint != null
    }

    private fun setState(connected: Boolean, log: String) {
        _state.update { it.copy(connected = connected, logText = log) }
    }

    private fun findDualSenseDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull {
            it.vendorId == SONY_VENDOR_ID && DUALSENSE_PRODUCT_IDS.contains(it.productId)
        }
    }

    private fun findHidInterfaceWithInAndOutEndpoint(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_HID &&
                findOutEndpoint(intf) != null &&
                findInEndpoint(intf) != null
            ) {
                return intf
            }
        }
        return null
    }

    private fun findOutEndpoint(intf: UsbInterface): UsbEndpoint? {
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_OUT) return ep
        }
        return null
    }

    private fun findInEndpoint(intf: UsbInterface): UsbEndpoint? {
        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_IN) return ep
        }
        return null
    }

    private fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        usbManager.requestPermission(
            device,
            PendingIntent.getBroadcast(appContext, 0, Intent(ACTION_USB_PERMISSION), flags)
        )
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
