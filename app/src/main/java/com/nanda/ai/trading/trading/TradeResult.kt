package com.nanda.ai.trading.trading

data class TradeResult(
    val success: Boolean,
    val orderId: String = "",
    val symbol: String = "",
    val side: String = "",
    val entryPrice: String = "",
    val qty: String = "",
    val takeProfit: String = "",
    val stopLoss: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class OrderRequest(
    val symbol: String,
    val side: String,      // Buy / Sell
    val orderType: String, // Market / Limit
    val qty: String,
    val price: String = "",
    val takeProfit: String = "",
    val stopLoss: String = ""
)
