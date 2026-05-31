package com.applemusic.clone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ItunesMetadataClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class SongInfo(val title: String, val artist: String, val album: String, val artworkUrl: String?)
    data class AlbumInfo(val albumName: String, val artist: String, val artworkUrl: String?, val description: String?, val genre: String?, val releaseDate: String?, val trackCount: Int)

    private suspend fun searchJson(term: String, entity: String, limit: Int = 1): JSONObject? = withContext(Dispatchers.IO) {
        // Try without country filter first (best for non-US music)
        val queries = listOf(
            "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&entity=$entity&limit=$limit&media=music",
            "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&entity=$entity&limit=$limit&country=JP",
            "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&entity=$entity&limit=$limit&country=CN"
        )
        for (url in queries) {
            try {
                val resp = client.newCall(Request.Builder().url(url).build()).execute()
                val json = JSONObject(resp.body?.string() ?: "")
                if (json.optJSONArray("results")?.length() ?: 0 > 0) return@withContext json
            } catch (_: Exception) {}
        }
        null
    }

    suspend fun searchAlbum(albumName: String, artist: String = ""): Result<AlbumInfo> {
        // Try with artist first, then without
        val json = searchJson("$albumName $artist", "album")
            ?: searchJson(albumName, "album")
            ?: return Result.failure(Exception("Not found"))

        return try {
            val results = json.getJSONArray("results")
            val album = results.getJSONObject(0)
            Result.success(AlbumInfo(
                albumName = album.optString("collectionName", albumName),
                artist = album.optString("artistName", artist),
                artworkUrl = album.optString("artworkUrl100", null)?.replace("100x100", "600x600"),
                description = null,
                genre = album.optString("primaryGenreName", null)?.takeIf { it.isNotBlank() },
                releaseDate = album.optString("releaseDate", null)?.takeIf { it.isNotBlank() }?.take(10),
                trackCount = album.optInt("trackCount", 0)
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun searchSong(title: String, artist: String = ""): Result<SongInfo> {
        val json = searchJson("$title $artist", "song")
            ?: return Result.failure(Exception("Not found"))

        return try {
            val track = json.getJSONArray("results").getJSONObject(0)
            Result.success(SongInfo(
                title = track.optString("trackName", title),
                artist = track.optString("artistName", artist),
                album = track.optString("collectionName", ""),
                artworkUrl = track.optString("artworkUrl100", null)?.replace("100x100", "600x600")
            ))
        } catch (e: Exception) { Result.failure(e) }
    }
}
