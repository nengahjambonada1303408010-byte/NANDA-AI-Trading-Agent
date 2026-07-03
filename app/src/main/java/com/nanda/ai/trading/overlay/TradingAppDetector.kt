package com.nanda.ai.trading.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nanda.ai.trading.utils.Constants

class TradingAppDetector : AccessibilityService() {

    private val TAG = "NANDA::Detector"
    private var lastPackage = ""

    companion object {
        var isTradingAppOpen = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Trading App Detector aktif")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            if (packageName != lastPackage) {
                lastPackage = packageName
                Log.d(TAG, "Window changed: $packageName")

                val isTradingApp = Constants.TRADING_PACKAGES.any {
                    packageName.contains(it, ignoreCase = true)
                }

                if (isTradingApp && !isTradingAppOpen) {
                    isTradingAppOpen = true
                    showFloatingBall()
                } else if (!isTradingApp && isTradingAppOpen) {
                    isTradingAppOpen = false
                    hideFloatingBall()
                }
            }
        }
    }

    private fun showFloatingBall() {
        Log.i(TAG, "Trading app detected, showing floating ball")
        val intent = Intent(this, FloatingBallService::class.java).apply {
            action = FloatingBallService.ACTION_SHOW
        }
        startService(intent)

        // Update notification
        sendBroadcast(Intent("com.nanda.ai.trading.TRADING_APP_OPEN"))
    }

    private fun hideFloatingBall() {
        Log.i(TAG, "Trading app closed, hiding floating ball")
        val intent = Intent(this, FloatingBallService::class.java).apply {
            action = FloatingBallService.ACTION_HIDE
        }
        startService(intent)

        sendBroadcast(Intent("com.nanda.ai.trading.TRADING_APP_CLOSE"))
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isTradingAppOpen = false
    }
}
