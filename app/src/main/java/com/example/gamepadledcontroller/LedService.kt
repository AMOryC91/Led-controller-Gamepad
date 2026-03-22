package com.example.gamepadledcontroller

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class LedService : Service() {

    private val binder = LocalBinder()
    private lateinit var bluetoothController: BluetoothController
    private lateinit var effects: Effects
    private lateinit var musicAnalyzer: MusicAnalyzer
    private lateinit var batteryMonitor: BatteryMonitor
    private var serviceJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private var currentMode: String = "Красный"

    companion object {
        const val ACTION_CHANGE_MODE = "com.example.gamepadledcontroller.CHANGE_MODE"
        const val EXTRA_MODE = "mode"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "led_controller_channel"
    }

    inner class LocalBinder : Binder() {
        fun getService(): LedService = this@LedService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        bluetoothController = BluetoothController(this)
        effects = Effects()
        musicAnalyzer = MusicAnalyzer()
        batteryMonitor = BatteryMonitor(this)

        // Регистрируем ресивер для автозапуска при перезагрузке
        val filter = IntentFilter(Intent.ACTION_BOOT_COMPLETED)
        registerReceiver(bootReceiver, filter)

        // Регистрируем ресивер для смены режима из Activity
        val modeFilter = IntentFilter(ACTION_CHANGE_MODE)
        registerReceiver(modeChangeReceiver, modeFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHANGE_MODE) {
            val newMode = intent.getStringExtra(EXTRA_MODE)
            if (newMode != null) {
                currentMode = newMode
                updateNotification()
            }
            return START_STICKY
        }

        if (!isRunning.get()) {
            startLedControl()
        }
        return START_STICKY
    }

    private fun startLedControl() {
        isRunning.set(true)
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning.get()) {
                val color = when (currentMode) {
                    "Красный" -> effects.red()
                    "Зеленый" -> effects.green()
                    "Синий" -> effects.blue()
                    "Белый" -> effects.white()
                    "Переливание RGB" -> effects.rgbCycle()
                    "Красивое переливание" -> effects.smoothRgbCycle()
                    "Радуга" -> effects.rainbow()
                    "Мигание" -> effects.blink()
                    "Режим ПОЛИЦИЯ" -> effects.police()
                    "Подсветка от музыки" -> musicAnalyzer.getMusicColor()
                    "Цвет от батареи" -> batteryMonitor.getBatteryColor()
                    "Random режим" -> effects.random()
                    "Breathing эффект" -> effects.breathing()
                    else -> effects.red()
                }
                bluetoothController.sendColor(color)
                delay(50) // частота обновления ~20 fps
            }
        }
    }

    private fun stopLedControl() {
        isRunning.set(false)
        serviceJob?.cancel()
        bluetoothController.disconnect()
    }

    override fun onDestroy() {
        stopLedControl()
        musicAnalyzer.release()
        unregisterReceiver(bootReceiver)
        unregisterReceiver(modeChangeReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Управление LED геймпада",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления о работе сервиса LED"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Controller Led GAMEPAD")
            .setContentText("Режим: $currentMode")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private val bootReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                val startIntent = Intent(context, LedService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent)
                } else {
                    context.startService(startIntent)
                }
            }
        }
    }

    private val modeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_CHANGE_MODE) {
                val newMode = intent.getStringExtra(EXTRA_MODE)
                if (newMode != null) {
                    currentMode = newMode
                    updateNotification()
                }
            }
        }
    }
}
