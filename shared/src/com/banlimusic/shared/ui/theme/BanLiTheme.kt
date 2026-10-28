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

/**
 * Default brand seed. It matches the amber-brown application icon so the
 * Android and desktop builds open with the same warm palette.
 */
const val BANLI_DEFAULT_SEED = 0xFFA9723F.toInt()

/** Default light palette shared by every platform (warm amber-brown). */
val BanLiLightColorScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFA9723F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6E1C8),
    onPrimaryContainer = Color(0xFF3E2410),
    secondary = Color(0xFF7A5B3C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE1D1),
    onSecondaryContainer = Color(0xFF2C1D0E),
    tertiary = Color(0xFF8C5A2B),
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF301804),
    background = Color(0xFFFBF7F1),
    surface = Color(0xFFFBF7F1),
    surfaceVariant = Color(0xFFE9DDCD),
    onSurfaceVariant = Color(0xFF51453A),
    surfaceContainer = Color(0xFFF4ECE1),
    surfaceContainerLow = Color(0xFFF8F2EA),
    surfaceContainerHigh = Color(0xFFEEE3D5),
    surfaceContainerHighest = Color(0xFFE9DDCD),
    outline = Color(0xFF85705C),
    outlineVariant = Color(0xFFD6C6B4),
)

/** Default dark palette shared by every platform (warm amber-brown). */
val BanLiDarkColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE7B87F),
    onPrimary = Color(0xFF43290F),
    primaryContainer = Color(0xFF5C3B1B),
    onPrimaryContainer = Color(0xFFF7DFC4),
    secondary = Color(0xFFD6BFA2),
    onSecondary = Color(0xFF3A2A19),
    secondaryContainer = Color(0xFF4A3A28),
    onSecondaryContainer = Color(0xFFF0E0CC),
    tertiary = Color(0xFFE9C08A),
    tertiaryContainer = Color(0xFF6B4520),
    onTertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF15110C),
    surface = Color(0xFF15110C),
    surfaceVariant = Color(0xFF4E4335),
    onSurfaceVariant = Color(0xFFD6C6B4),
    surfaceContainer = Color(0xFF1E1812),
    surfaceContainerLow = Color(0xFF191410),
    surfaceContainerHigh = Color(0xFF292117),
    surfaceContainerHighest = Color(0xFF352B20),
    outline = Color(0xFFA08B75),
    outlineVariant = Color(0xFF4E4335),
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
