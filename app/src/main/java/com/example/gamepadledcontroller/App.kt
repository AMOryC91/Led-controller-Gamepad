package com.example.gamepadledcontroller

import android.app.Application
import android.util.Log

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Инициализация глобальных компонентов, если требуется
        Log.d("App", "Application created")
    }
}
