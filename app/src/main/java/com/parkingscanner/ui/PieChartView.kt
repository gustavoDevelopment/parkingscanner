package com.parkingscanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val oval = RectF()

    private var countEfectivo = 0
    private var countQr = 0
    private var countAnulado = 0

    fun setData(efectivo: Int, qr: Int, anulado: Int) {
        countEfectivo = efectivo
        countQr = qr
        countAnulado = anulado
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = (countEfectivo + countQr + countAnulado).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy) - 4f
        oval.set(cx - r, cy - r, cx + r, cy + r)

        if (total == 0f) {
            paint.color = Color.parseColor("#EEEEEE")
            canvas.drawCircle(cx, cy, r, paint)
            return
        }

        textPaint.textSize = r * 0.28f

        var startAngle = -90f

        fun drawSlice(count: Int, colorHex: String) {
            if (count <= 0) return
            val sweep = 360f * count / total
            paint.color = Color.parseColor(colorHex)
            canvas.drawArc(oval, startAngle, sweep, true, paint)

            // Draw percentage label if slice is large enough
            val pct = (count / total * 100).toInt()
            if (pct >= 8) {
                val midAngle = Math.toRadians((startAngle + sweep / 2).toDouble())
                val labelR = r * 0.62f
                val tx = cx + labelR * cos(midAngle).toFloat()
                val ty = cy + labelR * sin(midAngle).toFloat() + textPaint.textSize * 0.35f
                canvas.drawText("$pct%", tx, ty, textPaint)
            }

            startAngle += sweep
        }

        drawSlice(countEfectivo, "#4CAF50")
        drawSlice(countQr, "#2196F3")
        drawSlice(countAnulado, "#FF5722")
    }
}
