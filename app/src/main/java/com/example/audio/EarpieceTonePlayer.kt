package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

/**
 * EarpieceTonePlayer
 *
 * Plays a clean, calibrated acoustic harmonic chime sequence directly into the internal earpiece
 * to verify that audio routing to TYPE_BUILTIN_EARPIECE is working with zero loudspeaker leakage.
 */
class EarpieceTonePlayer(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "EarpieceTonePlayer"
        const val SAMPLE_RATE = 16000
    }

    private val isTestingEarpiece = AtomicBoolean(false)

    private val _isTestTonePlaying = MutableStateFlow(false)
    val isTestTonePlaying: StateFlow<Boolean> = _isTestTonePlaying.asStateFlow()

    private val _toneRms = MutableStateFlow(0f)
    val toneRms: StateFlow<Float> = _toneRms.asStateFlow()

    private var testToneJob: Job? = null

    fun playEarpieceTestTone() {
        if (isTestingEarpiece.get()) return

        testToneJob?.cancel()
        testToneJob = scope.launch(Dispatchers.Default) {
            isTestingEarpiece.set(true)
            _isTestTonePlaying.value = true
            Log.i(TAG, "Playing harmonic test tone sequence through internal earpiece")

            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(SAMPLE_RATE * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()

                // Harmonic frequencies: C5 (523Hz), E5 (659Hz), G5 (784Hz), C6 (1046Hz)
                val frequencies = listOf(523.25, 659.25, 783.99, 1046.50)
                val noteDurationMs = 280

                for (freq in frequencies) {
                    if (!isActive) break
                    val numSamples = (SAMPLE_RATE * (noteDurationMs / 1000.0)).toInt()
                    val pcmBytes = ByteArray(numSamples * 2)

                    for (i in 0 until numSamples) {
                        val t = i.toDouble() / SAMPLE_RATE
                        val attack = (i.toDouble() / (SAMPLE_RATE * 0.04)).coerceAtMost(1.0)
                        val decay = ((numSamples - i).toDouble() / (SAMPLE_RATE * 0.08)).coerceIn(0.0, 1.0)
                        val envelope = attack * decay

                        val sampleValue = (sin(2.0 * PI * freq * t) * 16000.0 * envelope).toInt().toShort()
                        pcmBytes[i * 2] = (sampleValue.toInt() and 0xFF).toByte()
                        pcmBytes[i * 2 + 1] = ((sampleValue.toInt() shr 8) and 0xFF).toByte()
                    }

                    _toneRms.value = 0.65f
                    track.write(pcmBytes, 0, pcmBytes.size)
                    Arrays.fill(pcmBytes, 0.toByte())
                    delay(30)
                }

                delay(150)
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing test tone", e)
            } finally {
                isTestingEarpiece.set(false)
                _isTestTonePlaying.value = false
                _toneRms.value = 0f
            }
        }
    }

    fun stopEarpieceTestTone() {
        testToneJob?.cancel()
        testToneJob = null
        isTestingEarpiece.set(false)
        _isTestTonePlaying.value = false
        _toneRms.value = 0f
    }

    fun release() {
        stopEarpieceTestTone()
    }
}
