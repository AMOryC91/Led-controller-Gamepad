package com.example.gamepadledcontroller

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.sqrt

class MusicAnalyzer {
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val fft = DoubleFFT_1D(bufferSize / 2)

    init {
        startRecording()
    }

    private fun startRecording() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord?.startRecording()
        isRecording = true
    }

    fun getMusicColor(): Int {
        if (!isRecording) return 0x00FF00

        val buffer = ShortArray(bufferSize)
        val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
        if (read <= 0) return 0x00FF00

        val doubleBuffer = DoubleArray(bufferSize)
        for (i in 0 until bufferSize) {
            doubleBuffer[i] = buffer[i].toDouble()
        }

        fft.realForward(doubleBuffer)

        var low = 0.0   // басы (20-250 Hz)
        var mid = 0.0   // средние (250-4000 Hz)
        var high = 0.0  // высокие (4000-20000 Hz)

        for (i in 0 until bufferSize / 2) {
            val freq = i * sampleRate.toDouble() / bufferSize
            val magnitude = sqrt(doubleBuffer[2*i]*doubleBuffer[2*i] + doubleBuffer[2*i+1]*doubleBuffer[2*i+1])
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
    }

    fun release() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
    }
}
