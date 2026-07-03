package com.nanda.ai.trading.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager

class PermissionHelper(private val context: Context) {

    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                AlertDialog.Builder(context)
                    .setTitle("Izin Overlay Diperlukan")
                    .setMessage("NANDA AI memerlukan izin untuk menampilkan floating ball di atas aplikasi trading. Mohon aktifkan di pengaturan.")
                    .setPositiveButton("Buka Pengaturan") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        activity.startActivityForResult(intent, REQUEST_OVERLAY)
                    }
                    .setNegativeButton("Batal", null)
                    .show()
            }
        }
    }

    fun isAccessibilityEnabled(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    fun requestAccessibilityPermission(activity: Activity) {
        AlertDialog.Builder(context)
            .setTitle("Izin Accessibility Diperlukan")
            .setMessage("NANDA AI memerlukan izin Accessibility Service untuk mendeteksi saat Anda membuka aplikasi trading (MT5, TradingView, dll).\n\nMohon aktifkan 'NANDA AI Trading Detector' di daftar layanan accessibility.")
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                activity.startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION
            )
        }
    }

    fun checkAllPermissions(activity: Activity): Boolean {
        var allGranted = true

        if (!canDrawOverlays()) {
            requestOverlayPermission(activity)
            allGranted = false
        }

        if (!isAccessibilityEnabled()) {
            requestAccessibilityPermission(activity)
            allGranted = false
        }

        if (!hasNotificationPermission()) {
            requestNotificationPermission(activity)
        }

        return allGranted
    }

    companion object {
        const val REQUEST_OVERLAY = 1001
        const val REQUEST_NOTIFICATION = 1002
        const val REQUEST_ACCESSIBILITY = 1003
    }
}
