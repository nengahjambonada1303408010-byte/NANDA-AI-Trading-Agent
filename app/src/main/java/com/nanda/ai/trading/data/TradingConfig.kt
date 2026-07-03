package com.nanda.ai.trading.data

data class TradingConfig(
    val symbol: String = "XAUUSD",
    val timeframe: String = "15",
    val riskPercent: Double = 1.0,
    val leverage: Int = 20,
    val takeProfitRatio: Double = 2.0,
    val stopLossRatio: Double = 1.0,
    val autoTrade: Boolean = false,
    val useTestnet: Boolean = true,
    val confidenceThreshold: Int = 80,
    val maxDailyTrades: Int = 10
)
