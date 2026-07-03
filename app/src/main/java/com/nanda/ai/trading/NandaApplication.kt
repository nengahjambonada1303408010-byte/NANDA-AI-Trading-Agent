package com.nanda.ai.trading

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import com.nanda.ai.trading.data.AppDatabase
import com.nanda.ai.trading.trading.BybitAPI
import com.nanda.ai.trading.ai.MoonshotAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NandaApplication : Application(), Configuration.Provider {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Lazy singletons
    val database by lazy { AppDatabase.getDatabase(this) }
    val bybitAPI by lazy { BybitAPI(this) }
    val moonshotAI by lazy { MoonshotAI(this) }

    companion object {
        const val CHANNEL_TRADE = "nanda_trade_notifications"
        const val CHANNEL_SIGNAL = "nanda_signal_notifications"
        const val CHANNEL_SYSTEM = "nanda_system_notifications"
        
        lateinit var instance: NandaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Trade execution channel
            val tradeChannel = NotificationChannel(
                CHANNEL_TRADE,
                "Trade Executions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi eksekusi trading"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
            }

            // Trading signals channel
            val signalChannel = NotificationChannel(
                CHANNEL_SIGNAL,
                "Trading Signals",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Sinyal trading dari AI"
            }

            // System channel
            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "System",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi sistem NANDA AI"
            }

            manager.createNotificationChannel(tradeChannel)
            manager.createNotificationChannel(signalChannel)
            manager.createNotificationChannel(systemChannel)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
