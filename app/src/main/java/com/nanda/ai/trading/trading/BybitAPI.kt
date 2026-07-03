package com.nanda.ai.trading.trading

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.nanda.ai.trading.data.CandleData
import com.nanda.ai.trading.data.MarketData
import com.nanda.ai.trading.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.Instant

class BybitAPI(private val context: Context) {

    private val TAG = "NANDA::BybitAPI"
    private val gson = Gson()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun getRetrofit(): Retrofit {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val useTestnet = prefs.getBoolean(Constants.KEY_TESTNET, true)
        val baseUrl = if (useTestnet) Constants.BYBIT_TESTNET_URL else Constants.BYBIT_BASE_URL

        return Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun getService(): BybitService = getRetrofit().create(BybitService::class.java)

    // === Public API (no auth required) ===

    suspend fun getLatestPrice(symbol: String): Double? = withContext(Dispatchers.IO) {
        try {
            val response = getService().getTickers("linear", "$symbol")
            if (response.isSuccessful && response.body()?.retCode == 0) {
                val ticker = response.body()?.result?.list?.firstOrNull()
                ticker?.lastPrice?.toDoubleOrNull()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Get price error: ${e.message}")
            null
        }
    }

    suspend fun getMarketData(symbol: String, timeframe: String): MarketData? = withContext(Dispatchers.IO) {
        try {
            // Get klines/candles
            val interval = when (timeframe) {
                "1" -> "1"
                "3" -> "3"
                "5" -> "5"
                "15" -> "15"
                "30" -> "30"
                "60", "H1" -> "60"
                "240", "H4" -> "240"
                "D", "1D" -> "D"
                else -> "15"
            }

            val response = getService().getKlines(
                category = "linear",
                symbol = symbol,
                interval = interval,
                limit = 200
            )

            if (response.isSuccessful && response.body()?.retCode == 0) {
                val klineData = response.body()?.result?.list ?: emptyList()

                val candles = klineData.map { k ->
                    CandleData(
                        timestamp = (k[0] as? Number)?.toLong() ?: 0L,
                        open = (k[1] as? String)?.toDoubleOrNull() ?: 0.0,
                        high = (k[2] as? String)?.toDoubleOrNull() ?: 0.0,
                        low = (k[3] as? String)?.toDoubleOrNull() ?: 0.0,
                        close = (k[4] as? String)?.toDoubleOrNull() ?: 0.0,
                        volume = (k[5] as? String)?.toDoubleOrNull() ?: 0.0
                    )
                }.sortedBy { it.timestamp }

                val lastPrice = candles.lastOrNull()?.close ?: 0.0
                val high24h = candles.maxOfOrNull { it.high } ?: 0.0
                val low24h = candles.minOfOrNull { it.low } ?: 0.0
                val volume24h = candles.sumOf { it.volume }
                val change24h = if (candles.size >= 2) {
                    ((lastPrice - candles.first().open) / candles.first().open) * 100
                } else 0.0

                MarketData(
                    symbol = symbol,
                    timeframe = timeframe,
                    lastPrice = lastPrice,
                    candles = candles,
                    volume24h = volume24h,
                    change24h = change24h,
                    high24h = high24h,
                    low24h = low24h
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Get market data error: ${e.message}")
            null
        }
    }

    // === Private API (auth required) ===

    suspend fun getWalletBalance(coin: String): Double? = withContext(Dispatchers.IO) {
        try {
            val (apiKey, apiSecret) = getCredentials()
            if (apiKey.isEmpty() || apiSecret.isEmpty()) return@withContext null

            val timestamp = Instant.now().toEpochMilli().toString()
            val recvWindow = "5000"
            val params = "accountType=UNIFIED&coin=$coin"
            val signaturePayload = timestamp + apiKey + recvWindow + params
            val signature = generateSignature(apiSecret, signaturePayload)

            val response = getService().getWalletBalance(
                apiKey = apiKey,
                timestamp = timestamp,
                recvWindow = recvWindow,
                signature = signature,
                accountType = "UNIFIED",
                coin = coin
            )

            if (response.isSuccessful && response.body()?.retCode == 0) {
                val balance = response.body()?.result?.list?.firstOrNull()
                    ?.coin?.firstOrNull()
                balance?.walletBalance?.toDoubleOrNull()
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Get balance error: ${e.message}")
            null
        }
    }

    suspend fun placeOrder(order: OrderRequest): TradeResult = withContext(Dispatchers.IO) {
        try {
            val (apiKey, apiSecret) = getCredentials()
            if (apiKey.isEmpty() || apiSecret.isEmpty()) {
                return@withContext TradeResult(
                    success = false,
                    message = "API Key Bybit belum diatur. Silakan atur di Settings."
                )
            }

            val timestamp = Instant.now().toEpochMilli().toString()
            val recvWindow = "5000"

            val orderMap = mutableMapOf<String, String>(
                "category" to "linear",
                "symbol" to order.symbol,
                "side" to order.side,
                "orderType" to order.orderType,
                "qty" to order.qty
            )
            if (order.orderType == "Limit") {
                orderMap["price"] = order.price
            }
            if (order.takeProfit.isNotEmpty()) {
                orderMap["takeProfit"] = order.takeProfit
            }
            if (order.stopLoss.isNotEmpty()) {
                orderMap["stopLoss"] = order.stopLoss
            }

            val bodyJson = gson.toJson(orderMap)
            val signaturePayload = timestamp + apiKey + recvWindow + bodyJson
            val signature = generateSignature(apiSecret, signaturePayload)

            val request = getService().placeOrder(
                apiKey = apiKey,
                timestamp = timestamp,
                recvWindow = recvWindow,
                signature = signature,
                body = orderMap
            )

            if (request.isSuccessful && request.body()?.retCode == 0) {
                val result = request.body()?.result
                TradeResult(
                    success = true,
                    orderId = result?.orderId ?: "",
                    symbol = order.symbol,
                    side = order.side,
                    entryPrice = orderMap["price"] ?: "Market",
                    qty = order.qty,
                    takeProfit = order.takeProfit,
                    stopLoss = order.stopLoss,
                    message = "Order berhasil! ID: ${result?.orderId ?: "N/A"}"
                )
            } else {
                val errorMsg = request.body()?.retMsg ?: request.errorBody()?.string() ?: "Unknown error"
                TradeResult(
                    success = false,
                    message = "Order gagal: $errorMsg"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Place order error: ${e.message}")
            TradeResult(
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }

    suspend fun getOpenOrders(symbol: String? = null): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val (apiKey, apiSecret) = getCredentials()
            if (apiKey.isEmpty() || apiSecret.isEmpty()) return@withContext emptyList()

            val timestamp = Instant.now().toEpochMilli().toString()
            val recvWindow = "5000"
            val params = if (symbol != null) "category=linear&symbol=$symbol" else "category=linear"
            val signaturePayload = timestamp + apiKey + recvWindow + params
            val signature = generateSignature(apiSecret, signaturePayload)

            val response = getService().getOpenOrders(
                apiKey = apiKey,
                timestamp = timestamp,
                recvWindow = recvWindow,
                signature = signature,
                category = "linear",
                symbol = symbol
            )

            if (response.isSuccessful) {
                response.body()?.result?.list ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Get open orders error: ${e.message}")
            emptyList()
        }
    }

    suspend fun cancelOrder(orderId: String, symbol: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val (apiKey, apiSecret) = getCredentials()
            if (apiKey.isEmpty() || apiSecret.isEmpty()) return@withContext false

            val timestamp = Instant.now().toEpochMilli().toString()
            val recvWindow = "5000"

            val bodyMap = mapOf(
                "category" to "linear",
                "symbol" to symbol,
                "orderId" to orderId
            )
            val bodyJson = gson.toJson(bodyMap)
            val signaturePayload = timestamp + apiKey + recvWindow + bodyJson
            val signature = generateSignature(apiSecret, signaturePayload)

            val response = getService().cancelOrder(
                apiKey = apiKey,
                timestamp = timestamp,
                recvWindow = recvWindow,
                signature = signature,
                body = bodyMap
            )

            response.isSuccessful && response.body()?.retCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Cancel order error: ${e.message}")
            false
        }
    }

    // === Helper methods ===

    private fun getCredentials(): Pair<String, String> {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(Constants.KEY_API_BYBIT_KEY, "") ?: ""
        val apiSecret = prefs.getString(Constants.KEY_API_BYBIT_SECRET, "") ?: ""
        return Pair(apiKey, apiSecret)
    }

    private fun generateSignature(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

// Retrofit Interface
interface BybitService {
    // Public endpoints
    @GET("v5/market/tickers")
    suspend fun getTickers(
        @Query("category") category: String,
        @Query("symbol") symbol: String
    ): Response<BybitResponse<TickerResult>>

    @GET("v5/market/kline")
    suspend fun getKlines(
        @Query("category") category: String,
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 200
    ): Response<BybitResponse<KlineResult>>

    // Private endpoints
    @GET("v5/account/wallet-balance")
    suspend fun getWalletBalance(
        @Header("X-BAPI-API-KEY") apiKey: String,
        @Header("X-BAPI-TIMESTAMP") timestamp: String,
        @Header("X-BAPI-RECV-WINDOW") recvWindow: String,
        @Header("X-BAPI-SIGN") signature: String,
        @Query("accountType") accountType: String,
        @Query("coin") coin: String
    ): Response<BybitResponse<WalletResult>>

    @POST("v5/order/create")
    suspend fun placeOrder(
        @Header("X-BAPI-API-KEY") apiKey: String,
        @Header("X-BAPI-TIMESTAMP") timestamp: String,
        @Header("X-BAPI-RECV-WINDOW") recvWindow: String,
        @Header("X-BAPI-SIGN") signature: String,
        @Body body: Map<String, String>
    ): Response<BybitResponse<OrderResult>>

    @GET("v5/order/realtime")
    suspend fun getOpenOrders(
        @Header("X-BAPI-API-KEY") apiKey: String,
        @Header("X-BAPI-TIMESTAMP") timestamp: String,
        @Header("X-BAPI-RECV-WINDOW") recvWindow: String,
        @Header("X-BAPI-SIGN") signature: String,
        @Query("category") category: String,
        @Query("symbol") symbol: String?
    ): Response<BybitResponse<OrderListResult>>

    @POST("v5/order/cancel")
    suspend fun cancelOrder(
        @Header("X-BAPI-API-KEY") apiKey: String,
        @Header("X-BAPI-TIMESTAMP") timestamp: String,
        @Header("X-BAPI-RECV-WINDOW") recvWindow: String,
        @Header("X-BAPI-SIGN") signature: String,
        @Body body: Map<String, String>
    ): Response<BybitResponse<CancelResult>>
}

// Response data classes
data class BybitResponse<T>(
    val retCode: Int,
    val retMsg: String,
    val result: T
)

data class TickerResult(
    val category: String,
    val list: List<TickerItem>
)

data class TickerItem(
    val symbol: String,
    @SerializedName("lastPrice") val lastPrice: String,
    @SerializedName("prevPrice24h") val prevPrice24h: String,
    @SerializedName("price24hPcnt") val price24hPcnt: String,
    @SerializedName("highPrice24h") val highPrice24h: String,
    @SerializedName("lowPrice24h") val lowPrice24h: String,
    @SerializedName("volume24h") val volume24h: String
)

data class KlineResult(
    val category: String,
    val symbol: String,
    val list: List<List<Any>>
)

data class WalletResult(
    val list: List<WalletItem>
)

data class WalletItem(
    val coin: List<CoinBalance>
)

data class CoinBalance(
    @SerializedName("coin") val coin: String,
    @SerializedName("walletBalance") val walletBalance: String,
    @SerializedName("availableToWithdraw") val availableToWithdraw: String,
    @SerializedName("unrealisedPnl") val unrealisedPnl: String
)

data class OrderResult(
    @SerializedName("orderId") val orderId: String,
    @SerializedName("orderLinkId") val orderLinkId: String
)

data class OrderListResult(
    val category: String,
    val list: List<Map<String, Any>>
)

data class CancelResult(
    @SerializedName("orderId") val orderId: String
)
