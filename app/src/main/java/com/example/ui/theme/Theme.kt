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
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color from wallpaper (Android 12+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // Fallback to custom dark theme
        darkTheme -> darkColorScheme(
            primary = Color(0xFF6C63FF),
            secondary = Color(0xFF4ECDC4),
            background = Color(0xFF0D0D0F),
            surface = Color(0xFF1A1A1F)
        )
        else -> lightColorScheme(
            primary = Color(0xFF6C63FF),
            background = Color(0xFFF5F5F8),
            surface = Color(0xFFFFFFFF)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Enabled by default as requested
    primaryColorHex: String? = null,
    content: @Composable () -> Unit,
) {
    val parsedColor = androidx.compose.runtime.remember(primaryColorHex, darkTheme) {
        if (!primaryColorHex.isNullOrEmpty()) {
            try {
                Color(android.graphics.Color.parseColor(primaryColorHex))
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            if (parsedColor != null) {
                DarkColorScheme.copy(primary = parsedColor)
            } else {
                DarkColorScheme
            }
        }
        else -> {
            if (parsedColor != null) {
                LightColorScheme.copy(primary = parsedColor)
            } else {
                LightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
