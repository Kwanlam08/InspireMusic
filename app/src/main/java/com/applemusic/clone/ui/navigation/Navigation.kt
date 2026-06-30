package com.applemusic.clone.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.applemusic.clone.R

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Filled.AutoAwesome)
    object Library : Screen("library", R.string.nav_library, Icons.Filled.LibraryMusic)
    object Search : Screen("search", R.string.nav_search, Icons.Filled.Search)
    object Diary : Screen("diary", R.string.nav_diary, Icons.Filled.CalendarMonth)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings)
}

val BottomNavItems = listOf(
    Screen.Search,
    Screen.Library,
    Screen.Home,
    Screen.Diary,
    Screen.Settings
)

object SubRoutes {
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
    const val DIARY = "library/diary"
}
