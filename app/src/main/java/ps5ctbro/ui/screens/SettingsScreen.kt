package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.settings.SettingsUiState

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
        LanguageCard(
            currentLanguage = uiState.currentLanguage,
            onLanguageSelected = onLanguageSelected
        )

        GainCard(
            currentGain = uiState.audioGain,
            onGainChanged = onGainChanged
        )

        AboutCard(
            version = uiState.appVersion
        )
    }
}

@Composable
private fun LanguageCard(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    SettingsSectionCard(title = stringResource(R.string.card_title_language)) {
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
private fun GainCard(
    currentGain: Float,
    onGainChanged: (Float) -> Unit
) {
    SettingsSectionCard(title = stringResource(R.string.card_title_audio_gain)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.gain_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_gain_value, currentGain),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Slider(
                value = currentGain,
                onValueChange = onGainChanged,
                valueRange = 0f..1.5f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AboutCard(version: String) {
    SettingsSectionCard(title = stringResource(R.string.card_title_about)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_version),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            AssistChip(
                onClick = {},
                label = { Text(version) },
                colors = AssistChipDefaults.assistChipColors()
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}
