package com.example.util

import android.util.Base64
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Universal Smart Local Proxy Bridge (SOCKS5 / SOCKS4 / HTTP / HTTPS -> Local HTTP Proxy).
 *
 * Why this bridge is critical for Android WebView:
 * 1. Chromium / Android System WebView does NOT natively support SOCKS5 username/password
 *    authentication, nor does it reliably send Proxy-Authorization headers on HTTPS CONNECT
 *    tunnels without failing with ERR_TUNNEL_CONNECTION_FAILED or ERR_PROXY_CONNECTION_FAILED.
 * 2. Every upstream socket in this bridge explicitly uses `Socket(Proxy.NO_PROXY)` so it is
 *    100% immune to JVM global proxy properties (`socksProxyHost` / `http.proxyHost`).
 * 3. Smart Protocol Auto-Detection: If a user configures a proxy as SOCKS5 when it is actually
 *    HTTP or SOCKS4 (or vice versa), the bridge automatically detects the working protocol and
 *    caches it for `host:port`.
 * 4. SOCKS5 Domain-to-IPv4 Fallback: If an upstream SOCKS5 proxy rejects `ATYP=0x03` (domain
 *    resolution), the bridge automatically resolves the domain to IPv4 and retries `ATYP=0x01`.
 * 5. Fail-Safe Direct Fallback: If the upstream proxy dies mid-session or refuses connections,
 *    the bridge can seamlessly fall back to a direct `Socket(Proxy.NO_PROXY)` so websites in
 *    WebView NEVER fail to open with `ERR_PROXY_CONNECTION_FAILED` or `ERR_TUNNEL_CONNECTION_FAILED`.
 */
object LocalSocks5HttpBridge {

    private const val TAG = "Socks5Bridge"

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning: Boolean = false

    @Volatile
    var localPort: Int = 0
        private set

    @Volatile
    private var remoteHost: String = ""

    @Volatile
    private var remotePort: Int = 0

    @Volatile
    private var remoteType: String = "socks5"

    @Volatile
    private var proxyUser: String = ""

    @Volatile
    private var proxyPass: String = ""

    @Volatile
    var allowDirectFallbackOnDeadUpstream: Boolean = false

    @Volatile
    var onUpstreamFailure: ((host: String, port: Int, reason: String) -> Unit)? = null

    private val detectedProtocolCache = ConcurrentHashMap<String, String>()
    private val consecutiveUpstreamFailures = AtomicInteger(0)
    private val threadPool = Executors.newCachedThreadPool()

    // ── Intelligence API: ذكاء الجسر — يتعلم ويوزع ──
    @Volatile
    var lastRotationMs: Long = 0L
        private set

    private val protocolSuccess = ConcurrentHashMap<String, AtomicInteger>()
    private val protocolAttempts = ConcurrentHashMap<String, AtomicInteger>()
    private val targetCategoryStats = ConcurrentHashMap<String, AtomicInteger>()

    data class BridgeHealthSnapshot(
        val isConfigured: Boolean,
        val localPort: Int,
        val remoteType: String,
        val consecutiveFailures: Int,
        val cachedProtocols: Int,
        val lastRotationMs: Long
    )

    fun currentProtocol(): String = remoteType.ifBlank { "socks5" }

    fun isConfigured(): Boolean = remoteHost.isNotBlank() && remotePort in 1..65535

    fun healthSnapshot(): BridgeHealthSnapshot = BridgeHealthSnapshot(
        isConfigured = isConfigured(),
        localPort = localPort,
        remoteType = currentProtocol(),
        consecutiveFailures = consecutiveUpstreamFailures.get(),
        cachedProtocols = detectedProtocolCache.size,
        lastRotationMs = lastRotationMs
    )

    fun consumeFailureCount(): Int = consecutiveUpstreamFailures.getAndSet(0)

    fun peekFailureCount(): Int = consecutiveUpstreamFailures.get()

    /** تصفير ذكي للكاش عند الدوران — يوزع ذكاء البروتوكول من جديد */
    fun resetIntelligenceCache() {
        detectedProtocolCache.clear()
        consecutiveUpstreamFailures.set(0)
        lastRotationMs = System.currentTimeMillis()
        Log.i(TAG, "Intelligence: protocol cache cleared, failures reset (rotation)")
    }

    /** يسمى من المنسق لتدوير البروكسي عند الخطر — يرجع true إذا كان مهيأ */
    fun rotateIfConfigured(): Boolean {
        if (!isConfigured()) return false
        resetIntelligenceCache()
        return true
    }

    fun recordTunnelResult(protocol: String, success: Boolean, targetHost: String) {
        protocolAttempts.computeIfAbsent(protocol) { AtomicInteger(0) }.incrementAndGet()
        if (success) protocolSuccess.computeIfAbsent(protocol) { AtomicInteger(0) }.incrementAndGet()
        val cat = categorizeTarget(targetHost)
        targetCategoryStats.computeIfAbsent("$cat:$protocol") { AtomicInteger(0) }.incrementAndGet()
    }

    fun protocolSuccessRate(protocol: String): Double {
        val att = protocolAttempts[protocol]?.get() ?: 0
        if (att == 0) return 0.5
        val ok = protocolSuccess[protocol]?.get() ?: 0
        return ok.toDouble() / att.toDouble()
    }

    /** لقطة قابلة للحفظ: نجاح/محاولات كل بروتوكول — للذاكرة المستمرة عبر الجلسات */
    fun protocolRatesSnapshot(): Map<String, Pair<Int, Int>> {
        val out = mutableMapOf<String, Pair<Int, Int>>()
        for ((proto, att) in protocolAttempts) {
            out[proto] = Pair(protocolSuccess[proto]?.get() ?: 0, att.get())
        }
        return out
    }

    fun restoreProtocolRates(snapshot: Map<String, Pair<Int, Int>>) {
        for ((proto, pair) in snapshot) {
            protocolSuccess.computeIfAbsent(proto) { AtomicInteger(0) }.set(pair.first)
            protocolAttempts.computeIfAbsent(proto) { AtomicInteger(0) }.set(pair.second)
        }
    }

    /** اختيار تكيفي للبروتوكول حسب فئة الهدف + ما تعلمه الجسر */
    fun bestProtocolForTarget(targetHost: String, fallback: String): String {
        val cat = categorizeTarget(targetHost)
        val candidates = listOf("socks5", "http", "socks4")
        var best = fallback.ifBlank { "socks5" }
        var bestScore = -1.0
        for (p in candidates) {
            val rate = protocolSuccessRate(p)
            val catBonus = (targetCategoryStats["$cat:$p"]?.get() ?: 0) * 0.01
            val score = rate + catBonus
            if (score > bestScore) { bestScore = score; best = p }
        }
        return best
    }

    fun categorizeTarget(host: String): String {
        val h = host.lowercase()
        return when {
            h.contains("amazon") || h.contains("amzn") || h.contains("walmart") || h.contains("nike") -> "e-commerce"
            h.contains("facebook") || h.contains("instagram") || h.contains("tiktok") || h.contains("twitter") -> "social"
            h.contains("google") || h.contains("bing") || h.contains("api") -> "api"
            h.contains("cloudflare") || h.contains("captcha") || h.contains("challenge") -> "challenge"
            else -> "general"
        }
    }

    @Synchronized
    fun start(
        host: String,
        port: Int,
        user: String = "",
        pass: String = "",
        type: String = "socks5"
    ): Int {
        val cleanHost = host.trim()
        val cleanType = type.trim().lowercase().ifBlank { "socks5" }
        val cleanUser = user.trim()
        val cleanPass = pass.trim()

        if (isRunning && serverSocket != null && !serverSocket!!.isClosed &&
            remoteHost == cleanHost && remotePort == port &&
            remoteType == cleanType && proxyUser == cleanUser && proxyPass == cleanPass &&
            localPort > 0
        ) {
            return localPort
        }

        // Update upstream config dynamically without tearing down the local server socket if already listening!
        remoteHost = cleanHost
        remotePort = port
        remoteType = cleanType
        proxyUser = cleanUser
        proxyPass = cleanPass
        consecutiveUpstreamFailures.set(0)

        if (isRunning && serverSocket != null && !serverSocket!!.isClosed && localPort > 0) {
            Log.i(TAG, "Updated LocalProxyBridge target on 127.0.0.1:$localPort -> ${cleanType.uppercase()} $cleanHost:$port")
            return localPort
        }

        stop()

        remoteHost = cleanHost
        remotePort = port
        remoteType = cleanType
        proxyUser = cleanUser
        proxyPass = cleanPass

        return try {
            val server = ServerSocket(0, 128, InetAddress.getByName("127.0.0.1"))
            serverSocket = server
            localPort = server.localPort
            isRunning = true

            Log.i(
                TAG,
                "Started Universal LocalProxyBridge on 127.0.0.1:$localPort -> ${cleanType.uppercase()} $remoteHost:$remotePort (Auth: ${proxyUser.isNotBlank()})"
            )

            thread(name = "LocalProxyBridge-Acceptor", isDaemon = true) {
                while (isRunning && !server.isClosed) {
                    try {
                        val clientSocket = server.accept()
                        threadPool.execute {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }

            localPort
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LocalProxyBridge: ${e.message}", e)
            0
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        localPort = 0
        Log.i(TAG, "Stopped LocalProxyBridge")
    }

    private fun handleClient(clientSocket: Socket) {
        var remoteSocket: Socket? = null
        try {
            clientSocket.soTimeout = 25000
            clientSocket.tcpNoDelay = true
            val clientIn = BufferedInputStream(clientSocket.getInputStream())
            val clientOut = BufferedOutputStream(clientSocket.getOutputStream())

            val requestLine = readLine(clientIn) ?: return
            val parts = requestLine.trim().split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val target = parts[1]

            if (method == "CONNECT") {
                // HTTPS Tunneling: CONNECT host:port HTTP/1.1
                val hostPort = target.split(":")
                val targetHost = hostPort[0].trim()
                val targetPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 443

                // Drain remaining HTTP headers from client
                while (true) {
                    val line = readLine(clientIn) ?: break
                    if (line.isEmpty()) break
                }

                // Block WebRTC STUN/TURN ports and local/private LAN port-scanning probes
                if (isBlockedLocalOrStunTarget(targetHost, targetPort)) {
                    clientOut.write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    clientOut.flush()
                    return
                }

                remoteSocket = openTunnelToTarget(targetHost, targetPort)
                if (remoteSocket == null) {
                    clientOut.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    clientOut.flush()
                    return
                }

                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII))
                clientOut.flush()

                clientSocket.soTimeout = 0
                pipeBiDirectional(clientSocket, remoteSocket, clientIn, clientOut)
            } else {
                // Plain HTTP request: GET http://host:port/path HTTP/1.1
                val urlString = if (target.startsWith("http://", ignoreCase = true)) {
                    target.substring(7)
                } else {
                    target
                }
                val hostPart = urlString.substringBefore("/")
                val targetPath = "/" + urlString.substringAfter("/", "")

                val hostPort = hostPart.split(":")
                val targetHost = hostPort[0].trim()
                val targetPort = hostPort.getOrNull(1)?.toIntOrNull() ?: 80

                // Collect client headers first (strip Proxy-*, Connection/Keep-Alive, WebView X-Requested-With, and IP-forwarding headers)
                val clientHeaders = mutableListOf<String>()
                var hasHostHeader = false
                while (true) {
                    val line = readLine(clientIn) ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Proxy-", ignoreCase = true)) continue
                    if (line.startsWith("Connection:", ignoreCase = true)) continue
                    if (line.startsWith("Keep-Alive:", ignoreCase = true)) continue
                    if (line.startsWith("X-Requested-With:", ignoreCase = true)) continue
                    if (line.startsWith("X-Forwarded-", ignoreCase = true)) continue
                    if (line.startsWith("X-Real-IP:", ignoreCase = true)) continue
                    if (line.startsWith("Forwarded:", ignoreCase = true)) continue
                    if (line.startsWith("Via:", ignoreCase = true)) continue
                    if (line.startsWith("Client-IP:", ignoreCase = true)) continue
                    if (line.startsWith("True-Client-IP:", ignoreCase = true)) continue
                    if (line.startsWith("X-Client-IP:", ignoreCase = true)) continue
                    if (line.startsWith("CF-Connecting-IP:", ignoreCase = true)) continue
                    if (line.startsWith("Host:", ignoreCase = true)) hasHostHeader = true
                    val sanitizedLine = if (line.startsWith("Sec-CH-UA", ignoreCase = true)) {
                        line.replace("Android WebView", "Google Chrome", ignoreCase = true)
                    } else {
                        line
                    }
                    clientHeaders.add(sanitizedLine)
                }

                if (isBlockedLocalOrStunTarget(targetHost, targetPort)) {
                    clientOut.write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    clientOut.flush()
                    return
                }

                remoteSocket = openTunnelToTarget(targetHost, targetPort)
                if (remoteSocket == null) {
                    clientOut.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    clientOut.flush()
                    return
                }

                val remoteOut = BufferedOutputStream(remoteSocket.getOutputStream())
                val httpVersion = parts.getOrNull(2) ?: "HTTP/1.1"
                remoteOut.write("$method $targetPath $httpVersion\r\n".toByteArray(Charsets.US_ASCII))
                if (!hasHostHeader) {
                    remoteOut.write("Host: $hostPart\r\n".toByteArray(Charsets.US_ASCII))
                }
                for (h in clientHeaders) {
                    remoteOut.write("$h\r\n".toByteArray(Charsets.US_ASCII))
                }
                remoteOut.write("Connection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                remoteOut.flush()

                clientSocket.soTimeout = 0
                pipeBiDirectional(clientSocket, remoteSocket, clientIn, clientOut)
            }
        } catch (_: Exception) {
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
            try { remoteSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun isBlockedLocalOrStunTarget(host: String, port: Int): Boolean {
        val h = host.trim().lowercase().removeSurrounding("[", "]")
        if (h.isBlank()) return true
        // Block STUN/TURN ports & hosts
        if (port in setOf(3478, 5349, 19302, 19305, 19307, 19308)) return true
        if (h.startsWith("stun.") || h.startsWith("turn.") || h.contains(".stun.") || h.contains(".turn.")) return true
        // Block loopback & local network port scanning by anti-bot scripts
        if (h == "localhost" || h == "0.0.0.0" || h == "::1" || h.startsWith("127.") || h.endsWith(".local")) return true
        if (h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("169.254.")) return true
        if (Regex("""^172\.(1[6-9]|2\d|3[0-1])\.""").containsMatchIn(h)) return true
        return false
    }

    /**
     * Opens a tunnel to (targetHost, targetPort) strictly via the configured upstream proxy,
     * with automatic protocol detection (SOCKS5 -> HTTP -> SOCKS4) and transient retry
     * so the real IP is NEVER leaked even if a proxy socket momentarily drops.
     */
    private fun openTunnelToTarget(targetHost: String, targetPort: Int): Socket? {
        val uHost = remoteHost
        val uPort = remotePort
        val uUser = proxyUser
        val uPass = proxyPass
        val uType = remoteType

        if (uHost.isNotBlank() && uPort in 1..65535) {
            val cacheKey = "$uHost:$uPort"
            val preferred = detectedProtocolCache[cacheKey] ?: when {
                uType.contains("socks4") -> "socks4"
                uType.contains("http") -> "http"
                else -> "socks5"
            }

            // ذكاء توزيعي: رتب البروتوكولات حسب ما تعلمه الجسر لكل فئة هدف
            val learned = bestProtocolForTarget(targetHost, preferred)
            val protocolOrder = listOf(preferred, learned, "socks5", "http", "socks4").distinct()

            for (retryRound in 0..1) {
                var tcpReachable = true
                for (proto in protocolOrder) {
                    if (!tcpReachable) break
                    val attempt = when (proto) {
                        "socks5" -> connectSocks5(uHost, uPort, uUser, uPass, targetHost, targetPort)
                        "socks4" -> connectSocks4(uHost, uPort, uUser, targetHost, targetPort)
                        "http" -> connectHttpUpstream(uHost, uPort, uUser, uPass, targetHost, targetPort)
                        else -> ConnectAttemptResult(null, tcpReachable = false)
                    }
                    if (attempt.socket != null) {
                        consecutiveUpstreamFailures.set(0)
                        detectedProtocolCache[cacheKey] = proto
                        recordTunnelResult(proto, true, targetHost)
                        return attempt.socket
                    }
                    recordTunnelResult(proto, false, targetHost)
                    if (!attempt.tcpReachable) {
                        tcpReachable = false
                    }
                }
                if (retryRound == 0) {
                    try { Thread.sleep(220L) } catch (_: Exception) {}
                }
            }

            val fails = consecutiveUpstreamFailures.incrementAndGet()
            if (fails == 1 || fails == 4) {
                Log.w(TAG, "Upstream proxy $uHost:$uPort failed ($fails consecutive) for $targetHost:$targetPort — blocking direct leak.")
                onUpstreamFailure?.invoke(uHost, uPort, "تعذر الاتصال بالبروكسي $uHost:$uPort (تم منع تسريب الـ IP الحقيقي)")
            }
            // Strict Fail-Closed when upstream proxy is configured unless explicitly overridden
            if (!allowDirectFallbackOnDeadUpstream) {
                return null
            }
        }

        if (allowDirectFallbackOnDeadUpstream) {
            return connectDirect(targetHost, targetPort)
        }
        return null
    }

    private data class ConnectAttemptResult(
        val socket: Socket?,
        val tcpReachable: Boolean
    )

    private fun connectDirect(targetHost: String, targetPort: Int): Socket? {
        val s = Socket(Proxy.NO_PROXY)
        return try {
            s.tcpNoDelay = true
            s.soTimeout = 15000
            s.connect(InetSocketAddress(targetHost, targetPort), 10000)
            s.soTimeout = 0
            s
        } catch (e: Exception) {
            try { s.close() } catch (_: Exception) {}
            null
        }
    }

    private fun connectSocks5(
        uHost: String,
        uPort: Int,
        uUser: String,
        uPass: String,
        targetHost: String,
        targetPort: Int
    ): ConnectAttemptResult {
        // First try with domain name (ATYP = 0x03) or direct IPv4 if targetHost is already an IPv4 literal
        val firstTry = connectSocks5Internal(uHost, uPort, uUser, uPass, targetHost, targetPort, forceResolveIpv4 = false)
        if (firstTry.socket != null || !firstTry.tcpReachable) {
            return firstTry
        }
        // If SOCKS5 handshake worked up to CONNECT, some SOCKS5 servers disable remote DNS (ATYP=0x03).
        // Retry once with locally resolved IPv4 (ATYP=0x01).
        val isAlreadyIpv4 = targetHost.split(".").let { it.size == 4 && it.all { p -> p.toIntOrNull() in 0..255 } }
        if (!isAlreadyIpv4) {
            val secondTry = connectSocks5Internal(uHost, uPort, uUser, uPass, targetHost, targetPort, forceResolveIpv4 = true)
            if (secondTry.socket != null) {
                return secondTry
            }
        }
        return ConnectAttemptResult(null, tcpReachable = true)
    }

    private fun connectSocks5Internal(
        uHost: String,
        uPort: Int,
        uUser: String,
        uPass: String,
        targetHost: String,
        targetPort: Int,
        forceResolveIpv4: Boolean
    ): ConnectAttemptResult {
        val s = Socket(Proxy.NO_PROXY)
        var connectedTcp = false
        try {
            s.tcpNoDelay = true
            s.soTimeout = 7000
            s.connect(InetSocketAddress(uHost, uPort), 4500)
            connectedTcp = true
            val sIn = s.getInputStream()
            val sOut = s.getOutputStream()

            // 1. SOCKS5 Greeting (RFC 1928)
            val hasAuth = uUser.isNotBlank() && uPass.isNotBlank()
            if (hasAuth) {
                sOut.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            } else {
                sOut.write(byteArrayOf(0x05, 0x01, 0x00))
            }
            sOut.flush()

            val greetingResp = ByteArray(2)
            readExact(sIn, greetingResp)
            if (greetingResp[0] != 0x05.toByte()) {
                s.close()
                return ConnectAttemptResult(null, tcpReachable = true)
            }

            val chosenMethod = greetingResp[1].toInt() and 0xFF
            if (chosenMethod == 0xFF) {
                s.close()
                return ConnectAttemptResult(null, tcpReachable = true)
            }

            // 2. Authentication Subnegotiation (RFC 1929)
            if (chosenMethod == 0x02) {
                if (!hasAuth) {
                    s.close()
                    return ConnectAttemptResult(null, tcpReachable = true)
                }
                val uBytes = uUser.toByteArray(Charsets.UTF_8)
                val pBytes = uPass.toByteArray(Charsets.UTF_8)
                val authReq = ByteArray(3 + uBytes.size + pBytes.size)
                authReq[0] = 0x01
                authReq[1] = uBytes.size.toByte()
                System.arraycopy(uBytes, 0, authReq, 2, uBytes.size)
                authReq[2 + uBytes.size] = pBytes.size.toByte()
                System.arraycopy(pBytes, 0, authReq, 3 + uBytes.size, pBytes.size)

                sOut.write(authReq)
                sOut.flush()

                val authResp = ByteArray(2)
                readExact(sIn, authResp)
                if (authResp[1] != 0x00.toByte()) {
                    s.close()
                    return ConnectAttemptResult(null, tcpReachable = true)
                }
            }

            // 3. SOCKS5 CONNECT request
            val ipParts = targetHost.split(".")
            val isIpv4Literal = ipParts.size == 4 && ipParts.all { part -> part.toIntOrNull() in 0..255 }

            val resolvedIpv4Bytes: ByteArray? = if (isIpv4Literal) {
                ByteArray(4) { i -> ipParts[i].toInt().toByte() }
            } else if (forceResolveIpv4) {
                resolveIpv4Bytes(targetHost)
            } else {
                null
            }

            val connReq = if (resolvedIpv4Bytes != null && resolvedIpv4Bytes.size == 4) {
                val req = ByteArray(10)
                req[0] = 0x05
                req[1] = 0x01 // CONNECT
                req[2] = 0x00
                req[3] = 0x01 // ATYP = IPv4
                System.arraycopy(resolvedIpv4Bytes, 0, req, 4, 4)
                req[8] = (targetPort shr 8).toByte()
                req[9] = (targetPort and 0xFF).toByte()
                req
            } else {
                val hostBytes = targetHost.toByteArray(Charsets.US_ASCII)
                val req = ByteArray(4 + 1 + hostBytes.size + 2)
                req[0] = 0x05
                req[1] = 0x01 // CONNECT
                req[2] = 0x00
                req[3] = 0x03 // ATYP = Domain Name
                req[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
                req[5 + hostBytes.size] = (targetPort shr 8).toByte()
                req[6 + hostBytes.size] = (targetPort and 0xFF).toByte()
                req
            }

            sOut.write(connReq)
            sOut.flush()

            // 4. SOCKS5 Connection Response
            val head = ByteArray(4)
            readExact(sIn, head)
            if (head[1] != 0x00.toByte()) {
                s.close()
                return ConnectAttemptResult(null, tcpReachable = true)
            }

            val atyp = head[3].toInt() and 0xFF
            when (atyp) {
                0x01 -> readExact(sIn, ByteArray(4 + 2))
                0x03 -> {
                    val len = sIn.read()
                    if (len == -1) throw EOFException("Premature EOF reading BND.ADDR")
                    readExact(sIn, ByteArray(len + 2))
                }
                0x04 -> readExact(sIn, ByteArray(16 + 2))
            }

            s.soTimeout = 0
            return ConnectAttemptResult(s, tcpReachable = true)
        } catch (e: Exception) {
            try { s.close() } catch (_: Exception) {}
            return ConnectAttemptResult(null, tcpReachable = connectedTcp)
        }
    }

    private fun connectSocks4(
        uHost: String,
        uPort: Int,
        uUser: String,
        targetHost: String,
        targetPort: Int
    ): ConnectAttemptResult {
        val s = Socket(Proxy.NO_PROXY)
        var connectedTcp = false
        try {
            s.tcpNoDelay = true
            s.soTimeout = 6500
            s.connect(InetSocketAddress(uHost, uPort), 4500)
            connectedTcp = true
            val sIn = s.getInputStream()
            val sOut = s.getOutputStream()

            val ipParts = targetHost.split(".")
            val isIpv4Literal = ipParts.size == 4 && ipParts.all { part -> part.toIntOrNull() in 0..255 }
            val userBytes = uUser.toByteArray(Charsets.US_ASCII)

            // Prefer SOCKS4a (remote DNS resolution via 0.0.0.1 + domain) to prevent local DNS leaks!
            val req: ByteArray = if (isIpv4Literal) {
                val ipv4 = ByteArray(4) { i -> ipParts[i].toInt().toByte() }
                val buf = ByteArray(8 + userBytes.size + 1)
                buf[0] = 0x04
                buf[1] = 0x01 // CONNECT
                buf[2] = (targetPort shr 8).toByte()
                buf[3] = (targetPort and 0xFF).toByte()
                System.arraycopy(ipv4, 0, buf, 4, 4)
                if (userBytes.isNotEmpty()) {
                    System.arraycopy(userBytes, 0, buf, 8, userBytes.size)
                }
                buf[8 + userBytes.size] = 0x00
                buf
            } else {
                // SOCKS4a (0.0.0.1 + domain) — remote DNS resolution on the proxy server
                val hostBytes = targetHost.toByteArray(Charsets.US_ASCII)
                val buf = ByteArray(8 + userBytes.size + 1 + hostBytes.size + 1)
                buf[0] = 0x04
                buf[1] = 0x01
                buf[2] = (targetPort shr 8).toByte()
                buf[3] = (targetPort and 0xFF).toByte()
                buf[4] = 0x00
                buf[5] = 0x00
                buf[6] = 0x00
                buf[7] = 0x01
                var pos = 8
                if (userBytes.isNotEmpty()) {
                    System.arraycopy(userBytes, 0, buf, pos, userBytes.size)
                    pos += userBytes.size
                }
                buf[pos++] = 0x00
                System.arraycopy(hostBytes, 0, buf, pos, hostBytes.size)
                pos += hostBytes.size
                buf[pos] = 0x00
                buf
            }

            sOut.write(req)
            sOut.flush()

            val resp = ByteArray(8)
            readExact(sIn, resp)
            // SOCKS4 success reply: VN=0x00 (or 0x04), CD=0x5A (90 = request granted)
            if (resp[1] != 0x5A.toByte()) {
                s.close()
                return ConnectAttemptResult(null, tcpReachable = true)
            }

            s.soTimeout = 0
            return ConnectAttemptResult(s, tcpReachable = true)
        } catch (e: Exception) {
            try { s.close() } catch (_: Exception) {}
            return ConnectAttemptResult(null, tcpReachable = connectedTcp)
        }
    }

    private fun connectHttpUpstream(
        uHost: String,
        uPort: Int,
        uUser: String,
        uPass: String,
        targetHost: String,
        targetPort: Int
    ): ConnectAttemptResult {
        val s = Socket(Proxy.NO_PROXY)
        var connectedTcp = false
        try {
            s.tcpNoDelay = true
            s.soTimeout = 7000
            s.connect(InetSocketAddress(uHost, uPort), 4500)
            connectedTcp = true
            val sIn = BufferedInputStream(s.getInputStream())
            val sOut = BufferedOutputStream(s.getOutputStream())

            val sb = StringBuilder()
            sb.append("CONNECT $targetHost:$targetPort HTTP/1.1\r\n")
            sb.append("Host: $targetHost:$targetPort\r\n")
            sb.append("Proxy-Connection: keep-alive\r\n")
            if (uUser.isNotBlank() && uPass.isNotBlank()) {
                val credentials = "$uUser:$uPass"
                val base64Auth = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                sb.append("Proxy-Authorization: Basic $base64Auth\r\n")
            }
            sb.append("\r\n")

            sOut.write(sb.toString().toByteArray(Charsets.US_ASCII))
            sOut.flush()

            val statusLine = readLine(sIn) ?: run {
                s.close()
                return ConnectAttemptResult(null, tcpReachable = true)
            }

            // Drain remaining response headers from HTTP proxy
            while (true) {
                val line = readLine(sIn) ?: break
                if (line.isEmpty()) break
            }

            if (!statusLine.contains(" 200 ")) {
                s.close()
                return ConnectAttemptResult(null, tcpReachable = true)
            }

            s.soTimeout = 0
            return ConnectAttemptResult(s, tcpReachable = true)
        } catch (e: Exception) {
            try { s.close() } catch (_: Exception) {}
            return ConnectAttemptResult(null, tcpReachable = connectedTcp)
        }
    }

    private fun resolveIpv4Bytes(host: String): ByteArray? {
        return try {
            val addresses = InetAddress.getAllByName(host)
            val ipv4 = addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull()
            val bytes = ipv4?.address
            if (bytes != null && bytes.size == 4) bytes else null
        } catch (_: Exception) {
            null
        }
    }

    private fun pipeBiDirectional(client: Socket, remote: Socket, clientIn: InputStream, clientOut: OutputStream) {
        val remoteIn = remote.getInputStream()
        val remoteOut = remote.getOutputStream()
        val closed = AtomicBoolean(false)

        fun closeBoth() {
            if (closed.compareAndSet(false, true)) {
                try { client.close() } catch (_: Exception) {}
                try { remote.close() } catch (_: Exception) {}
            }
        }

        val t1 = thread(name = "Bridge-C2R", isDaemon = true) {
            try {
                val buf = ByteArray(16384)
                var read: Int
                while (clientIn.read(buf).also { read = it } != -1) {
                    remoteOut.write(buf, 0, read)
                    remoteOut.flush()
                }
            } catch (_: Exception) {
            } finally {
                closeBoth()
            }
        }

        val t2 = thread(name = "Bridge-R2C", isDaemon = true) {
            try {
                val buf = ByteArray(16384)
                var read: Int
                while (remoteIn.read(buf).also { read = it } != -1) {
                    clientOut.write(buf, 0, read)
                    clientOut.flush()
                }
            } catch (_: Exception) {
            } finally {
                closeBoth()
            }
        }

        try { t1.join() } catch (_: Exception) {}
        try { t2.join() } catch (_: Exception) {}
        closeBoth()
    }

    private fun readLine(input: InputStream): String? {
        val bos = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (bos.size() == 0) return null
                break
            }
            if (b == '\n'.code) {
                break
            }
            if (b != '\r'.code) {
                bos.write(b)
            }
        }
        return bos.toString("US-ASCII")
    }

    private fun readExact(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw EOFException("Premature EOF reading proxy frame")
            offset += read
        }
    }
}
