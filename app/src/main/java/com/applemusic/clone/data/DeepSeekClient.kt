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

object DeepSeekClient {

    // ⚠️ 在此填入你的 DeepSeek API Key
    private const val API_KEY = "sk-a50abd6a080b4716b352f5801ea83d7e"
    private const val BASE_URL = "https://api.deepseek.com/chat/completions"
    private const val MODEL = "deepseek-chat"

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

    suspend fun chat(
        systemPrompt: String,
        userMessage: String,
        temperature: Float = 0.7f
    ): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userMessage)
                })
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put("messages", messages)
                put("temperature", temperature.toDouble())
                put("max_tokens", 2048)
            }

            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer $API_KEY")
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
            val choices = json.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            val content = message.getString("content")
            val finishReason = firstChoice.getString("finish_reason")

            Result.success(ChatResponse(content.trim(), finishReason))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
