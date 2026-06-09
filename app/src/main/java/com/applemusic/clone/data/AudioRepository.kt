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

class AudioRepository(private val context: Context) {

    private val metadataDao = AppDatabase.getInstance(context).metadataDao()

    // 联网下载并缓存封面到本地文件（首次），返回本地 file:// URI
    private suspend fun cacheArtworkToFile(audioId: Long, remoteUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val artDir = File(context.filesDir, "artwork"); artDir.mkdirs()
            val target = File(artDir, "$audioId.jpg")
            // 如果已存在且大小 > 0，直接复用
            if (target.exists() && target.length() > 0) return@withContext Uri.fromFile(target).toString()
            val url = URL(remoteUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect()
            if (conn.responseCode !in 200..299) return@withContext null
            conn.inputStream.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            Uri.fromFile(target).toString()
        } catch (_: Exception) { null }
    }

    suspend fun getLocalAudioFiles(): List<AudioItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<AudioItem>()

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
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK
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
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

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
                    val rawTrack = cursor.getInt(trackCol)
                    val cleanTrack = if (rawTrack > 0) rawTrack % 1000 else 0
                    val localDiscNumber = if (rawTrack >= 1000) rawTrack / 1000 else 1
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    var cachedMeta = metadataDao.getMetadata(id)
                    var hasEmbedded = cachedMeta?.hasEmbeddedArt ?: false

                    if (cachedMeta == null) {
                        // 首次扫描跳过重操作，先返回基础信息；后台异步补齐
                        hasEmbedded = false
                        cachedMeta = MetadataEntity(id, false, null, null, null, null)
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
                                var fetchedArt: String? = null
                                var fetchedTrk: Int? = null
                                var fetchedDis: Int? = null
                                if (!emb || cleanTrack == 0) {
                                    val meta = OnlineMetadataManager.fetchItunesMetadata(title, artist)
                                    if (!emb) {
                                        // 把 iTunes 远程 URL 立刻下载到本地并缓存 file://，
                                        // 之后再也不需要联网就能显示艺人头像
                                        val remote = meta?.artworkUrl
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
                                        val onlineLyrics = OnlineMetadataManager.fetchLyrics(title, artist)
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
                    val artUri = when {
                        cachedMeta!!.fetchedAlbumArtUrl != null -> Uri.parse(cachedMeta.fetchedAlbumArtUrl)
                        cachedMeta.hasEmbeddedArt || albumId > 0 -> Uri.parse("content://media/external/audio/albumart/$albumId")
                        else -> null
                    }

                    val lyricsPath = findLyricsFile(data) ?: cachedMeta.fetchedLyricsPath
                    val finalTrack = cachedMeta.fetchedTrackNumber ?: cleanTrack
                    // 优先用 MediaStore 解析出的碟号（rawTrack/1000），然后才是 iTunes
                    val finalDisc = if (localDiscNumber > 1) localDiscNumber else (cachedMeta.fetchedDiscNumber ?: localDiscNumber)

                    audioList.add(
                        AudioItem(id, title, artist, album, duration, contentUri, artUri, data, lyricsPath, finalTrack, finalDisc)
                    )
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        return@withContext audioList
    }

    private fun findLyricsFile(audioPath: String): String? {
        if (audioPath.isEmpty()) return null
        val file = File(audioPath)
        val nameWithoutExt = file.nameWithoutExtension
        val lrcFile = File(file.parent, "$nameWithoutExt.lrc")
        return if (lrcFile.exists()) lrcFile.absolutePath else null
    }
}
