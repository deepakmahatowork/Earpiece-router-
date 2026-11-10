package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.sensor.EarpieceOverlayService
import com.example.ui.CallScreen
import com.example.ui.CallViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * MainActivity
 *
 * Hosts the Earpiece Router user interface.
 * Coordinates with EarpieceOverlayService to apply proper window flags (FLAG_LAYOUT_NO_LIMITS,
 * FLAG_KEEP_SCREEN_ON) and brightness overrides to prevent the status bar from interrupting
 * hardware proximity states while earpiece mode is active.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: CallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Observe earpiece activation to manage persistent service and window flags
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state.isEarpieceRouteActive) {
                    applyActiveEarpieceWindowFlags()
                    EarpieceOverlayService.start(this@MainActivity)
                } else {
                    clearActiveEarpieceWindowFlags()
                    EarpieceOverlayService.stop(this@MainActivity)
                }
            }
        }

        // Observe proximity near/far changes to prevent status bar interruptions
        lifecycleScope.launch {
            EarpieceOverlayService.proximityTelemetry.collectLatest { telemetry ->
                if (telemetry.isServiceRunning && telemetry.isNearEar) {
                    // Dim screen and prevent accidental touch pulls
                    val lp = window.attributes
                    lp.screenBrightness = 0.01f
                    window.attributes = lp
                } else {
                    val lp = window.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    window.attributes = lp
                }
            }
        }

        setContent {
            MyApplicationTheme {
                CallScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun applyActiveEarpieceWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun clearActiveEarpieceWindowFlags() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppLifecycleStart()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppLifecycleStop()
    }
}
