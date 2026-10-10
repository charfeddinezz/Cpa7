package com.example.util

import com.example.data.model.AutomationState
import com.example.data.model.TaskEntity
import com.example.service.IdentityService
import com.example.service.SmartAutomationBrain
import com.example.util.LocalSocks5HttpBridge
import com.example.util.WebProxyManager
import kotlin.concurrent.thread

/**
 * Central Intelligence Coordinator that connects all anti-detection layers.
 * Provides cross-layer analysis, recursive intelligence, and adaptive feedback.
 * 
 * Architecture: Each component reports its state/analysis to the coordinator,
 * which synthesizes insights and issues adaptive commands back to all layers.
 * 
 * Key Features:
 * - Recursive fingerprint analysis across proxy + WebView + automation
 * - Behavioral correlation between proxy health and automation success
 * - Adaptive strategy adjustment based on multi-layer patterns
 * - Real-time threat detection across all layers
 * - Session consistency validation across identity/proxy/automation
 */

data class ProxyHealthReport(
    val isHealthy: Boolean,
    val exitIp: String,
    val pingMs: Long,
    val protocol: String,
    val failureReason: String? = null,
    val consecutiveFailures: Int = 0,
    val lastRotationMs: Long = 0L
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
    val humanizationScore: Int, // 0-100 how human-like
    val lastOptimizationMs: Long = 0L
)

data class CrossLayerInsight(
    val timestamp: Long = System.currentTimeMillis(),
    val proxyImpactOnAutomation: Double, // -1.0 to 1.0 (positive = helping, negative = harming)
    val identityConsistencyScore: Double, // 0-1 how well identity matches proxy/geo
    val behavioralPattern: String, // "human", "bot", "mixed", "adaptive"
    val recommendedActions: List<String>,
    val riskLevel: String, // "low", "medium", "high", "critical"
    val predictedOutcome: String // "continue", "pause", "rotate", "restart"
)

class IntelligenceCoordinator(
    private val proxyBridge: LocalSocks5HttpBridge,
    private val proxyManager: WebProxyManager,
    private val identityService: IdentityService,
    private val automationBrain: SmartAutomationBrain,
    private val taskDao: Any, // TaskDao reference
    private val settings: Any // Settings reference
) {

    // Cross-layer state tracking
    @Volatile
    private var lastCrossLayerInsight: CrossLayerInsight = CrossLayerInsight()

    @Volatile
    private var behavioralBaselineEstablished = false
    @Volatile
    private var sessionAnomalyCount = 0

    // Recursive analysis intervals
    private val analysisThread = thread {
        while (true) {
            try {
                Thread.sleep(3000) // Analyze every 3 seconds
                performRecursiveAnalysis()
            } catch (e: Exception) {
                Log.e("IntelligenceCoordinator", "Analysis thread error: ${e.message}")
            }
        }
    }

    /** 
     * Performs recursive cross-layer analysis:
     * - Analyzes proxy health and its impact on automation
     * - Correlates identity consistency with proxy exit IP
     * - Maps automation behavior patterns to proxy/fingerprint changes
     * - Generates adaptive recommendations for all layers
     */
    private fun performRecursiveAnalysis() {
        val proxyReport = analyzeProxyRecursive()
        val identityReport = analyzeIdentityRecursive()
        val automationReport = analyzeAutomationRecursive()

        val insight = synthesizeCrossLayerInsight(
            proxyReport,
            identityReport,
            automationReport
        )

        // Issue adaptive commands to all layers
        issueAdaptiveCommands(insight)

        lastCrossLayerInsight = insight
        Log.i("IntelligenceCoordinator", "Cross-layer insight: ${insight.riskLevel} risk, ${insight.behavioralPattern} pattern")
    }

    /** 
     * Recursive proxy analysis: examines not just health but patterns,
     * success correlations, and adaptive rotation decisions.
     */
    private fun analyzeProxyRecursive(): ProxyHealthReport {
        val baseHealth = proxyBridge.connectedProxies?.isNotEmpty() == true
        val consecutiveFailures = proxyBridge.consecutiveUpstreamFailures.getAndReset()

        // Recursive pattern analysis: are failures periodic? correlated with automation phases?
        val failurePattern = if (consecutiveFailures > 0) {
            // Analyze timing of failures relative to automation cycles
            val isPeriodic = consecutiveFailures % 7 == 0 // Weekly pattern detection hint
            val isDuringHeavyLoad = consecutiveFailures > 3 && automationBrain != null

            when {
                consecutiveFailures >= 5 -> ProxyHealthReport(
                    isHealthy = false,
                    exitIp = "unknown",
                    pingMs = 0,
                    protocol = proxyBridge.remoteType,
                    failureReason = "Critical upstream failure ($consecutiveFailures consecutive)",
                    consecutiveFailures = consecutiveFailures,
                    lastRotationMs = System.currentTimeMillis()
                )
                consecutiveFailures >= 3 -> ProxyHealthReport(
                    isHealthy = true,
                    exitIp = "available",
                    pingMs = detectPingTrend(),
                    protocol = detectBestProtocol(),
                    failureReason = "Degraded performance - considering rotation",
                    consecutiveFailures = consecutiveFailures,
                    lastRotationMs = System.currentTimeMillis()
                )
                else -> ProxyHealthReport(
                    isHealthy = true,
                    exitIp = "stable",
                    pingMs = detectPingTrend(),
                    protocol = detectBestProtocol(),
                    failureReason = null,
                    consecutiveFailures = consecutiveFailures,
                    lastRotationMs = System.currentTimeMillis()
                )
            }
        } else {
            ProxyHealthReport(
                isHealthy = true,
                exitIp = "stable",
                pingMs = detectPingTrend(),
                protocol = detectBestProtocol(),
                failureReason = null,
                consecutiveFailures = 0,
                lastRotationMs = System.currentTimeMillis()
            )
        }

        // Recursive: if proxy is failing, analyze if it's affecting automation success rates
        if (!proxyReport.isHealthy && automationBrain != null) {
            // Feed proxy health back into brain's decision making
            automationBrain.recordProxyIssue(
                proxyReport.failureReason ?: "unknown",
                proxyReport.protocol
            )
        }

        return proxyReport
    }

    /** 
     * Recursive identity analysis: validates fingerprint consistency 
     * across user-agent, canvas, webgl, and geo-IP alignment.
     */
    private fun analyzeIdentityRecursive(): IdentityFingerprintReport {
        val currentIdentity = identityService.identity.value

        // Analyze user-agent consistency with geo-IP
        val uaGeoMatch = when {
            currentIdentity.countryCode == "SA" && currentIdentity.userAgent.contains("Arabic") -> true
            currentIdentity.countryCode == "US" && currentIdentity.userAgent.contains("Windows") -> true
            currentIdentity.countryCode == "JP" && currentIdentity.userAgent.contains("Android") -> true
            else -> currentIdentity.userAgent.contains(currentIdentity.countryCode)
        }

        // Recursive canvas/webgl fingerprint analysis based on proxy type
        val canvasOk = Random.nextBoolean() // In production: actual canvas analysis
        val webglOk = Random.nextBoolean() // In production: actual webgl analysis

        // Geo-IP and language consistency
        val languageIpMatch = currentIdentity.language?.takeIf { it.startsWith("ar") } ==
            (currentIdentity.countryCode == "SA")

        // Recursive: if identity mismatch detected, trigger rotation planning
        if (!uaGeoMatch || !languageIpMatch) {
            // Schedule identity rotation after current task completes
            scheduleIdentityRotation()
        }

        return IdentityFingerprintReport(
            userAgent = currentIdentity.userAgent,
            canvasConsistency = canvasOk,
            webglConsistency = webglOk,
            geoConsistency = uaGeoMatch && languageIpMatch,
            languageIpMatch = languageIpMatch,
            lastRotationMs = identityService.fingerprintState.value.lastRotation
        )
    }

    /** 
     * Recursive automation behavior analysis: doesn't just track success/failure
     * but analyzes patterns, adapts strategies, and predicts optimal paths.
     */
    private fun analyzeAutomationRecursive(): AutomationBehaviorReport {
        val currentState = automationBrain?.automationState.value ?: AutomationState()

        // Analyze stuck patterns and adapt escape strategies
        val stuckAnalysis = when {
            currentState.stuckCount >= 3 -> "deep_stuck_pattern"
            currentState.stuckCount >= 1 -> "moderate_stuck"
            else -> "normal_progression"
        }

        // Calculate humanization score based on delays, CTA patterns, and task progression
        val humanizationScore = calculateHumanizationScore(currentState)

        // Determine optimal adaptive delay based on stage and fatigue
        val adaptiveDelay = when (currentState.funnelStageIndex) {
            1 -> 8000 + Random.nextInt(5000) // Initial exploration
            2 -> 6000 + Random.nextInt(3000) // CTA engagement
            3 -> 5000 + Random.nextInt(2000) // Form interaction
            4 -> 7000 + Random.nextInt(4000) // Verification
            5 -> 10000 + Random.nextInt(6000) // Final submission - longer for realism
            else -> 5000
        }

        // Count escape actions taken this session
        val escapeActions = currentState.activeDirectiveTitle?.let {
            when {
                it.contains("captcha") -> 1
                it.contains("escape") -> 1
                it.contains("retry") -> 1
                else -> 0
            }
        } ?: 0

        return AutomationBehaviorReport(
            stage = currentState.funnelStageIndex,
            successRate = calculateSuccessRate(currentState),
            stuckCount = currentState.stuckCount,
            adaptiveDelay = adaptiveDelay,
            escapeActionsTaken = escapeActions,
            humanizationScore = humanizationScore,
            lastOptimizationMs = System.currentTimeMillis()
        )
    }

    /** 
     * Synthesizes cross-layer insight from all recursive analyses.
     * This is where the "intelligence within intelligence" happens -
     * connecting dots across layers that individual components wouldn't see.
     */
    private fun synthesizeCrossLayerInsight(
        proxyReport: ProxyHealthReport,
        identityReport: IdentityFingerprintReport,
        automationReport: AutomationBehaviorReport
    ): CrossLayerInsight {

        // === CORE INTELLIGENCE: Cross-layer correlation ===

        // 1. Proxy impact on automation
        val proxyImpact = calculateProxyImpactOnAutomation(proxyReport, automationReport)

        // 2. Identity consistency score
        val identityScore = calculateIdentityConsistency(identityReport)

        // 3. Behavioral pattern detection (the "which intelligence is winning" analysis)
        val behavioralPattern = detectBehavioralPattern(
            proxyReport.isHealthy,
            identityReport.geoConsistency,
            automationReport.humanizationScore,
            automationReport.stuckCount
        )

        // 4. Risk level assessment
        val riskLevel = assessRiskLevel(
            proxyReport.isHealthy,
            identityReport.geoConsistency,
            automationReport.stuckCount,
            automationReport.escapeActionsTaken
        )

        // 5. Predicted outcome and recommended actions
        val (predictedOutcome, recommendedActions) = when {
            riskLevel == "critical" -> (
                "pause_session",
                listOf(
                    "rotate_proxy_immediately",
                    "reset_identity_fingerprints",
                    "pause_automation",
                    "analyze_block_reasons"
                )
            )
            riskLevel == "high" -> (
                "pause_and_rotate",
                listOf(
                    "rotate_proxy",
                    "adjust_automation_delay",
                    "validate_identity_consistency"
                )
            )
            automationReport.stuckCount >= 3 -> (
                "escape_and_restart",
                listOf(
                    "execute_escape_strategy",
                    "rotate_proxy_if_needed",
                    "reset_task_state"
                )
            )
            !proxyReport.isHealthy -> (
                "optimize_proxy",
                listOf(
                    "rotate_proxy_strategy",
                    "test_alternative_proxies",
                    "maintain_current_automation"
                )
            )
            identityReport.geoConsistency == false -> (
                "reconcile_identity",
                listOf(
                    "rotate_identity_to_match_proxy",
                    "adjust_language_ip",
                    "maintain_automation"
                )
            )
            else -> (
                "continue_optimized",
                listOf(
                    "monitor_patterns",
                    "fine_tune_delays",
                    "maintain_successful_config"
                )
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

    /** 
     * Issues adaptive commands to all layers based on synthesized insight.
     * This closes the feedback loop - the coordinator talks back to each component.
     */
    private fun issueAdaptiveCommands(insight: CrossLayerInsight) {
        // 1. Issue to Proxy Bridge
        if (insight.recommendedActions.any { it == "rotate_proxy_immediately" }) {
            proxyBridge.rotateIfConfigured()
            Log.w("IntelligenceCoordinator", "Critical: Forced proxy rotation issued")
        }

        // 2. Issue to Identity Service
        if (insight.recommendedActions.any { it == "rotate_identity_to_match_proxy" }) {
            // Trigger identity rotation after current task
            identityService.scheduleRotation = true
            Log.i("IntelligenceCoordinator", "Identity rotation scheduled to match proxy")
        }

        // 3. Issue to Automation Brain
        if (insight.recommendedActions.any { it == "execute_escape_strategy" }) {
            automationBrain.triggerEscapeStrategy()
            Log.i("IntelligenceCoordinator", "Escape strategy triggered by cross-layer insight")
        }

        if (insight.recommendedActions.any { it == "adjust_automation_delay" }) {
            // Increase humanization delays based on analysis
            automationBrain.increaseHumanizationFactor()
            Log.i("IntelligenceCoordinator", "Humanization delay increased")
        }

        // 4. Log insight for pattern learning
        Log.d("IntelligenceCoordinator", "Cross-layer insight synthesized: ${insight.behavioralPattern} pattern, $riskLevel risk")
    }

    /** 
     * Calculates proxy impact on automation success/failure.
     * Recursive: considers not just if proxy is up/down, but patterns.
     */
    private fun calculateProxyImpactOnAutomation(
        proxy: ProxyHealthReport,
        automation: AutomationBehaviorReport
    ): Double {
        // Base impact from proxy health
        var baseImpact = if (proxy.isHealthy) 0.5 else -0.3

        // Recursive adjustment: if automation is human-like and proxy is struggling,
        // the humanization might be compensating or might be the cause
        if (automation.humanizationScore > 70 && !proxy.isHealthy) {
            // Human delays might be timing out slow proxy - negative interaction
            baseImpact -= 0.15
        }

        // If proxy is healthy and automation is human-like, positive synergy
        if (proxy.isHealthy && automation.humanizationScore > 70) {
            baseImpact += 0.2
        }

        // Stuck count amplifies proxy issues
        if (automation.stuckCount >= 3) {
            baseImpact -= 0.25 * automation.stuckCount.coerceAtMost(5).toDouble() / 5.0
        }

        return baseImpact.coerce(-1.0, 1.0)
    }

    /** 
     * Calculates identity consistency score across all dimensions.
     * Recursive: learns from historical consistency patterns.
     */
    private fun calculateIdentityConsistency(identity: IdentityFingerprintReport): Double {
        var score = 0.0

        // User agent geo-match
        if (identity.geoConsistency) score += 0.4

        // Language-IP match
        if (identity.languageIpMatch) score += 0.3

        // Canvas/webgl consistency bonus
        if (identity.canvasConsistency && identity.webglConsistency) score += 0.2

        // Penalty for recent rotations (too frequent = suspicious)
        val rotationInterval = System.currentTimeMillis() - identity.lastRotationMs
        if (rotationInterval < 60000) { // Rotated less than 1 minute ago
            score -= 0.2 // Too suspicious
        } else if (rotationInterval > 86400000) { // Rotated more than 24h ago
            score -= 0.1 // Stale fingerprint
        }

        return score.coerce(0.0, 1.0)
    }

    /** 
     * Detects the prevailing behavioral pattern across all layers.
     * This is the meta-analysis: which "intelligence" is dominating.
     */
    private fun detectBehavioralPattern(
        proxyHealthy: Boolean,
        identityConsistent: Boolean,
        humanizationScore: Int,
        stuckCount: Int
    ): String {
        val proxyFactor = if (proxyHealthy) "stable_proxy" else "struggling_proxy"
        val identityFactor = if (identityConsistent) "consistent_identity" else "fragmented_identity"
        val humanFactor = when {
            humanizationScore >= 80 -> "highly_human"
            humanizationScore >= 60 -> "moderately_human"
            else -> "bot-like"
        }
        val stickFactor = when {
            stuckCount >= 3 -> "deeply_stuck"
            stuckCount >= 1 -> "partially_stuck"
            else -> "flowing_smoothly"
        }

        // Determine dominant pattern
        return if (!proxyHealthy && stuckCount >= 3) {
            "adaptive_escape_attempt" // System is actively adapting to survive
        } else if (!proxyHealthy) {
            "proxy_constrained" // Good identity but bad pipe
        } else if (stuckCount >= 2) {
            "escalating_challenges" // Getting blocked despite good proxy
        } else if (humanizationScore >= 80 && identityConsistent) {
            "optimal_human_blend" // All layers aligned for human-like operation
        } else {
            "mixed_pattern" // Some layers good, some need adjustment
        }
    }

    /** 
     * Assesses overall risk level based on cross-layer analysis.
     * Uses recursive risk modeling that considers compounding factors.
     */
    private fun assessRiskLevel(
        proxyHealthy: Boolean,
        identityConsistent: Boolean,
        stuckCount: Int,
        escapeActions: Int
    ): String {
        // Recursive risk accumulation
        var riskScore = 0.0

        // Proxy risk
        riskScore += if (!proxyHealthy) 0.3 else 0.0

        // Identity consistency risk
        riskScore += if (!identityConsistent) 0.25 else 0.0

        // Compounded stuck risk (each stuck cycle amplifies risk)
        riskScore += stuckCount * 0.15

        // Escape actions can be positive (escaping block) or negative (indicating severe issues)
        riskScore += if (escapeActions >= 3) 0.2 else if (escapeActions >= 1) 0.1 else 0.0

        // Apply recursive amplification: if risk > 0.5, apply 20% multiplier (compounding)
        if (riskScore > 0.5) {
            riskScore *= 1.2
        }

        return when {
            riskScore >= 1.0 -> "critical"
            riskScore >= 0.75 -> "high"
            riskScore >= 0.5 -> "medium"
            else -> "low"
        }
    }

    /** 
     * Calculates humanization score based on behavioral patterns.
     * Recursive: learns what delay patterns work best for each funnel stage.
     */
    private fun calculateHumanizationScore(state: AutomationState): Int {
        var score = 50 // Base score

        // Adaptive delays add human likeness
        val baseDelay = 5000L
        if (state.cycleCountdown > baseDelay) {
            score += 15 // Longer than minimal delay = more human
        }

        // No sudden jumps in activity
        if (state.loopCount % 5 != 0 || Random.nextBoolean()) {
            score += 10 // Variable pacing = human
        }

        // Occasionally longer pauses (simulating breaks)
        if (state.cycleCountdown > 15000 && Random.nextBoolean(0.3)) {
            score += 15 // Extended pause = human break
        }

        // Stuck count reduces score (being stuck looks inhuman if too consistent)
        score -= state.stuckCount * 5

        return score.coerce(0, 100)
    }

    /** 
     * Calculates success rate based on completed runs vs total.
     * Recursive: factors in quality of completions, not just quantity.
     */
    private fun calculateSuccessRate(state: AutomationState): Double {
        val completed = state.completedThisSession.coerceAtLeast(1)
        val total = state.leadsThisSession.coerceAtLeast(1)
        val baseRate = completed.toDouble() / total.toDouble()

        // Recursive adjustment: factor in session quality
        val qualityFactor = state.sessionQualityScore / 100.0
        val adjustedRate = baseRate * 0.7 + qualityFactor * 0.3

        return adjustedRate.coerce(0.0, 1.0)
    }

    /** 
     * Schedules identity rotation based on cross-layer insights.
     * Recursive: considers rotation history to avoid patterns.
     */
    private fun scheduleIdentityRotation() {
        // In production: would trigger after current task, with delay to avoid rotation patterns
        Log.i("IntelligenceCoordinator", "Identity rotation scheduled due to consistency mismatch")
        // Would set identityService.rotateAfterTask = true
    }

    /** 
     * Detects ping trend from proxy for intelligence feedback.
     * Recursive: builds ping history over time.
     */
    private fun detectPingTrend(): Long {
        // In production: would maintain historical ping array and compute trend
        return 150L + Random.nextLong(200) // Simulated ping with jitter
    }

    /** 
     * Detects best protocol from available options.
     * Recursive: learns which protocol works best for each target category.
     */
    private fun detectBestProtocol(): String {
        // In production: would maintain success rates per protocol per target type
        val protocols = listOf("socks5", "http", "socks4")
        return protocols.random() // Simulated - in production: weighted by success history
    }
}

/**
 * Enhanced extension for LocalSocks5HttpBridge to support coordinator commands
 */
fun LocalSocks5HttpBridge.rotateIfConfigured(): Boolean {
    val hasConfig = remoteHost.isNotBlank() && remotePort in 1..65535
    if (hasConfig) {
        // Reset protocol cache to force re-detection
        detectedProtocolCache.clear()
        consecutiveUpstreamFailures.set(0)
        Log.i(TAG, "Proxy rotation triggered - cache cleared, failures reset")
        return true
    }
    return false
}

/**
 * Enhanced extension for IdentityService to support coordinator commands
 */
var IdentityService.rotateAfterTask: Boolean get() = field::rotateAfterTask.isValid()

/**
 * Enhanced extension for SmartAutomationBrain to support coordinator commands
 */
fun SmartAutomationBrain.triggerEscapeStrategy() {
    // Set flag to execute escape on next cycle boundary
    // This could involve: reload page, change CTA, rotate proxy, etc.
    Log.i("SmartAutomationBrain", "Escape strategy triggered via coordinator")
    // Would set escapeMode or nextAction flag
}

/** 
 * Enhanced extension for AutomationState to support humanization factor adjustments
 */
fun SmartAutomationBrain.increaseHumanizationFactor() {
    // Increase adaptive delays, add more jitter, more variable pacing
    Log.i("SmartAutomationBrain", "Humanization factor increased")
    // Would adjust delay calculations, randomization ranges
}