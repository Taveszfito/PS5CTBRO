package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.PI
import kotlin.math.abs
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.audio.TouchpadPoint
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRow
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.InputTestUiState
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.StickState
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.TriggerState

@Composable
fun InputTestScreen(
    uiState: InputTestUiState,
    onRefreshConnectionClick: () -> Unit,
    showLogs: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(title = stringResource(R.string.label_connection)) {
            StatusRow(
                label = stringResource(R.string.label_controller),
                value = if (uiState.controllerConnected) {
                    stringResource(R.string.status_connected)
                } else {
                    stringResource(R.string.status_no_active_connection)
                }
            )

            if (showLogs) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = uiState.logText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.rawReportInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRefreshConnectionClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.button_refresh_short))
                }

                TextButton(
                    onClick = onRefreshConnectionClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.button_reopen))
                }
            }
        }

        TouchpadCard(
            touch1 = uiState.touch1,
            touch2 = uiState.touch2
        )

        GyroCard(
            gyro = uiState.gyro,
            accel = uiState.accel
        )

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
private fun GyroCard(
    gyro: com.DueBoysenberry1226.ps5ctbro.ui.inputtest.GyroState,
    accel: com.DueBoysenberry1226.ps5ctbro.ui.inputtest.AccelState
) {
    SectionCard(title = "Motion Sensors") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Horizont kijelző (Accel alapú)
            val roll = atan2(accel.x.toFloat(), accel.z.toFloat()) * (180f / PI.toFloat())
            val pitch = atan2(-accel.y.toFloat(), accel.z.toFloat()) * (180f / PI.toFloat())

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                // "Horizont" vonal
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    rotate(degrees = -roll, pivot = center) {
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.5f),
                            start = Offset(0f, size.height / 2),
                            end = Offset(size.width, size.height / 2),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Kontroller ikon/nyíl ami dől
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .rotate(-roll),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Dőlés: ${roll.toInt()}°",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Gyroscope (szögsebesség) adatok nyilakkal
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GyroDataLine(label = "X (Pitch)", value = gyro.x)
                GyroDataLine(label = "Y (Roll)", value = gyro.y)
                GyroDataLine(label = "Z (Yaw)", value = gyro.z)
            }
        }
    }
}

@Composable
private fun GyroDataLine(label: String, value: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (abs(value) > 100) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (value > 0) 0f else 180f),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        val progress = ((value + 2000f) / 4000f).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = if (abs(value) > 2000) Color.Red else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun TouchpadCard(
    touch1: TouchpadPoint,
    touch2: TouchpadPoint
) {
    SectionCard(title = "Touchpad") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1920f / 1080f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    )
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (touch1.isActive) {
                        val x = (touch1.x / 1919f) * size.width
                        val y = (touch1.y / 1079f) * size.height
                        drawCircle(
                            color = primaryColor,
                            radius = 12.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                    if (touch2.isActive) {
                        val x = (touch2.x / 1919f) * size.width
                        val y = (touch2.y / 1079f) * size.height
                        drawCircle(
                            color = secondaryColor,
                            radius = 12.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TouchpadPointInfo(label = "T1", point = touch1)
                TouchpadPointInfo(label = "T2", point = touch2)
            }
        }
    }
}

@Composable
private fun TouchpadPointInfo(
    label: String,
    point: TouchpadPoint
) {
    Column {
        Text(
            text = "$label: ${if (point.isActive) "Aktív" else "Inaktív"}",
            style = MaterialTheme.typography.labelLarge,
            color = if (point.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        if (point.isActive) {
            Text(
                text = "X: ${point.x}, Y: ${point.y}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PressedButtonsCard(
    buttons: List<String>
) {
    SectionCard(title = stringResource(R.string.label_pressed_buttons)) {
        if (buttons.isEmpty()) {
            Text(
                text = stringResource(R.string.msg_no_buttons_pressed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                modifier = Modifier.weight(1f),
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = MaterialTheme.colorScheme.primary
                                )
                            )
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
    SectionCard(title = title) {
        AxisLine(
            label = "X",
            rawValue = stick.rawX,
            percentValue = stick.percentX
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        AxisLine(
            label = "Y",
            rawValue = stick.rawY,
            percentValue = stick.percentY
        )
    }
}

@Composable
private fun AxisLine(
    label: String,
    rawValue: Int,
    percentValue: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$label: $percentValue%",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "raw: $rawValue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }

        val progress = ((percentValue + 100f) / 200f).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun TriggerCard(
    title: String,
    trigger: TriggerState
) {
    SectionCard(title = title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${trigger.percent}%",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "raw: ${trigger.rawValue}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }

        LinearProgressIndicator(
            progress = { (trigger.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}