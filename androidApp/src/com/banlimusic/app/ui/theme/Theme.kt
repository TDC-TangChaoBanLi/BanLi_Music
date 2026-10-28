/**
 * 文件作用：把 Android 动态取色接入两端共用的 Material 3 主题。
 */
package com.banlimusic.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.shared.ui.theme.BanLiMusicTheme

@Composable
fun BanLiMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    customThemeColor: Int? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Wallpaper colors are only used while explicitly enabled; the shared
    // amber-brown palette is the default on every platform.
    val dynamicScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dark = when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }

    BanLiMusicTheme(
        themeMode = themeMode,
        dynamicColorScheme = dynamicScheme,
        customThemeColor = customThemeColor,
        content = content,
    )
}
