package com.example.gamepadledcontroller

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.*

class MusicAnalyzer {
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val tag = "MusicAnalyzer"

    init {
        startRecording()
    }

    private fun startRecording() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
                isRecording = true
                Log.d(tag, "AudioRecord initialized and started")
            } else {
                Log.e(tag, "AudioRecord initialization failed")
                isRecording = false
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting AudioRecord", e)
            isRecording = false
        }
    }

    fun getMusicColor(): Int {
        if (!isRecording) return 0x00FF00

        try {
            val buffer = ShortArray(bufferSize)
            val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            if (read <= 0) return 0x00FF00

            val doubleBuffer = DoubleArray(bufferSize)
            for (i in 0 until bufferSize) {
                doubleBuffer[i] = buffer[i].toDouble()
            }

            val fftResult = fft(doubleBuffer)

            var low = 0.0   // басы (20-250 Hz)
            var mid = 0.0   // средние (250-4000 Hz)
            var high = 0.0  // высокие (4000-20000 Hz)

            for (i in 0 until bufferSize / 2) {
                val freq = i * sampleRate.toDouble() / bufferSize
                val magnitude = fftResult[i]
                when {
                    freq < 250 -> low += magnitude
                    freq < 4000 -> mid += magnitude
                    else -> high += magnitude
                }
            }

            val maxVal = maxOf(low, mid, high)
            val r = (low / maxVal * 255).toInt().coerceIn(0, 255)
            val g = (mid / maxVal * 255).toInt().coerceIn(0, 255)
            val b = (high / maxVal * 255).toInt().coerceIn(0, 255)

            return (r shl 16) or (g shl 8) or b
        } catch (e: Exception) {
            Log.e(tag, "Error analyzing music", e)
            return 0x00FF00
        }
    }

    // Простая реализация FFT (Cooley–Tukey)
    private fun fft(x: DoubleArray): DoubleArray {
        val n = x.size
        val real = DoubleArray(n) { x[it] }
        val imag = DoubleArray(n) { 0.0 }

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n - 1) {
            var bit = n / 2
            while (j >= bit) {
                j -= bit
                bit /= 2
            }
            j += bit
            if (i < j) {
                val tempReal = real[i]
                val tempImag = imag[i]
                real[i] = real[j]
                imag[i] = imag[j]
                real[j] = tempReal
                imag[j] = tempImag
            }
        }

        // FFT
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wlenReal = cos(angle)
            val wlenImag = sin(angle)
            for (i in 0 until n step len) {
                var wReal = 1.0
                var wImag = 0.0
                for (j in 0 until len / 2) {
                    val uReal = real[i + j]
                    val uImag = imag[i + j]
                    val vReal = real[i + j + len / 2] * wReal - imag[i + j + len / 2] * wImag
                    val vImag = real[i + j + len / 2] * wImag + imag[i + j + len / 2] * wReal
                    real[i + j] = uReal + vReal
                    imag[i + j] = uImag + vImag
                    real[i + j + len / 2] = uReal - vReal
                    imag[i + j + len / 2] = uImag - vImag
                    val newWReal = wReal * wlenReal - wImag * wlenImag
                    val newWImag = wReal * wlenImag + wImag * wlenReal
                    wReal = newWReal
                    wImag = newWImag
                }
            }
            len *= 2
        }

        // Возвращаем magnitude
        val magnitude = DoubleArray(n / 2)
        for (i in 0 until n / 2) {
            magnitude[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
        return magnitude
    }

    fun release() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioRecord", e)
        }
    }
}
