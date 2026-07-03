package com.nanda.ai.trading.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.core.content.ContextCompat
import com.nanda.ai.trading.R

class FloatingBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var ballColor = Color.parseColor("#00E5A0")
    private var glowColor = Color.parseColor("#00E5A0")
    private var borderColor = Color.parseColor("#FFFFFF")
    private var textColor = Color.WHITE

    private var ballRadius = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var priceIndicator = "---"
    private var isBullish = true
    private var pulsePhase = 0f

    private var isHidden = false

    init {
        // Initialize paints
        paint.apply {
            style = Paint.Style.FILL
            color = ballColor
        }

        glowPaint.apply {
            style = Paint.Style.FILL
            color = glowColor
            alpha = 60
        }

        borderPaint.apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = 4f
            alpha = 180
        }

        textPaint.apply {
            color = textColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // Set default size
        val size = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 72f, resources.displayMetrics
        ).toInt()
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 72f, resources.displayMetrics
        ).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        ballRadius = (w.coerceAtMost(h) / 2f) - 6f
        textPaint.textSize = ballRadius * 0.35f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw glow effect (pulsing)
        val glowRadius = ballRadius + 12f + (Math.sin(pulsePhase.toDouble()) * 6f).toFloat()
        canvas.drawCircle(centerX, centerY, glowRadius, glowPaint)

        // Draw outer border
        canvas.drawCircle(centerX, centerY, ballRadius + 2f, borderPaint)

        // Draw main ball with gradient
        val gradient = RadialGradient(
            centerX - ballRadius * 0.3f,
            centerY - ballRadius * 0.3f,
            ballRadius * 1.2f,
            if (isBullish) Color.parseColor("#00E5A0") else Color.parseColor("#FF4757"),
            if (isBullish) Color.parseColor("#008B5C") else Color.parseColor("#CC0000"),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawCircle(centerX, centerY, ballRadius, paint)
        paint.shader = null

        // Draw "AI" text
        canvas.drawText("AI", centerX, centerY - textPaint.textSize * 0.2f, textPaint)

        // Draw price indicator
        val smallTextPaint = Paint(textPaint).apply {
            textSize = textPaint.textSize * 0.55f
        }
        canvas.drawText(priceIndicator, centerX, centerY + textPaint.textSize * 0.9f, smallTextPaint)
    }

    fun updatePriceIndicator(price: Double) {
        priceIndicator = when {
            price >= 10000 -> String.format("%.0f", price)
            price >= 1000 -> String.format("%.1f", price)
            price >= 100 -> String.format("%.2f", price)
            else -> String.format("%.4f", price)
        }
        invalidate()
    }

    fun setBullish(bullish: Boolean) {
        isBullish = bullish
        ballColor = if (bullish) Color.parseColor("#00E5A0") else Color.parseColor("#FF4757")
        glowColor = ballColor
        paint.color = ballColor
        glowPaint.color = glowColor
        invalidate()
    }

    fun startPulseAnimation() {
        post(object : Runnable {
            override fun run() {
                pulsePhase += 0.08f
                glowPaint.alpha = (40 + (Math.sin(pulsePhase.toDouble()) * 30).toInt()).coerceIn(20, 100)
                invalidate()
                if (!isHidden) {
                    postDelayed(this, 50)
                }
            }
        })
    }

    fun hide() {
        isHidden = true
        visibility = GONE
    }

    fun show() {
        isHidden = false
        visibility = VISIBLE
        startPulseAnimation()
        val anim = ScaleAnimation(
            0f, 1f, 0f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 200
        }
        startAnimation(anim)
    }

    fun showErrorState() {
        ballColor = Color.parseColor("#FF4757")
        glowColor = ballColor
        paint.color = ballColor
        glowPaint.color = glowColor
        invalidate()
    }
}
