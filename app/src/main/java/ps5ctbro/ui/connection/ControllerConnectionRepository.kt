package com.DueBoysenberry1226.ps5ctbro.ui.connection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.view.InputDevice
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

    private val _uiState = MutableStateFlow(ControllerConnectionUiState())
    val uiState: StateFlow<ControllerConnectionUiState> = _uiState.asStateFlow()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    init {
        inputManager.registerInputDeviceListener(this, null)

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                usbReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            appContext.registerReceiver(usbReceiver, filter)
        }

        refresh()
    }

    fun refresh() {
        var deviceName: String? = null
        val type = when {
            hasUsbDualSense() -> {
                deviceName = "DualSense (USB)"
                ControllerConnectionType.USB
            }
            hasInputDualSense() -> {
                val device = getBtDualSenseDevice()
                deviceName = device?.name ?: "Wireless Controller"
                ControllerConnectionType.BLUETOOTH
            }
            else -> ControllerConnectionType.NONE
        }

        _uiState.value = ControllerConnectionUiState(type = type, deviceName = deviceName)
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