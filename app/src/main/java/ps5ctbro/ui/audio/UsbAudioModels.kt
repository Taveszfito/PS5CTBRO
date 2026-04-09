package com.DueBoysenberry1226.ps5ctbro.audio

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface

data class AudioIsoTarget(
    val interfaceNumber: Int,
    val altSetting: Int,
    val endpointAddress: Int,
    val packetSize: Int
)

data class AudioRouteSession(
    val device: UsbDevice,
    val connection: UsbDeviceConnection,
    val target: AudioIsoTarget,
    val claimedInterface: UsbInterface
)