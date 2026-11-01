package com.dage.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6B5E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE9E0),
    onPrimaryContainer = Color(0xFF0C2E27),
    secondary = Color(0xFFB4703A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7E0CC),
    onSecondaryContainer = Color(0xFF3D2210),
    tertiary = Color(0xFF4C5C8A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF9F6),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFEFEAE3),
    onSurfaceVariant = Color(0xFF5C5751),
    outline = Color(0xFFD8D2C9),
    outlineVariant = Color(0xFFE8E3DB),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD3C2),
    onPrimary = Color(0xFF07332B),
    primaryContainer = Color(0xFF2A4A42),
    onPrimaryContainer = Color(0xFFB8E6DA),
    secondary = Color(0xFFE0A46A),
    onSecondary = Color(0xFF3A2110),
    secondaryContainer = Color(0xFF4A3524),
    onSecondaryContainer = Color(0xFFF7DFC9),
    tertiary = Color(0xFFA8B6E8),
    onTertiary = Color(0xFF1B2440),
    background = Color(0xFF121110),
    onBackground = Color(0xFFEDE9E3),
    surface = Color(0xFF1C1B19),
    onSurface = Color(0xFFEDE9E3),
    surfaceVariant = Color(0xFF2A2825),
    onSurfaceVariant = Color(0xFFB8B2A8),
    outline = Color(0xFF3D3A35),
    outlineVariant = Color(0xFF2A2825),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun DageTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
