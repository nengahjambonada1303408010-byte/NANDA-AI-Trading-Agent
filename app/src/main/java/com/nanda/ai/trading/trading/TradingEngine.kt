package com.nanda.ai.trading.trading

import android.content.Context
import android.util.Log
import com.nanda.ai.trading.ai.AIAnalyzer
import com.nanda.ai.trading.ai.AnalysisResult
import com.nanda.ai.trading.data.MarketData
import com.nanda.ai.trading.utils.Constants
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class TradingEngine(
    private val context: Context,
    private val bybitAPI: BybitAPI,
    private val aiAnalyzer: AIAnalyzer
) {
    private val TAG = "NANDA::Engine"
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)

    private var currentJob: Job? = null
    private var currentSymbol = Constants.DEFAULT_SYMBOL
    private var currentTimeframe = Constants.DEFAULT_TIMEFRAME

    private var onSignalListener: ((AnalysisResult) -> Unit)? = null
    private var onTradeListener: ((TradeResult) -> Unit)? = null
    private var onErrorListener: ((String) -> Unit)? = null

    fun setCallbacks(
        onSignal: (AnalysisResult) -> Unit,
        onTrade: (TradeResult) -> Unit,
        onError: (String) -> Unit
    ) {
        onSignalListener = onSignal
        onTradeListener = onTrade
        onErrorListener = onError
    }

    fun start(symbol: String = currentSymbol, timeframe: String = currentTimeframe) {
        if (isRunning.get()) return

        currentSymbol = symbol
        currentTimeframe = timeframe
        isRunning.set(true)

        Log.i(TAG, "Trading engine started for $symbol (${timeframe}m)")

        currentJob = engineScope.launch {
            while (isActive && isRunning.get()) {
                try {
                    // 1. Fetch market data
                    val marketData = bybitAPI.getMarketData(currentSymbol, currentTimeframe)
                    if (marketData == null) {
                        onErrorListener?.invoke("Gagal mengambil data market")
                        delay(10000)
                        continue
                    }

                    // 2. AI Analysis
                    val analysis = aiAnalyzer.analyzeMarket(marketData)
                    onSignalListener?.invoke(analysis)

                    // 3. Check auto-trade conditions
                    val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    val autoTrade = prefs.getBoolean(Constants.KEY_AUTO_TRADE, false)
                    val confidenceThreshold = prefs.getInt("confidence_threshold", 80)

                    if (autoTrade && analysis.confidence >= confidenceThreshold &&
                        (analysis.signal == "BUY" || analysis.signal == "SELL" ||
                         analysis.signal == "STRONG_BUY" || analysis.signal == "STRONG_SELL")) {

                        // Check daily trade limit
                        val dailyTrades = getDailyTradeCount()
                        val maxTrades = prefs.getInt("max_daily_trades", 10)

                        if (dailyTrades < maxTrades) {
                            executeAutoTrade(analysis)
                        } else {
                            Log.w(TAG, "Daily trade limit reached ($maxTrades)")
                        }
                    }

                    // 4. Wait before next cycle
                    delay(getAnalysisInterval())

                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Engine cycle error: ${e.message}")
                    onErrorListener?.invoke("Error: ${e.message}")
                    delay(15000)
                }
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        currentJob?.cancel()
        currentJob = null
        Log.i(TAG, "Trading engine stopped")
    }

    fun isActive(): Boolean = isRunning.get()

    private suspend fun executeAutoTrade(analysis: AnalysisResult) {
        try {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val riskPercent = prefs.getFloat(Constants.KEY_RISK_PERCENT, Constants.DEFAULT_RISK_PERCENT.toFloat())
            val leverage = prefs.getInt(Constants.KEY_LEVERAGE, Constants.DEFAULT_LEVERAGE)

            // Get balance
            val balance = bybitAPI.getWalletBalance("USDT") ?: return
            val riskAmount = balance * (riskPercent / 100.0)

            // Calculate position size
            val positionSize = riskAmount * leverage / analysis.entryPrice

            // Create order
            val side = when (analysis.signal) {
                "BUY", "STRONG_BUY" -> "Buy"
                "SELL", "STRONG_SELL" -> "Sell"
                else -> return
            }

            val order = OrderRequest(
                symbol = currentSymbol,
                side = side,
                orderType = "Market",
                qty = String.format("%.4f", positionSize),
                takeProfit = String.format("%.5f", analysis.takeProfit),
                stopLoss = String.format("%.5f", analysis.stopLoss)
            )

            val result = bybitAPI.placeOrder(order)
            onTradeListener?.invoke(result)

            if (result.success) {
                incrementDailyTradeCount()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Auto-trade error: ${e.message}")
            onErrorListener?.invoke("Auto-trade error: ${e.message}")
        }
    }

    private fun getAnalysisInterval(): Long {
        return when (currentTimeframe) {
            "1" -> 30000L      // 30 seconds for 1m
            "5" -> 60000L      // 1 minute for 5m
            "15" -> 120000L    // 2 minutes for 15m
            "30" -> 180000L    // 3 minutes for 30m
            "60" -> 300000L    // 5 minutes for 1H
            else -> 120000L
        }
    }

    private fun getDailyTradeCount(): Int {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString("last_trade_date", "") ?: ""
        val today = java.time.LocalDate.now().toString()

        return if (lastDate == today) {
            prefs.getInt("daily_trade_count", 0)
        } else {
            prefs.edit().putString("last_trade_date", today).putInt("daily_trade_count", 0).apply()
            0
        }
    }

    private fun incrementDailyTradeCount() {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        val current = getDailyTradeCount()
        prefs.edit()
            .putString("last_trade_date", today)
            .putInt("daily_trade_count", current + 1)
            .apply()
    }

    fun cleanup() {
        stop()
        engineScope.cancel()
    }
}
