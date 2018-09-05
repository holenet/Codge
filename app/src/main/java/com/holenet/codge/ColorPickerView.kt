package com.holenet.codge

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

class ColorPickerView : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    companion object {
        const val WIDTH = 810
        const val HEIGHT = 955
        const val STROKE_WIDTH = 10f

        const val CIRCLE_PICKER_RADIUS = 40f
        const val CIRCLE_CENTER = WIDTH / 2f
        const val CIRCLE_RADIUS = 360f

        const val BAR_PICKER_WIDTH = 34f
        const val BAR_PICKER_HEIGHT = 125f
        const val BAR_X = STROKE_WIDTH / 2f + CIRCLE_PICKER_RADIUS
        const val BAR_Y = WIDTH + 10f + BAR_PICKER_HEIGHT / 2f + STROKE_WIDTH / 2f
        const val BAR_WIDTH = CIRCLE_RADIUS * 2
        const val BAR_HEIGHT = 100f
    }

    enum class ChangingMode {
        NONE, CIRCLE, BAR
    }

    private var changingMode = ChangingMode.NONE
    private var circlePickerX = 0f
    private var circlePickerY = 0f
    private var barPickerX = 1f

    private val hsvTemp = floatArrayOf(0f, 0f, 1f)
    private val hsv = floatArrayOf(0f, 0f, 1f)
    var color: Int
        get() = Color.HSVToColor(hsv)
        set(value) {
            Color.colorToHSV(value, hsv)
            hsvTemp[0] = hsv[0]
            hsvTemp[0] = hsv[1]
            invalidate()
        }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val colors = IntArray(360) {
            Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))
        }
        val positions = FloatArray(360) {
            it / 360f
        }
        val gradient = SweepGradient(0f, 0f, colors, positions)
        shader = gradient
    }
    private val circlePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val gradient = RadialGradient(0f, 0f, 1f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        shader = gradient
    }
    private val circlePaint3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val gradient = LinearGradient(0f, 0f, 1f, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        shader = gradient
    }
    private val circlePickerPaintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val circlePickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH / CIRCLE_RADIUS
        color = Color.WHITE
    }
    private val barPickerPaintInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barPickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH / BAR_WIDTH
        color = Color.WHITE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        when {
            widthMode == MeasureSpec.EXACTLY -> setMeasuredDimension(width, width * HEIGHT / WIDTH)
            heightMode == MeasureSpec.EXACTLY -> setMeasuredDimension(height * WIDTH / HEIGHT, height)
            widthMode == MeasureSpec.AT_MOST -> setMeasuredDimension(width, width * HEIGHT / WIDTH)
            heightMode == MeasureSpec.AT_MOST -> setMeasuredDimension(height * WIDTH / HEIGHT, height)
            else -> setMeasuredDimension(WIDTH, HEIGHT)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.apply {
            // preset
            scale(width.toFloat() / WIDTH, width.toFloat() / WIDTH)

            // draw circle
            save()
            translate(CIRCLE_CENTER, CIRCLE_CENTER)
            scale(CIRCLE_RADIUS, CIRCLE_RADIUS)
            drawCircle(0f, 0f, 1f, circlePaint)
            drawCircle(0f ,0f, 1f, circlePaint2)
            drawCircle(0f, 0f, 1f, circlePaint3.apply { alpha = (255 * (1 - hsv[2])).roundToInt() })
            // picker
            drawCircle(circlePickerX, circlePickerY, CIRCLE_PICKER_RADIUS / CIRCLE_RADIUS, circlePickerPaintInner.apply { color = this@ColorPickerView.color })
            drawCircle(circlePickerX, circlePickerY, CIRCLE_PICKER_RADIUS / CIRCLE_RADIUS, circlePickerPaint)
            restore()

            // draw bar
            save()
            translate(BAR_X, BAR_Y)
            scale(BAR_WIDTH, BAR_WIDTH)
            val barHalfHeight = BAR_HEIGHT / 2f / BAR_WIDTH
            drawRoundRect(0f, -barHalfHeight, 1f, barHalfHeight, barHalfHeight, barHalfHeight, barPaint.apply { color = Color.HSVToColor(hsvTemp) })
            drawRoundRect(0f, -barHalfHeight, 1f, barHalfHeight, barHalfHeight, barHalfHeight, barPaint2)
            // picker
            translate(barPickerX, 0f)
            val barPickerHalfWidth = BAR_PICKER_WIDTH / 2f / BAR_WIDTH
            val barPickerHalfHeight = BAR_PICKER_HEIGHT / 2f / BAR_WIDTH
            drawRoundRect(-barPickerHalfWidth, -barPickerHalfHeight, barPickerHalfWidth, barPickerHalfHeight, barPickerHalfWidth, barPickerHalfWidth, barPickerPaintInner.apply { color = this@ColorPickerView.color })
            drawRoundRect(-barPickerHalfWidth, -barPickerHalfHeight, barPickerHalfWidth, barPickerHalfHeight, barPickerHalfWidth, barPickerHalfWidth, barPickerPaint)
            restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x * WIDTH / width
        val y = event.y * WIDTH / width

        if (event.action == MotionEvent.ACTION_DOWN) {
            if (hypot(x - CIRCLE_CENTER, y - CIRCLE_CENTER) < CIRCLE_RADIUS) {
                changingMode = ChangingMode.CIRCLE
            } else if (BAR_X < x && x < BAR_X + BAR_WIDTH && BAR_Y - BAR_HEIGHT / 2f < y && y < BAR_Y + BAR_HEIGHT / 2f) {
                changingMode = ChangingMode.BAR
            }
        }

        when (changingMode) {
            ChangingMode.CIRCLE -> {
                circlePickerX = (x - CIRCLE_CENTER) / CIRCLE_RADIUS
                circlePickerY = (y - CIRCLE_CENTER) / CIRCLE_RADIUS
                val length = hypot(circlePickerX, circlePickerY)
                if (length > 1) {
                    circlePickerX /= length
                    circlePickerY /= length
                }
                hsv[0] = (atan2(circlePickerY, circlePickerX) * 180 / PI).toFloat()
                if (hsv[0] < 0) hsv[0] += 360f
                hsv[1] = length
                hsvTemp[0] = hsv[0]
                hsvTemp[1] = hsv[1]
                invalidate()
            }
            ChangingMode.BAR -> {
                barPickerX = (x - BAR_X) / BAR_WIDTH
                if (barPickerX < 0)
                    barPickerX = 0f
                else if (barPickerX > 1)
                    barPickerX = 1f
                hsv[2] = barPickerX
                invalidate()
            }
        }

        if (event.action == MotionEvent.ACTION_UP) {
            changingMode = ChangingMode.NONE
        }

        return true
    }
}