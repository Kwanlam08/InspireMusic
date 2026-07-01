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
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.MulticastSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
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

class LocalSendReceiveSession(
    val address: String,
    private val onClose: () -> Unit
) {
    fun close() = onClose()
}

class LocalSendBackupSender(context: Context) {
    private val appContext = context.applicationContext
    private val fingerprint = "$FINGERPRINT_PREFIX${UUID.randomUUID()}"
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
        found.values
            .filter { it.fingerprint.startsWith(FINGERPRINT_PREFIX) }
            .sortedBy { it.alias.lowercase() }
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

    suspend fun startReceiveSession(
        onBackupReceived: (String) -> Unit
    ): Result<LocalSendReceiveSession> = withContext(Dispatchers.IO) {
        runCatching {
            val serverSocket = ServerSocket(MULTICAST_PORT)
            val multicastSocket = MulticastSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(MULTICAST_PORT))
                soTimeout = 700
                joinGroup(InetAddress.getByName(MULTICAST_ADDRESS))
            }
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock = wifiManager?.createMulticastLock("InspireMusicLocalSendReceiver")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            val tokens = ConcurrentHashMap<String, String>()
            val thread = Thread {
                while (!serverSocket.isClosed) {
                    runCatching {
                        serverSocket.accept().use { socket ->
                            handleServerRequest(socket, tokens, onBackupReceived)
                        }
                    }
                }
            }.apply {
                isDaemon = true
                name = "InspireMusicLocalSendReceiver"
                start()
            }
            val announceThread = Thread {
                val group = InetAddress.getByName(MULTICAST_ADDRESS)
                val announcement = localDeviceJson(announce = true, receiveEnabled = true)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                runCatching {
                    multicastSocket.send(DatagramPacket(announcement, announcement.size, group, MULTICAST_PORT))
                }
                val buffer = ByteArray(8192)
                while (!multicastSocket.isClosed) {
                    runCatching {
                        val packet = DatagramPacket(buffer, buffer.size)
                        multicastSocket.receive(packet)
                        val raw = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                        val json = JSONObject(raw)
                        val remoteFingerprint = json.optString("fingerprint")
                        if (remoteFingerprint == fingerprint) return@runCatching
                        val response = localDeviceJson(announce = false, receiveEnabled = true)
                            .toString()
                            .toByteArray(Charsets.UTF_8)
                        multicastSocket.send(DatagramPacket(response, response.size, packet.address, packet.port))
                    }
                }
            }.apply {
                isDaemon = true
                name = "InspireMusicLocalSendAnnouncer"
                start()
            }
            LocalSendReceiveSession(localIpv4Addresses().firstOrNull().orEmpty()) {
                runCatching { serverSocket.close() }
                runCatching { multicastSocket.leaveGroup(InetAddress.getByName(MULTICAST_ADDRESS)) }
                runCatching { multicastSocket.close() }
                if (multicastLock?.isHeld == true) multicastLock.release()
            }
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

    private fun handleServerRequest(
        socket: Socket,
        tokens: ConcurrentHashMap<String, String>,
        onBackupReceived: (String) -> Unit
    ) {
        val input = socket.getInputStream().buffered()
        val requestLine = readHttpLine(input)
        if (requestLine.isBlank()) {
            writeHttpResponse(socket, 400, "Bad Request")
            return
        }
        val parts = requestLine.split(" ")
        val pathWithQuery = parts.getOrNull(1).orEmpty()
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readHttpLine(input)
            if (line.isBlank()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
        }
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) input.readNBytes(contentLength) else ByteArray(0)
        val path = pathWithQuery.substringBefore('?')
        val query = pathWithQuery.substringAfter('?', "")

        when (path) {
            "/api/localsend/v2/register" -> {
                writeHttpJson(socket, localDeviceJson(announce = false, receiveEnabled = true))
            }
            "/api/localsend/v2/prepare-upload" -> {
                val request = runCatching { JSONObject(String(body, Charsets.UTF_8)) }.getOrNull()
                val files = request?.optJSONObject("files")
                val responseFiles = JSONObject()
                if (files != null) {
                    files.keys().forEach { fileId ->
                        val token = UUID.randomUUID().toString()
                        tokens[fileId] = token
                        responseFiles.put(fileId, token)
                    }
                }
                writeHttpJson(
                    socket,
                    JSONObject()
                        .put("sessionId", UUID.randomUUID().toString())
                        .put("files", responseFiles)
                )
            }
            "/api/localsend/v2/upload" -> {
                val queryParams = parseQuery(query)
                val fileId = queryParams["fileId"].orEmpty()
                val token = queryParams["token"].orEmpty()
                if (fileId.isBlank() || tokens[fileId] != token) {
                    writeHttpResponse(socket, 403, "Forbidden")
                    return
                }
                tokens.remove(fileId)
                onBackupReceived(String(body, Charsets.UTF_8))
                writeHttpResponse(socket, 200, "OK")
            }
            else -> writeHttpResponse(socket, 404, "Not Found")
        }
    }

    private fun localDeviceJson(announce: Boolean, receiveEnabled: Boolean = false): JSONObject {
        return JSONObject()
            .put("alias", "Inspire Music")
            .put("version", "2.0")
            .put("deviceModel", Build.MODEL)
            .put("deviceType", "mobile")
            .put("fingerprint", fingerprint)
            .put("port", MULTICAST_PORT)
            .put("protocol", "http")
            .put("download", receiveEnabled)
            .put("announce", announce)
    }

    private fun readHttpLine(input: java.io.BufferedInputStream): String {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val next = input.read()
            if (next == -1) break
            if (next == '\n'.code) break
            if (next != '\r'.code) bytes.add(next.toByte())
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun writeHttpJson(socket: Socket, body: JSONObject) {
        writeHttpResponse(socket, 200, body.toString(), "application/json; charset=utf-8")
    }

    private fun writeHttpResponse(
        socket: Socket,
        code: Int,
        body: String,
        contentType: String = "text/plain; charset=utf-8"
    ) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "OK"
        }
        val header = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().use { output ->
            output.write(header.toByteArray(Charsets.UTF_8))
            output.write(bodyBytes)
            output.flush()
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "")
            if (key.isBlank()) return@mapNotNull null
            val value = part.substringAfter('=', "")
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }.toMap()
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
        const val FINGERPRINT_PREFIX = "inspire-"
    }
}
