package com.inspiremusic.settings

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

enum class GlassRenderingMode(val value: String) {
    AUTO("auto"),
    COMPATIBLE("compatible");

    companion object {
        fun fromValue(value: String?): GlassRenderingMode =
            entries.firstOrNull { it.value == value } ?: AUTO
    }
}

enum class NowPlayingStyle(val value: String) {
    CLASSIC("classic"),
    IMMERSIVE("immersive");

    companion object {
        fun fromValue(value: String?): NowPlayingStyle =
            entries.firstOrNull { it.value == value } ?: CLASSIC
    }
}

enum class OverlayGlassBackend {
    LAYER_BACKDROP,
    HAZE_BACKDROP
}

const val DEFAULT_GLASS_BLUR_PERCENT = 100
const val MAX_GLASS_BLUR_PERCENT = 200

val GlassRenderingMode.overlayGlassBackend: OverlayGlassBackend
    get() = when (this) {
        GlassRenderingMode.AUTO -> OverlayGlassBackend.LAYER_BACKDROP
        GlassRenderingMode.COMPATIBLE -> OverlayGlassBackend.HAZE_BACKDROP
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
    val restorePlaybackQueue: Boolean = true,
    val glassRenderingMode: GlassRenderingMode = GlassRenderingMode.AUTO,
    val defaultNowPlayingTab: Int = 0,
    val hideStatusBarInLandscape: Boolean = true,
    val enableFluidMeshGradient: Boolean = true,
    val nowPlayingStyle: NowPlayingStyle = NowPlayingStyle.CLASSIC
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
    const val GLASS_RENDERING_MODE = "glass_rendering_mode"
    const val GLASS_BLUR_PERCENT = "glass_blur_percent"
    const val DEFAULT_NOW_PLAYING_TAB = "default_now_playing_tab"
    const val HIDE_STATUS_BAR_LANDSCAPE = "hide_status_bar_landscape"
    const val ENABLE_FLUID_MESH_GRADIENT = "enable_fluid_mesh_gradient"
    const val NOW_PLAYING_STYLE = "now_playing_style"
    const val AUTO_RENDERING_TRIAL_PENDING = "auto_rendering_trial_pending"
    const val LEGACY_RENDER_SESSION_PENDING = "render_session_pending"
}

class AppSettingsController(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        AppSettingsKeys.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    val recoveredFromUnsafeRendering: Boolean

    init {
        val legacyFullMode = prefs.getString(AppSettingsKeys.GLASS_RENDERING_MODE, null) == "full"
        val unfinishedAutoTrial = prefs.getBoolean(AppSettingsKeys.AUTO_RENDERING_TRIAL_PENDING, false)
        recoveredFromUnsafeRendering = unfinishedAutoTrial

        val editor = prefs.edit()
            .remove(AppSettingsKeys.LEGACY_RENDER_SESSION_PENDING)
            .putBoolean(AppSettingsKeys.AUTO_RENDERING_TRIAL_PENDING, false)
        when {
            unfinishedAutoTrial -> editor.putString(
                AppSettingsKeys.GLASS_RENDERING_MODE,
                GlassRenderingMode.COMPATIBLE.value
            )
            legacyFullMode -> editor.putString(
                AppSettingsKeys.GLASS_RENDERING_MODE,
                GlassRenderingMode.AUTO.value
            )
        }
        // An unfinished trial means the process died immediately after switching
        // to LayerBackdrop. Recover once without treating every ordinary launch or
        // quick app exit as a rendering crash.
        editor.commit()
    }

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Keep live slider updates separate from settings observed by entire screens.
    private val _glassBlurPercent = MutableStateFlow(
        prefs.getInt(AppSettingsKeys.GLASS_BLUR_PERCENT, DEFAULT_GLASS_BLUR_PERCENT)
            .coerceIn(0, MAX_GLASS_BLUR_PERCENT)
    )
    val glassBlurPercent: StateFlow<Int> = _glassBlurPercent.asStateFlow()

    fun previewGlassBlur(percent: Int) {
        _glassBlurPercent.value = percent.coerceIn(0, MAX_GLASS_BLUR_PERCENT)
    }

    fun saveGlassBlur() {
        prefs.edit().putInt(AppSettingsKeys.GLASS_BLUR_PERCENT, _glassBlurPercent.value).apply()
    }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != AppSettingsKeys.GLASS_BLUR_PERCENT) _settings.value = readSettings()
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

    fun setDefaultNowPlayingTab(tab: Int) {
        prefs.edit().putInt(AppSettingsKeys.DEFAULT_NOW_PLAYING_TAB, tab.coerceIn(0, 2)).apply()
    }

    fun setHideStatusBarInLandscape(enabled: Boolean) {
        prefs.edit().putBoolean(AppSettingsKeys.HIDE_STATUS_BAR_LANDSCAPE, enabled).apply()
    }

    fun setEnableFluidMeshGradient(enabled: Boolean) {
        prefs.edit().putBoolean(AppSettingsKeys.ENABLE_FLUID_MESH_GRADIENT, enabled).apply()
    }

    fun setNowPlayingStyle(style: NowPlayingStyle) {
        prefs.edit().putString(AppSettingsKeys.NOW_PLAYING_STYLE, style.value).apply()
    }

    fun setRestorePlaybackQueue(enabled: Boolean) {
        prefs.edit().putBoolean(AppSettingsKeys.RESTORE_PLAYBACK_QUEUE, enabled).apply()
    }

    fun setGlassRenderingMode(mode: GlassRenderingMode) {
        val storedMode = GlassRenderingMode.fromValue(
            prefs.getString(AppSettingsKeys.GLASS_RENDERING_MODE, GlassRenderingMode.AUTO.value)
        )
        if (storedMode == mode) return
        prefs.edit()
            .putString(AppSettingsKeys.GLASS_RENDERING_MODE, mode.value)
            .putBoolean(
                AppSettingsKeys.AUTO_RENDERING_TRIAL_PENDING,
                mode == GlassRenderingMode.AUTO
            )
            .commit()
    }

    fun markRenderSessionStable() {
        if (!prefs.getBoolean(AppSettingsKeys.AUTO_RENDERING_TRIAL_PENDING, false)) return
        prefs.edit().putBoolean(AppSettingsKeys.AUTO_RENDERING_TRIAL_PENDING, false).commit()
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
        restorePlaybackQueue = prefs.getBoolean(AppSettingsKeys.RESTORE_PLAYBACK_QUEUE, true),
        glassRenderingMode = GlassRenderingMode.fromValue(
            prefs.getString(AppSettingsKeys.GLASS_RENDERING_MODE, GlassRenderingMode.AUTO.value)
        ),
        defaultNowPlayingTab = prefs.getInt(AppSettingsKeys.DEFAULT_NOW_PLAYING_TAB, 0).coerceIn(0, 2),
        hideStatusBarInLandscape = prefs.getBoolean(AppSettingsKeys.HIDE_STATUS_BAR_LANDSCAPE, true),
        enableFluidMeshGradient = prefs.getBoolean(AppSettingsKeys.ENABLE_FLUID_MESH_GRADIENT, true),
        nowPlayingStyle = NowPlayingStyle.fromValue(
            prefs.getString(AppSettingsKeys.NOW_PLAYING_STYLE, NowPlayingStyle.CLASSIC.value)
        )
    )
}

val LocalAppSettingsController = staticCompositionLocalOf<AppSettingsController> {
    error("AppSettingsController is not provided")
}
