package com.applemusic.clone.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import com.applemusic.clone.model.AudioItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioRepository(private val context: Context) {

    private val metadataDao = AppDatabase.getInstance(context).metadataDao()

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
                        "" // 空字符串更容易让 iTunes 仅通过文件名查找到单曲
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

                    if (cachedMeta == null || (!hasEmbedded && cachedMeta.fetchedAlbumArtUrl == null)) {
                        // Detect embedded cover
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(data)
                            hasEmbedded = retriever.embeddedPicture != null
                            retriever.release()
                        } catch (e: Exception) {}

                        // Online Sync
                        var fetchedArtUrl: String? = null
                        var fetchedTrackNumber: Int? = null
                        var fetchedDiscNumber: Int? = null

                        if (!hasEmbedded || cleanTrack == 0) {
                            val meta = OnlineMetadataManager.fetchItunesMetadata(title, artist)
                            if (!hasEmbedded) fetchedArtUrl = meta?.artworkUrl
                            if (cleanTrack == 0) fetchedTrackNumber = meta?.trackNumber
                            fetchedDiscNumber = meta?.discNumber
                        }

                        var fetchedLyricsPath: String? = null
                        val localLrc = findLyricsFile(data)
                        if (localLrc == null) {
                            val onlineLyrics = OnlineMetadataManager.fetchLyrics(title, artist)
                            if (onlineLyrics != null) {
                                val dir = File(context.filesDir, "lyrics")
                                dir.mkdirs()
                                val f = File(dir, "$id.lrc")
                                f.writeText(onlineLyrics)
                                fetchedLyricsPath = f.absolutePath
                            }
                        }

                        cachedMeta = MetadataEntity(id, hasEmbedded, fetchedArtUrl, fetchedLyricsPath, fetchedTrackNumber, fetchedDiscNumber)
                        metadataDao.insert(cachedMeta)
                    }

                    val artUri = if (cachedMeta.fetchedAlbumArtUrl != null) {
                        Uri.parse(cachedMeta.fetchedAlbumArtUrl)
                    } else if (hasEmbedded) {
                        Uri.parse("content://media/external/audio/albumart/$albumId")
                    } else null

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
