package com.nanda.ai.trading.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.*
import androidx.core.content.ContextCompat
import com.nanda.ai.trading.R
import com.nanda.ai.trading.ai.AnalysisResult
import com.nanda.ai.trading.trading.TradeResult

class ControlPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var tvTitle: TextView
    private lateinit var tvPrice: TextView
    private lateinit var tvSignal: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var tvAnalysis: TextView
    private lateinit var btnAnalyze: Button
    private lateinit var btnBuy: Button
    private lateinit var btnSell: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var llActions: LinearLayout
    private lateinit var llAnalysis: LinearLayout
    private lateinit var scrollAnalysis: ScrollView
    private lateinit var tvError: TextView

    private var onCloseListener: (() -> Unit)? = null
    private var onAnalyzeListener: (() -> Unit)? = null
    private var onTradeListener: ((String) -> Unit)? = null
    private var onSettingsListener: (() -> Unit)? = null

    init {
        initView()
    }

    private fun initView() {
        // Set background
        val background = GradientDrawable().apply {
            setColor(Color.parseColor("#1A1A2E"))
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics
            )
            setStroke(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt(),
                Color.parseColor("#00E5A0")
            )
        }
        setBackgroundDrawable(background)
        setPadding(24, 24, 24, 24)

        // Create layout
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        // Header
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        tvTitle = TextView(context).apply {
            text = "NANDA AI Trading"
            setTextColor(Color.parseColor("#00E5A0"))
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnSettings = ImageButton(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.ic_menu_preferences))
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(80, 80)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        btnClose = ImageButton(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.ic_menu_close_clear_cancel))
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(80, 80)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        headerLayout.addView(tvTitle)
        headerLayout.addView(btnSettings)
        headerLayout.addView(btnClose)

        // Price display
        tvPrice = TextView(context).apply {
            text = "XAUUSD: ---"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        // Signal display
        tvSignal = TextView(context).apply {
            text = "Sinyal: MENUNGGU"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }

        // Confidence
        tvConfidence = TextView(context).apply {
            text = "Confidence: 0%"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
        }

        // Progress bar
        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
            visibility = GONE
            isIndeterminate = true
        }

        // Error text
        tvError = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#FF4757"))
            textSize = 12f
            gravity = Gravity.CENTER
            visibility = GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }

        // Analysis section
        llAnalysis = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
            visibility = GONE
        }

        val analysisLabel = TextView(context).apply {
            text = "Analisis AI"
            setTextColor(Color.parseColor("#00E5A0"))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        scrollAnalysis = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            ).apply { topMargin = 8 }
        }

        tvAnalysis = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        scrollAnalysis.addView(tvAnalysis)
        llAnalysis.addView(analysisLabel)
        llAnalysis.addView(scrollAnalysis)

        // Actions layout
        llActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        // Analyze button
        btnAnalyze = Button(context).apply {
            text = "ANALISA AI"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f).apply {
                marginEnd = 8
            }
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0F3460"))
                cornerRadius = 16f
            }
            background = bg
        }

        // Buy button
        btnBuy = Button(context).apply {
            text = "BUY"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f).apply {
                marginEnd = 8
            }
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#00B894"))
                cornerRadius = 16f
            }
            background = bg
        }

        // Sell button
        btnSell = Button(context).apply {
            text = "SELL"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FF4757"))
                cornerRadius = 16f
            }
            background = bg
        }

        llActions.addView(btnAnalyze)
        llActions.addView(btnBuy)
        llActions.addView(btnSell)

        // Assemble
        mainLayout.addView(headerLayout)
        mainLayout.addView(tvPrice)
        mainLayout.addView(tvSignal)
        mainLayout.addView(tvConfidence)
        mainLayout.addView(progressBar)
        mainLayout.addView(tvError)
        mainLayout.addView(llAnalysis)
        mainLayout.addView(llActions)

        addView(mainLayout)

        // Listeners
        btnClose.setOnClickListener { onCloseListener?.invoke() }
        btnSettings.setOnClickListener { onSettingsListener?.invoke() }
        btnAnalyze.setOnClickListener { onAnalyzeListener?.invoke() }
        btnBuy.setOnClickListener { onTradeListener?.invoke("BUY") }
        btnSell.setOnClickListener { onTradeListener?.invoke("SELL") }

        // Fade in animation
        val fadeIn = AlphaAnimation(0f, 1f).apply { duration = 200 }
        startAnimation(fadeIn)
    }

    fun setOnCloseListener(listener: () -> Unit) { onCloseListener = listener }
    fun setOnAnalyzeListener(listener: () -> Unit) { onAnalyzeListener = listener }
    fun setOnTradeListener(listener: (String) -> Unit) { onTradeListener = listener }
    fun setOnSettingsListener(listener: () -> Unit) { onSettingsListener = listener }

    fun updatePrice(price: Double) {
        tvPrice.text = "XAUUSD: ${String.format("%.4f", price)}"
    }

    fun setAnalyzing(analyzing: Boolean) {
        progressBar.visibility = if (analyzing) VISIBLE else GONE
        btnAnalyze.isEnabled = !analyzing
        btnBuy.isEnabled = !analyzing
        btnSell.isEnabled = !analyzing
        if (analyzing) {
            tvSignal.text = "Menganalisa market..."
            tvSignal.setTextColor(Color.parseColor("#FFD700"))
        }
    }

    fun setTrading(trading: Boolean) {
        progressBar.visibility = if (trading) VISIBLE else GONE
        btnAnalyze.isEnabled = !trading
        btnBuy.isEnabled = !trading
        btnSell.isEnabled = !trading
        if (trading) {
            tvSignal.text = "Mengeksekusi trade..."
            tvSignal.setTextColor(Color.parseColor("#FFD700"))
        }
    }

    fun showAnalysis(analysis: AnalysisResult) {
        llAnalysis.visibility = VISIBLE
        tvError.visibility = GONE

        tvSignal.text = "Sinyal: ${analysis.signal}"
        tvSignal.setTextColor(when(analysis.signal) {
            "BUY", "STRONG_BUY" -> Color.parseColor("#00E5A0")
            "SELL", "STRONG_SELL" -> Color.parseColor("#FF4757")
            else -> Color.parseColor("#FFD700")
        })

        tvConfidence.text = "Confidence: ${analysis.confidence}%"
        tvConfidence.setTextColor(when {
            analysis.confidence >= 80 -> Color.parseColor("#00E5A0")
            analysis.confidence >= 60 -> Color.parseColor("#FFD700")
            else -> Color.parseColor("#FF4757")
        })

        tvAnalysis.text = analysis.reasoning
    }

    fun showTradeResult(result: TradeResult) {
        tvError.visibility = GONE
        val color = if (result.success) "#00E5A0" else "#FF4757"
        tvSignal.text = if (result.success) "Trade Sukses!" else "Trade Gagal"
        tvSignal.setTextColor(Color.parseColor(color))
        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
    }

    fun showError(message: String) {
        tvError.text = message
        tvError.visibility = VISIBLE
        progressBar.visibility = GONE
    }
}
