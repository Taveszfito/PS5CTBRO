package com.DueBoysenberry1226.ps5ctbro.ui.vibrate

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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class VibrationController(private val context: Context) {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.DueBoysenberry1226.ps5ctbro.VIBRATE_USB_PERMISSION"
        private const val SONY_VENDOR_ID = 0x054c
        private const val DUALSENSE_PRODUCT_ID = 0x0ce6
    }

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _uiState = MutableStateFlow(VibrationUiState())
    val uiState: StateFlow<VibrationUiState> = _uiState

    private var hidHandle: HidHandle? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var leftJob: Job? = null
    private var rightJob: Job? = null
    private var heartbeatJob: Job? = null

    private data class HidHandle(
        val connection: UsbDeviceConnection,
        val usbInterface: UsbInterface,
        val outEndpoint: UsbEndpoint
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

    init {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(appContext, permissionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        startHeartbeat()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                val state = _uiState.value
                if (state.isLeftActive || state.isRightActive) {
                    updateVibration()
                }
                delay(1000) // Send every second to keep alive and apply updates
            }
        }
    }

    fun onScreenVisible() {
        refreshConnection()
    }

    fun onScreenHidden() {
        stopVibration()
        closeHandle()
        _uiState.update { it.copy(logText = appContext.getString(R.string.log_vibrate_screen_left)) }
    }

    fun refreshConnection() {
        val device = findDualSense()
        if (device == null) {
            _uiState.update { it.copy(controllerConnected = false, logText = appContext.getString(R.string.log_no_usb_dualsense)) }
            return
        }

        if (usbManager.hasPermission(device)) {
            reopenHandle(device)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val intent = PendingIntent.getBroadcast(appContext, 0, Intent(ACTION_USB_PERMISSION), flags)
            usbManager.requestPermission(device, intent)
            _uiState.update { it.copy(logText = appContext.getString(R.string.log_usb_permission_requested)) }
        }
    }

    private fun findDualSense(): UsbDevice? {
        return usbManager.deviceList.values.find {
            it.vendorId == SONY_VENDOR_ID && it.productId == DUALSENSE_PRODUCT_ID
        }
    }

    private fun reopenHandle(device: UsbDevice) {
        closeHandle()
        val intf = findHidInterface(device)
        if (intf == null) {
            _uiState.update { it.copy(logText = appContext.getString(R.string.log_hid_interface_not_found)) }
            return
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            _uiState.update { it.copy(logText = appContext.getString(R.string.log_usb_open_failed)) }
            return
        }

        if (connection.claimInterface(intf, true)) {
            val outEp = (0 until intf.endpointCount).map { intf.getEndpoint(it) }
                .find { it.direction == UsbConstants.USB_DIR_OUT }

            if (outEp != null) {
                hidHandle = HidHandle(connection, intf, outEp)
                _uiState.update { it.copy(controllerConnected = true, logText = appContext.getString(R.string.log_vibrate_control_ready)) }
            } else {
                connection.releaseInterface(intf)
                connection.close()
                _uiState.update { it.copy(logText = "Output endpoint not found.") }
            }
        } else {
            connection.close()
            _uiState.update { it.copy(logText = "Could not claim interface.") }
        }
    }

    private fun findHidInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_HID) return intf
        }
        return null
    }

    private fun closeHandle() {
        hidHandle?.close()
        hidHandle = null
        _uiState.update { it.copy(controllerConnected = false) }
    }

    fun setStrengthLeft(strength: Int) {
        _uiState.update { it.copy(strengthLeftPercent = strength) }
        if (_uiState.value.isLeftActive) updateVibration()
    }

    fun setStrengthRight(strength: Int) {
        _uiState.update { it.copy(strengthRightPercent = strength) }
        if (_uiState.value.isRightActive) updateVibration()
    }

    fun setDuration(seconds: Int) {
        _uiState.update { it.copy(durationSeconds = seconds) }
    }

    fun setInfinite(infinite: Boolean) {
        _uiState.update { it.copy(isInfinite = infinite) }
    }

    fun applyVibration(left: Boolean, right: Boolean) {
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
        
        val sideRes = if (left && right) R.string.vibration_side_both 
                      else if (left) R.string.vibration_side_left 
                      else R.string.vibration_side_right
        val sideName = appContext.getString(sideRes)
        _uiState.update { it.copy(logText = appContext.getString(R.string.log_vibration_applied, sideName)) }
    }

    fun stopVibration() {
        leftJob?.cancel()
        rightJob?.cancel()
        _uiState.update { it.copy(isLeftActive = false, isRightActive = false) }
        updateVibration()
        _uiState.update { it.copy(logText = appContext.getString(R.string.log_vibration_stopped)) }
    }

    private fun updateVibration() {
        val state = _uiState.value
        val left = if (state.isLeftActive) (state.strengthLeftPercent / 100f * 255).toInt().coerceIn(0, 255) else 0
        val right = if (state.isRightActive) (state.strengthRightPercent / 100f * 255).toInt().coerceIn(0, 255) else 0
        sendVibrationReport(left, right)
    }

    private fun sendVibrationReport(left: Int, right: Int) {
        val handle = hidHandle ?: return
        val report = ByteArray(48)
        report[0] = 0x02.toByte() // Report ID
        report[1] = 0xFF.toByte() // Enable all
        report[2] = 0xF7.toByte()
        report[3] = right.toByte()
        report[4] = left.toByte()
        
        handle.connection.bulkTransfer(handle.outEndpoint, report, report.size, 1000)
    }

    fun release() {
        stopVibration()
        closeHandle()
        heartbeatJob?.cancel()
        try { appContext.unregisterReceiver(permissionReceiver) } catch (_: Throwable) {}
        scope.cancel()
    }
}
