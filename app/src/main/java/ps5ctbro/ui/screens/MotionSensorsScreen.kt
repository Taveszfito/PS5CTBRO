package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRow
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.InputTestUiState
import androidx.compose.ui.draw.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

@Composable
fun MotionSensorsScreen(
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

        GyroCard(
            gyro = uiState.gyro,
            accel = uiState.accel
        )
    }
}

@Composable
private fun GyroCard(
    gyro: com.DueBoysenberry1226.ps5ctbro.ui.inputtest.GyroState,
    accel: com.DueBoysenberry1226.ps5ctbro.ui.inputtest.AccelState
) {
    SectionCard(title = stringResource(R.string.section_motion_sensors)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val roll = atan2(accel.x.toFloat(), accel.z.toFloat()) * (180f / PI.toFloat())

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
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

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
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
                    Icon(
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
