package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSliderRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.AppSwitchRow
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.SmallActionButton
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRowModern
import com.DueBoysenberry1226.ps5ctbro.ui.controllertest.ControllerTestStickState
import com.DueBoysenberry1226.ps5ctbro.ui.controllertest.ControllerTestUiState

@Composable
fun ControllerTestScreen(
    uiState: ControllerTestUiState,
    onRefreshConnectionClick: () -> Unit,
    onTestRunningChanged: (Boolean) -> Unit,
    onLightEnabledChanged: (Boolean) -> Unit,
    onRumbleEnabledChanged: (Boolean) -> Unit,
    onMicLedEnabledChanged: (Boolean) -> Unit,
    onPlayerLedEnabledChanged: (Int, Boolean) -> Unit,
    onRedChanged: (Int) -> Unit,
    onGreenChanged: (Int) -> Unit,
    onBlueChanged: (Int) -> Unit,
    onLeftRumbleChanged: (Int) -> Unit,
    onRightRumbleChanged: (Int) -> Unit,
    onSendIntervalChanged: (Int) -> Unit,
    showLogs: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(
            uiState = uiState,
            onRefreshConnectionClick = onRefreshConnectionClick
        )

        SectionCard(title = "Egyesitett report loop") {
            AppSwitchRow(
                label = "Teszt futtatasa",
                checked = uiState.testRunning,
                onCheckedChange = onTestRunningChanged,
                subLabel = "Ugyanaz a report viszi a nativ rezgest, lightbart, player LED-et es mic LED-et."
            )

            Spacer(modifier = Modifier.height(8.dp))

            AppSliderRow(
                label = "Report kuldesi idokoz",
                value = uiState.sendIntervalMs.toFloat(),
                onValueChange = { onSendIntervalChanged(it.toInt()) },
                valueRange = 8f..250f,
                valueDisplay = "${uiState.sendIntervalMs} ms"
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Text(
                text = "Kimeneti reportok: ${uiState.outputReportsSent}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Kimeneti hibak: ${uiState.outputErrors}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.outputErrors > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        SectionCard(title = "Folyamatos vilagitas") {
            AppSwitchRow(
                label = "Lightbar es player LED-ek",
                checked = uiState.lightEnabled,
                onCheckedChange = onLightEnabledChanged
            )

            Spacer(modifier = Modifier.height(10.dp))

            ColorPreview(uiState)

            Spacer(modifier = Modifier.height(12.dp))

            AppSliderRow(
                label = "Piros",
                value = uiState.red.toFloat(),
                onValueChange = { onRedChanged(it.toInt()) },
                valueRange = 0f..255f,
                valueDisplay = uiState.red.toString()
            )
            AppSliderRow(
                label = "Zold",
                value = uiState.green.toFloat(),
                onValueChange = { onGreenChanged(it.toInt()) },
                valueRange = 0f..255f,
                valueDisplay = uiState.green.toString()
            )
            AppSliderRow(
                label = "Kek",
                value = uiState.blue.toFloat(),
                onValueChange = { onBlueChanged(it.toInt()) },
                valueRange = 0f..255f,
                valueDisplay = uiState.blue.toString()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Player LED-ek",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (index in 0 until 5) {
                    val enabled = (uiState.playerLedMask and (1 shl index)) != 0
                    FilterChip(
                        selected = enabled,
                        onClick = { onPlayerLedEnabledChanged(index, !enabled) },
                        label = { Text("${index + 1}") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AppSwitchRow(
                label = "Mic LED",
                checked = uiState.micLedEnabled,
                onCheckedChange = onMicLedEnabledChanged
            )
        }

        SectionCard(title = "Nativ report rezges") {
            AppSwitchRow(
                label = "Folyamatos rezges",
                checked = uiState.rumbleEnabled,
                onCheckedChange = onRumbleEnabledChanged,
                subLabel = "Nem audio/ISO stream alapu, hanem HID output report motor byte."
            )

            Spacer(modifier = Modifier.height(8.dp))

            AppSliderRow(
                label = "Bal motor",
                value = uiState.leftRumblePercent.toFloat(),
                onValueChange = { onLeftRumbleChanged(it.toInt()) }
            )
            AppSliderRow(
                label = "Jobb motor",
                value = uiState.rightRumblePercent.toFloat(),
                onValueChange = { onRightRumbleChanged(it.toInt()) }
            )
        }

        SectionCard(title = "Input kozben") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricPill("Input report", uiState.inputReportsRead.toString(), Modifier.weight(1f))
                MetricPill(
                    "Utolso input",
                    uiState.lastInputAgeMs?.let { "${it} ms" } ?: "-",
                    Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            StickRow("Bal stick", uiState.leftStick)
            StickRow("Jobb stick", uiState.rightStick)

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricPill("L2", "${uiState.l2Percent}%", Modifier.weight(1f))
                MetricPill("R2", "${uiState.r2Percent}%", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (uiState.pressedButtons.isEmpty()) {
                    "Lenyomott gomb: nincs"
                } else {
                    "Lenyomott gombok: ${uiState.pressedButtons.joinToString(", ")}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.rawReportInfo.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.rawReportInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showLogs && uiState.logText.isNotEmpty()) {
            SectionCard(title = "Log") {
                Text(
                    text = uiState.logText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    uiState: ControllerTestUiState,
    onRefreshConnectionClick: () -> Unit
) {
    SectionCard(title = "Allapot") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF102345),
                border = BorderStroke(1.dp, Color(0xFF2B4C7E))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    StatusRowModern(
                        icon = Icons.Outlined.SportsEsports,
                        title = "Kontroller",
                        value = if (uiState.controllerConnected) "USB HID aktiv" else "Nincs kapcsolat",
                        isOnline = uiState.controllerConnected,
                        onClick = onRefreshConnectionClick
                    )
                }
            }

            SmallActionButton(
                text = "Frissit",
                icon = Icons.Outlined.Refresh,
                onClick = onRefreshConnectionClick,
                modifier = Modifier.height(56.dp)
            )
        }
    }
}

@Composable
private fun ColorPreview(uiState: ControllerTestUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(
                color = if (uiState.lightEnabled) {
                    Color(uiState.red, uiState.green, uiState.blue)
                } else {
                    Color.Black
                },
                shape = RoundedCornerShape(16.dp)
            )
    )
}

@Composable
private fun StickRow(label: String, stick: ControllerTestStickState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricPill("$label X", "${stick.xPercent}%", Modifier.weight(1f))
        MetricPill("$label Y", "${stick.yPercent}%", Modifier.weight(1f))
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF102345),
        border = BorderStroke(1.dp, Color(0xFF2B4C7E))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF7FB0FF),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
