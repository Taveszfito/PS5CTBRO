package com.DueBoysenberry1226.ps5ctbro.ui

import androidx.annotation.StringRes
import com.DueBoysenberry1226.ps5ctbro.R

enum class AppSection(@StringRes val titleRes: Int) {
    SPEAKER(R.string.section_speaker),
    ADAPTIVE_TRIGGERS(R.string.section_adaptive_triggers),
    LEDS(R.string.section_leds),
    INPUT_TEST(R.string.section_input_test),
    MOTION_SENSORS(R.string.section_motion_sensors),
    VIBRATE_TEST(R.string.section_vibrate_test),
    SETTINGS(R.string.section_settings)
}