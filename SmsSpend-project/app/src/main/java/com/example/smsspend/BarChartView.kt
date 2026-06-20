package com.example.smsspend

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class BarChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Bar(val label: String, val value: Float)

    private val PAD_L = 16f; private val PAD_R = 16f
    private val PAD_T = 16f; private val PAD_B = 40f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00C896.toInt(); style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8B949E.toInt(); textSize = 24f; textAlign = Paint.Align.CENTER
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = 22f; textAlign = Paint.Align.CENTER
    }

    var bars: List<Bar> = emptyList()
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bars.isEmpty()) return

        val cW = width - PAD_L - PAD_R
        val cH = height - PAD_T - PAD_B
        val maxVal = bars.maxOfOrNull { it.value } ?: 1f
        val barW = cW / bars.size * 0.6f
        val gap = cW / bars.size

        bars.forEachIndexed { i, bar ->
            val x = PAD_L + gap * i + gap / 2f
            val barH = if (maxVal > 0) (bar.value / maxVal) * cH else 0f
            val top = PAD_T + cH - barH
            barPaint.color = if (i % 2 == 0) 0xFF00C896.toInt() else 0xFF0D9970.toInt()
            canvas.drawRect(x - barW / 2, top, x + barW / 2, PAD_T + cH, barPaint)
            canvas.drawText(bar.label, x, PAD_T + cH + 28, labelPaint)
            if (bar.value > 0 && barH > 24) {
                val lbl = if (bar.value >= 1000) "%.0fK".format(bar.value / 1000) else "%.0f".format(bar.value)
                canvas.drawText(lbl, x, top - 4, valuePaint)
            }
        }
    }
}
