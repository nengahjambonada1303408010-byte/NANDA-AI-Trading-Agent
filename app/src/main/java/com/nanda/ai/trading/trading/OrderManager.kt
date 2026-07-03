package com.nanda.ai.trading.trading

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nanda.ai.trading.data.AppDatabase
import com.nanda.ai.trading.data.TradeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderManager(context: Context, private val bybitAPI: BybitAPI) {

    private val database = AppDatabase.getDatabase(context)
    private val tradeDao = database.tradeDao()

    private val _openOrders = MutableLiveData<List<Map<String, Any>>>()
    val openOrders: LiveData<List<Map<String, Any>>> = _openOrders

    private val _tradeHistory = MutableLiveData<List<TradeEntity>>()
    val tradeHistory: LiveData<List<TradeEntity>> = _tradeHistory

    fun refreshOpenOrders(symbol: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val orders = bybitAPI.getOpenOrders(symbol)
            _openOrders.postValue(orders)
        }
    }

    fun refreshTradeHistory() {
        CoroutineScope(Dispatchers.IO).launch {
            val trades = tradeDao.getAllTrades()
            _tradeHistory.postValue(trades)
        }
    }

    suspend fun cancelOrder(orderId: String, symbol: String): Boolean {
        return withContext(Dispatchers.IO) {
            val result = bybitAPI.cancelOrder(orderId, symbol)
            if (result) {
                refreshOpenOrders(symbol)
            }
            result
        }
    }

    fun saveTradeLocally(tradeResult: TradeResult) {
        CoroutineScope(Dispatchers.IO).launch {
            val entity = TradeEntity(
                orderId = tradeResult.orderId,
                symbol = tradeResult.symbol,
                side = tradeResult.side,
                entryPrice = tradeResult.entryPrice,
                qty = tradeResult.qty,
                takeProfit = tradeResult.takeProfit,
                stopLoss = tradeResult.stopLoss,
                status = if (tradeResult.success) "EXECUTED" else "FAILED",
                message = tradeResult.message,
                timestamp = tradeResult.timestamp
            )
            tradeDao.insertTrade(entity)
            refreshTradeHistory()
        }
    }

    suspend fun getTradeStats(): TradeStats {
        return withContext(Dispatchers.IO) {
            val allTrades = tradeDao.getAllTradesSync()
            val total = allTrades.size
            val wins = allTrades.count { it.status == "WIN" }
            val losses = allTrades.count { it.status == "LOSS" }
            val winRate = if (total > 0) (wins.toDouble() / total * 100) else 0.0

            TradeStats(
                totalTrades = total,
                winCount = wins,
                lossCount = losses,
                winRate = winRate
            )
        }
    }
}

data class TradeStats(
    val totalTrades: Int,
    val winCount: Int,
    val lossCount: Int,
    val winRate: Double
)
