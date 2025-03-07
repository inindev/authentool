package com.github.inindev.authentool.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val White = Color.White                      // app background (light)
val Black = Color.Black                      // text/icons on app background (light), card TOTP (dark highlight, unused)
val LightGray = Color(0xFFF5F5F5)      // card background (light normal)
val SoftBlack = Color(0xFF49454F)      // card name (light normal and highlight), card name (dark highlight)
val DeepBlue = Color(0xFF1976D2)       // progress bar, card TOTP (light normal and highlight)
val MediumGray = Color(0xFFE0E0E0)     // progress track (light), top app bar background (light)
val LightBlue = Color(0xFF40C4FF)      // card background (light highlight), progress bar, card TOTP (dark normal)
val DarkGray = Color(0xFF121212)       // app background (dark)
val SoftWhite = Color(0xFFE0E0E0)      // text/icons on app background (dark)
val MediumDarkGray = Color(0xFF2C2C2C) // card background (dark normal), card TOTP (dark highlight)
val LightGrayish = Color(0xFFCAC4D0)   // card name (dark normal)
val DarkerGray = Color(0xFF616161)     // progress track (dark)
val VeryDarkGray = Color(0xFF1E1E1E)   // top app bar background (dark)
val DarkGoldenRod = Color(0xFFB8860B)  // unused, experimental
val OliveDrab = Color(0xFF5B8655)      // card background (dark highlight)
val SlateGray = Color(0xFF708090)      // unused, experimental

/**
 * Light color scheme for day mode.
 */
val LightColorScheme = lightColorScheme(
    surface = White,                 // app background
    onSurface = Black,               // text/icons on app background
    surfaceVariant = LightGray,      // card background (normal)
    onSurfaceVariant = SoftBlack,    // card name (normal and highlighted)
    primary = DeepBlue,              // progress bar, card TOTP (normal and highlighted)
    onPrimary = MediumGray,          // progress track
    secondary = MediumGray,          // top app bar background
    tertiary = LightBlue,            // card background (highlighted)
    onTertiary = DeepBlue            // card TOTP (highlighted)
)

/**
 * Dark color scheme for night mode.
 */
val DarkColorScheme = darkColorScheme(
    surface = DarkGray,              // app background
    onSurface = SoftWhite,           // text/icons on app background
    surfaceVariant = MediumDarkGray, // card background (normal)
    onSurfaceVariant = LightGrayish, // card name (normal)
    primary = LightBlue,             // progress bar, card TOTP (normal)
    onPrimary = DarkerGray,          // progress track
    secondary = VeryDarkGray,        // top app bar background
    tertiary = OliveDrab,            // card background (highlighted)
    onTertiary = SoftBlack           // card name (highlighted)
)
