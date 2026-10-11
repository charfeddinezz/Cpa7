package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AppSettings
import com.example.service.ExtractedPlanResult
import com.example.service.TaskCategoryPlanner
import com.example.ui.AppViewModel
import com.example.ui.ScreenTab
import com.example.ui.components.MainNavigation
import com.example.ui.components.PhaseIndicatorStrip
import com.example.ui.components.TopConsoleBar
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.ClickScreen
import com.example.ui.screens.EmailPoolScreen
import com.example.ui.screens.InfoScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.ProxyPoolScreen
import com.example.ui.screens.ScriptsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.TaskScreen
import com.example.ui.theme.CpaBg
import com.example.ui.theme.MyApplicationTheme
import java.io.ByteArrayInputStream
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // Ensure hardware accelerated window rendering with graceful fallback
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        } catch (_: Exception) {}
        enableEdgeToEdge()
        // Foreground-service notifications need runtime permission on Android 13+
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        } catch (_: Exception) {}
        setContent {
            MyApplicationTheme {
                CpaAutomatorApp()
            }
        }
    }

    /**
     * Clears WebView cache, cookies, local storage, databases, and session state on demand
     * to prevent previous real-IP sessions from leaking information through cached assets.
     */
    fun clearWebViewData(webView: WebView? = null, onComplete: (() -> Unit)? = null) {
        clearWebViewData(this, webView, onComplete)
    }

    /**
     * Custom WebViewClient implemented in the main activity to intercept network requests
     * and strictly block all WebRTC-related STUN/TURN traffic and direct sockets to prevent
     * leaking the device's real public or private IP address.
     */
    open class CustomWebViewClient(
        private val proxyUserProvider: () -> String = { "" },
        private val proxyPassProvider: () -> String = { "" },
        private val onPageStartedCallback: ((WebView?, String?, Bitmap?) -> Unit)? = null,
        private val onPageFinishedCallback: ((WebView?, String?) -> Unit)? = null,
        private val onErrorCallback: ((WebView?, WebResourceRequest?, WebResourceError?) -> Unit)? = null,
        // ── Shield intel: لغة مقبولة موحدة مع navigator.language + منفذ الجسر + مزامنة الرابط النهائي ──
        private val acceptLanguageProvider: () -> String = { "" },
        private val bridgePortProvider: () -> Int = { 0 },
        private val onMainFrameFinalUrl: ((String) -> Unit)? = null,
        // ── Strict intelligence: وضع صارم عند الخطر + عدائية المضيف + تبليغ الحظر ──
        private val strictModeProvider: () -> Boolean = { false },
        private val isHostileHost: (String) -> Boolean = { false },
        private val onSurveillanceBlocked: ((String) -> Unit)? = null,
        // ── Tracker mode gate: allow = no blocking, block/deceive = block network ──
        private val trackerBlockProvider: () -> Boolean = { true },
        // ── Kill-switch: تصحيح XHR يمكن تعطيله من الإعدادات ──
        private val xhrCorrectionProvider: () -> Boolean = { true },
        // ── Renderer recovery: إعادة إنشاء WebView بعد موت العملية ──
        private val onRenderProcessGoneCallback: ((Boolean) -> Unit)? = null
    ) : WebViewClient() {

        /**
         * Overrides shouldInterceptRequest to block WebRTC STUN/TURN server connections,
         * ICE candidate exchanges, and background probe sockets — plus header-corrected
         * main-frame loads so Accept-Language NEVER contradicts navigator.language.
         */
        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            val uri = request?.url ?: return super.shouldInterceptRequest(view, request)
            if (isWebRtcStunTurnTraffic(uri, request.isForMainFrame)) {
                Log.w(TAG, "Shield intercepted and blocked WebRTC STUN/TURN traffic: $uri")
                return createBlockedWebResourceResponse()
            }
            if (isSurveillanceTraffic(uri, request.isForMainFrame)) {
                val domain = (uri.host ?: uri.toString()).lowercase()
                Log.w(TAG, "Shield strictly blocked session-replay surveillance: $uri")
                try { onSurveillanceBlocked?.invoke(domain) } catch (_: Exception) {}
                return createBlockedWebResourceResponse()
            }
            if (request.isForMainFrame && request.method.equals("GET", ignoreCase = true)) {
                try {
                    headerCorrectedMainFrame(request)?.let { return it }
                } catch (_: Exception) {
                }
            }
            if (!request.isForMainFrame && request.method.equals("GET", ignoreCase = true)) {
                try {
                    if (xhrCorrectionProvider()) {
                        headerCorrectedXhr(request)?.let { return it }
                    }
                } catch (_: Exception) {
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        /**
         * Re-executes a main-frame GET with Accept-Language forced to the spoofed locale,
         * following redirects manually (cookies synced) so smartlink chains keep working.
         * Returns null when no correction is needed or on any doubt (fail-open to WebView).
         */
        private fun headerCorrectedMainFrame(request: WebResourceRequest): WebResourceResponse? {
            val want = acceptLanguageProvider().trim()
            if (want.isBlank() || want.contains("{")) return null
            val scheme = request.url.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val current = request.requestHeaders["Accept-Language"] ?: request.requestHeaders["accept-language"]
            if (!current.isNullOrBlank() && current.equals(want, ignoreCase = true)) return null

            return executeCorrectedLoad(
                startUrl = request.url.toString(),
                request = request,
                wantLanguage = want,
                stripAppTag = true,
                syncFinalUrl = true
            )
        }

        /**
         * XHR/fetch GET correction: the site's own signing JS (e.g. signature-agent per RFC 9421)
         * reads browser state — if its API calls carried a different Accept-Language than the
         * spoofed navigator.language, the signature mismatches and the server blank-walls us.
         * Only GET XHR/fetch (no body to lose), never Range/media streams.
         */
        private fun headerCorrectedXhr(request: WebResourceRequest): WebResourceResponse? {
            val want = acceptLanguageProvider().trim()
            if (want.isBlank() || want.contains("{")) return null
            val scheme = request.url.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val h = request.requestHeaders
            val isXhr = h.containsKey("X-Requested-With") || h.containsKey("x-requested-with")
            val dest = h["Sec-Fetch-Dest"] ?: h["sec-fetch-dest"] ?: ""
            val isFetchApi = dest.equals("empty", ignoreCase = true)
            if (!isXhr && !isFetchApi) return null
            if (h.containsKey("Range") || h.containsKey("range")) return null
            val current = h["Accept-Language"] ?: h["accept-language"]
            if (!current.isNullOrBlank() && current.equals(want, ignoreCase = true)) return null

            return executeCorrectedLoad(
                startUrl = request.url.toString(),
                request = request,
                wantLanguage = want,
                stripAppTag = false, // XHR AJAX marker required by some backends — keep it
                syncFinalUrl = false
            )
        }

        /**
         * Shared corrected-load executor: forced Accept-Language, cookies synced, redirects
         * followed manually. Fail-open (null) on any doubt so pages never break.
         */
        private fun executeCorrectedLoad(
            startUrl: String,
            request: WebResourceRequest,
            wantLanguage: String,
            stripAppTag: Boolean,
            syncFinalUrl: Boolean
        ): WebResourceResponse? {
            var urlStr = startUrl
            val baseHeaders = request.requestHeaders.toMutableMap()
            baseHeaders.remove("Accept-Language")
            baseHeaders.remove("accept-language")
            baseHeaders["Accept-Language"] = wantLanguage
            baseHeaders.remove("Content-Length")
            baseHeaders.remove("content-length")
            baseHeaders.remove("Host")
            baseHeaders.remove("host")
            if (stripAppTag) {
                // App-identity leak: WebView tags requests with the host app package — strip it
                baseHeaders.remove("X-Requested-With")
                baseHeaders.remove("x-requested-with")
            }

            val bridgePort = bridgePortProvider()
            val proxy = if (bridgePort in 1..65535) {
                java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", bridgePort))
            } else {
                java.net.Proxy.NO_PROXY
            }

            var hops = 0
            while (hops < 6) {
                hops++
                val conn = (java.net.URL(urlStr).openConnection(proxy) as java.net.HttpURLConnection)
                try {
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 15000
                    conn.readTimeout = 20000
                    conn.requestMethod = "GET"
                    view_ua(request).takeIf { it.isNotBlank() }?.let {
                        conn.setRequestProperty("User-Agent", it)
                    }
                    for ((k, v) in baseHeaders) {
                        if (k.equals("User-Agent", ignoreCase = true)) continue
                        try { conn.setRequestProperty(k, v) } catch (_: Exception) {}
                    }
                    try {
                        CookieManager.getInstance().getCookie(urlStr)?.takeIf { it.isNotBlank() }?.let {
                            conn.setRequestProperty("Cookie", it)
                        }
                    } catch (_: Exception) {}
                    conn.connect()
                    val code = try { conn.responseCode } catch (_: Exception) { return null }
                    try {
                        val hdrs = conn.headerFields
                        val setCookies = (hdrs["Set-Cookie"] ?: hdrs["set-cookie"] ?: hdrs["Set-cookie"] ?: emptyList())
                        for (sc in setCookies) {
                            try { CookieManager.getInstance().setCookie(urlStr, sc) } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location") ?: return null
                        try { conn.disconnect() } catch (_: Exception) {}
                        urlStr = try { java.net.URL(java.net.URL(urlStr), loc).toString() } catch (_: Exception) { return null }
                        continue
                    }
                    val mime = (conn.contentType?.substringBefore(";")?.trim())?.ifBlank { "text/html" } ?: "text/html"
                    val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                        .find(conn.contentType ?: "")?.groupValues?.getOrNull(1)?.trim()?.ifBlank { "utf-8" } ?: "utf-8"
                    val rawStream = try { conn.inputStream } catch (_: Exception) { conn.errorStream } ?: return null
                    val respHeaders = mutableMapOf<String, String>()
                    try {
                        conn.headerFields?.forEach { (k, v) ->
                            if (k != null && v != null && v.isNotEmpty() && !k.equals("Transfer-Encoding", ignoreCase = true)) {
                                respHeaders[k] = v.first()
                            }
                        }
                    } catch (_: Exception) {}
                    // Small bodies: buffer to provide explicit Content-Length (players/downloaders
                    // misbehave on endless streams); large/unknown bodies stream through untouched.
                    val stream: java.io.InputStream = try {
                        val declared = try { conn.contentLengthLong } catch (_: Exception) { -1L }
                        if (declared in 1..262144) {
                            val bytes = rawStream.readBytes()
                            respHeaders["Content-Length"] = bytes.size.toString()
                            try { rawStream.close() } catch (_: Exception) {}
                            java.io.ByteArrayInputStream(bytes)
                        } else {
                            rawStream
                        }
                    } catch (_: Exception) { rawStream }
                    if (syncFinalUrl && urlStr != request.url.toString()) {
                        try { onMainFrameFinalUrl?.invoke(urlStr) } catch (_: Exception) {}
                    }
                    val reason = try { conn.responseMessage } catch (_: Exception) { null } ?: "OK"
                    return WebResourceResponse(mime, charset, code, reason, respHeaders, stream)
                } catch (e: Exception) {
                    try { conn.disconnect() } catch (_: Exception) {}
                    return null
                }
            }
            return null
        }

        private fun view_ua(request: WebResourceRequest): String {
            return try {
                request.requestHeaders["User-Agent"] ?: request.requestHeaders["user-agent"]
                ?: System.getProperty("http.agent").orEmpty()
            } catch (_: Exception) {
                ""
            }
        }

        @Deprecated("Deprecated in Java")
        override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
            if (url != null) {
                val uri = Uri.parse(url)
                if (isWebRtcStunTurnTraffic(uri, isMainFrame = false)) {
                    Log.w(TAG, "Shield intercepted and blocked WebRTC STUN/TURN traffic: $url")
                    return createBlockedWebResourceResponse()
                }
                if (isSurveillanceTraffic(uri, isMainFrame = false)) {
                    try { onSurveillanceBlocked?.invoke((uri.host ?: url).lowercase()) } catch (_: Exception) {}
                    return createBlockedWebResourceResponse()
                }
            }
            return super.shouldInterceptRequest(view, url)
        }

        /**
         * Strict intelligent surveillance blocking: session-replay SDKs record every synthetic
         * mouse move and keystroke — pure surveillance that degrades silently when absent
         * (unlike fraud SDKs whose tokens pages require, so those are NEVER blocked).
         * Active only when the coordinator sees risk OR the host already probed fingerprints.
         */
        private fun isSurveillanceTraffic(uri: Uri, isMainFrame: Boolean): Boolean {
            if (isMainFrame) return false
            val host = (uri.host ?: "").lowercase()
            if (host.isBlank()) return false
            var vendor: String? = null
            for (v in SURVEILLANCE_VENDORS) {
                if (host == v || host.endsWith(".$v")) { vendor = v; break }
            }
            if (vendor == null) return false
            return try {
                trackerBlockProvider() && (strictModeProvider() || isHostileHost(host))
            } catch (_: Exception) { false }
        }

        override fun onReceivedHttpAuthRequest(
            view: WebView?,
            handler: HttpAuthHandler?,
            host: String?,
            realm: String?
        ) {
            val u = proxyUserProvider()
            val p = proxyPassProvider()
            if (u.isNotBlank() && p.isNotBlank()) {
                handler?.proceed(u, p)
            } else {
                super.onReceivedHttpAuthRequest(view, handler, host, realm)
            }
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            onPageStartedCallback?.invoke(view, url, favicon)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onPageFinishedCallback?.invoke(view, url)
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            return handleUrlNavigation(view, uri, request.isForMainFrame)
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val uri = try { Uri.parse(url) } catch (_: Exception) { return false }
            return handleUrlNavigation(view, uri, isMainFrame = true)
        }

        protected fun handleUrlNavigation(view: WebView?, uri: Uri, isMainFrame: Boolean): Boolean {
            val rawUrl = uri.toString()
            val scheme = uri.scheme?.lowercase() ?: ""

            if (isWebRtcStunTurnTraffic(uri, isMainFrame)) {
                return true
            }

            if (scheme == "http" || scheme == "https") {
                return false
            }

            val resolvedUrl = resolveSpecialSchemeUrl(rawUrl)
            if (!resolvedUrl.isNullOrBlank()) {
                if (isMainFrame) {
                    val headers = mapOf("Referer" to (view?.url?.takeIf { it.startsWith("http") } ?: "https://www.google.com/"))
                    view?.post { view.loadUrl(resolvedUrl, headers) }
                }
                return true
            }

            return try {
                if (isMainFrame && (scheme == "tel" || scheme == "mailto" || scheme == "sms")) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                    view?.context?.startActivity(intent)
                    true
                } else {
                    true
                }
            } catch (_: Exception) {
                true
            }
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            onErrorCallback?.invoke(view, request, error)
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: android.webkit.SslErrorHandler?,
            error: android.net.http.SslError?
        ) {
            Log.w(TAG, "SSL warning ignored for offer tracking redirect: ${error?.primaryError} on ${error?.url}")
            // Proceed so affiliate tracking networks and intermediate certificates do not fail or show blank pages
            handler?.proceed()
        }

        override fun onRenderProcessGone(
            view: WebView?,
            detail: RenderProcessGoneDetail?
        ): Boolean {
            val crashed = detail?.didCrash() == true
            Log.w(TAG, "WebView render process exited (didCrash=$crashed). Recreating WebView.")
            try { onRenderProcessGoneCallback?.invoke(crashed) } catch (_: Exception) {}
            return true
        }

        companion object {
            private const val TAG = "AntiWebRtcClient"

            /**
             * Resolves special redirect schemes (such as intent://, android-app://, and googlechrome:// used by Monetag / omg10.com / ad networks
             * to break out of WebViews, or market:// links) into standard loadable HTTP/HTTPS URLs.
             */
            fun resolveSpecialSchemeUrl(rawUrl: String?): String? {
                val url = rawUrl?.trim().orEmpty()
                if (url.isBlank()) return null
                if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                    return url
                }
                if (url.startsWith("//")) {
                    return "https:$url"
                }

                // Helper to decode nested url= / fallback_url= parameters (supports single or double URL-encoding)
                fun extractEmbeddedHttpUrl(source: String): String? {
                    val patterns = listOf(
                        Regex("""(?:browser_fallback_url|S\.browser_fallback_url|fallback_url|redirect_url)=([^;#&]+)""", RegexOption.IGNORE_CASE),
                        Regex("""[?&](?:url|link|target|redirect|u|dest|r|goto)=([^;#&]+)""", RegexOption.IGNORE_CASE)
                    )
                    for (regex in patterns) {
                        val match = regex.find(source) ?: continue
                        var candidate = Uri.decode(match.groupValues[1]).trim()
                        if (candidate.startsWith("http%3A", ignoreCase = true) || candidate.startsWith("https%3A", ignoreCase = true)) {
                            candidate = Uri.decode(candidate).trim()
                        }
                        if (candidate.startsWith("http://", ignoreCase = true) || candidate.startsWith("https://", ignoreCase = true)) {
                            return candidate
                        }
                    }
                    return null
                }

                // Handle android-app://com.android.chrome/https/domain.com/path or android-app://.../http/domain.com/path
                if (url.startsWith("android-app://", ignoreCase = true)) {
                    extractEmbeddedHttpUrl(url)?.let { return it }
                    val httpsIdx = url.indexOf("/https/", ignoreCase = true)
                    if (httpsIdx != -1) {
                        val rest = url.substring(httpsIdx + 7).substringBefore("#Intent").trim()
                        if (rest.isNotBlank()) return "https://$rest"
                    }
                    val httpIdx = url.indexOf("/http/", ignoreCase = true)
                    if (httpIdx != -1) {
                        val rest = url.substring(httpIdx + 6).substringBefore("#Intent").trim()
                        if (rest.isNotBlank()) return "http://$rest"
                    }
                }

                // Handle googlechrome://navigate?url=... or googlechromes://...
                if (url.startsWith("googlechrome://", ignoreCase = true) || url.startsWith("googlechromes://", ignoreCase = true)) {
                    extractEmbeddedHttpUrl(url)?.let { return it }
                    val stripped = url.replaceFirst(Regex("""^googlechromes?://(navigate\?url=)?""", RegexOption.IGNORE_CASE), "")
                    val decoded = Uri.decode(stripped).trim()
                    if (decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)) {
                        return decoded
                    }
                    if (decoded.substringBefore("/").substringBefore("?").contains(".")) {
                        return "https://$decoded"
                    }
                }

                if (url.startsWith("intent://", ignoreCase = true) || url.startsWith("intent:", ignoreCase = true)) {
                    // 1. Check embedded browser_fallback_url or navigate?url= first
                    extractEmbeddedHttpUrl(url)?.let { return it }

                    // Check if it's a Google Play Store / market intent (e.g. intent://details?id=...#Intent;scheme=market;...)
                    if (url.contains("scheme=market", ignoreCase = true) ||
                        url.contains("com.android.vending", ignoreCase = true) ||
                        url.startsWith("intent://details?", ignoreCase = true)
                    ) {
                        val idMatch = Regex("""[?&]id=([^;#&]+)""", RegexOption.IGNORE_CASE).find(url)
                        if (idMatch != null) {
                            return "https://play.google.com/store/apps/details?id=${idMatch.groupValues[1]}"
                        }
                    }

                    try {
                        val intent = android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
                        val fallback = intent.getStringExtra("browser_fallback_url")
                            ?: intent.getStringExtra("S.browser_fallback_url")
                            ?: intent.getStringExtra("fallback_url")
                        if (!fallback.isNullOrBlank()) {
                            val cleanFallback = Uri.decode(fallback).trim()
                            if (cleanFallback.startsWith("http://", ignoreCase = true) || cleanFallback.startsWith("https://", ignoreCase = true)) {
                                return cleanFallback
                            }
                        }
                        val dataUri = intent.data
                        if (dataUri != null) {
                            val ds = (dataUri.scheme ?: "").lowercase()
                            if (ds == "http" || ds == "https") {
                                return dataUri.toString()
                            }
                            if (ds == "market" || dataUri.host.equals("details", ignoreCase = true)) {
                                val q = dataUri.encodedQuery
                                return if (!q.isNullOrBlank()) "https://play.google.com/store/apps/details?$q" else "https://play.google.com/store"
                            }
                            for (paramName in listOf("url", "link", "target", "redirect", "fallback_url")) {
                                val paramVal = dataUri.getQueryParameter(paramName)
                                if (!paramVal.isNullOrBlank() && (paramVal.startsWith("http://", true) || paramVal.startsWith("https://", true))) {
                                    return paramVal
                                }
                            }
                            val hostPart = dataUri.host.orEmpty()
                            if (hostPart.isNotBlank() && hostPart.contains(".") && !hostPart.equals("navigate", ignoreCase = true)) {
                                val targetScheme = if (ds == "http") "http" else "https"
                                val path = dataUri.encodedPath ?: ""
                                val query = dataUri.encodedQuery?.let { "?$it" } ?: ""
                                return "$targetScheme://${dataUri.authority ?: hostPart}$path$query"
                            }
                        }
                    } catch (_: Exception) {}

                    // 2. Manual fallback for intent://domain.com/path#Intent;scheme=https;...;end
                    try {
                        val withoutPrefix = url
                            .replaceFirst(Regex("""^intent:(//)?""", RegexOption.IGNORE_CASE), "")
                        val beforeHash = withoutPrefix.substringBefore("#Intent").trim()
                        if (beforeHash.startsWith("http://", ignoreCase = true) || beforeHash.startsWith("https://", ignoreCase = true)) {
                            return beforeHash
                        }
                        val hostCandidate = beforeHash.substringBefore("/").substringBefore("?")
                        if (hostCandidate.isNotBlank() && hostCandidate.contains(".") && !hostCandidate.equals("navigate", ignoreCase = true)) {
                            val isHttp = url.contains("scheme=http;", ignoreCase = true)
                            return (if (isHttp) "http://" else "https://") + beforeHash
                        }
                    } catch (_: Exception) {}
                }
                if (url.startsWith("market://", ignoreCase = true)) {
                    val query = url.substringAfter("?", "")
                    return if (query.isNotBlank()) {
                        "https://play.google.com/store/apps/details?$query"
                    } else {
                        "https://play.google.com/store"
                    }
                }
                return null
            }

            /**
             * Inspects URI scheme, host, port, and path for any WebRTC STUN/TURN signatures
             * or anti-bot local network/loopback port-scanning probes.
             */
            fun isWebRtcStunTurnTraffic(uri: Uri, isMainFrame: Boolean = false): Boolean {
                val scheme = (uri.scheme ?: "").lowercase()
                val host = (uri.host ?: "").lowercase().removeSurrounding("[", "]")
                val path = (uri.path ?: "").lowercase()
                val fullUrl = uri.toString().lowercase()
                val port = uri.port

                // 1. STUN/TURN explicit protocol schemes
                if (scheme == "stun" || scheme == "stuns" || scheme == "turn" || scheme == "turns") {
                    return true
                }

                // Never block standard HTTP/HTTPS main-frame navigations or redirects
                if (isMainFrame && (scheme == "http" || scheme == "https")) {
                    return false
                }

                // 2. Standard STUN / TURN port numbers
                if (port in setOf(3478, 5349, 19302, 19305, 19307, 19308)) {
                    return true
                }

                // 3. Block subresource local loopback & RFC1918 LAN port-scanning probes (used by ThreatMetrix / Seon / anti-bot SDKs)
                if (!isMainFrame && host.isNotBlank()) {
                    if (host == "localhost" || host == "0.0.0.0" || host == "::1" ||
                        host.startsWith("127.") || host.endsWith(".local") ||
                        host.startsWith("10.") || host.startsWith("192.168.") || host.startsWith("169.254.") ||
                        Regex("""^172\.(1[6-9]|2\d|3[0-1])\.""").containsMatchIn(host)
                    ) {
                        return true
                    }
                }

                // 4. Known public & private STUN / TURN servers and relay endpoints
                if (host.startsWith("stun.") || host.startsWith("turn.") ||
                    host.contains(".stun.") || host.contains(".turn.") ||
                    host.contains("stunprotocol.org") ||
                    host.contains("stunserver.org") ||
                    host.contains("numb.viagenie.ca") ||
                    host.contains("stun.cloudflare.com") ||
                    host.contains("stun.l.google.com") ||
                    host.contains("stun1.l.google.com") ||
                    host.contains("stun2.l.google.com") ||
                    host.contains("stun3.l.google.com") ||
                    host.contains("stun4.l.google.com") ||
                    host.contains("stun.services.mozilla.com") ||
                    host.contains("global.stun.twilio.com") ||
                    host.contains("stun.ekiga.net") ||
                    host.contains("stun.ideasip.com") ||
                    host.contains("stun.voiparound.com") ||
                    host.contains("stun.voipbuster.com") ||
                    host.contains("stun.voipstunt.com") ||
                    host.contains("stun.voxgratia.org") ||
                    host.contains("openrelay.metered.ca") ||
                    host.contains("stun.metered.ca") ||
                    host.contains("turn.metered.ca") ||
                    host.contains("xirsys.com") ||
                    host.contains("bistri.com") ||
                    host.contains("anyfirewall.com")
                ) {
                    return true
                }

                // 5. Subresources attempting explicit WebRTC ICE candidate lookups or STUN/TURN signaling
                if (!isMainFrame) {
                    val isCloudflareTurnstile = fullUrl.contains("challenges.cloudflare.com") ||
                            fullUrl.contains("turnstile") ||
                            path.contains("turnstile")

                    if (!isCloudflareTurnstile) {
                        if (path.contains("turn.php") || path.contains("stun.php") ||
                            path.contains("webrtc-ip") || path.contains("ice-candidate") ||
                            fullUrl.contains("stun:") || fullUrl.contains("turn:")
                        ) {
                            return true
                        }
                    }
                }

                return false
            }

            /**
             * Session-replay & behavior-surveillance vendors: blocked strictly because they
             * exfiltrate full interaction recordings. Fraud/attribution SDKs are DELIBERATELY
             * absent (blocking them breaks submits/conversions).
             */
            internal val SURVEILLANCE_VENDORS = setOf(
                "hotjar.com", "static.hotjar.com", "fullstory.com", "mouseflow.com",
                "crazyegg.com", "luckyorange.com", "inspectlet.com", "contentsquare.net",
                "quantummetric.com", "smartlook.com", "sessioncam.com", "userreplay.com",
                "logrocket.com", "glassboxcdn.com", "decibelinsight.net", "clicktale.net"
            )

            /**
             * Returns a silent synthetic response to abort the WebRTC/port-scan socket cleanly
             * without exposing custom headers to page scripts.
             */
            fun createBlockedWebResourceResponse(): WebResourceResponse {
                val emptyStream = ByteArrayInputStream(ByteArray(0))
                val headers = mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-store"
                )
                return WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    204,
                    "No Content",
                    headers,
                    emptyStream
                )
            }
        }
    }

    companion object {
        private const val MAIN_TAG = "MainActivity"

        /**
         * Clears all WebView cache, cookies, local storage, WebSQL/IndexedDB databases,
         * and cached assets on demand to ensure that previous real-IP sessions do not
         * leak information through cached assets, persistent cookies, or local storage.
         */
        fun clearWebViewData(
            context: Context,
            webView: WebView? = null,
            onComplete: (() -> Unit)? = null
        ) {
            try {
                // 1. Clear In-Memory and Disk Cache for WebView
                webView?.let { wv ->
                    wv.clearCache(true)
                    wv.clearFormData()
                    wv.clearHistory()
                    wv.clearSslPreferences()

                    // Clear DOM Storage (localStorage, sessionStorage), Cache API, and Service Workers
                    wv.evaluateJavascript(
                        """
                        (function() {
                            try {
                                try { window.name = ''; } catch(e0) {}
                                if (window.localStorage) window.localStorage.clear();
                                if (window.sessionStorage) window.sessionStorage.clear();
                                if ('caches' in window) {
                                    caches.keys().then(function(names) {
                                        for (var i = 0; i < names.length; i++) caches.delete(names[i]);
                                    });
                                }
                                if (navigator.serviceWorker) {
                                    navigator.serviceWorker.getRegistrations().then(function(regs) {
                                        for (var i = 0; i < regs.length; i++) regs[i].unregister();
                                    });
                                }
                                if (window.indexedDB && window.indexedDB.databases) {
                                    window.indexedDB.databases().then(function(dbs) {
                                        for (var i = 0; i < dbs.length; i++) {
                                            if (dbs[i].name) window.indexedDB.deleteDatabase(dbs[i].name);
                                        }
                                    });
                                }
                            } catch(e) {}
                        })();
                        """.trimIndent(),
                        null
                    )
                }

                // 2. Clear all Cookies (Session + Persistent)
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies { _ ->
                    cookieManager.flush()
                }
                cookieManager.removeSessionCookies(null)

                // 3. Clear HTML5 Web Storage (localStorage, WebSQL, IndexedDB)
                WebStorage.getInstance().deleteAllData()

                // 4. Clear Geolocation permissions
                GeolocationPermissions.getInstance().clearAll()

                // 5. Clear saved HTTP Auth credentials and form data in WebViewDatabase
                try {
                    val webViewDatabase = WebViewDatabase.getInstance(context)
                    webViewDatabase.clearHttpAuthUsernamePassword()
                    @Suppress("DEPRECATION")
                    webViewDatabase.clearFormData()
                } catch (e: Exception) {
                    Log.w(MAIN_TAG, "Error clearing WebViewDatabase: ${e.message}")
                }

                // 6. Flush CookieManager to persist clean state without deleting active Chromium internal cache descriptors
                try {
                    CookieManager.getInstance().flush()
                } catch (e: Exception) {
                    Log.w(MAIN_TAG, "Error flushing cookies: ${e.message}")
                }

                Log.i(MAIN_TAG, "WebView cache, cookies, and local storage successfully cleared on demand.")
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(MAIN_TAG, "Failed to clear WebView data: ${e.message}", e)
                onComplete?.invoke()
            }
        }
    }
}

@Composable
fun CpaAutomatorApp(vm: AppViewModel = viewModel()) {
    val currentTab by vm.currentTab.collectAsState()
    val automationState by vm.automationState.collectAsState()
    val extractedInfo by vm.extractedInfo.collectAsState()
    val identity by vm.identity.collectAsState()
    val settings by vm.settings.collectAsState()
    val logs by vm.logs.collectAsState()
    val browserCommand by vm.browserCommand.collectAsState()

    val tasks by vm.tasks.collectAsState()
    val proxies by vm.proxies.collectAsState()
    val emails by vm.emails.collectAsState()
    val scripts by vm.scripts.collectAsState()
    val stats by vm.stats.collectAsState()
    val offerClickItems by vm.offerClickItems.collectAsState()
    val enabledClickItems by vm.enabledClickItems.collectAsState()
    val workTemplates by vm.workTemplates.collectAsState()
    val aiConsole by vm.aiConsole.collectAsState()
    val agentState by vm.agentState.collectAsState()
    val agentHistory by vm.agentHistory.collectAsState()

    var browserInitialized by remember { mutableStateOf(true) }
    val isBrowserTab = (currentTab == ScreenTab.BROWSER)
    if (isBrowserTab || automationState.isRunning) {
        browserInitialized = true
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                TopConsoleBar(
                    automationState = automationState,
                    disableWebRtc = settings.disableWebRtc || settings.webrtcMode == "disabled",
                    onToggleAutomation = { vm.toggleAutomation() },
                    onRefreshGeo = { vm.refreshGeoInfo() },
                    onToggleDisableWebRtc = { vm.toggleDisableWebRtc(it) }
                )
                if (!isBrowserTab && (automationState.isRunning || automationState.phase != "idle")) {
                    PhaseIndicatorStrip(automationState = automationState)
                }
            }
        },
        bottomBar = {
            MainNavigation(
                selectedTab = currentTab,
                onTabSelected = { vm.selectTab(it) }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CpaBg)
                .padding(innerPadding)
        ) {
            // Persistent Browser Screen Container: Preserves WebView state, continuous automation, and prevents page reload
            if (browserInitialized) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (isBrowserTab) 1f else 0f
                        }
                        .then(if (isBrowserTab) Modifier else Modifier.zIndex(-1f))
                ) {
                    BrowserScreen(
                        settings = settings,
                        automationState = automationState,
                        extractedInfo = extractedInfo,
                        identity = identity,
                        scripts = scripts,
                        browserCommand = browserCommand,
                        clickTexts = enabledClickItems.map { it.text },
                        activeClickText = automationState.activeClickText,
                        onClearBrowserCommand = { vm.clearBrowserCommand() },
                        onNotifyCompletion = { kw, url -> vm.notifyTaskCompleted(kw, url) },
                        onOfferClicked = { txt, url -> vm.onOfferClicked(txt, url) },
                        onOfferClickedInNewTab = { txt, url, tabId -> vm.onOfferClickedInNewTab(txt, url, tabId) },
                        onNewTabInteractionCompleted = { tabId, url, details -> vm.onNewTabInteractionCompleted(tabId, url, details) },
                        onLockerDetectedWithCount = { url, id, isTriggered, count -> vm.onLockerDetected(url, id, isTriggered, count) },
                        onPageAnalyzed = { reportJson -> vm.onPageAnalyzed(reportJson) },
                        onPageActionMapGenerated = { vm.onPageActionMapGenerated(it) },
                        onPageActionStepExecuted = { idx, name, status -> vm.onPageActionStepExecuted(idx, name, status) },
                        onMidPageInfoExtracted = { k, v -> vm.onMidPageInfoExtracted(k, v) },
                        onTemplateGenerated = { vm.onTemplateGenerated(it) },
                        onRequestAutoTemplate = { vm.requestGenerateTemplateFromPage() },
                        onActiveTabChanged = { tabId -> vm.setActiveTabId(tabId) },
                        onUpdateWebRtcMode = { vm.setWebRtcMode(it) },
                        onToggleDisableWebRtc = { vm.toggleDisableWebRtc(it) },
                        onGoToTasks = { vm.selectTab(ScreenTab.TASKS) },
                        onLockerOffersExtracted = { json, onDone -> vm.onLockerOffersExtracted(json, onDone) },
                        onGoToOfferClick = { vm.selectTab(ScreenTab.CLICK) },
                        onHumanActionStatus = { vm.onHumanActionStatus(it) },
                        onShieldTelemetry = { vm.onShieldTelemetry(it) },
                        onShieldSelfTestResult = { vm.onShieldSelfTestResult(it) },
                        onIsHostileHost = { vm.isHostileHost(it) },
                        onSurveillanceBlocked = { vm.onSurveillanceBlocked(it) },
                        onWebViewRendererGone = { tabId, crashed, url, gaveUp -> vm.onWebViewRendererGone(tabId, crashed, url, gaveUp) },
                        onConsoleError = { vm.addLog("warning", it) },
                        onAiFixResult = { fixId, json -> vm.onAiFixResult(fixId, json) }
                    )
                }
            }

            if (!isBrowserTab) {
                when (currentTab) {
                    ScreenTab.TASKS -> TaskScreen(
                        tasks = tasks,
                        automationState = automationState,
                        workTemplates = workTemplates,
                        onSaveTask = { vm.saveTask(it) },
                        onDeleteTask = { vm.deleteTask(it) },
                        onToggleTask = { vm.toggleTaskEnabled(it) },
                        onRunTask = { vm.runTaskNow(it) },
                        onOptimizeTask = { task ->
                            val plan = vm.extractPlanForUrl(task.url)
                            val optimized = task.copy(
                                name = if (task.name.isBlank() || task.name.startsWith("Task ")) plan.detectedName else task.name,
                                categories = plan.categories.joinToString(", "),
                                mode = plan.recommendedMode,
                                browserDuration = plan.recommendedDuration,
                                completionKeywords = plan.recommendedKeywords,
                                referer = plan.recommendedReferer
                            )
                            vm.saveTask(optimized)
                        },
                        onQuickAddAndRunTask = { url -> vm.createAndRunExtractedTask(url) }
                    )
                    ScreenTab.BROWSER -> { /* Handled in persistent container above */ }
                    ScreenTab.TEMPLATES -> com.example.ui.screens.TemplatesScreen(
                        templates = workTemplates,
                        automationState = automationState,
                        onSaveTemplate = { vm.saveWorkTemplate(it) },
                        onDeleteTemplate = { vm.deleteWorkTemplate(it) },
                        onRequestAutoTemplate = { vm.requestGenerateTemplateFromPage() },
                        onExecuteTemplate = { tmpl ->
                            vm.executeWorkTemplateInBrowser(tmpl)
                        }
                    )
                    ScreenTab.CLICK -> ClickScreen(
                        clickItems = offerClickItems,
                        automationState = automationState,
                        settings = settings,
                        onUpdateSettings = { vm.saveSettings(it) },
                        onAddItem = { text, tag -> vm.addOfferClickItem(text, tag) },
                        onUpdateItem = { vm.updateOfferClickItem(it) },
                        onToggleItem = { id, enabled -> vm.toggleOfferClickItem(id, enabled) },
                        onDeleteItem = { vm.deleteOfferClickItem(it) },
                        onMoveItem = { id, dir -> vm.moveOfferClickItem(id, dir) },
                        onResetDefaults = { vm.resetDefaultOfferClickItems() },
                        onGoToTasks = { vm.selectTab(ScreenTab.TASKS) },
                        onScanLockerFromBrowser = { vm.triggerBrowserLockerScanAndExtract(switchToBrowser = false) },
                        onDeduplicateAndSmartSort = { onDone -> vm.deduplicateAndSmartSortOfferClickItems(onDone) },
                        onSetAllEnabled = { enabled -> vm.setAllOfferClickItemsEnabled(enabled) }
                    )
                    ScreenTab.INFO -> InfoScreen(
                    extractedInfo = extractedInfo,
                    identity = identity,
                    automationState = automationState,
                    onRegenerateIdentity = { vm.regenerateIdentity() },
                    onRefreshGeo = { vm.refreshGeoInfo() }
                )
                ScreenTab.SHIELD -> com.example.ui.screens.ShieldScreen(
                    settings = settings,
                    automationState = automationState,
                    onToggleTrackerShield = { vm.setTrackerShieldEnabled(it) },
                    onSetTrackerMode = { vm.setTrackerMode(it) },
                    onRunSelfTest = { vm.runShieldSelfTest() },
                    onRotateProxy = { vm.rotateProxyNow() },
                    onTriggerEscape = { vm.triggerEscapeNow() },
                    onNudgeHumanization = { vm.nudgeHumanization(it) },
                    onSetKillSwitch = { key, enabled -> vm.setKillSwitch(key, enabled) },
                    onBatteryExemption = { vm.openBatteryExemptionSettings() },
                    statsProvider = { vm.shieldStatsSnapshot() }
                )
                ScreenTab.AI -> com.example.ui.screens.AiScreen(
                    settings = settings,
                    aiConsole = aiConsole,
                    totalScore = vm.aiTotalScore(),
                    ledger = vm.aiLedgerSnapshot(),
                    tasks = tasks,
                    agentState = agentState,
                    agentHistory = agentHistory,
                    onRunFixRound = { vm.runAiFixRound(manual = true) },
                    onToggleAutoFix = { vm.setAiAutoFix(it) },
                    onClearConsole = { vm.clearAiConsole() },
                    onStartMission = { vm.startAgentMission(it) },
                    onStopMission = { vm.stopAgentMission() }
                )
                ScreenTab.PROXIES -> ProxyPoolScreen(
                    proxies = proxies,
                    currentSettings = settings,
                    onFetchFromUrl = { url, protocol, callback ->
                        vm.fetchProxiesFromUrl(url, protocol, callback)
                    },
                    onImportBulk = { raw, protocol ->
                        vm.importProxiesBulk(raw, protocol)
                    },
                    onAddSingleProxy = { vm.addSingleProxy(it) },
                    onSetActiveProxy = { vm.setActiveProxy(it) },
                    onTestProxy = { proxy, callback ->
                        vm.testProxy(proxy, callback)
                    },
                    onDeleteProxy = { vm.deleteProxy(it) },
                    onClearAll = { vm.clearAllProxies() },
                    onToggleAutoRotate = { vm.updateSettings(settings.copy(proxyAutoRotate = it)) },
                    onToggleProxyGlobalEnabled = { vm.toggleProxyGlobalEnabled(it) },
                    onTestAllProxies = { onProg, onComp -> vm.testAllProxies(onProg, onComp) },
                    onDeleteFailed = { vm.deleteFailedProxies() },
                    onResetFailed = { vm.resetFailedProxies() },
                    onAutoSelectFastest = { vm.autoSelectFastestProxy() },
                    onExportWorking = { vm.getWorkingProxiesFormatted() }
                )
                ScreenTab.SCRIPTS -> ScriptsScreen(
                    scripts = scripts,
                    onSaveScript = { vm.saveScript(it) },
                    onToggleScript = { vm.toggleScript(it) },
                    onDeleteScript = { vm.deleteScript(it) }
                )
                ScreenTab.EMAILS -> EmailPoolScreen(
                    emails = emails,
                    onAddEmail = { vm.addEmail(it) },
                    onImportBulk = { vm.importEmailsBulk(it) },
                    onGenerateTestEmails = { vm.generateTestEmails() },
                    onDeleteEmail = { vm.deleteEmail(it) },
                    onClearAll = { vm.clearAllEmails() }
                )
                ScreenTab.STATS -> StatsScreen(
                    stats = stats,
                    tasks = tasks,
                    automationState = automationState,
                    onClearStats = { vm.clearStats() }
                )
                ScreenTab.SETTINGS -> SettingsScreen(
                    currentSettings = settings,
                    onSaveSettings = { vm.updateSettings(it) },
                    onTestCpa = { callback -> vm.testCpaConnection(callback) }
                )
                ScreenTab.LOGS -> LogsScreen(
                    logs = logs,
                    onClearLogs = { vm.clearLogs() },
                    onExportLogs = { vm.exportLogsPath() }
                )
            }
        }
    }
}
}
