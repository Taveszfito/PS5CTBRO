package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRow
import com.DueBoysenberry1226.ps5ctbro.ui.settings.SettingsUiState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onLanguageSelected: (String) -> Unit,
    onGainChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AudioSettingsCard(
            audioGain = uiState.audioGain,
            onGainChanged = onGainChanged
        )

        LanguageCard(
            currentLanguage = uiState.currentLanguage,
            onLanguageSelected = onLanguageSelected
        )

        AboutCard(
            version = uiState.appVersion
        )
    }
}

@Composable
private fun AudioSettingsCard(
    audioGain: Float,
    onGainChanged: (Float) -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_audio_settings)) {
        AppSliderRow(
            label = stringResource(R.string.label_audio_gain),
            value = audioGain,
            onValueChange = onGainChanged,
            valueRange = 0f..1.0f,
            steps = 20,
            valueDisplay = "${(audioGain * 100).roundToInt()}%"
        )
    }
}

@Composable
private fun LanguageCard(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_language)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentLanguage == "en",
                onClick = { onLanguageSelected("en") },
                label = { Text(stringResource(R.string.label_language_english)) },
                modifier = Modifier.weight(1f)
            )

            FilterChip(
                selected = currentLanguage == "hu",
                onClick = { onLanguageSelected("hu") },
                label = { Text(stringResource(R.string.label_language_hungarian)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AboutCard(version: String) {
    SectionCard(title = stringResource(R.string.card_title_about)) {
        StatusRow(
            label = stringResource(R.string.label_version),
            value = version
        )
    }
}