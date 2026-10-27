/**
 * 文件作用：提供 Android 与桌面端共用的自适应导航外壳和播放条槽位。
 */
package com.banlimusic.shared.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/** A top-level destination rendered by the shared navigation shell. */
data class BanLiDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** Navigation placement used by the adaptive shell. */
enum class BanLiNavigationLayout { SIDE, BOTTOM }

/** Side used by the rail when [BanLiNavigationLayout.SIDE] is active. */
enum class BanLiNavigationSide { START, END }

/** Vertical position of an optional playback bar inside the content pane. */
enum class BanLiBarPosition { TOP, BOTTOM }

/**
 * Common app shell for phones, tablets and desktop windows.
 *
 * Side layouts render a [NavigationRail] with optional header/footer slots and
 * an optional playback bar above or below the content. Bottom layouts render
 * the playback bar above a [NavigationBar]. The content is always drawn inside
 * the shell's safe-area padding.
 */
@Composable
fun BanLiAppShell(
    destinations: List<BanLiDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    navigationLayout: BanLiNavigationLayout,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    navigationSide: BanLiNavigationSide = BanLiNavigationSide.START,
    navigationContainerColor: Color = MaterialTheme.colorScheme.surface,
    railWidth: Dp? = null,
    showRailDivider: Boolean = false,
    playerBar: (@Composable () -> Unit)? = null,
    playerBarPosition: BanLiBarPosition = BanLiBarPosition.BOTTOM,
    railHeader: (@Composable ColumnScope.() -> Unit)? = null,
    railFooter: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (navigationLayout == BanLiNavigationLayout.BOTTOM) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showNavigation) {
                    Column {
                        // The phone mini player always sits directly above the bar.
                        playerBar?.invoke()
                        NavigationBar(containerColor = navigationContainerColor) {
                            destinations.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { onNavigate(destination.route) },
                                    icon = {
                                        Icon(
                                            destination.icon,
                                            contentDescription = destination.label,
                                        )
                                    },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                content()
            }
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showNavigation && navigationSide == BanLiNavigationSide.START) {
                NavigationRailPane(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    containerColor = navigationContainerColor,
                    width = railWidth,
                    header = railHeader,
                    footer = railFooter,
                )
                if (showRailDivider) {
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
            ) {
                if (playerBar != null && playerBarPosition == BanLiBarPosition.TOP) {
                    playerBar()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    content()
                }
                if (playerBar != null && playerBarPosition == BanLiBarPosition.BOTTOM) {
                    playerBar()
                }
            }
            if (showNavigation && navigationSide == BanLiNavigationSide.END) {
                if (showRailDivider) {
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                }
                NavigationRailPane(
                    destinations = destinations,
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    containerColor = navigationContainerColor,
                    width = railWidth,
                    header = railHeader,
                    footer = railFooter,
                )
            }
        }
    }
}

@Composable
private fun NavigationRailPane(
    destinations: List<BanLiDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    containerColor: Color,
    width: Dp?,
    header: (@Composable ColumnScope.() -> Unit)?,
    footer: (@Composable ColumnScope.() -> Unit)?,
) {
    val modifier = if (width == null) {
        Modifier.fillMaxHeight()
    } else {
        Modifier.fillMaxHeight().width(width)
    }
    NavigationRail(
        modifier = modifier,
        containerColor = containerColor,
    ) {
        header?.invoke(this)
        destinations.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = {
                    Icon(destination.icon, contentDescription = destination.label)
                },
                label = { Text(destination.label) },
            )
        }
        footer?.invoke(this)
    }
}
