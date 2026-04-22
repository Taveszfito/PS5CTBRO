package com.DueBoysenberry1226.ps5ctbro.audio

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager

private const val USB_RECIP_INTERFACE = 0x01

class DualSenseUsbController(
    private val connection: UsbDeviceConnection,
    private val usbInterface: UsbInterface,
    private val outEndpoint: UsbEndpoint,
    private val inEndpoint: UsbEndpoint
) {
    fun send(report: ByteArray): Boolean {
        val sent = connection.bulkTransfer(outEndpoint, report, report.size, 1000)
        return sent == report.size
    }

    fun receive(buffer: ByteArray, timeout: Int = 10): Int {
        return connection.bulkTransfer(inEndpoint, buffer, buffer.size, timeout)
    }

    fun getFeatureReport(reportId: Int, buffer: ByteArray): Int {
        if (buffer.isEmpty()) return -1

        return try {
            buffer.fill(0)
            buffer[0] = reportId.toByte()

            connection.controlTransfer(
                UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
                0x01, // HID_GET_REPORT
                (0x03 shl 8) or reportId, // Feature report
                usbInterface.id,
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

        return try {
            buffer.fill(0)
            buffer[0] = reportId.toByte()

            connection.controlTransfer(
                UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
                0x09, // HID_SET_REPORT
                (0x03 shl 8) or reportId,
                usbInterface.id,
                buffer,
                buffer.size,
                1000
            )

            connection.controlTransfer(
                UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
                0x01, // HID_GET_REPORT
                (0x03 shl 8) or reportId,
                usbInterface.id,
                buffer,
                buffer.size,
                2000
            )
        } catch (_: Throwable) {
            -1
        }
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

                val inEndpoint = findInEndpoint(targetInterface) ?: run {
                    connection.releaseInterface(targetInterface)
                    connection.close()
                    return null
                }

                return DualSenseUsbController(connection, targetInterface, outEndpoint, inEndpoint)
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

        private fun findInEndpoint(intf: UsbInterface): UsbEndpoint? {
            for (i in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(i)
                if (ep.direction == UsbConstants.USB_DIR_IN) return ep
            }
            return null
        }
    }
}