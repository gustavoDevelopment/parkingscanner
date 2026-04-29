package com.parkingscanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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

        var startAngle = -90f

        fun drawSlice(count: Int, colorHex: String) {
            if (count <= 0) return
            val sweep = 360f * count / total
            paint.color = Color.parseColor(colorHex)
            canvas.drawArc(oval, startAngle, sweep, true, paint)
            startAngle += sweep
        }

        drawSlice(countEfectivo, "#4CAF50")
        drawSlice(countQr, "#2196F3")
        drawSlice(countAnulado, "#FF5722")
    }
}
