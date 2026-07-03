package com.nanda.ai.trading.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nanda.ai.trading.NandaApplication
import com.nanda.ai.trading.R
import com.nanda.ai.trading.databinding.ActivityMainBinding
import com.nanda.ai.trading.overlay.FloatingBallService
import com.nanda.ai.trading.trading.OrderManager
import com.nanda.ai.trading.utils.Constants
import com.nanda.ai.trading.utils.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val TAG = "NANDA::Main"
    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionHelper: PermissionHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionHelper = PermissionHelper(this)

        setupUI()
        checkFirstTimeSetup()
        loadSettings()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "NANDA AI Trading"

        // Overlay toggle
        binding.switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (permissionHelper.canDrawOverlays()) {
                    startFloatingService()
                } else {
                    permissionHelper.requestOverlayPermission(this)
                    binding.switchOverlay.isChecked = false
                }
            } else {
                stopFloatingService()
            }
        }

        // Auto-trade toggle
        binding.switchAutoTrade.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanPref(Constants.KEY_AUTO_TRADE, isChecked)
            Toast.makeText(this, if (isChecked) "Auto-trading AKTIF" else "Auto-trading NON-AKTIF", Toast.LENGTH_SHORT).show()
        }

        // Testnet toggle
        binding.switchTestnet.setOnCheckedChangeListener { _, isChecked ->
            saveBooleanPref(Constants.KEY_TESTNET, isChecked)
            Toast.makeText(this, if (isChecked) "Testnet AKTIF" else "Live Trading AKTIF", Toast.LENGTH_SHORT).show()
        }

        // Risk slider
        binding.sliderRisk.addOnChangeListener { _, value, _ ->
            binding.tvRiskValue.text = "${value.toInt()}%"
            saveFloatPref(Constants.KEY_RISK_PERCENT, value)
        }

        // Leverage slider
        binding.sliderLeverage.addOnChangeListener { _, value, _ ->
            binding.tvLeverageValue.text = "${value.toInt()}x"
            saveIntPref(Constants.KEY_LEVERAGE, value.toInt())
        }

        // Symbol selector
        binding.etSymbol.setText(Constants.DEFAULT_SYMBOL)

        // Timeframe selector
        val timeframes = arrayOf("1 menit", "5 menit", "15 menit", "30 menit", "1 jam", "4 jam", "1 hari")
        val timeframeValues = arrayOf("1", "5", "15", "30", "60", "240", "D")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeframes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeframe.adapter = adapter
        binding.spinnerTimeframe.setSelection(2) // Default 15m

        // Save settings button
        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        // Start overlay button
        binding.btnStartOverlay.setOnClickListener {
            if (permissionHelper.canDrawOverlays()) {
                startFloatingService()
                binding.switchOverlay.isChecked = true
            } else {
                permissionHelper.requestOverlayPermission(this)
            }
        }

        // Test API button
        binding.btnTestApi.setOnClickListener {
            testAPIConnection()
        }

        // Open API settings
        binding.btnApiSettings.setOnClickListener {
            showApiSettingsDialog()
        }

        // Trade history button
        binding.btnTradeHistory.setOnClickListener {
            showTradeHistory()
        }
    }

    private fun checkFirstTimeSetup() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_time", true)) {
            startActivity(Intent(this, SetupActivity::class.java))
            prefs.edit().putBoolean("first_time", false).apply()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        binding.switchOverlay.isChecked = FloatingBallService.isRunning
        binding.switchAutoTrade.isChecked = prefs.getBoolean(Constants.KEY_AUTO_TRADE, false)
        binding.switchTestnet.isChecked = prefs.getBoolean(Constants.KEY_TESTNET, true)

        val risk = prefs.getFloat(Constants.KEY_RISK_PERCENT, Constants.DEFAULT_RISK_PERCENT.toFloat())
        binding.sliderRisk.value = risk
        binding.tvRiskValue.text = "${risk.toInt()}%"

        val leverage = prefs.getInt(Constants.KEY_LEVERAGE, Constants.DEFAULT_LEVERAGE)
        binding.sliderLeverage.value = leverage.toFloat()
        binding.tvLeverageValue.text = "${leverage}x"

        val symbol = prefs.getString(Constants.KEY_SYMBOL, Constants.DEFAULT_SYMBOL) ?: Constants.DEFAULT_SYMBOL
        binding.etSymbol.setText(symbol)

        val timeframe = prefs.getString(Constants.KEY_TIMEFRAME, Constants.DEFAULT_TIMEFRAME) ?: Constants.DEFAULT_TIMEFRAME
        val tfIndex = when(timeframe) {
            "1" -> 0; "5" -> 1; "15" -> 2; "30" -> 3; "60" -> 4; "240" -> 5; "D" -> 6
            else -> 2
        }
        binding.spinnerTimeframe.setSelection(tfIndex)
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()

        prefs.putString(Constants.KEY_SYMBOL, binding.etSymbol.text.toString().uppercase())

        val timeframeValues = arrayOf("1", "5", "15", "30", "60", "240", "D")
        prefs.putString(Constants.KEY_TIMEFRAME, timeframeValues[binding.spinnerTimeframe.selectedItemPosition])

        prefs.putFloat(Constants.KEY_RISK_PERCENT, binding.sliderRisk.value)
        prefs.putInt(Constants.KEY_LEVERAGE, binding.sliderLeverage.value.toInt())
        prefs.putBoolean(Constants.KEY_AUTO_TRADE, binding.switchAutoTrade.isChecked)
        prefs.putBoolean(Constants.KEY_TESTNET, binding.switchTestnet.isChecked)

        prefs.apply()

        Toast.makeText(this, "Pengaturan disimpan!", Toast.LENGTH_SHORT).show()

        // Restart service if running
        if (FloatingBallService.isRunning) {
            stopFloatingService()
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingBallService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        FloatingBallService.isRunning = true
        Toast.makeText(this, "NANDA AI Overlay aktif!", Toast.LENGTH_SHORT).show()
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingBallService::class.java)
        stopService(intent)
        FloatingBallService.isRunning = false
    }

    private fun testAPIConnection() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val bybitAPI = (application as NandaApplication).bybitAPI
                val price = bybitAPI.getLatestPrice("XAUUSD")
                if (price != null) {
                    Toast.makeText(this@MainActivity, "Bybit API OK! Price: $price", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Bybit API: Gagal mengambil data", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun showApiSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_api_settings, null)
        val etBybitKey = dialogView.findViewById<EditText>(R.id.etBybitKey)
        val etBybitSecret = dialogView.findViewById<EditText>(R.id.etBybitSecret)
        val etMoonshotKey = dialogView.findViewById<EditText>(R.id.etMoonshotKey)

        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        etBybitKey.setText(prefs.getString(Constants.KEY_API_BYBIT_KEY, ""))
        etBybitSecret.setText(prefs.getString(Constants.KEY_API_BYBIT_SECRET, ""))
        etMoonshotKey.setText(prefs.getString(Constants.KEY_API_MOONSHOT_KEY, ""))

        AlertDialog.Builder(this)
            .setTitle("API Settings")
            .setView(dialogView)
            .setPositiveButton("Simpan") { _, _ ->
                prefs.edit()
                    .putString(Constants.KEY_API_BYBIT_KEY, etBybitKey.text.toString().trim())
                    .putString(Constants.KEY_API_BYBIT_SECRET, etBybitSecret.text.toString().trim())
                    .putString(Constants.KEY_API_MOONSHOT_KEY, etMoonshotKey.text.toString().trim())
                    .apply()
                Toast.makeText(this, "API Keys disimpan!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showTradeHistory() {
        val intent = Intent(this, TradeHistoryActivity::class.java)
        startActivity(intent)
    }

    private fun saveBooleanPref(key: String, value: Boolean) {
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(key, value).apply()
    }

    private fun saveFloatPref(key: String, value: Float) {
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(key, value).apply()
    }

    private fun saveIntPref(key: String, value: Int) {
        getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(key, value).apply()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_setup -> {
                startActivity(Intent(this, SetupActivity::class.java))
                true
            }
            R.id.menu_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Tentang NANDA AI")
            .setMessage("NANDA AI Trading Agent v1.0\n\nAI-powered trading assistant dengan analisa real-time dan eksekusi otomatis via Bybit API.\n\nFitur:\n- Floating ball overlay\n- AI Analysis (Moonshot/Kimi)\n- Auto-trading\n- Risk management\n- Trade history\n\nDibuat untuk trader Indonesia.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
