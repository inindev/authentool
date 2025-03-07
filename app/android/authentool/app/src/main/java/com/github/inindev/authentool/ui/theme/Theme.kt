package com.github.inindev.authentool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun AppColorTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val customColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val material3ColorScheme = customColorScheme.toMaterial3ColorScheme()

    // provide the custom scheme via CompositionLocal
    androidx.compose.runtime.CompositionLocalProvider(
        LocalCustomColorScheme provides customColorScheme
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
