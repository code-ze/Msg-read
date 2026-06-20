package com.example.smsspend

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BalanceChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Point(val timestamp: Long, val balance: Double)

    private val PAD_L = 72f
    private val PAD_R = 16f
    private val PAD_T = 16f
    private val PAD_B = 48f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00C896.toInt(); strokeWidth = 3f
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1AFFFFFF; strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8B949E.toInt(); textSize = 26f
    }
    private val dipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF4B6E.toInt(); style = Paint.Style.FILL
    }
    private val selLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt(); strokeWidth = 1.5f; style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }
    private val selDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00C896.toInt(); style = Paint.Style.FILL
    }
    private val selDotRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x4400C896; style = Paint.Style.FILL
    }

    var dataPoints: List<Point> = emptyList()
        set(value) { field = value; selectedIndex = -1; updateShader(); invalidate() }

    var dipIndices: Set<Int> = emptySet()

    var onPointSelected: ((Point) -> Unit)? = null

    private var selectedIndex = -1

    private fun cL() = PAD_L
    private fun cR() = width - PAD_R
    private fun cT() = PAD_T
    private fun cB() = height - PAD_B
    private fun cW() = cR() - cL()
    private fun cH() = cB() - cT()

    private fun minTs() = dataPoints.minOfOrNull { it.timestamp } ?: 0L
    private fun maxTs() = dataPoints.maxOfOrNull { it.timestamp } ?: 1L
    private fun minBal(): Double {
        val m = dataPoints.minOfOrNull { it.balance } ?: 0.0
        return min(m, 0.0)
    }
    private fun maxBal(): Double {
        val m = dataPoints.maxOfOrNull { it.balance } ?: 1.0
        return max(m, m * 1.05)
    }

    private fun tsToX(ts: Long): Float {
        val range = maxTs() - minTs()
        if (range == 0L) return cL()
        return cL() + ((ts - minTs()).toFloat() / range) * cW()
    }

    private fun balToY(bal: Double): Float {
        val range = maxBal() - minBal()
        if (range == 0.0) return cT() + cH() / 2
        val pct = (bal - minBal()) / range
        return cB() - (pct * cH()).toFloat()
    }

    private fun xToNearestIndex(x: Float): Int {
        if (dataPoints.isEmpty()) return -1
        var best = 0; var bestDist = Float.MAX_VALUE
        for (i in dataPoints.indices) {
            val d = abs(tsToX(dataPoints[i].timestamp) - x)
            if (d < bestDist) { bestDist = d; best = i }
        }
        return best
    }

    private fun updateShader() {
        if (height == 0) return
        fillPaint.shader = LinearGradient(
            0f, cT(), 0f, cB(),
            intArrayOf(0x3300C896, 0x0000C896), null, Shader.TileMode.CLAMP
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.size < 2) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("No data", width / 2f, height / 2f, labelPaint)
            labelPaint.textAlign = Paint.Align.LEFT
            return
        }

        val minBal = minBal(); val maxBal = maxBal()

        // Grid lines + Y labels
        labelPaint.textAlign = Paint.Align.RIGHT
        for (i in 0..4) {
            val y = cT() + cH() * i / 4
            canvas.drawLine(cL(), y, cR(), y, gridPaint)
            val val_ = maxBal - (maxBal - minBal) * i / 4
            val lbl = if (abs(val_) >= 1000) "%.0fK".format(val_ / 1000) else "%.0f".format(val_)
            canvas.drawText(lbl, cL() - 6, y + 9, labelPaint)
        }

        // Zero line
        if (minBal < 0) {
            val y0 = balToY(0.0)
            val zp = Paint(gridPaint).apply { color = 0x44FFFFFF }
            canvas.drawLine(cL(), y0, cR(), y0, zp)
        }

        // Line + fill paths
        val lp = Path(); val fp = Path()
        dataPoints.forEachIndexed { i, pt ->
            val x = tsToX(pt.timestamp); val y = balToY(pt.balance)
            if (i == 0) { lp.moveTo(x, y); fp.moveTo(x, cB()); fp.lineTo(x, y) }
            else { lp.lineTo(x, y); fp.lineTo(x, y) }
        }
        fp.lineTo(tsToX(dataPoints.last().timestamp), cB()); fp.close()
        canvas.drawPath(fp, fillPaint)
        canvas.drawPath(lp, linePaint)

        // Dip markers
        for (idx in dipIndices) {
            if (idx < dataPoints.size) {
                val x = tsToX(dataPoints[idx].timestamp)
                val y = balToY(dataPoints[idx].balance)
                canvas.drawCircle(x, y, 5f, dipPaint)
            }
        }

        // X axis date labels
        labelPaint.textAlign = Paint.Align.CENTER
        val df = SimpleDateFormat("MMM yy", Locale.US)
        val n = min(5, dataPoints.size)
        for (i in 0 until n) {
            val idx = if (n == 1) 0 else i * (dataPoints.size - 1) / (n - 1)
            val x = tsToX(dataPoints[idx].timestamp)
            canvas.drawText(df.format(Date(dataPoints[idx].timestamp)), x, cB() + 36, labelPaint)
        }

        // Selected point
        if (selectedIndex in dataPoints.indices) {
            val x = tsToX(dataPoints[selectedIndex].timestamp)
            val y = balToY(dataPoints[selectedIndex].balance)
            canvas.drawLine(x, cT(), x, cB(), selLinePaint)
            canvas.drawCircle(x, y, 18f, selDotRingPaint)
            canvas.drawCircle(x, y, 7f, selDotPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dataPoints.isEmpty()) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val idx = xToNearestIndex(event.x)
                if (idx != selectedIndex) {
                    selectedIndex = idx; invalidate()
                    onPointSelected?.invoke(dataPoints[idx])
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
