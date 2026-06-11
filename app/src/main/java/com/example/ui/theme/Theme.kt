package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AppPrimary,
    secondary = AppPrimaryVariant,
    background = AppBackground,
    surface = AppSurface,
    onPrimary = AppOnPrimary,
    onSecondary = AppOnPrimary,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    primaryContainer = AppSurface,
    onPrimaryContainer = AppTextPrimary,
    secondaryContainer = AppSurface,
    onSecondaryContainer = AppTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = CrimsonPrimary,
    secondary = CrimsonSecondary,
    background = Color(0xFFFAF9F6), // Warm sand background
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = CharcoalDark,
    onSurface = CharcoalDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable to preserve custom brand styles
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
