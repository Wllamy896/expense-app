package com.example.expensemanager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

val DONUT_COLORS = intArrayOf(
    Color.parseColor("#12B76A"),
    Color.parseColor("#2563EB"),
    Color.parseColor("#F59E0B"),
    Color.parseColor("#EF4444"),
    Color.parseColor("#8B5CF6"),
    Color.parseColor("#06B6D4"),
    Color.parseColor("#EC4899"),
    Color.parseColor("#64748B")
)

fun compactNum(v: Double): String {
    val s = when {
        v >= 1_000_000 -> String.format(Locale.US, "%.1fM", v / 1_000_000)
        v >= 1_000 -> String.format(Locale.US, "%.1fK", v / 1_000)
        else -> String.format(Locale.US, "%.0f", v)
    }
    return s.replace(".0", "")
}

private fun niceCeil(v: Double): Double {
    if (v <= 0.0) return 100.0
    val e = 10.0.pow(floor(log10(v)))
    val f = v / e
    val nf = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return nf * e
}

class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var labels: List<String> = emptyList()
    private var income: List<Double> = emptyList()
    private var expense: List<Double> = emptyList()
    private val d = resources.displayMetrics.density

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5E7EB")
        strokeWidth = d
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = 10f * d
        textAlign = Paint.Align.RIGHT
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = 10f * d
        textAlign = Paint.Align.CENTER
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9CA3AF")
        textSize = 13f * d
        textAlign = Paint.Align.CENTER
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun setData(labels: List<String>, income: List<Double>, expense: List<Double>) {
        this.labels = labels
        this.income = income
        this.expense = expense
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = labels.size
        val maxV = max(income.maxOrNull() ?: 0.0, expense.maxOrNull() ?: 0.0)
        if (n == 0 || maxV <= 0.0) {
            canvas.drawText("لا توجد بيانات", width / 2f, height / 2f, emptyPaint)
            return
        }
        val niceMax = niceCeil(maxV)
        val left = 44f * d
        val right = width - 24f * d
        val top = 10f * d
        val bottom = height - 26f * d

        for (i in 0..4) {
            val y = bottom - (bottom - top) * i / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(compactNum(niceMax * i / 4.0), left - 6f * d, y + 4f * d, axisPaint)
        }
        for (i in 0 until n) {
            val x = if (n == 1) (left + right) / 2f else left + (right - left) * i / (n - 1)
            canvas.drawText(labels[i], x, height - 6f * d, labelPaint)
        }

        drawSeries(canvas, expense, Color.parseColor("#EF4444"), false, left, right, top, bottom, niceMax)
        drawSeries(canvas, income, Color.parseColor("#12B76A"), true, left, right, top, bottom, niceMax)
    }

    private fun drawSeries(
        canvas: Canvas, vs: List<Double>, color: Int, fill: Boolean,
        left: Float, right: Float, top: Float, bottom: Float, niceMax: Double
    ) {
        val n = vs.size
        if (n == 0) return
        fun xOf(i: Int): Float =
            if (n == 1) (left + right) / 2f else left + (right - left) * i / (n - 1)

        fun yOf(v: Double): Float = bottom - (bottom - top) * (v / niceMax).toFloat()

        val path = Path()
        path.moveTo(xOf(0), yOf(vs[0]))
        for (i in 1 until n) {
            val px = xOf(i - 1)
            val py = yOf(vs[i - 1])
            val x = xOf(i)
            val y = yOf(vs[i])
            val mx = px + (x - px) / 2f
            path.cubicTo(mx, py, mx, y, x, y)
        }
        if (fill) {
            val fp = Path(path)
            fp.lineTo(xOf(n - 1), bottom)
            fp.lineTo(xOf(0), bottom)
            fp.close()
            fillPaint.shader = LinearGradient(
                0f, top, 0f, bottom,
                Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(fp, fillPaint)
        }
        linePaint.color = color
        canvas.drawPath(path, linePaint)
        dotPaint.color = color
        for (i in 0 until n) {
            canvas.drawCircle(xOf(i), yOf(vs[i]), 3.5f * d, dotPaint)
        }
    }
}

class DonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Double> = emptyList()
    private var centerTop = ""
    private var centerBottom = ""
    private val d = resources.displayMetrics.density

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#14284B")
        textSize = 17f * d
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B7280")
        textSize = 11f * d
        textAlign = Paint.Align.CENTER
    }

    fun colorAt(i: Int): Int = DONUT_COLORS[i % DONUT_COLORS.size]

    fun setData(values: List<Double>, top: String, bottom: String) {
        this.values = values
        this.centerTop = top
        this.centerBottom = bottom
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val stroke = size * 0.2f
        ring.strokeWidth = stroke
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f - stroke / 2f - 2f * d
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        val total = values.sum()

        if (total <= 0.0) {
            ring.color = Color.parseColor("#E5E7EB")
            canvas.drawArc(rect, 0f, 360f, false, ring)
        } else {
            var start = -90f
            values.forEachIndexed { i, v ->
                val sweep = (v / total * 360.0).toFloat()
                val gap = if (values.size > 1) 2f else 0f
                ring.color = colorAt(i)
                canvas.drawArc(rect, start + gap / 2f, max(sweep - gap, 0.5f), false, ring)
                start += sweep
            }
        }
        canvas.drawText(centerTop, cx, cy + 4f * d, topPaint)
        canvas.drawText(centerBottom, cx, cy + 20f * d, subPaint)
    }
}
