package com.applemusic.clone.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.applemusic.clone.ui.components.BlurBottomNavigation
import com.applemusic.clone.ui.components.LocalHazeState
import com.applemusic.clone.ui.components.MiniPlayer
import com.applemusic.clone.ui.navigation.Screen
import com.applemusic.clone.ui.navigation.SubRoutes
import com.applemusic.clone.ui.screens.*
import com.applemusic.clone.viewmodel.MusicViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: MusicViewModel = viewModel()
    var showNowPlaying by remember { mutableStateOf(false) }
    val currentSong by viewModel.currentSong.collectAsState()
    // 真实毛玻璃：捕获 NavHost 背后内容，给底栏�?MiniPlayer 用来模糊
    val hazeState = remember { HazeState() }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
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
                    .haze(hazeState),
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
            composable(Screen.Search.route) {
                SearchScreen(viewModel = viewModel, onNavigateTo = { route -> navController.navigate(route) })
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
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
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
