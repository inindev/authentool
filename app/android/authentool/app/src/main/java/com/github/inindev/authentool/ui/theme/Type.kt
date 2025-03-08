package com.github.inindev.authentool.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.inindev.authentool.R

private val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold)
)

val Typography = Typography(
    // App title in TopAppBar
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp, // accessible size
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // Card names
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp, // accessible size
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    // TOTP digits - use Lato here
    displayLarge = TextStyle(
        fontFamily = Lato,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp, // accessible large text
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    // Dialog titles
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp, // accessible size
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    ),
    // Button text and error messages
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp, // accessible size
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )
)
