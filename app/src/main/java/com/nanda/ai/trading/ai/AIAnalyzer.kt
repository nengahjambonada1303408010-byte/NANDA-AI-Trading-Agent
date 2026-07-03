package com.nanda.ai.trading.ai

import com.nanda.ai.trading.data.MarketData

interface AIAnalyzer {
    suspend fun analyzeMarket(marketData: MarketData): AnalysisResult
    suspend fun analyzeMultipleTimeframes(dataMap: Map<String, MarketData>): List<AnalysisResult>
}
