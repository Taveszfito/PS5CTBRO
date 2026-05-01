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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.DueBoysenberry1226.ps5ctbro.ui.theme.BlueBright
import com.DueBoysenberry1226.ps5ctbro.ui.theme.CyanAxis
import com.DueBoysenberry1226.ps5ctbro.ui.theme.GreenAxis
import com.DueBoysenberry1226.ps5ctbro.ui.theme.PanelStroke
import com.DueBoysenberry1226.ps5ctbro.ui.theme.RedAxis
import com.DueBoysenberry1226.ps5ctbro.ui.theme.SuccessGreen
import com.DueBoysenberry1226.ps5ctbro.ui.theme.TextMutedDark
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val HISTORY_SIZE = 180
private const val MAX_GRAPH_ANGLE = 180f
private const val YAW_RATE_SCALE = 8f

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

        if (lastSampleNanos != 0L) {
            val dt = ((now - lastSampleNanos) / 1_000_000_000f).coerceIn(0f, 0.12f)
            integratedYaw = normalizeAngle(
                integratedYaw - ((uiState.gyro.z / YAW_RATE_SCALE) * dt)
            )
        }
        lastSampleNanos = now

        if (baselineRoll.isNaN()) {
            baselineRoll = rawRoll
            baselinePitch = rawPitch
            baselineYaw = integratedYaw
        }

        val relativeRoll = -shortestAngleDelta(rawRoll, baselineRoll)
        val relativePitch = shortestAngleDelta(rawPitch, baselinePitch)
        val relativeYaw = integratedYaw - baselineYaw

        appendHistory(xRateHistory, uiState.gyro.x / 16f)
        appendHistory(yRateHistory, uiState.gyro.y / 16f)
        appendHistory(zRateHistory, uiState.gyro.z / 16f)
        appendHistory(rollHistory, relativeRoll)
        appendHistory(pitchHistory, relativePitch)
        appendHistory(yawHistory, relativeYaw)
    }

    val orientationState = MotionOrientationState(
        roll = if (baselineRoll.isNaN()) 0f else -shortestAngleDelta(latestAbsRoll, baselineRoll),
        pitch = if (baselinePitch.isNaN()) 0f else shortestAngleDelta(latestAbsPitch, baselinePitch),
        yaw = integratedYaw - baselineYaw,
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
        rollHistory.clear()
        pitchHistory.clear()
        yawHistory.clear()
        appendHistory(rollHistory, 0f)
        appendHistory(pitchHistory, 0f)
        appendHistory(yawHistory, 0f)
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

        AxisSummarySection(
            orientation = orientationState,
            enabled = !isBtMode
        )

        RealtimeGraphCard(
            orientation = orientationState,
            enabled = !isBtMode
        )

        BottomDetailsRow(
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
                    text = "Valós idejű orientáció",
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
                MiniIconPill(
                    onClick = onResetReference
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Reset nézet",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                }
                MiniInfoPill(text = "60 Hz")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            BlueBright.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    1.dp,
                    PanelStroke.copy(alpha = 0.42f),
                    RoundedCornerShape(20.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f - 6f
                val radius = size.minDimension * 0.28f

                drawCircle(
                    color = BlueBright.copy(alpha = 0.08f),
                    radius = radius * 1.40f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.0.dp.toPx())
                )

                drawCircle(
                    color = BlueBright.copy(alpha = 0.14f),
                    radius = radius * 1.05f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.0.dp.toPx())
                )

                drawOval(
                    color = RedAxis.copy(alpha = 0.18f),
                    topLeft = Offset(cx - radius * 1.12f, cy - radius * 0.86f),
                    size = Size(radius * 2.24f, radius * 1.72f),
                    style = Stroke(width = 1.0.dp.toPx())
                )

                drawLine(
                    color = RedAxis.copy(alpha = 0.95f),
                    start = Offset(cx, cy + radius * 1.18f),
                    end = Offset(cx, cy - radius * 1.38f),
                    strokeWidth = 2.0.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = GreenAxis.copy(alpha = 0.95f),
                    start = Offset(cx - radius * 1.34f, cy),
                    end = Offset(cx + radius * 1.34f, cy),
                    strokeWidth = 2.0.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawOval(
                    color = CyanAxis.copy(alpha = 0.92f),
                    topLeft = Offset(cx - radius * 1.15f, cy - radius * 0.20f),
                    size = Size(radius * 2.30f, radius * 0.40f),
                    style = Stroke(width = 2.0.dp.toPx())
                )

                val controllerWidth = radius * 1.45f
                val controllerHeight = radius * 0.72f

                val controllerCenterX = cx + (orientation.pitch / 180f) * radius * 0.22f
                val controllerCenterY = cy + (orientation.roll / 180f) * radius * 0.18f

                val left = controllerCenterX - controllerWidth / 2f
                val top = controllerCenterY - controllerHeight / 2f + 2f

                withTransform({
                    rotate(degrees = orientation.yaw * 0.28f, pivot = Offset(controllerCenterX, controllerCenterY))
                }) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.07f),
                        topLeft = Offset(left, top),
                        size = Size(controllerWidth, controllerHeight),
                        cornerRadius = CornerRadius(34f, 34f)
                    )

                    drawRoundRect(
                        color = BlueBright.copy(alpha = 0.16f),
                        topLeft = Offset(
                            left + controllerWidth * 0.27f,
                            top + controllerHeight * 0.08f
                        ),
                        size = Size(controllerWidth * 0.46f, controllerHeight * 0.22f),
                        cornerRadius = CornerRadius(18f, 18f)
                    )

                    drawCircle(
                        color = Color.Black.copy(alpha = 0.42f),
                        radius = controllerHeight * 0.11f,
                        center = Offset(
                            left + controllerWidth * 0.36f,
                            top + controllerHeight * 0.70f
                        )
                    )

                    drawCircle(
                        color = Color.Black.copy(alpha = 0.42f),
                        radius = controllerHeight * 0.11f,
                        center = Offset(
                            left + controllerWidth * 0.64f,
                            top + controllerHeight * 0.70f
                        )
                    )
                }

                val pointerLength = radius * 1.00f
                val angleRad = Math.toRadians(orientation.yaw.toDouble()).toFloat()
                val endX = cx + cos(angleRad) * pointerLength
                val endY = cy + sin(angleRad) * pointerLength

                drawLine(
                    color = CyanAxis,
                    start = Offset(cx, cy),
                    end = Offset(endX, endY),
                    strokeWidth = 3.2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val arrowPath = Path().apply {
                    moveTo(endX, endY)
                    lineTo(endX - 18f, endY - 8f)
                    lineTo(endX - 12f, endY + 12f)
                    close()
                }
                drawPath(path = arrowPath, color = CyanAxis)
            }

            Text(
                text = "X",
                color = RedAxis,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            )

            Text(
                text = "Y",
                color = GreenAxis,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )

            Text(
                text = "Z",
                color = CyanAxis,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            ) {
                Text(
                    text = "Dőlés: ${orientation.roll.toInt()}°",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Pitch ${orientation.pitch.toInt()}° • Yaw ${orientation.yaw.toInt()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMutedDark,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
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
private fun AxisSummarySection(
    orientation: MotionOrientationState,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompactAxisMetricCard(
            modifier = Modifier.weight(1f),
            title = "X tengely",
            value = formatAxisValue(orientation.gyroXRate),
            axisColor = RedAxis,
            history = orientation.xRateHistory,
            enabled = enabled
        )

        CompactAxisMetricCard(
            modifier = Modifier.weight(1f),
            title = "Y tengely",
            value = formatAxisValue(orientation.gyroYRate),
            axisColor = GreenAxis,
            history = orientation.yRateHistory,
            enabled = enabled
        )

        CompactAxisMetricCard(
            modifier = Modifier.weight(1f),
            title = "Z tengely",
            value = formatAxisValue(orientation.gyroZRate),
            axisColor = CyanAxis,
            history = orientation.zRateHistory,
            enabled = enabled
        )
    }
}

@Composable
private fun CompactAxisMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    axisColor: Color,
    history: List<Float>,
    enabled: Boolean
) {
    GlassPanel(
        modifier = modifier.aspectRatio(1f),
        contentPadding = 10,
        enabled = enabled
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = axisColor,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp)
        )

        Text(
            text = "Fordulatszám",
            style = MaterialTheme.typography.labelMedium,
            color = TextMutedDark,
            modifier = Modifier.padding(top = 2.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(30.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = axisColor.copy(alpha = 0.18f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.dp.toPx()
                )
                drawHistoryLine(
                    values = history,
                    color = axisColor,
                    minValue = -80f,
                    maxValue = 80f
                )
            }
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
                text = "Valós idejű grafikon",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Időtartam: 10 mp",
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
                    values = orientation.rollHistory,
                    color = RedAxis,
                    minValue = -MAX_GRAPH_ANGLE,
                    maxValue = MAX_GRAPH_ANGLE,
                    contentLeft = left,
                    contentTop = top,
                    contentRight = right,
                    contentBottom = bottom
                )
                drawHistoryLine(
                    values = orientation.pitchHistory,
                    color = GreenAxis,
                    minValue = -MAX_GRAPH_ANGLE,
                    maxValue = MAX_GRAPH_ANGLE,
                    contentLeft = left,
                    contentTop = top,
                    contentRight = right,
                    contentBottom = bottom
                )
                drawHistoryLine(
                    values = orientation.yawHistory,
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
            LegendDot("X tengely", RedAxis)
            Box(modifier = Modifier.width(16.dp))
            LegendDot("Y tengely", GreenAxis)
            Box(modifier = Modifier.width(16.dp))
            LegendDot("Z tengely", CyanAxis)
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
private fun BottomDetailsRow(
    orientation: MotionOrientationState,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        OrientationDetailsCard(
            modifier = Modifier.weight(1f),
            orientation = orientation,
            enabled = enabled
        )

        ControllerViewCard(
            modifier = Modifier.weight(1f),
            orientation = orientation,
            enabled = enabled
        )
    }
}

@Composable
private fun OrientationDetailsCard(
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
            text = "Orientáció (Eulerek szögek)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EulerBar(label = "Dőlés (X)", value = orientation.roll, color = RedAxis)
            EulerBar(label = "Bólintás (Y)", value = orientation.pitch, color = GreenAxis)
            EulerBar(label = "Irány (Z)", value = orientation.yaw, color = CyanAxis)
        }

        AxisAngleSummaryRow(
            roll = orientation.roll,
            pitch = orientation.pitch,
            yaw = orientation.yaw,
            modifier = Modifier.padding(top = 12.dp)
        )

        Text(
            text = "Gyorsulás nagyság: ${orientation.accelMagnitude.toInt()}",
            color = TextMutedDark,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun AxisAngleSummaryRow(
    roll: Float,
    pitch: Float,
    yaw: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AngleSummaryCell(
            modifier = Modifier.weight(1f),
            label = "Roll",
            value = roll,
            color = RedAxis
        )
        AngleSummaryCell(
            modifier = Modifier.weight(1f),
            label = "Pitch",
            value = pitch,
            color = GreenAxis
        )
        AngleSummaryCell(
            modifier = Modifier.weight(1f),
            label = "Yaw",
            value = yaw,
            color = CyanAxis
        )
    }
}

@Composable
private fun AngleSummaryCell(
    modifier: Modifier = Modifier,
    label: String,
    value: Float,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = String.format("%+.0f°", value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun EulerBar(
    label: String,
    value: Float,
    color: Color
) {
    val normalized = ((value + 180f) / 360f).coerceIn(0f, 1f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = String.format("%+.1f°", value),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalized)
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.35f),
                                color
                            )
                        )
                    )
            )
        }
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
            text = "Kontroller nézet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToggleChip(
                text = "3D nézet",
                selected = true,
                modifier = Modifier.weight(1f)
            )
            ToggleChip(
                text = "Fentről nézet",
                selected = false,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(190.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f))
                .border(
                    1.dp,
                    PanelStroke.copy(alpha = 0.34f),
                    RoundedCornerShape(18.dp)
                )
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f + 14f
                val radius = size.minDimension * 0.24f

                drawLine(
                    color = RedAxis.copy(alpha = 0.95f),
                    start = Offset(cx, cy + radius * 1.8f),
                    end = Offset(cx, cy - radius * 1.8f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = GreenAxis.copy(alpha = 0.95f),
                    start = Offset(cx - radius * 1.95f, cy),
                    end = Offset(cx + radius * 1.95f, cy),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val controllerWidth = radius * 2.7f
                val controllerHeight = radius * 1.45f

                val controllerCenterX = cx + (orientation.pitch / 180f) * radius * 0.18f
                val controllerCenterY = cy + (orientation.roll / 180f) * radius * 0.26f

                val left = controllerCenterX - controllerWidth / 2f
                val top = controllerCenterY - controllerHeight / 2f

                withTransform({
                    rotate(
                        degrees = orientation.yaw * 0.42f,
                        pivot = Offset(controllerCenterX, controllerCenterY)
                    )
                }) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.06f),
                        topLeft = Offset(left, top),
                        size = Size(controllerWidth, controllerHeight),
                        cornerRadius = CornerRadius(46f, 46f)
                    )

                    drawRoundRect(
                        color = BlueBright.copy(alpha = 0.14f),
                        topLeft = Offset(
                            left + controllerWidth * 0.29f,
                            top + controllerHeight * 0.06f
                        ),
                        size = Size(controllerWidth * 0.42f, controllerHeight * 0.23f),
                        cornerRadius = CornerRadius(16f, 16f)
                    )

                    drawCircle(
                        color = Color.Black.copy(alpha = 0.34f),
                        radius = controllerHeight * 0.12f,
                        center = Offset(
                            left + controllerWidth * 0.36f,
                            top + controllerHeight * 0.68f
                        )
                    )

                    drawCircle(
                        color = Color.Black.copy(alpha = 0.34f),
                        radius = controllerHeight * 0.12f,
                        center = Offset(
                            left + controllerWidth * 0.64f,
                            top + controllerHeight * 0.68f
                        )
                    )
                }

                drawCircle(
                    color = BlueBright,
                    radius = 5.dp.toPx(),
                    center = Offset(controllerCenterX, controllerCenterY)
                )
            }

            Text(
                text = "Y",
                color = GreenAxis,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
            )

            Text(
                text = "X",
                color = RedAxis,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )

            Text(
                text = "Z",
                color = CyanAxis,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
            )
        }
    }
}

@Composable
private fun ToggleChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                }
            )
            .border(
                1.dp,
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
                } else {
                    Color.Transparent
                },
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else TextMutedDark,
            fontWeight = FontWeight.SemiBold
        )
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
                    text = "Információ",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Mozgasd a kontrollert a giroszkóp adatok valós idejű megtekintéséhez.",
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
            text = "Debug",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = logText.ifBlank { "Nincs log." },
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
    values.forEachIndexed { index, value ->
        val normalizedY = 1f - ((value - minValue) / range).coerceIn(0f, 1f)
        val point = Offset(
            x = contentLeft + (stepX * index),
            y = contentTop + (height * normalizedY)
        )
        previous?.let {
            drawLine(
                color = color,
                start = it,
                end = point,
                strokeWidth = 2.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        previous = point
    }
}

private fun formatAxisValue(value: Float): String {
    return String.format("%+.2f °/s", value)
}
