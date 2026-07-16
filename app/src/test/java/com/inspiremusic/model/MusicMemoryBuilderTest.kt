package com.inspiremusic.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MusicMemoryBuilderTest {
    @Test
    fun emptyHistoryProducesNoMemories() {
        val result = MusicMemoryBuilder.build(emptyList(), setOf(1L), emptySet(), now = 1_000L)

        assertEquals(0, result.uniqueSongCount)
        assertTrue(result.mixes.isEmpty())
    }

    @Test
    fun allTimeMixRanksSongsByListeningDuration() {
        val now = timestamp(2026, Calendar.JULY, 16, 12)
        val records = listOf(
            record(1, now - day, duration = 30_000),
            record(2, now - day, duration = 80_000),
            record(1, now - 2 * day, duration = 60_000)
        )

        val result = MusicMemoryBuilder.build(records, setOf(1L, 2L), emptySet(), now)
        val allTime = result.mixes.first { it.kind == MusicMemoryKind.ALL_TIME }

        assertEquals(listOf(1L, 2L), allTime.songIds.take(2))
        assertEquals(2, result.uniqueSongCount)
        assertEquals(170_000L, result.totalListeningDuration)
    }

    @Test
    fun oldSongsAppearInForgottenMix() {
        val now = timestamp(2026, Calendar.JULY, 16, 12)
        val result = MusicMemoryBuilder.build(
            records = listOf(record(7, now - 120 * day)),
            availableSongIds = setOf(7L),
            favoriteSongIds = emptySet(),
            now = now
        )

        assertEquals(listOf(7L), result.mixes.first { it.kind == MusicMemoryKind.FORGOTTEN }.songIds)
    }

    @Test
    fun previousYearSameDateAppearsOnThisDay() {
        val now = timestamp(2026, Calendar.JULY, 16, 12)
        val previousYear = timestamp(2025, Calendar.JULY, 16, 22)
        val result = MusicMemoryBuilder.build(
            records = listOf(record(9, previousYear)),
            availableSongIds = setOf(9L),
            favoriteSongIds = emptySet(),
            now = now
        )

        assertEquals(listOf(9L), result.mixes.first { it.kind == MusicMemoryKind.ON_THIS_DAY }.songIds)
    }

    @Test
    fun favoriteMixOnlyIncludesPlayedAvailableFavorites() {
        val now = timestamp(2026, Calendar.JULY, 16, 12)
        val result = MusicMemoryBuilder.build(
            records = listOf(record(1, now - day), record(2, now - day)),
            availableSongIds = setOf(1L, 2L),
            favoriteSongIds = setOf(2L, 3L),
            now = now
        )

        assertEquals(listOf(2L), result.mixes.first { it.kind == MusicMemoryKind.FAVORITES }.songIds)
    }

    private fun record(songId: Long, playedAt: Long, duration: Long = 60_000L) = ListeningRecord(
        songId = songId,
        title = "Song $songId",
        artist = "Artist",
        album = "Album",
        playedAt = playedAt,
        duration = duration
    )

    private fun timestamp(year: Int, month: Int, dayOfMonth: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, dayOfMonth, hour, 0, 0)
        }.timeInMillis

    private companion object {
        const val day = 24L * 60L * 60L * 1000L
    }
}
