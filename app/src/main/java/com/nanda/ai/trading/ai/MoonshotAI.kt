package com.nanda.ai.trading.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.nanda.ai.trading.data.CandleData
import com.nanda.ai.trading.data.MarketData
import com.nanda.ai.trading.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class MoonshotAI(private val context: Context) : AIAnalyzer {

    private val TAG = "NANDA::MoonshotAI"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun analyzeMarket(marketData: MarketData): AnalysisResult {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val apiKey = prefs.getString(Constants.KEY_API_MOONSHOT_KEY, "") ?: ""

                if (apiKey.isEmpty()) {
                    return@withContext fallbackAnalysis(marketData, "API Key Moonshot belum diatur")
                }

                val prompt = buildAnalysisPrompt(marketData)
                val response = callMoonshotAPI(apiKey, prompt)

                parseAIResponse(response, marketData)
            } catch (e: Exception) {
                Log.e(TAG, "AI analysis error: ${e.message}")
                fallbackAnalysis(marketData, e.message ?: "Unknown error")
            }
        }
    }

    override suspend fun analyzeMultipleTimeframes(
        dataMap: Map<String, MarketData>
    ): List<AnalysisResult> {
        val results = mutableListOf<AnalysisResult>()
        dataMap.forEach { (_, data) ->
            results.add(analyzeMarket(data))
        }
        return results
    }

    private fun buildAnalysisPrompt(marketData: MarketData): String {
        val candles = marketData.candles.take(50)
        val candleStr = candles.joinToString("\n") { c ->
            "[${c.timestamp}] O:${c.open} H:${c.high} L:${c.low} C:${c.close} V:${c.volume}"
        }

        return """
        Kamu adalah analis trading profesional dengan spesialisasi scalping XAUUSD.
        
        Analisis data candlestick berikut untuk time frame ${marketData.timeframe} menit:
        
        Symbol: ${marketData.symbol}
        Timeframe: ${marketData.timeframe} menit
        Data terakhir: ${marketData.lastPrice}
        
        Candlestick Data (50 terakhir):
        $candleStr
        
        Berikan analisis dalam format JSON berikut:
        {
            "signal": "BUY atau SELL atau NEUTRAL",
            "confidence": 0-100,
            "entry_price": float,
            "take_profit": float,
            "stop_loss": float,
            "reasoning": "penjelasan singkat dalam Bahasa Indonesia",
            "key_levels": "level support dan resistance penting"
        }
        
        Aturan:
        - Signal BUY hanya jika ada konfirmasi bullish yang kuat
        - Signal SELL hanya jika ada konfirmasi bearish yang kuat  
        - NEUTRAL jika market sideways atau tidak ada konfirmasi jelas
        - Confidence >= 80 baru dianggap valid untuk eksekusi
        - Gunakan risk:reward minimum 1:2
        - Pertimbangkan volume, support/resistance, dan trend
        """.trimIndent()
    }

    private fun callMoonshotAPI(apiKey: String, prompt: String): String {
        val requestBody = MoonshotRequest(
            model = Constants.AI_MODEL,
            messages = listOf(
                Message(role = "system", content = "Kamu adalah AI trading assistant profesional. Analisis market dan berikan signal trading yang akurat. Response dalam format JSON."),
                Message(role = "user", content = prompt)
            ),
            max_tokens = Constants.AI_MAX_TOKENS,
            temperature = Constants.AI_TEMPERATURE
        )

        val json = gson.toJson(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())

        // Try primary URL first, fallback to alternative
        val urls = listOf(Constants.MOONSHOT_BASE_URL, Constants.MOONSHOT_ALT_URL)
        var lastError: Exception? = null

        for (baseUrl in urls) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return response.body?.string() ?: ""
                    } else {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.w(TAG, "API error from $baseUrl: ${response.code} - $errorBody")
                        lastError = Exception("HTTP ${response.code}: $errorBody")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to call $baseUrl: ${e.message}")
                lastError = e
            }
        }

        throw lastError ?: Exception("All API endpoints failed")
    }

    private fun parseAIResponse(response: String, marketData: MarketData): AnalysisResult {
        return try {
            val completion = gson.fromJson(response, MoonshotResponse::class.java)
            val content = completion.choices.firstOrNull()?.message?.content ?: ""

            // Extract JSON from content
            val jsonStr = extractJson(content)
            val aiResult = gson.fromJson(jsonStr, AIResult::class.java)

            AnalysisResult(
                signal = aiResult.signal.uppercase(),
                confidence = aiResult.confidence.coerceIn(0, 100),
                entryPrice = aiResult.entry_price,
                takeProfit = aiResult.take_profit,
                stopLoss = aiResult.stop_loss,
                reasoning = "${aiResult.reasoning}\n\nKey Levels: ${aiResult.key_levels}",
                timeframe = marketData.timeframe
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            fallbackAnalysis(marketData, "Parse error: ${e.message}")
        }
    }

    private fun extractJson(content: String): String {
        val start = content.indexOf("{")
        val end = content.lastIndexOf("}")
        return if (start >= 0 && end > start) {
            content.substring(start, end + 1)
        } else {
            content
        }
    }

    private fun fallbackAnalysis(marketData: MarketData, error: String): AnalysisResult {
        val lastCandle = marketData.candles.lastOrNull()
        val prevCandle = marketData.candles.dropLast(1).lastOrNull()

        // Simple technical fallback
        var signal = "NEUTRAL"
        var confidence = 50

        if (lastCandle != null && prevCandle != null) {
            val bullish = lastCandle.close > lastCandle.open && lastCandle.close > prevCandle.close
            val bearish = lastCandle.close < lastCandle.open && lastCandle.close < prevCandle.close

            signal = when {
                bullish -> "BUY"
                bearish -> "SELL"
                else -> "NEUTRAL"
            }
            confidence = if (bullish || bearish) 65 else 50
        }

        return AnalysisResult(
            signal = signal,
            confidence = confidence,
            entryPrice = marketData.lastPrice,
            takeProfit = if (signal == "BUY") marketData.lastPrice * 1.02 else marketData.lastPrice * 0.98,
            stopLoss = if (signal == "BUY") marketData.lastPrice * 0.99 else marketData.lastPrice * 1.01,
            reasoning = "Analisis fallback (AI tidak tersedia): $error\n\nBerdasarkan candlestick terakhir: ${signal}",
            timeframe = marketData.timeframe
        )
    }
}

// Data classes for API
private data class MoonshotRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_tokens") val max_tokens: Int,
    val temperature: Double
)

private data class Message(
    val role: String,
    val content: String
)

private data class MoonshotResponse(
    val choices: List<Choice>,
    val usage: Usage
)

private data class Choice(
    val message: Message,
    val finish_reason: String
)

private data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

private data class AIResult(
    val signal: String,
    val confidence: Int,
    @SerializedName("entry_price") val entry_price: Double,
    @SerializedName("take_profit") val take_profit: Double,
    @SerializedName("stop_loss") val stop_loss: Double,
    val reasoning: String,
    @SerializedName("key_levels") val key_levels: String
)
