package com.example.gamepadledcontroller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gamepadledcontroller.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingStartService = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Все разрешения получены", Toast.LENGTH_SHORT).show()
            if (pendingStartService) {
                startLedService()
                pendingStartService = false
            }
        } else {
            Toast.makeText(this, "Некоторые разрешения не предоставлены", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()

        binding.btnStartService.setOnClickListener {
            if (hasAllPermissions()) {
                startLedService()
            } else {
                pendingStartService = true
                checkPermissions()
            }
        }

        binding.btnStopService.setOnClickListener {
            stopLedService()
        }

        setupModeSpinner()
    }

    private fun setupModeSpinner() {
        val modes = resources.getStringArray(R.array.effects)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMode.adapter = adapter
        binding.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedMode = modes[position]
                val intent = Intent(this@MainActivity, LedService::class.java).apply {
                    action = LedService.ACTION_CHANGE_MODE
                    putExtra(LedService.EXTRA_MODE, selectedMode)
                }
                startService(intent)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun startLedService() {
        val intent = Intent(this, LedService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Сервис запущен", Toast.LENGTH_SHORT).show()
    }

    private fun stopLedService() {
        val intent = Intent(this, LedService::class.java)
        stopService(intent)
        Toast.makeText(this, "Сервис остановлен", Toast.LENGTH_SHORT).show()
    }

    private fun hasAllPermissions(): Boolean {
        val requiredPermissions = getRequiredPermissions()
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissions() {
        val requiredPermissions = getRequiredPermissions()
        val needRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needRequest.isNotEmpty()) {
            permissionLauncher.launch(needRequest.toTypedArray())
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        return permissions
    }
}
