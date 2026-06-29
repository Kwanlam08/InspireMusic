package com.applemusic.clone.data

import com.applemusic.clone.model.LrcLine
import java.io.File

object LyricsParser {

    // 支持 [mm:ss.xx]、[mm:ss.xxx]、[mm:ss]、[mm:ss:xx]、[m:ss.xx] 等常见变体；
    // 小数部分可选，没有也按整秒处理
    private val lrcLineRegex = Regex("""^\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?\](.*)$""")

    // offset 标签：[offset:xxx] 毫秒偏移（正值=延迟，负值=提前）
    private val offsetRegex = Regex("""^\[offset:\s*(-?\d+)\s*\]$""", RegexOption.IGNORE_CASE)

    // 元数据标签（忽略，但不能误匹配 offset）
    private val metaTagRegex = Regex("""^\[(\w+):.*\]$""")

    // 从外部 .lrc 文件路径读取
    fun parse(lrcPath: String, durationMs: Long = 0L): List<LrcLine> {
        return try {
            val content = File(lrcPath).readText(Charsets.UTF_8)
            parseFromString(content).ifEmpty { parsePlainText(content, durationMs) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 从字符串内容解析
    fun parseFromString(content: String): List<LrcLine> {
        val rawLines = content.lines()

        // 第一遍：提取 offset 值
        var offsetMs = 0L
        rawLines.forEach { raw ->
            val trimmed = raw.trim()
            val om = offsetRegex.find(trimmed)
            if (om != null) {
                offsetMs = om.groupValues[1].toLongOrNull() ?: 0L
            }
        }

        // 第二遍：解析歌词行
        val lines = mutableListOf<LrcLine>()
        rawLines.forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return@forEach
            if (offsetRegex.matches(trimmed)) return@forEach
            if (metaTagRegex.matches(trimmed)) return@forEach

            // 一行可能有多个时间戳 [mm:ss.xx][mm:ss.xx]text
            val remainingLine = StringBuilder(trimmed)
            val timestamps = mutableListOf<Long>()
            while (remainingLine.startsWith("[")) {
                val match = lrcLineRegex.find(remainingLine) ?: break
                val mins = match.groupValues[1].toLongOrNull() ?: break
                val secs = match.groupValues[2].toLongOrNull() ?: break
                // 第三组：1=十分之一秒，2=百分之一秒，3=毫秒
                val subsStr = match.groupValues[3]
                val subsMs = when {
                    subsStr.isEmpty() -> 0L
                    subsStr.length == 1 -> subsStr.toLong() * 100L  // tenths of a second
                    subsStr.length == 2 -> subsStr.toLong() * 10L     // centiseconds
                    else -> subsStr.toLong()                          // already ms
                }
                val timeMs = (mins * 60_000 + secs * 1_000 + subsMs + offsetMs).coerceAtLeast(0L)
                timestamps.add(timeMs)

                // 移除这个时间戳标签，继续检查剩余内容
                val tagEnd = match.value.indexOf("]") + 1
                remainingLine.delete(0, tagEnd)
            }

            val text = remainingLine.toString().trim()
            if (text.isNotEmpty() && timestamps.isNotEmpty()) {
                timestamps.forEach { t -> lines.add(LrcLine(t, text)) }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    fun parsePlainText(content: String, durationMs: Long = 0L): List<LrcLine> {
        val lines = content
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !metaTagRegex.matches(it) && !offsetRegex.matches(it) }

        if (lines.isEmpty()) return emptyList()

        val hasDuration = durationMs > 10_000L
        val startMs = if (hasDuration) (durationMs * 0.035f).toLong().coerceIn(1_200L, 8_000L) else 0L
        val endMs = if (hasDuration) (durationMs - 4_000L).coerceAtLeast(startMs) else 0L
        val stepMs = if (hasDuration && lines.size > 1) {
            ((endMs - startMs) / (lines.size - 1)).coerceAtLeast(1_200L)
        } else {
            0L
        }

        return lines.mapIndexed { index, text ->
            LrcLine(
                timeMs = if (hasDuration) startMs + stepMs * index else index.toLong(),
                text = text,
                isSynced = false
            )
        }
    }
}
