package com.banlimusic.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.banlimusic.app.core.datastore.AppSettings
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.feature.downloads.DownloadsScreen
import com.banlimusic.app.feature.home.HomeScreen
import com.banlimusic.app.feature.library.LibraryScreen
import com.banlimusic.app.feature.player.PlayerScreen
import com.banlimusic.app.feature.search.SearchScreen
import com.banlimusic.app.feature.settings.SettingsScreen
import com.banlimusic.app.ui.components.LocalRequestListArtwork
import com.banlimusic.app.ui.components.MiniPlayer
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val allDestinations = listOf(
    BottomDestination(Routes.HOME, "首页", Icons.Rounded.Home),
    BottomDestination(Routes.SEARCH, "搜索", Icons.Rounded.Search),
    BottomDestination(Routes.LIBRARY, "音乐库", Icons.Rounded.LibraryMusic),
    BottomDestination(Routes.DOWNLOADS, "下载", Icons.Rounded.Download),
    BottomDestination(Routes.SETTINGS, "设置", Icons.Rounded.Settings),
)

private fun destinationsFor(ids: List<String>): List<BottomDestination> {
    val selected = ids.mapNotNull { id -> allDestinations.firstOrNull { it.route == id } }
    return selected.ifEmpty { allDestinations }
}

@Composable
fun AppRoot(
    container: AppContainer,
    initialSettings: AppSettings = AppSettings(),
    onExitApp: () -> Unit = {},
    widgetRequested: Boolean = false,
    widgetOpenPlayer: Boolean = false,
    onWidgetRequestHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: initialSettings.defaultNavItem
    val playbackState by container.playbackController.state.collectAsStateWithLifecycle()
    val favoriteIds by container.libraryRepository.observeFavoriteIds()
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = initialSettings)
    val destinations = remember(settings.bottomNavItems) {
        destinationsFor(settings.bottomNavItems)
    }
    val playback = container.playbackController
    val scope = rememberCoroutineScope()
    val showNavigation = currentRoute != Routes.PLAYER

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun playQueue(tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        val safeIndex = index.coerceIn(tracks.indices)
        playback.play(tracks[safeIndex], tracks)
        if (settings.openPlayerOnTrackClick) {
            navController.navigate(Routes.PLAYER) { launchSingleTop = true }
        }
    }

    LaunchedEffect(widgetRequested, widgetOpenPlayer) {
        if (!widgetRequested) return@LaunchedEffect
        if (widgetOpenPlayer) {
            navController.navigate(Routes.PLAYER) { launchSingleTop = true }
        } else {
            navigateTo(Routes.HOME)
        }
        onWidgetRequestHandled()
    }

    CompositionLocalProvider(
        LocalRequestListArtwork provides settings.requestListArtwork,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
            if (isTablet) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    if (showNavigation) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            destinations.forEach { destination ->
                                NavigationRailItem(
                                    selected = currentRoute == destination.route,
                                    onClick = { navigateTo(destination.route) },
                                    icon = {
                                        Icon(destination.icon, contentDescription = destination.label)
                                    },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    ) {
                        if (showNavigation) {
                            MiniPlayer(
                                state = playbackState,
                                onClick = {
                                    navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                                },
                                onTogglePlayPause = playback::togglePlayPause,
                                onNext = playback::next,
                                onPrevious = playback::previous,
                                isFavorite = playbackState.currentTrack?.id in favoriteIds,
                                onToggleFavorite = {
                                    val current = playbackState.currentTrack
                                    if (current != null) {
                                        scope.launch {
                                            container.libraryRepository.toggleFavorite(
                                                current,
                                                settings.defaultFavoriteCategory,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        AppNavHost(
                            navController = navController,
                            playQueue = ::playQueue,
                            navigateTopLevel = ::navigateTo,
                            onExitApp = onExitApp,
                            navOrder = destinations.map { it.route },
                            startDestination = settings.defaultNavItem,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showNavigation) {
                        Column {
                            MiniPlayer(
                                state = playbackState,
                                onClick = {
                                    navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                                },
                                onTogglePlayPause = playback::togglePlayPause,
                                onNext = playback::next,
                                onPrevious = playback::previous,
                                isFavorite = playbackState.currentTrack?.id in favoriteIds,
                                onToggleFavorite = {
                                    val current = playbackState.currentTrack
                                    if (current != null) {
                                        scope.launch {
                                            container.libraryRepository.toggleFavorite(
                                                current,
                                                settings.defaultFavoriteCategory,
                                            )
                                        }
                                    }
                                },
                            )
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                destinations.forEach { destination ->
                                    NavigationBarItem(
                                        selected = currentRoute == destination.route,
                                        onClick = { navigateTo(destination.route) },
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
                AppNavHost(
                    navController = navController,
                    playQueue = ::playQueue,
                    navigateTopLevel = ::navigateTo,
                    onExitApp = onExitApp,
                    navOrder = destinations.map { it.route },
                    startDestination = settings.defaultNavItem,
                    modifier = Modifier.padding(padding),
                )
            }
        }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    playQueue: (List<Track>, Int) -> Unit,
    navigateTopLevel: (String) -> Unit,
    onExitApp: () -> Unit,
    navOrder: List<String>,
    startDestination: String,
    modifier: Modifier = Modifier,
) {
    fun indexOf(route: String?): Int = navOrder.indexOf(route).takeIf { it >= 0 } ?: 0

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            val forward = indexOf(targetState.destination.route) >=
                indexOf(initialState.destination.route)
            slideInHorizontally(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) { width -> if (forward) width else -width }
        },
        exitTransition = {
            if (targetState.destination.route == Routes.PLAYER) {
                // The player slides up over the current page.
                ExitTransition.None
            } else {
                val forward = indexOf(targetState.destination.route) >=
                    indexOf(initialState.destination.route)
                slideOutHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ) { width -> if (forward) -width else width }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route == Routes.PLAYER) {
                // Returning from the player: only the player animates (slides down).
                EnterTransition.None
            } else {
                val forward = indexOf(targetState.destination.route) >=
                    indexOf(initialState.destination.route)
                slideInHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ) { width -> if (forward) width else -width }
            }
        },
        popExitTransition = {
            val forward = indexOf(targetState.destination.route) >=
                indexOf(initialState.destination.route)
            slideOutHorizontally(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) { width -> if (forward) -width else width }
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onSearchClick = { navigateTopLevel(Routes.SEARCH) },
                onOpenLibrary = { navigateTopLevel(Routes.LIBRARY) },
                onPlayQueue = playQueue,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(onPlayQueue = playQueue, onExitApp = onExitApp)
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onPlayQueue = playQueue,
                onOpenDownloads = { navigateTopLevel(Routes.DOWNLOADS) },
                onExitApp = onExitApp,
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onOpenPlayer = {
                    navController.navigate(Routes.PLAYER) { launchSingleTop = true }
                },
                onExitApp = onExitApp,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onExitApp = onExitApp)
        }
        composable(
            route = Routes.PLAYER,
            enterTransition = {
                slideInVertically(animationSpec = tween(260)) { it }
            },
            exitTransition = {
                slideOutVertically(animationSpec = tween(260)) { it }
            },
            popEnterTransition = {
                slideInVertically(animationSpec = tween(260)) { it }
            },
            popExitTransition = {
                slideOutVertically(animationSpec = tween(260)) { it }
            },
        ) {
            PlayerScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME)
                    }
                },
            )
        }
    }
}
