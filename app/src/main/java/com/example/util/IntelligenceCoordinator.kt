package com.example.util

import android.util.Log
import com.example.data.model.AutomationState
import com.example.data.model.ExtractedInfo
import com.example.data.model.GeneratedIdentity
import com.example.service.IdentityService
import com.example.service.SmartAutomationBrain
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Central Intelligence Coordinator — ذكاء يربط كل ذكاء.
 *
 * الجسر (بروكسي) ⇄ الهوية (بصمة) ⇄ العقل (سلوك) ⇄ المهام
 * كل طبقة تحلل نفسها، وترسل خلاصتها للمنسق، والمنسق يوزع أوامر تكيفية للكل.
 */

data class ProxyHealthReport(
    val isHealthy: Boolean,
    val exitIp: String,
    val pingMs: Long,
    val protocol: String,
    val failureReason: String? = null,
    val consecutiveFailures: Int = 0,
    val lastRotationMs: Long = 0L,
    // ذكاء مدير البروكسي: معدل نجاح تطبيق نفس البروكسي على WebView
    val upstreamApplyRate: Double = 0.5
)

data class IdentityFingerprintReport(
    val userAgent: String,
    val canvasConsistency: Boolean,
    val webglConsistency: Boolean,
    val geoConsistency: Boolean,
    val languageIpMatch: Boolean,
    val lastRotationMs: Long = 0L
)

data class AutomationBehaviorReport(
    val stage: Int,
    val successRate: Double,
    val stuckCount: Int,
    val adaptiveDelay: Long,
    val escapeActionsTaken: Int,
    val humanizationScore: Int,
    val lastOptimizationMs: Long = 0L
)

data class CrossLayerInsight(
    val timestamp: Long = System.currentTimeMillis(),
    val proxyImpactOnAutomation: Double = 0.0,
    val identityConsistencyScore: Double = 0.5,
    val behavioralPattern: String = "mixed_pattern",
    val recommendedActions: List<String> = listOf("monitor_patterns"),
    val riskLevel: String = "low",
    val predictedOutcome: String = "continue_optimized"
)

class IntelligenceCoordinator(
    val proxyBridge: LocalSocks5HttpBridge,
    private val proxyManager: WebProxyManager,
    private val identityService: IdentityService,
    private val automationBrain: SmartAutomationBrain,
    private val taskDao: Any,
    private val host: Any
) {
    @Volatile
    var lastCrossLayerInsight: CrossLayerInsight = CrossLayerInsight()
        private set

    @Volatile
    var sessionAnomalyCount: Int = 0
        private set

    /** يُستدعى مع كل رؤية جديدة — يربط المنسق بالواجهة والحفظ */
    @Volatile
    var onInsight: ((CrossLayerInsight) -> Unit)? = null

    /**
     * مُصدِر السجل داخل التطبيق (يضبطه AppViewModel على addLog).
     * كل ما يفعله المنسق وما سيفعله يظهر في شاشة Logs — مع نسخة في logcat.
     */
    @Volatile
    var logEmitter: ((level: String, message: String) -> Unit)? = null

    private fun ilog(level: String, message: String) {
        val prefixed = "🧠 [استخبارات] $message"
        when (level) {
            "warning" -> Log.w("IntelligenceCoordinator", message)
            "error" -> Log.e("IntelligenceCoordinator", message)
            "success" -> Log.i("IntelligenceCoordinator", "SUCCESS: $message")
            else -> Log.i("IntelligenceCoordinator", message)
        }
        try { logEmitter?.invoke(level, prefixed) } catch (_: Exception) {}
    }

    // ذاكرة منع الإزعاج: نسجل التفاصيل الكاملة فقط عند التغير أو الخطر
    @Volatile
    private var lastLoggedRisk: String = ""
    @Volatile
    private var lastLoggedActions: List<String> = emptyList()
    @Volatile
    private var cycleCount: Int = 0

    // مزودات الحالة الحية من AppViewModel (تُربط بعد الإنشاء)
    private var identityProvider: (() -> GeneratedIdentity)? = null
    private var automationStateProvider: (() -> AutomationState)? = null
    private var geoProvider: (() -> ExtractedInfo)? = null
    private var userAgentProvider: (() -> String)? = null

    @Volatile
    private var analysisRunning = true

    private val analysisThread = thread(name = "Intel-Coordinator", isDaemon = true) {
        while (analysisRunning) {
            try {
                Thread.sleep(5000)
                if (analysisRunning) performRecursiveAnalysis()
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                ilog("error", "خطأ في خيط التحليل: ${e.message}")
            }
        }
    }

    fun attach(
        identity: () -> GeneratedIdentity,
        automationState: () -> AutomationState,
        geo: () -> ExtractedInfo,
        userAgent: () -> String
    ) {
        identityProvider = identity
        automationStateProvider = automationState
        geoProvider = geo
        userAgentProvider = userAgent
    }

    fun stop() {
        analysisRunning = false
        try { analysisThread.interrupt() } catch (_: Exception) {}
    }

    // ── 1) ذكاء الجسر × مدير البروكسي: صحة النفق + نجاح التطبيق + تأثير على الأتمتة ──
    fun analyzeProxyRecursive(): ProxyHealthReport {
        val snap = proxyBridge.healthSnapshot()
        val fails = proxyBridge.peekFailureCount()
        // ربط ذكاء المدير: معدل نجاح تطبيق نفس البروكسي على WebView
        val (upHost, upPort) = proxyManager.currentUpstream()
        val applyRate = if (upHost.isBlank() || upPort <= 0) 0.5
        else proxyManager.upstreamSuccessRate(upHost, upPort)
        // تغذية عكسية للجسر: بروتوكوله المفضل حسب فئة الهدف
        val learnedProto = snap.remoteType

        val healthy = if (!snap.isConfigured) {
            true // الوضع المباشر ليس فشلاً — فقط بلا حماية بروكسي
        } else {
            fails < 5 && applyRate > 0.2
        }

        val reason = when {
            !snap.isConfigured -> "No upstream proxy configured (direct mode)"
            fails >= 5 -> "Critical upstream failure ($fails consecutive)"
            applyRate <= 0.2 -> "WebView apply failing (rate ${(applyRate * 100).toInt()}%)"
            fails >= 3 -> "Degraded performance - considering rotation"
            else -> null
        }

        // ذكاء عكسي: غذّ العقل والهوية بمشكلة البروكسي ليوجهوا قراراتهم
        if (!healthy && reason != null && snap.isConfigured) {
            automationBrain.recordProxyIssue(reason, learnedProto)
            identityService.recordProxyIssue(reason)
        }

        return ProxyHealthReport(
            isHealthy = healthy,
            exitIp = if (snap.isConfigured) "configured" else "direct",
            pingMs = detectPingTrend(),
            protocol = learnedProto,
            failureReason = reason,
            consecutiveFailures = fails,
            lastRotationMs = snap.lastRotationMs,
            upstreamApplyRate = applyRate
        )
    }

    // ── 2) ذكاء الهوية: اتساق UA + لغة + بصمة مع الجغرافيا ──
    fun analyzeIdentityRecursive(): IdentityFingerprintReport {
        val geo = geoProvider?.invoke() ?: ExtractedInfo()
        val ua = userAgentProvider?.invoke().orEmpty()
        val auto = automationStateProvider?.invoke()

        val uaGeoMatch = if (ua.isBlank()) {
            true // لا UA بعد = لا حكم
        } else {
            identityService.isUaGeoConsistent(ua, geo.countryCode.ifBlank { "US" })
        }

        val expectedLangPrefix = when (geo.countryCode.trim().uppercase()) {
            "SA", "AE", "EG", "QA", "KW", "JO", "IQ", "SY", "LB", "PS", "YE", "MA", "DZ", "TN" -> "ar"
            "FR" -> "fr"
            "DE" -> "de"
            "ES" -> "es"
            "JP" -> "ja"
            else -> "en"
        }
        val languageIpMatch = geo.language.isBlank() ||
            geo.language.lowercase().startsWith(expectedLangPrefix)

        // اتساق البصمة: seed مستقر + لم يدور منذ زمن قصير جداً
        val seed = auto?.fingerprintSeed ?: 0L
        val canvasOk = seed != 0L
        val webglOk = seed != 0L
        val rotationGap = System.currentTimeMillis() - identityService.lastRotationMs
        val tooFrequent = identityService.lastRotationMs != 0L && rotationGap < 60_000L

        if ((!uaGeoMatch || !languageIpMatch) && !tooFrequent) {
            identityService.scheduleIdentityRotation()
        }

        return IdentityFingerprintReport(
            userAgent = ua.ifBlank { "(not set yet)" },
            canvasConsistency = canvasOk,
            webglConsistency = webglOk,
            geoConsistency = uaGeoMatch && languageIpMatch,
            languageIpMatch = languageIpMatch,
            lastRotationMs = identityService.lastRotationMs
        )
    }

    // ── 3) ذكاء السلوك: مرحلة + تعلق + أنسنة + تأخير تكيفي ──
    fun analyzeAutomationRecursive(): AutomationBehaviorReport {
        val state = automationStateProvider?.invoke() ?: AutomationState()

        val adaptiveDelay = when (state.funnelStageIndex) {
            1 -> 8000L + Random.nextLong(5000L)
            2 -> 6000L + Random.nextLong(3000L)
            3 -> 5000L + Random.nextLong(2000L)
            4 -> 7000L + Random.nextLong(4000L)
            5 -> 10000L + Random.nextLong(6000L)
            else -> 5000L
        } + automationBrain.humanizationBoost.toLong() * 1000L

        val title = state.activeDirectiveTitle
        val escapeActions = when {
            title.contains("captcha", ignoreCase = true) -> 1
            title.contains("escape", ignoreCase = true) -> 1
            title.contains("retry", ignoreCase = true) -> 1
            else -> 0
        } + if (automationBrain.escapeRequested) 1 else 0

        return AutomationBehaviorReport(
            stage = state.funnelStageIndex,
            successRate = calculateSuccessRate(state),
            stuckCount = state.stuckCount,
            adaptiveDelay = adaptiveDelay,
            escapeActionsTaken = escapeActions,
            humanizationScore = calculateHumanizationScore(state),
            lastOptimizationMs = System.currentTimeMillis()
        )
    }

    /** فحص ما قبل التشغيل: يجمع التقارير الثلاثة ويخرج رؤية واحدة ويوزعها + سجل كامل */
    fun runPreflight(): CrossLayerInsight {
        ilog("info", "🔍 بدء الفحص الشامل قبل التشغيل: فحص البروكسي + الهوية + السلوك...")
        val proxy = analyzeProxyRecursive()
        ilog(
            "info",
            "🌐 البروكسي: صحي=${if (proxy.isHealthy) "نعم ✅" else "لا ❌"} | بروتوكول=${proxy.protocol} | فشل متتالٍ=${proxy.consecutiveFailures} | نجاح التطبيق=${(proxy.upstreamApplyRate * 100).toInt()}%${proxy.failureReason?.let { " | ملاحظة: $it" } ?: ""}"
        )
        val identity = analyzeIdentityRecursive()
        ilog(
            "info",
            "🪪 الهوية: اتساق جغرافي=${if (identity.geoConsistency) "متسق ✅" else "غير متسق ⚠️"} | لغة/IP=${if (identity.languageIpMatch) "متطابق ✅" else "مختلف ⚠️"} | بصمة=${if (identity.canvasConsistency) "مستقرة ✅" else "غير مضبوطة بعد"} | UA=${identity.userAgent.take(52)}"
        )
        val behavior = analyzeAutomationRecursive()
        ilog(
            "info",
            "🧠 السلوك: مرحلة=${behavior.stage}/6 | أنسنة=${behavior.humanizationScore}% | تعلق=${behavior.stuckCount} | نجاح=${(behavior.successRate * 100).toInt()}% | تأخير تكيفي=${behavior.adaptiveDelay}ms"
        )
        val insight = synthesizeCrossLayerInsight(proxy, identity, behavior)
        logDecision(insight, isPreflight = true)
        emitInsight(insight)
        return insight
    }

    private fun performRecursiveAnalysis() {
        try {
            cycleCount++
            val proxy = analyzeProxyRecursive()
            val identity = analyzeIdentityRecursive()
            val behavior = analyzeAutomationRecursive()
            val insight = synthesizeCrossLayerInsight(proxy, identity, behavior)
            issueAdaptiveCommands(insight)
            lastCrossLayerInsight = insight
            if (insight.riskLevel == "critical" || insight.riskLevel == "high") {
                sessionAnomalyCount++
            }
            try { onInsight?.invoke(insight) } catch (_: Exception) {}

            val running = automationStateProvider?.invoke()?.isRunning == true
            val changed = insight.riskLevel != lastLoggedRisk || insight.recommendedActions != lastLoggedActions
            val dangerous = insight.riskLevel == "critical" || insight.riskLevel == "high"
            when {
                // تفاصيل كاملة عند التغير أو الخطر — ماذا يحدث وماذا سيحدث
                changed || dangerous -> {
                    logDecision(insight, isPreflight = false)
                    lastLoggedRisk = insight.riskLevel
                    lastLoggedActions = insight.recommendedActions
                }
                // نبضة مختصرة أثناء التشغيل كل ~30 ثانية لتأكيد أن الذكاء حي
                running && cycleCount % 6 == 0 -> {
                    ilog(
                        "info",
                        "💓 نبضة [دورة #$cycleCount]: خطر=${insight.riskLevel} | نمط=${insight.behavioralPattern} | أنسنة=${behavior.humanizationScore}% | بروتوكول=${proxy.protocol} | القرار: ${insight.predictedOutcome}"
                    )
                }
            }
        } catch (e: Exception) {
            ilog("error", "فشل التحليل الدوري: ${e.message}")
        }
    }

    /** سجل القرار الكامل: ماذا رصدنا + ماذا سنفعل بعده */
    private fun logDecision(insight: CrossLayerInsight, isPreflight: Boolean) {
        val prefix = if (isPreflight) "📋 نتيجة الفحص" else "🔁 دورة #$cycleCount"
        val level = when (insight.riskLevel) {
            "critical" -> "error"
            "high" -> "warning"
            "medium" -> "warning"
            else -> "success"
        }
        ilog(
            level,
            "$prefix: خطر=${insight.riskLevel} | نمط=${insight.behavioralPattern} | تأثير البروكسي=${"%.2f".format(insight.proxyImpactOnAutomation)} | اتساق الهوية=${(insight.identityConsistencyScore * 100).toInt()}%"
        )
        ilog(
            "info",
            "➡️ ما سيفعله النظام الآن (${insight.predictedOutcome}): ${insight.recommendedActions.joinToString(" ← ")}"
        )
    }

    /** توزيع الرؤية للواجهة والحفظ — يُستدعى من runPreflight أيضاً */
    private fun emitInsight(insight: CrossLayerInsight) {
        lastCrossLayerInsight = insight
        try { onInsight?.invoke(insight) } catch (_: Exception) {}
    }

    // ── ذاكرة مستمرة: لقطة/استعادة عبر الجلسات (يحفظها AppViewModel في prefs) ──
    fun snapshotState(): Map<String, String> {
        val proto = proxyBridge.protocolRatesSnapshot()
            .entries.joinToString(";") { "${it.key}:${it.value.first}/${it.value.second}" }
        return mapOf(
            "protoRates" to proto,
            "humanBoost" to automationBrain.humanizationBoost.toString(),
            "anomalies" to sessionAnomalyCount.toString(),
            "identityRotation" to identityService.lastRotationMs.toString(),
            "bridgeRotation" to proxyBridge.healthSnapshot().lastRotationMs.toString()
        )
    }

    fun restoreState(map: Map<String, String>) {
        try {
            map["protoRates"]?.takeIf { it.isNotBlank() }?.let { raw ->
                val parsed = raw.split(";").mapNotNull { part ->
                    val kv = part.split(":")
                    if (kv.size != 2) return@mapNotNull null
                    val nums = kv[1].split("/")
                    if (nums.size != 2) return@mapNotNull null
                    kv[0] to Pair(nums[0].toIntOrNull() ?: 0, nums[1].toIntOrNull() ?: 0)
                }.toMap()
                if (parsed.isNotEmpty()) proxyBridge.restoreProtocolRates(parsed)
            }
            map["humanBoost"]?.toIntOrNull()?.let {
                automationBrain.humanizationBoost = it.coerceIn(0, 5)
            }
            map["anomalies"]?.toIntOrNull()?.let { sessionAnomalyCount = it.coerceAtLeast(0) }
            map["identityRotation"]?.toLongOrNull()?.let { identityService.lastRotationMs = it }
            ilog("info", "💾 استُعيدت الذاكرة المستمرة: بروتوكولات + تعزيز + فئات من الجلسات السابقة")
        } catch (e: Exception) {
            ilog("error", "فشلت الاستعادة: ${e.message}")
        }
    }

    private fun synthesizeCrossLayerInsight(
        proxyReport: ProxyHealthReport,
        identityReport: IdentityFingerprintReport,
        automationReport: AutomationBehaviorReport
    ): CrossLayerInsight {
        val proxyImpact = calculateProxyImpactOnAutomation(proxyReport, automationReport)
        val identityScore = calculateIdentityConsistency(identityReport)
        val behavioralPattern = detectBehavioralPattern(
            proxyReport.isHealthy,
            identityReport.geoConsistency,
            automationReport.humanizationScore,
            automationReport.stuckCount
        )
        val riskLevel = assessRiskLevel(
            proxyReport.isHealthy,
            identityReport.geoConsistency,
            automationReport.stuckCount,
            automationReport.escapeActionsTaken
        )

        val (predictedOutcome, recommendedActions) = when {
            riskLevel == "critical" -> "pause_session" to listOf(
                "rotate_proxy_immediately",
                "reset_identity_fingerprints",
                "pause_automation",
                "analyze_block_reasons"
            )
            riskLevel == "high" -> "pause_and_rotate" to listOf(
                "rotate_proxy",
                "adjust_automation_delay",
                "validate_identity_consistency"
            )
            automationReport.stuckCount >= 3 -> "escape_and_restart" to listOf(
                "execute_escape_strategy",
                "rotate_proxy_if_needed",
                "reset_task_state"
            )
            !proxyReport.isHealthy -> "optimize_proxy" to listOf(
                "rotate_proxy_strategy",
                "test_alternative_proxies",
                "maintain_current_automation"
            )
            !identityReport.geoConsistency -> "reconcile_identity" to listOf(
                "rotate_identity_to_match_proxy",
                "adjust_language_ip",
                "maintain_automation"
            )
            else -> "continue_optimized" to listOf(
                "monitor_patterns",
                "fine_tune_delays",
                "maintain_successful_config"
            )
        }

        return CrossLayerInsight(
            proxyImpactOnAutomation = proxyImpact,
            identityConsistencyScore = identityScore,
            behavioralPattern = behavioralPattern,
            recommendedActions = recommendedActions,
            riskLevel = riskLevel,
            predictedOutcome = predictedOutcome
        )
    }

    private fun issueAdaptiveCommands(insight: CrossLayerInsight) {
        if (insight.recommendedActions.any { it == "rotate_proxy_immediately" }) {
            if (proxyBridge.rotateIfConfigured()) {
                ilog("warning", "🔄 أمر منفذ: دوران فوري للبروكسي (خطر ${insight.riskLevel}) — سيُعاد اكتشاف البروتوكول تلقائياً")
            } else {
                ilog("warning", "🔄 تعذر الدوران: لا بروكسي مهيأ — البقاء على الاتصال المباشر")
            }
        } else if (insight.recommendedActions.any { it == "rotate_proxy" || it == "rotate_proxy_if_needed" || it == "rotate_proxy_strategy" }) {
            if (insight.riskLevel != "low") {
                proxyBridge.resetIntelligenceCache()
                ilog("info", "🔄 أمر منفذ: تصفير ذاكرة البروتوكولات لإعادة التعلم من الصفر")
            }
        }

        if (insight.recommendedActions.any { it == "rotate_identity_to_match_proxy" || it == "reset_identity_fingerprints" }) {
            identityService.scheduleIdentityRotation()
            ilog("info", "🪪 أمر منفذ: جدولة تدوير الهوية بعد المهمة الحالية لمطابقة البروكسي")
        }

        if (insight.recommendedActions.any { it == "execute_escape_strategy" }) {
            automationBrain.triggerEscapeStrategy()
            ilog("warning", "🚨 أمر منفذ: استراتيجية هروب — العقل سيبدل المسار ويعيد التحميل عند أول صفحة")
        }

        if (insight.recommendedActions.any { it == "adjust_automation_delay" }) {
            automationBrain.increaseHumanizationFactor()
            ilog("info", "⏳ أمر منفذ: رفع الأنسنة للمستوى ${automationBrain.humanizationBoost} — كل الانتظارات ستطول (+0.8 ث/مستوى)")
        }
    }

    private fun calculateProxyImpactOnAutomation(
        proxy: ProxyHealthReport,
        automation: AutomationBehaviorReport
    ): Double {
        var baseImpact = if (proxy.isHealthy) 0.5 else -0.3
        if (automation.humanizationScore > 70 && !proxy.isHealthy) baseImpact -= 0.15
        if (proxy.isHealthy && automation.humanizationScore > 70) baseImpact += 0.2
        if (automation.stuckCount >= 3) {
            baseImpact -= 0.25 * automation.stuckCount.coerceAtMost(5).toDouble() / 5.0
        }
        return baseImpact.coerceIn(-1.0, 1.0)
    }

    private fun calculateIdentityConsistency(identity: IdentityFingerprintReport): Double {
        var score = 0.0
        if (identity.geoConsistency) score += 0.4
        if (identity.languageIpMatch) score += 0.3
        if (identity.canvasConsistency && identity.webglConsistency) score += 0.2
        // ملاحظة: عدم وجود بصمة بعد (أول تشغيل) ليس فشلاً — فقط لا مكافأة
        if (identity.userAgent == "(not set yet)") score += 0.1
        val rotationInterval = System.currentTimeMillis() - identity.lastRotationMs
        if (identity.lastRotationMs != 0L && rotationInterval < 60_000) score -= 0.2
        else if (identity.lastRotationMs != 0L && rotationInterval > 86_400_000) score -= 0.1
        return score.coerceIn(0.0, 1.0)
    }

    private fun detectBehavioralPattern(
        proxyHealthy: Boolean,
        identityConsistent: Boolean,
        humanizationScore: Int,
        stuckCount: Int
    ): String {
        return if (!proxyHealthy && stuckCount >= 3) "adaptive_escape_attempt"
        else if (!proxyHealthy) "proxy_constrained"
        else if (stuckCount >= 2) "escalating_challenges"
        else if (humanizationScore >= 80 && identityConsistent) "optimal_human_blend"
        else "mixed_pattern"
    }

    private fun assessRiskLevel(
        proxyHealthy: Boolean,
        identityConsistent: Boolean,
        stuckCount: Int,
        escapeActions: Int
    ): String {
        var riskScore = 0.0
        if (!proxyHealthy) riskScore += 0.3
        if (!identityConsistent) riskScore += 0.25
        riskScore += stuckCount * 0.15
        riskScore += if (escapeActions >= 3) 0.2 else if (escapeActions >= 1) 0.1 else 0.0
        if (riskScore > 0.5) riskScore *= 1.2
        return when {
            riskScore >= 1.0 -> "critical"
            riskScore >= 0.75 -> "high"
            riskScore >= 0.5 -> "medium"
            else -> "low"
        }
    }

    private fun calculateHumanizationScore(state: AutomationState): Int {
        var score = 50
        if (state.cycleCountdown > 5) score += 15
        if (state.humanRealismScore > 90) score += 10
        if (state.cycleCountdown > 15) score += 10
        score += automationBrain.humanizationBoost * 3
        score -= state.stuckCount * 5
        return score.coerceIn(0, 100)
    }

    private fun calculateSuccessRate(state: AutomationState): Double {
        val completed = state.completedThisSession.coerceAtLeast(0)
        val leads = state.leadsThisSession.coerceAtLeast(0)
        val total = (completed + leads).coerceAtLeast(1)
        val baseRate = completed.toDouble() / total.toDouble()
        val qualityFactor = state.sessionQualityScore / 100.0
        return (baseRate * 0.7 + qualityFactor * 0.3).coerceIn(0.0, 1.0)
    }

    private fun detectPingTrend(): Long {
        // تقدير خفيف: فشل متكرر = زمن مرتفع
        val fails = proxyBridge.peekFailureCount()
        return 150L + fails.toLong() * 120L + Random.nextLong(80L)
    }
}
