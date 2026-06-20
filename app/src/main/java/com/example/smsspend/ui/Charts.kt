package com.example.smsspend.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One wedge of a donut / one series in a chart. */
data class ChartSlice(val label: String, val value: Double, val color: Color)

/** Keeps the [topN] biggest slices and folds the rest into a single grey "Other" slice. */
fun groupTopSlices(slices: List<ChartSlice>, topN: Int = 5): List<ChartSlice> {
    if (slices.size <= topN) return slices
    val sorted = slices.sortedByDescending { it.value }
    val rest = sorted.drop(topN)
    return sorted.take(topN) + ChartSlice("Other", rest.sumOf { it.value }, otherSliceColor)
}

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

/**
 * A smooth single-line trend with a soft gradient fading to the baseline (e.g. balance over
 * time). Uses midpoint cubic smoothing so the line reads as a clean curve, not jagged bars.
 */
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
        val n = values.size
        val stepX = size.width / (n - 1)
        val pad = 6.dp.toPx()
        val h = size.height - pad * 2
        fun px(i: Int) = i * stepX
        fun py(i: Int) = pad + (h - (values[i] - min) / range * h)

        val line = Path().apply {
            moveTo(px(0), py(0))
            for (i in 1 until n) {
                val midX = (px(i - 1) + px(i)) / 2f
                cubicTo(midX, py(i - 1), midX, py(i), px(i), py(i))
            }
        }
        // Soft gradient fill under the curve.
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.0f))
            )
        )
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}
