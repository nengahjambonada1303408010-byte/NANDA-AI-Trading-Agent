package com.nanda.ai.trading.utils

object Constants {
    // API Endpoints
    const val BYBIT_BASE_URL = "https://api.bybit.com"
    const val BYBIT_TESTNET_URL = "https://api-testnet.bybit.com"
    const val MOONSHOT_BASE_URL = "https://api.moonshot.cn/v1"
    const val MOONSHOT_ALT_URL = "https://api.moonshot.ai/v1"

    // WebSocket Endpoints
    const val BYBIT_WS_PUBLIC = "wss://stream.bybit.com/v5/public/linear"
    const val BYBIT_WS_PRIVATE = "wss://stream.bybit.com/v5/private"
    const val BYBIT_WS_TESTNET_PUBLIC = "wss://stream-testnet.bybit.com/v5/public/linear"
    const val BYBIT_WS_TESTNET_PRIVATE = "wss://stream-testnet.bybit.com/v5/private"

    // Trading Defaults
    const val DEFAULT_SYMBOL = "XAUUSD"
    const val DEFAULT_RISK_PERCENT = 1.0
    const val DEFAULT_LEVERAGE = 20
    const val DEFAULT_TIMEFRAME = "15"
    const val DEFAULT_TAKE_PROFIT = 2.0  // 1:2 RR
    const val DEFAULT_STOP_LOSS = 1.0

    // AI Configuration
    const val AI_MODEL = "kimi-latest"
    const val AI_MAX_TOKENS = 2048
    const val AI_TEMPERATURE = 0.3

    // App Settings
    const val PREFS_NAME = "nanda_prefs"
    const val KEY_API_BYBIT_KEY = "bybit_api_key"
    const val KEY_API_BYBIT_SECRET = "bybit_api_secret"
    const val KEY_API_MOONSHOT_KEY = "moonshot_api_key"
    const val KEY_RISK_PERCENT = "risk_percent"
    const val KEY_LEVERAGE = "leverage"
    const val KEY_SYMBOL = "symbol"
    const val KEY_TIMEFRAME = "timeframe"
    const val KEY_AUTO_TRADE = "auto_trade"
    const val KEY_TESTNET = "use_testnet"
    const val KEY_TP_RATIO = "tp_ratio"
    const val KEY_SL_RATIO = "sl_ratio"

    // Trading App Packages (for detection)
    val TRADING_PACKAGES = listOf(
        "net.metaquotes.metatrader5",
        "net.metaquotes.metatrader4",
        "com.metatrader5.android",
        "com.metatrader4.android",
        "com.tradingview.tradingviewapp",
        "com.binance.dev",
        "com.bybit.app",
        "pro.xfutures",
        "com.audaque.icetower.tf"
    )

    // Notification IDs
    const val NOTIF_FOREGROUND_SERVICE = 1001
    const val NOTIF_TRADE_RESULT = 1002
    const val NOTIF_SIGNAL = 1003
}
