package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonCoral
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.StealthBackground
import com.example.ui.theme.StealthBorder
import com.example.ui.theme.StealthSurface
import com.example.ui.theme.TerminalEmerald
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    viewModel: CallViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = StealthBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Terminal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_emerald_earpiece_1794293380313),
                        contentDescription = "Earpiece Icon",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, TerminalEmerald.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (state.isEarpieceRouteActive) TerminalEmerald else AmberWarning)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EARPIECE ROUTER",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = if (state.isEarpieceRouteActive) "Earpiece route active • Overlay running" else "Standby • Proximity listener paused",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (state.isEarpieceRouteActive) TerminalEmerald else TextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.setDiagnosticsSheetVisible(true) },
                    modifier = Modifier.testTag("diagnostics_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Diagnostics",
                        tint = TerminalEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Routing Standby / Active Status Bar
            TerminalStatusBar(
                isRouteActive = state.isEarpieceRouteActive,
                activeRoute = state.activeAudioRoute,
                onReassert = { viewModel.forceReassertEarpiece() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Master Route Card (Single, Prominent Button)
            TerminalMasterCard(
                isRouteActive = state.isEarpieceRouteActive,
                durationSeconds = state.sessionDurationSeconds,
                onToggle = { viewModel.toggleEarpieceRoute() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Proximity Sensor & Overlay Hardware State Card (Dynamic UI/UX)
            ProximityHardwareCard(
                isRouteActive = state.isEarpieceRouteActive,
                isNearEar = state.isNearEar,
                distanceCm = state.proximityDistanceCm,
                isOverlayProtected = state.isOverlayProtected,
                isSensorPresent = state.isProximitySupported
            )
        }

        // Diagnostics Bottom Sheet
        if (state.showDiagnosticsSheet) {
            DiagnosticsBottomSheet(
                state = state,
                onDismiss = { viewModel.setDiagnosticsSheetVisible(false) },
                onReassert = { viewModel.forceReassertEarpiece() }
            )
        }
    }
}

/**
 * Terminal Status Bar: Crystal-clear distinction between STANDBY and ACTIVE
 */
@Composable
private fun TerminalStatusBar(
    isRouteActive: Boolean,
    activeRoute: String,
    onReassert: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = StealthSurface,
        border = BorderStroke(
            1.dp,
            if (isRouteActive) TerminalEmerald.copy(alpha = 0.6f) else AmberWarning.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .clickable { onReassert() }
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isRouteActive) Icons.Filled.Security else Icons.Outlined.Shield,
                contentDescription = null,
                tint = if (isRouteActive) TerminalEmerald else AmberWarning,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRouteActive) {
                    "ACTIVE ROUTE: $activeRoute"
                } else {
                    "STANDBY: $activeRoute"
                },
                color = if (isRouteActive) TextPrimary else AmberWarning,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (isRouteActive) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Re-assert",
                    tint = TerminalEmerald,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Master Toggle Card with ONE definitive, full-width button
 */
@Composable
private fun TerminalMasterCard(
    isRouteActive: Boolean,
    durationSeconds: Long,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_route_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = StealthSurface
        ),
        border = BorderStroke(
            width = if (isRouteActive) 2.dp else 1.dp,
            color = if (isRouteActive) TerminalEmerald else StealthBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRouteActive) TerminalEmerald else AmberWarning)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRouteActive) "[ STATE: EARPIECE LOCKED ]" else "[ STATE: ROUTING STANDBY ]",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isRouteActive) TerminalEmerald else AmberWarning
                    )
                }

                if (isRouteActive) {
                    Text(
                        text = formatDuration(durationSeconds),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TerminalEmerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isRouteActive) "EARPIECE ROUTING ENGAGED" else "SYSTEM IN STANDBY MODE",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isRouteActive) TerminalEmerald else TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isRouteActive) {
                    "Hardware audio locked to internal earpiece • Status bar guarded"
                } else {
                    "Audio currently outputs to loudspeaker. Tap below to engage earpiece"
                },
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // The ONLY master button for this task
            Button(
                onClick = onToggle,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRouteActive) CrimsonCoral else TerminalEmerald,
                    contentColor = if (isRouteActive) TextPrimary else StealthBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("toggle_earpiece_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRouteActive) "DEACTIVATE EARPIECE ROUTE" else "ACTIVATE EARPIECE ROUTE",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Dynamic Proximity Sensor & Overlay Hardware Card:
 * Transparently reveals sensor listener status (Active vs Paused for battery)
 */
@Composable
private fun ProximityHardwareCard(
    isRouteActive: Boolean,
    isNearEar: Boolean,
    distanceCm: Float,
    isOverlayProtected: Boolean,
    isSensorPresent: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRouteActive && isNearEar) EmeraldGlow.copy(alpha = 0.25f) else StealthSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isRouteActive) TerminalEmerald.copy(alpha = 0.4f) else StealthBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Sensors,
                        contentDescription = null,
                        tint = if (isRouteActive) TerminalEmerald else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PROXIMITY PERSISTENCE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isRouteActive) TerminalEmerald else TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isRouteActive) TerminalEmerald.copy(alpha = 0.2f) else StealthBorder
                ) {
                    Text(
                        text = if (isRouteActive) "SERVICE: RUNNING" else "LISTENER: PAUSED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isRouteActive) TerminalEmerald else TextSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isRouteActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isNearEar) "• SENSOR: NEAR EAR (DISPLAY SLEEP)" else "• SENSOR: AWAY (${String.format("%.1f", distanceCm)} cm)",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isNearEar) TerminalEmerald else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "• STATUS BAR: SHIELDED AGAINST PULL-DOWNS",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isNearEar) TerminalEmerald else Color(0xFF263238))
                    )
                }
            } else {
                Text(
                    text = "Sensor listener is strictly stopped to conserve battery. Activates automatically when earpiece route is enabled.",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}


/**
 * Diagnostics Bottom Sheet (Terminal style)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsBottomSheet(
    state: CallViewModel.UiState,
    onDismiss: () -> Unit,
    onReassert: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StealthSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 30.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "HARDWARE AUDIO & SENSOR TELEMETRY",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TerminalEmerald
            )
            Text(
                text = "Device endpoints, overlay service & window flags",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = StealthBackground),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, StealthBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "> Active Route: ${state.activeAudioRoute}",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "> Proximity Service: ${if (state.isProximityServiceActive) "RUNNING [Persistent]" else "STOPPED [Battery Conserved]"}",
                        color = if (state.isProximityServiceActive) TerminalEmerald else AmberWarning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "> Proximity State: ${if (state.isNearEar) "NEAR [0.0 cm]" else "AWAY [${String.format("%.1f", state.proximityDistanceCm)} cm]"}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "> Status Bar Protection: ${if (state.isOverlayProtected) "ACTIVE (Touch-shield applied)" else "WINDOW FLAGS ACTIVE"}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (state.availableDevices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Detected Endpoints:",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                state.availableDevices.forEach { dev ->
                    Text(
                        text = "• $dev",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            FilledTonalButton(
                onClick = onReassert,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = StealthBorder,
                    contentColor = TerminalEmerald
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FORCE RE-ASSERT EARPIECE (<200ms)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TerminalEmerald,
                    contentColor = StealthBackground
                )
            ) {
                Text(
                    text = "CLOSE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
