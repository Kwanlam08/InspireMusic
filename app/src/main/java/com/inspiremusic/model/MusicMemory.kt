package com.inspiremusic.model

import java.util.Calendar

enum class MusicMemoryKind {
    ON_THIS_DAY,
    FORGOTTEN,
    ALL_TIME,
    RECENT,
    LATE_NIGHT,
    FAVORITES,
    ARTIST_JOURNEY
}

data class MusicMemoryMix(
    val id: String,
    val kind: MusicMemoryKind,
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val songIds: List<Long>
)

data class MusicMemoryCollection(
    val firstPlayedAt: Long?,
    val totalListeningDuration: Long,
    val uniqueSongCount: Int,
    val mixes: List<MusicMemoryMix>
)

object MusicMemoryBuilder {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun build(
        records: List<ListeningRecord>,
        availableSongIds: Set<Long>,
        favoriteSongIds: Set<Long>,
        now: Long = System.currentTimeMillis()
    ): MusicMemoryCollection {
        val validRecords = records.filter { it.playedAt > 0L && it.songId in availableSongIds }
        if (validRecords.isEmpty()) {
            return MusicMemoryCollection(null, 0L, 0, emptyList())
        }

        val recordsBySong = validRecords.groupBy { it.songId }
        val totalBySong = recordsBySong.mapValues { (_, values) ->
            values.sumOf { it.duration.coerceAtLeast(0L) }
        }
        val countBySong = recordsBySong.mapValues { it.value.size }
        val lastPlayedBySong = recordsBySong.mapValues { (_, values) -> values.maxOf { it.playedAt } }
        val latestRecordBySong = recordsBySong.mapValues { (_, values) -> values.maxBy { it.playedAt } }

        fun rankedSongIds(source: List<ListeningRecord>, limit: Int = 18): List<Long> = source
            .groupBy { it.songId }
            .map { (songId, values) ->
                Triple(songId, values.sumOf { it.duration.coerceAtLeast(0L) }, values.size)
            }
            .sortedWith(compareByDescending<Triple<Long, Long, Int>> { it.second }.thenByDescending { it.third })
            .map { it.first }
            .take(limit)

        fun artistLabel(ids: List<Long>): String {
            val artists = ids.mapNotNull { latestRecordBySong[it]?.artist?.takeIf(String::isNotBlank) }.distinct()
            return when {
                artists.isEmpty() -> "你的音乐"
                artists.size == 1 -> artists.first()
                else -> "${artists.first()} 等 ${artists.size} 位艺人"
            }
        }

        val mixes = mutableListOf<MusicMemoryMix>()
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val currentYear = today.get(Calendar.YEAR)
        val currentMonth = today.get(Calendar.MONTH)
        val currentDay = today.get(Calendar.DAY_OF_MONTH)
        val onThisDayRecords = validRecords.filter { record ->
            val calendar = Calendar.getInstance().apply { timeInMillis = record.playedAt }
            calendar.get(Calendar.YEAR) < currentYear &&
                calendar.get(Calendar.MONTH) == currentMonth &&
                calendar.get(Calendar.DAY_OF_MONTH) == currentDay
        }
        rankedSongIds(onThisDayRecords).takeIf { it.isNotEmpty() }?.let { ids ->
            val yearsAgo = onThisDayRecords.minOfOrNull {
                currentYear - Calendar.getInstance().apply { timeInMillis = it.playedAt }.get(Calendar.YEAR)
            }?.coerceAtLeast(1) ?: 1
            mixes += MusicMemoryMix(
                id = "on_this_day",
                kind = MusicMemoryKind.ON_THIS_DAY,
                eyebrow = "往日今日",
                title = "${yearsAgo} 年前的今天",
                subtitle = artistLabel(ids),
                description = "把同一天听过的声音重新放回此刻。",
                songIds = ids
            )
        }

        val forgottenIds = lastPlayedBySong
            .filterValues { it < now - 90L * DAY_MS }
            .keys
            .sortedByDescending { totalBySong[it] ?: 0L }
            .take(18)
        forgottenIds.takeIf { it.isNotEmpty() }?.let { ids ->
            mixes += MusicMemoryMix(
                id = "forgotten",
                kind = MusicMemoryKind.FORGOTTEN,
                eyebrow = "好久不见",
                title = "被时间藏起来的歌",
                subtitle = artistLabel(ids),
                description = "这些歌至少三个月没有出现，也许正适合重新遇见。",
                songIds = ids
            )
        }

        val allTimeIds = recordsBySong.keys
            .sortedWith(
                compareByDescending<Long> { totalBySong[it] ?: 0L }
                    .thenByDescending { countBySong[it] ?: 0 }
            )
            .take(20)
        mixes += MusicMemoryMix(
            id = "all_time",
            kind = MusicMemoryKind.ALL_TIME,
            eyebrow = "长久陪伴",
            title = "曾经循环最多",
            subtitle = artistLabel(allTimeIds),
            description = "按真实听歌时长整理的长期偏爱。",
            songIds = allTimeIds
        )

        val recentRecords = validRecords.filter { it.playedAt >= now - 45L * DAY_MS }
        rankedSongIds(recentRecords).takeIf { it.isNotEmpty() }?.let { ids ->
            mixes += MusicMemoryMix(
                id = "recent",
                kind = MusicMemoryKind.RECENT,
                eyebrow = "近期新欢",
                title = "最近留在耳边",
                subtitle = artistLabel(ids),
                description = "过去 45 天里，真正陪伴你最久的歌。",
                songIds = ids
            )
        }

        val lateNightRecords = validRecords.filter { record ->
            val hour = Calendar.getInstance().apply { timeInMillis = record.playedAt }.get(Calendar.HOUR_OF_DAY)
            hour >= 22 || hour < 5
        }
        rankedSongIds(lateNightRecords).takeIf { it.isNotEmpty() }?.let { ids ->
            mixes += MusicMemoryMix(
                id = "late_night",
                kind = MusicMemoryKind.LATE_NIGHT,
                eyebrow = "夜色留声",
                title = "深夜常听",
                subtitle = artistLabel(ids),
                description = "只收录晚上十点到清晨五点真正播放过的音乐。",
                songIds = ids
            )
        }

        val favoriteIds = favoriteSongIds
            .filter { it in recordsBySong }
            .sortedByDescending { totalBySong[it] ?: 0L }
            .take(20)
        favoriteIds.takeIf { it.isNotEmpty() }?.let { ids ->
            mixes += MusicMemoryMix(
                id = "favorites",
                kind = MusicMemoryKind.FAVORITES,
                eyebrow = "收藏重温",
                title = "你亲手留下的歌",
                subtitle = artistLabel(ids),
                description = "把收藏与真实播放痕迹合在一起，重温不会错的选择。",
                songIds = ids
            )
        }

        val topArtist = validRecords
            .filter { it.artist.isNotBlank() }
            .groupBy { it.artist }
            .maxByOrNull { (_, values) -> values.sumOf { it.duration.coerceAtLeast(0L) } }
        topArtist?.let { (artist, artistRecords) ->
            rankedSongIds(artistRecords).takeIf { it.isNotEmpty() }?.let { ids ->
                mixes += MusicMemoryMix(
                    id = "artist_${artist.lowercase().hashCode()}",
                    kind = MusicMemoryKind.ARTIST_JOURNEY,
                    eyebrow = "艺人旅程",
                    title = "一路听来的 $artist",
                    subtitle = "${ids.size} 首歌 · 从第一次播放到现在",
                    description = "沿着你的播放记录，重新走一遍与这位艺人的相遇。",
                    songIds = ids
                )
            }
        }

        return MusicMemoryCollection(
            firstPlayedAt = validRecords.minOfOrNull { it.playedAt },
            totalListeningDuration = validRecords.sumOf { it.duration.coerceAtLeast(0L) },
            uniqueSongCount = recordsBySong.size,
            mixes = mixes.distinctBy { it.id }.filter { it.songIds.isNotEmpty() }.take(7)
        )
    }
}
