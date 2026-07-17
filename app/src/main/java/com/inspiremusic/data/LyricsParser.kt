package com.inspiremusic.data

import com.inspiremusic.model.LrcLine
import com.inspiremusic.model.LrcWord
import java.io.File
import java.util.Locale

object LyricsParser {
    private val timestampTag = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val offsetRegex = Regex("""^\[offset:\s*(-?\d+)\s*\]$""", RegexOption.IGNORE_CASE)
    private val metaTagRegex = Regex("""^\[(\w+):.*\]$""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val ttmlParagraph = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ttmlSpan = Regex("""<span\b([^>]*)>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val attributeTime = Regex("""\b(begin|end|dur)=[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
    private val htmlTag = Regex("""<[^>]+>""")

    fun parse(lrcPath: String, durationMs: Long = 0L): List<LrcLine> = runCatching {
        val content = File(lrcPath).readText(Charsets.UTF_8)
        parseFromString(content).ifEmpty { parsePlainText(content, durationMs) }
    }.getOrDefault(emptyList())

    fun parseFromString(content: String): List<LrcLine> {
        if (content.contains("<tt", ignoreCase = true) || content.contains("<p begin=", ignoreCase = true)) {
            parseTtml(content).takeIf { it.isNotEmpty() }?.let { return it }
        }
        val rawLines = content.lines()
        val offsetMs = rawLines.firstNotNullOfOrNull { raw ->
            offsetRegex.find(raw.trim())?.groupValues?.getOrNull(1)?.toLongOrNull()
        } ?: 0L

        val entries = mutableListOf<LrcLine>()
        rawLines.forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || offsetRegex.matches(trimmed) || metaTagRegex.matches(trimmed)) return@forEach
            var remaining = trimmed
            val timestamps = mutableListOf<Long>()
            while (remaining.startsWith("[")) {
                val match = timestampTag.find(remaining) ?: break
                timestamps += (tagToMs(match.groupValues[1], match.groupValues[2], match.groupValues[3]) + offsetMs).coerceAtLeast(0L)
                remaining = remaining.removePrefix(match.value)
            }
            if (timestamps.isEmpty()) return@forEach
            val parsedText = parseEnhancedText(remaining.trim(), timestamps.first())
            if (parsedText.first.isBlank()) return@forEach
            timestamps.forEach { time ->
                val shiftedWords = parsedText.second.map { word ->
                    val delta = time - timestamps.first()
                    word.copy(startMs = word.startMs + delta, endMs = word.endMs + delta)
                }
                entries += LrcLine(timeMs = time, text = parsedText.first, words = shiftedWords)
            }
        }

        // Common bilingual LRC files repeat the same timestamp: original first,
        // translation second and optional romanization third.
        return entries.groupBy { it.timeMs }.toSortedMap().map { (_, sameTime) ->
            val base = sameTime.first()
            base.copy(
                translation = sameTime.getOrNull(1)?.text,
                romanization = sameTime.getOrNull(2)?.text
            )
        }
    }

    private fun parseEnhancedText(value: String, lineTimeMs: Long): Pair<String, List<LrcWord>> {
        val matches = wordTag.findAll(value).toList()
        if (matches.isEmpty()) return value to emptyList()
        val words = mutableListOf<Pair<Long, String>>()
        matches.forEachIndexed { index, match ->
            val textStart = match.range.last + 1
            val textEnd = matches.getOrNull(index + 1)?.range?.first ?: value.length
            val text = value.substring(textStart, textEnd)
            if (text.isNotBlank()) {
                words += tagToMs(match.groupValues[1], match.groupValues[2], match.groupValues[3]) to text
            }
        }
        val timed = words.mapIndexed { index, (start, text) ->
            LrcWord(
                startMs = start.coerceAtLeast(lineTimeMs),
                endMs = words.getOrNull(index + 1)?.first?.coerceAtLeast(start + 80L) ?: (start + 700L),
                text = text
            )
        }
        return timed.joinToString(separator = "") { it.text }.trim() to timed
    }

    private fun parseTtml(content: String): List<LrcLine> = ttmlParagraph.findAll(content).mapNotNull { paragraph ->
        val attrs = paragraph.groupValues[1]
        val body = paragraph.groupValues[2]
        val lineStart = parseTtmlTime(attributeTime.findAll(attrs).firstOrNull { it.groupValues[1].equals("begin", true) }?.groupValues?.get(2))
            ?: return@mapNotNull null
        val spans = ttmlSpan.findAll(body).mapNotNull { span ->
            val spanAttrs = span.groupValues[1]
            val text = decodeXml(span.groupValues[2].replace(htmlTag, ""))
            val begin = parseTtmlTime(attributeTime.findAll(spanAttrs).firstOrNull { it.groupValues[1].equals("begin", true) }?.groupValues?.get(2))
            val end = parseTtmlTime(attributeTime.findAll(spanAttrs).firstOrNull { it.groupValues[1].equals("end", true) }?.groupValues?.get(2))
            if (text.isBlank() || begin == null) null else LrcWord(begin, end ?: begin + 700L, text)
        }.toList()
        val text = (if (spans.isNotEmpty()) spans.joinToString("") { it.text } else decodeXml(body.replace(htmlTag, ""))).trim()
        text.takeIf(String::isNotBlank)?.let { LrcLine(lineStart, it, words = spans) }
    }.sortedBy { it.timeMs }.toList()

    private fun parseTtmlTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        if (value.endsWith("ms")) return value.removeSuffix("ms").toDoubleOrNull()?.toLong()
        if (value.endsWith("s")) return value.removeSuffix("s").toDoubleOrNull()?.times(1000)?.toLong()
        val parts = value.split(':')
        if (parts.size == 3) {
            return ((parts[0].toDoubleOrNull() ?: return null) * 3_600_000 +
                (parts[1].toDoubleOrNull() ?: return null) * 60_000 +
                (parts[2].toDoubleOrNull() ?: return null) * 1_000).toLong()
        }
        return null
    }

    fun parsePlainText(content: String, durationMs: Long = 0L): List<LrcLine> {
        val lines = content.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !metaTagRegex.matches(it) && !offsetRegex.matches(it) }
        if (lines.isEmpty()) return emptyList()
        val hasDuration = durationMs > 10_000L
        val startMs = if (hasDuration) (durationMs * 0.035f).toLong().coerceIn(1_200L, 8_000L) else 0L
        val endMs = if (hasDuration) (durationMs - 4_000L).coerceAtLeast(startMs) else 0L
        val stepMs = if (hasDuration && lines.size > 1) ((endMs - startMs) / (lines.size - 1)).coerceAtLeast(1_200L) else 0L
        return lines.mapIndexed { index, text ->
            LrcLine(if (hasDuration) startMs + stepMs * index else index.toLong(), text, isSynced = false)
        }
    }

    fun toLrc(lines: List<LrcLine>): String = buildString {
        lines.forEach { line ->
            val tag = formatTimestamp(line.timeMs)
            append(tag)
            if (line.words.isNotEmpty()) {
                line.words.forEach { word -> append(formatWordTimestamp(word.startMs)).append(word.text) }
            } else {
                append(line.text)
            }
            append('\n')
            line.translation?.takeIf(String::isNotBlank)?.let { append(tag).append(it).append('\n') }
            line.romanization?.takeIf(String::isNotBlank)?.let { append(tag).append(it).append('\n') }
        }
    }.trimEnd()

    private fun formatTimestamp(value: Long): String {
        val safe = value.coerceAtLeast(0L)
        val minutes = safe / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val centiseconds = (safe % 1_000L) / 10L
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centiseconds)
    }

    private fun formatWordTimestamp(value: Long): String = formatTimestamp(value)
        .replaceFirst('[', '<')
        .replace(']', '>')

    private fun tagToMs(mins: String, secs: String, fraction: String): Long {
        val subMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return (mins.toLongOrNull() ?: 0L) * 60_000L + (secs.toLongOrNull() ?: 0L) * 1_000L + subMs
    }

    private fun decodeXml(value: String): String = value
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")
}
