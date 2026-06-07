package com.applemusic.clone.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * 代理设置：让 Google API 请求走用户自建/第三方代理。
 *
 * 用途：在受限网络（如大陆地区）访问 generativelanguage.googleapis.com。
 * 通过 SharedPreferences 持久化，DynamicProxySelector 每次连接时实时读取，
 * 所以改完设置不用重启 app，下次请求立刻生效。
 */
object ProxySettings {
    private const val PREFS_NAME = "proxy_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_TYPE = "type"

    const val TYPE_HTTP: String = "http"
    const val TYPE_SOCKS: String = "socks"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun isEnabled(): Boolean = prefs?.getBoolean(KEY_ENABLED, false) ?: false
    fun getHost(): String = prefs?.getString(KEY_HOST, "") ?: ""
    fun getPort(): Int = prefs?.getInt(KEY_PORT, 0) ?: 0
    fun getType(): String = prefs?.getString(KEY_TYPE, TYPE_HTTP) ?: TYPE_HTTP

    fun setAll(enabled: Boolean, host: String, port: Int, type: String) {
        prefs?.edit()
            ?.putBoolean(KEY_ENABLED, enabled)
            ?.putString(KEY_HOST, host.trim())
            ?.putInt(KEY_PORT, port)
            ?.putString(KEY_TYPE, type)
            ?.apply()
    }
}

/**
 * 给 OkHttp 用的动态代理选择器：每次连接都查一次 ProxySettings，
 * 改完设置后立刻生效，不用重建 OkHttpClient。
 */
class DynamicProxySelector : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        if (!ProxySettings.isEnabled()) {
            return listOf(Proxy.NO_PROXY)
        }
        val host = ProxySettings.getHost()
        val port = ProxySettings.getPort()
        if (host.isBlank() || port <= 0 || port > 65535) {
            return listOf(Proxy.NO_PROXY)
        }
        val type = if (ProxySettings.getType() == ProxySettings.TYPE_SOCKS) {
            Proxy.Type.SOCKS
        } else {
            Proxy.Type.HTTP
        }
        return listOf(Proxy(type, InetSocketAddress(host, port)))
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        Log.w("DynamicProxySelector", "代理 $sa 连接失败: ${uri} -> ${ioe?.message}")
    }
}
