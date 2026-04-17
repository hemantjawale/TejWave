package com.example.tejastra.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TejAstraColorScheme = darkColorScheme(
    primary = Snow,
    onPrimary = Void,
    primaryContainer = Graphite,
    onPrimaryContainer = Snow,
    secondary = Fog,
    onSecondary = Void,
    secondaryContainer = Slate,
    onSecondaryContainer = Cloud,
    tertiary = Mist,
    onTertiary = Void,
    background = Void,
    onBackground = Snow,
    surface = Void,
    onSurface = Snow,
    surfaceVariant = Charcoal,
    onSurfaceVariant = Fog,
    outline = BorderMedium,
    outlineVariant = BorderSubtle,
    inverseSurface = Snow,
    inverseOnSurface = Void,
    surfaceTint = Color.Transparent,
)

@Composable
fun TejAstraTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Void.toArgb()
            window.navigationBarColor = Void.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = TejAstraColorScheme,
        typography = Typography,
        content = content
    )
}