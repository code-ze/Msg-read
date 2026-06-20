package com.example.smsspend.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One wedge of a donut / one series in a chart. */
data class ChartSlice(val label: String, val value: Double, val color: Color)

/**
 * A donut chart drawn with Canvas (no chart library / dependency). Wedges are sized by value;
 * [center] renders whatever you want in the hole (e.g. the total).
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 24.dp,
    gapDegrees: Float = 2.5f,
    center: @Composable () -> Unit = {}
) {
    val total = slices.sumOf { it.value }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (total <= 0.0) return@Canvas
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            var start = -90f
            slices.forEach { s ->
                val sweep = (360.0 * (s.value / total)).toFloat()
                if (sweep > 0f) {
                    drawArc(
                        color = s.color,
                        startAngle = start + gapDegrees / 2f,
                        sweepAngle = (sweep - gapDegrees).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

/** A minimal line chart for a value series (e.g. balance over time). */
@Composable
fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val pad = 4.dp.toPx()
        val h = size.height - pad * 2
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = pad + (h - (v - min) / range * h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}
