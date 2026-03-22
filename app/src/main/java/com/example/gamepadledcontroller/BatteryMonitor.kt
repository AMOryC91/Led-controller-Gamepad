package com.example.gamepadledcontroller

import android.content.Context
import android.os.BatteryManager
import android.os.Build

class BatteryMonitor(private val context: Context) {

    fun getBatteryColor(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            // fallback для старых версий
            50
        }
        return when (level) {
            in 0..15 -> 0xFF0000  // красный
            in 16..30 -> 0xFF3300 // оранжевый
            in 31..60 -> 0xFFFF00 // жёлтый
            in 61..90 -> 0x00FF00 // зелёный
            else -> 0x00AAFF     // синий
        }
    }
}
