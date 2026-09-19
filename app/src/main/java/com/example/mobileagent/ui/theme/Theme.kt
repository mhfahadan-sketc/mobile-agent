package com.example.mobileagent.ui.theme

import android.app.Activity
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

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleSoft,
    onPrimaryContainer = InkDark,

    secondary = PurpleDeep,
    onSecondary = Color.White,
    secondaryContainer = PurpleLight,
    onSecondaryContainer = InkDark,

    tertiary = CyanAccent,
    onTertiary = InkDark,
    tertiaryContainer = CyanLight,
    onTertiaryContainer = InkDark,

    background = LightBackground,
    onBackground = InkDark,
    surface = LightSurface,
    onSurface = InkDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = InkSoft,
    outline = LightOutline,
    outlineVariant = LightOutline,

    error = ErrorRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = PurpleLight,
    onPrimary = InkDark,
    primaryContainer = PurpleDeep,
    onPrimaryContainer = Color.White,

    secondary = CyanAccent,
    onSecondary = InkDark,
    secondaryContainer = Color(0xFF2A4A5A),
    onSecondaryContainer = Color.White,

    tertiary = CyanGlow,
    onTertiary = InkDark,
    tertiaryContainer = Color(0xFF1A3A44),
    onTertiaryContainer = Color.White,

    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,

    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MobileAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
