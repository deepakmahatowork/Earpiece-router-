package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val StealthBackground = Color(0xFF0A0B0E)
val StealthSurface    = Color(0xFF14171F)
val StealthBorder     = Color(0xFF232836)

// New Vibrant Primary: Terminal Emerald Green (Clean, tactical, high-contrast)
val TerminalEmerald   = Color(0xFF00E676)
val EmeraldGlow       = Color(0xFF004D28)

// Legacy alias for compatibility
val ElectricCyan      = TerminalEmerald
val CyanGlow          = EmeraldGlow

val CrimsonCoral      = Color(0xFFFF4B4B)
val TextPrimary       = Color(0xFFF1F5F9)
val TextSecondary     = Color(0xFF94A3B8)
val AmberWarning      = Color(0xFFF59E0B)

val EarpieceAppColorScheme = darkColorScheme(
    background = StealthBackground,
    surface = StealthSurface,
    primary = TerminalEmerald,
    onPrimary = StealthBackground,
    secondary = EmeraldGlow,
    onSecondary = TextPrimary,
    error = CrimsonCoral,
    onError = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = StealthBorder,
    onSurfaceVariant = TextSecondary
)
