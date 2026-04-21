package com.DueBoysenberry1226.ps5ctbro.adaptive

import kotlin.math.roundToInt

object DualSenseTriggerReportBuilder {

    private const val USB_REPORT_ID = 0x02
    private const val USB_REPORT_SIZE = 63

    private const val FLAGS_INDEX_1 = 1
    private const val FLAGS_INDEX_2 = 2

    private const val ALLOW_RIGHT_TRIGGER_FFB = 0x04
    private const val ALLOW_LEFT_TRIGGER_FFB = 0x08

    private const val RIGHT_TRIGGER_OFFSET = 11
    private const val LEFT_TRIGGER_OFFSET = 22
    private const val TRIGGER_BLOCK_SIZE = 11

    private const val MODE_OFF = 0x00
    private const val MODE_SECTION = 0x02
    private const val MODE_MACHINE = 0x27

    fun buildReport(
        left: AdaptiveTriggerConfig,
        right: AdaptiveTriggerConfig,
        leftStrength: Int = 0,
        rightStrength: Int = 0
    ): ByteArray {
        val report = ByteArray(USB_REPORT_SIZE)

        report[0] = USB_REPORT_ID.toByte()
        report[FLAGS_INDEX_1] = (ALLOW_RIGHT_TRIGGER_FFB or ALLOW_LEFT_TRIGGER_FFB).toByte()
        report[FLAGS_INDEX_2] = 0x00

        writeTriggerBlock(
            report = report,
            offset = RIGHT_TRIGGER_OFFSET,
            config = right,
            currentStrength = rightStrength
        )

        writeTriggerBlock(
            report = report,
            offset = LEFT_TRIGGER_OFFSET,
            config = left,
            currentStrength = leftStrength
        )

        return report
    }

    private fun writeTriggerBlock(
        report: ByteArray,
        offset: Int,
        config: AdaptiveTriggerConfig,
        currentStrength: Int
    ) {
        for (i in 0 until TRIGGER_BLOCK_SIZE) {
            report[offset + i] = 0x00
        }

        if (config.effect == AdaptiveTriggerEffect.OFF || currentStrength <= 0) {
            report[offset] = MODE_OFF.toByte()
            return
        }

        when (config.effect) {
            AdaptiveTriggerEffect.RESISTANCE -> writeResistance(report, offset, config, currentStrength)
            AdaptiveTriggerEffect.VIBRATION -> writeMachine(report, offset, config) // Vibrációnál egyelőre marad a fix
            else -> report[offset] = MODE_OFF.toByte()
        }
    }

    private fun writeResistance(
        report: ByteArray,
        offset: Int,
        config: AdaptiveTriggerConfig,
        currentStrength: Int
    ) {
        // Átváltunk MODE_CONTINUOUS (0x01) módra, mert ez stabilabban kezeli az erőt.
        // Mivel szoftveresen kapuzzuk, így is szekcióként fog viselkedni.
        val strength = percentToByte(currentStrength)

        report[offset + 0] = 0x01.toByte() // Continuous Resistance
        report[offset + 1] = 0.toByte()    // Start position (szoftveresen vezérelve)
        report[offset + 2] = strength.toByte() // Force/Strength
    }

    private fun writeMachine(
        report: ByteArray,
        offset: Int,
        config: AdaptiveTriggerConfig
    ) {
        val amplitudeA = percentToStrength7(config.strengthPercent).coerceIn(0, 7)
        val amplitudeB = amplitudeA.coerceAtLeast(1).coerceIn(0, 7)
        val frequency = percentToByte(config.speedPercent).coerceAtLeast(1)
        val period = percentToPeriod(config.speedPercent)

        // Machine módnál (vibráció) 10 zónás maszkot használunk (0x03FF = mind a 10 zóna).
        // Az app-szintű időzítés miatt ez így a legpontosabb.
        val activeZones = 0x03FF 
        val strengthPair = ((amplitudeA and 0x07) shl 0) or ((amplitudeB and 0x07) shl 3)

        report[offset + 0] = MODE_MACHINE.toByte()
        report[offset + 1] = (activeZones and 0xFF).toByte()
        report[offset + 2] = ((activeZones shr 8) and 0xFF).toByte()
        report[offset + 3] = (strengthPair and 0xFF).toByte()
        report[offset + 4] = frequency.toByte()
        report[offset + 5] = period.toByte()
    }

    private fun percentToByte(percent: Int): Int {
        return (percent.coerceIn(0, 100) / 100f * 255f).roundToInt()
    }

    private fun percentToStrength7(percent: Int): Int {
        return (percent.coerceIn(0, 100) / 100f * 7f).roundToInt()
    }

    private fun percentToPeriod(percent: Int): Int {
        val clamped = percent.coerceIn(0, 100)
        return when {
            clamped <= 10 -> 10
            clamped <= 20 -> 9
            clamped <= 30 -> 8
            clamped <= 40 -> 7
            clamped <= 50 -> 6
            clamped <= 60 -> 5
            clamped <= 70 -> 4
            clamped <= 80 -> 3
            clamped <= 90 -> 2
            else -> 1
        }
    }
}