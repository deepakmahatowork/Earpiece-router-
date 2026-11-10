package com.example.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * EarpieceOverlayService
 *
 * Persistent foreground service that conditionally maintains proximity sensor persistence
 * ONLY while 'activate to earpiece' mode is enabled.
 *
 * Implements:
 * 1. Proximity SensorEventListener management: Strictly registered only while active,
 *    and unconditionally unregistered upon termination to conserve battery.
 * 2. PROXIMITY_SCREEN_OFF_WAKE_LOCK management to dim display when held near ear.
 * 3. Status bar protection overlay: Applies touch-shield flags (FLAG_LAYOUT_NO_LIMITS,
 *    FLAG_LAYOUT_IN_SCREEN) to prevent status bar pull-downs from interrupting hardware state.
 */
class EarpieceOverlayService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "EarpieceOverlayService"
        const val CHANNEL_ID = "earpiece_router_channel"
        const val NOTIFICATION_ID = 2026

        const val ACTION_START = "com.example.sensor.ACTION_START"
        const val ACTION_STOP = "com.example.sensor.ACTION_STOP"

        data class ProximityTelemetry(
            val isServiceRunning: Boolean = false,
            val isNearEar: Boolean = false,
            val distanceCm: Float = 5.0f,
            val maxRangeCm: Float = 5.0f,
            val isSensorPresent: Boolean = false,
            val isOverlayActive: Boolean = false
        )

        private val _proximityTelemetry = MutableStateFlow(ProximityTelemetry())
        val proximityTelemetry: StateFlow<ProximityTelemetry> = _proximityTelemetry.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, EarpieceOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, EarpieceOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var powerManager: PowerManager? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null
    private var windowManager: WindowManager? = null
    private var overlayShieldView: View? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "EarpieceOverlayService created")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.i(TAG, "onStartCommand received action: $action")

        if (action == ACTION_STOP) {
            stopForegroundAndTearDown()
            return START_NOT_STICKY
        }

        // Start foreground service with persistent status notification
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Register proximity sensor listener conditionally
        registerProximityListener()

        // Acquire hardware proximity wake lock
        acquireProximityWakeLock()

        // Apply status bar touch-shield overlay
        attachStatusBarProtectionOverlay()

        _proximityTelemetry.value = _proximityTelemetry.value.copy(
            isServiceRunning = true,
            isSensorPresent = proximitySensor != null,
            maxRangeCm = proximitySensor?.maximumRange ?: 5f
        )

        return START_STICKY
    }

    private fun registerProximityListener() {
        proximitySensor?.let { sensor ->
            // Strictly register listener with SENSOR_DELAY_NORMAL
            val registered = sensorManager?.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            ) ?: false
            Log.i(TAG, "Proximity SensorEventListener registered: $registered")
        } ?: run {
            Log.w(TAG, "No physical TYPE_PROXIMITY sensor found on device")
        }
    }

    private fun unregisterProximityListener() {
        try {
            sensorManager?.unregisterListener(this)
            Log.i(TAG, "Proximity SensorEventListener strictly unregistered to conserve battery")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering proximity listener", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val isNear = distance < maxRange && distance < 5f

            Log.d(TAG, "Proximity changed: distance=$distance cm, max=$maxRange cm, isNear=$isNear")

            _proximityTelemetry.value = _proximityTelemetry.value.copy(
                isNearEar = isNear,
                distanceCm = distance,
                maxRangeCm = maxRange
            )

            updateOverlayState(isNear)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun acquireProximityWakeLock() {
        try {
            val pm = powerManager ?: return
            if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                if (proximityWakeLock == null) {
                    proximityWakeLock = pm.newWakeLock(
                        PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                        "EarpieceRouter:ProximityWakeLock"
                    ).apply {
                        setReferenceCounted(false)
                        acquire()
                        Log.i(TAG, "Proximity screen-off wake lock acquired")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring proximity wake lock", e)
        }
    }

    private fun releaseProximityWakeLock() {
        try {
            proximityWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.i(TAG, "Proximity screen-off wake lock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing proximity wake lock", e)
        } finally {
            proximityWakeLock = null
        }
    }

    /**
     * Attaches status bar protection overlay to prevent notifications shade or status bar
     * touch interruptions from interfering with the hardware proximity state.
     */
    private fun attachStatusBarProtectionOverlay() {
        if (overlayShieldView != null) return

        try {
            // Check if application has permission to draw system overlays
            val canDraw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }

            if (canDraw && windowManager != null) {
                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                // Invisible touch-filtering shield over top status bar
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    1, // Minimal 1px trigger height
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP
                }

                val shield = View(this).apply {
                    setBackgroundColor(0x00000000) // 100% transparent
                }

                windowManager?.addView(shield, params)
                overlayShieldView = shield
                _proximityTelemetry.value = _proximityTelemetry.value.copy(isOverlayActive = true)
                Log.i(TAG, "Status bar protection overlay view attached")
            } else {
                Log.d(TAG, "Overlay drawing not granted or not needed; relying on window flags")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not attach overlay view (handled gracefully)", e)
        }
    }

    private fun removeStatusBarProtectionOverlay() {
        try {
            overlayShieldView?.let { view ->
                windowManager?.removeView(view)
                Log.i(TAG, "Status bar protection overlay view removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay view", e)
        } finally {
            overlayShieldView = null
            _proximityTelemetry.value = _proximityTelemetry.value.copy(isOverlayActive = false)
        }
    }

    private fun updateOverlayState(isNear: Boolean) {
        // Adjust overlay properties dynamically when near ear
        overlayShieldView?.let { view ->
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            // When near ear, expand shield to cover full screen to prevent accidental touches
            params.height = if (isNear) WindowManager.LayoutParams.MATCH_PARENT else 1
            try {
                windowManager?.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating overlay layout", e)
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, EarpieceOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Earpiece Router Active")
            .setContentText("Hardware routed to earpiece • Proximity persistence ON")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(0xFF00E676.toInt()) // Terminal Emerald Green
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Deactivate", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Earpiece Hardware Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps audio routing and proximity sensor persistent during earpiece mode"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundAndTearDown() {
        unregisterProximityListener()
        releaseProximityWakeLock()
        removeStatusBarProtectionOverlay()

        _proximityTelemetry.value = ProximityTelemetry(
            isServiceRunning = false,
            isNearEar = false,
            distanceCm = 5.0f,
            maxRangeCm = 5.0f,
            isSensorPresent = proximitySensor != null,
            isOverlayActive = false
        )

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "EarpieceOverlayService stopped and cleaned up")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundAndTearDown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
