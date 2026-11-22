package com.inspiremusic.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.inspiremusic.R

sealed class Screen(val route: String, val titleResId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Outlined.AutoAwesome)
    object Library : Screen("library", R.string.nav_library, Icons.Outlined.LibraryMusic)
    object Diary : Screen("diary", R.string.nav_diary, Icons.Outlined.CalendarMonth)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Outlined.Settings)
}

val BottomNavItems = listOf(
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
