package com.nanda.ai.trading.ai

data class AnalysisResult(
    val signal: String,        // BUY, SELL, NEUTRAL, STRONG_BUY, STRONG_SELL
    val confidence: Int,       // 0-100
    val entryPrice: Double,
    val takeProfit: Double,
    val stopLoss: Double,
    val reasoning: String,
    val timeframe: String,
    val timestamp: Long = System.currentTimeMillis()
)
