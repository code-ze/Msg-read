package com.example.smsspend

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Slice(val label: String, val value: Float, val color: Int)

    private val COLORS = intArrayOf(
        0xFF00C896.toInt(), 0xFFFF4B6E.toInt(), 0xFFA78BFA.toInt(), 0xFFFBBF24.toInt(),
        0xFF38BDF8.toInt(), 0xFFF97316.toInt(), 0xFF4ADE80.toInt(), 0xFFE879F9.toInt(),
        0xFF6366F1.toInt(), 0xFFEC4899.toInt(), 0xFF14B8A6.toInt(), 0xFFF43F5E.toInt(),
        0xFF8B5CF6.toInt(), 0xFF22D3EE.toInt()
    )

    var slices: List<Slice> = emptyList()
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0D1117.toInt(); style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8B949E.toInt(); textSize = 28f; textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) return

        val size = min(width, height).toFloat()
        val cx = width / 2f; val cy = height / 2f
        val radius = size / 2f - 8f

        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        var startAngle = -90f

        slices.forEachIndexed { i, slice ->
            paint.color = COLORS[i % COLORS.size]
            val sweep = if (total > 0) (slice.value / total) * 360f else 0f
            canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
                startAngle, sweep, true, paint)
            startAngle += sweep
        }

        // Donut hole
        canvas.drawCircle(cx, cy, radius * 0.55f, holePaint)

        // Center label
        val topCat = slices.maxByOrNull { it.value }
        if (topCat != null) {
            canvas.drawText("Top", cx, cy - 8, textPaint)
            val pct = if (total > 0) (topCat.value / total * 100).toInt() else 0
            canvas.drawText("$pct%", cx, cy + 24, Paint(textPaint).apply {
                color = 0xFFFFFFFF.toInt(); textSize = 34f
            })
        }
    }

    companion object {
        val CHART_COLORS = intArrayOf(
            0xFF00C896.toInt(), 0xFFFF4B6E.toInt(), 0xFFA78BFA.toInt(), 0xFFFBBF24.toInt(),
            0xFF38BDF8.toInt(), 0xFFF97316.toInt(), 0xFF4ADE80.toInt(), 0xFFE879F9.toInt(),
            0xFF6366F1.toInt(), 0xFFEC4899.toInt(), 0xFF14B8A6.toInt(), 0xFFF43F5E.toInt(),
            0xFF8B5CF6.toInt(), 0xFF22D3EE.toInt()
        )
    }
}
