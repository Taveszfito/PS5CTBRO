package com.DueBoysenberry1226.ps5ctbro.adaptive

import kotlin.math.ceil
import kotlin.math.floor
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
    private const val MODE_WEAPON = 0x25
    private const val MODE_MACHINE = 0x27

    fun buildReport(
        left: AdaptiveTriggerConfig,
        right: AdaptiveTriggerConfig
    ): ByteArray {
        val report = ByteArray(USB_REPORT_SIZE)

        report[0] = USB_REPORT_ID.toByte()
        report[FLAGS_INDEX_1] = (ALLOW_RIGHT_TRIGGER_FFB or ALLOW_LEFT_TRIGGER_FFB).toByte()
        report[FLAGS_INDEX_2] = 0x00

        writeTriggerBlock(
            report = report,
            offset = RIGHT_TRIGGER_OFFSET,
            config = right
        )

        writeTriggerBlock(
            report = report,
            offset = LEFT_TRIGGER_OFFSET,
            config = left
        )

        return report
    }

    private fun writeTriggerBlock(
        report: ByteArray,
        offset: Int,
        config: AdaptiveTriggerConfig
    ) {
        for (i in 0 until TRIGGER_BLOCK_SIZE) {
            report[offset + i] = 0x00
        }

        when (config.effect) {
            AdaptiveTriggerEffect.OFF -> writeOff(report, offset)
            AdaptiveTriggerEffect.RESISTANCE -> writeWeapon(report, offset, config)
            AdaptiveTriggerEffect.VIBRATION -> writeMachine(report, offset, config)
        }
    }

    private fun writeOff(report: ByteArray, offset: Int) {
        report[offset] = MODE_OFF.toByte()
    }

    private fun writeWeapon(
        report: ByteArray,
        offset: Int,
        config: AdaptiveTriggerConfig
    ) {
        val startZone = percentToStartZone(config.startPercent).coerceIn(0, 8)
        val endZone = percentToEndZone(config.endPercent).coerceIn(startZone, 9)
        val strength = percentToStrength8(config.strengthPercent)

        if (strength <= 0) {
            writeOff(report, offset)
            return
        }

        val activeZones = buildContinuousZoneMask(startZone, endZone)

        report[offset + 0] = MODE_WEAPON.toByte()
        report[offset + 1] = (activeZones and 0xFF).toByte()
        report[offset + 2] = ((activeZones shr 8) and 0xFF).toByte()
        report[offset + 3] = (strength - 1).toByte()
        report[offset + 4] = 0x00
        report[offset + 5] = 0x00
        report[offset + 6] = 0x00
        report[offset + 7] = 0x00
        report[offset + 8] = 0x00
        report[offset + 9] = 0x00
        report[offset + 10] = 0x00
    }

    private fun writeMachine(
        report: ByteArray,
        offset: Int,
        config: AdaptiveTriggerConfig
    ) {
        val startZone = percentToStartZone(config.startPercent).coerceIn(0, 8)
        val endZone = percentToEndZone(config.endPercent).coerceIn(startZone, 9)

        val amplitudeA = percentToStrength7(config.strengthPercent).coerceIn(0, 7)
        val amplitudeB = amplitudeA.coerceAtLeast(1).coerceIn(0, 7)

        val frequency = percentToByte(config.speedPercent).coerceAtLeast(1)
        val period = percentToPeriod(config.speedPercent)

        if (amplitudeA <= 0 || frequency <= 0) {
            writeOff(report, offset)
            return
        }

        val activeZones = buildContinuousZoneMask(startZone, endZone)
        val strengthPair = ((amplitudeA and 0x07) shl 0) or
                ((amplitudeB and 0x07) shl 3)

        report[offset + 0] = MODE_MACHINE.toByte()
        report[offset + 1] = (activeZones and 0xFF).toByte()
        report[offset + 2] = ((activeZones shr 8) and 0xFF).toByte()
        report[offset + 3] = (strengthPair and 0xFF).toByte()
        report[offset + 4] = frequency.toByte()
        report[offset + 5] = period.toByte()
        report[offset + 6] = 0x00
        report[offset + 7] = 0x00
        report[offset + 8] = 0x00
        report[offset + 9] = 0x00
        report[offset + 10] = 0x00
    }

    private fun buildContinuousZoneMask(startZone: Int, endZone: Int): Int {
        var mask = 0
        for (zone in startZone..endZone) {
            mask = mask or (1 shl zone)
        }
        return mask
    }

    private fun percentToStartZone(percent: Int): Int {
        val normalized = percent.coerceIn(0, 100) / 100f
        return floor(normalized * 9f).toInt()
    }

    private fun percentToEndZone(percent: Int): Int {
        val normalized = percent.coerceIn(0, 100) / 100f
        return ceil(normalized * 9f).toInt()
    }

    private fun percentToStrength8(percent: Int): Int {
        val normalized = percent.coerceIn(0, 100) / 100f
        return (normalized * 8f).roundToInt().coerceIn(0, 8)
    }

    private fun percentToStrength7(percent: Int): Int {
        val normalized = percent.coerceIn(0, 100) / 100f
        return (normalized * 7f).roundToInt().coerceIn(0, 7)
    }

    private fun percentToByte(percent: Int): Int {
        val normalized = percent.coerceIn(0, 100) / 100f
        return (normalized * 255f).roundToInt().coerceIn(0, 255)
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