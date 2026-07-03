package com.nanda.ai.trading.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nanda.ai.trading.utils.Constants

class BootReceiver : BroadcastReceiver() {

    private val TAG = "NANDA::Boot"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed, starting NANDA AI service")

            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean("auto_start", true)

            if (autoStart) {
                val serviceIntent = Intent(context, FloatingBallService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.i(TAG, "NANDA AI service started on boot")
            }
        }
    }
}
