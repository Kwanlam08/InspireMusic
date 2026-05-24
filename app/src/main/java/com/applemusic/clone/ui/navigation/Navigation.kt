package com.applemusic.clone.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.applemusic.clone.R

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Filled.AutoAwesome)
    object Library : Screen("library", R.string.nav_library, Icons.Filled.LibraryMusic)
    object Search : Screen("search", R.string.nav_search, Icons.Filled.Search)
}

val BottomNavItems = listOf(
    Screen.Home,
    Screen.Library,
    Screen.Search
)

object SubRoutes {
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
}
