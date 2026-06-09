package com.applemusic.clone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 智谱 AI (Zhipu / BigModel) 客户端，OpenAI 兼容模式。
 *
 * - 端点：open.bigmodel.cn/api/paas/v4/chat/completions
 * - 模型：glm-4-flash（几乎免费、速度快、支持联网搜索 + JSON 模式）
 * - 鉴权：Authorization: Bearer <ZHIPU_API_KEY>
 * - 联网：tools 数组里加 web_search 工具；search_result=true 让模型
 *        自己用搜索结果生成最终回答，不需要我们手动 function calling
 *
 * 国内直连，零代理。
 * API Key 在这里申请（实名 + 手机号即可，注册送 200 万 token）：
 *   https://bigmodel.cn/user-center/apikeys
 */
object AiClient {

    // ↓↓↓ 拿到 key 后把这一行替换成你的真实 key（保留双引号）↓↓↓
    private const val API_KEY = "72d58adeab3248129e49456330a27c62.6LWDozhyN35SsHUD"

    // 智谱 GLM-4.6 最新模型（用户指定），失败回退
    private val MODELS = listOf("glm-4-6", "glm-4-flash", "glm-4-plus", "glm-4")
    private const val BASE_URL =
        "https://open.bigmodel.cn/api/paas/v4/chat/completions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    data class TagsResult(
        val tags: List<String>,
        val emotions: List<String>,
        val rawContent: String
    )

    /**
     * 让 AI 根据用户输入生成 5 个音乐风格标签 + 3 个情感关键词。
     * 启用联网搜索后，AI 可参考最新音乐潮流、艺人新作等。
     * 按 MODELS 列表依次尝试，第一个成功的为准。
     * @param userMessage 用户在输入框里写的字，或点击的快捷卡片文字
     */
    suspend fun generateTags(userMessage: String): Result<TagsResult> = withContext(Dispatchers.IO) {
        val systemPrompt = """
你是一個音樂專家與情感分析師。請根據用戶輸入的心情、場景或靈感，從音樂庫匹配邏輯出發，生成 5 個適合的音樂風格標籤（Tags，如 Lo-Fi, Pop, Rock, 輕音樂）和 3 個情感關鍵字。請嚴格以 JSON 格式輸出，結構如：{"tags": [], "emotions": []}，不要包含任何 Markdown 標記或額外解釋。
        """.trimIndent()

        var lastError: Exception? = null
        for (model in MODELS) {
            try {
                val result = generateTagsOnce(model, systemPrompt, userMessage)
                if (result.isSuccess) return@withContext result
                lastError = result.exceptionOrNull() as? Exception
                android.util.Log.w("AiClient", "model $model failed: ${lastError?.message}")
            } catch (e: Exception) {
                lastError = e
                android.util.Log.w("AiClient", "model $model threw: ${e.message}")
            }
        }
        Result.failure(lastError ?: Exception("所有模型都失败"))
    }

    private fun generateTagsOnce(model: String, systemPrompt: String, userMessage: String): Result<TagsResult> {
        return try {
            val body = JSONObject().apply {
                put("model", model)
                put("response_format", JSONObject().apply { put("type", "json_object") })
                // ★ 智谱联网搜索：search_result=true 让模型把搜索结果直接
                //   整合进 content 里，我们不用手动处理 tool_calls
                put("tools", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "web_search")
                        put("web_search", JSONObject().apply {
                            put("enable", true)
                            put("search_result", true)
                        })
                    })
                })
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
            }

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $API_KEY")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return Result.failure(Exception("API ${response.code}: $errorBody"))
            }

            // ── 解析 OpenAI 风格响应 ──
            val responseBody = response.body?.string() ?: ""
            val json = JSONObject(responseBody)
            val choices = json.getJSONArray("choices")
            val first = choices.getJSONObject(0)
            val message = first.getJSONObject("message")
            val content = message.optString("content", "").trim()

            if (content.isEmpty()) {
                // 智谱偶尔在 tool_call 模式下 content 为空
                return Result.failure(
                    Exception("AI 返回空内容（可能联网被拒）。响应：$responseBody")
                )
            }

            val cleaned = content
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val inner = try {
                JSONObject(cleaned)
            } catch (_: Exception) {
                val start = cleaned.indexOf('{')
                val end = cleaned.lastIndexOf('}')
                if (start >= 0 && end > start) {
                    JSONObject(cleaned.substring(start, end + 1))
                } else {
                    return Result.failure(
                        Exception("AI 返回非 JSON 格式：$content")
                    )
                }
            }

            val tags = inner.optJSONArray("tags")?.toStringList() ?: emptyList()
            val emotions = inner.optJSONArray("emotions")?.toStringList() ?: emptyList()

            Result.success(TagsResult(tags, emotions, content))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }
}
