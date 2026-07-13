package com.applemusic.clone.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromValue(value: String?): ThemeMode = entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

enum class AccentColorStyle(val value: String) {
    APPLE_RED("apple_red"),
    ANDROID_BLUE("android_blue");

    companion object {
        fun fromValue(value: String?): AccentColorStyle =
            entries.firstOrNull { it.value == value } ?: APPLE_RED
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val accentColorStyle: AccentColorStyle = AccentColorStyle.APPLE_RED,
    val onlineLyricsEnabled: Boolean = true,
    val preferSyncedLyrics: Boolean = true,
    val onlineArtworkEnabled: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val replayGainEnabled: Boolean = false,
    val restorePlaybackQueue: Boolean = true
)

object AppSettingsKeys {
    const val PREFS_NAME = "app_settings"
    const val THEME_MODE = "theme_mode"
    const val USE_DYNAMIC_COLOR = "use_dynamic_color"
    const val ACCENT_COLOR_STYLE = "accent_color_style"
    const val ONLINE_LYRICS_ENABLED = "online_lyrics_enabled"
    const val PREFER_SYNCED_LYRICS = "prefer_synced_lyrics"
    const val ONLINE_ARTWORK_ENABLED = "online_artwork_enabled"
    const val CROSSFADE_SECONDS = "crossfade_seconds"
    const val REPLAY_GAIN_ENABLED = "replay_gain_enabled"
    const val RESTORE_PLAYBACK_QUEUE = "restore_playback_queue"
}

class AppSettingsController(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        AppSettingsKeys.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = readSettings()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(AppSettingsKeys.THEME_MODE, mode.value).apply()
    }

    fun setUseDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(AppSettingsKeys.USE_DYNAMIC_COLOR, enabled).apply()
    }

    fun setAccentColorStyle(style: AccentColorStyle) {
        prefs.edit().putString(AppSettingsKeys.ACCENT_COLOR_STYLE, style.value).apply()
    }

    fun setOnlineLyricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(AppSettingsKeys.ONLINE_LYRICS_ENABLED, enabled).apply()
    }

    fun setPreferSyncedLyrics(enabled: Boolean) {
        prefs.edit().putBoolean(AppSettingsKeys.PREFER_SYNCED_LYRICS, enabled).apply()
    }

    fun setOnlineArtworkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(AppSettingsKeys.ONLINE_ARTWORK_ENABLED, enabled).apply()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        prefs.edit().putInt(AppSettingsKeys.CROSSFADE_SECONDS, seconds.coerceIn(0, 12)).apply()
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(AppSettingsKeys.REPLAY_GAIN_ENABLED, enabled).apply()
    }

    fun setRestorePlaybackQueue(enabled: Boolean) {
        prefs.edit().putBoolean(AppSettingsKeys.RESTORE_PLAYBACK_QUEUE, enabled).apply()
    }

    private fun readSettings(): AppSettings = AppSettings(
        themeMode = ThemeMode.fromValue(prefs.getString(AppSettingsKeys.THEME_MODE, ThemeMode.SYSTEM.value)),
        useDynamicColor = prefs.getBoolean(AppSettingsKeys.USE_DYNAMIC_COLOR, true),
        accentColorStyle = AccentColorStyle.fromValue(
            prefs.getString(AppSettingsKeys.ACCENT_COLOR_STYLE, AccentColorStyle.APPLE_RED.value)
        ),
        onlineLyricsEnabled = prefs.getBoolean(AppSettingsKeys.ONLINE_LYRICS_ENABLED, true),
        preferSyncedLyrics = prefs.getBoolean(AppSettingsKeys.PREFER_SYNCED_LYRICS, true),
        onlineArtworkEnabled = prefs.getBoolean(AppSettingsKeys.ONLINE_ARTWORK_ENABLED, true),
        crossfadeSeconds = prefs.getInt(AppSettingsKeys.CROSSFADE_SECONDS, 0).coerceIn(0, 12),
        replayGainEnabled = prefs.getBoolean(AppSettingsKeys.REPLAY_GAIN_ENABLED, false),
        restorePlaybackQueue = prefs.getBoolean(AppSettingsKeys.RESTORE_PLAYBACK_QUEUE, true)
    )
}

val LocalAppSettingsController = staticCompositionLocalOf<AppSettingsController> {
    error("AppSettingsController is not provided")
}
