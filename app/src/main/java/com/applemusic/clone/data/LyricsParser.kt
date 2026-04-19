package com.applemusic.clone.data

import com.applemusic.clone.model.LrcLine
import java.io.File

/**
 * 解析 LRC 格式歌词文件。
 * LRC 时间标签格式: [mm:ss.xx] 歌词内容
 */
object LyricsParser {

    private val timeTagRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""")

    fun parse(lrcPath: String): List<LrcLine> {
        val file = File(lrcPath)
        if (!file.exists() || !file.canRead()) return emptyList()
        return parseFromString(file.readText(Charsets.UTF_8))
    }

    fun parseFromString(content: String): List<LrcLine> {
        val lines = mutableListOf<LrcLine>()
        try {
            content.lineSequence().forEach { rawLine ->
                val tags = timeTagRegex.findAll(rawLine)
                val textStart = timeTagRegex.find(rawLine)?.let {
                    rawLine.lastIndexOf(']') + 1
                } ?: return@forEach

                val text = rawLine.substring(textStart).trim()
                if (text.isEmpty()) return@forEach

                tags.forEach { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                    val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                    val centis = match.groupValues[3].padEnd(3, '0').toLongOrNull() ?: return@forEach
                    val timeMs = minutes * 60_000 + seconds * 1_000 + centis
                    lines.add(LrcLine(timeMs, text))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return lines.sortedBy { it.timeMs }
    }
}
