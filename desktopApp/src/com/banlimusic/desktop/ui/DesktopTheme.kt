package com.banlimusic.desktop.ui

import androidx.compose.runtime.Composable
import com.banlimusic.app.core.datastore.ThemeMode
import com.banlimusic.shared.ui.theme.BanLiMusicTheme

@Composable
fun BanLiDesktopTheme(
    mode: ThemeMode,
    customThemeColor: Int?,
    content: @Composable () -> Unit,
) {
    BanLiMusicTheme(
        themeMode = mode,
        customThemeColor = customThemeColor,
        content = content,
    )
}
