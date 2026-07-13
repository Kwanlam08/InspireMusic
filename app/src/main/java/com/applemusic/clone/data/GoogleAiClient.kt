package com.applemusic.clone.data

import android.content.Context
import com.applemusic.clone.settings.AiConfiguration
import com.applemusic.clone.settings.AiProtocol
import com.applemusic.clone.settings.AiSettingsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    data class TagsResult(val tags: List<String>, val emotions: List<String>, val rawContent: String)

    suspend fun testConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val settings = AiSettingsController(context)
        request(settings.configuration.value, settings.apiKey(), "Reply only with: OK", "You are a connection test.")
            .map { "Connected: ${settings.configuration.value.provider.displayName}" }
    }

    suspend fun analyzeDiary(context: Context, listeningContext: String): Result<String> = withContext(Dispatchers.IO) {
        val system = """
            You are a thoughtful music diary assistant. Analyze only the supplied listening data.
            Write in Chinese. Use four short sections, each beginning with an emoji and a concise title:
            🎧 听歌偏好, 🌙 情绪线索, ✨ 最近的你, 🧭 接下来可以听什么.
            Emotional observations must be tentative, never medical or diagnostic. Keep the tone warm, grounded, and under 500 Chinese characters.
        """.trimIndent()
        val settings = AiSettingsController(context)
        request(settings.configuration.value, settings.apiKey(), listeningContext, system)
    }

    suspend fun generateTags(context: Context, userMessage: String): Result<TagsResult> = withContext(Dispatchers.IO) {
        val system = """
            You translate a listening request into terms that can be matched against a LOCAL music library.
            Return JSON only: {"tags":[...],"emotions":[...]} with no Markdown or explanation.
            tags: exactly 8 short, concrete search terms ordered by importance. Prefer canonical genres,
            subgenres, era/decade, language/region, instrumentation, tempo or activity terms that are
            likely to appear in song, album, artist or genre metadata. Include useful Chinese/English
            aliases when they improve matching, but never duplicate the same meaning.
            emotions: exactly 4 concise mood/energy terms. Distinguish calm vs sad, energetic vs aggressive,
            dreamy vs sleepy, and positive vs nostalgic instead of returning vague words such as "好听".
            Respect every constraint in the request (scene, energy, era, language, exclusions).
            If a LOCAL LIBRARY SUMMARY is provided, prioritize genres and eras that actually exist there.
        """.trimIndent()
        val settings = AiSettingsController(context)
        request(settings.configuration.value, settings.apiKey(), userMessage, system).mapCatching { content ->
            val cleaned = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(cleaned.substring(cleaned.indexOf('{').coerceAtLeast(0), cleaned.lastIndexOf('}').let { if (it >= 0) it + 1 else cleaned.length }))
            TagsResult(
                tags = json.optJSONArray("tags").toStringList(),
                emotions = json.optJSONArray("emotions").toStringList(),
                rawContent = content
            )
        }
    }

    private fun request(config: AiConfiguration, apiKey: String, userMessage: String, systemPrompt: String): Result<String> {
        if (config.provider.protocol != AiProtocol.OLLAMA && apiKey.isBlank()) {
            return Result.failure(IllegalStateException("请先在设置 > AI 配置中填入 API Key"))
        }
        if (config.model.isBlank()) return Result.failure(IllegalStateException("请填写模型名称"))
        return runCatching {
            val request = when (config.provider.protocol) {
                AiProtocol.OPENAI -> openAiRequest(config, apiKey, userMessage, systemPrompt)
                AiProtocol.ANTHROPIC -> anthropicRequest(config, apiKey, userMessage, systemPrompt)
                AiProtocol.GEMINI -> geminiRequest(config, apiKey, userMessage, systemPrompt)
                AiProtocol.OLLAMA -> ollamaRequest(config, userMessage, systemPrompt)
            }
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IllegalStateException("API ${response.code}: ${body.take(300)}")
                parseResponse(config.provider.protocol, body).ifBlank { throw IllegalStateException("AI 返回了空内容") }
            }
        }
    }

    private fun openAiRequest(config: AiConfiguration, key: String, user: String, system: String): Request {
        val body = JSONObject().apply {
            put("model", config.model)
            put("temperature", 0.7)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }
        return Request.Builder().url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer $key").header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia)).build()
    }

    private fun anthropicRequest(config: AiConfiguration, key: String, user: String, system: String): Request {
        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 900)
            put("system", system)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
        }
        return Request.Builder().url("${config.baseUrl.trimEnd('/')}/v1/messages")
            .header("x-api-key", key).header("anthropic-version", "2023-06-01").header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonMedia)).build()
    }

    private fun geminiRequest(config: AiConfiguration, key: String, user: String, system: String): Request {
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", user)))))
            put("generationConfig", JSONObject().put("temperature", 0.7))
        }
        return Request.Builder().url("${config.baseUrl.trimEnd('/')}/v1beta/models/${config.model}:generateContent?key=$key")
            .header("Content-Type", "application/json").post(body.toString().toRequestBody(jsonMedia)).build()
    }

    private fun ollamaRequest(config: AiConfiguration, user: String, system: String): Request {
        val body = JSONObject().apply {
            put("model", config.model)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }
        return Request.Builder().url("${config.baseUrl.trimEnd('/')}/api/chat")
            .header("Content-Type", "application/json").post(body.toString().toRequestBody(jsonMedia)).build()
    }

    private fun parseResponse(protocol: AiProtocol, body: String): String {
        val json = JSONObject(body)
        return when (protocol) {
            AiProtocol.OPENAI -> json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            AiProtocol.ANTHROPIC -> json.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
            AiProtocol.GEMINI -> json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
            AiProtocol.OLLAMA -> json.optJSONObject("message")?.optString("content").orEmpty()
        }.trim()
    }

    private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}
