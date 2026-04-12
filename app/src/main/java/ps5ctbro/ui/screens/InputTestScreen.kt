package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.InputTestUiState
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.StickState
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.TriggerState

@Composable
fun InputTestScreen(
    uiState: InputTestUiState,
    onRefreshConnectionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.label_connection),
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = if (uiState.controllerConnected) {
                        stringResource(R.string.status_connected)
                    } else {
                        stringResource(R.string.status_no_active_connection)
                    },
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = uiState.logText,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = uiState.rawReportInfo,
                    style = MaterialTheme.typography.bodySmall
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRefreshConnectionClick) {
                        Text(stringResource(R.string.button_refresh_short))
                    }

                    TextButton(onClick = onRefreshConnectionClick) {
                        Text(stringResource(R.string.button_reopen))
                    }
                }
            }
        }

        StickCard(
            title = stringResource(R.string.label_left_stick),
            stick = uiState.leftStick
        )

        StickCard(
            title = stringResource(R.string.label_right_stick),
            stick = uiState.rightStick
        )

        TriggerCard(
            title = "L2",
            trigger = uiState.l2
        )

        TriggerCard(
            title = "R2",
            trigger = uiState.r2
        )

        PressedButtonsCard(
            buttons = uiState.pressedButtons
        )
    }
}

@Composable
private fun PressedButtonsCard(
    buttons: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.label_pressed_buttons),
                style = MaterialTheme.typography.titleLarge
            )

            if (buttons.isEmpty()) {
                Text(
                    text = stringResource(R.string.msg_no_buttons_pressed),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val rows = buttons.chunked(2)

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rows.forEach { rowButtons ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowButtons.forEach { button ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(button) },
                                    modifier = Modifier.widthIn(min = 120.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickCard(
    title: String,
    stick: StickState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )

            AxisLine(
                label = "X",
                rawValue = stick.rawX,
                percentValue = stick.percentX
            )

            HorizontalDivider()

            AxisLine(
                label = "Y",
                rawValue = stick.rawY,
                percentValue = stick.percentY
            )
        }
    }
}

@Composable
private fun AxisLine(
    label: String,
    rawValue: Int,
    percentValue: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$label: $percentValue%   (raw: $rawValue)",
            style = MaterialTheme.typography.bodyLarge
        )

        val progress = ((percentValue + 100f) / 200f).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TriggerCard(
    title: String,
    trigger: TriggerState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = "${trigger.percent}%   (raw: ${trigger.rawValue})",
                style = MaterialTheme.typography.bodyLarge
            )

            LinearProgressIndicator(
                progress = { (trigger.percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}