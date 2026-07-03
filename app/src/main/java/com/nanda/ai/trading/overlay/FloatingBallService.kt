package com.nanda.ai.trading.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.nanda.ai.trading.NandaApplication
import com.nanda.ai.trading.R
import com.nanda.ai.trading.ai.AIAnalyzer
import com.nanda.ai.trading.ai.AnalysisResult
import com.nanda.ai.trading.data.MarketData
import com.nanda.ai.trading.trading.BybitAPI
import com.nanda.ai.trading.trading.OrderRequest
import com.nanda.ai.trading.trading.TradeResult
import com.nanda.ai.trading.ui.MainActivity
import com.nanda.ai.trading.utils.Constants
import kotlinx.coroutines.*
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

class FloatingBallService : Service() {

    private val TAG = "NANDA::FloatingBall"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams
    private var floatingBallView: FloatingBallView? = null
    private var controlPanelView: ControlPanelView? = null

    private lateinit var bybitAPI: BybitAPI
    private lateinit var aiAnalyzer: AIAnalyzer

    private var isPanelOpen = false
    private var isAnalyzing = false
    private var lastPrice: Double = 0.0
    private var currentSymbol = Constants.DEFAULT_SYMBOL
    private var currentAnalysis: AnalysisResult? = null

    // Screen dimensions for boundary
    private var screenWidth = 0
    private var screenHeight = 0

    companion object {
        const val ACTION_SHOW = "com.nanda.ai.trading.SHOW_BALL"
        const val ACTION_HIDE = "com.nanda.ai.trading.HIDE_BALL"
        const val ACTION_TOGGLE = "com.nanda.ai.trading.TOGGLE_BALL"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bybitAPI = (application as NandaApplication).bybitAPI
        aiAnalyzer = (application as NandaApplication).moonshotAI

        // Get screen dimensions
        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        createFloatingBall()
        startForegroundService()
        startPriceUpdates()
    }

    private fun createFloatingBall() {
        floatingBallView = FloatingBallView(this).apply {
            setOnClickListener { toggleControlPanel() }
            setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var isDragging = false
                private val dragThreshold = 15

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = ballParams.x
                            initialY = ballParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDragging = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(dx) > dragThreshold || Math.abs(dy) > dragThreshold) {
                                isDragging = true
                            }
                            ballParams.x = (initialX + dx).coerceIn(0, screenWidth - v.width)
                            ballParams.y = (initialY + dy).coerceIn(0, screenHeight - v.height)
                            windowManager.updateViewLayout(v, ballParams)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!isDragging) {
                                v.performClick()
                            } else {
                                snapToEdge(v)
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }

        ballParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 150
            y = screenHeight / 3
        }

        windowManager.addView(floatingBallView, ballParams)
    }

    private fun snapToEdge(v: View) {
        val centerX = ballParams.x + v.width / 2
        val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - v.width

        serviceScope.launch {
            val startX = ballParams.x
            val distance = targetX - startX
            val steps = 10
            for (i in 1..steps) {
                ballParams.x = startX + (distance * i / steps)
                try {
                    windowManager.updateViewLayout(v, ballParams)
                } catch (e: Exception) {
                    break
                }
                delay(16)
            }
        }
    }

    private fun toggleControlPanel() {
        if (isPanelOpen) {
            closeControlPanel()
        } else {
            openControlPanel()
        }
    }

    private fun openControlPanel() {
        if (controlPanelView != null) return

        controlPanelView = ControlPanelView(this).apply {
            setOnCloseListener { closeControlPanel() }
            setOnAnalyzeListener { performAnalysis() }
            setOnTradeListener { side -> executeTrade(side) }
            setOnSettingsListener { openSettings() }
            updatePrice(lastPrice)
        }

        panelParams = WindowManager.LayoutParams(
            (screenWidth * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.4f
        }

        windowManager.addView(controlPanelView, panelParams)
        isPanelOpen = true
        floatingBallView?.hide()
    }

    private fun closeControlPanel() {
        controlPanelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing panel: ${e.message}")
            }
        }
        controlPanelView = null
        isPanelOpen = false
        floatingBallView?.show()
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NandaApplication.CHANNEL_SYSTEM)
            .setContentTitle("NANDA AI Trading")
            .setContentText("AI Agent aktif - Menunggu chart dibuka")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(Constants.NOTIF_FOREGROUND_SERVICE, notification)
    }

    private fun startPriceUpdates() {
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val price = bybitAPI.getLatestPrice(currentSymbol)
                    if (price != null) {
                        lastPrice = price
                        withContext(Dispatchers.Main) {
                            controlPanelView?.updatePrice(price)
                            floatingBallView?.updatePriceIndicator(price)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Price update error: ${e.message}")
                }
                delay(3000)
            }
        }
    }

    private fun performAnalysis() {
        if (isAnalyzing) return
        isAnalyzing = true

        controlPanelView?.setAnalyzing(true)

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Fetch market data from Bybit
                val marketData = bybitAPI.getMarketData(currentSymbol, Constants.DEFAULT_TIMEFRAME)

                if (marketData != null) {
                    // Send to AI for analysis
                    val analysis = aiAnalyzer.analyzeMarket(marketData)
                    currentAnalysis = analysis

                    withContext(Dispatchers.Main) {
                        controlPanelView?.showAnalysis(analysis)
                    }

                    // If auto-trade enabled and confidence high
                    val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    if (prefs.getBoolean(Constants.KEY_AUTO_TRADE, false) &&
                        analysis.confidence >= 80 &&
                        analysis.signal != "NEUTRAL") {
                        executeTrade(analysis.signal)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        controlPanelView?.showError("Gagal mengambil data market")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis error: ${e.message}")
                withContext(Dispatchers.Main) {
                    controlPanelView?.showError("Error: ${e.message}")
                }
            } finally {
                isAnalyzing = false
                withContext(Dispatchers.Main) {
                    controlPanelView?.setAnalyzing(false)
                }
            }
        }
    }

    private fun executeTrade(side: String) {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val riskPercent = prefs.getFloat(Constants.KEY_RISK_PERCENT, Constants.DEFAULT_RISK_PERCENT.toFloat())
        val leverage = prefs.getInt(Constants.KEY_LEVERAGE, Constants.DEFAULT_LEVERAGE)
        val tpRatio = prefs.getFloat(Constants.KEY_TP_RATIO, Constants.DEFAULT_TAKE_PROFIT.toFloat())
        val slRatio = prefs.getFloat(Constants.KEY_SL_RATIO, Constants.DEFAULT_STOP_LOSS.toFloat())

        controlPanelView?.setTrading(true)

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Get account balance
                val balance = bybitAPI.getWalletBalance("USDT")
                if (balance == null || balance <= 0) {
                    withContext(Dispatchers.Main) {
                        controlPanelView?.showError("Saldo tidak mencukupi")
                        controlPanelView?.setTrading(false)
                    }
                    return@launch
                }

                // Calculate position size
                val riskAmount = balance * (riskPercent / 100.0)
                val positionSize = (riskAmount * leverage / lastPrice)
                    .toBigDecimal()
                    .setScale(4, RoundingMode.DOWN)
                    .toDouble()

                // Calculate TP/SL
                val tpPrice = if (side == "BUY") {
                    lastPrice * (1 + (tpRatio / 100.0) / leverage)
                } else {
                    lastPrice * (1 - (tpRatio / 100.0) / leverage)
                }

                val slPrice = if (side == "BUY") {
                    lastPrice * (1 - (slRatio / 100.0) / leverage)
                } else {
                    lastPrice * (1 + (slRatio / 100.0) / leverage)
                }

                // Create order
                val order = OrderRequest(
                    symbol = currentSymbol,
                    side = if (side == "BUY") "Buy" else "Sell",
                    orderType = "Market",
                    qty = positionSize.toString(),
                    takeProfit = String.format("%.5f", tpPrice),
                    stopLoss = String.format("%.5f", slPrice)
                )

                // Execute
                val result = bybitAPI.placeOrder(order)

                withContext(Dispatchers.Main) {
                    if (result.success) {
                        controlPanelView?.showTradeResult(result)
                        showTradeNotification(result)
                    } else {
                        controlPanelView?.showError("Trade gagal: ${result.message}")
                    }
                    controlPanelView?.setTrading(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Trade error: ${e.message}")
                withContext(Dispatchers.Main) {
                    controlPanelView?.showError("Trade error: ${e.message}")
                    controlPanelView?.setTrading(false)
                }
            }
        }
    }

    private fun showTradeNotification(result: TradeResult) {
        val notification = NotificationCompat.Builder(this, NandaApplication.CHANNEL_TRADE)
            .setContentTitle("Trade Executed - NANDA AI")
            .setContentText("${result.side} ${result.symbol} @ ${result.entryPrice}")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(Constants.NOTIF_TRADE_RESULT, notification)
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                floatingBallView?.visibility = View.VISIBLE
            }
            ACTION_HIDE -> {
                floatingBallView?.visibility = View.GONE
                closeControlPanel()
            }
            ACTION_TOGGLE -> {
                if (floatingBallView?.visibility == View.VISIBLE) {
                    floatingBallView?.visibility = View.GONE
                    closeControlPanel()
                } else {
                    floatingBallView?.visibility = View.VISIBLE
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        closeControlPanel()
        floatingBallView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        isRunning = false
    }
}
