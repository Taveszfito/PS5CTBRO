package com.DueBoysenberry1226.ps5ctbro.ui.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.view.InputDevice
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ControllerConnectionRepository private constructor(
    private val appContext: Context
) : InputManager.InputDeviceListener {

    private val usbManager =
        appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private val inputManager =
        appContext.getSystemService(Context.INPUT_SERVICE) as InputManager

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val _uiState = MutableStateFlow(ControllerConnectionUiState())
    val uiState: StateFlow<ControllerConnectionUiState> = _uiState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    init {
        inputManager.registerInputDeviceListener(this, null)

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            appContext.registerReceiver(receiver, filter)
        }

        refresh()
    }

    fun refresh() {
        var deviceName: String? = null
        var btAddress: String? = null
        var batteryLevel: Int = -1
        
        val type = when {
            hasUsbDualSense() -> {
                deviceName = "DualSense (USB)"
                ControllerConnectionType.USB
            }
            hasInputDualSense() -> {
                val inputDevice = getBtDualSenseDevice()
                deviceName = inputDevice?.name ?: "Wireless Controller"
                
                // Megpróbáljuk megkeresni a Bluetooth eszközt a rendszerben a név alapján
                val btDevice = findBluetoothDeviceByName(deviceName)
                if (btDevice != null) {
                    btAddress = btDevice.address
                    // A getBatteryLevel() csak API 28+ felett érhető el megbízhatóan
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        try {
                            batteryLevel = btDevice.javaClass.getMethod("getBatteryLevel").invoke(btDevice) as Int
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                
                ControllerConnectionType.BLUETOOTH
            }
            else -> ControllerConnectionType.NONE
        }

        _uiState.value = ControllerConnectionUiState(
            type = type, 
            deviceName = deviceName,
            btAddress = btAddress,
            batteryLevel = batteryLevel
        )
    }

    private fun findBluetoothDeviceByName(name: String): BluetoothDevice? {
        try {
            val adapter = bluetoothManager.adapter ?: return null
            val pairedDevices = adapter.bondedDevices
            return pairedDevices.find { 
                it.name == name || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && it.alias == name)
            }
        } catch (e: SecurityException) {
            return null
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        refresh()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        refresh()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        refresh()
    }

    private fun hasUsbDualSense(): Boolean {
        val devices: Collection<UsbDevice> = usbManager.deviceList.values

        for (device: UsbDevice in devices) {
            if (
                device.vendorId == SONY_VENDOR_ID &&
                DUALSENSE_PRODUCT_IDS.contains(device.productId)
            ) {
                return true
            }
        }

        return false
    }

    private fun getBtDualSenseDevice(): InputDevice? {
        val ids: IntArray = InputDevice.getDeviceIds()

        for (id: Int in ids) {
            val device: InputDevice = InputDevice.getDevice(id) ?: continue
            val sources: Int = device.sources
            val isGamepad =
                sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                        sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

            if (!isGamepad) continue

            val name = device.name.orEmpty().lowercase()
            val isSony =
                device.vendorId == SONY_VENDOR_ID ||
                        name.contains("dualsense") ||
                        name.contains("wireless controller") ||
                        name.contains("dualshock")

            if (isSony) {
                return device
            }
        }

        return null
    }

    private fun hasInputDualSense(): Boolean {
        return getBtDualSenseDevice() != null
    }

    companion object {
        private const val SONY_VENDOR_ID = 0x054C

        private val DUALSENSE_PRODUCT_IDS = setOf(
            0x0CE6, // DualSense USB
            0x0DF2  // DualSense Edge USB
        )

        @Volatile
        private var INSTANCE: ControllerConnectionRepository? = null

        fun get(context: Context): ControllerConnectionRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ControllerConnectionRepository(
                    context.applicationContext
                ).also { created ->
                    INSTANCE = created
                }
            }
        }
    }
}