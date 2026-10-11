package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.MainActivity
import com.example.data.model.AppSettings
import com.example.data.model.AutomationState
import com.example.data.model.ExtractedInfo
import com.example.data.model.GeneratedIdentity
import com.example.data.model.ScriptItem
import com.example.service.AutomationScriptBuilder
import com.example.service.TaskCategoryPlanner
import com.example.ui.BrowserCommand
import com.example.ui.theme.CpaAccent
import com.example.ui.theme.CpaBg
import com.example.ui.theme.CpaBorder
import com.example.ui.theme.CpaCard
import com.example.ui.theme.CpaCardElevated
import com.example.ui.theme.CpaError
import com.example.ui.theme.CpaPrimary
import com.example.ui.theme.CpaPrimaryBorder
import com.example.ui.theme.CpaPrimaryDim
import com.example.ui.theme.CpaSuccess
import com.example.ui.theme.CpaText
import com.example.ui.theme.CpaTextDim
import com.example.ui.theme.CpaTextMuted
import com.example.ui.theme.CpaWarning
import android.os.Message
import com.example.util.WebProxyManager
import com.example.ui.components.CpaLockerDialog
import kotlinx.coroutines.delay
import java.util.UUID

class WebAppInterface(
    private val onCompleted: (String, String) -> Unit,
    private val onOfferClicked: ((String, String) -> Unit)? = null,
    private val onOfferClickedInNewTab: ((String, String) -> Unit)? = null,
    private val onNewTabInteractionCompleted: ((String, String, String) -> Unit)? = null,
    private val onPageAnalyzed: ((String) -> Unit)? = null,
    private val onPageActionMapGenerated: ((String) -> Unit)? = null,
    private val onPageActionStepExecuted: ((Int, String, String) -> Unit)? = null,
    private val onMidPageInfoExtracted: ((String, String) -> Unit)? = null,
    private val onTemplateGenerated: ((String) -> Unit)? = null,
    private val onLockerDetected: ((String, String, Boolean, Int) -> Unit)? = null,
    private val onLockerStatus: ((String) -> Unit)? = null,
    private val onLockerOffersExtracted: ((String) -> Unit)? = null,
    private val onHumanActionStatus: ((String) -> Unit)? = null,
    private val onShieldTelemetry: ((String) -> Unit)? = null,
    private val isLockerScanRequestedProvider: (() -> Boolean)? = null,
    private val isRunningProvider: (() -> Boolean)? = null,
    private val activeClickTextProvider: (() -> String?)? = null,
    private val clickTextsProvider: (() -> List<String>)? = null
) {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @JavascriptInterface
    fun isAutomationRunning(): Boolean {
        return try { isRunningProvider?.invoke() ?: false } catch (_: Exception) { false }
    }

    @JavascriptInterface
    fun isLockerScanRequested(): Boolean {
        return try { isLockerScanRequestedProvider?.invoke() ?: false } catch (_: Exception) { false }
    }

    @JavascriptInterface
    fun getActiveClickText(): String {
        return try { activeClickTextProvider?.invoke().orEmpty() } catch (_: Exception) { "" }
    }

    @JavascriptInterface
    fun getClickTextsJson(): String {
        return try {
            val arr = org.json.JSONArray()
            clickTextsProvider?.invoke()?.filter { it.isNotBlank() }?.forEach { arr.put(it) }
            arr.toString()
        } catch (_: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun onTaskCompleted(keyword: String, url: String) {
        mainHandler.post { onCompleted(keyword, url) }
    }

    @JavascriptInterface
    fun onOfferClicked(text: String, url: String) {
        mainHandler.post { onOfferClicked?.invoke(text, url) }
    }

    @JavascriptInterface
    fun onOfferClickedInNewTab(text: String, url: String) {
        mainHandler.post { onOfferClickedInNewTab?.invoke(text, url) }
    }

    @JavascriptInterface
    fun onNewTabInteractionCompleted(tabId: String, currentUrl: String, details: String) {
        mainHandler.post { onNewTabInteractionCompleted?.invoke(tabId, currentUrl, details) }
    }

    @JavascriptInterface
    fun onPageAnalyzed(reportJson: String) {
        mainHandler.post { onPageAnalyzed?.invoke(reportJson) }
    }

    @JavascriptInterface
    fun onPageActionMapGenerated(actionMapJson: String) {
        mainHandler.post { onPageActionMapGenerated?.invoke(actionMapJson) }
    }

    @JavascriptInterface
    fun onPageActionStepExecuted(stepIndex: Int, stepName: String, status: String) {
        mainHandler.post { onPageActionStepExecuted?.invoke(stepIndex, stepName, status) }
    }

    @JavascriptInterface
    fun onMidPageInfoExtracted(extractedKey: String, extractedValue: String) {
        mainHandler.post { onMidPageInfoExtracted?.invoke(extractedKey, extractedValue) }
    }

    @JavascriptInterface
    fun onTemplateGenerated(templateJson: String) {
        mainHandler.post { onTemplateGenerated?.invoke(templateJson) }
    }

    @JavascriptInterface
    fun onLockerDetected(url: String, id: String, isTriggered: Boolean) {
        mainHandler.post { onLockerDetected?.invoke(url, id, isTriggered, 0) }
    }

    @JavascriptInterface
    fun onLockerDetectedWithCount(url: String, id: String, isTriggered: Boolean, offersCount: Int) {
        mainHandler.post { onLockerDetected?.invoke(url, id, isTriggered, offersCount) }
    }

    @JavascriptInterface
    fun onLockerStatus(status: String) {
        mainHandler.post { onLockerStatus?.invoke(status) }
    }

    @JavascriptInterface
    fun onLockerOffersExtracted(jsonPayload: String) {
        mainHandler.post { onLockerOffersExtracted?.invoke(jsonPayload) }
    }

    @JavascriptInterface
    fun onHumanActionStatus(statusAr: String) {
        mainHandler.post { onHumanActionStatus?.invoke(statusAr) }
    }

    @JavascriptInterface
    fun onShieldTelemetry(jsonPayload: String) {
        mainHandler.post { onShieldTelemetry?.invoke(jsonPayload) }
    }
}

/**
 * Multi-Tab Model tracking tab identity, display title, URL, loading state and associated WebView.
 */
data class BrowserTabModel(
    val id: String,
    var title: String = "Tab 1",
    var url: String = "about:blank",
    var isLoading: Boolean = false,
    var progress: Float = 0f,
    var stageBadge: String = "",
    var webView: WebView? = null,
    var lastError: String? = null,
    var detectedLockerUrl: String? = null,
    var detectedLockerId: String? = null,
    var isLockerTriggered: Boolean = false,
    var lockerStatusMsg: String? = null,
    var isOfferTab: Boolean = false,
    var offerOpenedTime: Long = 0L,
    var hasSimulatedInteraction: Boolean = false,
    var initialReferer: String? = null,
    var navigationSeq: Int = 0,
    val docStartHandlers: MutableList<androidx.webkit.ScriptHandler> = mutableListOf()
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    settings: AppSettings,
    automationState: AutomationState,
    extractedInfo: ExtractedInfo,
    identity: GeneratedIdentity,
    scripts: List<ScriptItem>,
    browserCommand: BrowserCommand?,
    clickTexts: List<String> = emptyList(),
    activeClickText: String? = null,
    onClearBrowserCommand: () -> Unit,
    onNotifyCompletion: (String, String) -> Unit,
    onOfferClicked: ((String, String) -> Unit)? = null,
    onOfferClickedInNewTab: ((String, String, String) -> Unit)? = null,
    onNewTabInteractionCompleted: ((String, String, String) -> Unit)? = null,
    onLockerDetectedWithCount: ((String, String, Boolean, Int) -> Unit)? = null,
    onLockerOffersExtracted: ((String, ((Int, Int, Int, String) -> Unit)?) -> Unit)? = null,
    onPageAnalyzed: ((String) -> Unit)? = null,
    onPageActionMapGenerated: ((String) -> Unit)? = null,
    onPageActionStepExecuted: ((Int, String, String) -> Unit)? = null,
    onMidPageInfoExtracted: ((String, String) -> Unit)? = null,
    onTemplateGenerated: ((String) -> Unit)? = null,
    onRequestAutoTemplate: (() -> Unit)? = null,
    onActiveTabChanged: ((String) -> Unit)? = null,
    onUpdateWebRtcMode: ((String) -> Unit)? = null,
    onToggleDisableWebRtc: ((Boolean) -> Unit)? = null,
    onGoToTasks: (() -> Unit)? = null,
    onGoToOfferClick: (() -> Unit)? = null,
    onHumanActionStatus: ((String) -> Unit)? = null,
    onShieldTelemetry: ((String) -> Unit)? = null,
    onShieldSelfTestResult: ((String) -> Unit)? = null,
    onIsHostileHost: ((String) -> Boolean)? = null,
    onSurveillanceBlocked: ((String) -> Unit)? = null,
    onWebViewRendererGone: ((String, Boolean, String, Boolean) -> Unit)? = null,
    onConsoleError: ((String) -> Unit)? = null,
    onAiFixResult: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Multi-Tab browser tabs collection
    val tabs = remember {
        mutableStateListOf(
            BrowserTabModel(id = "tab_1", title = "Main Tab", url = "about:blank")
        )
    }
    var activeTabId by remember { mutableStateOf("tab_1") }
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.firstOrNull() ?: BrowserTabModel("tab_1")

    var urlInput by remember { mutableStateOf("") }
    var currentDisplayUrl by remember { mutableStateOf("about:blank") }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isPageLoading by remember { mutableStateOf(false) }
    var showDirectiveDetails by remember { mutableStateOf(false) }
    var showToolsDrawer by remember { mutableStateOf(false) }
    var showLockerHarvestBanner by remember { mutableStateOf(false) }
    var isScanningLocker by remember { mutableStateOf(false) }
    var lastConsoleLogMs by remember { mutableStateOf(0L) }
    var lockerScanRequestedUntil by remember { mutableStateOf(0L) }
    var retriedEmptyTrackerUrl by remember { mutableStateOf("") }
    var retriedProxyUrl by remember { mutableStateOf("") }
    // Renderer crash generations: bumping a tab's generation recreates only its WebView
    var crashGen by remember { mutableStateOf(mapOf<String, Int>()) }

    fun handleRendererGone(tabItem: BrowserTabModel, didCrash: Boolean) {
        // State mutation + destroy must happen on the main thread (callback thread varies)
        val runner = tabItem.webView
        val runOnUi: (() -> Unit) -> Unit = { block ->
            try {
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) block()
                else runner?.post { block() } ?: android.os.Handler(android.os.Looper.getMainLooper()).post { block() }
            } catch (_: Exception) {
                try { block() } catch (_: Exception) {}
            }
        }
        runOnUi {
            val gen = (crashGen[tabItem.id] ?: 0) + 1
            if (gen > 3) {
                onWebViewRendererGone?.invoke(tabItem.id, didCrash, tabItem.url, true)
                return@runOnUi
            }
            try {
                val dead = tabItem.webView
                tabItem.webView = null
                try { dead?.stopLoading() } catch (_: Exception) {}
                try { dead?.destroy() } catch (_: Exception) {}
            } catch (_: Exception) {}
            crashGen = crashGen + (tabItem.id to gen)
            onWebViewRendererGone?.invoke(tabItem.id, didCrash, tabItem.url, false)
        }
    }

    val triggerLockerScanAndHarvest: () -> Unit = {
        val wv = activeTab.webView
        if (wv != null) {
            isScanningLocker = true
            showLockerHarvestBanner = true
            lockerScanRequestedUntil = System.currentTimeMillis() + 5000L
            val defaultLockerId = settings.cpaLockerDefaultId.ifBlank { "1741238" }
            wv.evaluateJavascript(
                AutomationScriptBuilder.buildCpaLockerDetectorAndActivatorScript(
                    customId = defaultLockerId,
                    forceTrigger = true,
                    forceInjectIfMissing = settings.cpaLockerAutoInjectIfMissing
                ),
                null
            )
            wv.evaluateJavascript(
                AutomationScriptBuilder.buildLockerOfferExtractorScript(
                    customId = defaultLockerId,
                    forceActivateLocker = true
                ),
                null
            )
            wv.postDelayed({
                isScanningLocker = false
            }, 2600L)
        } else {
            Toast.makeText(context, "يرجى فتح صفحة في المتصفح أولاً لفحص اللوكر", Toast.LENGTH_SHORT).show()
        }
    }

    val defaultMobileUa = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36"
    val defaultDesktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    var isMobileUaMode by remember { mutableStateOf(true) }
    var autoPilotFormsEnabled by remember { mutableStateOf(true) }

    val effectiveWebRtcMode = remember(settings.disableWebRtc, settings.webrtcMode, settings.proxyEnabled, settings.proxyHost) {
        if (settings.disableWebRtc || settings.webrtcMode == "disabled" || settings.webrtcMode == "block") {
            "disabled"
        } else if (settings.webrtcMode == "real" && (settings.proxyEnabled || settings.proxyHost.isNotBlank())) {
            // Never allow native UDP WebRTC ("real") when proxy is configured, as UDP bypasses HTTP/SOCKS proxies and leaks the real ISP IP
            "spoof"
        } else {
            settings.webrtcMode.ifBlank { "spoof" }
        }
    }

    var lastOfferTabOpenedAt by remember { mutableStateOf(0L) }
    var lastOfferTabOpenedUrl by remember { mutableStateOf("") }

    val openInNewTab: (String, String) -> Unit = { rawTargetUrl, tabTitle ->
        val targetUrl = MainActivity.CustomWebViewClient.resolveSpecialSchemeUrl(rawTargetUrl.trim()) ?: rawTargetUrl.trim()
        val now = System.currentTimeMillis()
        if (targetUrl.isNotBlank() && targetUrl != "about:blank" &&
            (now - lastOfferTabOpenedAt > 3000L || targetUrl != lastOfferTabOpenedUrl)
        ) {
            lastOfferTabOpenedAt = now
            lastOfferTabOpenedUrl = targetUrl
            val parentReferer = activeTab.detectedLockerUrl?.takeIf { it.startsWith("http") }
                ?: activeTab.url.takeIf { it.startsWith("http") }
            val newId = "tab_${now}"
            val newTab = BrowserTabModel(
                id = newId,
                title = tabTitle.take(18).ifBlank { "Offer Tab" },
                url = targetUrl,
                stageBadge = "Offer",
                isOfferTab = true,
                offerOpenedTime = now,
                initialReferer = parentReferer
            )
            tabs.add(newTab)
            activeTabId = newId
            currentDisplayUrl = targetUrl
            urlInput = targetUrl
            onActiveTabChanged?.invoke(newId)
            onOfferClickedInNewTab?.invoke(tabTitle, targetUrl, newId)
        }
    }

    val openPopupInNewTab: (String) -> Unit = { rawTargetUrl ->
        val targetUrl = MainActivity.CustomWebViewClient.resolveSpecialSchemeUrl(rawTargetUrl.trim()) ?: rawTargetUrl.trim()
        val now = System.currentTimeMillis()
        if (targetUrl.isNotBlank() && targetUrl != "about:blank" &&
            (now - lastOfferTabOpenedAt > 3000L || targetUrl != lastOfferTabOpenedUrl)
        ) {
            lastOfferTabOpenedAt = now
            lastOfferTabOpenedUrl = targetUrl
            val parentReferer = activeTab.detectedLockerUrl?.takeIf { it.startsWith("http") }
                ?: activeTab.url.takeIf { it.startsWith("http") }
            val newId = "tab_${UUID.randomUUID()}"
            val newTab = BrowserTabModel(
                id = newId,
                title = "Offer Destination",
                url = targetUrl,
                stageBadge = "Offer",
                isOfferTab = true,
                offerOpenedTime = now,
                initialReferer = parentReferer
            )
            tabs.add(newTab)
            activeTabId = newId
            currentDisplayUrl = targetUrl
            urlInput = targetUrl
            onActiveTabChanged?.invoke(newId)
            onOfferClickedInNewTab?.invoke("Locker Offer", targetUrl, newId)
        }
    }

    // Synchronize URL input with active tab
    LaunchedEffect(activeTabId, activeTab.url) {
        urlInput = if (activeTab.url == "about:blank") "" else activeTab.url
        currentDisplayUrl = activeTab.url
        isPageLoading = activeTab.isLoading
        webProgress = activeTab.progress
    }

    // Resolve the active proxy IP to be used for WebRTC ICE spoofing
    val effectiveWebRtcIp = remember(settings.webrtcCustomIp, automationState.activeIp, extractedInfo.ip, settings.proxyHost) {
        if (settings.webrtcCustomIp.isNotBlank()) {
            settings.webrtcCustomIp.trim()
        } else if (automationState.activeIp.isNotBlank() && automationState.activeIp != "Not Connected") {
            automationState.activeIp
        } else if (extractedInfo.ip.isNotBlank()) {
            extractedInfo.ip
        } else if (settings.proxyHost.isNotBlank()) {
            settings.proxyHost
        } else {
            "104.28.19.42"
        }
    }

    // Keep proxy configuration synchronized whenever proxy settings change
    var prevProxyEnabled by remember { mutableStateOf(settings.proxyEnabled) }
    var prevProxyHost by remember { mutableStateOf(settings.proxyHost) }
    var prevProxyPort by remember { mutableStateOf(settings.proxyPort) }
    LaunchedEffect(settings.proxyEnabled, settings.proxyHost, settings.proxyPort, settings.proxyType, settings.proxyUser, settings.proxyPass) {
        val proxyToggled = prevProxyEnabled != settings.proxyEnabled || prevProxyHost != settings.proxyHost || prevProxyPort != settings.proxyPort
        prevProxyEnabled = settings.proxyEnabled
        prevProxyHost = settings.proxyHost
        prevProxyPort = settings.proxyPort

        val port = settings.proxyPort.toIntOrNull()
        WebProxyManager.applyProxy(context, settings.proxyEnabled, settings.proxyHost, port, settings.proxyType, settings.proxyUser, settings.proxyPass) { _, _ ->
            if (proxyToggled && !automationState.isRunning && activeTab.url.isNotBlank() && activeTab.url != "about:blank") {
                activeTab.lastError = null
                activeTab.webView?.let { wv ->
                    val target = activeTab.url
                    wv.post { wv.loadUrl(target) }
                }
            }
        }
    }

    fun refreshTabDocumentStartScripts(wv: WebView?, tabItem: BrowserTabModel) {
        if (wv == null) return
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                tabItem.docStartHandlers.forEach { h ->
                    try { h.remove() } catch (_: Exception) {}
                }
                tabItem.docStartHandlers.clear()
                val currentUa = wv.settings.userAgentString.ifBlank { if (isMobileUaMode) defaultMobileUa else defaultDesktopUa }
                tabItem.docStartHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        wv,
                        AutomationScriptBuilder.buildAntiDetectionScript(
                            proxyIp = effectiveWebRtcIp,
                            webrtcMode = effectiveWebRtcMode,
                            timezone = extractedInfo.timezone,
                            language = extractedInfo.language,
                            latitude = extractedInfo.latitude,
                            longitude = extractedInfo.longitude,
                            userAgent = currentUa,
                            fingerprintSeed = automationState.fingerprintSeed
                        ),
                        setOf("*")
                    )
                )
                tabItem.docStartHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        wv,
                        AutomationScriptBuilder.buildDeviceBridgeScript(
                            latitude = extractedInfo.latitude,
                            longitude = extractedInfo.longitude,
                            userAgent = currentUa,
                            fingerprintSeed = automationState.fingerprintSeed,
                            language = extractedInfo.language
                        ),
                        setOf("*")
                    )
                )
                // Tracker deception stubs (invisible blocking): page believes trackers run, nothing exfiltrates
                if (settings.trackerShieldEnabled && settings.trackerMode != "allow") {
                    tabItem.docStartHandlers.add(
                        WebViewCompat.addDocumentStartJavaScript(
                            wv,
                            AutomationScriptBuilder.buildTrackerDeceptionScript(),
                            setOf("*")
                        )
                    )
                }
                tabItem.docStartHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        wv,
                        AutomationScriptBuilder.buildTimezoneScript(extractedInfo.timezone, extractedInfo.language),
                        setOf("*")
                    )
                )
                tabItem.docStartHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        wv,
                        AutomationScriptBuilder.buildCanvasNoiseScript(),
                        setOf("*")
                    )
                )
                tabItem.docStartHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        wv,
                        AutomationScriptBuilder.buildCrossOriginSubframeWorkerScript(),
                        setOf("*")
                    )
                )
                tabItem.docStartHandlers.add(
                    WebViewCompat.addDocumentStartJavaScript(
                        wv,
                        AutomationScriptBuilder.buildSmartlinkRedirectHelperScript(),
                        setOf("*")
                    )
                )
            } catch (_: Exception) {}
        }
    }

    fun loadUrlSmart(wv: WebView?, tabItem: BrowserTabModel, rawTargetUrl: String, customReferer: String? = null, customUa: String? = null) {
        val trimmed = rawTargetUrl.trim()
        if (trimmed.isBlank()) return
        val resolved = MainActivity.CustomWebViewClient.resolveSpecialSchemeUrl(trimmed)
            ?: (if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed")
        tabItem.navigationSeq++
        tabItem.url = resolved
        tabItem.lastError = null
        tabItem.isLoading = true
        if (tabItem.id == activeTabId) {
            urlInput = resolved
            currentDisplayUrl = resolved
            isPageLoading = true
        }
        val targetWv = wv ?: tabItem.webView
        targetWv?.let { webView ->
            val resolvedUa = com.example.service.IdentityService.resolveUserAgent(customUa ?: webView.settings.userAgentString, isMobileUaMode)
            webView.settings.userAgentString = resolvedUa
            refreshTabDocumentStartScripts(webView, tabItem)
            val effectiveRef = customReferer?.takeIf { it.isNotBlank() }
                ?: tabItem.initialReferer?.takeIf { it.isNotBlank() }
                ?: "https://www.google.com/"
            val headers = mutableMapOf("Referer" to effectiveRef)
            // Shield: Accept-Language header matches navigator.language (else header-vs-JS mismatch = bot signal)
            extractedInfo.language.trim().takeIf { it.matches(Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$")) }?.let {
                headers["Accept-Language"] = it
            }
            webView.post {
                try {
                    webView.loadUrl(resolved, headers)
                } catch (_: Exception) {}
            }
        }
    }

    // Re-inject WebRTC protection dynamically ONLY into the ACTIVE displayed tab
    // Expert: stable fingerprintSeed per repetition — same session same fingerprint
    LaunchedEffect(activeTabId, activeTab.webView, effectiveWebRtcIp, effectiveWebRtcMode, extractedInfo.timezone, extractedInfo.language, automationState.fingerprintSeed) {
        val currentUa = activeTab.webView?.settings?.userAgentString.orEmpty().ifBlank { if (isMobileUaMode) defaultMobileUa else defaultDesktopUa }
        refreshTabDocumentStartScripts(activeTab.webView, activeTab)
        activeTab.webView?.evaluateJavascript(
            AutomationScriptBuilder.buildAntiDetectionScript(
                proxyIp = effectiveWebRtcIp,
                webrtcMode = effectiveWebRtcMode,
                timezone = extractedInfo.timezone,
                language = extractedInfo.language,
                latitude = extractedInfo.latitude,
                longitude = extractedInfo.longitude,
                userAgent = currentUa,
                fingerprintSeed = automationState.fingerprintSeed
            ),
            null
        )
    }

    // Autonomous Closed-Loop Re-Analysis Engine (Brain-Paced 4s + Instant URL/DOM change detection):
    val isAutoOrOfferActive = automationState.isRunning || activeTab.isOfferTab || !automationState.activeClickText.isNullOrBlank() || (autoPilotFormsEnabled && currentDisplayUrl.startsWith("http"))
    var reanalysisCountdown by remember { mutableIntStateOf(4) }
    LaunchedEffect(activeTabId, currentDisplayUrl, isAutoOrOfferActive) {
        if (!isAutoOrOfferActive) return@LaunchedEffect
        reanalysisCountdown = 4
        while (isAutoOrOfferActive) {
            delay(1000)
            reanalysisCountdown--
            val activeDisplayed = tabs.find { it.id == activeTabId }
            if (activeDisplayed != null && !activeDisplayed.isLoading) {
                val realUrl = activeDisplayed.webView?.url.orEmpty()
                if (realUrl.isNotBlank() && realUrl != currentDisplayUrl && realUrl != "about:blank") {
                    currentDisplayUrl = realUrl
                    urlInput = realUrl
                    activeDisplayed.url = realUrl
                    reanalysisCountdown = 4
                    activeDisplayed.webView?.evaluateJavascript(
                        AutomationScriptBuilder.buildPageAnalyzerScript(clickTexts, activeClickText ?: automationState.activeClickText),
                        null
                    )
                } else if (reanalysisCountdown <= 0) {
                    val nextCadenceSec = (automationState.brainRecommendedDelayMs / 1000L).toInt().coerceIn(3, 6)
                    reanalysisCountdown = nextCadenceSec
                    activeDisplayed.webView?.evaluateJavascript(
                        AutomationScriptBuilder.buildPageAnalyzerScript(clickTexts, activeClickText ?: automationState.activeClickText),
                        null
                    )
                }
            }
        }
    }

    // Autonomous Smart Automation Loop:
    // Paced human cycle: Maintains the automation loop, running a cycle every 15 to 25 seconds!
    var cycleCountdownSeconds by remember { mutableIntStateOf(0) }
    var currentCycleTotal by remember { mutableIntStateOf(20) }

    LaunchedEffect(
        activeTabId, identity, extractedInfo, isAutoOrOfferActive,
        automationState.activeTaskCategories, clickTexts, activeClickText,
        settings.cycleIntervalMinSec, settings.cycleIntervalMaxSec
    ) {
        while (isAutoOrOfferActive) {
            val minSec = settings.cycleIntervalMinSec.coerceAtLeast(15)
            val maxSec = settings.cycleIntervalMaxSec.coerceAtLeast(minSec)
            val cycleSec = if (minSec < maxSec) kotlin.random.Random.nextInt(minSec, maxSec + 1) else minSec
            currentCycleTotal = cycleSec
            cycleCountdownSeconds = cycleSec

            val activeDisplayed = tabs.find { it.id == activeTabId }
            if (activeDisplayed != null) {
                val effClickText = activeClickText ?: automationState.activeClickText
                activeDisplayed.webView?.evaluateJavascript("window._cpaIsOfferTab = ${activeDisplayed.isOfferTab};", null)

                if (!activeDisplayed.isOfferTab) {
                    // Also check for Content Locker on landing page
                    activeDisplayed.webView?.evaluateJavascript(
                        AutomationScriptBuilder.buildCpaLockerDetectorAndActivatorScript(
                            customId = settings.cpaLockerDefaultId.ifBlank { "1741238" },
                            forceTrigger = settings.cpaLockerAutoTrigger,
                            forceInjectIfMissing = settings.cpaLockerAutoInjectIfMissing
                        ),
                        null
                    )
                    activeDisplayed.webView?.evaluateJavascript(
                        AutomationScriptBuilder.buildLockerOfferAutoClickScript(
                            clickTexts = clickTexts,
                            activeTargetText = effClickText,
                            selectionStrategy = settings.offerSelectionStrategy,
                            openInNewTab = settings.offerClickOpenInNewTab
                        ),
                        null
                    )
                }

                // Perform smart form filling, survey answering, and CTA progression with live Brain Mode, Persona Facts & Human Simulation
                if (!automationState.isWaitingBetweenStages) {
                    activeDisplayed.webView?.evaluateJavascript(
                        AutomationScriptBuilder.buildSmartFormFillScript(
                            identity = identity,
                            categories = automationState.activeTaskCategories,
                            clickTexts = clickTexts,
                            activeClickText = effClickText,
                            extractedInfo = extractedInfo,
                            brainMode = automationState.brainExecutionMode,
                            personaFactsJson = automationState.personaFactsJson,
                            humanSimulationEnabled = settings.humanSimulationEnabled,
                            humanSimulationProfile = settings.humanSimulationProfile,
                            stageTransitionWaitMinSec = settings.stageTransitionWaitMinSec,
                            stageTransitionWaitMaxSec = settings.stageTransitionWaitMaxSec,
                            humanTypingSimulation = settings.humanTypingSimulation,
                            humanPointerAndTouchPaths = settings.humanPointerAndTouchPaths,
                            humanReadingPauseBeforeAction = settings.humanReadingPauseBeforeAction
                        ),
                        null
                    )
                    if (settings.humanSimulationEnabled && settings.humanScrollAndGazeSimulation) {
                        activeDisplayed.webView?.evaluateJavascript(
                            AutomationScriptBuilder.buildAwareHumanSimulationScript(),
                            null
                        )
                    }
                }
            }

            // Second-by-second countdown for the 15-25s interval
            for (sec in cycleSec downTo 1) {
                if (!isAutoOrOfferActive) break
                cycleCountdownSeconds = sec
                delay(1000)
            }
        }
        cycleCountdownSeconds = 0
    }

    // Execute BrowserCommands from ViewModel on the ACTIVE tab
    LaunchedEffect(browserCommand) {
        browserCommand?.let { cmd ->
            when (cmd) {
                is BrowserCommand.LoadUrl -> {
                    retriedEmptyTrackerUrl = ""
                    val targetTab = if (activeTab.isOfferTab && tabs.size > 1) {
                        val mainTab = tabs.first()
                        activeTabId = mainTab.id
                        mainTab
                    } else {
                        activeTab
                    }
                    loadUrlSmart(targetTab.webView, targetTab, cmd.url, cmd.referer, cmd.userAgent)
                    if (targetTab.webView == null) {
                        // Wait briefly for AndroidView factory to attach WebView and then trigger load
                        for (attempt in 1..15) {
                            delay(80)
                            val attachedWv = targetTab.webView
                            if (attachedWv != null) {
                                loadUrlSmart(attachedWv, targetTab, cmd.url, cmd.referer, cmd.userAgent)
                                break
                            }
                        }
                    }
                }
                is BrowserCommand.Reload -> {
                    val targetUrl = activeTab.url.ifBlank { currentDisplayUrl }
                    if (activeTab.webView?.url.isNullOrBlank() || activeTab.webView?.url == "about:blank") {
                        if (targetUrl.isNotBlank() && targetUrl != "about:blank") {
                            loadUrlSmart(activeTab.webView, activeTab, targetUrl)
                        }
                    } else {
                        activeTab.webView?.reload()
                    }
                }
                is BrowserCommand.GoBack -> if (activeTab.webView?.canGoBack() == true) activeTab.webView?.goBack()
                is BrowserCommand.GoForward -> if (activeTab.webView?.canGoForward() == true) activeTab.webView?.goForward()
                is BrowserCommand.ClearUrl -> {
                    activeTab.webView?.let { webView ->
                        webView.clearCache(true)
                        webView.clearHistory()
                        webView.clearFormData()
                        webView.loadUrl("about:blank")
                        currentDisplayUrl = "about:blank"
                        activeTab.url = "about:blank"
                        activeTab.title = "New Tab"
                        urlInput = ""
                    }
                }
                is BrowserCommand.ClearCacheAndStorage -> {
                    MainActivity.clearWebViewData(context, activeTab.webView) {
                        // Never overwrite an active task navigation with about:blank!
                        if (!automationState.isRunning && (activeTab.url.isBlank() || activeTab.url == "about:blank")) {
                            activeTab.webView?.loadUrl("about:blank")
                            currentDisplayUrl = "about:blank"
                            activeTab.url = "about:blank"
                            activeTab.title = "New Tab"
                            urlInput = ""
                        }
                    }
                }
                is BrowserCommand.GenerateAutoTemplate -> {
                    activeTab.webView?.evaluateJavascript(
                        AutomationScriptBuilder.buildAutoTemplateGeneratorScript(),
                        null
                    )
                }
                is BrowserCommand.ExecuteAiFix -> {
                    activeTab.webView?.let { wv ->
                        try {
                            wv.evaluateJavascript(
                                cmd.script,
                                android.webkit.ValueCallback<String?> { v ->
                                    onAiFixResult?.invoke(cmd.fixId, v.orEmpty())
                                }
                            )
                        } catch (_: Exception) {}
                    }
                }
                is BrowserCommand.RunShieldSelfTest -> {
                    activeTab.webView?.let { wv ->
                        try {
                            wv.evaluateJavascript(cmd.script, null)
                            delay(2600)
                            wv.evaluateJavascript(
                                "JSON.stringify(window.__cpaSelfTestResult || null)",
                                android.webkit.ValueCallback<String?> { v ->
                                    onShieldSelfTestResult?.invoke(v.orEmpty())
                                }
                            )
                        } catch (_: Exception) {}
                    }
                }
                is BrowserCommand.ExecuteWorkTemplate -> {
                    activeTab.webView?.evaluateJavascript(
                        AutomationScriptBuilder.buildWorkTemplateExecutionScript(cmd.stepsJson, identity),
                        null
                    )
                }
                is BrowserCommand.OpenInNewTab -> {
                    openInNewTab(cmd.url, cmd.title)
                }
                is BrowserCommand.ScanAndExtractLockerOffers -> {
                    triggerLockerScanAndHarvest()
                }
                is BrowserCommand.ExecuteBrainAction -> {
                    activeTab.webView?.let { wv ->
                        val safeMode = org.json.JSONObject.quote(cmd.executionMode.ifBlank { "normal" })
                        val facts = cmd.personaFactsJson.takeIf { it.isNotBlank() && it != "{}" }
                            ?: com.example.service.IdentityService.buildPersonaFactsJson(identity)
                        wv.evaluateJavascript(
                            """
                            (function() {
                              window._cpaBrainMode = $safeMode;
                              window._cpaPersonaFacts = $facts;
                              if (typeof window._cpaExecuteCycle === 'function') {
                                window._cpaExecuteCycle();
                              }
                            })();
                            """.trimIndent(),
                            null
                        )
                        if (!cmd.recoveryJs.isNullOrBlank()) {
                            wv.evaluateJavascript(cmd.recoveryJs, null)
                        }
                    }
                }
            }
            onClearBrowserCommand()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CpaBg)) {
        // 1. Unified Compact Browser Navigation & Telemetry Bar (38.dp — frees ~90% of screen for WebView)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(CpaCard)
                .border(0.5.dp, CpaBorder)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { activeTab.webView?.let { if (it.canGoBack()) it.goBack() } },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CpaText, modifier = Modifier.size(15.dp))
            }

            IconButton(
                onClick = { activeTab.webView?.reload() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = CpaText, modifier = Modifier.size(15.dp))
            }

            // Read-Only Active Task URL Pill (Links are added & launched exclusively from Tasks screen)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CpaBg)
                    .border(0.5.dp, if (automationState.isRunning) CpaPrimary.copy(alpha = 0.6f) else CpaBorder, RoundedCornerShape(6.dp))
                    .clickable { onGoToTasks?.invoke() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (currentDisplayUrl.startsWith("https")) Icons.Default.Lock else Icons.Default.Language,
                    contentDescription = "SSL",
                    tint = if (currentDisplayUrl.startsWith("https")) CpaSuccess else CpaPrimary,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                val displayHostOrUrl = if (currentDisplayUrl.isBlank() || currentDisplayUrl == "about:blank") {
                    "إضافة وتشغيل الروابط من شاشة المهام (Tasks) ⬅"
                } else {
                    currentDisplayUrl.removePrefix("https://").removePrefix("http://")
                }
                Text(
                    text = displayHostOrUrl,
                    color = if (currentDisplayUrl == "about:blank") CpaTextMuted else CpaText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (extractedInfo.countryCode.isNotBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = extractedInfo.countryCode,
                        color = CpaPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Smart Locker Detect & Offer Extractor Button (in Browser Top Bar)
            val lockerActiveOrFound = automationState.lockerDetectedOnPage || activeTab.detectedLockerUrl != null || automationState.lastExtractedLockerOffers.isNotEmpty()
            val extractedCount = maxOf(automationState.lockerOffersFoundCount, automationState.lastExtractedLockerOffers.size)
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isScanningLocker -> CpaWarning.copy(alpha = 0.25f)
                            lockerActiveOrFound -> CpaSuccess.copy(alpha = 0.20f)
                            else -> CpaAccent.copy(alpha = 0.18f)
                        }
                    )
                    .border(
                        0.7.dp,
                        when {
                            isScanningLocker -> CpaWarning
                            lockerActiveOrFound -> CpaSuccess
                            else -> CpaAccent
                        },
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { triggerLockerScanAndHarvest() }
                    .padding(horizontal = 6.dp)
                    .testTag("browser_detect_extract_locker_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Detect Locker and Extract Offers",
                        tint = when {
                            isScanningLocker -> CpaWarning
                            lockerActiveOrFound -> CpaSuccess
                            else -> CpaAccent
                        },
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = when {
                            isScanningLocker -> "فحص..."
                            extractedCount > 0 -> "Locker ($extractedCount)"
                            else -> "Locker"
                        },
                        color = when {
                            isScanningLocker -> CpaWarning
                            lockerActiveOrFound -> CpaSuccess
                            else -> CpaAccent
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick Tasks Screen Button
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(CpaPrimary.copy(alpha = 0.16f))
                    .border(0.5.dp, CpaPrimary, RoundedCornerShape(6.dp))
                    .clickable { onGoToTasks?.invoke() }
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Tasks", color = CpaPrimary, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
            }

            // Collapsible Tools Drawer Toggle Button (keeps Browser screen clean & uncluttered)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (showToolsDrawer) CpaPrimary else CpaCardElevated)
                    .border(0.5.dp, if (showToolsDrawer) CpaPrimary else CpaBorder, RoundedCornerShape(6.dp))
                    .clickable { showToolsDrawer = !showToolsDrawer },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showToolsDrawer) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Browser Tools",
                    tint = if (showToolsDrawer) Color.Black else CpaText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Thin 2.dp Web Loading Progress Indicator
        if (isPageLoading && webProgress < 1f) {
            LinearProgressIndicator(
                progress = { webProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = CpaPrimary,
                trackColor = CpaBorder
            )
        }

        // 2. Slim Single-Line Live Automation & Multi-Tab Bar (shown ONLY when running or multiple tabs exist)
        if (automationState.isRunning || tabs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(25.dp)
                    .background(CpaCardElevated)
                    .border(0.5.dp, CpaBorder.copy(alpha = 0.5f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (automationState.isRunning) {
                    val catLabel = automationState.detectedCategoryAr.ifBlank {
                        automationState.detectedPageCategory.ifBlank { "تحليل تلقائي" }
                    }
                    Text(
                        text = "🟢 $catLabel",
                        color = CpaSuccess,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🧠 مرحلة ${automationState.funnelStageIndex}/6" +
                            (if (automationState.totalStepsAdvanced > 0) " · خطوة #${automationState.totalStepsAdvanced}" else "") +
                            " (${automationState.brainConfidence}%)",
                        color = CpaPrimary,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (automationState.brainExecutionMode != "normal") {
                        Text(
                            text = "⚡${automationState.brainExecutionMode}",
                            color = CpaWarning,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val activeDecisionText = automationState.smartDecisionTitle.ifBlank { automationState.activeDirectiveTitle }
                    if (activeDecisionText.isNotBlank()) {
                        Text(
                            text = "• ${activeDecisionText.take(38)}",
                            color = CpaText,
                            fontSize = 8.5.sp,
                            maxLines = 1
                        )
                    }
                    if (cycleCountdownSeconds > 0) {
                        Text(
                            text = "⏱️${cycleCountdownSeconds}s",
                            color = CpaPrimary,
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (tabs.size > 1) {
                    tabs.forEachIndexed { idx, tab ->
                        val isActive = tab.id == activeTabId
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isActive) CpaPrimary.copy(alpha = 0.2f) else CpaBg)
                                .border(0.5.dp, if (isActive) CpaPrimary else CpaBorder, RoundedCornerShape(4.dp))
                                .clickable {
                                    activeTabId = tab.id
                                    onActiveTabChanged?.invoke(tab.id)
                                }
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tab.title.take(12).ifBlank { "Tab ${idx + 1}" },
                                color = if (isActive) CpaPrimary else CpaTextMuted,
                                fontSize = 8.5.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Tab",
                                tint = CpaError,
                                modifier = Modifier
                                    .size(10.dp)
                                    .clickable {
                                        val closingId = tab.id
                                        val wasOfferTab = tab.isOfferTab
                                        val closingIndex = tabs.indexOfFirst { it.id == closingId }
                                        tab.webView?.destroy()
                                        tabs.remove(tab)
                                        if (activeTabId == closingId && tabs.isNotEmpty()) {
                                            val nextIndex = closingIndex.coerceAtMost(tabs.size - 1)
                                            activeTabId = tabs[nextIndex].id
                                            onActiveTabChanged?.invoke(activeTabId)
                                        }
                                        if (wasOfferTab && tabs.none { it.isOfferTab }) {
                                            tabs.firstOrNull { !it.isOfferTab }?.webView?.evaluateJavascript(
                                                """
                                                (function() {
                                                  window.__cpa_locker_offer_clicked = false;
                                                  window.__cpa_locker_human_sequence_busy = false;
                                                })();
                                                """.trimIndent(),
                                                null
                                            )
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        // 3. Optional Collapsible Quick Tools Drawer (Hidden by default so WebView stays full-screen)
        if (showToolsDrawer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CpaCardElevated)
                    .border(0.5.dp, CpaBorder)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "IP: $effectiveWebRtcIp",
                    color = CpaText,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace
                )

                val isWebRtcDisabledNow = effectiveWebRtcMode == "disabled"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isWebRtcDisabledNow) CpaError.copy(alpha = 0.2f) else CpaSuccess.copy(alpha = 0.18f))
                        .border(0.5.dp, if (isWebRtcDisabledNow) CpaError else CpaSuccess, RoundedCornerShape(4.dp))
                        .clickable { onToggleDisableWebRtc?.invoke(!isWebRtcDisabledNow) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isWebRtcDisabledNow) "🔇 WebRTC: Désactivé" else "🛡️ WebRTC: Spoof",
                        color = if (isWebRtcDisabledNow) CpaError else CpaSuccess,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (autoPilotFormsEnabled) CpaPrimary.copy(alpha = 0.2f) else CpaCard)
                        .border(0.5.dp, if (autoPilotFormsEnabled) CpaPrimary else CpaBorder, RoundedCornerShape(4.dp))
                        .clickable { autoPilotFormsEnabled = !autoPilotFormsEnabled }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (autoPilotFormsEnabled) "🤖 تفاعل تلقائي: ON" else "⏸️ تفاعل تلقائي: OFF",
                        color = if (autoPilotFormsEnabled) CpaPrimary else CpaTextMuted,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isMobileUaMode) CpaPrimary.copy(alpha = 0.2f) else CpaAccent.copy(alpha = 0.2f))
                        .border(0.5.dp, if (isMobileUaMode) CpaPrimary else CpaAccent, RoundedCornerShape(4.dp))
                        .clickable {
                            isMobileUaMode = !isMobileUaMode
                            activeTab.webView?.settings?.userAgentString = if (isMobileUaMode) defaultMobileUa else defaultDesktopUa
                            activeTab.webView?.reload()
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isMobileUaMode) "📱 Mobile UA" else "💻 Desktop UA",
                        color = if (isMobileUaMode) CpaPrimary else CpaAccent,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CpaSuccess.copy(alpha = 0.2f))
                        .border(0.5.dp, CpaSuccess, RoundedCornerShape(4.dp))
                        .clickable { triggerLockerScanAndHarvest() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("tools_extract_locker_offers_button")
                ) {
                    Text(
                        text = if (isScanningLocker) "⏳ جاري استخراج عروض اللوكر..." else "🔓 كشف اللوكر وحفظ نصوصه (${clickTexts.size})",
                        color = CpaSuccess,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (onGoToOfferClick != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CpaPrimary.copy(alpha = 0.2f))
                            .border(0.5.dp, CpaPrimary, RoundedCornerShape(4.dp))
                            .clickable { onGoToOfferClick.invoke() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🎯 شاشة Offer Click",
                            color = CpaPrimary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CpaAccent.copy(alpha = 0.2f))
                        .border(0.5.dp, CpaAccent, RoundedCornerShape(4.dp))
                        .clickable {
                            val effClick = activeClickText ?: automationState.activeClickText
                            activeTab.webView?.evaluateJavascript(
                                AutomationScriptBuilder.buildSmartFormFillScript(
                                    identity = identity,
                                    categories = automationState.activeTaskCategories.ifBlank { "Offer Click, Survey / Quiz, Email Submit, Lead Gen Form, Terms Agreement, Skip Upsells, Confirmation" },
                                    clickTexts = clickTexts,
                                    activeClickText = effClick,
                                    extractedInfo = extractedInfo
                                ),
                                null
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("⚡ ملء وتفاعل فوري", color = CpaAccent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CpaError.copy(alpha = 0.15f))
                        .border(0.5.dp, CpaError.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable {
                            MainActivity.clearWebViewData(context, activeTab.webView) {
                                Toast.makeText(context, "تم مسح الكاش والكوكيز", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("🧹 مسح الكاش", color = CpaError, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3.5. Smart Locker Detection & Offer Extraction Live Banner (shown when user clicks Locker button)
        if (showLockerHarvestBanner) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CpaCardElevated)
                    .border(0.5.dp, if (automationState.lastExtractedLockerOffers.isNotEmpty()) CpaSuccess else CpaPrimary)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isScanningLocker) "⏳ جاري كشف اللوكر واستخراج النصوص..."
                                   else if (automationState.lastLockerHarvestSummary.isNotBlank()) automationState.lastLockerHarvestSummary
                                   else "🔓 اضغط «إعادة الفحص» لاستخراج عروض اللوكر وحفظها بدون تكرار",
                            color = if (automationState.lastLockerHarvestNewCount > 0) CpaSuccess else CpaText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CpaAccent.copy(alpha = 0.2f))
                                .border(0.5.dp, CpaAccent, RoundedCornerShape(4.dp))
                                .clickable { triggerLockerScanAndHarvest() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("فحص 🔄", color = CpaAccent, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                        if (onGoToOfferClick != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CpaPrimary)
                                    .clickable { onGoToOfferClick.invoke() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Offer Click 🎯", color = Color.Black, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Locker Banner",
                            tint = CpaTextMuted,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { showLockerHarvestBanner = false }
                        )
                    }
                }

                if (automationState.lastExtractedLockerOffers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "نصوص مستخرجة (اضغط للنقر الفوري):",
                            color = CpaTextMuted,
                            fontSize = 8.sp
                        )
                        automationState.lastExtractedLockerOffers.forEachIndexed { idx, offerTxt ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CpaBg)
                                    .border(0.5.dp, CpaSuccess.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        val quotedOfferText = org.json.JSONObject.quote(offerTxt)
                                        activeTab.webView?.evaluateJavascript(
                                            """
                                            (function() {
                                              window.__cpa_locker_offer_clicked = false;
                                              window.__cpa_locker_human_sequence_busy = false;
                                              try {
                                                for (var w = 0; w < window.frames.length; w++) {
                                                  window.frames[w].postMessage({ type: 'CPA_CLICK_LOCKER_OFFER', targetText: $quotedOfferText }, '*');
                                                }
                                              } catch(e) {}
                                            })();
                                            """.trimIndent(),
                                            null
                                        )
                                        activeTab.webView?.evaluateJavascript(
                                            AutomationScriptBuilder.buildLockerOfferAutoClickScript(
                                                clickTexts = listOf(offerTxt) + clickTexts,
                                                activeTargetText = offerTxt,
                                                selectionStrategy = "priority",
                                                openInNewTab = settings.offerClickOpenInNewTab
                                            ),
                                            null
                                        )
                                        Toast.makeText(context, "جاري النقر البشري على العرض: ${offerTxt.take(32)}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "#${idx + 1} ${offerTxt.take(36)}",
                                    color = CpaSuccess,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Compact Connection Error Recovery Banner (only when an actual network error occurs)
        if (activeTab.lastError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CpaError.copy(alpha = 0.14f))
                    .border(0.5.dp, CpaError.copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "⚠️ ${activeTab.lastError}",
                    color = CpaError,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CpaPrimary)
                        .clickable {
                            activeTab.lastError = null
                            retriedProxyUrl = ""
                            val retryUrl = activeTab.url.ifBlank { currentDisplayUrl }
                            if (settings.proxyEnabled && settings.proxyHost.isNotBlank()) {
                                // Strict Proxy Kill-Switch: Re-apply proxy rather than clearing it so real IP never leaks
                                WebProxyManager.applyProxy(
                                    context,
                                    true,
                                    settings.proxyHost,
                                    settings.proxyPort.toIntOrNull(),
                                    settings.proxyType,
                                    settings.proxyUser,
                                    settings.proxyPass
                                ) { _, _ ->
                                    if (retryUrl.isNotBlank() && retryUrl != "about:blank") {
                                        loadUrlSmart(activeTab.webView, activeTab, retryUrl)
                                    } else {
                                        activeTab.webView?.reload()
                                    }
                                }
                            } else {
                                if (retryUrl.isNotBlank() && retryUrl != "about:blank") {
                                    loadUrlSmart(activeTab.webView, activeTab, retryUrl)
                                } else {
                                    activeTab.webView?.reload()
                                }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("إعادة محاولة 🔄", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Multi-Tab WebViews Container
        // Each tab retains its own distinct WebView instance in memory.
        // Background tabs are kept hidden (View.GONE) to save GPU/rendering cycles.
        // Automated scripts and interactions ONLY target the active tab!
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            tabs.forEach { tabItem ->
                val isThisTabActive = tabItem.id == activeTabId

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (isThisTabActive) 1f else 0f
                            translationY = if (isThisTabActive) 0f else 99999f
                        }
                ) {
                    key(tabItem.id, crashGen[tabItem.id] ?: 0) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                // NOTE: tab URL restore on recreate is handled at the end of
                                // factory (loadUrlSmart) — crashed tabs reload automatically.
                                tabItem.webView = this
                                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                this.settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    databaseEnabled = true
                                    setSupportMultipleWindows(true)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = if (isMobileUaMode) defaultMobileUa else defaultDesktopUa

                                    safeBrowsingEnabled = false
                                    setGeolocationEnabled(true)
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    @Suppress("DEPRECATION")
                                    allowFileAccessFromFileURLs = false
                                    @Suppress("DEPRECATION")
                                    allowUniversalAccessFromFileURLs = false
                                    mediaPlaybackRequiresUserGesture = false
                                }

                                // Install-offer downloads behave like a real browser (system DownloadManager)
                                try {
                                    setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                                        try {
                                            val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                                            val req = android.app.DownloadManager.Request(android.net.Uri.parse(url)).apply {
                                                setTitle(fileName)
                                                setDescription("Offer download via Smart Automator")
                                                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                setMimeType(mimeType)
                                                setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                                            }
                                            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                            dm.enqueue(req)
                                            onHumanActionStatus?.invoke("⬇️ بدء تنزيل العرض: $fileName")
                                        } catch (_: Exception) {}
                                    }
                                } catch (_: Exception) {}

                                // Strip X-Requested-With package header if supported by AndroidX WebKit
                                try {
                                    val method = WebSettingsCompat::class.java.getMethod(
                                        "setRequestedWithHeaderOriginAllowList",
                                        WebSettings::class.java,
                                        Set::class.java
                                    )
                                    method.invoke(null, this.settings, emptySet<String>())
                                } catch (_: Throwable) {}

                                try {
                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                    cookieManager.setAcceptCookie(true)
                                    cookieManager.setAcceptThirdPartyCookies(this, true)
                                } catch (e: Exception) {}

                                if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                                    try {
                                        WebSettingsCompat.setSafeBrowsingEnabled(this.settings, false)
                                    } catch (e: Exception) {}
                                }

                                refreshTabDocumentStartScripts(this, tabItem)

                                addJavascriptInterface(
                                    WebAppInterface(
                                        onCompleted = { kw, pageUrl ->
                                            onNotifyCompletion(kw, pageUrl)
                                        },
                                        onOfferClicked = { txt, pageUrl ->
                                            onOfferClicked?.invoke(txt, pageUrl)
                                        },
                                        onOfferClickedInNewTab = { txt, pageUrl ->
                                            openInNewTab(pageUrl, txt)
                                        },
                                        onNewTabInteractionCompleted = { tid, pageUrl, details ->
                                            onNewTabInteractionCompleted?.invoke(tid, pageUrl, details)
                                        },
                                        onPageAnalyzed = { reportJson ->
                                            try {
                                                val report = TaskCategoryPlanner.parseAnalysisReport(reportJson)
                                                tabItem.stageBadge = report.detectedCategoryAr.take(8)
                                            } catch (e: Exception) {}
                                            onPageAnalyzed?.invoke(reportJson)
                                        },
                                        onPageActionMapGenerated = { mapJson ->
                                            onPageActionMapGenerated?.invoke(mapJson)
                                        },
                                        onPageActionStepExecuted = { idx, name, status ->
                                            onPageActionStepExecuted?.invoke(idx, name, status)
                                        },
                                        onMidPageInfoExtracted = { key, value ->
                                            onMidPageInfoExtracted?.invoke(key, value)
                                        },
                                        onTemplateGenerated = { tmplJson ->
                                            onTemplateGenerated?.invoke(tmplJson)
                                        },
                                        onLockerDetected = { lockerUrl, lockerId, isTriggered, count ->
                                            tabItem.detectedLockerUrl = lockerUrl
                                            tabItem.detectedLockerId = lockerId
                                            tabItem.isLockerTriggered = isTriggered
                                            onLockerDetectedWithCount?.invoke(lockerUrl, lockerId, isTriggered, count)
                                        },
                                        onLockerStatus = { status ->
                                            tabItem.lockerStatusMsg = status
                                        },
                                        onLockerOffersExtracted = { jsonPayload ->
                                            val isManualScanActive = isScanningLocker || System.currentTimeMillis() < lockerScanRequestedUntil
                                            onLockerOffersExtracted?.invoke(jsonPayload) { found, newSaved, skippedDup, lockerId ->
                                                if (isManualScanActive && (found > 0 || newSaved > 0)) {
                                                    val msg = if (newSaved > 0) {
                                                        "✅ تم كشف اللوكر (#$lockerId): حُفظ $newSaved نص جديد في Offer Click (وتُجاهل $skippedDup مكرر)"
                                                    } else {
                                                        "ℹ️ تم فحص $found عروض باللوكر (#$lockerId): جميعها محفوظة مسبقاً بدون تكرار"
                                                    }
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        onHumanActionStatus = { statusAr ->
                                            onHumanActionStatus?.invoke(statusAr)
                                        },
                                        onShieldTelemetry = { jsonPayload ->
                                            onShieldTelemetry?.invoke(jsonPayload)
                                        },
                                        isLockerScanRequestedProvider = {
                                            isScanningLocker || System.currentTimeMillis() < lockerScanRequestedUntil
                                        },
                                        isRunningProvider = {
                                            automationState.isRunning ||
                                                tabItem.isOfferTab ||
                                                !automationState.activeClickText.isNullOrBlank() ||
                                                (autoPilotFormsEnabled && tabItem.url.startsWith("http"))
                                        },
                                        activeClickTextProvider = { activeClickText ?: automationState.activeClickText },
                                        clickTextsProvider = { clickTexts }
                                    ),
                                    "AndroidBridge"
                                )

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        val p = newProgress / 100f
                                        tabItem.progress = p
                                        tabItem.isLoading = newProgress < 100
                                        if (tabItem.id == activeTabId) {
                                            webProgress = p
                                            isPageLoading = newProgress < 100
                                        }
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        super.onReceivedTitle(view, title)
                                        if (!title.isNullOrBlank() && !title.startsWith("http")) {
                                            tabItem.title = title
                                        }
                                    }

                                    override fun onPermissionRequest(request: PermissionRequest?) {
                                        try { request?.deny() } catch (e: Exception) {}
                                    }

                                    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                                        try { callback?.invoke(origin, true, false) } catch (e: Exception) {}
                                    }

                                    // Containment: never let page JS dialogs stall automation — auto-resolve + log
                                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                                        try { result?.confirm() } catch (_: Exception) {}
                                        onConsoleError?.invoke("⚠️ [حوار JS]: تنبيه أُغلق تلقائياً على ${(url ?: "").take(60)}: ${(message ?: "").take(80)}")
                                        return true
                                    }
                                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                                        try { result?.confirm() } catch (_: Exception) {}
                                        onConsoleError?.invoke("⚠️ [حوار JS]: تأكيد قُبِل تلقائياً على ${(url ?: "").take(60)}: ${(message ?: "").take(80)}")
                                        return true
                                    }
                                    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult?): Boolean {
                                        try { result?.confirm("") } catch (_: Exception) {}
                                        onConsoleError?.invoke("⚠️ [حوار JS]: إدخال أُجيب فارغاً تلقائياً على ${(url ?: "").take(60)}")
                                        return true
                                    }

                                    // Page console errors → app logs (throttled: 1 per 8s, automation only)
                                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                        try {
                                            val msg = consoleMessage
                                            if (msg != null && msg.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR &&
                                                automationState.isRunning
                                            ) {
                                                val now = System.currentTimeMillis()
                                                if (now - lastConsoleLogMs > 8000) {
                                                    lastConsoleLogMs = now
                                                    onConsoleError?.invoke("🔴 [كونسول الصفحة]: ${(msg.message() ?: "").take(140)} @ ${(msg.sourceId() ?: "").take(48)}:${msg.lineNumber()}")
                                                }
                                            }
                                        } catch (_: Exception) {}
                                        return super.onConsoleMessage(consoleMessage)
                                    }

                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message?
                                     ): Boolean {
                                         val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                         val context = view?.context ?: return false
                                         val popupRelay = WebView(context)
                                         var popupHandled = false

                                         fun openPopup(rawUrl: String?): Boolean {
                                             val trimmed = rawUrl?.trim().orEmpty()
                                             if (trimmed.isBlank() || trimmed == "about:blank" || popupHandled) return false
                                             val resolved = MainActivity.CustomWebViewClient.resolveSpecialSchemeUrl(trimmed) ?: trimmed
                                             val uri = try { android.net.Uri.parse(resolved) } catch (_: Exception) { null } ?: return true
                                             val scheme = uri.scheme?.lowercase().orEmpty()
                                             if (scheme != "http" && scheme != "https") return true
                                             popupHandled = true
                                             val parentUrl = view?.url.orEmpty().lowercase()
                                             val isStrictRedirectBridge = parentUrl.contains("/redirect") ||
                                                 parentUrl.contains("/track") ||
                                                 Regex("/\\d+/\\d+$").containsMatchIn(parentUrl)
                                             if (isStrictRedirectBridge && activeTab.detectedLockerUrl == null && !automationState.lockerDetectedOnPage) {
                                                 view?.post {
                                                     loadUrlSmart(view, tabItem, uri.toString(), view.url)
                                                 }
                                             } else {
                                                 openPopupInNewTab(uri.toString())
                                             }
                                             popupRelay.post { try { popupRelay.destroy() } catch (_: Exception) {} }
                                             return true
                                         }

                                          popupRelay.settings.apply {
                                              javaScriptEnabled = true
                                              domStorageEnabled = true
                                              databaseEnabled = true
                                              allowFileAccess = false
                                              allowContentAccess = false
                                              @Suppress("DEPRECATION")
                                              allowFileAccessFromFileURLs = false
                                              @Suppress("DEPRECATION")
                                              allowUniversalAccessFromFileURLs = false
                                              userAgentString = view?.settings?.userAgentString ?: (if (isMobileUaMode) defaultMobileUa else defaultDesktopUa)
                                              mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                          }
                                         try {
                                             val method = WebSettingsCompat::class.java.getMethod(
                                                 "setRequestedWithHeaderOriginAllowList",
                                                 WebSettings::class.java,
                                                 Set::class.java
                                             )
                                             method.invoke(null, popupRelay.settings, emptySet<String>())
                                         } catch (_: Throwable) {}
                                          if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                              try {
                                                  val popupUa = popupRelay.settings.userAgentString.ifBlank { defaultMobileUa }
                                                  // Full shield stack parity with main tabs (no unshielded window, even transient)
                                                  WebViewCompat.addDocumentStartJavaScript(
                                                      popupRelay,
                                                      AutomationScriptBuilder.buildAntiDetectionScript(
                                                          proxyIp = effectiveWebRtcIp,
                                                          webrtcMode = effectiveWebRtcMode,
                                                          timezone = extractedInfo.timezone,
                                                          language = extractedInfo.language,
                                                          latitude = extractedInfo.latitude,
                                                          longitude = extractedInfo.longitude,
                                                          userAgent = popupUa,
                                                          fingerprintSeed = automationState.fingerprintSeed
                                                      ),
                                                      setOf("*")
                                                  )
                                                  WebViewCompat.addDocumentStartJavaScript(
                                                      popupRelay,
                                                      AutomationScriptBuilder.buildDeviceBridgeScript(
                                                          latitude = extractedInfo.latitude,
                                                          longitude = extractedInfo.longitude,
                                                          userAgent = popupUa,
                                                          fingerprintSeed = automationState.fingerprintSeed,
                                                          language = extractedInfo.language
                                                      ),
                                                      setOf("*")
                                                  )
                                                  if (settings.trackerShieldEnabled && settings.trackerMode != "allow") {
                                                      WebViewCompat.addDocumentStartJavaScript(
                                                          popupRelay,
                                                          AutomationScriptBuilder.buildTrackerDeceptionScript(),
                                                          setOf("*")
                                                      )
                                                  }
                                                  WebViewCompat.addDocumentStartJavaScript(
                                                      popupRelay,
                                                      AutomationScriptBuilder.buildTimezoneScript(extractedInfo.timezone, extractedInfo.language),
                                                      setOf("*")
                                                  )
                                                  WebViewCompat.addDocumentStartJavaScript(
                                                      popupRelay,
                                                      AutomationScriptBuilder.buildCanvasNoiseScript(),
                                                      setOf("*")
                                                  )
                                              } catch (_: Exception) {}
                                          }
                                          // Minimal bridge (telemetry + running-state only; automation acts in tabs)
                                          try {
                                              popupRelay.addJavascriptInterface(
                                                  WebAppInterface(
                                                      onCompleted = { _, _ -> },
                                                      isRunningProvider = {
                                                          automationState.isRunning || tabItem.isOfferTab
                                                      },
                                                      activeClickTextProvider = { activeClickText ?: automationState.activeClickText },
                                                      clickTextsProvider = { clickTexts }
                                                  ),
                                                  "AndroidBridge"
                                              )
                                          } catch (_: Exception) {}
                                          // Popup containment: auto-dismiss dialogs, deny permissions, no geolocation grant
                                          popupRelay.webChromeClient = object : WebChromeClient() {
                                              override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                                                  try { result?.confirm() } catch (_: Exception) {}
                                                  return true
                                              }
                                              override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                                                  try { result?.confirm() } catch (_: Exception) {}
                                                  return true
                                              }
                                              override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult?): Boolean {
                                                  try { result?.confirm("") } catch (_: Exception) {}
                                                  return true
                                              }
                                              override fun onPermissionRequest(request: PermissionRequest?) {
                                                  try { request?.deny() } catch (_: Exception) {}
                                              }
                                          }
                                         try {
                                             android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(popupRelay, true)
                                         } catch (e: Exception) {}

                                          popupRelay.webViewClient = object : MainActivity.CustomWebViewClient(
                                              proxyUserProvider = { settings.proxyUser },
                                              proxyPassProvider = { settings.proxyPass },
                                              acceptLanguageProvider = { extractedInfo.language },
                                              bridgePortProvider = { com.example.util.LocalSocks5HttpBridge.localPort },
                                              strictModeProvider = { automationState.intelRiskLevel != "low" },
                                              isHostileHost = { h -> onIsHostileHost?.invoke(h) == true },
                                              onSurveillanceBlocked = { d -> onSurveillanceBlocked?.invoke(d) },
                                              trackerBlockProvider = { settings.trackerShieldEnabled && settings.trackerMode != "allow" && settings.surveillanceBlocking },
                                              xhrCorrectionProvider = { settings.xhrCorrection }
                                          ) {
                                             override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                                                 openPopup(request?.url?.toString())

                                             @Deprecated("Deprecated in Java")
                                             override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                                                 openPopup(url)

                                             override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                 openPopup(url)
                                             }
                                         }
                                         transport.webView = popupRelay
                                         resultMsg.sendToTarget()
                                         popupRelay.postDelayed({
                                             if (!popupHandled) {
                                                 try { popupRelay.destroy() } catch (_: Exception) {}
                                             }
                                         }, 15_000L)
                                         return true
                                     }
                                }

                                 webViewClient = object : MainActivity.CustomWebViewClient(
                                     proxyUserProvider = { settings.proxyUser },
                                     proxyPassProvider = { settings.proxyPass },
                                     acceptLanguageProvider = { extractedInfo.language },
                                     bridgePortProvider = { com.example.util.LocalSocks5HttpBridge.localPort },
                                     strictModeProvider = { automationState.intelRiskLevel != "low" },
                                     isHostileHost = { h -> onIsHostileHost?.invoke(h) == true },
                                     onSurveillanceBlocked = { d -> onSurveillanceBlocked?.invoke(d) },
                                     trackerBlockProvider = { settings.trackerShieldEnabled && settings.trackerMode != "allow" && settings.surveillanceBlocking },
                                     xhrCorrectionProvider = { settings.xhrCorrection },
                                     onRenderProcessGoneCallback = { didCrash -> handleRendererGone(tabItem, didCrash) },
                                     onMainFrameFinalUrl = { finalUrl ->
                                         // Header-corrected load followed redirects: sync tab URL with reality
                                         tabItem.webView?.post {
                                             tabItem.url = finalUrl
                                             if (tabItem.id == activeTabId) {
                                                 currentDisplayUrl = finalUrl
                                                 urlInput = finalUrl
                                             }
                                         }
                                     },
                                     onPageStartedCallback = { view, url, _ ->
                                        tabItem.navigationSeq++
                                        tabItem.isLoading = true
                                        tabItem.lastError = null
                                        url?.let {
                                            tabItem.url = it
                                            if (tabItem.id == activeTabId) {
                                                currentDisplayUrl = it
                                                urlInput = it
                                                isPageLoading = true
                                            }
                                        }

                                        // WebRTC and anti-detection script injection
                                        val viewUa = view?.settings?.userAgentString.orEmpty()
                                        view?.evaluateJavascript(
                                            AutomationScriptBuilder.buildAntiDetectionScript(
                                                proxyIp = effectiveWebRtcIp,
                                                webrtcMode = effectiveWebRtcMode,
                                                timezone = extractedInfo.timezone,
                                                language = extractedInfo.language,
                                                latitude = extractedInfo.latitude,
                                                longitude = extractedInfo.longitude,
                                                userAgent = viewUa,
                                                fingerprintSeed = automationState.fingerprintSeed
                                            ),
                                            null
                                        )
                                        view?.evaluateJavascript(AutomationScriptBuilder.buildTimezoneScript(extractedInfo.timezone, extractedInfo.language), null)
                                        view?.evaluateJavascript(AutomationScriptBuilder.buildCanvasNoiseScript(), null)
                                        view?.evaluateJavascript(AutomationScriptBuilder.buildSmartlinkRedirectHelperScript(), null)

                                        scripts.filter { it.enabled && it.timing == "before" }.forEach { s ->
                                            view?.evaluateJavascript(s.code, null)
                                        }
                                    },
                                    onPageFinishedCallback = { view, url ->
                                        tabItem.isLoading = false
                                        url?.let {
                                            tabItem.url = it
                                            if (tabItem.id == activeTabId) {
                                                currentDisplayUrl = it
                                                urlInput = it
                                                isPageLoading = false
                                            }
                                        }

                                        // Reinforced anti-detection & Smartlink redirect script injection
                                        val finishUa = view?.settings?.userAgentString.orEmpty()
                                        view?.evaluateJavascript(
                                            AutomationScriptBuilder.buildAntiDetectionScript(
                                                proxyIp = effectiveWebRtcIp,
                                                webrtcMode = effectiveWebRtcMode,
                                                timezone = extractedInfo.timezone,
                                                language = extractedInfo.language,
                                                latitude = extractedInfo.latitude,
                                                longitude = extractedInfo.longitude,
                                                userAgent = finishUa,
                                                fingerprintSeed = automationState.fingerprintSeed
                                            ),
                                            null
                                        )
                                        view?.evaluateJavascript(AutomationScriptBuilder.buildSmartlinkRedirectHelperScript(), null)

                                        // Multi-Redirect Settle Window (850ms):
                                        // Wait 850ms after onPageFinished before injecting heavy automation/locker/form-fill scripts.
                                        // If another redirect hop starts within this window (navigationSeq increments or isLoading becomes true),
                                        // we skip this intermediate hop so multi-redirect chains complete cleanly without interruption!
                                        val finishedSeq = tabItem.navigationSeq
                                        val shouldAutoInteract = automationState.isRunning || tabItem.isOfferTab || !automationState.activeClickText.isNullOrBlank() || (autoPilotFormsEnabled && !url.isNullOrBlank() && url != "about:blank")
                                        if (shouldAutoInteract && tabItem.id == activeTabId) {
                                            view?.postDelayed({
                                                try {
                                                    if (tabItem.navigationSeq != finishedSeq || tabItem.isLoading || tabItem.id != activeTabId) {
                                                        return@postDelayed
                                                    }
                                                    val settledUrl = view.url.orEmpty().ifBlank { url.orEmpty() }
                                                    if (settledUrl.isBlank() || settledUrl == "about:blank") {
                                                        return@postDelayed
                                                    }

                                                    val effClickText = activeClickText ?: automationState.activeClickText
                                                    view.evaluateJavascript("window._cpaIsOfferTab = ${tabItem.isOfferTab};", null)

                                                    // Run page analyzer once the redirect chain has settled
                                                    view.evaluateJavascript(
                                                        AutomationScriptBuilder.buildPageAnalyzerScript(clickTexts, effClickText),
                                                        null
                                                    )

                                                    if (!tabItem.isOfferTab) {
                                                        // Activate content locker & auto-click offer if a locker is on this page
                                                        view.evaluateJavascript(
                                                            AutomationScriptBuilder.buildCpaLockerDetectorAndActivatorScript(
                                                                customId = settings.cpaLockerDefaultId.ifBlank { "1741238" },
                                                                forceTrigger = settings.cpaLockerAutoTrigger,
                                                                forceInjectIfMissing = settings.cpaLockerAutoInjectIfMissing
                                                            ),
                                                            null
                                                        )
                                                        view.evaluateJavascript(
                                                            AutomationScriptBuilder.buildLockerOfferAutoClickScript(
                                                                clickTexts = clickTexts,
                                                                activeTargetText = effClickText,
                                                                selectionStrategy = settings.offerSelectionStrategy,
                                                                openInNewTab = settings.offerClickOpenInNewTab
                                                            ),
                                                            null
                                                        )
                                                    }

                                                    // Run smart form fill, survey solver, and CTA progression
                                                    val effectiveCats = automationState.activeTaskCategories.ifBlank {
                                                        "Offer Click, Survey / Quiz, Email Submit, Lead Gen Form, Terms Agreement, Skip Upsells, Confirmation"
                                                    }
                                                    view.evaluateJavascript(
                                                        AutomationScriptBuilder.buildSmartFormFillScript(
                                                            identity = identity,
                                                            categories = effectiveCats,
                                                            clickTexts = clickTexts,
                                                            activeClickText = effClickText,
                                                            extractedInfo = extractedInfo,
                                                            brainMode = automationState.brainExecutionMode,
                                                            personaFactsJson = automationState.personaFactsJson,
                                                            humanSimulationEnabled = settings.humanSimulationEnabled,
                                                            humanSimulationProfile = settings.humanSimulationProfile,
                                                            stageTransitionWaitMinSec = settings.stageTransitionWaitMinSec,
                                                            stageTransitionWaitMaxSec = settings.stageTransitionWaitMaxSec,
                                                            humanTypingSimulation = settings.humanTypingSimulation,
                                                            humanPointerAndTouchPaths = settings.humanPointerAndTouchPaths,
                                                            humanReadingPauseBeforeAction = settings.humanReadingPauseBeforeAction
                                                        ),
                                                        null
                                                    )
                                                    if (settings.humanSimulationEnabled && settings.humanScrollAndGazeSimulation) {
                                                        view.evaluateJavascript(AutomationScriptBuilder.buildAwareHumanSimulationScript(), null)
                                                    } else {
                                                        view.evaluateJavascript(AutomationScriptBuilder.buildHumanBehaviorScript(), null)
                                                    }

                                                    // Second delayed pass at +2.0s for dynamically rendered SPA forms/surveys
                                                    view.postDelayed({
                                                        try {
                                                            if (tabItem.navigationSeq != finishedSeq || tabItem.isLoading) return@postDelayed
                                                            view.evaluateJavascript(
                                                                AutomationScriptBuilder.buildPageAnalyzerScript(clickTexts, effClickText),
                                                                null
                                                            )
                                                            view.evaluateJavascript(
                                                                AutomationScriptBuilder.buildSmartFormFillScript(
                                                                    identity = identity,
                                                                    categories = effectiveCats,
                                                                    clickTexts = clickTexts,
                                                                    activeClickText = effClickText,
                                                                    extractedInfo = extractedInfo,
                                                                    brainMode = automationState.brainExecutionMode,
                                                                    personaFactsJson = automationState.personaFactsJson,
                                                                    humanSimulationEnabled = settings.humanSimulationEnabled,
                                                                    humanSimulationProfile = settings.humanSimulationProfile,
                                                                    stageTransitionWaitMinSec = settings.stageTransitionWaitMinSec,
                                                                    stageTransitionWaitMaxSec = settings.stageTransitionWaitMaxSec,
                                                                    humanTypingSimulation = settings.humanTypingSimulation,
                                                                    humanPointerAndTouchPaths = settings.humanPointerAndTouchPaths,
                                                                    humanReadingPauseBeforeAction = settings.humanReadingPauseBeforeAction
                                                                ),
                                                                null
                                                            )
                                                        } catch (_: Exception) {}
                                                    }, 2000L)

                                                    val keywords = listOf(
                                                        "thank you for completing",
                                                        "thank you for your submission",
                                                        "submission received",
                                                        "registration complete",
                                                        "survey completed",
                                                        "responses recorded",
                                                        "successfully submitted",
                                                        "check your inbox",
                                                        "entry confirmed"
                                                    )
                                                    view.evaluateJavascript(AutomationScriptBuilder.buildCompletionDetectorScript(keywords), null)

                                                    if (tabItem.isOfferTab && !tabItem.hasSimulatedInteraction && settings.offerClickAutoSimulateHuman) {
                                                        tabItem.hasSimulatedInteraction = true
                                                        view.evaluateJavascript(
                                                            AutomationScriptBuilder.buildHumanInteractionOnOfferPageScript(
                                                                tabId = tabItem.id,
                                                                stayDurationSec = settings.offerClickStayDurationSec
                                                            ),
                                                            null
                                                        )
                                                    }
                                                } catch (_: Exception) {}
                                            }, 850L)
                                        }

                                        scripts.filter { it.enabled && it.timing == "after" }.forEach { s ->
                                            view?.evaluateJavascript(s.code, null)
                                        }
                                    },
                                    onErrorCallback = { view, request, error ->
                                        if (request?.isForMainFrame == true) {
                                            val failingUrl = request.url?.toString().orEmpty()
                                            val resolvedSpecial = MainActivity.CustomWebViewClient.resolveSpecialSchemeUrl(failingUrl)
                                            val errDesc = error?.description?.toString() ?: "ERR_CONNECTION_REFUSED"
                                            if (!resolvedSpecial.isNullOrBlank() && (resolvedSpecial.startsWith("http://") || resolvedSpecial.startsWith("https://")) && resolvedSpecial != failingUrl) {
                                                tabItem.lastError = null
                                                view?.post { loadUrlSmart(view, tabItem, resolvedSpecial, view.url) }
                                            } else if (errDesc.contains("TOO_MANY_REDIRECTS", true) && retriedEmptyTrackerUrl != failingUrl) {
                                                retriedEmptyTrackerUrl = failingUrl
                                                tabItem.lastError = null
                                                try {
                                                    android.webkit.CookieManager.getInstance().removeSessionCookies(null)
                                                } catch (_: Exception) {}
                                                val retryTarget = failingUrl.ifBlank { tabItem.url }
                                                view?.post {
                                                    if (retryTarget.isNotBlank() && retryTarget != "about:blank") {
                                                        loadUrlSmart(view, tabItem, retryTarget)
                                                    }
                                                }
                                            } else if ((WebProxyManager.isProxyActive || settings.proxyEnabled) && (
                                                    errDesc.contains("PROXY", true) ||
                                                    errDesc.contains("SOCKS", true) ||
                                                    errDesc.contains("TUNNEL", true) ||
                                                    errDesc.contains("TIMED_OUT", true) ||
                                                    errDesc.contains("RESET", true) ||
                                                    errDesc.contains("REFUSED", true) ||
                                                    errDesc.contains("CLOSED", true) ||
                                                    errDesc.contains("EMPTY_RESPONSE", true) ||
                                                    errDesc.contains("UNREACHABLE", true) ||
                                                    errDesc.contains("NAME_NOT_RESOLVED", true)
                                                )
                                            ) {
                                                val retryTarget = failingUrl.ifBlank { tabItem.url }
                                                if (retriedProxyUrl != retryTarget && settings.proxyHost.isNotBlank()) {
                                                    // Retry ONCE through the active proxy bridge without ever disabling proxy
                                                    retriedProxyUrl = retryTarget
                                                    tabItem.lastError = null
                                                    WebProxyManager.applyProxy(
                                                        context,
                                                        true,
                                                        settings.proxyHost,
                                                        settings.proxyPort.toIntOrNull(),
                                                        settings.proxyType,
                                                        settings.proxyUser,
                                                        settings.proxyPass
                                                    ) { _, _ ->
                                                        view?.postDelayed({
                                                            if (retryTarget.isNotBlank() && retryTarget != "about:blank") {
                                                                loadUrlSmart(view, tabItem, retryTarget)
                                                            } else {
                                                                view.reload()
                                                            }
                                                        }, 350L)
                                                    }
                                                } else {
                                                    // Strict Proxy Kill-Switch: Block page load rather than falling back to real IP
                                                    tabItem.isLoading = false
                                                    if (tabItem.id == activeTabId) isPageLoading = false
                                                    tabItem.lastError = "🛡️ تم حظر الاتصال المباشر لمنع تسريب IP الحقيقي (فشل البروكسي: $errDesc)"
                                                }
                                            } else {
                                                tabItem.isLoading = false
                                                if (tabItem.id == activeTabId) isPageLoading = false
                                                tabItem.lastError = errDesc
                                            }
                                        }
                                    }
                                ) {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val uri = request?.url ?: return false
                                        val rawUrl = uri.toString()
                                        val scheme = uri.scheme?.lowercase() ?: ""

                                        if (MainActivity.CustomWebViewClient.isWebRtcStunTurnTraffic(uri, request.isForMainFrame)) {
                                            return true
                                        }

                                        if (scheme == "http" || scheme == "https") {
                                            return false
                                        }

                                        // Resolve intent://, android-app://, and market:// redirects used by Monetag (omg10.com) and CPA ad networks
                                        val resolvedUrl = MainActivity.CustomWebViewClient.resolveSpecialSchemeUrl(rawUrl)
                                        if (!resolvedUrl.isNullOrBlank()) {
                                            if (request.isForMainFrame) {
                                                val headers = mutableMapOf("Referer" to (view?.url?.takeIf { it.startsWith("http") } ?: "https://www.google.com/"))
                                                extractedInfo.language.trim().takeIf { it.matches(Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$")) }?.let {
                                                    headers["Accept-Language"] = it
                                                }
                                                view?.post { view.loadUrl(resolvedUrl, headers) }
                                            }
                                            return true
                                        }

                                        return try {
                                            if (request.isForMainFrame && (scheme == "tel" || scheme == "mailto" || scheme == "sms")) {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                                view?.context?.startActivity(intent)
                                                true
                                            } else {
                                                true
                                            }
                                        } catch (e: Exception) {
                                            true
                                        }
                                    }
                                }

                                tabItem.webView = this
                                if (tabItem.url.isNotBlank() && tabItem.url != "about:blank") {
                                    loadUrlSmart(this, tabItem, tabItem.url)
                                }
                            }
                        },
                        update = { webView ->
                            tabItem.webView = webView
                            if (isThisTabActive) {
                                webView.visibility = View.VISIBLE
                                webView.onResume()
                                if (tabItem.url.isNotBlank() && tabItem.url != "about:blank" && webView.url.isNullOrBlank() && !tabItem.isLoading) {
                                    loadUrlSmart(webView, tabItem, tabItem.url)
                                }
                            } else {
                                webView.visibility = View.GONE
                                // Keep the Main Locker Tab alive (unpaused) while an Offer Tab is open so the Locker's background lead-check AJAX polling continues!
                                val keepAliveForLockerPolling = !tabItem.isOfferTab && tabs.any { it.isOfferTab }
                                if (keepAliveForLockerPolling) {
                                    webView.onResume()
                                } else {
                                    webView.onPause()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    } // key(tabItem.id, crashGen)
                }
            }

            // Standby Overlay when browser is idle and no page is loaded in active tab
            if (!automationState.isRunning && (currentDisplayUrl.isBlank() || currentDisplayUrl == "about:blank")) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CpaBg.copy(alpha = 0.96f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(CpaCardElevated)
                                .border(1.dp, CpaBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WebAsset,
                                contentDescription = null,
                                tint = CpaPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "SMART UNIVERSAL BROWSER",
                            color = CpaText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تتم إضافة وتشغيل جميع الروابط بذكاء وتوجيه تلقائي كامل من شاشة المهام (Tasks).",
                            color = CpaTextDim,
                            fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { onGoToTasks?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = CpaPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🚀 فتح شاشة المهام (Tasks) لإضافة وتشغيل الروابط", color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
