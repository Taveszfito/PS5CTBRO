package com.DueBoysenberry1226.ps5ctbro.ui.bt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.lang.reflect.Method

class BtHidOutputProbe(
    private val context: Context
) {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    fun probe(onResult: (String) -> Unit) {
        if (!hasBluetoothConnectPermission()) {
            onResult("BT HID probe failed: hiányzik a BLUETOOTH_CONNECT permission.")
            return
        }

        val bluetoothManager =
            appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

        val adapter: BluetoothAdapter? = bluetoothManager?.adapter

        if (adapter == null) {
            onResult("BT HID probe failed: nincs BluetoothAdapter.")
            return
        }

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                val methods: List<Method> = findInterestingMethods(proxy)
                val connectedDevices: List<BluetoothDevice> = getConnectedHidDevices(proxy)

                val result = buildString {
                    append("BT HID_HOST proxy connected.\n")
                    append("profile=$profile\n")
                    append("proxyClass=${proxy.javaClass.name}\n\n")

                    append("Available interesting methods:\n")

                    if (methods.isEmpty()) {
                        append("- Nincs publikus/reflectionnel látható HID method.\n")
                    } else {
                        for (method in methods) {
                            val params = method.parameterTypes.joinToString { type ->
                                type.simpleName
                            }

                            append("- ${method.name}($params) : ${method.returnType.simpleName}\n")
                        }
                    }

                    append("\nConnected HID devices:\n")

                    if (connectedDevices.isEmpty()) {
                        append("- none / inaccessible\n")
                    } else {
                        for (device in connectedDevices) {
                            append("- ${safeDeviceName(device)} | ${safeDeviceAddress(device)}\n")
                        }
                    }

                    append("\nProbe result:\n")

                    val hasOutputMethod = methods.any { method ->
                        method.name == "setReport" || method.name == "sendData"
                    }

                    if (hasOutputMethod) {
                        append("HID output methods visible. Következő lépés: óvatos dummy report teszt.")
                    } else {
                        append("HID output methods nem láthatók. Lehet, hogy hidden API / permission / OEM blokkolja.")
                    }
                }

                onResult(result)


            }

            override fun onServiceDisconnected(profile: Int) {
                // Ne írjuk felül a sikeres probe logot.
            }
        }

        val requested = runCatching {
            adapter.getProfileProxy(
                appContext,
                listener,
                HID_HOST_PROFILE
            )
        }.getOrElse { error ->
            onResult("BT HID probe failed: ${error.javaClass.simpleName}: ${error.message}")
            false
        }

        if (!requested) {
            onResult("BT HID probe failed: getProfileProxy(HID_HOST) false-t adott vissza.")
        }
    }

    private fun findInterestingMethods(proxy: BluetoothProfile): List<Method> {
        val interestingNames = setOf(
            "getConnectedDevices",
            "getDevicesMatchingConnectionStates",
            "getConnectionState",
            "getPriority",
            "setPriority",
            "getProtocolMode",
            "setProtocolMode",
            "getReport",
            "setReport",
            "sendData",
            "virtualUnplug"
        )

        return proxy.javaClass.methods
            .filter { method -> interestingNames.contains(method.name) }
            .sortedBy { method -> method.name }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getConnectedHidDevices(proxy: BluetoothProfile): List<BluetoothDevice> {
        return try {
            val method = proxy.javaClass.getMethod("getConnectedDevices")
            val value = method.invoke(proxy)

            if (value is List<*>) {
                value.filterIsInstance<BluetoothDevice>()
            } else {
                emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: "unknown"
        } catch (_: Throwable) {
            "name inaccessible"
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeDeviceAddress(device: BluetoothDevice): String {
        return try {
            device.address ?: "unknown"
        } catch (_: Throwable) {
            "address inaccessible"
        }
    }

    companion object {
        private const val HID_HOST_PROFILE = 4
    }
}