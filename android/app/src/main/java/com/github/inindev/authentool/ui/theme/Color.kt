package com.github.inindev.authentool.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// frost colors palette (light)
val Black = Color.Black                // app text, top bar text
val White = Color.White                // app background
val SoftBlack = Color(0xFF49454F)      // card name text, card name text when highlighted
val DeepBlue = Color(0xFF1976D2)       // card totp text, card totp text when highlighted, progress fill
val LightBlue = Color(0xFF40C4FF)      // card highlight background
val LightGray = Color(0xFFF5F5F5)      // card background
val MediumGray = Color(0xFFE0E0E0)     // progress track, top bar background

// sunrise colors palette (light)
val DarkNavy = Color(0xFF1A1A2E)       // app text, top bar text
val VividBlue = Color(0xFF3D45AA)      // card totp text
val GoldenYellow = Color(0xFFFFE89A)   // card highlight background
val WarmOrange = Color(0xFFF8843F)     // progress fill
val DarkBlue = Color(0xFF2A2F78)       // card name text when highlighted
val RedOrange = Color(0xFFDA3D20)      // card totp text when highlighted
val WarmGray = Color(0xFFF0EDE6)       // card background
val BlueGray = Color(0xFFDDDFE8)       // top bar background
val WarmTrack = Color(0xFFE0DDD6)      // progress track

// midnight colors palette (dark)
val SoftWhite = Color(0xFFE0E0E0)      // app text, top bar text
val DarkGray = Color(0xFF121212)       // app background
val LightGrayish = Color(0xFFCAC4D0)   // card name text
val MediumDarkGray = Color(0xFF2C2C2C) // card background, card totp text when highlighted
val OliveDrab = Color(0xFF5B8655)      // card highlight background
val DarkerGray = Color(0xFF616161)     // progress track
val VeryDarkGray = Color(0xFF1E1E1E)   // top bar background

// espresso colors palette (dark)
val EspressoBrown = Color(0xFF120D08)  // app background
val TopBarBrown = Color(0xFF1E1610)    // top bar background
val DarkBrown = Color(0xFF412D15)      // card background, card highlight name text
val DeepBrown = Color(0xFF332310)      // card highlight totp text
val Tan = Color(0xFFEED9B9)            // card totp text, app text, top bar text
val MutedTan = Color(0xFFCBB99A)       // card name text
val Khaki = Color(0xFFB5A07E)          // card highlight background
val DarkKhaki = Color(0xFF8A7555)      // progress fill
val DarkLoam = Color(0xFF1E1812)       // progress track

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
    val TopBarText: Color,
    val isDark: Boolean = false
)

val FrostColorScheme = CustomColorScheme(
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

val SunriseColorScheme = CustomColorScheme(
    AppText = DarkNavy,
    AppBackground = White,
    CardName = SoftBlack,
    CardTotp = VividBlue,
    CardBackground = WarmGray,
    CardHiName = DarkBlue,
    CardHiTotp = RedOrange,
    CardHiBackground = GoldenYellow,
    ProgressTrack = WarmTrack,
    ProgressFill = WarmOrange,
    TopBarBackground = BlueGray,
    TopBarText = DarkNavy
)

val MidnightColorScheme = CustomColorScheme(
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
    TopBarText = SoftWhite,
    isDark = true
)

val EspressoColorScheme = CustomColorScheme(
    AppText = Tan,
    AppBackground = EspressoBrown,
    CardName = MutedTan,
    CardTotp = Tan,
    CardBackground = DarkBrown,
    CardHiName = DarkBrown,
    CardHiTotp = DeepBrown,
    CardHiBackground = Khaki,
    ProgressTrack = DarkLoam,
    ProgressFill = DarkKhaki,
    TopBarBackground = TopBarBrown,
    TopBarText = Tan,
    isDark = true
)

// composition Local to provide CustomColorScheme
val LocalCustomColorScheme = compositionLocalOf { SunriseColorScheme }

/**
 * Maps CustomColorScheme to Material 3's ColorScheme
 */
fun CustomColorScheme.toMaterial3ColorScheme(): ColorScheme {
    return if (isDark) {
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
    } else {
        lightColorScheme(
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