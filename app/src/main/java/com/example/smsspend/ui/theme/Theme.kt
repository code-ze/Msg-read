package com.example.smsspend.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Premium, muted semantic palette (soft mint / coral, not neon).
private val Mint = Color(0xFF4DB6AC)
private val Coral = Color(0xFFE57373)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7C7B),
    secondary = Color(0xFF3B6EA5),
    tertiary = Color(0xFF7C5CBF),
    error = Color(0xFFC2554D),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEFF3)
)

// Deep-gray dark theme with subtle elevated surfaces (#121212 / #1E1E1E).
private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF06302B),
    secondary = Color(0xFF7FB0E6),
    tertiary = Color(0xFFB39DDB),
    error = Coral,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF262A2E),
    onSurfaceVariant = Color(0xFF9BA4AE),
    outlineVariant = Color(0xFF333A40)
)

@Composable
fun SmsSpendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
