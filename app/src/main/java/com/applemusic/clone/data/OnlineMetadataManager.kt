package com.applemusic.clone.data

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
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

// MusicBrainz API Models
data class MBSearchResponse(
    @SerializedName("release-groups") val releaseGroups: List<MBReleaseGroup>?
)

data class MBReleaseGroup(
    val id: String?,
    val title: String?,
    val annotation: String?
)

interface MusicBrainzApi {
    @GET("ws/2/release-group")
    suspend fun searchReleaseGroup(
        @Header("User-Agent") userAgent: String,
        @Query("query") query: String,
        @Query("fmt") fmt: String = "json",
        @Query("limit") limit: Int = 3
    ): MBSearchResponse

    @GET("ws/2/release-group/{mbid}")
    suspend fun getReleaseGroup(
        @Header("User-Agent") userAgent: String,
        @Path("mbid") mbid: String,
        @Query("inc") inc: String = "annotation",
        @Query("fmt") fmt: String = "json"
    ): MBReleaseGroup
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

    private val musicBrainzRetrofit = Retrofit.Builder()
        .baseUrl("https://musicbrainz.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val itunesApi: ITunesApi = itunesRetrofit.create(ITunesApi::class.java)
    val lrclibApi: LrcLibApi = lrclibRetrofit.create(LrcLibApi::class.java)
    val musicBrainzApi: MusicBrainzApi = musicBrainzRetrofit.create(MusicBrainzApi::class.java)

    private val MB_USER_AGENT = "AppleMusicClone/1.0 (android.music.app)"

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
        // iTunes: genre, releaseDate, label
        var genre: String? = null
        var releaseDate: String? = null
        var label: String? = null
        var debugLog = StringBuilder()

        // 多种搜索策略，按命中率从高到低
        val searchStrategies = mutableListOf<String>()
        if (firstSongTitle.isNotBlank() && artist.isNotBlank()) {
            searchStrategies.add("$firstSongTitle $artist")
        }
        if (albumName.isNotBlank() && artist.isNotBlank()) {
            searchStrategies.add("$albumName $artist")
        }
        if (firstSongTitle.isNotBlank()) {
            searchStrategies.add(firstSongTitle)
        }
        if (albumName.isNotBlank()) {
            searchStrategies.add(albumName)
        }

        for ((idx, term) in searchStrategies.withIndex()) {
            if (genre != null && releaseDate != null && label != null) break
            try {
                debugLog.append("[$idx] '$term' -> ")
                val searchResp = itunesApi.searchTrack(term)
                val track = searchResp.results.firstOrNull()
                if (track == null) {
                    debugLog.append("no results\n")
                    continue
                }

                // 搜索结果中直接带 primaryGenreName（流派），优先用此值
                if (genre == null) {
                    genre = track.genre?.takeIf { it.isNotBlank() }
                }

                val cid = track.collectionId
                if (cid != null) {
                    val lookupResp = itunesApi.lookupAlbum(cid)
                    // 取第一个看起来像专辑（有 releaseDate 或 genre 或没有 trackName）的结果
                    val album = lookupResp.results.firstOrNull {
                        it.trackName.isNullOrBlank() && (it.genre != null || it.releaseDate != null || it.copyright != null)
                    } ?: lookupResp.results.firstOrNull()

                    if (releaseDate == null) {
                        releaseDate = album?.releaseDate?.takeIf { it.isNotBlank() }?.take(10)
                    }
                    if (label == null) {
                        label = album?.copyright?.takeIf { it.isNotBlank() }
                    }
                    if (genre == null) {
                        genre = album?.genre?.takeIf { it.isNotBlank() }
                    }
                    debugLog.append("cid=$cid, genre=${genre ?: "-"}, date=${releaseDate ?: "-"}\n")
                } else {
                    debugLog.append("no collectionId\n")
                }
            } catch (e: Exception) {
                debugLog.append("error: ${e.message}\n")
            }
        }
        android.util.Log.d("OnlineMetadata", "fetchAlbumInfo(album='$albumName', artist='$artist', song='$firstSongTitle'):\n$debugLog")

        // MusicBrainz: album description/annotation
        val description = fetchMusicBrainzDescription(albumName, artist)

        // Return null only if we got absolutely nothing
        if (genre == null && releaseDate == null && label == null && description == null) return null

        return AlbumOnlineInfo(
            genre = genre,
            releaseDate = releaseDate,
            label = label,
            description = description
        )
    }

    private suspend fun fetchMusicBrainzDescription(albumName: String, artist: String): String? {
        return try {
            // Step 1: Search for the release-group
            // 尝试多种查询格式以提高命中率
            val queries = mutableListOf<String>()
            if (albumName.isNotBlank() && artist.isNotBlank()) {
                queries.add("releasegroup:\"$albumName\" AND artist:\"$artist\"")
                queries.add("\"$albumName\" $artist")
            }
            if (albumName.isNotBlank()) {
                queries.add("\"$albumName\"")
            }

            var mbid: String? = null
            for (q in queries) {
                try {
                    val searchResp = musicBrainzApi.searchReleaseGroup(
                        userAgent = MB_USER_AGENT,
                        query = q
                    )
                    mbid = searchResp.releaseGroups?.firstOrNull()?.id
                    if (mbid != null) break
                } catch (_: Exception) {}
            }
            if (mbid == null) {
                android.util.Log.d("OnlineMetadata", "MusicBrainz: no release-group found for album='$albumName' artist='$artist'")
                return null
            }

            // Step 2: Fetch full release-group with annotation
            kotlinx.coroutines.delay(300) // MusicBrainz rate limit: 1 req/sec
            val group = musicBrainzApi.getReleaseGroup(
                userAgent = MB_USER_AGENT,
                mbid = mbid
            )
            val annotation = group.annotation?.takeIf { it.isNotBlank() }
            android.util.Log.d("OnlineMetadata", "MusicBrainz annotation for $albumName: ${if (annotation != null) "found (${annotation.length} chars)" else "empty"}")
            annotation
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
