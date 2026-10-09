package com.example.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.net.Authenticator
import java.util.concurrent.Executor

object WebProxyManager {

    private const val TAG = "WebProxyManager"

    @Volatile
    var isProxyActive: Boolean = false
        private set

    @Volatile
    var activeProxySummary: String = "Direct (No Proxy)"
        private set

    @Volatile
    private var lastAppliedHost: String = ""

    @Volatile
    private var lastAppliedPort: Int = 0

    @Volatile
    private var lastAppliedType: String = ""

    @Volatile
    private var lastAppliedUser: String = ""

    @Volatile
    private var lastAppliedPass: String = ""

    private fun clearJvmProxyProperties() {
        try {
            System.clearProperty("socksProxyHost")
            System.clearProperty("socksProxyPort")
            System.clearProperty("socksProxyVersion")
            System.clearProperty("socksNonProxyHosts")
            System.clearProperty("java.net.socks.username")
            System.clearProperty("java.net.socks.password")
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            System.clearProperty("http.proxyUser")
            System.clearProperty("http.proxyPassword")
            System.clearProperty("https.proxyHost")
            System.clearProperty("https.proxyPort")
            System.clearProperty("https.proxyUser")
            System.clearProperty("https.proxyPassword")
            Authenticator.setDefault(null)
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing JVM proxy properties: ${e.message}")
        }
    }

    /**
     * Applies proxy settings to the Android System WebView globally using AndroidX WebKit ProxyController
     * paired with our Universal Smart Local Proxy Bridge (LocalSocks5HttpBridge).
     *
     * Routing all proxy protocols (SOCKS5, SOCKS4, HTTP, HTTPS, with or without Auth) through
     * the local bridge on 127.0.0.1 guarantees:
     * - Full RFC 1928/1929 SOCKS5 and HTTP Basic Proxy-Authorization support in WebView
     * - Automatic protocol recovery if user selected SOCKS5 for an HTTP/SOCKS4 proxy
     * - Zero JVM System.setProperty("socksProxyHost") socket poisoning
     * - Seamless fail-safe tunnel fallback so websites never fail with ERR_TUNNEL_CONNECTION_FAILED
     */
    fun applyProxy(
        context: Context,
        enabled: Boolean,
        host: String?,
        port: Int?,
        type: String?,
        username: String? = null,
        password: String? = null,
        onApplied: ((Boolean, String) -> Unit)? = null
    ) {
        // Always keep JVM global properties clean so background test sockets are never hijacked
        clearJvmProxyProperties()

        if (!enabled) {
            clearProxy(context, onApplied)
            return
        }

        val cleanHost = host?.trim().orEmpty()
        val cleanType = type?.trim()?.lowercase()?.ifBlank { "socks5" } ?: "socks5"
        val cleanUser = username?.trim().orEmpty()
        val cleanPass = password?.trim().orEmpty()

        if (cleanHost.isBlank() || port == null || port <= 0 || cleanType == "none" || cleanType == "direct") {
            clearProxy(context, onApplied)
            return
        }

        // Start or update the Universal Smart Local Proxy Bridge on 127.0.0.1
        val localBridgePort = LocalSocks5HttpBridge.start(
            host = cleanHost,
            port = port,
            user = cleanUser,
            pass = cleanPass,
            type = cleanType
        )

        // If the exact same proxy and bridge port are already active in ProxyController, avoid resetting Chromium sockets
        if (isProxyActive &&
            localBridgePort > 0 &&
            lastAppliedHost == cleanHost &&
            lastAppliedPort == port &&
            lastAppliedType == cleanType &&
            lastAppliedUser == cleanUser &&
            lastAppliedPass == cleanPass
        ) {
            onApplied?.invoke(true, "Proxy active: $cleanHost:$port (${cleanType.uppercase()})")
            return
        }

        lastAppliedHost = cleanHost
        lastAppliedPort = port
        lastAppliedType = cleanType
        lastAppliedUser = cleanUser
        lastAppliedPass = cleanPass

        activeProxySummary = if (localBridgePort > 0) {
            "${cleanType.uppercase()}://$cleanHost:$port (Smart Bridge :$localBridgePort)"
        } else {
            "${cleanType.uppercase()}://$cleanHost:$port"
        }
        isProxyActive = true

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            val builder = ProxyConfig.Builder()
            if (localBridgePort > 0) {
                builder.addProxyRule("http://127.0.0.1:$localBridgePort")
            } else {
                when (cleanType) {
                    "socks5", "socks" -> builder.addProxyRule("socks5://$cleanHost:$port")
                    "socks4" -> builder.addProxyRule("socks4://$cleanHost:$port")
                    else -> builder.addProxyRule("http://$cleanHost:$port")
                }
            }

            try {
                val proxyConfig = builder.build()
                val executor = Executor { command ->
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        command.run()
                    } else {
                        Handler(Looper.getMainLooper()).post(command)
                    }
                }
                ProxyController.getInstance().setProxyOverride(
                    proxyConfig,
                    executor,
                    Runnable {
                        Log.i(TAG, "WebView ProxyOverride active -> $cleanHost:$port [$cleanType] (bridge=$localBridgePort)")
                        onApplied?.invoke(true, "Proxy active: $cleanHost:$port (${cleanType.uppercase()})")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setProxyOverride: ${e.message}", e)
                onApplied?.invoke(false, "Failed to apply WebView proxy: ${e.message}")
            }
        } else {
            Log.w(TAG, "PROXY_OVERRIDE is not supported on this WebView version")
            onApplied?.invoke(false, "PROXY_OVERRIDE not supported by current Android WebView")
        }
    }

    /**
     * Overload for applyProxy defaulting enabled to true.
     */
    fun applyProxy(
        context: Context,
        host: String?,
        port: Int?,
        type: String?,
        username: String? = null,
        password: String? = null,
        onApplied: ((Boolean, String) -> Unit)? = null
    ) {
        applyProxy(context, true, host, port, type, username, password, onApplied)
    }

    /**
     * Clears all proxy configurations and returns WebView to direct internet connection.
     */
    fun clearProxy(context: Context, onCleared: ((Boolean, String) -> Unit)? = null) {
        val wasProxyActive = isProxyActive
        activeProxySummary = "Direct (No Proxy)"
        isProxyActive = false
        lastAppliedHost = ""
        lastAppliedPort = 0
        lastAppliedType = ""
        lastAppliedUser = ""
        lastAppliedPass = ""

        clearJvmProxyProperties()

        if (!wasProxyActive) {
            LocalSocks5HttpBridge.stop()
            onCleared?.invoke(true, "Direct connection active")
            return
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            val executor = Executor { command ->
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    command.run()
                } else {
                    Handler(Looper.getMainLooper()).post(command)
                }
            }
            try {
                ProxyController.getInstance().clearProxyOverride(
                    executor,
                    Runnable {
                        Log.i(TAG, "WebView ProxyOverride cleared, direct connection active.")
                        LocalSocks5HttpBridge.stop()
                        onCleared?.invoke(true, "Direct connection restored")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed in clearProxyOverride: ${e.message}", e)
                LocalSocks5HttpBridge.stop()
                onCleared?.invoke(true, "Direct connection restored")
            }
        } else {
            LocalSocks5HttpBridge.stop()
            onCleared?.invoke(true, "Direct connection restored")
        }
    }

    /**
     * Clears all WebView cache, cookies, local storage, databases, and session state on demand.
     */
    fun clearAllSessionData(
        context: Context,
        webView: android.webkit.WebView? = null,
        onComplete: (() -> Unit)? = null
    ) {
        try {
            webView?.let { wv ->
                wv.clearCache(true)
                wv.clearFormData()
                wv.clearHistory()
                wv.clearSslPreferences()
                wv.evaluateJavascript(
                    """
                    try {
                        if (window.localStorage) window.localStorage.clear();
                        if (window.sessionStorage) window.sessionStorage.clear();
                        if ('caches' in window) {
                            caches.keys().then(function(names) {
                                for (var i = 0; i < names.length; i++) caches.delete(names[i]);
                            });
                        }
                    } catch(e) {}
                    """.trimIndent(),
                    null
                )
            }

            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies {
                cookieManager.flush()
            }
            cookieManager.removeSessionCookies(null)
            android.webkit.WebStorage.getInstance().deleteAllData()
            android.webkit.GeolocationPermissions.getInstance().clearAll()

            try {
                val db = android.webkit.WebViewDatabase.getInstance(context)
                db.clearHttpAuthUsernamePassword()
                @Suppress("DEPRECATION")
                db.clearFormData()
            } catch (e: Exception) {
                Log.w(TAG, "Error clearing WebViewDatabase: ${e.message}")
            }

            onComplete?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Error in clearAllSessionData: ${e.message}", e)
            onComplete?.invoke()
        }
    }
}
