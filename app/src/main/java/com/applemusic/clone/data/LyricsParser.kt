package com.applemusic.clone.data

import com.applemusic.clone.model.LrcLine
import java.io.File

/**
 * 解析 LRC 格式歌词文件。
 * LRC 时间标签格式: [mm:ss.xx] 歌词内容
 * 同时支持无时间戳的纯文本歌词（每行5秒间隔）
 */
object LyricsParser {

    private val timeTagRegex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\]""")

    fun parse(lrcPath: String): List<LrcLine> {
        val file = File(lrcPath)
        if (!file.exists() || !file.canRead()) return emptyList()
        return parseFromString(file.readText(Charsets.UTF_8))
    }

    fun parseFromString(content: String): List<LrcLine> {
        if (content.isBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        try {
            var hasTimeTags = false
            content.lineSequence().forEach { rawLine ->
                val tags = timeTagRegex.findAll(rawLine)
                val textStart = timeTagRegex.find(rawLine)?.let {
                    hasTimeTags = true
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

            // 纯文本歌词（无时间戳）：每行分配5秒
            if (!hasTimeTags) {
                var timeMs = 0L
                content.lineSequence().forEach { line ->
                    val cleanLine = line.trim()
                    if (cleanLine.isNotEmpty()) {
                        lines.add(LrcLine(timeMs, cleanLine))
                        timeMs += 5000L
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return lines.sortedBy { it.timeMs }
    }
}
