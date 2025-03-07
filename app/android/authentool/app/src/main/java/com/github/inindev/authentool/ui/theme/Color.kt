package com.github.inindev.authentool.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// app colors palette
val Black = Color.Black                      // text/icons on app background (light), top bar text (light)
val White = Color.White                      // app background (light)
val SoftWhite = Color(0xFFE0E0E0)      // text/icons on app background (dark), top bar text (dark)
val DarkGray = Color(0xFF121212)       // app background (dark)
val SoftBlack = Color(0xFF49454F)      // card name text (light), card name text when highlighted (light and dark)
val LightGrayish = Color(0xFFCAC4D0)   // card name text (dark)
val DeepBlue = Color(0xFF1976D2)       // card totp text (light), card totp text when highlighted (light), progress fill (light)
val LightBlue = Color(0xFF40C4FF)      // card totp text (dark), card background highlight (light), progress fill (dark)
val LightGray = Color(0xFFF5F5F5)      // card background (light)
val MediumDarkGray = Color(0xFF2C2C2C) // card background (dark), card totp text when highlighted (dark)
val OliveDrab = Color(0xFF5B8655)      // card background highlight (dark)
val MediumGray = Color(0xFFE0E0E0)     // progress track (light), top bar background (light)
val DarkerGray = Color(0xFF616161)     // progress track (dark)
val VeryDarkGray = Color(0xFF1E1E1E)   // top bar background (dark)
// experimental colors (unused)
val DarkGoldenRod = Color(0xFFB8860B)  // experimental, unused
val SlateGray = Color(0xFF708090)      // experimental, unused

/**
 * Custom color scheme for Authentool
 */
data class CustomColorScheme(
    val AppText: Color,
    val AppBackground: Color,
    val CardName: Color,
    val CardTotp: Color,
    val CardBackground: Color,
    val CardHiName: Color,
    val CardHiTotp: Color,
    val CardHiBackground: Color,
    val ProgressTrack: Color,
    val ProgressFill: Color,
    val TopBarBackground: Color,
    val TopBarText: Color
)

val LightColorScheme = CustomColorScheme(
    AppText = Black,
    AppBackground = White,
    CardName = SoftBlack,
    CardTotp = DeepBlue,
    CardBackground = LightGray,
    CardHiName = SoftBlack,
    CardHiTotp = DeepBlue,
    CardHiBackground = LightBlue,
    ProgressTrack = MediumGray,
    ProgressFill = DeepBlue,
    TopBarBackground = MediumGray,
    TopBarText = Black
)

val DarkColorScheme = CustomColorScheme(
    AppText = SoftWhite,
    AppBackground = DarkGray,
    CardName = LightGrayish,
    CardTotp = LightBlue,
    CardBackground = MediumDarkGray,
    CardHiName = SoftBlack,
    CardHiTotp = MediumDarkGray,
    CardHiBackground = OliveDrab,
    ProgressTrack = DarkerGray,
    ProgressFill = LightBlue,
    TopBarBackground = VeryDarkGray,
    TopBarText = SoftWhite
)

// composition Local to provide CustomColorScheme
val LocalCustomColorScheme = compositionLocalOf { LightColorScheme }

/**
 * Maps CustomColorScheme to Material 3’s ColorScheme
 */
fun CustomColorScheme.toMaterial3ColorScheme(): androidx.compose.material3.ColorScheme {
    return if (this == LightColorScheme) {
        lightColorScheme(
            primary = this.CardTotp,              // CardTotp maps to primary
            onPrimary = this.ProgressTrack,       // ProgressTrack as onPrimary
            secondary = this.TopBarBackground,    // TopBarBackground as secondary
            onSecondary = this.TopBarText,        // TopBarText as onSecondary
            tertiary = this.CardHiBackground,     // CardHiBackground as tertiary
            onTertiary = this.CardHiName,         // CardHiName as onTertiary
            surface = this.AppBackground,         // AppBackground as surface
            onSurface = this.AppText,             // AppText as onSurface
            surfaceVariant = this.CardBackground, // CardBackground as surfaceVariant
            onSurfaceVariant = this.CardName      // CardName as onSurfaceVariant
        )
    } else {
        darkColorScheme(
            primary = this.CardTotp,
            onPrimary = this.ProgressTrack,
            secondary = this.TopBarBackground,
            onSecondary = this.TopBarText,
            tertiary = this.CardHiBackground,
            onTertiary = this.CardHiName,
            surface = this.AppBackground,
            onSurface = this.AppText,
            surfaceVariant = this.CardBackground,
            onSurfaceVariant = this.CardName
        )
    }
}
