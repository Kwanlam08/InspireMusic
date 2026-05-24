package com.applemusic.clone.data

import com.applemusic.clone.model.AudioItem
import org.json.JSONArray

object AiPlaylistGenerator {

    // ── 构建系统 prompt ───────────────────────────────────
    private fun buildSystemPrompt(): String = """
你是一个音乐推荐助手。用户会描述他们想听什么样的音乐（心情、场景、风格等），
并附上他们设备上的本地曲库列表。请从曲库中严格挑选最匹配的歌曲。

规则：
1. 只从提供的曲库中选择，不要编造不存在的歌曲
2. 挑选 10-20 首最合适的
3. 返回纯 JSON 数组，每项格式: {"title":"歌名","artist":"艺人"}
4. 不要返回任何 JSON 以外的文字

示例输出：
[{"title":"晴天","artist":"周杰伦"},{"title":"后来","artist":"刘若英"}]
    """.trimIndent()

    // ── 构建用户 prompt ───────────────────────────────────
    fun buildUserPrompt(userInput: String, songs: List<AudioItem>): String {
        val catalogLines = songs
            .distinctBy { "${it.title}||${it.artist}" }
            .take(300)
            .mapIndexed { i, s -> "${i + 1}. ${s.title} - ${s.artist}" }

        val catalog = catalogLines.joinToString("\n")

        return """
用户想听：$userInput

以下是用户设备上的本地曲库（共 ${catalogLines.size} 首）：

$catalog

请从上述曲库中挑选最符合用户要求的歌曲，只返回 JSON 数组。
        """.trimIndent()
    }

    // ── 解析 AI 返回的 JSON 数组 ──────────────────────────
    fun parseAiResponse(jsonText: String): List<Pair<String, String>> {
        val cleaned = jsonText
            .replace("```json", "")
            .replace("```", "")
            .trim()
        return try {
            val arr = JSONArray(cleaned)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val title = obj.optString("title", "").trim()
                val artist = obj.optString("artist", "").trim()
                title to artist
            }.filter { it.first.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── 模糊匹配本地歌曲 ─────────────────────────────────
    fun matchSongs(
        aiResults: List<Pair<String, String>>,
        localSongs: List<AudioItem>
    ): List<AudioItem> {
        val matched = mutableListOf<AudioItem>()
        val usedIds = mutableSetOf<Long>()

        for ((aiTitle, aiArtist) in aiResults) {
            val aiTitleLower = aiTitle.lowercase().trim()
            val aiArtistLower = aiArtist.lowercase().trim()

            // 优先级 1: 标题和艺人都精确包含匹配
            var best = localSongs.find {
                it.id !in usedIds &&
                    it.title.lowercase().contains(aiTitleLower) &&
                    it.artist.lowercase().contains(aiArtistLower)
            }

            // 优先级 2: 标题精确包含匹配
            if (best == null) {
                best = localSongs.find {
                    it.id !in usedIds &&
                        it.title.lowercase().contains(aiTitleLower)
                }
            }

            // 优先级 3: 标题或艺人部分匹配
            if (best == null) {
                best = localSongs.find {
                    it.id !in usedIds &&
                        (it.title.lowercase().contains(aiTitleLower.take(4)) ||
                            it.artist.lowercase().contains(aiArtistLower.take(4)))
                }
            }

            best?.let {
                matched.add(it)
                usedIds.add(it.id)
            }
        }

        return matched
    }

    // ── 一键：生成 AI 推荐播放列表 ──────────────────────────
    suspend fun generate(
        userInput: String,
        localSongs: List<AudioItem>
    ): Result<GenerateResult> {
        val prompt = buildUserPrompt(userInput, localSongs)
        val result = DeepSeekClient.chat(
            systemPrompt = buildSystemPrompt(),
            userMessage = prompt,
            temperature = 0.7f
        )

        return result.map { chatResponse ->
            val aiPairs = parseAiResponse(chatResponse.content)
            val matched = matchSongs(aiPairs, localSongs)
            GenerateResult(
                aiResponseText = chatResponse.content,
                aiSongPairs = aiPairs,
                matchedSongs = matched
            )
        }
    }

    data class GenerateResult(
        val aiResponseText: String,
        val aiSongPairs: List<Pair<String, String>>,
        val matchedSongs: List<AudioItem>
    )
}
