package com.applemusic.clone.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import com.applemusic.clone.model.AudioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class AudioRepository(private val context: Context) {

    private val metadataDao = AppDatabase.getInstance(context).metadataDao()
    private val artworkExtensions = setOf("jpg", "jpeg", "png", "webp")
    private val preferredArtworkNames = listOf("cover", "folder", "front", "album", "artwork")

    private fun highResolutionArtworkUrl(url: String): String {
        return url
            .replace("100x100bb", "1200x1200bb")
            .replace("600x600bb", "1200x1200bb")
            .replace("100x100", "1200x1200")
            .replace("600x600", "1200x1200")
    }

    private fun findAlbumFolderArtwork(audioPath: String, albumName: String = ""): String? {
        val parent = audioPath.takeIf { it.isNotBlank() }?.let { File(it).parentFile } ?: return null
        val candidates = parent.listFiles { file ->
            file.isFile &&
                file.length() > 0L &&
                file.extension.lowercase(Locale.ROOT) in artworkExtensions
        }?.toList().orEmpty()
        if (candidates.isEmpty()) return null

        val normalizedAlbum = albumName.trim().lowercase(Locale.ROOT)
        val audioStem = File(audioPath).nameWithoutExtension.trim().lowercase(Locale.ROOT)
        val exactLocalArtwork = candidates.firstOrNull { file ->
            val name = file.nameWithoutExtension.trim().lowercase(Locale.ROOT)
            name == normalizedAlbum || name == audioStem
        }
        val preferred = preferredArtworkNames
            .asSequence()
            .mapNotNull { targetName ->
                candidates.firstOrNull { file ->
                    file.nameWithoutExtension.equals(targetName, ignoreCase = true)
                }
            }
            .firstOrNull()

        val chosen = exactLocalArtwork
            ?: preferred
            ?: candidates.maxWithOrNull(compareBy<File> { it.length() }.thenBy { it.lastModified() })
            ?: return null
        return Uri.fromFile(chosen).toString()
    }

    private fun findInternalCachedArtwork(audioId: Long): String? {
        val dir = File(context.filesDir, "artwork")
        return artworkExtensions
            .asSequence()
            .map { File(dir, "$audioId.$it") }
            .firstOrNull { it.exists() && it.length() > 0L }
            ?.let { Uri.fromFile(it).toString() }
    }

    private fun validCachedArtworkUri(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return when {
            value.startsWith("file:", ignoreCase = true) -> {
                val path = Uri.parse(value).path ?: return null
                value.takeIf { File(path).exists() && File(path).length() > 0L }
            }
            value.startsWith("http", ignoreCase = true) -> value
            value.startsWith("content:", ignoreCase = true) -> value
            else -> null
        }
    }

    private fun needsArtworkUpgrade(value: String?): Boolean {
        if (value.isNullOrBlank() || !value.startsWith("file:", ignoreCase = true)) return false
        val path = Uri.parse(value).path ?: return false
        val file = File(path)
        val cacheDir = File(context.filesDir, "artwork")
        return runCatching {
            file.exists() &&
                file.canonicalFile.parentFile == cacheDir.canonicalFile &&
                file.length() in 1L until 250_000L
        }.getOrDefault(false)
    }

    private suspend fun fetchRemoteArtworkUrl(title: String, artist: String, album: String): String? {
        val albumArtwork = if (album.isNotBlank()) {
            ItunesMetadataClient.searchAlbum(album, artist).getOrNull()?.artworkUrl
        } else {
            null
        }
        val trackArtwork = OnlineMetadataManager.fetchItunesMetadata(title, artist)?.artworkUrl
        return (albumArtwork ?: trackArtwork)?.takeIf { it.isNotBlank() }?.let(::highResolutionArtworkUrl)
    }

    // 联网下载并缓存封面到本地文件（首次），返回本地 file:// URI
    private suspend fun cacheArtworkToFile(audioId: Long, remoteUrl: String): String? = withContext(Dispatchers.IO) {
        var existingUri: String? = null
        try {
            val artDir = File(context.filesDir, "artwork"); artDir.mkdirs()
            val target = File(artDir, "$audioId.jpg")
            // 如果已存在且大小 > 0，直接复用
            if (target.exists() && target.length() > 0) {
                existingUri = Uri.fromFile(target).toString()
                if (target.length() >= 250_000L) return@withContext existingUri
            }
            val url = URL(highResolutionArtworkUrl(remoteUrl))
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect()
            if (conn.responseCode !in 200..299) return@withContext existingUri
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            Uri.fromFile(target).toString()
        } catch (_: Exception) { existingUri }
    }

    suspend fun getLocalAudioFiles(): List<AudioItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<AudioItem>()
        // Artwork is explicitly chosen in Settings. Never fetch covers implicitly.
        val allowOnlineArtwork = false

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            "album_artist",
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        try {
            context.contentResolver.query(
                collection, projection, null, null, "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val albumArtistCol = cursor.getColumnIndex("album_artist")
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val duration = cursor.getLong(durationCol)
                    if (duration < 30_000) continue

                    val id = cursor.getLong(idCol)
                    val data = cursor.getString(dataCol) ?: ""

                    val rawTitle = cursor.getString(titleCol)
                    val title = if (rawTitle.isNullOrBlank() || rawTitle.contains("unknown", ignoreCase = true)) {
                        File(data).nameWithoutExtension
                    } else rawTitle

                    val rawArtist = cursor.getString(artistCol)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist.contains("unknown", ignoreCase = true)) {
                        ""
                    } else rawArtist

                    val rawAlbum = cursor.getString(albumCol)
                    val album = if (rawAlbum.isNullOrBlank() || rawAlbum.contains("unknown", ignoreCase = true)) {
                        "未知专辑"
                    } else rawAlbum

                    val albumId = cursor.getLong(albumIdCol)
                    val rawAlbumArtist = albumArtistCol.takeIf { it >= 0 }?.let(cursor::getString)
                    val albumArtist = rawAlbumArtist
                        ?.takeUnless { it.isBlank() || it.contains("unknown", ignoreCase = true) }
                        ?: artist
                    val rawTrack = cursor.getInt(trackCol)
                    val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val mediaStoreModified = cursor.getLong(dateModifiedCol)
                    val dateModifiedMs = when {
                        mediaStoreModified > 9_999_999_999L -> mediaStoreModified
                        mediaStoreModified > 0L -> mediaStoreModified * 1000L
                        data.isNotBlank() -> File(data).lastModified().coerceAtLeast(0L)
                        else -> 0L
                    }
                    val cleanTrack = if (rawTrack > 0) rawTrack % 1000 else 0
                    val localDiscNumber = if (rawTrack >= 1000) rawTrack / 1000 else 1
                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val folderArtworkUri = findAlbumFolderArtwork(data, album)
                    val importedCachedArtworkUri = findInternalCachedArtwork(id)

                    var cachedMeta = metadataDao.getMetadata(id)
                    val hadCachedMeta = cachedMeta != null
                    var hasEmbedded = cachedMeta?.hasEmbeddedArt ?: false

                    if (cachedMeta == null) {
                        // 首次扫描跳过重操作，先返回基础信息；后台异步补齐
                        hasEmbedded = false
                        cachedMeta = MetadataEntity(id, false, folderArtworkUri ?: importedCachedArtworkUri, null, null, null)
                        metadataDao.insert(cachedMeta)

                        // 后台异步获取元数据
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                var emb = false
                                try {
                                    val retriever = MediaMetadataRetriever()
                                    retriever.setDataSource(data)
                                    emb = retriever.embeddedPicture != null
                                    retriever.release()
                                } catch (_: Exception) {}
                                var fetchedArt: String? = folderArtworkUri ?: importedCachedArtworkUri
                                var fetchedTrk: Int? = null
                                var fetchedDis: Int? = null
                                if (fetchedArt == null || cleanTrack == 0) {
                                    val meta = OnlineMetadataManager.fetchItunesMetadata(title, artist)
                                    if (fetchedArt == null && allowOnlineArtwork) {
                                        // 把 iTunes 远程 URL 立刻下载到本地并缓存 file://，
                                        // 之后再也不需要联网就能显示艺人头像
                                        val remote = fetchRemoteArtworkUrl(title, artist, album)
                                        if (!remote.isNullOrBlank()) {
                                            val localUri = cacheArtworkToFile(id, remote)
                                            fetchedArt = localUri ?: remote
                                        }
                                    }
                                    if (cleanTrack == 0) fetchedTrk = meta?.trackNumber
                                    fetchedDis = meta?.discNumber
                                }
                                // 优先级: 本地 .lrc > 内嵌歌词 > 网络歌词
                                var lyricsP: String? = null
                                if (findLyricsFile(data) == null) {
                                    // 尝试提取内嵌歌词
                                    val embeddedLyrics = EmbeddedLyricsExtractor.extract(data)
                                    if (embeddedLyrics != null) {
                                        val dir = File(context.filesDir, "lyrics"); dir.mkdirs()
                                        val f = File(dir, "$id.lrc"); f.writeText(embeddedLyrics)
                                        lyricsP = f.absolutePath
                                    } else {
                                        // 回退到网络歌词：拉到本地 .lrc 文件后写入缓存
                                        val onlineLyrics = OnlineMetadataManager.fetchLyrics(title, artist, album, duration)
                                        if (onlineLyrics != null) {
                                            val dir = File(context.filesDir, "lyrics"); dir.mkdirs()
                                            val f = File(dir, "$id.lrc"); f.writeText(onlineLyrics)
                                            lyricsP = f.absolutePath
                                        }
                                    }
                                }
                                metadataDao.insert(MetadataEntity(id, emb, fetchedArt, lyricsP, fetchedTrk, fetchedDis))
                            } catch (_: Exception) {}
                        }
                    } else if (cachedMeta.fetchedLyricsPath == null && findLyricsFile(data) == null) {
                        // 已缓存但没有歌词 → 异步补尝内嵌歌词（针对老版本缓存）
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val embeddedLyrics = EmbeddedLyricsExtractor.extract(data)
                                if (embeddedLyrics != null) {
                                    val dir = File(context.filesDir, "lyrics"); dir.mkdirs()
                                    val f = File(dir, "$id.lrc"); f.writeText(embeddedLyrics)
                                    metadataDao.insert(cachedMeta.copy(fetchedLyricsPath = f.absolutePath))
                                }
                            } catch (_: Exception) {}
                        }
                    } else {
                        val remoteUrl = cachedMeta.fetchedAlbumArtUrl
                        if (remoteUrl != null && remoteUrl.startsWith("http")) {
                            // 老版本缓存的远程 URL，转成本地文件并把 DB 也更新掉
                            GlobalScope.launch(Dispatchers.IO) {
                                try {
                                    val localUri = cacheArtworkToFile(id, remoteUrl)
                                    if (localUri != null) {
                                        metadataDao.insert(cachedMeta.copy(fetchedAlbumArtUrl = localUri))
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    // 优先使用本地缓存的 file:// URI；其次 iTunes URL；最后 MediaStore albumart
                    val localArtworkOverride = folderArtworkUri ?: importedCachedArtworkUri
                    val cachedArtwork = validCachedArtworkUri(cachedMeta!!.fetchedAlbumArtUrl)
                    if (hadCachedMeta) {
                        when {
                            localArtworkOverride != null && cachedArtwork != localArtworkOverride -> {
                                GlobalScope.launch(Dispatchers.IO) {
                                    try {
                                        metadataDao.insert(cachedMeta.copy(fetchedAlbumArtUrl = localArtworkOverride))
                                    } catch (_: Exception) {}
                                }
                            }
                            allowOnlineArtwork && localArtworkOverride == null &&
                                (cachedArtwork == null || needsArtworkUpgrade(cachedArtwork)) -> {
                                GlobalScope.launch(Dispatchers.IO) {
                                    try {
                                        val remote = fetchRemoteArtworkUrl(title, artist, album)
                                        if (!remote.isNullOrBlank()) {
                                            val localUri = cacheArtworkToFile(id, remote)
                                            metadataDao.insert(cachedMeta.copy(fetchedAlbumArtUrl = localUri ?: remote))
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }

                    val artUri = when {
                        localArtworkOverride != null -> Uri.parse(localArtworkOverride)
                        cachedArtwork != null -> Uri.parse(cachedArtwork)
                        cachedMeta.hasEmbeddedArt || albumId > 0 -> Uri.parse("content://media/external/audio/albumart/$albumId")
                        else -> null
                    }

                    val lyricsPath = findLyricsFile(data) ?: cachedMeta.fetchedLyricsPath
                    val finalTrack = cachedMeta.fetchedTrackNumber ?: cleanTrack
                    // 优先用 MediaStore 解析出的碟号（rawTrack/1000），然后才是 iTunes
                    val finalDisc = if (localDiscNumber > 1) localDiscNumber else (cachedMeta.fetchedDiscNumber ?: localDiscNumber)

                    audioList.add(
                        AudioItem(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            uri = contentUri,
                            albumArtUri = artUri,
                            data = data,
                            lyricsPath = lyricsPath,
                            trackNumber = finalTrack,
                            discNumber = finalDisc,
                            sizeBytes = sizeBytes,
                            dateModifiedMs = dateModifiedMs,
                            albumId = albumId,
                            albumArtist = albumArtist
                        )
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        return@withContext audioList
    }

    suspend fun cacheLyricsForAudio(audioId: Long, lyrics: String): String? = withContext(Dispatchers.IO) {
        if (lyrics.isBlank()) return@withContext null
        try {
            val dir = File(context.filesDir, "lyrics")
            dir.mkdirs()
            val target = File(dir, "$audioId.lrc")
            target.writeText(lyrics, Charsets.UTF_8)

            val current = metadataDao.getMetadata(audioId)
            metadataDao.insert(
                current?.copy(fetchedLyricsPath = target.absolutePath)
                    ?: MetadataEntity(audioId, false, null, target.absolutePath, null, null)
            )
            target.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    suspend fun clearLyricsCacheForAudio(audioId: Long): Boolean = withContext(Dispatchers.IO) {
        val target = File(File(context.filesDir, "lyrics"), "$audioId.lrc")
        val deleted = !target.exists() || target.delete()
        metadataDao.clearLyricsPath(audioId)
        deleted
    }

    suspend fun clearAllLyricsCache(): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "lyrics")
        val deleted = dir.listFiles { file -> file.isFile && file.extension.equals("lrc", ignoreCase = true) }
            ?.count { !it.exists() || it.delete() }
            ?: 0
        metadataDao.clearAllLyricsPaths()
        deleted
    }

    private fun findLyricsFile(audioPath: String): String? {
        if (audioPath.isEmpty()) return null
        val file = File(audioPath)
        val nameWithoutExt = file.nameWithoutExtension
        val lrcFile = File(file.parent, "$nameWithoutExt.lrc")
        return if (lrcFile.exists()) lrcFile.absolutePath else null
    }

    suspend fun refreshArtworkForAudio(song: AudioItem): String? = withContext(Dispatchers.IO) {
        val remote = fetchRemoteArtworkUrl(song.title, song.artist, song.album) ?: return@withContext null
        val cached = cacheArtworkToFile(song.id, remote) ?: return@withContext null
        val current = metadataDao.getMetadata(song.id)
        metadataDao.insert(
            current?.copy(fetchedAlbumArtUrl = cached)
                ?: MetadataEntity(song.id, false, cached, null, null, null)
        )
        cached
    }

    /** Keeps one downloaded cover for an album and points every track at that same file. */
    suspend fun refreshArtworkForAlbum(songs: List<AudioItem>): String? = withContext(Dispatchers.IO) {
        val seed = songs.firstOrNull() ?: return@withContext null
        val remote = fetchRemoteArtworkUrl(seed.title, seed.artist, seed.album) ?: return@withContext null
        val cached = cacheArtworkToFile(seed.id, remote) ?: return@withContext null
        applySharedArtwork(songs, cached)
        cached
    }

    /** Migrates legacy one-file-per-song artwork into one shared file per album. */
    suspend fun consolidateArtworkCache(songs: List<AudioItem>) = withContext(Dispatchers.IO) {
        val artworkDir = File(context.filesDir, "artwork")
        songs.groupBy(::albumCacheKey)
            .values
            .forEach { albumSongs ->
                val internalFiles = albumSongs.mapNotNull { song ->
                    val uri = validCachedArtworkUri(metadataDao.getMetadata(song.id)?.fetchedAlbumArtUrl)
                    uri?.takeIf { isInternalArtworkFile(it, artworkDir) }?.let { File(Uri.parse(it).path!!) }
                }.distinctBy { it.absolutePath }
                val shared = internalFiles.maxByOrNull { it.length() } ?: return@forEach
                applySharedArtwork(albumSongs, Uri.fromFile(shared).toString())
                internalFiles.filter { it.absolutePath != shared.absolutePath }.forEach { duplicate ->
                    runCatching { duplicate.delete() }
                }
            }
    }

    suspend fun clearArtworkCacheForAlbum(songs: List<AudioItem>) = withContext(Dispatchers.IO) {
        val artworkDir = File(context.filesDir, "artwork")
        val paths = songs.mapNotNull { song ->
            validCachedArtworkUri(metadataDao.getMetadata(song.id)?.fetchedAlbumArtUrl)
                ?.takeIf { isInternalArtworkFile(it, artworkDir) }
        }.toSet()
        paths.forEach { uri -> runCatching { File(Uri.parse(uri).path!!).delete() } }
        songs.forEach { song -> metadataDao.clearArtworkUrl(song.id) }
    }

    suspend fun setCustomArtworkForAlbum(songs: List<AudioItem>, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
        val seed = songs.firstOrNull() ?: return@withContext null
        val artworkDir = File(context.filesDir, "artwork").apply { mkdirs() }
        // Keep the first track id as the shared cache key so cache management can
        // continue to identify the owning album without a schema migration.
        val target = File(artworkDir, "${seed.id}.jpg")
        runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return@withContext null
            applySharedArtwork(songs, Uri.fromFile(target).toString())
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    private suspend fun applySharedArtwork(songs: List<AudioItem>, uri: String) {
        songs.forEach { song ->
            val current = metadataDao.getMetadata(song.id)
            metadataDao.insert(
                current?.copy(fetchedAlbumArtUrl = uri)
                    ?: MetadataEntity(song.id, false, uri, null, null, null)
            )
        }
    }

    private fun albumCacheKey(song: AudioItem): String = when {
        song.albumId > 0L -> "id:${song.albumId}"
        else -> "${song.albumArtist.trim().lowercase(Locale.ROOT)}\u0000${song.album.trim().lowercase(Locale.ROOT)}"
    }

    private fun isInternalArtworkFile(uri: String, artworkDir: File): Boolean = runCatching {
        val path = Uri.parse(uri).path ?: return false
        File(path).canonicalFile.parentFile == artworkDir.canonicalFile
    }.getOrDefault(false)

    suspend fun clearArtworkCacheForAudio(audioId: Long) = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "artwork")
        artworkExtensions.forEach { extension ->
            runCatching { File(dir, "$audioId.$extension").delete() }
        }
        metadataDao.clearArtworkUrl(audioId)
    }
}
