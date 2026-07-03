package com.nanda.ai.trading.data

data class MarketData(
    val symbol: String,
    val timeframe: String,
    val lastPrice: Double,
    val candles: List<CandleData>,
    val volume24h: Double = 0.0,
    val change24h: Double = 0.0,
    val high24h: Double = 0.0,
    val low24h: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class CandleData(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)
