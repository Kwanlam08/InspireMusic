package com.applemusic.clone.data

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.MulticastSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class LocalSendDevice(
    val alias: String,
    val host: String,
    val port: Int,
    val protocol: String,
    val fingerprint: String
) {
    val displayName: String get() = "$alias ($host)"
}

data class LocalSendTransferResult(
    val deviceName: String,
    val bytesSent: Int
)

class LocalSendBackupSender(context: Context) {
    private val appContext = context.applicationContext
    private val fingerprint = "inspire-${UUID.randomUUID()}"
    private val client = createClient()
    private val scanClient = client.newBuilder()
        .connectTimeout(360, TimeUnit.MILLISECONDS)
        .readTimeout(700, TimeUnit.MILLISECONDS)
        .writeTimeout(700, TimeUnit.MILLISECONDS)
        .build()

    suspend fun discoverNearbyDevices(timeoutMillis: Long = 2600L): List<LocalSendDevice> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, LocalSendDevice>()
        discoverByMulticast(timeoutMillis).forEach { found[it.fingerprint] = it }
        discoverByHttpSweep().forEach { found[it.fingerprint] = it }
        found.values.sortedBy { it.alias.lowercase() }
    }

    suspend fun sendBackupToDevice(
        device: LocalSendDevice,
        fileName: String,
        content: String
    ): Result<LocalSendTransferResult> = withContext(Dispatchers.IO) {
        runCatching {
            sendToDevice(device, fileName, content)
        }
    }

    private fun discoverByMulticast(timeoutMillis: Long): List<LocalSendDevice> {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifiManager?.createMulticastLock("InspireMusicLocalSend")?.apply {
            setReferenceCounted(false)
        }
        val devices = linkedMapOf<String, LocalSendDevice>()
        val group = InetAddress.getByName(MULTICAST_ADDRESS)
        val deadline = System.currentTimeMillis() + timeoutMillis
        lock?.acquire()
        try {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(MULTICAST_PORT))
                socket.soTimeout = 250
                socket.timeToLive = 1
                socket.joinGroup(group)
                val announcement = localDeviceJson(announce = true).toString().toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(announcement, announcement.size, group, MULTICAST_PORT))

                val buffer = ByteArray(8192)
                while (System.currentTimeMillis() < deadline) {
                    runCatching {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val json = JSONObject(raw)
                        val remoteFingerprint = json.optString("fingerprint")
                        if (remoteFingerprint.isBlank() || remoteFingerprint == fingerprint) return@runCatching
                        val alias = json.optString("alias").ifBlank { "LocalSend" }
                        val port = json.optInt("port", MULTICAST_PORT)
                        val protocol = json.optString("protocol", "https").ifBlank { "https" }
                        val host = packet.address.hostAddress ?: return@runCatching
                        devices[remoteFingerprint] = LocalSendDevice(
                            alias = alias,
                            host = host,
                            port = port,
                            protocol = protocol,
                            fingerprint = remoteFingerprint
                        )
                    }
                }
                runCatching { socket.leaveGroup(group) }
            }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
        return devices.values.toList()
    }

    private suspend fun discoverByHttpSweep(): List<LocalSendDevice> = coroutineScope {
        val localAddresses = localIpv4Addresses()
        val jobs = localAddresses.flatMap { local ->
            val prefix = local.substringBeforeLast('.', missingDelimiterValue = "")
            if (prefix.isBlank()) return@flatMap emptyList()
            (1..254).map { suffix ->
                async(Dispatchers.IO) {
                    val host = "$prefix.$suffix"
                    if (host == local) return@async null
                    probeHost(host, "https") ?: probeHost(host, "http")
                }
            }
        }
        jobs.awaitAll().filterNotNull().distinctBy { it.fingerprint }
    }

    private fun probeHost(host: String, protocol: String): LocalSendDevice? {
        val body = localDeviceJson(announce = false).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$protocol://$host:$MULTICAST_PORT/api/localsend/v2/register")
            .post(body)
            .build()
        return runCatching {
            scanClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string().orEmpty())
                val remoteFingerprint = json.optString("fingerprint").ifBlank { "$protocol://$host:$MULTICAST_PORT" }
                if (remoteFingerprint == fingerprint) return@use null
                LocalSendDevice(
                    alias = json.optString("alias").ifBlank { "LocalSend" },
                    host = host,
                    port = json.optInt("port", MULTICAST_PORT),
                    protocol = json.optString("protocol", protocol).ifBlank { protocol },
                    fingerprint = remoteFingerprint
                )
            }
        }.getOrNull()
    }

    private fun sendToDevice(
        device: LocalSendDevice,
        fileName: String,
        content: String
    ): LocalSendTransferResult {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val fileId = UUID.randomUUID().toString()
        val baseUrl = "${device.protocol}://${device.host}:${device.port}"
        val prepareJson = JSONObject()
            .put("info", localDeviceJson(announce = false))
            .put(
                "files",
                JSONObject().put(
                    fileId,
                    JSONObject()
                        .put("id", fileId)
                        .put("fileName", fileName)
                        .put("size", bytes.size)
                        .put("fileType", "application/json")
                        .put("sha256", sha256(bytes))
                )
            )

        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val prepareRequest = Request.Builder()
            .url("$baseUrl/api/localsend/v2/prepare-upload")
            .post(prepareJson.toString().toRequestBody(jsonMediaType))
            .build()
        val prepareBody = client.newCall(prepareRequest).execute().use { response ->
            if (response.code == 204) return LocalSendTransferResult(device.displayName, 0)
            if (!response.isSuccessful) error("LocalSend rejected backup (${response.code})")
            response.body?.string().orEmpty()
        }
        val prepareResponse = JSONObject(prepareBody)
        val sessionId = prepareResponse.optString("sessionId")
        val token = prepareResponse.optJSONObject("files")?.optString(fileId).orEmpty()
        if (sessionId.isBlank() || token.isBlank()) error("LocalSend did not return an upload token")

        val uploadUrl = "$baseUrl/api/localsend/v2/upload".toHttpUrl().newBuilder()
            .addQueryParameter("sessionId", sessionId)
            .addQueryParameter("fileId", fileId)
            .addQueryParameter("token", token)
            .build()
        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        client.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful) error("LocalSend upload failed (${response.code})")
        }
        return LocalSendTransferResult(device.displayName, bytes.size)
    }

    private fun localDeviceJson(announce: Boolean): JSONObject {
        return JSONObject()
            .put("alias", "Inspire Music")
            .put("version", "2.0")
            .put("deviceModel", Build.MODEL)
            .put("deviceType", "mobile")
            .put("fingerprint", fingerprint)
            .put("port", MULTICAST_PORT)
            .put("protocol", "http")
            .put("download", false)
            .put("announce", announce)
    }

    private fun localIpv4Addresses(): List<String> {
        return runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .flatMap { networkInterface ->
                    Collections.list(networkInterface.inetAddresses)
                        .mapNotNull { address ->
                            val host = address.hostAddress ?: return@mapNotNull null
                            host.takeIf {
                                !address.isLoopbackAddress &&
                                    address is java.net.Inet4Address &&
                                    (it.startsWith("192.168.") || it.startsWith("10.") || it.matches(Regex("172\\.(1[6-9]|2\\d|3[0-1])\\..+")))
                            }
                        }
                }
        }.getOrDefault(emptyList())
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun createClient(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private companion object {
        const val MULTICAST_ADDRESS = "224.0.0.167"
        const val MULTICAST_PORT = 53317
    }
}
