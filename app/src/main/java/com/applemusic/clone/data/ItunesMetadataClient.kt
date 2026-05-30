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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class SongInfo(val title: String, val artist: String, val album: String, val artworkUrl: String?)
    data class AlbumInfo(val albumName: String, val artist: String, val artworkUrl: String?, val description: String?, val genre: String?, val releaseDate: String?, val trackCount: Int)

    suspend fun searchSong(title: String, artist: String = ""): Result<SongInfo> = withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode("$title $artist".trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&entity=song&limit=1&country=US"
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JSONObject(resp.body?.string() ?: "")
            val results = json.optJSONArray("results")
            if (results == null || results.length() == 0) return@withContext Result.failure(Exception("Not found"))

            val track = results.getJSONObject(0)
            Result.success(SongInfo(
                title = track.optString("trackName", title),
                artist = track.optString("artistName", artist),
                album = track.optString("collectionName", ""),
                artworkUrl = track.optString("artworkUrl100", null)?.replace("100x100", "600x600")
            ))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun searchAlbum(albumName: String, artist: String = ""): Result<AlbumInfo> = withContext(Dispatchers.IO) {
        try {
            val query = URLEncoder.encode("$albumName $artist".trim(), "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&entity=album&limit=1&country=US"
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            val json = JSONObject(resp.body?.string() ?: "")
            val results = json.optJSONArray("results")
            if (results == null || results.length() == 0) return@withContext Result.failure(Exception("Not found"))

            val album = results.getJSONObject(0)
            // Lookup for more details
            val albumId = album.optLong("collectionId", -1)
            var description: String? = null
            var genre: String? = null
            var releaseDate: String? = album.optString("releaseDate", null)
            var trackCount = album.optInt("trackCount", 0)

            if (albumId > 0) {
                try {
                    val lookupUrl = "https://itunes.apple.com/lookup?id=$albumId&entity=song&country=US"
                    val lookupResp = client.newCall(Request.Builder().url(lookupUrl).build()).execute()
                    val lookupJson = JSONObject(lookupResp.body?.string() ?: "")
                    val lookupResults = lookupJson.optJSONArray("results")
                    if (lookupResults != null && lookupResults.length() > 0) {
                        val detail = lookupResults.getJSONObject(0)
                        genre = detail.optString("primaryGenreName", null)
                        releaseDate = detail.optString("releaseDate", releaseDate)
                        trackCount = detail.optInt("trackCount", trackCount)
                        // iTunes doesn't provide description, use genre + release as info
                        val descParts = mutableListOf<String>()
                        if (!genre.isNullOrBlank()) descParts.add(genre)
                        if (!releaseDate.isNullOrBlank()) descParts.add(releaseDate.take(10))
                        if (descParts.isNotEmpty()) description = descParts.joinToString(" · ")
                    }
                } catch (_: Exception) {}
            }

            Result.success(AlbumInfo(
                albumName = album.optString("collectionName", albumName),
                artist = album.optString("artistName", artist),
                artworkUrl = album.optString("artworkUrl100", null)?.replace("100x100", "600x600"),
                description = description,
                genre = genre,
                releaseDate = releaseDate,
                trackCount = trackCount
            ))
        } catch (e: Exception) { Result.failure(e) }
    }
}
