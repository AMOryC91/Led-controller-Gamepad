package com.example.gamepadledcontroller

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class BluetoothController(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null
    private var hidReportCharacteristic: BluetoothGattCharacteristic? = null
    private val isConnected = AtomicBoolean(false)
    private var connectJob: Job? = null

    companion object {
        private const val TAG = "BluetoothController"
        // HID Service UUID (стандартный для BLE HID)
        val HID_SERVICE_UUID = ParcelUuid(UUID.fromString("00001812-0000-1000-8000-00805f9b34fb"))
        // Report Characteristic UUID (стандартный для HID)
        val REPORT_CHAR_UUID = ParcelUuid(UUID.fromString("00002A4D-0000-1000-8000-00805f9b34fb"))
    }

    init {
        startScan()
    }

    private fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth не включен")
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: ""
                // Ищем устройства с именем, содержащим "gamepad", "controller", "dualshock", "xbox"
                if (name.contains("gamepad", ignoreCase = true) ||
                    name.contains("controller", ignoreCase = true) ||
                    name.contains("dualshock", ignoreCase = true) ||
                    name.contains("xbox", ignoreCase = true)
                ) {
                    Log.d(TAG, "Найден возможный геймпад: $name")
                    connect(device)
                    scanner.stopScan(this)
                }
            }
        }

        val scanSettings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, scanSettings, scanCallback)
    }

    private fun connect(device: BluetoothDevice) {
        connectJob = CoroutineScope(Dispatchers.IO).launch {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
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
                    // Попробовать переподключиться
                    connectJob?.cancel()
                    startScan()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val hidService = gatt.getService(HID_SERVICE_UUID.uuid)
                if (hidService != null) {
                    val reportChar = hidService.getCharacteristic(REPORT_CHAR_UUID.uuid)
                    if (reportChar != null) {
                        hidReportCharacteristic = reportChar
                        Log.d(TAG, "Найден HID Report characteristic")
                    } else {
                        Log.e(TAG, "Report characteristic не найден")
                    }
                } else {
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

        // Формируем HID Output Report. Предполагаем reportId = 0x02 (для некоторых геймпадов)
        val report = byteArrayOf(0x02, red.toByte(), green.toByte(), blue.toByte())

        hidReportCharacteristic?.let { characteristic ->
            characteristic.value = report
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            bluetoothGatt?.writeCharacteristic(characteristic)
        }
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        isConnected.set(false)
    }
}
