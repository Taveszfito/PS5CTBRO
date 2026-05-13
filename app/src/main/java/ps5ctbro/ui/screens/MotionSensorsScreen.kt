package com.DueBoysenberry1226.ps5ctbro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.DueBoysenberry1226.ps5ctbro.R
import com.DueBoysenberry1226.ps5ctbro.ui.components.GlassPanel
import com.DueBoysenberry1226.ps5ctbro.ui.components.MiniInfoPill
import com.DueBoysenberry1226.ps5ctbro.ui.components.SectionCard
import com.DueBoysenberry1226.ps5ctbro.ui.components.SmallActionButton
import com.DueBoysenberry1226.ps5ctbro.ui.components.StatusRowModern
import com.DueBoysenberry1226.ps5ctbro.ui.inputtest.InputTestUiState
import com.DueBoysenberry1226.ps5ctbro.ui.theme.CyanAxis
import com.DueBoysenberry1226.ps5ctbro.ui.theme.GreenAxis
import com.DueBoysenberry1226.ps5ctbro.ui.theme.PanelStroke
import com.DueBoysenberry1226.ps5ctbro.ui.theme.RedAxis
import com.DueBoysenberry1226.ps5ctbro.ui.theme.SuccessGreen
import com.DueBoysenberry1226.ps5ctbro.ui.theme.TextMutedDark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private const val HISTORY_SIZE = 180
private const val MAX_GRAPH_ANGLE = 180f
private const val YAW_RATE_SCALE = 16f
private const val YAW_DEAD_ZONE_DPS = 1.4f
private const val YAW_BIAS_LEARNING_SAMPLES = 90
private const val STATIONARY_GYRO_THRESHOLD_DPS = 2.2f
private const val STATIONARY_YAW_CORRECTION = 0.018f

data class MotionOrientationState(
    val roll: Float,
    val pitch: Float,
    val yaw: Float,
    val gyroXRate: Float,
    val gyroYRate: Float,
    val gyroZRate: Float,
    val accelMagnitude: Float,
    val xRateHistory: List<Float>,
    val yRateHistory: List<Float>,
    val zRateHistory: List<Float>,
    val rollHistory: List<Float>,
    val pitchHistory: List<Float>,
    val yawHistory: List<Float>
)

@Composable
fun MotionSensorsScreen(
    uiState: InputTestUiState,
    isBtMode: Boolean,
    onRefreshConnectionClick: () -> Unit,
    showLogs: Boolean = false
) {
    val xRateHistory = remember { mutableStateListOf<Float>() }
    val yRateHistory = remember { mutableStateListOf<Float>() }
    val zRateHistory = remember { mutableStateListOf<Float>() }
    val rollHistory = remember { mutableStateListOf<Float>() }
    val pitchHistory = remember { mutableStateListOf<Float>() }
    val yawHistory = remember { mutableStateListOf<Float>() }

    var latestAbsRoll by remember { mutableFloatStateOf(0f) }
    var latestAbsPitch by remember { mutableFloatStateOf(0f) }
    var integratedYaw by remember { mutableFloatStateOf(0f) }
    var baselineRoll by remember { mutableFloatStateOf(Float.NaN) }
    var baselinePitch by remember { mutableFloatStateOf(Float.NaN) }
    var baselineYaw by remember { mutableFloatStateOf(0f) }
    var yawBiasRate by remember { mutableFloatStateOf(0f) }
    var yawBiasSamples by remember { mutableLongStateOf(0L) }
    var lastSampleNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(
        uiState.gyro.x,
        uiState.gyro.y,
        uiState.gyro.z,
        uiState.accel.x,
        uiState.accel.y,
        uiState.accel.z,
        uiState.controllerConnected,
        isBtMode
    ) {
        if (isBtMode || !uiState.controllerConnected) {
            lastSampleNanos = 0L
            return@LaunchedEffect
        }

        val now = System.nanoTime()
        val ax = uiState.accel.x.toFloat()
        val ay = uiState.accel.y.toFloat()
        val az = uiState.accel.z.toFloat()

        val rawRoll = atan2(ay, az) * (180f / PI.toFloat())
        val rawPitch = atan2(-ax, sqrt(ay * ay + az * az)) * (180f / PI.toFloat())

        latestAbsRoll = rawRoll
        latestAbsPitch = rawPitch

        if (baselineRoll.isNaN()) {
            baselineRoll = rawRoll
            baselinePitch = rawPitch
            baselineYaw = integratedYaw
        }

        val measuredYawRate = -(uiState.gyro.y / YAW_RATE_SCALE)

        if (yawBiasSamples < YAW_BIAS_LEARNING_SAMPLES) {
            yawBiasRate = ((yawBiasRate * yawBiasSamples) + measuredYawRate) / (yawBiasSamples + 1L)
            yawBiasSamples += 1L
        }

        val correctedYawRate = measuredYawRate - yawBiasRate
        val yawRateForIntegration = if (abs(correctedYawRate) < YAW_DEAD_ZONE_DPS) {
            0f
        } else {
            correctedYawRate
        }

        if (lastSampleNanos != 0L) {
            val dt = ((now - lastSampleNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            integratedYaw = normalizeAngle(integratedYaw + (yawRateForIntegration * dt))

            val controllerLooksStill =
                abs(uiState.gyro.x / 16f) < STATIONARY_GYRO_THRESHOLD_DPS &&
                        abs(uiState.gyro.y / 16f) < STATIONARY_GYRO_THRESHOLD_DPS &&
                        abs(uiState.gyro.z / 16f) < STATIONARY_GYRO_THRESHOLD_DPS

            if (controllerLooksStill) {
                integratedYaw = normalizeAngle(
                    integratedYaw - shortestAngleDelta(integratedYaw, baselineYaw) * STATIONARY_YAW_CORRECTION
                )
            }
        }
        lastSampleNanos = now

        val relativePitch = -shortestAngleDelta(rawRoll, baselineRoll)
        val relativeRoll = shortestAngleDelta(rawPitch, baselinePitch)
        val relativeYaw = shortestAngleDelta(integratedYaw, baselineYaw)

        appendHistory(xRateHistory, uiState.gyro.x / 16f)
        appendHistory(yRateHistory, uiState.gyro.y / 16f)
        appendHistory(zRateHistory, uiState.gyro.z / 16f)
        appendHistory(rollHistory, relativeRoll)
        appendHistory(pitchHistory, relativePitch)
        appendHistory(yawHistory, relativeYaw)
    }

    val orientationState = MotionOrientationState(
        pitch = if (baselineRoll.isNaN()) 0f else -shortestAngleDelta(latestAbsRoll, baselineRoll),
        roll = if (baselinePitch.isNaN()) 0f else shortestAngleDelta(latestAbsPitch, baselinePitch),
        yaw = shortestAngleDelta(integratedYaw, baselineYaw),
        gyroXRate = uiState.gyro.x / 16f,
        gyroYRate = uiState.gyro.y / 16f,
        gyroZRate = uiState.gyro.z / 16f,
        accelMagnitude = sqrt(
            uiState.accel.x.toFloat() * uiState.accel.x.toFloat() +
                    uiState.accel.y.toFloat() * uiState.accel.y.toFloat() +
                    uiState.accel.z.toFloat() * uiState.accel.z.toFloat()
        ),
        xRateHistory = xRateHistory.toList(),
        yRateHistory = yRateHistory.toList(),
        zRateHistory = zRateHistory.toList(),
        rollHistory = rollHistory.toList(),
        pitchHistory = pitchHistory.toList(),
        yawHistory = yawHistory.toList()
    )

    val onResetReference = {
        baselineRoll = latestAbsRoll
        baselinePitch = latestAbsPitch
        baselineYaw = integratedYaw
        yawBiasRate = 0f
        yawBiasSamples = 0L
        rollHistory.clear()
        pitchHistory.clear()
        yawHistory.clear()
        appendHistory(rollHistory, 0f)
        appendHistory(pitchHistory, 0f)
        appendHistory(yawHistory, 0f)
    }

    LaunchedEffect(Unit) {
        onResetReference()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MotionStatusCard(
            controllerConnected = uiState.controllerConnected && !isBtMode,
            onRefreshConnectionClick = onRefreshConnectionClick,
            onResetReference = onResetReference
        )

        OrientationVisualizerCard(
            orientation = orientationState,
            enabled = !isBtMode,
            onResetReference = onResetReference
        )

        RealtimeGraphCard(
            orientation = orientationState,
            enabled = !isBtMode
        )

        ControllerViewCard(
            modifier = Modifier.fillMaxWidth(),
            orientation = orientationState,
            enabled = !isBtMode
        )

        InfoCard()

        if (showLogs) {
            DebugLogsCard(
                logText = uiState.logText,
                rawReportInfo = uiState.rawReportInfo
            )
        }
    }
}

@Composable
private fun MotionStatusCard(
    controllerConnected: Boolean,
    onRefreshConnectionClick: () -> Unit,
    onResetReference: () -> Unit
) {
    SectionCard(title = stringResource(R.string.card_title_status)) {
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
                        title = stringResource(R.string.label_controller),
                        value = if (controllerConnected) {
                            stringResource(R.string.status_connected)
                        } else {
                            stringResource(R.string.status_disconnected)
                        },
                        isOnline = controllerConnected,
                        onClick = onRefreshConnectionClick
                    )
                }
            }

            SmallActionButton(
                text = stringResource(R.string.button_refresh),
                icon = Icons.Outlined.Refresh,
                onClick = onRefreshConnectionClick,
                modifier = Modifier.height(56.dp)
            )
        }
    }
}

@Composable
private fun OrientationVisualizerCard(
    orientation: MotionOrientationState,
    enabled: Boolean,
    onResetReference: () -> Unit
) {
    GlassPanel(contentPadding = 14, enabled = enabled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen)
                )

                Text(
                    text = stringResource(R.string.label_realtime_orientation),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniIconPill(onClick = onResetReference) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = stringResource(R.string.content_desc_reset_view),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                }
                MiniInfoPill(text = stringResource(R.string.label_2_5d))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TopDownYawPanel(
                yaw = orientation.yaw,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TiltMeterPanel(
                    modifier = Modifier
                        .weight(1f)
                        .height(132.dp),
                    label = stringResource(R.string.label_x),
                    title = stringResource(R.string.label_pitch),
                    value = orientation.pitch,
                    color = RedAxis,
                    verticalMode = true
                )

                TiltMeterPanel(
                    modifier = Modifier
                        .weight(1f)
                        .height(132.dp),
                    label = stringResource(R.string.label_z),
                    title = stringResource(R.string.label_roll),
                    value = orientation.roll,
                    color = CyanAxis,
                    verticalMode = false
                )
            }
        }
    }
}

@Composable
private fun TopDownYawPanel(
    yaw: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f))
            .border(
                1.dp,
                PanelStroke.copy(alpha = 0.38f),
                RoundedCornerShape(20.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f + 4f
            val radius = size.minDimension * 0.34f

            drawCircle(
                color = GreenAxis.copy(alpha = 0.08f),
                radius = radius * 1.18f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = GreenAxis.copy(alpha = 0.14f),
                radius = radius * 0.82f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )

            drawLine(
                color = PanelStroke.copy(alpha = 0.38f),
                start = Offset(cx - radius * 1.28f, cy),
                end = Offset(cx + radius * 1.28f, cy),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = PanelStroke.copy(alpha = 0.38f),
                start = Offset(cx, cy - radius * 1.28f),
                end = Offset(cx, cy + radius * 1.28f),
                strokeWidth = 1.dp.toPx()
            )

            val controllerWidth = radius * 1.92f
            val controllerHeight = radius * 0.92f
            val left = cx - controllerWidth / 2f
            val top = cy - controllerHeight / 2f

            withTransform({ rotate(degrees = yaw, pivot = Offset(cx, cy)) }) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.075f),
                    topLeft = Offset(left, top),
                    size = Size(controllerWidth, controllerHeight),
                    cornerRadius = CornerRadius(40f, 40f)
                )
                drawRoundRect(
                    color = GreenAxis.copy(alpha = 0.18f),
                    topLeft = Offset(left + controllerWidth * 0.34f, top + controllerHeight * 0.08f),
                    size = Size(controllerWidth * 0.32f, controllerHeight * 0.18f),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.38f),
                    radius = controllerHeight * 0.12f,
                    center = Offset(left + controllerWidth * 0.34f, top + controllerHeight * 0.66f)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.38f),
                    radius = controllerHeight * 0.12f,
                    center = Offset(left + controllerWidth * 0.66f, top + controllerHeight * 0.66f)
                )

                drawLine(
                    color = GreenAxis,
                    start = Offset(cx, cy),
                    end = Offset(cx, top - radius * 0.24f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Text(
            text = stringResource(R.string.label_yaw_top_down),
            color = GreenAxis,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        )

        Text(
            text = stringResource(R.string.label_degrees_format, yaw),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp)
        )
    }
}

@Composable
private fun TiltMeterPanel(
    modifier: Modifier = Modifier,
    label: String,
    title: String,
    value: Float,
    color: Color,
    verticalMode: Boolean
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f))
            .border(
                1.dp,
                PanelStroke.copy(alpha = 0.34f),
                RoundedCornerShape(18.dp)
            )
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val usableWidth = size.width
            val usableHeight = size.height
            val center = Offset(usableWidth / 2f, usableHeight / 2f + 8f)
            val clamped = value.coerceIn(-90f, 90f)
            val normalized = clamped / 90f

            drawRoundRect(
                color = color.copy(alpha = 0.08f),
                topLeft = Offset(0f, 26f),
                size = Size(usableWidth, usableHeight - 32f),
                cornerRadius = CornerRadius(18f, 18f)
            )

            if (verticalMode) {
                drawLine(
                    color = PanelStroke.copy(alpha = 0.42f),
                    start = Offset(center.x, 34f),
                    end = Offset(center.x, usableHeight - 8f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(center.x, center.y - normalized * usableHeight * 0.34f),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            } else {
                drawLine(
                    color = PanelStroke.copy(alpha = 0.42f),
                    start = Offset(12f, center.y),
                    end = Offset(usableWidth - 12f, center.y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(center.x + normalized * usableWidth * 0.38f, center.y),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = color,
                radius = 5.dp.toPx(),
                center = if (verticalMode) {
                    Offset(center.x, center.y - normalized * usableHeight * 0.34f)
                } else {
                    Offset(center.x + normalized * usableWidth * 0.38f, center.y)
                }
            )
        }

        Text(
            text = stringResource(R.string.label_combination_format, label, title),
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Text(
            text = stringResource(R.string.label_degrees_format, value),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

@Composable
private fun MiniIconPill(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .border(
                1.dp,
                PanelStroke.copy(alpha = 0.34f),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Transparent)
        ) {
            content()
        }
    }
}

@Composable
private fun RealtimeGraphCard(
    orientation: MotionOrientationState,
    enabled: Boolean
) {
    GlassPanel(contentPadding = 14, enabled = enabled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_realtime_graph),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.label_graph_duration),
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedDark
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(185.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
                .border(
                    1.dp,
                    PanelStroke.copy(alpha = 0.34f),
                    RoundedCornerShape(18.dp)
                )
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                val w = size.width
                val h = size.height
                val left = 28f
                val bottom = h - 16f
                val top = 12f
                val right = w - 8f

                for (i in 0..4) {
                    val y = top + ((bottom - top) / 4f) * i
                    drawLine(
                        color = PanelStroke.copy(alpha = 0.30f),
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 1f
                    )
                }

                for (i in 0..5) {
                    val x = left + ((right - left) / 5f) * i
                    drawLine(
                        color = PanelStroke.copy(alpha = 0.24f),
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = 1f
                    )
                }

                drawHistoryLine(
                    values = orientation.pitchHistory,
                    color = RedAxis,
                    minValue = -MAX_GRAPH_ANGLE,
                    maxValue = MAX_GRAPH_ANGLE,
                    contentLeft = left,
                    contentTop = top,
                    contentRight = right,
                    contentBottom = bottom
                )
                drawHistoryLine(
                    values = orientation.yawHistory,
                    color = GreenAxis,
                    minValue = -MAX_GRAPH_ANGLE,
                    maxValue = MAX_GRAPH_ANGLE,
                    contentLeft = left,
                    contentTop = top,
                    contentRight = right,
                    contentBottom = bottom
                )
                drawHistoryLine(
                    values = orientation.rollHistory,
                    color = CyanAxis,
                    minValue = -MAX_GRAPH_ANGLE,
                    maxValue = MAX_GRAPH_ANGLE,
                    contentLeft = left,
                    contentTop = top,
                    contentRight = right,
                    contentBottom = bottom
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            LegendDot(stringResource(R.string.label_axis_x), RedAxis)
            Box(modifier = Modifier.width(16.dp))
            LegendDot(stringResource(R.string.label_axis_y), GreenAxis)
            Box(modifier = Modifier.width(16.dp))
            LegendDot(stringResource(R.string.label_axis_z), CyanAxis)
        }
    }
}

@Composable
private fun LegendDot(
    label: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMutedDark,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun ControllerViewCard(
    modifier: Modifier = Modifier,
    orientation: MotionOrientationState,
    enabled: Boolean
) {
    GlassPanel(
        modifier = modifier,
        contentPadding = 14,
        enabled = enabled
    ) {
        Text(
            text = stringResource(R.string.label_controller_view),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = stringResource(R.string.label_2_5d_split_view),
            style = MaterialTheme.typography.bodySmall,
            color = TextMutedDark,
            modifier = Modifier.padding(top = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiniAxisPreview(
                label = stringResource(R.string.label_y),
                title = stringResource(R.string.label_yaw),
                value = orientation.yaw,
                color = GreenAxis
            )
            MiniAxisPreview(
                label = stringResource(R.string.label_x),
                title = stringResource(R.string.label_pitch),
                value = orientation.pitch,
                color = RedAxis
            )
            MiniAxisPreview(
                label = stringResource(R.string.label_z),
                title = stringResource(R.string.label_roll),
                value = orientation.roll,
                color = CyanAxis
            )
        }
    }
}

@Composable
private fun MiniAxisPreview(
    label: String,
    title: String,
    value: Float,
    color: Color
) {
    val normalized = ((value.coerceIn(-90f, 90f) + 90f) / 180f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_combination_format, label, title),
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.label_degrees_format, value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalized)
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color.copy(alpha = 0.78f))
            )
        }
    }
}

@Composable
private fun InfoCard() {
    GlassPanel(contentPadding = 14) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier.padding(start = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.label_information),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = stringResource(R.string.msg_gyro_instruction),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedDark,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DebugLogsCard(
    logText: String,
    rawReportInfo: String
) {
    GlassPanel(contentPadding = 14) {
        Text(
            text = stringResource(R.string.label_debug),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = logText.ifBlank { stringResource(R.string.msg_no_logs) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp)
        )

        if (rawReportInfo.isNotBlank()) {
            Text(
                text = rawReportInfo,
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedDark,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun appendHistory(target: MutableList<Float>, value: Float) {
    target.add(value)
    while (target.size > HISTORY_SIZE) {
        target.removeAt(0)
    }
}

private fun normalizeAngle(value: Float): Float {
    var normalized = value
    while (normalized > 180f) normalized -= 360f
    while (normalized < -180f) normalized += 360f
    return normalized
}

private fun shortestAngleDelta(current: Float, baseline: Float): Float {
    var delta = current - baseline
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHistoryLine(
    values: List<Float>,
    color: Color,
    minValue: Float,
    maxValue: Float,
    contentLeft: Float = 0f,
    contentTop: Float = 0f,
    contentRight: Float = size.width,
    contentBottom: Float = size.height
) {
    if (values.size < 2) return

    val width = contentRight - contentLeft
    val height = contentBottom - contentTop
    val range = (maxValue - minValue).coerceAtLeast(0.001f)
    val stepX = width / (values.lastIndex.coerceAtLeast(1))

    var previous: Offset? = null
    var previousValue: Float? = null

    values.forEachIndexed { index, value ->
        val normalizedY = 1f - ((value - minValue) / range).coerceIn(0f, 1f)
        val point = Offset(
            x = contentLeft + (stepX * index),
            y = contentTop + (height * normalizedY)
        )

        val shouldBreakLine = previousValue?.let { old ->
            abs(value - old) > 180f
        } ?: false

        if (!shouldBreakLine) {
            previous?.let {
                drawLine(
                    color = color,
                    start = it,
                    end = point,
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        previous = point
        previousValue = value
    }
}