package com.DueBoysenberry1226.ps5ctbro.ui

import androidx.annotation.StringRes
import com.DueBoysenberry1226.ps5ctbro.R

enum class AppSection(
    @param:StringRes val titleRes: Int? = null,
    val titleOverride: String? = null
) {
    SPEAKER(titleRes = R.string.section_speaker),
    ADAPTIVE_TRIGGERS(titleRes = R.string.section_adaptive_triggers),
    LEDS(titleRes = R.string.section_leds),
    INPUT_TEST(titleRes = R.string.section_input_test),
    MOTION_SENSORS(titleRes = R.string.section_motion_sensors),
    MIC_TEST(titleRes = R.string.section_mic_test),
    VIBRATE_TEST(titleRes = R.string.section_vibrate_test),
    CONTROLLER_TEST(titleOverride = "Kontroller teszt"),
    BYTE_TEST(titleRes = R.string.section_byte_test),
    SETTINGS(titleRes = R.string.section_settings)
}
