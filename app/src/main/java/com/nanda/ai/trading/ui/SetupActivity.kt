package com.nanda.ai.trading.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nanda.ai.trading.NandaApplication
import com.nanda.ai.trading.R
import com.nanda.ai.trading.utils.Constants
import com.nanda.ai.trading.utils.PermissionHelper
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var permissionHelper: PermissionHelper
    private var currentStep = 0

    private lateinit var tvStepTitle: TextView
    private lateinit var tvStepDescription: TextView
    private lateinit var btnNext: Button
    private lateinit var btnPrev: Button
    private lateinit var btnAction: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutApiInput: View
    private lateinit var etBybitKey: EditText
    private lateinit var etBybitSecret: EditText
    private lateinit var etMoonshotKey: EditText

    private val steps = listOf(
        Step("Selamat Datang", "NANDA AI Trading Agent akan membantu Anda trading dengan analisa AI otomatis."),
        Step("Izin Overlay", "Aplikasi memerlukan izin untuk menampilkan floating ball di atas aplikasi trading."),
        Step("Izin Accessibility", "Izin ini digunakan untuk mendeteksi saat Anda membuka aplikasi trading."),
        Step("API Bybit", "Masukkan API Key dan Secret dari akun Bybit Anda."),
        Step("API Moonshot AI", "Masukkan API Key untuk analisa AI (opsional, bisa nanti)."),
        Step("Selesai", "Setup selesai! NANDA AI siap digunakan.")
    )

    data class Step(val title: String, val description: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        permissionHelper = PermissionHelper(this)

        tvStepTitle = findViewById(R.id.tvStepTitle)
        tvStepDescription = findViewById(R.id.tvStepDescription)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnAction = findViewById(R.id.btnAction)
        progressBar = findViewById(R.id.progressBar)
        layoutApiInput = findViewById(R.id.layoutApiInput)
        etBybitKey = findViewById(R.id.etBybitKey)
        etBybitSecret = findViewById(R.id.etBybitSecret)
        etMoonshotKey = findViewById(R.id.etMoonshotKey)

        btnNext.setOnClickListener { nextStep() }
        btnPrev.setOnClickListener { prevStep() }
        btnAction.setOnClickListener { handleAction() }

        updateStep()
    }

    private fun updateStep() {
        val step = steps[currentStep]
        tvStepTitle.text = "${currentStep + 1}/${steps.size}: ${step.title}"
        tvStepDescription.text = step.description
        progressBar.progress = ((currentStep + 1) * 100) / steps.size

        btnPrev.visibility = if (currentStep > 0) View.VISIBLE else View.GONE

        layoutApiInput.visibility = View.GONE
        btnAction.visibility = View.GONE

        when (currentStep) {
            0 -> {
                btnNext.visibility = View.VISIBLE
                btnNext.text = "Mulai Setup"
            }
            1 -> {
                btnNext.visibility = View.GONE
                btnAction.visibility = View.VISIBLE
                btnAction.text = "Berikan Izin Overlay"
            }
            2 -> {
                btnNext.visibility = View.GONE
                btnAction.visibility = View.VISIBLE
                btnAction.text = "Berikan Izin Accessibility"
            }
            3 -> {
                btnNext.visibility = View.GONE
                btnAction.visibility = View.VISIBLE
                btnAction.text = "Simpan API Bybit"
                layoutApiInput.visibility = View.VISIBLE
            }
            4 -> {
                btnNext.visibility = View.GONE
                btnAction.visibility = View.VISIBLE
                btnAction.text = "Simpan API Moonshot"
                layoutApiInput.visibility = View.VISIBLE
                etBybitKey.visibility = View.GONE
                etBybitSecret.visibility = View.GONE
                etMoonshotKey.visibility = View.VISIBLE
            }
            5 -> {
                btnNext.visibility = View.VISIBLE
                btnNext.text = "Selesai"
                btnAction.visibility = View.GONE
            }
        }
    }

    private fun handleAction() {
        when (currentStep) {
            1 -> {
                permissionHelper.requestOverlayPermission(this)
                currentStep++
                updateStep()
            }
            2 -> {
                permissionHelper.requestAccessibilityPermission(this)
                currentStep++
                updateStep()
            }
            3 -> {
                val key = etBybitKey.text.toString().trim()
                val secret = etBybitSecret.text.toString().trim()
                if (key.isNotEmpty() && secret.isNotEmpty()) {
                    getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(Constants.KEY_API_BYBIT_KEY, key)
                        .putString(Constants.KEY_API_BYBIT_SECRET, secret)
                        .apply()
                    Toast.makeText(this, "API Bybit disimpan!", Toast.LENGTH_SHORT).show()
                    currentStep++
                    updateStep()
                } else {
                    Toast.makeText(this, "Mohon isi API Key dan Secret", Toast.LENGTH_SHORT).show()
                }
            }
            4 -> {
                val key = etMoonshotKey.text.toString().trim()
                getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
                    .putString(Constants.KEY_API_MOONSHOT_KEY, key)
                    .apply()
                Toast.makeText(this, "API Moonshot disimpan!", Toast.LENGTH_SHORT).show()
                currentStep++
                updateStep()
            }
        }
    }

    private fun nextStep() {
        if (currentStep < steps.size - 1) {
            currentStep++
            updateStep()
        } else {
            finishSetup()
        }
    }

    private fun prevStep() {
        if (currentStep > 0) {
            currentStep--
            updateStep()
        }
    }

    private fun finishSetup() {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean("setup_complete", true)
            .apply()

        Toast.makeText(this, "Setup selesai! NANDA AI siap digunakan.", Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onBackPressed() {
        if (currentStep > 0) {
            prevStep()
        } else {
            super.onBackPressed()
        }
    }
}
