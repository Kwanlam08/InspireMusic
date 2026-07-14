package com.inspiremusic.ui

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inspiremusic.ui.components.BlurBottomNavigation
import com.inspiremusic.ui.components.AppChromeController
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.LocalAppChromeController
import com.inspiremusic.ui.components.LocalBackdropLayer
import com.inspiremusic.ui.components.LocalBackdropRenderingEnabled
import com.inspiremusic.ui.components.LocalHazeState
import com.inspiremusic.ui.components.MiniPlayer
import com.inspiremusic.ui.navigation.Screen
import com.inspiremusic.ui.navigation.SubRoutes
import com.inspiremusic.ui.screens.*
import com.inspiremusic.viewmodel.MusicViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val viewModel: MusicViewModel = viewModel()
    var showNowPlaying by remember { mutableStateOf(false) }
    var appChromeVisible by remember { mutableStateOf(true) }
    val currentSong by viewModel.currentSong.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    // 真实毛玻璃：捕获 NavHost 背后内容，给底栏�?MiniPlayer 用来模糊
    val hazeState = remember { HazeState() }
    val backdrop = rememberLayerBackdrop()
    val sharedBackdropRenderingEnabled = remember { isSharedBackdropRenderingSafe() }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPlaybackStateFromController()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalBackdropLayer provides backdrop,
        LocalBackdropRenderingEnabled provides sharedBackdropRenderingEnabled,
        LocalAppChromeController provides remember { AppChromeController { appChromeVisible = it } }
    ) {
        // ── 主布局：Box 叠层，NavContent / MiniPlayer / BottomNav ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            // ── 导航内容区（haze source 捕获真实滚动内容，给玻璃面板做模糊源） ──
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .haze(hazeState)
                    .layerBackdrop(backdrop),
                // Tab 切换淡入淡出
                enterTransition = {
                    fadeIn(tween(220)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMediumLow)
                    ) { it / 6 }
                },
                exitTransition = {
                    fadeOut(tween(180)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMediumLow)
                    ) { -it / 6 }
                },
                popEnterTransition = {
                    fadeIn(tween(220)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMediumLow)
                    ) { -it / 6 }
                },
                popExitTransition = {
                    fadeOut(tween(180)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMediumLow)
                    ) { it / 6 }
                }
            ) {
            // ── 主标签页 ──────────────────────────────────────
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateTo = { route -> navController.navigate(route) }
                )
            }
            composable(Screen.Library.route) {
                LibraryScreen(
                    viewModel = viewModel,
                    onNavigateTo = { route -> navController.navigate(route) }
                )
            }
            composable("library/search") {
                SearchScreen(viewModel = viewModel, onNavigateTo = { route -> navController.navigate(route) })
            }
            composable("library/organizer") {
                LibraryOrganizerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
            }
            composable(Screen.Diary.route) {
                MusicDiaryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToArtist = { artistName ->
                        navController.navigate("library/artist/${android.net.Uri.encode(artistName)}")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateTo = { route -> navController.navigate(route) }
                )
            }

            // ── Library 子页�?────────────────────────────────
            composable(SubRoutes.SONGS) {
                SongsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubRoutes.ALBUMS) {
                AlbumsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToAlbum = { albumName ->
                        navController.navigate("library/album/${android.net.Uri.encode(albumName)}")
                    }
                )
            }
            composable(SubRoutes.ARTISTS) {
                ArtistsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToArtist = { artistName ->
                        navController.navigate("library/artist/${android.net.Uri.encode(artistName)}")
                    }
                )
            }
            composable("playlists") {
                PlaylistsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToPlaylist = { playlistId ->
                        navController.navigate("playlist/$playlistId")
                    }
                )
            }
            composable("favorites") {
                FavoritesScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(SubRoutes.DIARY) {
                MusicDiaryScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigate(Screen.Diary.route) },
                    onNavigateToArtist = { artistName ->
                        navController.navigate("library/artist/${android.net.Uri.encode(artistName)}")
                    }
                )
            }
            composable("library/settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigate(Screen.Settings.route) },
                    onNavigateTo = { route -> navController.navigate(route) }
                )
            }

            composable(
                "library/search/album/{albumName}",
                enterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { it }
                },
                exitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { -it / 3 }
                },
                popEnterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { -it / 3 }
                },
                popExitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { it }
                }
            ) { backStackEntry ->
                val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
                AlbumDetailScreen(
                    albumName = android.net.Uri.decode(albumName),
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToArtist = { aName ->
                        navController.navigate("library/artist/${android.net.Uri.encode(aName)}")
                    }
                )
            }

            // ── 专辑详情 ──────────────────────────────────────
            composable(
                "library/album/{albumName}",
                enterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { it }
                },
                exitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { -it / 3 }
                },
                popEnterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { -it / 3 }
                },
                popExitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { it }
                }
            ) { backStackEntry ->
                val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
                AlbumDetailScreen(
                    albumName = android.net.Uri.decode(albumName),
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToArtist = { aName ->
                        navController.navigate("library/artist/${android.net.Uri.encode(aName)}")
                    }
                )
            }

            // ── 艺术家详�?────────────────────────────────────
            composable(
                "library/artist/{artistName}",
                enterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { it }
                },
                exitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { -it / 3 }
                },
                popEnterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { -it / 3 }
                },
                popExitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { it }
                }
            ) { backStackEntry ->
                val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
                ArtistDetailScreen(
                    artistName = android.net.Uri.decode(artistName),
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToAlbum = { aName ->
                        navController.navigate("library/album/${android.net.Uri.encode(aName)}")
                    }
                )
            }

            // ── 播放列表详情 ────────────────────────────────────
            composable(
                "playlist/{playlistId}",
                enterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { it }
                },
                exitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { -it / 3 }
                },
                popEnterTransition = {
                    fadeIn(tween(250)) + slideInHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { -it / 3 }
                },
                popExitTransition = {
                    fadeOut(tween(200)) + slideOutHorizontally(
                        spring(stiffness = Spring.StiffnessMedium)
                    ) { it }
                }
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        // ── 底部 MiniPlayer + 导航�?──────────────────────────
        val librarySearchAction = when (currentRoute) {
            Screen.Library.route -> Icons.Default.Search
            "library/search" -> Icons.AutoMirrored.Filled.ArrowBack
            else -> null
        }
        if (librarySearchAction != null) {
            FloatingGlassIconButton(
                icon = librarySearchAction,
                contentDescription = if (currentRoute == Screen.Library.route) "Search library" else "Back to library",
                onClick = {
                    if (currentRoute == Screen.Library.route) {
                        navController.navigate("library/search")
                    } else {
                        navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 20.dp),
                width = 52.dp,
                height = 44.dp,
                cornerRadius = 18.dp,
                useSharedBackdrop = true,
                ignoreBackdropCompatibility = true
            )
        }

        AnimatedVisibility(
            visible = appChromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) { it } + fadeOut(tween(150))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // MiniPlayer slide+fade 出现
            AnimatedVisibility(
                visible = currentSong != null,
                enter = slideInVertically(
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                ) { it } + fadeIn(tween(300)),
                exit = slideOutVertically(
                    spring(stiffness = Spring.StiffnessMediumLow)
                ) { it } + fadeOut(tween(200))
            ) {
                MiniPlayer(
                    viewModel = viewModel,
                    onExpand = { showNowPlaying = true }
                )
            }

            Spacer(Modifier.height(4.dp))

            // Liquid Glass 底栏
                BlurBottomNavigation(navController)
            }
        }
    }

    // ── NowPlaying 全屏 Modal（弹性滑�? ────────────────────
    AnimatedVisibility(
        visible = showNowPlaying,
        enter = slideInVertically(
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)
        ) { it } + fadeIn(tween(500)),
        exit = slideOutVertically(
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)
        ) { it } + fadeOut(tween(400))
    ) {
        NowPlayingScreen(
            viewModel = viewModel,
            onClose = { showNowPlaying = false },
            onNavigateToAlbum = { aName ->
                showNowPlaying = false
                navController.navigate("library/album/${android.net.Uri.encode(aName)}")
            },
            onNavigateToArtist = { aName ->
                showNowPlaying = false
                navController.navigate("library/artist/${android.net.Uri.encode(aName)}")
            }
        )
    }
    }  // CompositionLocalProvider 结束
}

private fun isSharedBackdropRenderingSafe(): Boolean {
    val device = Build.DEVICE.lowercase(Locale.ROOT)
    val model = Build.MODEL.lowercase(Locale.ROOT)
    val product = Build.PRODUCT.lowercase(Locale.ROOT)
    val display = Build.DISPLAY.lowercase(Locale.ROOT)
    val hardware = Build.HARDWARE.lowercase(Locale.ROOT)

    val knownUnsafeSonyXz2 =
        device.contains("xz2") ||
            model.contains("xz2") ||
            product.contains("xz2") ||
            device.contains("akari") ||
            product.contains("akari")
    val customRomWithOldAdreno =
        display.contains("crdroid") && hardware.contains("qcom") && Build.VERSION.SDK_INT <= 33

    return !knownUnsafeSonyXz2 && !customRomWithOldAdreno
}
