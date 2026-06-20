package com.DueBoysenberry1226.ps5ctbro.ui.hid

object DualSenseOutputReportMerger {
    private const val OUTPUT_REPORT_ID_USB = 0x02
    private const val OUTPUT_REPORT_SIZE_USB = 63

    private const val VALID_FLAG0_INDEX = 1
    private const val VALID_FLAG1_INDEX = 2
    private const val RIGHT_RUMBLE_INDEX = 3
    private const val LEFT_RUMBLE_INDEX = 4
    private const val SPEAKER_VOLUME_INDEX = 5
    private const val SPEAKER_ROUTE_VOLUME_INDEX = 6
    private const val MIC_VOLUME_INDEX = 7
    private const val AUDIO_ROUTE_INDEX = 8
    private const val MIC_LED_INDEX = 9
    private const val RIGHT_TRIGGER_OFFSET = 11
    private const val LEFT_TRIGGER_OFFSET = 22
    private const val TRIGGER_BLOCK_SIZE = 11
    private const val VALID_FLAG2_INDEX = 39
    private const val LIGHTBAR_SETUP_INDEX = 42
    private const val LED_BRIGHTNESS_INDEX = 43
    private const val PLAYER_LEDS_INDEX = 44
    private const val LIGHTBAR_RED_INDEX = 45
    private const val LIGHTBAR_GREEN_INDEX = 46
    private const val LIGHTBAR_BLUE_INDEX = 47

    private const val VALID_FLAG1_MIC_MUTE_LED_CONTROL_ENABLE = 0x01
    private const val VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE = 0x04
    private const val VALID_FLAG1_PLAYER_INDICATOR_CONTROL_ENABLE = 0x10
    private const val VALID_FLAG2_LED_BRIGHTNESS_CONTROL_ENABLE = 0x01
    private const val VALID_FLAG2_LIGHTBAR_SETUP_CONTROL_ENABLE = 0x02
    private const val ALLOW_RIGHT_TRIGGER_FFB = 0x04
    private const val ALLOW_LEFT_TRIGGER_FFB = 0x08
    private const val AUDIO_ROUTE_ENABLE_BITS = 0xE0
    private const val AUDIO_ROUTE_FLAG = 0xF3
    private const val NATIVE_RUMBLE_FLAG0 = 0xFF
    private const val NATIVE_RUMBLE_FLAG1 = 0xF7
    private const val MUSIC_RUMBLE_FLAG1 = 0x15
    private const val MUSIC_RUMBLE_VALID_FLAG2 = 0x03
    private const val MUSIC_RUMBLE_SETUP = 0x02

    private val lock = Any()
    private val state = ByteArray(OUTPUT_REPORT_SIZE_USB).also {
        it[0] = OUTPUT_REPORT_ID_USB.toByte()
    }
    private var audioRouteActive = false
    private var musicRumbleRouteActive = false
    private var directRumbleActive = false

    fun merge(report: ByteArray): ByteArray {
        if (report.isEmpty()) return report
        if ((report[0].toInt() and 0xFF) != OUTPUT_REPORT_ID_USB) return report

        synchronized(lock) {
            val incoming = report.copyOf(OUTPUT_REPORT_SIZE_USB)
            mergeAudio(incoming)
            mergeRumble(incoming)
            mergeTriggers(incoming)
            mergeLeds(incoming)

            state[0] = OUTPUT_REPORT_ID_USB.toByte()
            keepMusicRumbleWakeAlive()

            return state.copyOf()
        }
    }

    fun clearRumble(): ByteArray {
        synchronized(lock) {
            state[RIGHT_RUMBLE_INDEX] = 0
            state[LEFT_RUMBLE_INDEX] = 0
            directRumbleActive = false
            keepMusicRumbleWakeAlive()
            return state.copyOf()
        }
    }

    fun isDirectRumbleActive(): Boolean {
        synchronized(lock) {
            return directRumbleActive
        }
    }

    fun isMusicRumbleRouteActive(): Boolean {
        synchronized(lock) {
            return musicRumbleRouteActive
        }
    }

    fun musicRumbleWakeReport(): ByteArray {
        synchronized(lock) {
            return ByteArray(OUTPUT_REPORT_SIZE_USB).also {
                it[0] = OUTPUT_REPORT_ID_USB.toByte()
                it[VALID_FLAG1_INDEX] = MUSIC_RUMBLE_FLAG1.toByte()
                it[VALID_FLAG2_INDEX] = MUSIC_RUMBLE_VALID_FLAG2.toByte()
                it[MIC_LED_INDEX] = state[MIC_LED_INDEX]
                it[LIGHTBAR_SETUP_INDEX] = MUSIC_RUMBLE_SETUP.toByte()
                it[LED_BRIGHTNESS_INDEX] = state[LED_BRIGHTNESS_INDEX]
                it[PLAYER_LEDS_INDEX] = state[PLAYER_LEDS_INDEX]
                it[LIGHTBAR_RED_INDEX] = state[LIGHTBAR_RED_INDEX]
                it[LIGHTBAR_GREEN_INDEX] = state[LIGHTBAR_GREEN_INDEX]
                it[LIGHTBAR_BLUE_INDEX] = state[LIGHTBAR_BLUE_INDEX]
            }
        }
    }

    private fun mergeAudio(report: ByteArray) {
        val flag0 = report[VALID_FLAG0_INDEX].toInt() and 0xFF
        val flag1 = report[VALID_FLAG1_INDEX].toInt() and 0xFF
        val isNativeRumbleReport =
            flag0 == NATIVE_RUMBLE_FLAG0 || flag1 == NATIVE_RUMBLE_FLAG1
        val hasAudioRoute =
            report[SPEAKER_VOLUME_INDEX] != 0.toByte() ||
                    report[SPEAKER_ROUTE_VOLUME_INDEX] != 0.toByte() ||
                    report[MIC_VOLUME_INDEX] != 0.toByte() ||
                    report[AUDIO_ROUTE_INDEX] != 0.toByte() ||
                    ((flag0 and AUDIO_ROUTE_ENABLE_BITS) != 0 && !isNativeRumbleReport)
        val hasMusicRumbleRoute =
            (report[VALID_FLAG1_INDEX].toInt() and 0xFF) == MUSIC_RUMBLE_FLAG1 &&
                    (report[VALID_FLAG2_INDEX].toInt() and 0xFF) == MUSIC_RUMBLE_VALID_FLAG2 &&
                    (report[LIGHTBAR_SETUP_INDEX].toInt() and 0xFF) == MUSIC_RUMBLE_SETUP

        if (!hasAudioRoute && !hasMusicRumbleRoute) return

        if (hasAudioRoute) {
            audioRouteActive = true
            state[VALID_FLAG0_INDEX] = flag0.toByte()
            state[SPEAKER_VOLUME_INDEX] = report[SPEAKER_VOLUME_INDEX]
            state[SPEAKER_ROUTE_VOLUME_INDEX] = report[SPEAKER_ROUTE_VOLUME_INDEX]
            state[MIC_VOLUME_INDEX] = report[MIC_VOLUME_INDEX]
            state[AUDIO_ROUTE_INDEX] = report[AUDIO_ROUTE_INDEX]
        }

        if (hasMusicRumbleRoute) {
            musicRumbleRouteActive = true
            state[VALID_FLAG1_INDEX] = MUSIC_RUMBLE_FLAG1.toByte()
            state[VALID_FLAG2_INDEX] =
                (state[VALID_FLAG2_INDEX].toInt() or MUSIC_RUMBLE_VALID_FLAG2).toByte()
            state[LIGHTBAR_SETUP_INDEX] = MUSIC_RUMBLE_SETUP.toByte()
            state[MIC_LED_INDEX] = report[MIC_LED_INDEX]
            state[LED_BRIGHTNESS_INDEX] = report[LED_BRIGHTNESS_INDEX]
            if (report[PLAYER_LEDS_INDEX] != 0.toByte()) {
                state[PLAYER_LEDS_INDEX] = report[PLAYER_LEDS_INDEX]
            }
        }
    }

    private fun mergeRumble(report: ByteArray) {
        val hasRumbleFlags =
            (report[VALID_FLAG0_INDEX].toInt() and 0xFF) == NATIVE_RUMBLE_FLAG0 ||
                    (report[VALID_FLAG1_INDEX].toInt() and 0xFF) == NATIVE_RUMBLE_FLAG1
        val hasRumbleBytes =
            report[RIGHT_RUMBLE_INDEX] != 0.toByte() || report[LEFT_RUMBLE_INDEX] != 0.toByte()

        if (!hasRumbleFlags && !hasRumbleBytes) return

        directRumbleActive = hasRumbleBytes

        state[VALID_FLAG0_INDEX] =
            if (audioRouteActive) {
                val current = state[VALID_FLAG0_INDEX].toInt() and 0xFF
                if ((current and AUDIO_ROUTE_ENABLE_BITS) != 0) {
                    state[VALID_FLAG0_INDEX]
                } else {
                    AUDIO_ROUTE_FLAG.toByte()
                }
            } else {
                NATIVE_RUMBLE_FLAG0.toByte()
            }
        if (musicRumbleRouteActive) {
            state[VALID_FLAG1_INDEX] = MUSIC_RUMBLE_FLAG1.toByte()
            state[VALID_FLAG2_INDEX] =
                (state[VALID_FLAG2_INDEX].toInt() or MUSIC_RUMBLE_VALID_FLAG2).toByte()
            state[LIGHTBAR_SETUP_INDEX] = MUSIC_RUMBLE_SETUP.toByte()
            if (state[PLAYER_LEDS_INDEX] == 0.toByte()) {
                state[PLAYER_LEDS_INDEX] = 0x24.toByte()
            }
        } else {
            state[VALID_FLAG1_INDEX] =
                (state[VALID_FLAG1_INDEX].toInt() or NATIVE_RUMBLE_FLAG1).toByte()
        }
        state[RIGHT_RUMBLE_INDEX] = report[RIGHT_RUMBLE_INDEX]
        state[LEFT_RUMBLE_INDEX] = report[LEFT_RUMBLE_INDEX]
    }

    private fun mergeTriggers(report: ByteArray) {
        val flags = report[VALID_FLAG0_INDEX].toInt() and 0xFF
        val hasTriggerFlags =
            (flags and (ALLOW_RIGHT_TRIGGER_FFB or ALLOW_LEFT_TRIGGER_FFB)) != 0

        if (!hasTriggerFlags) return

        state[VALID_FLAG0_INDEX] = (state[VALID_FLAG0_INDEX].toInt() or flags).toByte()
        copyRange(report, state, RIGHT_TRIGGER_OFFSET, TRIGGER_BLOCK_SIZE)
        copyRange(report, state, LEFT_TRIGGER_OFFSET, TRIGGER_BLOCK_SIZE)
    }

    private fun mergeLeds(report: ByteArray) {
        val flag1 = report[VALID_FLAG1_INDEX].toInt() and 0xFF
        val flag2 = report[VALID_FLAG2_INDEX].toInt() and 0xFF
        val hasLedFlags =
            (flag1 and (
                    VALID_FLAG1_MIC_MUTE_LED_CONTROL_ENABLE or
                            VALID_FLAG1_LIGHTBAR_CONTROL_ENABLE or
                            VALID_FLAG1_PLAYER_INDICATOR_CONTROL_ENABLE
                    )) != 0

        if (!hasLedFlags) return

        val isMusicRumbleWake =
            (flag1 == MUSIC_RUMBLE_FLAG1) &&
                    ((report[VALID_FLAG2_INDEX].toInt() and 0xFF) == MUSIC_RUMBLE_VALID_FLAG2) &&
                    ((report[LIGHTBAR_SETUP_INDEX].toInt() and 0xFF) == MUSIC_RUMBLE_SETUP)

        if (isMusicRumbleWake) {
            state[VALID_FLAG1_INDEX] = MUSIC_RUMBLE_FLAG1.toByte()
            state[VALID_FLAG2_INDEX] =
                (state[VALID_FLAG2_INDEX].toInt() or MUSIC_RUMBLE_VALID_FLAG2).toByte()
            state[LIGHTBAR_SETUP_INDEX] = MUSIC_RUMBLE_SETUP.toByte()
            state[MIC_LED_INDEX] = report[MIC_LED_INDEX]
            state[LED_BRIGHTNESS_INDEX] = report[LED_BRIGHTNESS_INDEX]
            if (report[PLAYER_LEDS_INDEX] != 0.toByte()) {
                state[PLAYER_LEDS_INDEX] = report[PLAYER_LEDS_INDEX]
            }
            if (report[LIGHTBAR_RED_INDEX] != 0.toByte() ||
                report[LIGHTBAR_GREEN_INDEX] != 0.toByte() ||
                report[LIGHTBAR_BLUE_INDEX] != 0.toByte()
            ) {
                state[LIGHTBAR_RED_INDEX] = report[LIGHTBAR_RED_INDEX]
                state[LIGHTBAR_GREEN_INDEX] = report[LIGHTBAR_GREEN_INDEX]
                state[LIGHTBAR_BLUE_INDEX] = report[LIGHTBAR_BLUE_INDEX]
            }
            return
        }

        state[VALID_FLAG1_INDEX] = (state[VALID_FLAG1_INDEX].toInt() or flag1).toByte()
        state[VALID_FLAG2_INDEX] = (state[VALID_FLAG2_INDEX].toInt() or flag2).toByte()
        state[MIC_LED_INDEX] = report[MIC_LED_INDEX]
        state[LIGHTBAR_SETUP_INDEX] = report[LIGHTBAR_SETUP_INDEX]
        state[LED_BRIGHTNESS_INDEX] = report[LED_BRIGHTNESS_INDEX]
        state[PLAYER_LEDS_INDEX] = report[PLAYER_LEDS_INDEX]
        state[LIGHTBAR_RED_INDEX] = report[LIGHTBAR_RED_INDEX]
        state[LIGHTBAR_GREEN_INDEX] = report[LIGHTBAR_GREEN_INDEX]
        state[LIGHTBAR_BLUE_INDEX] = report[LIGHTBAR_BLUE_INDEX]
    }

    private fun keepMusicRumbleWakeAlive() {
        if (!musicRumbleRouteActive) return

        state[VALID_FLAG1_INDEX] = MUSIC_RUMBLE_FLAG1.toByte()
        state[VALID_FLAG2_INDEX] =
            (state[VALID_FLAG2_INDEX].toInt() or MUSIC_RUMBLE_VALID_FLAG2).toByte()
        state[LIGHTBAR_SETUP_INDEX] = MUSIC_RUMBLE_SETUP.toByte()
    }

    private fun copyRange(source: ByteArray, target: ByteArray, offset: Int, size: Int) {
        for (i in 0 until size) {
            target[offset + i] = source[offset + i]
        }
    }
}
