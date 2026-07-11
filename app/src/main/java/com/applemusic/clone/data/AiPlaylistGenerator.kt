package com.applemusic.clone.data

import android.content.Context
import com.applemusic.clone.model.AudioItem

/**
 * AI 歌单生成器（新流程）：
 *   1. Cloudflare AI 返回 5 个 tags + 3 个 emotions（不依赖本地曲库大小）
 *   2. 在本地用关键词打分，筛出最像的歌
 *   3. 兜底：完全没匹配上时返回随机 N 首
 */
object AiPlaylistGenerator {

    /** 一键：标签 → 匹配本地歌单 */
    suspend fun generate(
        context: Context,
        userInput: String,
        localSongs: List<AudioItem>
    ): Result<GenerateResult> {
        if (localSongs.isEmpty()) {
            return Result.failure(IllegalStateException("本地曲库为空，先添加歌曲再试"))
        }
        val tagsResult = AiClient.generateTags(context, userInput)
        return tagsResult.map { tr ->
            val matched = matchByKeywords(tr.tags, tr.emotions, localSongs)
            GenerateResult(
                tags = tr.tags,
                emotions = tr.emotions,
                rawContent = tr.rawContent,
                matchedSongs = matched
            )
        }
    }

    /**
     * 关键词打分匹配。
     * 思路：把每个 tag / emotion 当作"词"，
     *       在 song 的 title / artist / album 中做子串包含打分。
     *       标题命中权重大于艺人命中。
     * 优点：纯本地，不依赖外部元数据；
     *       AI 给的 tag（Lo-Fi / Pop / Rock / 轻音乐 …）通常会出现在歌名或专辑名里。
     */
    fun matchByKeywords(
        tags: List<String>,
        emotions: List<String>,
        localSongs: List<AudioItem>
    ): List<AudioItem> {
        if (localSongs.isEmpty()) return emptyList()

        val keywords = (tags + emotions)
            .map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (keywords.isEmpty()) {
            return localSongs.shuffled().take(20)
        }

        data class Scored(val song: AudioItem, val score: Int)

        val scored: List<Scored> = localSongs.map { song ->
            val title = song.title.lowercase()
            val artist = song.artist.lowercase()
            val album = song.album.lowercase()
            val haystack = "$title $artist $album"

            var score = 0
            for (kw in keywords) {
                when {
                    // 标题命中（最相关）
                    title.contains(kw) -> score += 3
                    // 艺人命中
                    artist.contains(kw) -> score += 2
                    // 专辑名命中
                    album.contains(kw) -> score += 2
                    // 模糊匹配前 2 字
                    kw.length >= 2 && haystack.contains(kw.take(2)) -> score += 1
                }
            }
            Scored(song, score)
        }

        val positive = scored.filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(25)
            .map { it.song }

        // 兜底：完全没匹配上时返回随机 20 首
        return positive.ifEmpty { localSongs.shuffled().take(20) }
    }

    data class GenerateResult(
        val tags: List<String>,
        val emotions: List<String>,
        val rawContent: String,
        val matchedSongs: List<AudioItem>
    )
}
