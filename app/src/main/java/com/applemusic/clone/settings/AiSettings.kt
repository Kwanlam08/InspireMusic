package com.applemusic.clone.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiProvider(
    val value: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val protocol: AiProtocol
) {
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", AiProtocol.OPENAI),
    CLAUDE("claude", "Anthropic Claude", "https://api.anthropic.com", "claude-3-5-haiku-latest", AiProtocol.ANTHROPIC),
    GEMINI("gemini", "Google AI Studio", "https://generativelanguage.googleapis.com", "gemini-2.0-flash", AiProtocol.GEMINI),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", AiProtocol.OPENAI),
    QWEN("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", AiProtocol.OPENAI),
    ZHIPU("zhipu", "智谱 AI", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", AiProtocol.OPENAI),
    KIMI("kimi", "Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", AiProtocol.OPENAI),
    DOUBAO("doubao", "豆包", "https://ark.cn-beijing.volces.com/api/v3", "", AiProtocol.OPENAI),
    SILICONFLOW("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct", AiProtocol.OPENAI),
    OLLAMA("ollama", "Ollama", "http://10.0.2.2:11434", "llama3.2", AiProtocol.OLLAMA),
    CUSTOM("custom", "自定义 OpenAI 兼容接口", "", "", AiProtocol.OPENAI);

    companion object {
        fun fromValue(value: String?): AiProvider = entries.firstOrNull { it.value == value } ?: OPENAI
    }
}

enum class AiProtocol { OPENAI, ANTHROPIC, GEMINI, OLLAMA }

data class AiConfiguration(
    val provider: AiProvider = AiProvider.OPENAI,
    val baseUrl: String = AiProvider.OPENAI.defaultBaseUrl,
    val model: String = AiProvider.OPENAI.defaultModel,
    val hasApiKey: Boolean = false
)

data class AiSavedProfile(
    val id: String,
    val name: String,
    val provider: AiProvider,
    val baseUrl: String,
    val model: String,
    val hasApiKey: Boolean
)

class AiSettingsController(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureStore = KeystoreValueStore(appContext)
    private val _configuration = MutableStateFlow(readConfiguration())
    val configuration: StateFlow<AiConfiguration> = _configuration.asStateFlow()
    private val _profiles = MutableStateFlow(readProfiles())
    val profiles: StateFlow<List<AiSavedProfile>> = _profiles.asStateFlow()
    private val _activeProfileId = MutableStateFlow(prefs.getString(KEY_ACTIVE_PROFILE_ID, null))
    val activeProfileId: StateFlow<String?> = _activeProfileId.asStateFlow()

    fun save(provider: AiProvider, baseUrl: String, model: String, apiKey: String) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/').ifBlank { provider.defaultBaseUrl }
        val normalizedModel = model.trim().ifBlank { provider.defaultModel }
        prefs.edit()
            .putString(KEY_PROVIDER, provider.value)
            .putString(KEY_BASE_URL, normalizedBaseUrl)
            .putString(KEY_MODEL, normalizedModel)
            .apply()
        if (apiKey.isNotBlank()) secureStore.put(KEY_API_KEY, apiKey.trim())
        _configuration.value = readConfiguration()
    }

    fun clearApiKey() {
        secureStore.remove(KEY_API_KEY)
        _configuration.value = readConfiguration()
    }

    fun saveProfile(name: String, provider: AiProvider, baseUrl: String, model: String, apiKey: String) {
        val cleanedName = name.trim().ifBlank { "${provider.displayName} 配置" }
        save(provider, baseUrl, model, apiKey)
        val existing = _profiles.value.firstOrNull { it.name.equals(cleanedName, ignoreCase = true) }
        val id = existing?.id ?: UUID.randomUUID().toString()
        val normalized = readConfiguration()
        val currentKey = apiKey().takeIf { it.isNotBlank() }
        if (currentKey != null) secureStore.put(profileKey(id), currentKey)
        val profile = AiSavedProfile(
            id = id,
            name = cleanedName,
            provider = normalized.provider,
            baseUrl = normalized.baseUrl,
            model = normalized.model,
            hasApiKey = secureStore.get(profileKey(id))?.isNotBlank() == true
        )
        _profiles.value = (_profiles.value.filterNot { it.id == id } + profile)
            .sortedBy { it.name.lowercase() }
        persistProfiles()
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, id).apply()
        _activeProfileId.value = id
    }

    fun activateProfile(id: String): Boolean {
        val profile = _profiles.value.firstOrNull { it.id == id } ?: return false
        val profileKey = secureStore.get(profileKey(profile.id)).orEmpty()
        save(profile.provider, profile.baseUrl, profile.model, profileKey)
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, id).apply()
        _activeProfileId.value = id
        return true
    }

    fun deleteProfile(id: String) {
        _profiles.value = _profiles.value.filterNot { it.id == id }
        secureStore.remove(profileKey(id))
        if (prefs.getString(KEY_ACTIVE_PROFILE_ID, null) == id) {
            prefs.edit().remove(KEY_ACTIVE_PROFILE_ID).apply()
            _activeProfileId.value = null
        }
        persistProfiles()
    }

    fun apiKey(): String = secureStore.get(KEY_API_KEY).orEmpty()

    private fun readConfiguration(): AiConfiguration {
        val provider = AiProvider.fromValue(prefs.getString(KEY_PROVIDER, AiProvider.OPENAI.value))
        return AiConfiguration(
            provider = provider,
            baseUrl = prefs.getString(KEY_BASE_URL, provider.defaultBaseUrl).orEmpty().ifBlank { provider.defaultBaseUrl },
            model = prefs.getString(KEY_MODEL, provider.defaultModel).orEmpty().ifBlank { provider.defaultModel },
            hasApiKey = secureStore.get(KEY_API_KEY)?.isNotBlank() == true
        )
    }

    private fun readProfiles(): List<AiSavedProfile> = runCatching {
        val raw = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val provider = AiProvider.fromValue(item.optString("provider"))
                add(
                    AiSavedProfile(
                        id = id,
                        name = item.optString("name", provider.displayName),
                        provider = provider,
                        baseUrl = item.optString("baseUrl", provider.defaultBaseUrl),
                        model = item.optString("model", provider.defaultModel),
                        hasApiKey = secureStore.get(profileKey(id))?.isNotBlank() == true
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun persistProfiles() {
        val array = JSONArray()
        _profiles.value.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("provider", profile.provider.value)
                    .put("baseUrl", profile.baseUrl)
                    .put("model", profile.model)
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun profileKey(id: String) = "profile_api_key_$id"

    private companion object {
        const val PREFS_NAME = "ai_configuration"
        const val KEY_PROVIDER = "provider"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
        const val KEY_PROFILES = "profiles"
        const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    }
}

private class KeystoreValueStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_configuration_secure", Context.MODE_PRIVATE)

    fun put(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)).apply()
    }

    fun get(key: String): String? = runCatching {
        val payload = prefs.getString(key, null) ?: return null
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_SIZE)
        val encrypted = bytes.copyOfRange(IV_SIZE, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }.getOrNull()

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "inspire_music_ai_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_SIZE_BITS = 128
    }
}
