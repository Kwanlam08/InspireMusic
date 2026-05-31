package com.applemusic.clone.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// iTunes API Models
data class ITunesResponse(val results: List<ITunesTrack>)
data class ITunesTrack(
    val trackName: String?,
    val artistName: String?,
    val collectionName: String?,
    @SerializedName("collectionId") val collectionId: Long?,
    @SerializedName("artworkUrl100") val artworkUrl: String?,
    @SerializedName("trackNumber") val trackNumber: Int?,
    @SerializedName("discNumber") val discNumber: Int?,
    @SerializedName("primaryGenreName") val genre: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    val copyright: String?,
    val description: String?
)

interface ITunesApi {
    @GET("search?entity=song&limit=1")
    suspend fun searchTrack(@Query("term") term: String): ITunesResponse

    @GET("lookup?entity=album")
    suspend fun lookupAlbum(@Query("id") albumId: Long): ITunesResponse
}

data class ItunesMetadata(val artworkUrl: String?, val trackNumber: Int?, val discNumber: Int?)
data class AlbumOnlineInfo(val genre: String?, val releaseDate: String?, val label: String?, val description: String?)

// LrcLib API Models
data class LrcLibResponse(
    val syncedLyrics: String?,
    val plainLyrics: String?
)

interface LrcLibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
    ): LrcLibResponse
}

object OnlineMetadataManager {
    private val itunesRetrofit = Retrofit.Builder()
        .baseUrl("https://itunes.apple.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val lrclibRetrofit = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val itunesApi: ITunesApi = itunesRetrofit.create(ITunesApi::class.java)
    val lrclibApi: LrcLibApi = lrclibRetrofit.create(LrcLibApi::class.java)

    suspend fun fetchItunesMetadata(title: String, artist: String): ItunesMetadata? {
        return try {
            val response = itunesApi.searchTrack("$title $artist")
            val track = response.results.firstOrNull() ?: return null
            ItunesMetadata(
                artworkUrl = track.artworkUrl?.replace("100x100bb", "600x600bb"),
                trackNumber = track.trackNumber,
                discNumber = track.discNumber
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchAlbumInfo(albumName: String, artist: String, firstSongTitle: String = ""): AlbumOnlineInfo? {
        return try {
            // 先搜一首歌获取专辑 ID
            val searchTerm = if (firstSongTitle.isNotBlank()) "$firstSongTitle $artist" else "$albumName $artist"
            val searchResp = itunesApi.searchTrack(searchTerm)
            val track = searchResp.results.firstOrNull() ?: return null
            val cid = track.collectionId ?: return null

            // 用专辑 ID 查详情
            val lookupResp = itunesApi.lookupAlbum(cid)
            val album = lookupResp.results.firstOrNull() ?: return null

            AlbumOnlineInfo(
                genre = album.genre?.takeIf { it.isNotBlank() },
                releaseDate = album.releaseDate?.takeIf { it.isNotBlank() }?.take(10),
                label = album.copyright?.takeIf { it.isNotBlank() },
                description = album.description?.takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchLyrics(title: String, artist: String): String? {
        return try {
            val response = lrclibApi.getLyrics(title, artist)
            response.syncedLyrics ?: response.plainLyrics
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
