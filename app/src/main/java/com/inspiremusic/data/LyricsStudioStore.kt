package com.inspiremusic.data

import android.content.Context
import com.inspiremusic.model.FavoriteLyricLine
import com.inspiremusic.model.LyricIssue
import com.inspiremusic.model.LyricIssueType
import com.inspiremusic.model.LyricTextAlignment
import com.inspiremusic.model.LyricVersion
import com.inspiremusic.model.LyricsDisplaySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class LyricsStudioStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val versionsDir = File(appContext.filesDir, "lyrics_studio/versions").apply { mkdirs() }

    private val _displaySettings = MutableStateFlow(readDisplaySettings())
    val displaySettings: StateFlow<LyricsDisplaySettings> = _displaySettings.asStateFlow()
    private val _favorites = MutableStateFlow(readFavorites())
    val favorites: StateFlow<List<FavoriteLyricLine>> = _favorites.asStateFlow()
    private val _issues = MutableStateFlow(readIssues())
    val issues: StateFlow<List<LyricIssue>> = _issues.asStateFlow()

    fun updateDisplaySettings(value: LyricsDisplaySettings) {
        val safe = value.copy(
            portraitFontSizeSp = value.portraitFontSizeSp.coerceIn(16f, 38f),
            landscapeFontSizeSp = value.landscapeFontSizeSp.coerceIn(15f, 34f),
            activeFontWeight = value.activeFontWeight.coerceIn(500, 900),
            translationFontSizeSp = value.translationFontSizeSp.coerceIn(11f, 24f),
            lineSpacingDp = value.lineSpacingDp.coerceIn(4f, 30f),
            activeEmphasis = value.activeEmphasis.coerceIn(0.75f, 1.25f),
            bluetoothDelayMs = value.bluetoothDelayMs.coerceIn(-2_000L, 2_000L)
        )
        _displaySettings.value = safe
        prefs.edit().putString(KEY_DISPLAY, displayToJson(safe).toString()).apply()
    }

    fun resetDisplaySettings() = updateDisplaySettings(LyricsDisplaySettings())

    fun toggleFavorite(audioId: Long, title: String, artist: String, line: com.inspiremusic.model.LrcLine): Boolean {
        val existing = _favorites.value.firstOrNull { it.audioId == audioId && it.timeMs == line.timeMs && it.text == line.text }
        val updated = if (existing != null) {
            _favorites.value.filterNot { it.id == existing.id }
        } else {
            listOf(
                FavoriteLyricLine(
                    id = UUID.randomUUID().toString(), audioId = audioId, title = title,
                    artist = artist, timeMs = line.timeMs, text = line.text, translation = line.translation
                )
            ) + _favorites.value
        }.take(MAX_FAVORITES)
        _favorites.value = updated
        persistFavorites(updated)
        return existing == null
    }

    fun isFavorite(audioId: Long, line: com.inspiremusic.model.LrcLine): Boolean =
        _favorites.value.any { it.audioId == audioId && it.timeMs == line.timeMs && it.text == line.text }

    fun addIssue(audioId: Long, type: LyricIssueType, note: String) {
        val updated = (listOf(LyricIssue(UUID.randomUUID().toString(), audioId, type, note.trim().take(160))) + _issues.value)
            .take(MAX_ISSUES)
        _issues.value = updated
        persistIssues(updated)
    }

    fun saveVersion(audioId: Long, sourceName: String, rawContent: String): LyricVersion? {
        if (rawContent.isBlank()) return null
        val version = LyricVersion(
            id = UUID.randomUUID().toString(), audioId = audioId,
            sourceName = sourceName.take(48), createdAt = System.currentTimeMillis(), rawContent = rawContent
        )
        val songDir = File(versionsDir, audioId.toString()).apply { mkdirs() }
        File(songDir, "${version.createdAt}_${version.id}.json").writeText(versionToJson(version).toString(), Charsets.UTF_8)
        songDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_VERSIONS_PER_SONG)?.forEach { it.delete() }
        return version
    }

    fun versionsFor(audioId: Long): List<LyricVersion> = File(versionsDir, audioId.toString())
        .listFiles()
        ?.mapNotNull { file -> runCatching { versionFromJson(JSONObject(file.readText(Charsets.UTF_8))) }.getOrNull() }
        ?.sortedByDescending { it.createdAt }
        .orEmpty()

    fun exportBackup(): JSONObject = JSONObject()
        .put("version", 1)
        .put("display", displayToJson(_displaySettings.value))
        .put("favorites", JSONArray().apply { _favorites.value.forEach { put(favoriteToJson(it)) } })
        .put("issues", JSONArray().apply { _issues.value.forEach { put(issueToJson(it)) } })
        .put("lyricVersions", JSONArray().apply {
            versionsDir.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { file ->
                runCatching { put(JSONObject(file.readText(Charsets.UTF_8))) }
            }
        })

    fun importBackup(root: JSONObject) {
        root.optJSONObject("display")?.let { updateDisplaySettings(displayFromJson(it)) }
        root.optJSONArray("favorites")?.let { array ->
            val merged = (_favorites.value + (0 until array.length()).mapNotNull { favoriteFromJson(array.optJSONObject(it)) })
                .distinctBy { it.id }.sortedByDescending { it.createdAt }.take(MAX_FAVORITES)
            _favorites.value = merged
            persistFavorites(merged)
        }
        root.optJSONArray("issues")?.let { array ->
            val merged = (_issues.value + (0 until array.length()).mapNotNull { issueFromJson(array.optJSONObject(it)) })
                .distinctBy { it.id }.sortedByDescending { it.createdAt }.take(MAX_ISSUES)
            _issues.value = merged
            persistIssues(merged)
        }
        root.optJSONArray("lyricVersions")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val version = versionFromJson(item) ?: continue
                val dir = File(versionsDir, version.audioId.toString()).apply { mkdirs() }
                File(dir, "${version.createdAt}_${version.id}.json").writeText(item.toString(), Charsets.UTF_8)
            }
        }
    }

    private fun readDisplaySettings(): LyricsDisplaySettings = runCatching {
        displayFromJson(JSONObject(prefs.getString(KEY_DISPLAY, "{}") ?: "{}"))
    }.getOrDefault(LyricsDisplaySettings())

    private fun displayToJson(value: LyricsDisplaySettings) = JSONObject()
        .put("portraitFontSizeSp", value.portraitFontSizeSp.toDouble())
        .put("landscapeFontSizeSp", value.landscapeFontSizeSp.toDouble())
        .put("activeFontWeight", value.activeFontWeight)
        .put("translationFontSizeSp", value.translationFontSizeSp.toDouble())
        .put("lineSpacingDp", value.lineSpacingDp.toDouble())
        .put("alignment", value.alignment.name)
        .put("activeEmphasis", value.activeEmphasis.toDouble())
        .put("showTranslation", value.showTranslation)
        .put("bluetoothDelayMs", value.bluetoothDelayMs)

    private fun displayFromJson(json: JSONObject) = LyricsDisplaySettings(
        portraitFontSizeSp = json.optDouble("portraitFontSizeSp", 24.0).toFloat(),
        landscapeFontSizeSp = json.optDouble("landscapeFontSizeSp", 22.0).toFloat(),
        activeFontWeight = json.optInt("activeFontWeight", 900),
        translationFontSizeSp = json.optDouble("translationFontSizeSp", 15.0).toFloat(),
        lineSpacingDp = json.optDouble("lineSpacingDp", 12.0).toFloat(),
        alignment = runCatching { LyricTextAlignment.valueOf(json.optString("alignment", "START")) }.getOrDefault(LyricTextAlignment.START),
        activeEmphasis = json.optDouble("activeEmphasis", 1.0).toFloat(),
        showTranslation = json.optBoolean("showTranslation", true),
        bluetoothDelayMs = json.optLong("bluetoothDelayMs", 0L)
    )

    private fun readFavorites(): List<FavoriteLyricLine> = readArray(KEY_FAVORITES) { favoriteFromJson(it) }
    private fun persistFavorites(values: List<FavoriteLyricLine>) = persistArray(KEY_FAVORITES, values.map(::favoriteToJson))
    private fun favoriteToJson(value: FavoriteLyricLine) = JSONObject().put("id", value.id).put("audioId", value.audioId)
        .put("title", value.title).put("artist", value.artist).put("timeMs", value.timeMs).put("text", value.text)
        .put("translation", value.translation).put("createdAt", value.createdAt)
    private fun favoriteFromJson(json: JSONObject?): FavoriteLyricLine? = json?.let {
        FavoriteLyricLine(it.optString("id"), it.optLong("audioId"), it.optString("title"), it.optString("artist"),
            it.optLong("timeMs"), it.optString("text"), it.optString("translation").takeIf(String::isNotBlank), it.optLong("createdAt"))
    }?.takeIf { it.id.isNotBlank() && it.text.isNotBlank() }

    private fun readIssues(): List<LyricIssue> = readArray(KEY_ISSUES) { issueFromJson(it) }
    private fun persistIssues(values: List<LyricIssue>) = persistArray(KEY_ISSUES, values.map(::issueToJson))
    private fun issueToJson(value: LyricIssue) = JSONObject().put("id", value.id).put("audioId", value.audioId)
        .put("type", value.type.name).put("note", value.note).put("createdAt", value.createdAt)
    private fun issueFromJson(json: JSONObject?): LyricIssue? = json?.let {
        LyricIssue(it.optString("id"), it.optLong("audioId"),
            runCatching { LyricIssueType.valueOf(it.optString("type")) }.getOrDefault(LyricIssueType.TIMING),
            it.optString("note"), it.optLong("createdAt"))
    }?.takeIf { it.id.isNotBlank() }

    private fun versionToJson(value: LyricVersion) = JSONObject().put("id", value.id).put("audioId", value.audioId)
        .put("sourceName", value.sourceName).put("createdAt", value.createdAt).put("rawContent", value.rawContent)
    private fun versionFromJson(json: JSONObject?): LyricVersion? = json?.let {
        LyricVersion(it.optString("id"), it.optLong("audioId"), it.optString("sourceName"), it.optLong("createdAt"), it.optString("rawContent"))
    }?.takeIf { it.id.isNotBlank() && it.audioId > 0 && it.rawContent.isNotBlank() }

    private fun <T> readArray(key: String, convert: (JSONObject?) -> T?): List<T> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { convert(array.optJSONObject(it)) }
    }.getOrDefault(emptyList())
    private fun persistArray(key: String, values: List<JSONObject>) {
        prefs.edit().putString(key, JSONArray().apply { values.forEach { put(it) } }.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "lyrics_studio"
        const val KEY_DISPLAY = "display_v1"
        const val KEY_FAVORITES = "favorites_v1"
        const val KEY_ISSUES = "issues_v1"
        const val MAX_VERSIONS_PER_SONG = 12
        const val MAX_FAVORITES = 600
        const val MAX_ISSUES = 300
    }
}
