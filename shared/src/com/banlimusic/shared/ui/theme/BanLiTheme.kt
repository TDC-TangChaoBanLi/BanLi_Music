/**
 * 文件作用：定义 Android 与桌面端共用的 Material 3 主题、配色与自定义主题色算法。
 */
package com.banlimusic.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.banlimusic.app.core.datastore.ThemeMode

/** Default light palette shared by every platform. */
val BanLiLightColorScheme: ColorScheme = lightColorScheme(
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

/** Default dark palette shared by every platform. */
val BanLiDarkColorScheme: ColorScheme = darkColorScheme(
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

/**
 * Applies the shared Material 3 theme. Android may pass a dynamic wallpaper
 * scheme; otherwise [customThemeColor] or the bundled palette is used.
 */
@Composable
fun BanLiMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColorScheme: ColorScheme? = null,
    customThemeColor: Int? = null,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = dynamicColorScheme
        ?: customThemeColor?.let { banLiCustomColorScheme(it, darkTheme) }
        ?: if (darkTheme) BanLiDarkColorScheme else BanLiLightColorScheme

    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * Builds a Material 3 scheme from a single ARGB seed without pulling in a
 * platform palette library. Neutral surfaces are tinted toward the seed hue so
 * custom colors feel consistent rather than sitting on a fixed green surface.
 */
fun banLiCustomColorScheme(seedColor: Int, darkTheme: Boolean): ColorScheme {
    val hsl = hslOf(Color(seedColor))
    val hue = ((hsl[0] % 360f) + 360f) % 360f
    val saturation = hsl[1].coerceIn(0.15f, 1f)

    fun color(h: Float, s: Float, l: Float): Color = Color.hsl(
        ((h % 360f) + 360f) % 360f,
        s.coerceIn(0f, 1f),
        l.coerceIn(0f, 1f),
    )

    return if (darkTheme) {
        val primary = color(hue, saturation, 0.72f)
        BanLiDarkColorScheme.copy(
            primary = primary,
            onPrimary = color(hue, saturation, 0.12f),
            primaryContainer = color(hue, saturation, 0.30f),
            onPrimaryContainer = color(hue, saturation * 0.7f, 0.92f),
            inversePrimary = color(hue, saturation * 0.7f, 0.42f),
            secondary = color(hue, saturation * 0.40f, 0.74f),
            onSecondary = color(hue, saturation * 0.25f, 0.12f),
            secondaryContainer = color(hue, saturation * 0.40f, 0.30f),
            onSecondaryContainer = color(hue, saturation * 0.35f, 0.90f),
            tertiary = color(hue + 36f, saturation * 0.5f, 0.74f),
            onTertiary = color(hue + 36f, saturation * 0.25f, 0.12f),
            tertiaryContainer = color(hue + 36f, saturation * 0.4f, 0.30f),
            onTertiaryContainer = color(hue + 36f, saturation * 0.4f, 0.90f),
            background = color(hue, 0.12f, 0.05f),
            onBackground = color(hue, 0.08f, 0.92f),
            surface = color(hue, 0.12f, 0.05f),
            onSurface = color(hue, 0.08f, 0.92f),
            surfaceVariant = color(hue, 0.10f, 0.28f),
            onSurfaceVariant = color(hue, 0.08f, 0.78f),
            surfaceTint = primary,
            outline = color(hue, 0.08f, 0.56f),
            outlineVariant = color(hue, 0.08f, 0.30f),
            surfaceDim = color(hue, 0.10f, 0.05f),
            surfaceBright = color(hue, 0.10f, 0.20f),
            surfaceContainerLowest = color(hue, 0.10f, 0.03f),
            surfaceContainerLow = color(hue, 0.10f, 0.07f),
            surfaceContainer = color(hue, 0.10f, 0.09f),
            surfaceContainerHigh = color(hue, 0.10f, 0.13f),
            surfaceContainerHighest = color(hue, 0.10f, 0.17f),
        )
    } else {
        val primary = color(hue, saturation, 0.36f)
        BanLiLightColorScheme.copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = color(hue, saturation * 0.7f, 0.85f),
            onPrimaryContainer = color(hue, saturation, 0.18f),
            inversePrimary = color(hue, saturation * 0.6f, 0.72f),
            secondary = color(hue, saturation * 0.35f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = color(hue, saturation * 0.35f, 0.87f),
            onSecondaryContainer = color(hue, saturation * 0.45f, 0.20f),
            tertiary = color(hue + 36f, saturation * 0.45f, 0.42f),
            onTertiary = Color.White,
            tertiaryContainer = color(hue + 36f, saturation * 0.4f, 0.88f),
            onTertiaryContainer = color(hue + 36f, saturation * 0.5f, 0.22f),
            background = color(hue, 0.10f, 0.98f),
            onBackground = color(hue, 0.10f, 0.10f),
            surface = color(hue, 0.10f, 0.98f),
            onSurface = color(hue, 0.10f, 0.10f),
            surfaceVariant = color(hue, 0.10f, 0.90f),
            onSurfaceVariant = color(hue, 0.08f, 0.38f),
            surfaceTint = primary,
            outline = color(hue, 0.08f, 0.48f),
            outlineVariant = color(hue, 0.08f, 0.72f),
            surfaceDim = color(hue, 0.08f, 0.88f),
            surfaceBright = color(hue, 0.08f, 0.99f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = color(hue, 0.08f, 0.97f),
            surfaceContainer = color(hue, 0.08f, 0.95f),
            surfaceContainerHigh = color(hue, 0.08f, 0.92f),
            surfaceContainerHighest = color(hue, 0.08f, 0.90f),
        )
    }
}

/** Converts an sRGB color to HSL, returning hue in degrees and ratios in 0..1. */
private fun hslOf(color: Color): FloatArray {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val lightness = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, lightness)

    val delta = max - min
    val saturation = if (lightness > 0.5f) {
        delta / (2f - max - min)
    } else {
        delta / (max + min)
    }
    val hue = when (max) {
        color.red -> (color.green - color.blue) / delta +
            if (color.green < color.blue) 6f else 0f
        color.green -> (color.blue - color.red) / delta + 2f
        else -> (color.red - color.green) / delta + 4f
    } * 60f
    return floatArrayOf(hue, saturation, lightness)
}
