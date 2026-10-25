package com.banlimusic.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.banlimusic.desktop.ui.BanLiDesktopTheme
import com.banlimusic.desktop.ui.DesktopAgreementScreen
import com.banlimusic.desktop.ui.DesktopAppState
import com.banlimusic.desktop.ui.DesktopDownloadsScreen
import com.banlimusic.desktop.ui.DesktopHomeScreen
import com.banlimusic.desktop.ui.DesktopLibraryScreen
import com.banlimusic.desktop.ui.DesktopNowPlayingScreen
import com.banlimusic.desktop.ui.DesktopPlayerBar
import com.banlimusic.desktop.ui.DesktopSearchScreen
import com.banlimusic.desktop.ui.DesktopSettingsScreen
import com.banlimusic.desktop.ui.DesktopTab
import java.awt.Dimension

fun main() = application {
    val container = remember { DesktopContainer() }
    DisposableEffect(container) {
        onDispose { container.close() }
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "BanLiMusic",
        state = rememberWindowState(width = 1280.dp, height = 840.dp),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1_000, 680)
        }
        DesktopApp(container = container, onExit = ::exitApplication)
    }
}

@Composable
private fun DesktopApp(container: DesktopContainer, onExit: () -> Unit) {
    val settings by container.settingsRepository.settings.collectAsState()
    BanLiDesktopTheme(settings.themeMode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (!settings.agreementAccepted) {
                DesktopAgreementScreen(
                    onAccept = { container.settingsRepository.setAgreementAccepted(true) },
                    onExit = onExit,
                )
            } else {
                DesktopMain(container)
            }
        }
    }
}

@Composable
private fun DesktopMain(container: DesktopContainer) {
    val scope = rememberCoroutineScope()
    val state = remember(container, scope) { DesktopAppState(container, scope) }

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight().width(104.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "♫",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "BanLi",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(18.dp))
            DesktopTab.entries.forEach { tab ->
                NavigationRailItem(
                    selected = state.tab == tab && !state.showPlayer,
                    onClick = { state.selectTab(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = { Text(tab.label) },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "GD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
        }
        VerticalDivider()
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (state.showPlayer) {
                    DesktopNowPlayingScreen(state)
                } else {
                    when (state.tab) {
                        DesktopTab.HOME -> DesktopHomeScreen(state)
                        DesktopTab.SEARCH -> DesktopSearchScreen(state)
                        DesktopTab.LIBRARY -> DesktopLibraryScreen(state)
                        DesktopTab.DOWNLOADS -> DesktopDownloadsScreen(state)
                        DesktopTab.SETTINGS -> DesktopSettingsScreen(state)
                    }
                }
            }
            if (!state.showPlayer) {
                DesktopPlayerBar(state)
            }
        }
    }
}
