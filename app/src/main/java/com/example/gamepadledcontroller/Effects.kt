package com.example.gamepadledcontroller

import kotlin.math.*
import kotlin.random.Random

class Effects {
    private var lastUpdate = 0L
    private var phase = 0f
    private var blinkState = false

    fun red(): Int = 0xFF0000.toInt()
    fun green(): Int = 0x00FF00.toInt()
    fun blue(): Int = 0x0000FF.toInt()
    fun white(): Int = 0xFFFFFF.toInt()

    fun rgbCycle(): Int {
        phase += 0.02f
        if (phase > 1f) phase -= 1f
        val hue = phase * 360f
        return hslToRgb(hue, 1f, 0.5f)
    }

    fun smoothRgbCycle(): Int {
        phase += 0.005f
        if (phase > 1f) phase -= 1f
        val hue = phase * 360f
        return hslToRgb(hue, 1f, 0.5f)
    }

    fun rainbow(): Int {
        phase += 0.01f
        if (phase > 1f) phase -= 1f
        val hue = phase * 360f
        return hslToRgb(hue, 1f, 0.5f)
    }

    fun blink(): Int {
        val now = System.currentTimeMillis()
        if (now - lastUpdate > 500) {
            blinkState = !blinkState
            lastUpdate = now
        }
        return if (blinkState) 0xFFFFFF else 0x000000
    }

    fun police(): Int {
        val now = System.currentTimeMillis()
        val period = 150L
        val step = (now / period) % 4
        return when (step) {
            0L, 2L -> 0xFF0000  // красный
            1L, 3L -> 0x0000FF  // синий
            else -> 0x000000
        }
    }

    fun random(): Int = Random.nextInt(0xFFFFFF)

    fun breathing(): Int {
        val now = System.currentTimeMillis()
        val brightness = (sin(now / 1000.0 * PI) + 1) / 2
        val base = 0x00FF00  // зелёный
        return adjustBrightness(base, brightness)
    }

    private fun adjustBrightness(color: Int, factor: Double): Int {
        val r = ((color shr 16 and 0xFF) * factor).toInt()
        val g = ((color shr 8 and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    private fun hslToRgb(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1 - abs(2 * lightness - 1)) * saturation
        val h = hue / 60f
        val x = c * (1 - abs(h % 2 - 1))
        val (r1, g1, b1) = when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            5 -> Triple(c, 0f, x)
            else -> Triple(0f, 0f, 0f)
        }
        val m = lightness - c / 2
        val r = ((r1 + m) * 255).toInt()
        val g = ((g1 + m) * 255).toInt()
        val b = ((b1 + m) * 255).toInt()
        return (r shl 16) or (g shl 8) or b
    }
}
