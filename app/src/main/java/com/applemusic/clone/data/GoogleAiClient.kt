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

object GoogleAiClient {

    private const val API_KEY = "AQ.Ab8RN6Kf7q7T9HkYY05zpQUj8dHmU7KA1-3ms9Lu0BQZ7Flw7w"
    private const val MODEL = "gemini-2.0-flash"
    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$API_KEY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    data class ChatResponse(
        val content: String,
        val finishReason: String
    )

    /**
     * 调用 Gemini API，将 systemPrompt + userMessage 合并为对话内容。
     * Gemini REST API 使用 `contents` 数组，`systemInstruction` 字段支持系统提示。
     */
    suspend fun chat(
        systemPrompt: String,
        userMessage: String,
        temperature: Float = 0.7f
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            // 系统提示
            val systemInstruction = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            }

            // 用户消息
            val userContent = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", userMessage) })
                })
            }

            // 生成配置
            val generationConfig = JSONObject().apply {
                put("temperature", temperature.toDouble())
                put("maxOutputTokens", 2048)
            }

            val body = JSONObject().apply {
                put("systemInstruction", systemInstruction)
                put("contents", JSONArray().apply { put(userContent) })
                put("generationConfig", generationConfig)
            }

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(Exception("API error ${response.code}: $errorBody"))
            }

            val responseBody = response.body?.string() ?: ""
            val json = JSONObject(responseBody)
            val candidates = json.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text")
            val finishReason = firstCandidate.optString("finishReason", "STOP")

            Result.success(ChatResponse(text.trim(), finishReason))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
