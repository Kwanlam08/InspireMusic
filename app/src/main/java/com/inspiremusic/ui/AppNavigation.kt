package com.inspiremusic.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.inspiremusic.R
import com.inspiremusic.ui.components.BlurBottomNavigation
import com.inspiremusic.ui.components.AppChromeController
import com.inspiremusic.ui.components.FloatingGlassIconButton
import com.inspiremusic.ui.components.LocalAppChromeController
import com.inspiremusic.ui.components.LocalBackdropLayer
import com.inspiremusic.ui.components.LocalBackdropRenderingEnabled
import com.inspiremusic.ui.components.LocalHazeBackdropRenderingEnabled
import com.inspiremusic.ui.components.LocalHazeState
import com.inspiremusic.ui.components.MiniPlayer
import com.inspiremusic.settings.OverlayGlassBackend
import com.inspiremusic.settings.overlayGlassBackend
import com.inspiremusic.settings.LocalAppSettingsController
import com.inspiremusic.ui.navigation.Screen
import com.inspiremusic.ui.navigation.SubRoutes
import com.inspiremusic.ui.screens.*
import com.inspiremusic.viewmodel.MusicViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.MutableSharedFlow

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val viewModel: MusicViewModel = viewModel()
    var showNowPlaying by remember { mutableStateOf(false) }
    var appChromeVisible by remember { mutableStateOf(true) }
    // 日记页内部子页面（日志列表/日志详情）返回信号：全局返回按钮优先走内部返回
    val diaryBackRequest = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    var diarySubPageOpen by remember { mutableStateOf(false) }
    // 设置页二级页返回信号：全局返回按钮优先走内部返回
    val settingsBackRequest = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    var settingsSubPageOpen by remember { mutableStateOf(false) }
    // 歌曲页排序信号：全局排序按钮触发屏幕内排序弹层
    val songsSortRequest = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val currentSong by viewModel.currentSong.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val appSettingsController = LocalAppSettingsController.current
    val appSettings by appSettingsController.settings.collectAsState()
    // 真实毛玻璃：捕获 NavHost 背后内容，给底栏�?MiniPlayer 用来模糊
    val hazeState = remember { HazeState() }
    val backdrop = rememberLayerBackdrop()
    val overlayGlassBackend = appSettings.glassRenderingMode.overlayGlassBackend
    val activeOverlayBackdrop = backdrop.takeIf {
        overlayGlassBackend == OverlayGlassBackend.LAYER_BACKDROP
    }

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
        LocalAppChromeController provides remember { AppChromeController { appChromeVisible = it } }
    ) {
        // ── 主布局：Box 叠层，NavContent / MiniPlayer / BottomNav ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {

            // ── 导航内容区（haze source 捕获真实滚动内容，给玻璃面板做模糊源） ──
            // A destination must never sample the backdrop layer that contains itself.
            // Doing so creates a cyclic RenderNode tree and overflows RenderThread's native stack.
            CompositionLocalProvider(
                LocalBackdropLayer provides null,
                LocalBackdropRenderingEnabled provides false,
                LocalHazeBackdropRenderingEnabled provides false
            ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .haze(hazeState)
                    .then(
                        if (activeOverlayBackdrop != null) {
                            Modifier.layerBackdrop(activeOverlayBackdrop)
                        } else {
                            Modifier
                        }
                    ),
                // Tab 切换平滑微纵深过渡（兼具 60/120fps 流畅度与高级纵深感）
                enterTransition = {
                    fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.98f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing))
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
                    diaryBackRequest = diaryBackRequest,
                    onSubPageOpenChange = { diarySubPageOpen = it },
                    onNavigateToArtist = { artistName ->
                        navController.navigate("library/artist/${android.net.Uri.encode(artistName)}")
                    },
                    onNavigateToAlbum = { albumName ->
                        navController.navigate("library/album/${android.net.Uri.encode(albumName)}")
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateTo = { route -> navController.navigate(route) },
                    settingsBackRequest = settingsBackRequest,
                    onSubPageOpenChange = { settingsSubPageOpen = it }
                )
            }

            // ── Library 子页�?────────────────────────────────
            composable(SubRoutes.SONGS) {
                SongsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    songsSortRequest = songsSortRequest
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
                    diaryBackRequest = diaryBackRequest,
                    onSubPageOpenChange = { diarySubPageOpen = it },
                    onNavigateToArtist = { artistName ->
                        navController.navigate("library/artist/${android.net.Uri.encode(artistName)}")
                    },
                    onNavigateToAlbum = { albumName ->
                        navController.navigate("library/album/${android.net.Uri.encode(albumName)}")
                    }
                )
            }
            composable("library/settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigate(Screen.Settings.route) },
                    onNavigateTo = { route -> navController.navigate(route) },
                    settingsBackRequest = settingsBackRequest,
                    onSubPageOpenChange = { settingsSubPageOpen = it }
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
            }

        // ── 底部 MiniPlayer + 导航�?──────────────────────────
        // Only sibling overlays outside the captured NavHost may sample its backdrop.
        CompositionLocalProvider(
            LocalBackdropLayer provides activeOverlayBackdrop,
            LocalBackdropRenderingEnabled provides (activeOverlayBackdrop != null),
            LocalHazeBackdropRenderingEnabled provides (
                overlayGlassBackend == OverlayGlassBackend.HAZE_BACKDROP
            )
        ) {
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
                ignoreBackdropCompatibility = false
            )
        }

        // ── 播放清单页右上角「新建」按钮（真玻璃，移到全局层渲染） ─────
        // 屏幕内按钮无法共享 NavHost 背景（循环 RenderNode 崩溃），
        // 所以和全局返回按钮一样放在 NavHost 外作为兄弟节点，共享 LayerBackdrop。
        if (currentRoute == "playlists") {
            val playlists by viewModel.playlists.collectAsState()
            val newPlaylistName = stringResource(R.string.playlist_new_default_name, playlists.size + 1)
            FloatingGlassIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.playlist_create),
                onClick = {
                    val newId = viewModel.createPlaylist(newPlaylistName)
                    navController.navigate("playlist/$newId")
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 20.dp),
                width = 52.dp,
                height = 44.dp,
                cornerRadius = 18.dp,
                useSharedBackdrop = true,
                ignoreBackdropCompatibility = false
            )
        }

        // ── 歌曲页右上角「排序」按钮（真玻璃，移到全局层渲染） ─────
        if (currentRoute == SubRoutes.SONGS) {
            FloatingGlassIconButton(
                icon = Icons.Default.Sort,
                contentDescription = stringResource(R.string.songs_sort),
                onClick = { songsSortRequest.tryEmit(Unit) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 20.dp),
                width = 52.dp,
                height = 44.dp,
                cornerRadius = 18.dp,
                useSharedBackdrop = true,
                ignoreBackdropCompatibility = false
            )
        }

        // ── 全局返回按钮（子页面用，真玻璃） ─────────────────────
        // 子页面在 NavHost 内，不能采样 NavHost backdrop（循环 RenderNode）。
        // 所以返回按钮必须渲染在 NavHost 外面作为兄弟节点 —— 共享 LayerBackdrop。
        val showGlobalBackButton = when (currentRoute) {
            Screen.Home.route,
            Screen.Library.route -> false
            // 日记/设置主标签页本身不显示；内部子页面打开时显示
            Screen.Diary.route -> diarySubPageOpen
            Screen.Settings.route -> settingsSubPageOpen
            "library/search", "library/organizer" -> true
            else -> true
        }
        if (showGlobalBackButton) {
            FloatingGlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = {
                    when (currentRoute) {
                        Screen.Diary.route -> diaryBackRequest.tryEmit(Unit)
                        SubRoutes.DIARY ->
                            if (!diarySubPageOpen) navController.popBackStack()
                            else diaryBackRequest.tryEmit(Unit)
                        Screen.Settings.route -> settingsBackRequest.tryEmit(Unit)
                        "library/settings" ->
                            if (!settingsSubPageOpen) navController.popBackStack()
                            else settingsBackRequest.tryEmit(Unit)
                        else -> navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 19.dp),
                width = 52.dp,
                height = 44.dp,
                cornerRadius = 18.dp,
                useSharedBackdrop = true,
                ignoreBackdropCompatibility = false
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
