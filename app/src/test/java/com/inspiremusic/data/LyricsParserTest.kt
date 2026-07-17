package com.inspiremusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test
    fun duplicateTimestampBecomesBilingualLine() {
        val lines = LyricsParser.parseFromString(
            """
            [00:01.20]Hello world
            [00:01.20]你好，世界
            [00:04.00]Second line
            """.trimIndent()
        )

        assertEquals(2, lines.size)
        assertEquals("Hello world", lines[0].text)
        assertEquals("你好，世界", lines[0].translation)
        assertEquals(1_200L, lines[0].timeMs)
    }

    @Test
    fun enhancedLrcKeepsWordTimingAndSpaces() {
        val lines = LyricsParser.parseFromString("[00:01.00]<00:01.00>Hello <00:01.50>world")

        assertEquals("Hello world", lines.single().text)
        assertEquals(2, lines.single().words.size)
        assertEquals(1_000L, lines.single().words[0].startMs)
        assertEquals(1_500L, lines.single().words[0].endMs)
        val reparsed = LyricsParser.parseFromString(LyricsParser.toLrc(lines))
        assertEquals(2, reparsed.single().words.size)
        assertEquals("Hello world", reparsed.single().text)
    }

    @Test
    fun ttmlWordTimingIsParsed() {
        val lines = LyricsParser.parseFromString(
            """<tt><body><div><p begin="00:00:02.000" end="00:00:04.000"><span begin="00:00:02.000" end="00:00:02.500">Hi </span><span begin="00:00:02.500" end="00:00:03.000">there</span></p></div></body></tt>"""
        )

        assertEquals(2_000L, lines.single().timeMs)
        assertEquals("Hi there", lines.single().text)
        assertEquals(2, lines.single().words.size)
    }

    @Test
    fun serializationPreservesTranslation() {
        val original = LyricsParser.parseFromString("[00:02.00]Good night\n[00:02.00]晚安")
        val reparsed = LyricsParser.parseFromString(LyricsParser.toLrc(original))

        assertEquals(original.first().text, reparsed.first().text)
        assertEquals(original.first().translation, reparsed.first().translation)
        assertTrue(reparsed.first().isSynced)
    }
}
