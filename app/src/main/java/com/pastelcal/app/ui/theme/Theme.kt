package com.pastelcal.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pastelcal.app.preferences.AccentColor
import com.pastelcal.app.preferences.ThemeMode

private fun lightColors(accent: androidx.compose.ui.graphics.Color) = lightColorScheme(
    primary = accent,
    secondary = Sky,
    tertiary = Mint,
    background = WarmCanvas,
    surface = WarmCanvas,
    onPrimary = Ink,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = MutedInk
)

private fun darkColors(accent: androidx.compose.ui.graphics.Color) = darkColorScheme(
    primary = accent,
    secondary = Sky,
    tertiary = Mint,
    background = DarkCanvas,
    surface = DarkSurface
)

@Composable
fun PastelCalTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColors: Boolean = false,
    accentColor: AccentColor = AccentColor.LAVENDER,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val accent = androidx.compose.ui.graphics.Color(accentColor.argb)
    val colors = if (dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColors(accent) else lightColors(accent)

    // Keep a real canvas color behind the app, while making Scaffold's default
    // background transparent so the subtle frog pattern can show through.
    val canvasColor = colors.background
    val appColors = colors.copy(background = Color.Transparent)

    MaterialTheme(colorScheme = appColors) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(canvasColor)
        ) {
            FrogPatternBackground(
                modifier = Modifier.fillMaxSize(),
                dark = dark
            )
            content()
        }
    }
}
