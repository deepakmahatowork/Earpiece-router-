package com.example.sensor

import android.content.Context
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Battery & Sensor Management Controller
 *
 * Implements:
 * 1. Proximity Screen-Off WakeLock: Turns off screen when phone is held to ear during active call,
 *    preventing accidental touch inputs and saving display battery power.
 * 2. CPU Partial WakeLock with maximum call duration guard (15 minutes) to prevent leaks.
 * 3. 3-minute idle silence auto-disconnect watchdog: hangs up when neither user nor Gemini
 *    speaks for 180 seconds to protect radio and battery.
 */
class BatteryAndSensorController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onIdleTimeout: () -> Unit
) {
    companion object {
        private const val TAG = "BatterySensorController"
        private const val MAX_CALL_TIMEOUT_MS = 15 * 60 * 1000L // 15-minute hard safety cap
        private const val IDLE_SILENCE_LIMIT_SECONDS = 180 // 3 minutes of silence
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    private var idleWatchdogJob: Job? = null
    private var lastActivityTimestamp = System.currentTimeMillis()

    private val _idleSeconds = MutableStateFlow(0)
    val idleSeconds: StateFlow<Int> = _idleSeconds.asStateFlow()

    private val _isProximitySupported = MutableStateFlow(false)
    val isProximitySupported: StateFlow<Boolean> = _isProximitySupported.asStateFlow()

    init {
        try {
            val supported = powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)
            _isProximitySupported.value = supported
            Log.i(TAG, "Proximity Screen-Off WakeLock supported: $supported")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query proximity wakelock support", e)
        }
    }

    /**
     * Call started: acquire proximity screen-off and CPU wake locks with safety timeouts.
     */
    fun onCallStarted() {
        acquireProximityWakeLock()
        acquireCpuWakeLock()
        startIdleWatchdog()
    }

    /**
     * Call stopped: safely release all locks and cancel timers.
     */
    fun onCallEnded() {
        releaseProximityWakeLock()
        releaseCpuWakeLock()
        stopIdleWatchdog()
    }

    /**
     * Notify activity (user speech or model audio response) to reset idle silence timer.
     */
    fun notifyAudioActivity() {
        lastActivityTimestamp = System.currentTimeMillis()
        _idleSeconds.value = 0
    }

    private fun acquireProximityWakeLock() {
        try {
            if (_isProximitySupported.value && proximityWakeLock == null) {
                proximityWakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "GeminiLive:ProximityScreenOff"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                    Log.i(TAG, "Proximity screen-off wakelock acquired")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring proximity wakelock", e)
        }
    }

    private fun releaseProximityWakeLock() {
        try {
            proximityWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "Proximity screen-off wakelock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing proximity wakelock", e)
        } finally {
            proximityWakeLock = null
        }
    }

    private fun acquireCpuWakeLock() {
        try {
            if (cpuWakeLock == null) {
                cpuWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "GeminiLive:CpuKeepAlive"
                ).apply {
                    setReferenceCounted(false)
                    // Maximum call cap timeout guard
                    acquire(MAX_CALL_TIMEOUT_MS)
                    Log.i(TAG, "CPU wakelock acquired with ${MAX_CALL_TIMEOUT_MS / 60000}m timeout guard")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring CPU wakelock", e)
        }
    }

    private fun releaseCpuWakeLock() {
        try {
            cpuWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "CPU wakelock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing CPU wakelock", e)
        } finally {
            cpuWakeLock = null
        }
    }

    private fun startIdleWatchdog() {
        stopIdleWatchdog()
        lastActivityTimestamp = System.currentTimeMillis()
        _idleSeconds.value = 0

        idleWatchdogJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val elapsedSeconds = ((System.currentTimeMillis() - lastActivityTimestamp) / 1000L).toInt()
                _idleSeconds.value = elapsedSeconds

                if (elapsedSeconds >= IDLE_SILENCE_LIMIT_SECONDS) {
                    Log.w(TAG, "Silence timeout reached ($elapsedSeconds s). Auto-disconnecting to save battery.")
                    onIdleTimeout()
                    break
                }
            }
        }
    }

    private fun stopIdleWatchdog() {
        idleWatchdogJob?.cancel()
        idleWatchdogJob = null
        _idleSeconds.value = 0
    }

    fun onLifecycleStop() {
        // If app goes to background during call, ensure proximity lock doesn't stay stuck
        releaseProximityWakeLock()
    }

    fun onLifecycleStart(isCallActive: Boolean) {
        if (isCallActive) {
            acquireProximityWakeLock()
        }
    }

    fun release() {
        onCallEnded()
    }
}
