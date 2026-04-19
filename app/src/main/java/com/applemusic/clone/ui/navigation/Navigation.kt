package com.applemusic.clone.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "首页", Icons.Filled.PlayCircle)
    object Library : Screen("library", "资料库", Icons.Filled.LibraryMusic)
    object Search : Screen("search", "搜索", Icons.Filled.Search)
}

val BottomNavItems = listOf(
    Screen.Home,
    Screen.Library,
    Screen.Search
)

// 子页面路由
object SubRoutes {
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
}
