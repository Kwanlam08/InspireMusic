package com.applemusic.clone.data

import android.content.Context
import com.applemusic.clone.model.AudioItem
import java.util.Locale
import kotlin.random.Random

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
        localSongs: List<AudioItem>,
        preferredSongIds: Set<Long> = emptySet()
    ): Result<GenerateResult> {
        if (localSongs.isEmpty()) {
            return Result.failure(IllegalStateException("本地曲库为空，先添加歌曲再试"))
        }
        val librarySummary = buildLibrarySummary(localSongs)
        val request = "$userInput\n\nLOCAL LIBRARY SUMMARY: $librarySummary"
        val tagsResult = AiClient.generateTags(context, request)
        return tagsResult.map { tr ->
            val matched = matchByKeywords(userInput, tr.tags, tr.emotions, localSongs, preferredSongIds)
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
        userInput: String,
        tags: List<String>,
        emotions: List<String>,
        localSongs: List<AudioItem>,
        preferredSongIds: Set<Long> = emptySet()
    ): List<AudioItem> {
        if (localSongs.isEmpty()) return emptyList()

        val directTerms = tokenize(userInput)
        val aiTerms = (tags + emotions).flatMap(::tokenize)
        val keywords = expandAliases(directTerms + aiTerms).distinct()
        if (keywords.isEmpty()) {
            return diverseFallback(localSongs, preferredSongIds, userInput)
        }

        data class Scored(val song: AudioItem, val score: Int)
        val requestedYears = requestedYearRanges(userInput + " " + tags.joinToString(" "))

        val scored: List<Scored> = localSongs.map { song ->
            val title = normalize(song.title)
            val artist = normalize(song.artist)
            val album = normalize(song.album)
            val albumArtist = normalize(song.albumArtist)
            val genre = normalize(song.genre)
            val haystack = "$title $artist $album $albumArtist $genre"

            var score = if (song.id in preferredSongIds) 2 else 0
            for (kw in keywords) {
                val normalized = normalize(kw)
                if (normalized.isBlank()) continue
                when {
                    genre.contains(normalized) -> score += 8
                    title.contains(normalized) -> score += 6
                    album.contains(normalized) -> score += 5
                    artist.contains(normalized) || albumArtist.contains(normalized) -> score += 4
                    normalized.length >= 3 && haystack.contains(normalized.take(3)) -> score += 1
                }
            }
            if (requestedYears.any { song.year in it }) score += 7
            if (directTerms.any { term -> normalize(term).let { it.length >= 2 && haystack.contains(it) } }) score += 4
            Scored(song, score)
        }

        val ranked = scored.filter { it.score > 0 }
            .sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.song.dateModifiedMs })
            .map { it.song }
        val positive = diversify(ranked, 25)

        return positive.ifEmpty { diverseFallback(localSongs, preferredSongIds, userInput) }
    }

    private fun buildLibrarySummary(songs: List<AudioItem>): String {
        val genres = songs.map { it.genre.trim() }.filter(String::isNotBlank)
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(16).map { it.key }
        val years = songs.map { it.year }.filter { it in 1900..2100 }
        val range = if (years.isEmpty()) "unknown" else "${years.min()}-${years.max()}"
        return "songs=${songs.size}; genres=${genres.joinToString(",").ifBlank { "unknown" }}; years=$range"
    }

    private fun tokenize(value: String): List<String> = value
        .lowercase(Locale.ROOT)
        .split(Regex("[\\s,，。.!！?？/、;；:：|]+"))
        .map(String::trim)
        .filter { it.length >= 2 }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")

    private val aliasGroups = listOf(
        setOf("放松", "轻松", "舒缓", "chill", "calm", "lofi", "轻音乐", "ambient"),
        setOf("运动", "健身", "跑步", "workout", "gym", "energetic", "高能量", "edm"),
        setOf("伤感", "难过", "sad", "melancholy", "忧郁", "emo"),
        setOf("派对", "聚会", "party", "dance", "舞曲", "disco"),
        setOf("睡眠", "助眠", "sleep", "眠", "安静", "ambient"),
        setOf("梦幻", "dreamy", "空灵", "ethereal", "shoegaze"),
        setOf("摇滚", "rock", "alternative", "另类"),
        setOf("流行", "pop", "华语", "mandopop", "粤语", "cantopop"),
        setOf("电子", "electronic", "electronica", "synth", "合成器"),
        setOf("说唱", "rap", "hiphop", "hip-hop", "嘻哈")
    )

    private fun expandAliases(terms: List<String>): List<String> = buildList {
        addAll(terms)
        terms.forEach { term ->
            val normalized = normalize(term)
            aliasGroups.filter { group -> group.any { normalize(it) == normalized } }.forEach(::addAll)
        }
    }

    private fun requestedYearRanges(value: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        Regex("(?:19|20)?(\\d0)[sS年代]").findAll(value).forEach { match ->
            val short = match.groupValues[1].toInt()
            val year = if (short >= 30) 1900 + short else 2000 + short
            ranges += year..(year + 9)
        }
        Regex("\\b(19\\d{2}|20\\d{2})\\b").findAll(value).forEach { match ->
            val year = match.value.toInt()
            ranges += year..year
        }
        return ranges
    }

    private fun diversify(ranked: List<AudioItem>, limit: Int): List<AudioItem> {
        val artistCounts = mutableMapOf<String, Int>()
        val albumCounts = mutableMapOf<String, Int>()
        return ranked.filter { song ->
            val artist = normalize(song.albumArtist.ifBlank { song.artist })
            val album = normalize(song.album)
            val allowed = (artistCounts[artist] ?: 0) < 4 && (albumCounts[album] ?: 0) < 3
            if (allowed) {
                artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
                albumCounts[album] = (albumCounts[album] ?: 0) + 1
            }
            allowed
        }.take(limit)
    }

    private fun diverseFallback(songs: List<AudioItem>, preferred: Set<Long>, seed: String): List<AudioItem> {
        val random = Random(seed.hashCode())
        val ordered = songs.filter { it.id in preferred }.shuffled(random) +
            songs.filterNot { it.id in preferred }.shuffled(random)
        return diversify(ordered, 20)
    }

    data class GenerateResult(
        val tags: List<String>,
        val emotions: List<String>,
        val rawContent: String,
        val matchedSongs: List<AudioItem>
    )
}
