package com.dk80.tool.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PrimaryLight.copy(alpha = 0.15f),
    secondary = Accent,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = BgLight,
    onBackground = TextMainLight,
    surface = CardLight,
    onSurface = TextMainLight,
    surfaceVariant = BgLight,
    onSurfaceVariant = TextSubLight,
    outline = DividerLight,
    error = Bad,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = PrimaryDark,
    secondary = Accent,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = BgDark,
    onBackground = TextMainDark,
    surface = CardDark,
    onSurface = TextMainDark,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSubDark,
    outline = DividerDark,
    error = Bad,
)

@Composable
fun DK80Theme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as Activity).window
            w.statusBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(w, view).isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
