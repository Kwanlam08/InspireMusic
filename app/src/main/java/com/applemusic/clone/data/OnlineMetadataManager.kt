package com.applemusic.clone.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File

// iTunes API Models
data class ITunesResponse(val results: List<ITunesTrack>)
data class ITunesTrack(
    val wrapperType: String?,         // "track" / "collection" / "artist"
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

    // ── 专辑在线信息缓存（流派/简介/发行日期） ──
    // 进程内 LRU + 磁盘 JSON 文件；首次联网 → 之后直接读本地
    private val albumInfoMemCache = LinkedHashMap<String, AlbumOnlineInfo?>(32, 0.75f, true)
    private val albumInfoMemMax = 400
    private val albumInfoDiskLock = Any()
    private val gson = Gson()
    @Volatile private var appContext: Context? = null
    private fun ensureContext(): Context? {
        if (appContext != null) return appContext
        appContext = try {
            // 通过反射拿到 Application 上下文，避免每次调用都要传 Context
            val appCls = Class.forName("android.app.ActivityThread")
            val app = appCls.getMethod("currentApplication").invoke(null) as? android.app.Application
            app
        } catch (_: Exception) { null } as Context?
        return appContext
    }
    // 缓存 key：album + artist（不带 firstSongTitle，避免同一专辑不同入口 key 不同）
    private fun albumKey(album: String, artist: String, song: String = ""): String {
        val norm: (String) -> String = { it.lowercase().trim().replace(Regex("\\s+"), " ") }
        return "${norm(album)}|${norm(artist)}"
    }
    // 安全的文件名（替代 hashCode，避免冲突）
    private fun safeFileName(key: String): String {
        val sb = StringBuilder(key.length)
        for (c in key) sb.append(if (c.isLetterOrDigit()) c else '_')
        return sb.toString().take(120)
    }
    private fun albumCacheDir(): File? {
        val ctx = ensureContext() ?: return null
        val dir = File(ctx.filesDir, "album_info_cache"); dir.mkdirs(); return dir
    }
    private fun readDiskAlbumCache(key: String): AlbumOnlineInfo? {
        val dir = albumCacheDir() ?: return null
        return try {
            val f = File(dir, safeFileName(key) + ".json")
            if (!f.exists()) null else gson.fromJson(f.readText(), AlbumOnlineInfo::class.java)
        } catch (_: Exception) { null }
    }
    private fun writeDiskAlbumCache(key: String, info: AlbumOnlineInfo?) {
        val dir = albumCacheDir() ?: return
        try {
            val f = File(dir, safeFileName(key) + ".json")
            if (info == null) { if (f.exists()) f.delete(); return }
            f.writeText(gson.toJson(info))
        } catch (_: Exception) {}
    }
    // 与磁盘已有缓存合并：取"非空"字段，避免新一次部分请求把已有的流派/简介清空
    private fun mergeWithDisk(key: String, fresh: AlbumOnlineInfo?): AlbumOnlineInfo? {
        val old = readDiskAlbumCache(key) ?: return fresh
        if (fresh == null) return old
        return AlbumOnlineInfo(
            genre = fresh.genre?.takeIf { it.isNotBlank() } ?: old.genre,
            releaseDate = fresh.releaseDate?.takeIf { it.isNotBlank() } ?: old.releaseDate,
            label = fresh.label?.takeIf { it.isNotBlank() } ?: old.label,
            description = fresh.description?.takeIf { it.isNotBlank() } ?: old.description
        )
    }

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
        // ── 1) 进程内 LRU 命中 → 立即返回（零网络/零磁盘） ──
        val key = albumKey(albumName, artist, firstSongTitle)
        synchronized(albumInfoDiskLock) {
            albumInfoMemCache[key]?.let { return it }
        }
        // ── 2) 磁盘 JSON 命中 → 写回内存，返回 ──
        readDiskAlbumCache(key)?.let { diskHit ->
            synchronized(albumInfoDiskLock) {
                albumInfoMemCache[key] = diskHit
                if (albumInfoMemCache.size > albumInfoMemMax) {
                    val firstKey = albumInfoMemCache.keys.iterator().next()
                    albumInfoMemCache.remove(firstKey)
                }
            }
            return diskHit
        }

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
                    // 优先按 wrapperType=="collection" 找专辑封装
                    // 备选：trackName 为空 + 有 releaseDate/genre/copyright 的
                    // 再备选：collectionName 与搜索词匹配的
                    val album = lookupResp.results.firstOrNull { it.wrapperType == "collection" }
                        ?: lookupResp.results.firstOrNull {
                            it.trackName.isNullOrBlank() && (it.genre != null || it.releaseDate != null || it.copyright != null)
                        }
                        ?: lookupResp.results.firstOrNull { it.wrapperType != "track" }
                        ?: lookupResp.results.firstOrNull()
                    debugLog.append("cid=$cid, lookupResults=${lookupResp.results.size}, albumField=${album?.wrapperType}/${album?.trackName}/${album?.releaseDate}/${album?.genre}/${album?.copyright}\n")

                    if (releaseDate == null) {
                        // iTunes 返回的 releaseDate 可能是 "2020-01-15T12:00:00Z" 或 "2020"
                        // 兼容两种：取前 10 字符能得 "2020-01-15"；若短于 10 则原样保留
                        releaseDate = album?.releaseDate?.takeIf { it.isNotBlank() }?.let { raw ->
                            if (raw.length >= 10 && raw[4] == '-' && raw[7] == '-') raw.take(10)
                            else raw
                        }
                    }
                    if (label == null) {
                        label = album?.copyright?.takeIf { it.isNotBlank() }
                    }
                    if (genre == null) {
                        genre = album?.genre?.takeIf { it.isNotBlank() }
                    }
                    debugLog.append("final: genre=${genre ?: "-"}, date=${releaseDate ?: "-"}, label=${label ?: "-"}\n")
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

        val fresh = if (genre == null && releaseDate == null && label == null && description == null) null
                    else AlbumOnlineInfo(genre, releaseDate, label, description)

        // 合并磁盘已有缓存：保留之前已查到的字段，本次未返回的字段不丢
        val merged = mergeWithDisk(key, fresh)

        // 写回缓存（包括 null：负缓存，避免每次重复 iTunes/MusicBrainz 联网）
        synchronized(albumInfoDiskLock) {
            albumInfoMemCache[key] = merged
            if (albumInfoMemCache.size > albumInfoMemMax) {
                val firstKey = albumInfoMemCache.keys.iterator().next()
                albumInfoMemCache.remove(firstKey)
            }
        }
        writeDiskAlbumCache(key, merged)
        return merged
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
