package com.DueBoysenberry1226.ps5ctbro.audio

object NativeAudioBridge {
    init {
        System.loadLibrary("ps5audio")
    }

    external fun nativeIsoStreamStart(
        fd: Int,
        interfaceNumber: Int,
        altSetting: Int,
        endpointAddress: Int
    ): Int

    external fun nativeIsoPushPcm(pcmData: ByteArray): Int

    external fun nativeIsoSetQueueLimit(limit: Int): Int

    external fun nativeIsoSetLowLatencyMode(enabled: Boolean): Int

    external fun nativeIsoStreamStop(): Int

    external fun nativeUsbDeviceReset(fd: Int): Int
}
