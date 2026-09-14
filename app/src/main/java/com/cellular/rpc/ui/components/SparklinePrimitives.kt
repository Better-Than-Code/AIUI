package com.cellular.rpc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.SignalGreen

/**
 * Epic 14 & FEAT-07 Universal SDUI Primitive:
 * Standalone, lightweight vector Sparkline and Mini Bar chart rendered via Jetpack Compose Canvas
 * with interactive touch scrubber inspection.
 */
@Composable
fun SparklineCanvas(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    secondaryDataPoints: List<Float>? = null,
    lineColor: Color = CyanPrimary,
    secondaryLineColor: Color = MaterialTheme.colorScheme.tertiary,
    fillGradient: Boolean = true,
    heightDp: Dp = 56.dp,
    enableScrubber: Boolean = true
) {
    var activeTouchX by remember { mutableStateOf<Float?>(null) }
    var activeSelectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (enableScrubber && activeSelectedIndex != null && activeSelectedIndex!! in dataPoints.indices) {
            val selectedVal = dataPoints[activeSelectedIndex!!]
            val secVal = if (secondaryDataPoints != null && activeSelectedIndex!! in secondaryDataPoints.indices) {
                secondaryDataPoints[activeSelectedIndex!!]
            } else null

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "POINT #${activeSelectedIndex!! + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = String.format("%.2f", selectedVal),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = lineColor
                    )
                )
                if (secVal != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "vs ${String.format("%.2f", secVal)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryLineColor
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .then(
                    if (enableScrubber && dataPoints.size >= 2) {
                        Modifier.pointerInput(dataPoints, secondaryDataPoints) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    activeTouchX = offset.x
                                    val index = ((offset.x / size.width) * (dataPoints.size - 1))
                                        .toInt()
                                        .coerceIn(0, dataPoints.size - 1)
                                    activeSelectedIndex = index
                                },
                                onDragEnd = {
                                    activeTouchX = null
                                    activeSelectedIndex = null
                                },
                                onDragCancel = {
                                    activeTouchX = null
                                    activeSelectedIndex = null
                                },
                                onDrag = { change, _ ->
                                    activeTouchX = change.position.x
                                    val index = ((change.position.x / size.width) * (dataPoints.size - 1))
                                        .toInt()
                                        .coerceIn(0, dataPoints.size - 1)
                                    activeSelectedIndex = index
                                }
                            )
                        }.pointerInput(dataPoints, secondaryDataPoints) {
                            detectTapGestures(
                                onPress = { offset ->
                                    activeTouchX = offset.x
                                    val index = ((offset.x / size.width) * (dataPoints.size - 1))
                                        .toInt()
                                        .coerceIn(0, dataPoints.size - 1)
                                    activeSelectedIndex = index
                                    tryAwaitRelease()
                                    activeTouchX = null
                                    activeSelectedIndex = null
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            if (dataPoints.size >= 2) {
                Canvas(modifier = Modifier.fillMaxWidth().height(heightDp)) {
                    val allPoints = if (secondaryDataPoints != null) dataPoints + secondaryDataPoints else dataPoints
                    val min = allPoints.minOrNull() ?: 0f
                    val max = allPoints.maxOrNull() ?: 1f
                    val range = if (max - min == 0f) 1f else max - min

                    // Draw secondary comparative series in dashed stroke
                    if (secondaryDataPoints != null && secondaryDataPoints.size >= 2) {
                        val secStrokePath = Path()
                        secondaryDataPoints.forEachIndexed { index, value ->
                            val x = (index.toFloat() / (secondaryDataPoints.size - 1)) * size.width
                            val y = size.height - ((value - min) / range) * (size.height - 8f) - 4f
                            if (index == 0) {
                                secStrokePath.moveTo(x, y)
                            } else {
                                secStrokePath.lineTo(x, y)
                            }
                        }
                        drawPath(
                            path = secStrokePath,
                            color = secondaryLineColor.copy(alpha = 0.85f),
                            style = Stroke(
                                width = 1.75.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            )
                        )
                    }

                    val strokePath = Path()
                    val fillPath = Path()
                    val pointOffsets = mutableListOf<Offset>()

                    dataPoints.forEachIndexed { index, value ->
                        val x = (index.toFloat() / (dataPoints.size - 1)) * size.width
                        val y = size.height - ((value - min) / range) * (size.height - 8f) - 4f
                        val offset = Offset(x, y)
                        pointOffsets.add(offset)

                        if (index == 0) {
                            strokePath.moveTo(x, y)
                            fillPath.moveTo(x, size.height)
                            fillPath.lineTo(x, y)
                        } else {
                            strokePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(size.width, size.height)
                    fillPath.close()

                    if (fillGradient) {
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                                startY = 0f,
                                endY = size.height
                            )
                        )
                    }

                    drawPath(
                        path = strokePath,
                        color = lineColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw interactive scrubber guideline and indicator node
                    activeSelectedIndex?.let { idx ->
                        if (idx in pointOffsets.indices) {
                            val activePt = pointOffsets[idx]
                            // Vertical dashed guideline
                            drawLine(
                                color = lineColor.copy(alpha = 0.6f),
                                start = Offset(activePt.x, 0f),
                                end = Offset(activePt.x, size.height),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                            // Outer glow circle
                            drawCircle(
                                color = lineColor.copy(alpha = 0.35f),
                                radius = 7.dp.toPx(),
                                center = activePt
                            )
                            // Inner solid anchor circle
                            drawCircle(
                                color = lineColor,
                                radius = 4.dp.toPx(),
                                center = activePt
                            )

                            // Secondary indicator anchor if present
                            if (secondaryDataPoints != null && idx in secondaryDataPoints.indices) {
                                val secY = size.height - ((secondaryDataPoints[idx] - min) / range) * (size.height - 8f) - 4f
                                val secPt = Offset(activePt.x, secY)
                                drawCircle(
                                    color = secondaryLineColor,
                                    radius = 3.5.dp.toPx(),
                                    center = secPt
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
fun MiniBarChartCanvas(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = SignalGreen,
    heightDp: Dp = 48.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (dataPoints.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxWidth().height(heightDp)) {
                val min = 0f
                val max = (dataPoints.maxOrNull() ?: 1f).coerceAtLeast(0.1f)
                val totalBars = dataPoints.size
                val barWidth = (size.width / totalBars) * 0.65f
                val barGap = (size.width / totalBars) * 0.35f

                dataPoints.forEachIndexed { index, value ->
                    val x = index * (barWidth + barGap) + barGap / 2f
                    val barHeight = ((value - min) / max) * size.height
                    val y = size.height - barHeight

                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                    )
                }
            }
        }
    }
}
