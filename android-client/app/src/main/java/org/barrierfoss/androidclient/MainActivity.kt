package org.barrierfoss.androidclient

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.barrierfoss.androidclient.data.ConnectionConfig
import org.barrierfoss.androidclient.data.SettingsRepository
import org.barrierfoss.androidclient.databinding.ActivityMainBinding
import org.barrierfoss.androidclient.service.BarrierAccessibilityService
import org.barrierfoss.androidclient.service.BarrierConnectionService

/*
 * Módulo Android desarrollado por Izai Alejandro Zalles Merino (zallesrene@gmail.com)
 * Este crédito debe preservarse en versiones derivadas de este módulo Android.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)
        loadSettings()

        binding.accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.overlayButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            startActivity(intent)
        }

        binding.startButton.setOnClickListener {
            startBarrierClient()
        }

        binding.stopButton.setOnClickListener {
            stopBarrierClient()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
    }

    private fun loadSettings() {
        val config = settingsRepository.load()
        binding.serverHostInput.setText(config.serverHost)
        binding.serverPortInput.setText(config.serverPort.toString())
        binding.screenNameInput.setText(config.screenName)
    }

    private fun refreshStatuses() {
        val accessibilityEnabled = BarrierAccessibilityService.isEnabled(this)
        binding.accessibilityStatus.text = if (accessibilityEnabled) {
            getString(R.string.status_accessibility_on)
        } else {
            getString(R.string.status_accessibility_off)
        }

        val overlayEnabled = Settings.canDrawOverlays(this)
        binding.overlayStatus.text = if (overlayEnabled) {
            getString(R.string.status_overlay_on)
        } else {
            getString(R.string.status_overlay_off)
        }
    }

    private fun startBarrierClient() {
        val host = binding.serverHostInput.text?.toString()?.trim().orEmpty()
        val port = binding.serverPortInput.text?.toString()?.trim()?.toIntOrNull()
            ?: ConnectionConfig.DEFAULT.serverPort
        val screenName = binding.screenNameInput.text?.toString()?.trim().orEmpty()

        val config = ConnectionConfig(
            serverHost = host,
            serverPort = port,
            screenName = screenName,
        )

        if (!config.isValid()) {
            Toast.makeText(this, "Configura host, puerto y nombre de pantalla válidos.", Toast.LENGTH_LONG)
                .show()
            return
        }

        settingsRepository.save(config)

        val intent = Intent(this, BarrierConnectionService::class.java).apply {
            action = BarrierConnectionService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "Cliente Barrier iniciado.", Toast.LENGTH_SHORT).show()
    }

    private fun stopBarrierClient() {
        val intent = Intent(this, BarrierConnectionService::class.java).apply {
            action = BarrierConnectionService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "Cliente Barrier detenido.", Toast.LENGTH_SHORT).show()
    }
}
