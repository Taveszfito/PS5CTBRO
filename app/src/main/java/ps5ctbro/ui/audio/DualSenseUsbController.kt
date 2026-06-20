package com.DueBoysenberry1226.ps5ctbro.audio

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.DueBoysenberry1226.ps5ctbro.ui.hid.DualSenseOutputReportMerger
import com.DueBoysenberry1226.ps5ctbro.ui.hid.DualSenseUsbHidManager

private const val USB_RECIP_INTERFACE = 0x01

class DualSenseUsbController(
    private val connection: UsbDeviceConnection? = null,
    private val usbInterface: UsbInterface? = null,
    private val outEndpoint: UsbEndpoint? = null,
    private val inEndpoint: UsbEndpoint? = null,
    private val sharedHidManager: DualSenseUsbHidManager? = null
) {
    fun send(report: ByteArray): Boolean {
        sharedHidManager?.let { return it.send(report) }
        val conn = connection ?: return false
        val endpoint = outEndpoint ?: return false
        val mergedReport = DualSenseOutputReportMerger.merge(report)
        val sent = conn.bulkTransfer(endpoint, mergedReport, mergedReport.size, 1000)
        if (sent != mergedReport.size) return false

        if (DualSenseOutputReportMerger.isMusicRumbleRouteActive()) {
            val wakeReport = DualSenseOutputReportMerger.musicRumbleWakeReport()
            try {
                conn.bulkTransfer(endpoint, wakeReport, wakeReport.size, 1000)
            } catch (_: Throwable) {
            }
        }

        return true
    }

    fun sendRaw(report: ByteArray): Boolean {
        sharedHidManager?.let { return it.sendRaw(report) }
        val conn = connection ?: return false
        val endpoint = outEndpoint ?: return false
        val sent = conn.bulkTransfer(endpoint, report, report.size, 1000)
        return sent == report.size
    }

    fun receive(buffer: ByteArray, timeout: Int = 10): Int {
        sharedHidManager?.let { return it.receive(buffer, timeout) }
        val conn = connection ?: return -1
        val endpoint = inEndpoint ?: return -1
        return conn.bulkTransfer(endpoint, buffer, buffer.size, timeout)
    }

    fun getFeatureReport(reportId: Int, buffer: ByteArray): Int {
        sharedHidManager?.let { return it.getFeatureReport(reportId, buffer) }
        if (buffer.isEmpty()) return -1
        val conn = connection ?: return -1
        val intf = usbInterface ?: return -1

        return try {
            buffer.fill(0)
            buffer[0] = reportId.toByte()

            conn.controlTransfer(
                UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
                0x01, // HID_GET_REPORT
                (0x03 shl 8) or reportId, // Feature report
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
        sharedHidManager?.let { return it.primeAndGetFeatureReport(reportId, buffer) }
        if (buffer.isEmpty()) return -1
        val conn = connection ?: return -1
        val intf = usbInterface ?: return -1

        return try {
            buffer.fill(0)
            buffer[0] = reportId.toByte()

            conn.controlTransfer(
                UsbConstants.USB_DIR_OUT or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
                0x09, // HID_SET_REPORT
                (0x03 shl 8) or reportId,
                intf.id,
                buffer,
                buffer.size,
                1000
            )

            conn.controlTransfer(
                UsbConstants.USB_DIR_IN or UsbConstants.USB_TYPE_CLASS or USB_RECIP_INTERFACE,
                0x01, // HID_GET_REPORT
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
        if (sharedHidManager != null) return
        try {
            usbInterface?.let { connection?.releaseInterface(it) }
        } catch (_: Throwable) {
        }

        try {
            connection?.close()
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun shared(context: Context): DualSenseUsbController {
            return DualSenseUsbController(
                sharedHidManager = DualSenseUsbHidManager.get(context.applicationContext)
            )
        }

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
