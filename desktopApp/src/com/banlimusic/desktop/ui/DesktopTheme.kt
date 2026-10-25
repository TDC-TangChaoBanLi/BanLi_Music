package com.banlimusic.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.banlimusic.app.core.datastore.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF134E4A),
    secondary = Color(0xFF9A5B2C),
    surface = Color(0xFFF7F8F7),
    surfaceContainer = Color(0xFFEDF1EE),
    surfaceContainerLow = Color(0xFFF2F5F2),
    background = Color(0xFFF7F8F7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003D38),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFE7B98A),
    surface = Color(0xFF111614),
    surfaceContainer = Color(0xFF1A211E),
    surfaceContainerLow = Color(0xFF151B18),
    background = Color(0xFF111614),
)

@Composable
fun BanLiDesktopTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
