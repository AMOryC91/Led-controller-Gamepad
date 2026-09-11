package com.example.gamepadledcontroller

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.gamepadledcontroller.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pendingStartService = false

    private var ledService: LedService? = null
    private var isBound = false
    private val statusHandler = Handler(Looper.getMainLooper())
    private val statusPoller = object : Runnable {
        override fun run() {
            binding.tvStatus.text = ledService?.getDiagnostics() ?: "Сервис не запущен"
            statusHandler.postDelayed(this, 1000)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? LedService.LocalBinder
            ledService = binder?.getService()
            isBound = true
            statusHandler.post(statusPoller)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ledService = null
            isBound = false
            statusHandler.removeCallbacks(statusPoller)
        }
    }

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

        binding.btnDevices.setOnClickListener {
            showDevicePicker()
        }

        binding.btnRescan.setOnClickListener {
            ledService?.rescan()
            Toast.makeText(this, "Повторный поиск запущен", Toast.LENGTH_SHORT).show()
        }

        setupModeSpinner()
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, LedService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        statusHandler.removeCallbacks(statusPoller)
    }

    private fun showDevicePicker() {
        val service = ledService
        if (service == null) {
            Toast.makeText(this, "Сначала включи сервис", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Нет разрешения BLUETOOTH_CONNECT", Toast.LENGTH_SHORT).show()
            return
        }
        val devices: List<BluetoothDevice> = service.getPairedDevices()
        if (devices.isEmpty()) {
            Toast.makeText(this, "Нет сопряжённых устройств. Сначала сопряги геймпад в настройках Bluetooth.", Toast.LENGTH_LONG).show()
            return
        }
        val names = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Выбери устройство")
            .setItems(names) { _, which ->
                service.connectToDevice(devices[which])
                Toast.makeText(this, "Подключаюсь к ${names[which]}...", Toast.LENGTH_SHORT).show()
            }
            .show()
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
