package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioRoutingController
import com.example.audio.EarpieceTonePlayer
import com.example.sensor.BatteryAndSensorController
import com.example.sensor.EarpieceOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * CallViewModel / EarpieceRouterViewModel
 *
 * Dedicated controller for strictly routing device audio to the internal earpiece.
 * Integrates persistent proximity sensor overlay service conditionally when active,
 * and ensures sensor listeners are paused when in standby.
 */
class CallViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "CallViewModel"
    }

    data class UiState(
        val isEarpieceRouteActive: Boolean = false,
        val isTestTonePlaying: Boolean = false,
        val earpieceRms: Float = 0f,
        val activeAudioRoute: String = "Loudspeaker (System Normal)",
        val isEarpieceHardened: Boolean = false,
        val routingMessage: String = "Standby: Normal loudspeaker playback",
        val isProximitySupported: Boolean = false,
        val isProximityServiceActive: Boolean = false,
        val isNearEar: Boolean = false,
        val proximityDistanceCm: Float = 5.0f,
        val isOverlayProtected: Boolean = false,
        val sessionDurationSeconds: Long = 0L,
        val availableDevices: List<String> = emptyList(),
        val showDiagnosticsSheet: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val routingController = AudioRoutingController(application, viewModelScope)
    private val tonePlayer = EarpieceTonePlayer(viewModelScope)
    private val sensorController = BatteryAndSensorController(
        context = application,
        scope = viewModelScope,
        onIdleTimeout = {
            // Idle timeout handler if needed
        }
    )

    private var durationTimerJob: Job? = null

    init {
        // Observe Audio Routing Hardware State
        viewModelScope.launch {
            routingController.routingState.collect { route ->
                val activeRouteDisplay = if (route.isSessionActive) {
                    if (route.activeDeviceName.contains("Earpiece", ignoreCase = true)) {
                        route.activeDeviceName
                    } else {
                        "Built-in Earpiece (Enforced)"
                    }
                } else {
                    "Loudspeaker (System Normal)"
                }

                _uiState.update {
                    it.copy(
                        isEarpieceRouteActive = route.isSessionActive,
                        isEarpieceHardened = route.isHardened,
                        activeAudioRoute = activeRouteDisplay,
                        routingMessage = if (route.isSessionActive) {
                            "Audio hardware locked to internal physical earpiece"
                        } else {
                            "Standby: Normal loudspeaker playback"
                        },
                        availableDevices = route.availableDevices
                    )
                }
            }
        }

        // Observe Proximity Overlay Service Telemetry
        viewModelScope.launch {
            EarpieceOverlayService.proximityTelemetry.collect { telemetry ->
                _uiState.update {
                    it.copy(
                        isProximityServiceActive = telemetry.isServiceRunning,
                        isNearEar = telemetry.isNearEar,
                        proximityDistanceCm = telemetry.distanceCm,
                        isOverlayProtected = telemetry.isOverlayActive
                    )
                }
            }
        }

        // Observe Earpiece Test Tone Playing State
        viewModelScope.launch {
            tonePlayer.isTestTonePlaying.collect { playing ->
                _uiState.update { it.copy(isTestTonePlaying = playing) }
            }
        }
        viewModelScope.launch {
            tonePlayer.toneRms.collect { rms ->
                _uiState.update { it.copy(earpieceRms = rms) }
            }
        }

        // Observe Proximity Hardware Support
        viewModelScope.launch {
            sensorController.isProximitySupported.collect { supported ->
                _uiState.update { it.copy(isProximitySupported = supported) }
            }
        }
    }

    /**
     * Master toggle: Route all audio to physical internal earpiece.
     * Guaranteed immediate, reliable state transition.
     */
    fun toggleEarpieceRoute() {
        val currentlyActive = _uiState.value.isEarpieceRouteActive
        Log.i(TAG, "toggleEarpieceRoute clicked! Currently active: $currentlyActive")
        if (currentlyActive) {
            stopEarpieceRouting()
        } else {
            startEarpieceRouting()
        }
    }

    fun startEarpieceRouting() {
        Log.i(TAG, "Starting earpiece routing & persistent overlay service")
        routingController.startEarpieceSession()
        sensorController.onCallStarted()
        EarpieceOverlayService.start(getApplication())
        startDurationTimer()
        _uiState.update { it.copy(isEarpieceRouteActive = true) }
    }

    fun stopEarpieceRouting() {
        Log.i(TAG, "Stopping earpiece routing & tearing down proximity service")
        tonePlayer.stopEarpieceTestTone()
        routingController.stopEarpieceSession()
        sensorController.onCallEnded()
        EarpieceOverlayService.stop(getApplication())
        stopDurationTimer()
        _uiState.update { it.copy(isEarpieceRouteActive = false) }
    }

    /**
     * Play or stop the earpiece test chime sequence.
     */
    fun toggleTestTone() {
        val isPlaying = _uiState.value.isTestTonePlaying
        if (isPlaying) {
            tonePlayer.stopEarpieceTestTone()
        } else {
            if (!_uiState.value.isEarpieceRouteActive) {
                startEarpieceRouting()
            }
            tonePlayer.playEarpieceTestTone()
        }
    }

    fun forceReassertEarpiece() {
        routingController.forceReassertEarpiece()
    }

    fun setDiagnosticsSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showDiagnosticsSheet = visible) }
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = viewModelScope.launch(Dispatchers.Main) {
            var seconds = 0L
            while (isActive) {
                delay(1000L)
                seconds++
                _uiState.update { it.copy(sessionDurationSeconds = seconds) }
            }
        }
    }

    private fun stopDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = null
        _uiState.update { it.copy(sessionDurationSeconds = 0L) }
    }

    fun onAppLifecycleStop() {
        sensorController.onLifecycleStop()
    }

    fun onAppLifecycleStart() {
        sensorController.onLifecycleStart(_uiState.value.isEarpieceRouteActive)
    }

    override fun onCleared() {
        super.onCleared()
        stopEarpieceRouting()
        routingController.release()
        tonePlayer.release()
        sensorController.release()
    }
}
