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
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PurpleSoft,
    onPrimaryContainer = InkDark,

    secondary = PurpleDeep,
    onSecondary = androidx.compose.ui.graphics.Color.White,
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
    onError = androidx.compose.ui.graphics.Color.White
)

private val DarkColors = darkColorScheme(
    primary = PurpleLight,
    onPrimary = InkDark,
    primaryContainer = PurpleDeep,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,

    secondary = CyanAccent,
    onSecondary = InkDark,
    secondaryContainer = Color0xFF2A4A5A,
    onSecondaryContainer = androidx.compose.ui.graphics.Color.White,

    tertiary = CyanGlow,
    onTertiary = InkDark,
    tertiaryContainer = Color0xFF1A3A44,
    onTertiaryContainer = androidx.compose.ui.graphics.Color.White,

    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,

    error = ErrorRed,
    onError = androidx.compose.ui.graphics.Color.White
)

private val Color0xFF2A4A5A = androidx.compose.ui.graphics.Color(0xFF2A4A5A)
private val Color0xFF1A3A44 = androidx.compose.ui.graphics.Color(0xFF1A3A44)

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
