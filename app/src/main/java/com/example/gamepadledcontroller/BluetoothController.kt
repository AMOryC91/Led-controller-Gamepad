package com.example.gamepadledcontroller

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothController(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null
    private var hidReportCharacteristic: BluetoothGattCharacteristic? = null
    private val isConnected = AtomicBoolean(false)
    private var connectJob: Job? = null
    private var isScanning = false
    private var activeScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var activeScanCallback: ScanCallback? = null

    // Диагностика для UI
    @Volatile private var connectedDeviceName: String? = null
    @Volatile private var hidServiceFound = false
    @Volatile private var reportCharFound = false
    @Volatile private var lastWriteOk: Boolean? = null

    fun getDiagnostics(): String {
        val adapter = bluetoothAdapter
        if (adapter == null) return "Bluetooth не поддерживается устройством"
        if (!adapter.isEnabled) return "Bluetooth выключен"

        return buildString {
            append(if (isScanning) "Идёт поиск устройства...\n" else "Поиск не идёт\n")
            append(if (isConnected.get()) "Статус: подключено к ${connectedDeviceName ?: "?"}\n" else "Статус: не подключено\n")
            if (isConnected.get()) {
                append(if (hidServiceFound) "HID-сервис (0x1812): найден\n" else "HID-сервис (0x1812): НЕ найден — этот геймпад не отдаёт LED через это BLE-подключение\n")
                if (hidServiceFound) {
                    append(if (reportCharFound) "Характеристика Report: найдена\n" else "Характеристика Report: НЕ найдена\n")
                }
                if (reportCharFound) {
                    append(
                        when (lastWriteOk) {
                            true -> "Передача цвета: команда уходит без ошибок"
                            false -> "Передача цвета: ошибка записи"
                            null -> "Передача цвета: ещё не пробовали"
                        }
                    )
                }
            }
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to read bonded devices", e)
            emptyList()
        }
    }

    fun connectManually(device: BluetoothDevice) {
        stopScan()
        connect(device)
    }

    fun rescan() {
        disconnect()
        startScan()
    }

    private fun stopScan() {
        if (isScanning) {
            try {
                activeScanCallback?.let { activeScanner?.stopScan(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
            }
        }
        isScanning = false
    }

    companion object {
        private const val TAG = "BluetoothController"
        val HID_SERVICE_UUID = ParcelUuid(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"))
        val REPORT_CHAR_UUID = ParcelUuid(UUID.fromString("00002A4D-0000-1000-8000-00805f9b34fb"))
    }

    init {
        // Проверки и запуск сканирования, если все условия выполнены
        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter is null")
        } else if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission, cannot start scan")
        } else {
            startScan()
        }
    }

    private fun startScan() {
        if (isScanning) return
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Cannot start scan: Bluetooth not available")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot start scan: missing BLUETOOTH_SCAN permission")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "Cannot start scan: BluetoothLeScanner is null")
            return
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission, cannot read device info")
                    return
                }
                val device = result.device
                val name = device.name ?: ""
                if (name.contains("gamepad", ignoreCase = true) ||
                    name.contains("controller", ignoreCase = true) ||
                    name.contains("dualshock", ignoreCase = true) ||
                    name.contains("xbox", ignoreCase = true)
                ) {
                    Log.d(TAG, "Найден возможный геймпад: $name")
                    connect(device)
                    scanner.stopScan(this)
                    isScanning = false
                }
            }
        }

        activeScanner = scanner
        activeScanCallback = scanCallback

        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, scanSettings, scanCallback)
        isScanning = true
        Log.d(TAG, "Started BLE scan")
    }

    private fun connect(device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot connect: missing BLUETOOTH_CONNECT permission")
            return
        }
        connectedDeviceName = try { device.name } catch (e: SecurityException) { null }
        hidServiceFound = false
        reportCharFound = false
        lastWriteOk = null
        connectJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to device", e)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Подключено к геймпаду")
                    isConnected.set(true)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Отключено от геймпада")
                    isConnected.set(false)
                    hidServiceFound = false
                    reportCharFound = false
                    lastWriteOk = null
                    connectJob?.cancel()
                    startScan()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val hidService = gatt.getService(HID_SERVICE_UUID.uuid)
                if (hidService != null) {
                    hidServiceFound = true
                    val reportChar = hidService.getCharacteristic(REPORT_CHAR_UUID.uuid)
                    if (reportChar != null) {
                        hidReportCharacteristic = reportChar
                        reportCharFound = true
                        Log.d(TAG, "Найден HID Report characteristic")
                    } else {
                        reportCharFound = false
                        Log.e(TAG, "Report characteristic не найден")
                    }
                } else {
                    hidServiceFound = false
                    Log.e(TAG, "HID service не найден")
                }
            }
        }
    }

    fun sendColor(rgb: Int) {
        if (!isConnected.get() || hidReportCharacteristic == null) return

        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF

        val report = byteArrayOf(0x02, red.toByte(), green.toByte(), blue.toByte())

        try {
            hidReportCharacteristic?.let { characteristic ->
                characteristic.value = report
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                val started = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
                lastWriteOk = started
            }
        } catch (e: Exception) {
            lastWriteOk = false
            Log.e(TAG, "Error sending color", e)
        }
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
        isConnected.set(false)
        hidServiceFound = false
        reportCharFound = false
        lastWriteOk = null
    }
}
