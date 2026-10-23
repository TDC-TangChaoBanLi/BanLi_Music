package com.banlimusic.app.ui.theme

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
import com.banlimusic.app.core.datastore.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2DFDA),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E3),
    onSecondaryContainer = Color(0xFF06201D),
    tertiary = Color(0xFF9A5B2C),
    tertiaryContainer = Color(0xFFFFDCC6),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
    surfaceVariant = Color(0xFFDBE5E2),
    surfaceContainer = Color(0xFFEDF3F0),
    surfaceContainerHigh = Color(0xFFE6EEEA),
    outline = Color(0xFF6F7977),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD5CD),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF005049),
    onPrimaryContainer = Color(0xFFB2DFDA),
    secondary = Color(0xFFB0CCC7),
    secondaryContainer = Color(0xFF334B47),
    tertiary = Color(0xFFFFB784),
    tertiaryContainer = Color(0xFF743F12),
    background = Color(0xFF081311),
    surface = Color(0xFF081311),
    surfaceVariant = Color(0xFF3F4947),
    surfaceContainer = Color(0xFF121E1B),
    surfaceContainerHigh = Color(0xFF1C2926),
    outline = Color(0xFF899390),
)

@Composable
fun BanLiMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
