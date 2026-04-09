package com.DueBoysenberry1226.ps5ctbro.audio

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

class DualSenseUsbController(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val outEndpoint: UsbEndpoint
) {
    fun send(report: ByteArray): Boolean {
        val sent = connection.bulkTransfer(outEndpoint, report, report.size, 1000)
        return sent == report.size
    }

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

    companion object {
        fun open(usbManager: UsbManager, device: UsbDevice): DualSenseUsbController? {
            if (!usbManager.hasPermission(device)) return null

            val connection = try {
                usbManager.openDevice(device)
            } catch (_: SecurityException) {
                return null
            } ?: return null

            try {
                val targetInterface = findHidInterfaceWithOutEndpoint(device) ?: run {
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

                return DualSenseUsbController(connection, targetInterface, outEndpoint)
            } catch (_: Throwable) {
                try {
                    connection.close()
                } catch (_: Throwable) {
                }
                return null
            }
        }

        private fun findHidInterfaceWithOutEndpoint(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass == UsbConstants.USB_CLASS_HID && findOutEndpoint(intf) != null) {
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
    }
}