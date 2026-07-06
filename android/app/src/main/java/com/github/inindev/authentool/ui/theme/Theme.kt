package com.github.inindev.authentool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun AppColorTheme(
    colorScheme: CustomColorScheme = SunriseColorScheme,
    content: @Composable () -> Unit
) {
    val material3ColorScheme = colorScheme.toMaterial3ColorScheme()

    // provide the custom scheme via CompositionLocal
    CompositionLocalProvider(
        LocalCustomColorScheme provides colorScheme
    ) {
        // apply Material 3 theme for compatibility
        MaterialTheme(
            colorScheme = material3ColorScheme,
            typography = Typography,
            content = content
        )
    }
}

// helper to access the custom color scheme
val MaterialTheme.customColorScheme: CustomColorScheme
    @Composable
    @ReadOnlyComposable
    get() = LocalCustomColorScheme.current