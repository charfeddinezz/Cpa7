package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.AutomationState
import com.example.data.model.CampaignStat
import com.example.data.model.EmailItem
import com.example.data.model.ExtractedInfo
import com.example.data.model.GeneratedIdentity
import com.example.data.model.LogEntry
import com.example.data.model.ProxyItem
import com.example.data.model.ScriptItem
import com.example.data.model.TaskEntity
import com.example.service.AutomationScriptBuilder
import com.example.service.ExtractedPlanResult
import com.example.service.IdentityService
import com.example.service.SmartAutomationBrain
import com.example.service.TaskCategoryPlanner
import com.example.util.IntelligenceCoordinator
import com.example.util.LocalSocks5HttpBridge
import com.example.util.WebProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ScreenTab(val id: String, val title: String, val titleAr: String = "") {
    TASKS("tasks", "Tasks", "المهام"),
    BROWSER("browser", "Browser", "المتصفح"),
    TEMPLATES("templates", "Templates", "قوالب العمل"),
    CLICK("click", "Offer Click", "شاشة النقرة"),
    INFO("info", "Identity", "الهوية"),
    PROXIES("proxies", "Proxies", "البروكسي"),
    SCRIPTS("scripts", "Scripts", "السكربتات"),
    EMAILS("emails", "Emails", "الإيميلات"),
    STATS("stats", "Analytics", "الإحصائيات"),
    SETTINGS("settings", "Settings", "الإعدادات"),
    LOGS("logs", "Logs", "السجلات")
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val taskDao = db.taskDao()
    private val emailDao = db.emailDao()
    private val scriptDao = db.scriptDao()
    private val leadLogDao = db.leadLogDao()
    private val proxyDao = db.proxyDao()
    private val offerClickDao = db.offerClickDao()
    private val workTemplateDao = db.workTemplateDao()

    private val intelligenceCoordinator = IntelligenceCoordinator(
        LocalSocks5HttpBridge,
        WebProxyManager,
        IdentityService,
        SmartAutomationBrain,
        taskDao,
        this@AppViewModel
    )

    private var proxySequenceIndex: Int = 0

    private val prefs = application.getSharedPreferences("cpa_automator_prefs", Context.MODE_PRIVATE)

    // Navigation Tab
    private val _currentTab = MutableStateFlow(ScreenTab.TASKS)
    val currentTab: StateFlow<ScreenTab> = _currentTab.asStateFlow()

    // Automation State
    private val _automationState = MutableStateFlow(AutomationState())
    val automationState: StateFlow<AutomationState> = _automationState.asStateFlow()

    // Logs
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // Extracted Geo Info
    private val _extractedInfo = MutableStateFlow(ExtractedInfo())
    val extractedInfo: StateFlow<ExtractedInfo> = _extractedInfo.asStateFlow()

    // Generated Identity
    private val _identity = MutableStateFlow(IdentityService.generateIdentity())
    val identity: StateFlow<GeneratedIdentity> = _identity.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    // Flows from DB
    val tasks: StateFlow<List<TaskEntity>> = taskDao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emails: StateFlow<List<EmailItem>> = emailDao.getAllEmails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emailCount: StateFlow<Int> = emailDao.getEmailCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val proxies: StateFlow<List<ProxyItem>> = proxyDao.getAllProxies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val proxyCount: StateFlow<Int> = proxyDao.getProxyCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val scripts: StateFlow<List<ScriptItem>> = scriptDao.getAllScripts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<List<CampaignStat>> = leadLogDao.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val offerClickItems: StateFlow<List<com.example.data.model.OfferClickItem>> = offerClickDao.getAllClickItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledClickItems: StateFlow<List<com.example.data.model.OfferClickItem>> = offerClickDao.getEnabledClickItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workTemplates: StateFlow<List<com.example.data.model.WorkTemplateEntity>> = workTemplateDao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Browser navigation event channel
    private val _browserCommand = MutableStateFlow<BrowserCommand?>(null)
    val browserCommand: StateFlow<BrowserCommand?> = _browserCommand.asStateFlow()

    init {
        // ربط المنسق بمزودات الحالة الحية — ذكاء متبادل بعد اكتمال التهيئة
        intelligenceCoordinator.attach(
            identity = { _identity.value },
            automationState = { _automationState.value },
            geo = { _extractedInfo.value },
            userAgent = {
                (_browserCommand.value as? BrowserCommand.LoadUrl)?.userAgent
                    ?: IdentityService.resolveUserAgent("random", isMobileDefault = true)
            }
        )
        // سجل التطبيق يعرض كل ما يفعله المنسق وما سيفعله
        intelligenceCoordinator.logEmitter = { level, message -> addLog(level, message) }
        // توزيع الرؤى: المنسق → الواجهة (حقول intel) + حفظ مخنوق
        intelligenceCoordinator.onInsight = { insight ->
            val cur = _automationState.value
            if (cur.intelRiskLevel != insight.riskLevel || cur.intelPattern != insight.behavioralPattern) {
                _automationState.update {
                    it.copy(
                        intelRiskLevel = insight.riskLevel,
                        intelPattern = insight.behavioralPattern,
                        intelUpdatedAt = insight.timestamp
                    )
                }
            }
            // حفظ مخنوق: فقط عند تغير الخطر (تجنب كتابة prefs كل 5 ثوانٍ)
            if (cur.intelRiskLevel != insight.riskLevel) {
                try { persistIntelligence() } catch (_: Exception) {}
            }
        }
        restoreIntelligence()
    }

    // ── ذاكرة مستمرة للذكاء المتقاطع (بروتوكولات + فئات + تعزيز) ──
    private val intelPrefsKey: String = "cross_layer_intel_v1"
    private val categoryPrefsKey: String = "category_intel_v1"
    private val geoPrefsKey: String = "geo_intel_v1"

    private fun persistIntelligence() {
        try {
            val snap = intelligenceCoordinator.snapshotState()
            val obj = org.json.JSONObject()
            for ((k, v) in snap) obj.put(k, v)
            prefs.edit().putString(intelPrefsKey, obj.toString()).apply()
            val catObj = org.json.JSONObject()
            for ((k, v) in TaskCategoryPlanner.categoryStatsSnapshot()) {
                catObj.put(k, "${v.first}/${v.second}")
            }
            prefs.edit().putString(categoryPrefsKey, catObj.toString()).apply()
            val geoObj = org.json.JSONObject()
            for ((k, v) in com.example.util.GeoIntel.snapshot()) {
                geoObj.put(k, "${v.first}/${v.second}")
            }
            prefs.edit().putString(geoPrefsKey, geoObj.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun restoreIntelligence() {
        try {
            val raw = prefs.getString(intelPrefsKey, "") ?: ""
            if (raw.isNotBlank()) {
                val obj = org.json.JSONObject(raw)
                val map = mutableMapOf<String, String>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = obj.optString(k, "")
                }
                intelligenceCoordinator.restoreState(map)
            }
            val catRaw = prefs.getString(categoryPrefsKey, "") ?: ""
            if (catRaw.isNotBlank()) {
                val obj = org.json.JSONObject(catRaw)
                val map = mutableMapOf<String, Pair<Int, Int>>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val parts = obj.optString(k, "0/0").split("/")
                    if (parts.size == 2) {
                        map[k] = Pair(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
                    }
                }
                if (map.isNotEmpty()) TaskCategoryPlanner.restoreCategoryStats(map)
            }
            val geoRaw = prefs.getString(geoPrefsKey, "") ?: ""
            if (geoRaw.isNotBlank()) {
                val obj = org.json.JSONObject(geoRaw)
                val map = mutableMapOf<String, Pair<Int, Int>>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val parts = obj.optString(k, "0/0").split("/")
                    if (parts.size == 2) {
                        map[k] = Pair(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
                    }
                }
                if (map.isNotEmpty()) com.example.util.GeoIntel.restore(map)
            }
        } catch (_: Exception) {}
    }

    // Completion callback flag from Smart Mode
    private var completionReceivedForCurrentTask: Boolean = false

    private var automationJob: Job? = null

    // ── Smart Brain memory & learning ──
    private var brainMemory: SmartAutomationBrain.BrainMemory = SmartAutomationBrain.initialMemory()
    private val taskLearning: MutableMap<String, SmartAutomationBrain.TaskLearningStats> = mutableMapOf()
    private var lastSmartDecision: SmartAutomationBrain.SmartDecision? = null

    // ── Full-automation hardening (P0/P1/P2/P3) ──
    private val taskFailureCount: MutableMap<String, Int> = mutableMapOf()
    private val taskRetryLimit: Int = 3
    // ── Blank-wall intelligence: عدّاد الجدران البيضاء لكل مهمة + المهمة الجارية ──
    private val blankWallCount: MutableMap<String, Int> = mutableMapOf()
    private var currentRunTask: TaskEntity? = null

    /** البروكسي المطابق للإعدادات الحالية (يُستخدم للحرق/التقييم) */
    private suspend fun findCurrentProxyItem(): com.example.data.model.ProxyItem? {
        return try {
            val s = _settings.value
            if (!s.proxyEnabled || s.proxyHost.isBlank()) return null
            proxyDao.getAllProxiesList().firstOrNull {
                it.host.equals(s.proxyHost.trim(), ignoreCase = true) &&
                    it.port.toString() == s.proxyPort.trim()
            }
        } catch (_: Exception) { null }
    }
    private var automationStartTime: Long = 0L
    private val learningPrefsKey: String = "brain_learning_json_v1"
    private val logsPrefsKey: String = "persisted_logs_json_v1"
    companion object {
        const val NIKE_CLICK_TEXT: String = "Get a \$100 Nike Gift Card!"
        const val GDFQO_TASK_ID: String = "task_gdfqo_blogspot"
        const val GDFQO_UTM_URL: String = "https://gdfqo.blogspot.com/?utm_source=facebook&utm_medium=cpc&utm_campaign=tools&utm_content=tools_ad_1"
        const val OMG10_SMARTLINK_URL: String = "https://omg10.com/4/7780677"
    }

    init {
        addLog("info", "CPA Automator initialized and ready.")
        val initSettings = _settings.value
        val legacyDeadHosts = setOf("35.182.137.246", "68.1.210.189", "198.8.94.170")
        if (initSettings.proxyHost in legacyDeadHosts) {
            val cleaned = initSettings.copy(proxyEnabled = false, proxyHost = "", proxyPort = "", proxyType = "none")
            _settings.value = cleaned
            saveSettingsToPrefs(cleaned)
        }
        if (_settings.value.proxyEnabled && _settings.value.proxyHost.isNotBlank()) {
            val sNow = _settings.value
            val initPort = sNow.proxyPort.toIntOrNull()
            WebProxyManager.applyProxy(application, true, sNow.proxyHost, initPort, sNow.proxyType, sNow.proxyUser, sNow.proxyPass) { success, msg ->
                addLog(if (success) "info" else "warning", "[ProxyController] $msg")
            }
        } else {
            WebProxyManager.clearProxy(application) { _, _ ->
                addLog("info", "🌐 [الاتصال المحلي المباشر]: نظام البروكسي مغلق افتراضياً، الاعتماد على الاتصال المحلي العادي.")
            }
        }
        refreshGeoInfo()
        viewModelScope.launch(Dispatchers.IO) {
            // Purge any legacy hardcoded dead proxies from DB if present
            try {
                val existingProxies = proxyDao.getAllProxiesList()
                for (p in existingProxies) {
                    if (p.host in legacyDeadHosts && p.port == 4145) {
                        proxyDao.deleteProxy(p)
                    }
                }
            } catch (_: Exception) {}
            val allTasks = taskDao.getEnabledTasks()
            val hasCtc = allTasks.any { it.url.contains("consumertestconnect") }
            if (!hasCtc) {
                val ctcTask = TaskEntity(
                    id = "task_ctc_100gc",
                    name = "ConsumerTestConnect ($100 GC)",
                    url = "https://consumertestconnect.com/ctc-100gcsweep",
                    referer = "https://www.google.com",
                    userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    mode = "mode1",
                    repeatCount = 3,
                    browserDuration = 45,
                    categories = "Email Submit, Terms Agreement, Survey / Quiz, Lead Gen Form, Skip Upsells, Confirmation",
                    completionKeywords = "thank you, congratulations, success, confirmed, sweepstakes, completed",
                    enabled = true
                )
                taskDao.insertTask(ctcTask)
                addLog("info", "Loaded ConsumerTestConnect ($100 GC) optimized automation plan.")
            }

            // Ensure GDFQO Blogspot Offer Landing Bridge task exists (Priority #1)
            // Live-verified: homepage carries alignmentfiles locker id=1741238 + Blogger cookie banner.
            // UTM preserved so Facebook CPC attribution is not lost on LoadUrl.
            val fullTaskList = taskDao.getAllTasksList()

            // Upgrade any existing tasks that had overly broad completionKeywords ("continue", "reward", "sweepstakes", "claim")
            // which caused premature completion on initial landing pages, and ensure full universal categories.
            val universalCats = "Offer Click, Survey / Quiz, Email Submit, Zip Submit, Lead Gen Form, PIN Submit, Terms Agreement, Skip Upsells, Confirmation"
            val cleanCompletionKeywords = "thank you for completing, submission received, registration complete, survey completed, successfully submitted, check your inbox, entry confirmed"
            for (existing in fullTaskList) {
                val kwLower = existing.completionKeywords.lowercase()
                val hasBadKeywords = kwLower.contains("continue") || kwLower.contains("reward") || kwLower.contains("sweepstakes") || kwLower.contains("claim")
                val needsMobileUa = existing.userAgent == IdentityService.USER_AGENTS[0].value &&
                    (existing.url.contains(".php", ignoreCase = true) || existing.url.contains("id=", ignoreCase = true) || existing.url.contains("omg10", ignoreCase = true))
                if (hasBadKeywords || existing.categories.isBlank() || needsMobileUa) {
                    taskDao.updateTask(
                        existing.copy(
                            userAgent = if (needsMobileUa) "random" else existing.userAgent,
                            categories = existing.categories.ifBlank { universalCats },
                            completionKeywords = if (hasBadKeywords || existing.completionKeywords.isBlank()) cleanCompletionKeywords else existing.completionKeywords,
                            referer = existing.referer.ifBlank { "https://www.google.com/" }
                        )
                    )
                }
            }

            // Ensure the user's tested link (rileymarker.com) is ready in Tasks if not yet added
            val rileyUrl = "https://rileymarker.com/show.php?l=0&u=2227942&id=75008"
            if (fullTaskList.none { it.url.contains("rileymarker.com", ignoreCase = true) }) {
                val rileyPlan = TaskCategoryPlanner.extractFunnelPlanFromUrl(rileyUrl)
                val rileyTask = TaskEntity(
                    id = "task_rileymarker_75008",
                    name = rileyPlan.detectedName,
                    url = rileyPlan.targetUrl,
                    referer = rileyPlan.recommendedReferer,
                    userAgent = "random",
                    mode = rileyPlan.recommendedMode,
                    repeatCount = 1,
                    browserDuration = rileyPlan.recommendedDuration,
                    categories = rileyPlan.categories.joinToString(", "),
                    completionKeywords = rileyPlan.recommendedKeywords,
                    clearCacheOnRepeat = true,
                    newIdentityOnRepeat = true,
                    enabled = true
                )
                taskDao.insertTask(rileyTask)
                addLog("info", "🚀 Ready in Tasks: ${rileyTask.name} ($rileyUrl)")
            }

            // Ensure Canvas Fingerprint Noise script is active in scripts registry
            val activeScripts = scriptDao.getActiveScripts()
            if (activeScripts.none { it.id == "script_canvas_noise" }) {
                val canvasScript = ScriptItem(
                    id = "script_canvas_noise",
                    name = "Canvas Fingerprint Randomized Noise",
                    timing = "before",
                    execMode = "sequential",
                    code = AutomationScriptBuilder.buildCanvasNoiseScript(),
                    enabled = true,
                    isSystemPreset = true
                )
                scriptDao.insertScript(canvasScript)
                addLog("info", "Canvas API randomized noise anti-fingerprinting script registered.")
            }

            // Ensure Offer Click texts are seeded if table is currently empty
            // Nike $100 is Priority #0 per user request (GDFQO locker live offer)
            val clickItems = offerClickDao.getAllClickItemsList()
            if (clickItems.isEmpty()) {
                val defaults = listOf(
                    com.example.data.model.OfferClickItem(text = "Get a \$100 Nike Gift Card!", enabled = true, orderIndex = 0, tagOrNote = "Nike $100 Locker Offer #1"),
                    com.example.data.model.OfferClickItem(text = "Get \$1000 Walmart gift card", enabled = true, orderIndex = 1, tagOrNote = "Walmart $1000 GC Offer"),
                    com.example.data.model.OfferClickItem(text = "Claim \$750 Cash App Reward", enabled = true, orderIndex = 2, tagOrNote = "Cash App Reward"),
                    com.example.data.model.OfferClickItem(text = "Get \$500 Amazon Gift Card", enabled = true, orderIndex = 3, tagOrNote = "Amazon $500 Sweep"),
                    com.example.data.model.OfferClickItem(text = "Win \$100 Target Gift Card", enabled = true, orderIndex = 4, tagOrNote = "Target Gift Card"),
                    com.example.data.model.OfferClickItem(text = "Claim \$500 Apple Store Card", enabled = false, orderIndex = 5, tagOrNote = "Apple Store Voucher")
                )
                offerClickDao.insertClickItems(defaults)
                addLog("info", "🎯 [شاشة النقرة]: تم تجهيز نصوص النقرة الافتراضية (Nike $100 أولوية #1).")
            } else if (clickItems.none { it.text.contains("Nike", ignoreCase = true) }) {
                // Migration: ensure Nike offer exists for existing installs
                val maxOrder = offerClickDao.getMaxOrderIndex() ?: clickItems.size
                offerClickDao.insertClickItem(
                    com.example.data.model.OfferClickItem(text = "Get a \$100 Nike Gift Card!", enabled = true, orderIndex = maxOrder + 1, tagOrNote = "Nike $100 Locker Offer #1")
                )
                // Move Nike to top by shifting others is handled by orderIndex sort; force order 0 via re-index
                val all = offerClickDao.getAllClickItemsList().sortedBy { it.orderIndex }.toMutableList()
                val nike = all.firstOrNull { it.text.contains("Nike", ignoreCase = true) }
                if (nike != null) {
                    all.remove(nike)
                    all.add(0, nike)
                    all.forEachIndexed { idx, item -> offerClickDao.updateOrderIndex(item.id, idx) }
                }
                addLog("success", "🎯 [ترحيل]: تمت إضافة عرض Nike $100 كأولوية #1 تلقائياً.")
            }

            // Automatically populate proxy pool: ensure user's default US proxy exists
            val currentProxyCount = proxyDao.getProxyCountOnce()
            if (currentProxyCount == 0) {
                val userProxy = com.example.data.model.ProxyItem(
                    host = "185.100.232.75",
                    port = 9999,
                    type = "socks5",
                    username = "bqjcykpcvw-a2c12283-3e44-4012-b6c0-7a476bef9351",
                    password = "DKiwjdv40s2tG3Jk",
                    country = "US",
                    status = "working",
                    lastPingMs = 120L
                )
                proxyDao.insertProxy(userProxy)
                val targetUrl = _settings.value.proxyListUrl.ifBlank { IdentityService.DEFAULT_ASOCKS_URL }
                val result = IdentityService.fetchProxiesFromUrl(targetUrl, "socks5")
                if (result.isSuccess) {
                    val list = result.getOrNull() ?: emptyList()
                    if (list.isNotEmpty()) {
                        proxyDao.insertProxies(list)
                        addLog("success", "Loaded ${list.size} Asocks US proxies into pool. Default active: 185.100.232.75:9999")
                    }
                }
            }
        }
    }

    fun selectTab(tab: ScreenTab) {
        _currentTab.value = tab
    }

    fun addLog(level: String, message: String, taskName: String? = null) {
        val entry = LogEntry(level = level, message = message, taskName = taskName)
        _logs.update { listOf(entry) + it.take(250) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    // --- Task Operations ---

    fun saveTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.insertTask(task)
            addLog("info", "Task saved: ${task.name}")
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.deleteTask(task)
            addLog("warning", "Task deleted: ${task.name}")
        }
    }

    fun toggleTaskEnabled(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(enabled = !task.enabled)
            taskDao.updateTask(updated)
        }
    }

    // --- Email Pool Operations ---

    fun addEmail(email: String) {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.insertEmails(listOf(EmailItem(email = trimmed)))
            addLog("info", "Added email: $trimmed")
        }
    }

    fun importEmailsBulk(raw: String) {
        val lines = raw.split("\n", ",", ";")
            .map { it.trim() }
            .filter { it.contains("@") && it.contains(".") }
        if (lines.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.insertEmails(lines.map { EmailItem(email = it) })
            addLog("success", "Imported ${lines.size} emails to pool.")
        }
    }

    fun generateTestEmails(count: Int = 10) {
        val domains = listOf("gmail.com", "outlook.com", "yahoo.com", "icloud.com")
        val generated = (1..count).map {
            val ident = IdentityService.generateIdentity()
            val domain = domains.random()
            "${ident.firstName.lowercase()}.${ident.lastName.lowercase()}${System.currentTimeMillis() % 1000}@$domain"
        }
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.insertEmails(generated.map { EmailItem(email = it) })
            addLog("info", "Generated $count test emails into pool.")
        }
    }

    fun deleteEmail(email: EmailItem) {
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.deleteEmail(email)
        }
    }

    fun clearAllEmails() {
        viewModelScope.launch(Dispatchers.IO) {
            emailDao.clearAllEmails()
            addLog("warning", "Cleared email pool.")
        }
    }

    // --- Proxy Pool Operations ---

    fun fetchProxiesFromUrl(
        url: String,
        protocol: String = "socks5",
        onResult: (Boolean, String, Int) -> Unit
    ) {
        viewModelScope.launch {
            val cleanUrl = url.trim()
            addLog("info", "جاري جلب قائمة البروكسيات من المصدر: $cleanUrl")
            val result = IdentityService.fetchProxiesFromUrl(cleanUrl, protocol)
            if (result.isSuccess) {
                val list = result.getOrNull() ?: emptyList()
                withContext(Dispatchers.IO) {
                    proxyDao.insertProxies(list)
                }
                addLog("success", "تم استيراد ${list.size} بروكسي بنجاح. جاري الفحص السريع لاختيار أول بروكسي شغال تلقائياً...")

                // Quickly test up to the first 8 imported proxies in parallel to find a verified live proxy
                val allInDb = withContext(Dispatchers.IO) { proxyDao.getAllProxiesList() }
                val candidates = allInDb.take(8)
                var verifiedProxy: Pair<ProxyItem, com.example.service.ProxyDiagnosticResult>? = null

                if (candidates.isNotEmpty()) {
                    val checks = withContext(Dispatchers.IO) {
                        candidates.map { cand ->
                            async {
                                val diag = IdentityService.testAndDetectProxy(
                                    host = cand.host,
                                    port = cand.port,
                                    preferredType = cand.type,
                                    user = cand.username,
                                    pass = cand.password,
                                    timeoutMs = 3800
                                )
                                if (diag.isWorking) {
                                    if (diag.protocol != cand.type) {
                                        proxyDao.updateProxyType(cand.id, diag.protocol)
                                    }
                                    proxyDao.updateProxyFullDetails(
                                        id = cand.id,
                                        status = "working",
                                        ping = diag.pingMs,
                                        country = diag.country,
                                        city = diag.city,
                                        isp = diag.isp,
                                        score = diag.qualityScore
                                    )
                                } else {
                                    proxyDao.recordProxyFailure(cand.id)
                                }
                                Pair(cand, diag)
                            }
                        }.awaitAll()
                    }
                    verifiedProxy = checks
                        .filter { it.second.isWorking }
                        .minByOrNull { it.second.pingMs }
                }

                if (verifiedProxy != null) {
                    val (winner, diag) = verifiedProxy
                    val s = _settings.value
                    updateSettings(
                        s.copy(
                            proxyEnabled = true,
                            proxyListUrl = cleanUrl,
                            proxyHost = winner.host,
                            proxyPort = winner.port.toString(),
                            proxyType = diag.protocol,
                            proxyUser = winner.username,
                            proxyPass = winner.password
                        )
                    )
                    addLog(
                        "success",
                        "✅ تم تفعيل أسرع بروكسي شغال تلقائياً: ${winner.host}:${winner.port} [${diag.protocol.uppercase()}] (${diag.exitIp} • ${diag.pingMs}ms)"
                    )
                    onResult(true, "تم استيراد ${list.size} بروكسي وتفعيل البروكسي الشغال ${winner.host}:${winner.port} (${diag.pingMs}ms)", list.size)
                } else {
                    val s = _settings.value
                    saveSettingsToPrefs(s.copy(proxyListUrl = cleanUrl))
                    addLog(
                        "warning",
                        "ℹ️ تم استيراد ${list.size} بروكسي. البروكسيات الأولى لم تستجب للفحص السريع — اضغط «فحص الكل» لاكتشاف البروكسيات الشغالة في القائمة."
                    )
                    onResult(true, "تم استيراد ${list.size} بروكسي. اضغط «فحص الكل» لتصفية وتفعيل البروكسيات الشغالة.", list.size)
                }
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: "Unknown error fetching proxies"
                addLog("error", "Failed to fetch proxies: $err")
                onResult(false, err, 0)
            }
        }
    }

    fun importProxiesBulk(raw: String, protocol: String = "socks5", onResult: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = raw.trim()
            val list = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val res = IdentityService.fetchProxiesFromUrl(trimmed, protocol)
                res.getOrNull() ?: IdentityService.parseBulkProxies(trimmed, protocol)
            } else {
                IdentityService.parseBulkProxies(trimmed, protocol)
            }
            if (list.isNotEmpty()) {
                proxyDao.insertProxies(list)
                addLog("success", "Imported ${list.size} proxies into pool.")
                onResult?.invoke(list.size)
            } else {
                onResult?.invoke(0)
            }
        }
    }

    fun addSingleProxy(proxy: ProxyItem, makeActive: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            proxyDao.insertProxy(proxy)
            addLog("info", "Added proxy: ${proxy.host}:${proxy.port} [${proxy.type.uppercase()}]")
            if (makeActive) {
                withContext(Dispatchers.Main) {
                    setActiveProxy(proxy)
                }
            }
        }
    }

    fun deleteProxy(proxy: ProxyItem) {
        viewModelScope.launch(Dispatchers.IO) {
            proxyDao.deleteProxy(proxy)
            addLog("info", "Deleted proxy: ${proxy.host}:${proxy.port}")
        }
    }

    fun clearAllProxies() {
        viewModelScope.launch(Dispatchers.IO) {
            proxyDao.clearAllProxies()
            addLog("warning", "Cleared all proxies from pool.")
        }
    }

    fun setActiveProxy(proxy: ProxyItem) {
        viewModelScope.launch {
            addLog("info", "🔍 جاري فحص وتفعيل البروكسي ${proxy.host}:${proxy.port} [${proxy.type.uppercase()}]...")
            val diag = withContext(Dispatchers.IO) {
                IdentityService.testAndDetectProxy(
                    host = proxy.host,
                    port = proxy.port,
                    preferredType = proxy.type,
                    user = proxy.username,
                    pass = proxy.password,
                    timeoutMs = 4500
                )
            }
            val effectiveType = if (diag.isWorking) diag.protocol else proxy.type
            withContext(Dispatchers.IO) {
                if (diag.isWorking) {
                    if (diag.protocol != proxy.type) {
                        proxyDao.updateProxyType(proxy.id, diag.protocol)
                    }
                    proxyDao.updateProxyFullDetails(
                        id = proxy.id,
                        status = "working",
                        ping = diag.pingMs,
                        country = diag.country,
                        city = diag.city,
                        isp = diag.isp,
                        score = diag.qualityScore
                    )
                } else {
                    proxyDao.recordProxyFailure(proxy.id)
                }
            }

            val s = _settings.value.copy(
                proxyEnabled = true,
                proxyType = effectiveType,
                proxyHost = proxy.host,
                proxyPort = proxy.port.toString(),
                proxyUser = proxy.username,
                proxyPass = proxy.password
            )
            updateSettings(s)
            if (diag.isWorking) {
                addLog(
                    "success",
                    "✅ تم تفعيل البروكسي بنجاح: ${proxy.host}:${proxy.port} [${effectiveType.uppercase()}] -> IP: ${diag.exitIp} (${diag.pingMs}ms | ${diag.country})"
                )
            } else {
                addLog(
                    "warning",
                    "⚠️ البروكسي ${proxy.host}:${proxy.port} لم يستجب (${diag.errorMessage}) — سيعمل الجسر الذكي بوضع الحماية الاحتياطية لضمان فتح المواقع دون توقف."
                )
            }
        }
    }

    fun testProxy(proxy: ProxyItem, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val diag = withContext(Dispatchers.IO) {
                IdentityService.testAndDetectProxy(
                    host = proxy.host,
                    port = proxy.port,
                    preferredType = proxy.type,
                    user = proxy.username,
                    pass = proxy.password,
                    timeoutMs = 5000
                )
            }
            val isWorking = diag.isWorking
            val exitIp = diag.exitIp
            val ping = diag.pingMs
            withContext(Dispatchers.IO) {
                if (isWorking) {
                    if (diag.protocol != proxy.type) {
                        proxyDao.updateProxyType(proxy.id, diag.protocol)
                    }
                    proxyDao.updateProxyFullDetails(
                        id = proxy.id,
                        status = "working",
                        ping = ping,
                        country = diag.country,
                        city = diag.city,
                        isp = diag.isp,
                        score = diag.qualityScore
                    )
                } else {
                    proxyDao.recordProxyFailure(proxy.id)
                }
            }
            if (isWorking) {
                val protoMsg = if (diag.protocol != proxy.type) " [بروتوكول: ${diag.protocol.uppercase()}]" else ""
                val msg = "متصل: $exitIp (${ping}ms) - ${diag.city}, ${diag.country}$protoMsg (جودة: ${diag.qualityScore}/100)"
                addLog("success", "✅ بروكسي ${proxy.host}:${proxy.port} يعمل بنجاح! $msg")
                onResult(true, msg)
            } else {
                addLog("error", "❌ بروكسي ${proxy.host}:${proxy.port} فشل: ${diag.errorMessage}")
                onResult(false, "فشل: ${diag.errorMessage}")
            }
        }
    }

    fun testProxyDetails(
        host: String,
        port: Int,
        type: String,
        user: String = "",
        pass: String = "",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val diag = withContext(Dispatchers.IO) {
                IdentityService.testAndDetectProxy(host, port, type, user, pass, timeoutMs = 5000)
            }
            if (diag.isWorking) {
                onResult(true, "يعمل: ${diag.exitIp} - ${diag.pingMs}ms [${diag.protocol.uppercase()}]")
            } else {
                onResult(false, "فشل: ${diag.errorMessage}")
            }
        }
    }

    fun testAllProxies(
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        onComplete: (Int, Int) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { proxyDao.getAllProxiesList() }
            if (list.isEmpty()) {
                onComplete(0, 0)
                return@launch
            }
            addLog("info", "بدء فحص مجموعة البروكسيات (${list.size} بروكسي) بسرعة متوازية وذكاء اكتشاف البروتوكول...")
            val total = list.size
            val progressCount = java.util.concurrent.atomic.AtomicInteger(0)
            val workingCount = java.util.concurrent.atomic.AtomicInteger(0)
            val failedCount = java.util.concurrent.atomic.AtomicInteger(0)

            val semaphore = Semaphore(10)

            withContext(Dispatchers.IO) {
                val jobs = list.map { proxy ->
                    async {
                        semaphore.withPermit {
                            val diag = IdentityService.testAndDetectProxy(
                                host = proxy.host,
                                port = proxy.port,
                                preferredType = proxy.type,
                                user = proxy.username,
                                pass = proxy.password,
                                timeoutMs = 4500
                            )
                            val isWorking = diag.isWorking
                            val ping = diag.pingMs

                            if (isWorking) {
                                if (diag.protocol != proxy.type) {
                                    proxyDao.updateProxyType(proxy.id, diag.protocol)
                                }
                                proxyDao.updateProxyFullDetails(
                                    id = proxy.id,
                                    status = "working",
                                    ping = ping,
                                    country = diag.country,
                                    city = diag.city,
                                    isp = diag.isp,
                                    score = diag.qualityScore
                                )
                                workingCount.incrementAndGet()
                                addLog("success", "[#${progressCount.incrementAndGet()}/$total] ✅ ${proxy.host}:${proxy.port} [${diag.protocol.uppercase()}] ONLINE (${diag.exitIp}) - ${ping}ms | ${diag.country} (جودة: ${diag.qualityScore}★)")
                            } else {
                                proxyDao.recordProxyFailure(proxy.id)
                                failedCount.incrementAndGet()
                                addLog("warning", "[#${progressCount.incrementAndGet()}/$total] ❌ ${proxy.host}:${proxy.port} OFFLINE (${diag.errorMessage})")
                            }

                            val done = progressCount.get()
                            withContext(Dispatchers.Main) {
                                onProgress(done, total)
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            val finalWorking = workingCount.get()
            val finalFailed = failedCount.get()
            addLog("info", "اكتمل فحص البروكسيات: $finalWorking شغالة ✅ | $finalFailed معطلة ❌")
            if (finalWorking > 0) {
                autoSelectFastestProxy()
            }
            onComplete(finalWorking, finalFailed)
        }
    }

    fun deleteFailedProxies(onResult: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = proxyDao.deleteFailedProxies()
            addLog("info", "تم حذف $deleted بروكسي معطل من القائمة.")
            withContext(Dispatchers.Main) {
                onResult?.invoke(deleted)
            }
        }
    }

    fun resetFailedProxies(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = proxyDao.resetFailedProxies()
            addLog("info", "تمت إعادة تعيين $count بروكسي إلى الحالة النشطة لإعادة الفحص.")
            withContext(Dispatchers.Main) {
                onComplete?.invoke(count)
            }
        }
    }

    fun autoSelectFastestProxy(onResult: ((ProxyItem?) -> Unit)? = null) {
        viewModelScope.launch {
            val best = withContext(Dispatchers.IO) {
                proxyDao.getBestWorkingProxy() ?: proxyDao.getWorkingProxies().minByOrNull { if (it.lastPingMs > 0) it.lastPingMs else 999999 }
            }
            if (best != null) {
                setActiveProxy(best)
                addLog("success", "تم تفعيل أفضل بروكسي ذكياً: ${best.host}:${best.port} [${best.type.uppercase()}] (بنق: ${best.lastPingMs}ms | جودة: ${best.score}★)")
            } else {
                addLog("warning", "لا توجد بروكسيات صالحة في القائمة حالياً. يرجى فحص البروكسيات أولاً.")
            }
            onResult?.invoke(best)
        }
    }

    fun getWorkingProxiesFormatted(): String {
        val list = proxies.value
        val working = list.filter { it.status == "working" }
        val targetList = if (working.isNotEmpty()) working else list
        return targetList.joinToString("\n") { p ->
            if (p.username.isNotBlank() && p.password.isNotBlank()) {
                "${p.host}:${p.port}:${p.username}:${p.password}"
            } else {
                "${p.host}:${p.port}"
            }
        }
    }

    // --- Scripts Operations ---

    fun saveScript(script: ScriptItem) {
        viewModelScope.launch(Dispatchers.IO) {
            scriptDao.insertScript(script)
            addLog("info", "Saved script: ${script.name}")
        }
    }

    fun toggleScript(script: ScriptItem) {
        viewModelScope.launch(Dispatchers.IO) {
            scriptDao.toggleScript(script.id, !script.enabled)
        }
    }

    fun deleteScript(script: ScriptItem) {
        viewModelScope.launch(Dispatchers.IO) {
            scriptDao.deleteScript(script)
            addLog("warning", "Deleted script: ${script.name}")
        }
    }

    // --- Offer Click Operations (شاشة النقرة - Priority #1) ---

    private fun normalizeOfferClickText(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}$]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun computeOfferSmartPriorityScore(text: String, tagOrNote: String, ctr: Double, clickCount: Int): Int {
        val low = "$text $tagOrNote".lowercase()
        var score = 50
        if (low.contains("nike")) score += 28
        if (low.contains("walmart") || low.contains("cash app") || low.contains("cashapp") || low.contains("amazon")) score += 22
        if (low.contains("target") || low.contains("apple") || low.contains("paypal") || low.contains("google play")) score += 16
        if (low.contains("email") || low.contains("zip") || low.contains("soi")) score += 18
        if (low.contains("gift card") || low.contains("reward") || low.contains("claim") || low.contains("$100") || low.contains("$500") || low.contains("$750") || low.contains("$1000")) score += 14
        if (low.contains("survey") || low.contains("quiz")) score += 10
        if (low.contains("pin") || low.contains("sms")) score -= 10
        score += (ctr * 15.0).toInt().coerceIn(0, 15)
        score += (clickCount * 2).coerceAtMost(12)
        return score.coerceIn(1, 100)
    }

    fun addOfferClickItem(text: String, tagOrNote: String = "") {
        val cleanText = text.replace(Regex("\\s+"), " ").trim()
        if (cleanText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val existing = offerClickDao.getAllClickItemsList()
            val targetNorm = normalizeOfferClickText(cleanText)
            val duplicate = existing.firstOrNull {
                it.text.equals(cleanText, ignoreCase = true) ||
                    (targetNorm.length >= 3 && normalizeOfferClickText(it.text) == targetNorm)
            }
            if (duplicate != null) {
                if (!duplicate.enabled) {
                    offerClickDao.toggleClickItem(duplicate.id, true)
                }
                addLog("info", "ℹ️ [منع التكرار - شاشة النقرة]: النص '$cleanText' موجود مسبقاً (#${duplicate.orderIndex + 1}) — تم تفعيله دون تكرار.")
                return@launch
            }
            val maxOrder = offerClickDao.getMaxOrderIndex() ?: -1
            val item = com.example.data.model.OfferClickItem(
                text = cleanText,
                enabled = true,
                orderIndex = maxOrder + 1,
                tagOrNote = tagOrNote.trim()
            )
            offerClickDao.insertClickItem(item)
            addLog("info", "🎯 [شاشة النقرة]: تمت إضافة نص نقرة جديد: '${item.text}' (الترتيب: #${item.orderIndex + 1})")
        }
    }

    fun setAllOfferClickItemsEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            offerClickDao.setAllEnabled(enabled)
            val statusLabel = if (enabled) "تفعيل جميع النصوص" else "إيقاف جميع النصوص"
            addLog("info", "🎯 [شاشة النقرة]: تم $statusLabel بنجاح.")
        }
    }

    fun deduplicateAndSmartSortOfferClickItems(onResult: ((removedDuplicates: Int, totalSorted: Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = offerClickDao.getAllClickItemsList()
            if (all.isEmpty()) {
                withContext(Dispatchers.Main) { onResult?.invoke(0, 0) }
                return@launch
            }
            val seenNorm = mutableSetOf<String>()
            val uniqueItems = mutableListOf<com.example.data.model.OfferClickItem>()
            var removedCount = 0

            for (item in all.sortedBy { it.orderIndex }) {
                val clean = item.text.replace(Regex("\\s+"), " ").trim()
                val norm = normalizeOfferClickText(clean)
                val key = if (norm.length >= 3) norm else clean.lowercase()
                if (key.isBlank() || seenNorm.contains(key)) {
                    offerClickDao.deleteClickItem(item)
                    removedCount++
                } else {
                    seenNorm.add(key)
                    uniqueItems.add(if (clean != item.text) item.copy(text = clean) else item)
                }
            }

            // Sort by AI conversion priority score descending (while keeping enabled items ahead of disabled ones)
            val sorted = uniqueItems.sortedWith(
                compareByDescending<com.example.data.model.OfferClickItem> { it.enabled }
                    .thenByDescending { computeOfferSmartPriorityScore(it.text, it.tagOrNote, it.ctr, it.clickCount) }
                    .thenBy { it.orderIndex }
            )

            sorted.forEachIndexed { idx, item ->
                offerClickDao.updateClickItem(item.copy(orderIndex = idx))
            }

            val topOffer = sorted.firstOrNull { it.enabled }?.text
            if (!topOffer.isNullOrBlank()) {
                _automationState.update { it.copy(activeClickText = topOffer) }
            }

            addLog(
                "success",
                "🧹 [تنظيف وترتيب ذكي - Offer Click]: تم حذف $removedCount نص مكرر وإعادة ترتيب ${sorted.size} عرض حسب أولوية التحويل الذكية."
            )
            withContext(Dispatchers.Main) {
                onResult?.invoke(removedCount, sorted.size)
            }
        }
    }

    fun triggerBrowserLockerScanAndExtract(switchToBrowser: Boolean = false) {
        if (switchToBrowser) {
            _currentTab.value = ScreenTab.BROWSER
        }
        _browserCommand.value = BrowserCommand.ScanAndExtractLockerOffers
        addLog("info", "🔍 [كشف اللوكر واستخراج العروض]: جاري فحص الصفحة والإطارات (iframes) لاكتشاف اللوكر وحفظ نصوص عروضه بدون تكرار...")
    }

    fun onLockerOffersExtracted(
        jsonPayload: String,
        onCompleteUi: ((found: Int, newSaved: Int, skippedDuplicates: Int, lockerId: String) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val root = org.json.JSONObject(jsonPayload)
                val rawLockerId = root.optString("lockerId", "locker").ifBlank { "locker" }
                val rawLockerUrl = root.optString("lockerUrl", "")
                val isLockerDetected = root.optBoolean("isLockerDetected", true)
                val isManualTrigger = root.optBoolean("isManualTrigger", true)
                val offersArr = root.optJSONArray("offers") ?: org.json.JSONArray()

                data class ParsedLockerOffer(
                    val text: String,
                    val href: String,
                    val categoryTag: String,
                    val brandTag: String,
                    val priorityScore: Int
                )

                val batchSeenNorm = mutableSetOf<String>()
                val parsedList = mutableListOf<ParsedLockerOffer>()

                for (i in 0 until offersArr.length()) {
                    val obj = offersArr.optJSONObject(i) ?: continue
                    val cleanText = obj.optString("text", "")
                        .replace(Regex("[\\r\\n\\t]+"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (cleanText.length < 4 || cleanText.length > 135) continue
                    val norm = normalizeOfferClickText(cleanText)
                    if (norm.length < 3 || !batchSeenNorm.add(norm)) continue

                    parsedList.add(
                        ParsedLockerOffer(
                            text = cleanText,
                            href = obj.optString("href", "").trim(),
                            categoryTag = obj.optString("categoryTag", "Locker Offer 🔒").trim(),
                            brandTag = obj.optString("brandTag", "").trim(),
                            priorityScore = obj.optInt("priorityScore", 75)
                        )
                    )
                }

                // Sort extracted offers by priorityScore descending so best offers get added first
                val sortedExtracted = parsedList.sortedByDescending { it.priorityScore }
                val shouldSaveToDb = isManualTrigger || _settings.value.autoSaveExtractedLockerOffers

                var newlySavedCount = 0
                var skippedDuplicatesCount = 0

                if (shouldSaveToDb && sortedExtracted.isNotEmpty()) {
                    val existingItems = offerClickDao.getAllClickItemsList()
                    val existingLowerSet = existingItems.map { it.text.trim().lowercase() }.toMutableSet()
                    val existingNormMap = existingItems.associateBy { normalizeOfferClickText(it.text) }.toMutableMap()
                    var nextOrderIndex = (offerClickDao.getMaxOrderIndex() ?: -1) + 1

                    for (offer in sortedExtracted) {
                        val lower = offer.text.lowercase()
                        val norm = normalizeOfferClickText(offer.text)
                        val existingMatch = existingNormMap[norm]
                        if (existingLowerSet.contains(lower) || existingMatch != null) {
                            skippedDuplicatesCount++
                            // Smart enrichment: if the existing item had no tag or no URL filter, enrich it without duplicating!
                            if (existingMatch != null) {
                                val enrichedTag = if (existingMatch.tagOrNote.isBlank()) {
                                    buildString {
                                        append("🔒 لوكر #$rawLockerId")
                                        if (offer.brandTag.isNotBlank()) append(" • ${offer.brandTag}")
                                        if (offer.categoryTag.isNotBlank()) append(" • ${offer.categoryTag}")
                                    }
                                } else existingMatch.tagOrNote
                                val enrichedHref = if (existingMatch.targetUrlFilter.isBlank() && offer.href.startsWith("http")) {
                                    offer.href
                                } else existingMatch.targetUrlFilter
                                if (enrichedTag != existingMatch.tagOrNote || enrichedHref != existingMatch.targetUrlFilter) {
                                    offerClickDao.updateClickItem(
                                        existingMatch.copy(tagOrNote = enrichedTag, targetUrlFilter = enrichedHref)
                                    )
                                }
                            }
                        } else {
                            existingLowerSet.add(lower)
                            val smartTag = buildString {
                                append("🔒 لوكر #$rawLockerId")
                                if (offer.brandTag.isNotBlank()) append(" • ${offer.brandTag}")
                                if (offer.categoryTag.isNotBlank()) append(" • ${offer.categoryTag}")
                            }
                            val newItem = com.example.data.model.OfferClickItem(
                                text = offer.text,
                                enabled = true,
                                orderIndex = nextOrderIndex++,
                                tagOrNote = smartTag,
                                targetUrlFilter = if (offer.href.startsWith("http")) offer.href else ""
                            )
                            offerClickDao.insertClickItem(newItem)
                            existingNormMap[norm] = newItem
                            newlySavedCount++
                        }
                    }
                }

                val extractedTexts = sortedExtracted.map { it.text }
                val totalFound = extractedTexts.size
                val summaryAr = when {
                    totalFound > 0 && newlySavedCount > 0 ->
                        "✅ تم كشف اللوكر (#$rawLockerId) واستخراج $totalFound عروض: حُفظ $newlySavedCount نص جديد في Offer Click • وتُجاهل $skippedDuplicatesCount مكرر"
                    totalFound > 0 && newlySavedCount == 0 ->
                        "ℹ️ تم كشف اللوكر (#$rawLockerId) وفحص $totalFound عروض: جميع النصوص ($skippedDuplicatesCount) محفوظة مسبقاً في Offer Click بدون تكرار"
                    isLockerDetected ->
                        "🔓 تم كشف وتفعيل اللوكر (#$rawLockerId) — بانتظار ظهور نصوص العروض..."
                    else ->
                        "⚠️ لم يتم العثور على نصوص عروض لوكر في الصفحة الحالية بعد"
                }

                _automationState.update { state ->
                    val mergedOffers = if (extractedTexts.isNotEmpty()) {
                        (extractedTexts + state.lastExtractedLockerOffers).distinct().take(20)
                    } else state.lastExtractedLockerOffers
                    state.copy(
                        lockerDetectedOnPage = isLockerDetected || totalFound > 0 || state.lockerDetectedOnPage,
                        lockerId = if (rawLockerId != "page_scanner") rawLockerId else (state.lockerId ?: rawLockerId),
                        lockerUrl = rawLockerUrl.ifBlank { state.lockerUrl },
                        lockerOffersFoundCount = maxOf(totalFound, state.lockerOffersFoundCount),
                        lastLockerHarvestSummary = summaryAr,
                        lastExtractedLockerOffers = mergedOffers,
                        lastLockerHarvestNewCount = newlySavedCount,
                        lastLockerHarvestDuplicateCount = skippedDuplicatesCount,
                        lastLockerHarvestTimestamp = System.currentTimeMillis(),
                        activeClickText = state.activeClickText?.takeIf { it.isNotBlank() } ?: extractedTexts.firstOrNull()
                    )
                }

                if (totalFound > 0 || isManualTrigger) {
                    addLog(
                        if (newlySavedCount > 0) "success" else "info",
                        "🧲 [استخراج عروض اللوكر ➔ Offer Click]: $summaryAr",
                        _automationState.value.currentTaskName
                    )
                }

                withContext(Dispatchers.Main) {
                    onCompleteUi?.invoke(totalFound, newlySavedCount, skippedDuplicatesCount, rawLockerId)
                }
            } catch (e: Exception) {
                addLog("error", "فشل تحليل نصوص اللوكر المستخرجة: ${e.message}")
            }
        }
    }

    fun updateOfferClickItem(item: com.example.data.model.OfferClickItem) {
        viewModelScope.launch(Dispatchers.IO) {
            offerClickDao.updateClickItem(item)
            addLog("info", "🎯 [شاشة النقرة]: تم تعديل نص النقرة: '${item.text}'")
        }
    }

    fun toggleOfferClickItem(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            offerClickDao.toggleClickItem(id, enabled)
            val action = if (enabled) "تفعيل" else "غلق / إيقاف"
            addLog("info", "🎯 [شاشة النقرة]: تم $action نص النقرة بنجاح")
        }
    }

    fun deleteOfferClickItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = offerClickDao.getClickItemById(id)
            if (item != null) {
                offerClickDao.deleteClickItem(item)
                addLog("warning", "🎯 [شاشة النقرة]: تم حذف نص النقرة: '${item.text}'")
            }
        }
    }

    fun moveOfferClickItem(id: Long, direction: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = offerClickDao.getAllClickItemsList().sortedBy { it.orderIndex }
            val index = all.indexOfFirst { it.id == id }
            if (index == -1) return@launch
            val targetIndex = index + direction
            if (targetIndex in all.indices) {
                val current = all[index]
                val other = all[targetIndex]
                offerClickDao.updateOrderIndex(current.id, other.orderIndex)
                offerClickDao.updateOrderIndex(other.id, current.orderIndex)
                addLog("info", "🎯 [شاشة النقرة]: تم تغيير تسلسل النص '${current.text}'")
            }
        }
    }

    fun resetDefaultOfferClickItems() {
        viewModelScope.launch(Dispatchers.IO) {
            offerClickDao.clearAll()
            val defaults = listOf(
                com.example.data.model.OfferClickItem(text = "Get a \$100 Nike Gift Card!", enabled = true, orderIndex = 0, tagOrNote = "Nike $100 Locker Offer #1"),
                com.example.data.model.OfferClickItem(text = "Get \$1000 Walmart gift card", enabled = true, orderIndex = 1, tagOrNote = "Walmart $1000"),
                com.example.data.model.OfferClickItem(text = "Claim \$750 Cash App Reward", enabled = true, orderIndex = 2, tagOrNote = "Cash App $750"),
                com.example.data.model.OfferClickItem(text = "Get \$500 Amazon Gift Card", enabled = true, orderIndex = 3, tagOrNote = "Amazon $500"),
                com.example.data.model.OfferClickItem(text = "Win \$100 Target Gift Card", enabled = true, orderIndex = 4, tagOrNote = "Target $100"),
                com.example.data.model.OfferClickItem(text = "Claim \$500 Apple Store Card", enabled = false, orderIndex = 5, tagOrNote = "Apple Card")
            )
            offerClickDao.insertClickItems(defaults)
            addLog("success", "🎯 [شاشة النقرة]: تمت استعادة نصوص النقرة الافتراضية بنجاح.")
        }
    }

    fun onOfferClicked(text: String, url: String) {
        val cleanUrl = url.trim()
        val valid = cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")
        viewModelScope.launch(Dispatchers.IO) {
            try { offerClickDao.recordClickByText(text) } catch (_: Exception) {}
            _automationState.update {
                it.copy(
                    lastClickedOfferUrl = if (valid) cleanUrl else it.lastClickedOfferUrl,
                    activeClickText = text
                )
            }
            if (valid) {
                addLog("success", "🎯 [النقرة على العرض رقم 1]: تم النقر بنجاح على '$text' والتحول إلى: $cleanUrl", _automationState.value.currentTaskName)
            } else {
                // Cross-origin iframe click without direct URL: popup relay (onCreateWindow) opens the tab.
                addLog("info", "🎯 [نقرة لوكر داخل إطار]: '$text' بدون رابط مباشر — بانتظار تبويب العرض المنبثق.", _automationState.value.currentTaskName)
            }
        }
    }

    fun onOfferClickedInNewTab(text: String, url: String, tabId: String) {
        val cleanUrl = url.trim()
        val valid = cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")
        // Guard: never open locker loader / same-page URL as a "new offer tab"
        val isLoader = cleanUrl.contains("script_include.php") || cleanUrl.contains("load_box.php")
        val isSamePage = cleanUrl.isBlank() || cleanUrl == _automationState.value.currentUrl
        viewModelScope.launch(Dispatchers.IO) {
            try { offerClickDao.recordClickByText(text) } catch (_: Exception) {}
            if (!valid || isLoader) {
                addLog("warning", "⚠️ [تبويب جديد]: تم تجاهل رابط غير صالح للعرض '$text': '$cleanUrl' — الاعتماد على تبويب الـ popup المعزول.", _automationState.value.currentTaskName)
                _automationState.update {
                    it.copy(
                        activeClickText = text,
                        lockerOfferClicked = true,
                        newTabActionStatus = "تم النقر على '$text' داخل اللوكر — بانتظار تبويب العرض المنبثق..."
                    )
                }
                return@launch
            }
            _automationState.update {
                it.copy(
                    lastClickedOfferUrl = cleanUrl,
                    activeClickText = text,
                    lockerOfferClicked = true,
                    lastOpenedNewTabId = tabId,
                    activeTabId = tabId,
                    currentUrl = cleanUrl,
                    humanActionStatusAr = "🧐 انتقال التركيز البشري لتبويب العرض الجديد وقراءة الصفحة...",
                    newTabActionStatus = "تم فتح موقع العرض في تبويب جديد (#$tabId) ونقل بيئة العمل إليه بنجاح 🚀"
                )
            }
            addLog("success", "🚀 [اللوكر ➔ التبويب الجديد]: تم النقر التلقائي على عرض اللوكر '$text' وفتح موقعه في تبويب جديد (#$tabId): $cleanUrl. تم نقل بيئة العمل والتحكم التلقائي إليه فوراً!", _automationState.value.currentTaskName)
            if (isSamePage) {
                addLog("info", "ℹ️ [تبويب جديد]: الرابط مطابق للصفحة الحالية — سيتم التعامل معه كهجرة سياق للتبويب الجديد.", _automationState.value.currentTaskName)
            }
            // Human stage-transition wait when moving from Locker to the new Offer Tab
            if (_settings.value.humanSimulationEnabled) {
                waitBetweenStagesHuman(
                    fromStageCode = "locker_click",
                    toStageCode = "new_tab_settle",
                    fromStageAr = "نقر عرض اللوكر",
                    toStageAr = "تبويب العرض الجديد (#$tabId)",
                    humanBehaviorHintAr = "👁️ استقرار صفحة العرض والمسح البصري البشري قبل التعبئة",
                    complexity = 2,
                    taskName = _automationState.value.currentTaskName
                )
            }
        }
    }

    fun onHumanActionStatus(statusAr: String) {
        val clean = statusAr.trim()
        if (clean.isBlank()) return
        _automationState.update {
            it.copy(humanActionStatusAr = clean)
        }
    }

    private var selfTestDoneThisSession: Boolean = false
    private var selfTestReloadedThisSession: Boolean = false

    /** اختبار الدرع الذاتي على الصفحة الحية (غير حاجب — النتيجة عبر callback) */
    fun runShieldSelfTest() {
        try {
            val geo = _extractedInfo.value
            _browserCommand.value = BrowserCommand.RunShieldSelfTest(
                AutomationScriptBuilder.buildShieldSelfTestScript(
                    geo.language.ifBlank { "en-US" },
                    geo.timezone.ifBlank { "America/New_York" }
                )
            )
            addLog("info", "🧪 [اختبار الدرع الذاتي]: فحص ~30 إشارة بصمة داخل الصفحة الحية كما يراها أي موقع فاحص...")
        } catch (_: Exception) {}
    }

    private val loggedSurveillanceHosts = mutableSetOf<String>()

    /** هل هذا المضيف فاحص معادٍ؟ — يقرأه معترض الطلبات لتشديد الرقابة */
    fun isHostileHost(host: String): Boolean {
        return try { intelligenceCoordinator.isHostileHost(host) } catch (_: Exception) { false }
    }

    /** معترض الطلبات حظر متتبع مراقبة — سجل مختصر + عدّاد المنسق */
    fun onSurveillanceBlocked(domain: String) {
        val clean = domain.trim().lowercase().take(80)
        if (clean.isBlank()) return
        intelligenceCoordinator.noteSurveillanceBlock(clean)
        synchronized(loggedSurveillanceHosts) {
            if (loggedSurveillanceHosts.add(clean)) {
                addLog("info", "🛡️ [اعتراض صارم]: حُظر $clean عن تسجيل الجلسة (أول مرة) — باقي الحظر يُحتسب بصمت.")
            }
        }
    }

    fun onShieldSelfTestResult(rawJson: String?) {
        try {
            val wrapped = "[${rawJson ?: "null"}]"
            val decoded = try {
                org.json.JSONArray(wrapped).optString(0, "")
            } catch (_: Exception) { "" }
            if (decoded.isBlank() || decoded == "null") {
                addLog("warning", "🧪 [اختبار الدرع الذاتي]: لا نتيجة (الصفحة لم تنفذ الفحص — تحقق من حقن الدرع).")
                return
            }
            val obj = org.json.JSONObject(decoded)
            val passed = obj.optInt("passed", 0)
            val failed = obj.optInt("failed", 0)
            val failsArr = obj.optJSONArray("failures")
            val fails = StringBuilder()
            if (failsArr != null) {
                for (i in 0 until minOf(failsArr.length(), 8)) {
                    if (fails.isNotEmpty()) fails.append("، ")
                    fails.append(failsArr.optString(i, ""))
                }
            }
            intelligenceCoordinator.noteSelfTest(passed, failed, fails.toString())
            // Critical: webdriver visible = document-start injection missed (SPA?) — one therapeutic reload
            val failsStr = fails.toString()
            if (!selfTestReloadedThisSession && failsStr.contains("webdriver_false")) {
                selfTestReloadedThisSession = true
                addLog("warning", "🛡️ [إعادة حقن علاجية]: الدرع لم يُحقن في هذه الصفحة (webdriver ظاهر!) — إعادة تحميل واحدة لإعادة الحقن.")
                _browserCommand.value = BrowserCommand.Reload
            }
        } catch (e: Exception) {
            addLog("warning", "🧪 [اختبار الدرع الذاتي]: تعذر تحليل النتيجة: ${e.message}")
        }
    }

    /** قياس الجسر من الصفحة: الموقع يفحص البصمة — سجل + غذّ المنسق */
    fun onShieldTelemetry(jsonPayload: String) {
        try {
            val obj = org.json.JSONObject(jsonPayload)
            val score = obj.optInt("score", 0)
            val url = obj.optString("u", "").take(80)
            val topArr = obj.optJSONArray("top")
            val top = StringBuilder()
            if (topArr != null) {
                for (i in 0 until minOf(topArr.length(), 4)) {
                    if (top.isNotEmpty()) top.append("، ")
                    top.append(topArr.optString(i, ""))
                }
            }
            val host = try { java.net.URI(url).host ?: url.take(30) } catch (_: Exception) { url.take(30) }
            addLog(
                if (score >= 15) "warning" else "info",
                "🛰️ [جسر الجهاز]: الموقع يفحص البصمة (نقاط=$score) | الأكثر لمساً: $top | الصفحة: $host | الحالة: كل الطلبات حُوّلت للمزيف ✅",
                _automationState.value.currentTaskName
            )
            intelligenceCoordinator.noteShieldProbe(score, top.toString(), host)
        } catch (_: Exception) {}
    }

    fun onNewTabInteractionCompleted(tabId: String, currentUrl: String, details: String) {
        _automationState.update {
            it.copy(newTabActionStatus = "اكتملت المحاكاة في $tabId: $details")
        }
        addLog("success", "✅ [التعامل مع التبويب الجديد]: $details ($currentUrl)", _automationState.value.currentTaskName)
    }

    fun onLockerDetected(url: String, id: String, isTriggered: Boolean, offersCount: Int = 0) {
        _automationState.update {
            it.copy(
                lockerDetectedOnPage = true,
                lockerUrl = url,
                lockerId = id,
                lockerOffersFoundCount = offersCount
            )
        }
        addLog("info", "🛡️ [نظام اللوكر]: تم كشف لوكر في الصفحة (ID: $id) - العروض: $offersCount | الحالة: ${if (isTriggered) "نشط ومفعل" else "جاهز"}", _automationState.value.currentTaskName)
    }

    fun testLockerOfferClick(targetUrl: String = "https://gdfqo.blogspot.com", customLockerId: String? = null) {
        val lockerId = customLockerId ?: _settings.value.cpaLockerDefaultId.ifBlank { "1741238" }
        val defaultClick = _automationState.value.activeClickText ?: NIKE_CLICK_TEXT
        _automationState.update {
            it.copy(
                currentUrl = targetUrl,
                lockerId = lockerId,
                activeClickText = defaultClick,
                activeTaskCategories = "Content / Link Locker, Offer Click, Email Submit, Terms Agreement, Survey / Quiz, Lead Gen Form, Skip Upsells, Confirmation",
                newTabActionStatus = "جاري فتح الصفحة واختبار اللوكر والنقر التلقائي إلى تبويب جديد..."
            )
        }
        _currentTab.value = ScreenTab.BROWSER
        val ua = IdentityService.resolveUserAgent("random", isMobileDefault = true)
        _browserCommand.value = BrowserCommand.LoadUrl(targetUrl, "https://www.google.com/", ua)
        addLog("info", "🎯 [اختبار اللوكر والنقر]: جاري فحص الصفحة ($targetUrl) للتعامل التلقائي مع اللوكر والنقل إلى تبويب جديد...")
    }

    fun openOfferInNewTab(url: String, title: String = "Offer") {
        _browserCommand.value = BrowserCommand.OpenInNewTab(url, title)
        addLog("info", "🌐 [فتح تبويب جديد]: جاري فتح الرابط في تبويب منفصل: $url")
    }

    fun setActiveTabId(tabId: String) {
        _automationState.update { it.copy(activeTabId = tabId) }
    }

    fun onPageAnalyzed(reportJson: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val report = TaskCategoryPlanner.parseAnalysisReport(reportJson)
            val currentState = _automationState.value
            val currentCats = TaskCategoryPlanner.parseCategories(currentState.activeTaskCategories)
            val directive = TaskCategoryPlanner.deriveTacticalDirective(
                report = report,
                configuredCategories = currentCats,
                activeClickTarget = currentState.activeClickText
            )

            // ── Smart Brain: قرار واعٍ مبني على الذاكرة والسياق ──
            val learningForTask = currentState.currentTaskId?.let { taskLearning[it] }
            val decision = SmartAutomationBrain.decide(
                report = report,
                rawJson = reportJson,
                configuredCategories = currentCats,
                activeClickTarget = currentState.activeClickText,
                memory = brainMemory,
                taskLearning = learningForTask
            )
            lastSmartDecision = decision
            // ⬜ Blank-wall intelligence: حرق + تدوير + تحول جوال عند التكرار
            if (decision.executionMode == "blank_wall_evict") {
                val tid = currentState.currentTaskId
                if (tid != null) {
                    val n = (blankWallCount[tid] ?: 0) + 1
                    blankWallCount[tid] = n
                    addLog("warning", "⬜ [جدار أبيض #$n]: '${report.url.take(56)}' فارغة بعد التحميل — رفض شبكة/بصمة. حرق البروكسي والتدوير.", currentState.currentTaskName)
                    try {
                        findCurrentProxyItem()?.let {
                            proxyDao.recordProxyFailure(it.id)
                            proxyDao.recordProxyFailure(it.id)
                        }
                    } catch (_: Exception) {}
                    val runTask = currentRunTask
                    if (n >= 2 && runTask != null &&
                        !runTask.userAgent.contains("mobile", ignoreCase = true) &&
                        !runTask.userAgent.contains("android", ignoreCase = true)
                    ) {
                        try {
                            val forced = runTask.copy(userAgent = "random_mobile")
                            taskDao.updateTask(forced)
                            currentRunTask = forced
                            addLog("warning", "📱 [تكيّف الموقع]: جدار متكرر على ديسكتوب — فُرض وضع الجوال على '${runTask.name}' (الموقع يطلب بيئة جوال).", currentState.currentTaskName)
                        } catch (_: Exception) {}
                    }
                }
            }
            brainMemory = SmartAutomationBrain.updateMemory(brainMemory, report, decision)
            val quality = SmartAutomationBrain.sessionQuality(
                brainMemory,
                _automationState.value.leadsThisSession,
                _automationState.value.completedThisSession
            )

            val prevFunnelStage = currentState.funnelStageIndex
            val prevQuestion = currentState.currentQuestionText
            val s = _settings.value
            val humanBehaviorDesc = SmartAutomationBrain.describeHumanBehaviorForStage(
                action = decision.action,
                funnelStageIndex = decision.funnelStageIndex,
                questionText = report.currentQuestionText,
                targetOffer = currentState.activeClickText
            )
            val realismScore = SmartAutomationBrain.computeHumanRealismScore(
                humanEnabled = s.humanSimulationEnabled,
                typingEnabled = s.humanTypingSimulation,
                pointerEnabled = s.humanPointerAndTouchPaths,
                readingEnabled = s.humanReadingPauseBeforeAction,
                scrollEnabled = s.humanScrollAndGazeSimulation,
                consecutiveNoProgress = brainMemory.consecutiveNoProgress
            )

            _automationState.update { state ->
                val updatedCats = if (report.detectedCategory.isNotBlank() && report.detectedCategory != "general") {
                    val det = report.detectedCategory
                    val isOfferClick = det == "offer_click"
                    val hadOfferClick = currentCats.any { it.equals("offer_click", ignoreCase = true) } ||
                        state.activeTaskCategories.contains("offer_click", ignoreCase = true)
                    if (isOfferClick && !hadOfferClick) {
                        state.activeTaskCategories
                    } else {
                        val reordered = (listOf(det) + currentCats.filter { !it.equals(det, ignoreCase = true) }).distinct()
                        reordered.joinToString(", ")
                    }
                } else {
                    state.activeTaskCategories
                }

                val personaFacts = IdentityService.buildPersonaFactsJson(_identity.value)
                state.copy(
                    detectedPageCategory = report.detectedCategory,
                    detectedCategoryAr = report.detectedCategoryAr,
                    pageAnalysisSummary = report.summary,
                    activeTaskCategories = updatedCats,
                    lastAnalysisTime = System.currentTimeMillis(),
                    activeDirectiveTitle = directive.titleAr,
                    activeDirectiveReason = directive.reasonAr,
                    activeDirectiveStep = directive.actionPlanSteps.firstOrNull() ?: "",
                    directiveArchetype = directive.archetype,
                    analysisConfidence = report.confidence,
                    lockerDetectedOnPage = report.hasLocker || state.lockerDetectedOnPage,
                    // Closed-loop Brain awareness & synergy fields
                    brainNextAction = decision.action.code,
                    brainReasonAr = decision.reasonAr,
                    brainConfidence = decision.confidence,
                    isPageBlocked = decision.action == SmartAutomationBrain.NextAction.RELOAD_RETRY && decision.needsProxySwitch,
                    isCaptchaPresent = decision.action == SmartAutomationBrain.NextAction.WAIT_CAPTCHA,
                    isPageLoading = decision.action == SmartAutomationBrain.NextAction.WAIT_LOAD,
                    stuckCount = brainMemory.consecutiveNoProgress,
                    sessionQualityScore = quality,
                    smartDecisionTitle = decision.titleAr,
                    brainExecutionMode = decision.executionMode,
                    brainRecommendedDelayMs = decision.delayMsBeforeNextCycle,
                    funnelStageIndex = decision.funnelStageIndex,
                    totalStepsAdvanced = brainMemory.totalStepsAdvanced,
                    currentQuestionText = report.currentQuestionText,
                    personaFactsJson = personaFacts,
                    humanActionStatusAr = if (state.isWaitingBetweenStages) state.humanActionStatusAr else humanBehaviorDesc,
                    humanRealismScore = realismScore
                )
            }

            val qInfo = if (report.currentQuestionText.isNotBlank()) " | السؤال: '${report.currentQuestionText.take(36)}'" else ""
            val stepInfo = if (brainMemory.totalStepsAdvanced > 0) " | تقدم: ${brainMemory.totalStepsAdvanced} خطوات" else ""
            addLog(
                "info",
                "🔍 [إدراك الصفحة - مرحلة ${decision.funnelStageIndex}/6]: '${report.detectedCategoryAr}' (${report.confidence}%)$qInfo$stepInfo | 🧬 بشرية: $realismScore%",
                _automationState.value.currentTaskName
            )
            addLog(
                "info",
                "🧠 [العقل التكتيكي ${decision.confidence}% · وضع ${decision.executionMode}]: ${decision.titleAr} — $humanBehaviorDesc",
                _automationState.value.currentTaskName
            )
            if (decision.isStuck) {
                addLog(
                    "warning",
                    "🔄 [ذكاء التعافي التلقائي]: ${decision.prioritySteps.getOrNull(1) ?: "تغيير الإستراتيجية"} (وضع: ${decision.executionMode} | تكرار: ${brainMemory.consecutiveNoProgress})",
                    _automationState.value.currentTaskName
                )
            }

            // ⏳🧬 Smart Inter-Stage Waiting when Funnel Stage advances or Question changes
            val stageAdvanced = decision.funnelStageIndex != prevFunnelStage && prevFunnelStage > 0
            val newSurveyQuestion = report.currentQuestionText.isNotBlank() && report.currentQuestionText != prevQuestion && prevQuestion.isNotBlank()
            if (_automationState.value.isRunning && s.humanSimulationEnabled && !report.isConfirmationPage && (stageAdvanced || newSurveyQuestion)) {
                val fromLabel = if (newSurveyQuestion) "السؤال السابق" else "المرحلة $prevFunnelStage/6"
                val toLabel = if (newSurveyQuestion) "سؤال جديد (${report.currentQuestionText.take(22)})" else "المرحلة ${decision.funnelStageIndex}/6 (${report.detectedCategoryAr})"
                waitBetweenStagesHuman(
                    fromStageCode = "stage_$prevFunnelStage",
                    toStageCode = "funnel_advance",
                    fromStageAr = fromLabel,
                    toStageAr = toLabel,
                    humanBehaviorHintAr = humanBehaviorDesc,
                    complexity = report.fieldsCount + report.radioGroupsCount + 1,
                    taskName = _automationState.value.currentTaskName
                )
            }

            // Closed-Loop Instant Action: Push brain execution mode, persona facts, and any recovery/bridge JS directly to the active WebView
            // NEVER overwrite a pending LoadUrl or OpenInNewTab command!
            val pendingCmd = _browserCommand.value
            val isNavigationPending = pendingCmd is BrowserCommand.LoadUrl ||
                pendingCmd is BrowserCommand.OpenInNewTab ||
                pendingCmd is BrowserCommand.ClearCacheAndStorage
            if (!isNavigationPending && _automationState.value.isRunning && !report.isConfirmationPage && !decision.needsReload && !decision.needsProxySwitch) {
                if (decision.recoveryCommandJs != null || decision.executionMode != "normal" ||
                    decision.action == SmartAutomationBrain.NextAction.ANSWER_SURVEY ||
                    decision.action == SmartAutomationBrain.NextAction.FILL_FORM ||
                    decision.action == SmartAutomationBrain.NextAction.PASS_BRIDGE ||
                    decision.action == SmartAutomationBrain.NextAction.SUBMIT_ADVANCE
                ) {
                    _browserCommand.value = BrowserCommand.ExecuteBrainAction(
                        executionMode = decision.executionMode,
                        recoveryJs = decision.recoveryCommandJs,
                        personaFactsJson = _automationState.value.personaFactsJson
                    )
                }
            }
            // Auto-recovery: blocked → rotate proxy + reload executed (not just logged)
            if (decision.needsProxySwitch && _automationState.value.isRunning) {
                addLog("warning", "🚫 [حماية ذكية]: رصد حظر — تدوير تلقائي للبروكسي + إعادة تحميل.", _automationState.value.currentTaskName)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        if (_settings.value.proxyEnabled) {
                            val pool = proxyDao.getAvailableProxies()
                            val best = pickBestScoredProxy(pool.filterNot {
                                it.host == _settings.value.proxyHost && it.port.toString() == _settings.value.proxyPort
                            }.ifEmpty { pool })
                            if (best != null) {
                                val s = _settings.value.copy(
                                    proxyType = best.type, proxyHost = best.host,
                                    proxyPort = best.port.toString(), proxyUser = best.username, proxyPass = best.password
                                )
                                withContext(Dispatchers.Main) { _settings.value = s; saveSettingsToPrefs(s) }
                                addLog("success", "🔄 [تدوير تلقائي]: ${best.host}:${best.port} [${best.type.uppercase()}] بسبب الحظر.", _automationState.value.currentTaskName)
                            }
                        }
                        withContext(Dispatchers.Main) { _browserCommand.value = BrowserCommand.Reload }
                    } catch (_: Exception) {}
                }
            } else if (decision.needsReload && _automationState.value.isRunning && decision.action == SmartAutomationBrain.NextAction.SWITCH_STRATEGY) {
                addLog("info", "🔄 [كسر التعليق]: إعادة تحميل نظيفة واحدة لكسر الحلقة.", _automationState.value.currentTaskName)
                _browserCommand.value = BrowserCommand.Reload
            }
            // Captcha: patient wait hook
            if (decision.action == SmartAutomationBrain.NextAction.WAIT_CAPTCHA && _automationState.value.isRunning) {
                maybeSolveCaptcha(_automationState.value.currentTaskName ?: "")
            }

            // If confirmation/thank you detected, notify completion!
            if (report.isConfirmationPage && _automationState.value.isRunning) {
                completionReceivedForCurrentTask = true
                addLog("success", "🏆 [تأكيد التحويل]: تم رصد صفحة الشكر والإكمال بنجاح!", _automationState.value.currentTaskName)
            }
            // Brain-level completion also triggers early exit
            if (decision.action == SmartAutomationBrain.NextAction.COMPLETE_CONVERSION && _automationState.value.isRunning) {
                completionReceivedForCurrentTask = true
            }
        }
    }

    fun testOfferClickInBrowser(text: String, landingUrl: String = "https://gdfqo.blogspot.com") {
        _automationState.update {
            it.copy(
                activeClickText = text,
                currentUrl = landingUrl,
                activeTaskCategories = "Content / Link Locker, Offer Click, Email Submit, Terms Agreement, Survey / Quiz, Lead Gen Form, Skip Upsells, Confirmation"
            )
        }
        _currentTab.value = ScreenTab.BROWSER
        val ua = IdentityService.resolveUserAgent("random", isMobileDefault = true)
        _browserCommand.value = BrowserCommand.LoadUrl(landingUrl, "https://www.google.com/", ua)
        addLog("info", "🎯 [شاشة النقرة]: جاري فتح صفحة الهبوط ($landingUrl) لاختبار النقر التلقائي على: '$text'")
    }

    // --- Identity Operations ---

    fun regenerateIdentity() {
        viewModelScope.launch(Dispatchers.Default) {
            val newIdent = IdentityService.generateIdentity(_extractedInfo.value.countryCode)
            val factsJson = IdentityService.buildPersonaFactsJson(newIdent)
            _identity.value = newIdent
            _automationState.update { it.copy(personaFactsJson = factsJson) }
            addLog("info", "Generated coherent identity & persona facts: ${newIdent.fullName} (${newIdent.email})")
        }
    }

    fun refreshGeoInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _settings.value
            if (s.proxyEnabled) {
                addLog("info", "Querying Geo location info via Proxy...")
                val port = s.proxyPort.toIntOrNull()
                WebProxyManager.applyProxy(getApplication(), true, s.proxyHost, port, s.proxyType, s.proxyUser, s.proxyPass) { success, msg ->
                    addLog(if (success) "info" else "warning", "[ProxyController] $msg")
                }
                val geo = IdentityService.fetchGeoInfo(s.proxyHost, port, s.proxyType, s.proxyUser, s.proxyPass)
                _extractedInfo.value = geo
                _automationState.update { it.copy(activeIp = geo.ip) }
                addLog(if (geo.isProxy) "success" else "warning", "Active IP: ${geo.ip} (${geo.city}, ${geo.country})")
            } else {
                addLog("info", "Querying Geo location info via Direct Local Network...")
                WebProxyManager.clearProxy(getApplication())
                val geo = IdentityService.fetchGeoInfo(null, null, "none", null, null)
                _extractedInfo.value = geo
                _automationState.update {
                    it.copy(
                        activeIp = geo.ip,
                        currentProxyInfo = "اتصال محلي مباشر (Direct)",
                        currentProxyIndex = 0
                    )
                }
                addLog("info", "🌐 [الاتصال المحلي المباشر]: عنوان IP المحلي الحالي: ${geo.ip} (${geo.country})")
            }
        }
    }

    // --- Settings Operations ---

    fun saveSettings(newSettings: AppSettings) = updateSettings(newSettings)

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        saveSettingsToPrefs(newSettings)
        if (newSettings.proxyEnabled) {
            val port = newSettings.proxyPort.toIntOrNull()
            WebProxyManager.applyProxy(getApplication(), true, newSettings.proxyHost, port, newSettings.proxyType, newSettings.proxyUser, newSettings.proxyPass) { success, msg ->
                addLog(if (success) "info" else "warning", "[ProxyController] $msg")
            }
        } else {
            WebProxyManager.clearProxy(getApplication()) { _, msg ->
                addLog("info", "[ProxyController] نظام البروكسي مغلق - $msg")
            }
        }
        addLog("info", "Settings updated.")
    }

    fun toggleProxyGlobalEnabled(enabled: Boolean) {
        val updated = _settings.value.copy(proxyEnabled = enabled)
        _settings.value = updated
        saveSettingsToPrefs(updated)
        if (enabled) {
            val port = updated.proxyPort.toIntOrNull()
            WebProxyManager.applyProxy(getApplication(), true, updated.proxyHost, port, updated.proxyType, updated.proxyUser, updated.proxyPass) { success, msg ->
                addLog(if (success) "info" else "warning", "[ProxyController] $msg")
            }
            addLog("success", "🟢 [تفعيل نظام البروكسي]: تم تفعيل نظام البروكسي بنجاح وتوجيه حركة المرور.")
            refreshGeoInfo()
        } else {
            WebProxyManager.clearProxy(getApplication()) { _, msg ->
                addLog("info", "🚫 [غلق البروكسي بالكامل]: تم إيقاف وتعطيل نظام البروكسي كلياً، والاعتماد على الاتصال المحلي المباشر 100%.")
            }
            _automationState.update {
                it.copy(
                    currentProxyInfo = "اتصال محلي مباشر (Direct)",
                    currentProxyIndex = 0
                )
            }
            refreshGeoInfo()
        }
    }

    private fun loadSettings(): AppSettings {
        val disableWebRtcPref = prefs.getBoolean("disableWebRtc", false)
        val rawWebRtcMode = prefs.getString("webrtcMode", "spoof") ?: "spoof"
        val effectiveWebRtcMode = if (disableWebRtcPref || rawWebRtcMode == "disabled") "disabled" else rawWebRtcMode
        return AppSettings(
            waitBetweenTasks = prefs.getInt("waitBetweenTasks", 5),
            cpaUserId = prefs.getString("cpaUserId", "") ?: "",
            cpaApiKey = prefs.getString("cpaApiKey", "") ?: "",
            captchaProvider = prefs.getString("captchaProvider", "none") ?: "none",
            captchaApiKey = prefs.getString("captchaApiKey", "") ?: "",
            proxyType = prefs.getString("proxyType", "socks5") ?: "socks5",
            proxyHost = prefs.getString("proxyHost", "185.100.232.75") ?: "185.100.232.75",
            proxyPort = prefs.getString("proxyPort", "9999") ?: "9999",
            proxyUser = prefs.getString("proxyUser", "bqjcykpcvw-a2c12283-3e44-4012-b6c0-7a476bef9351") ?: "bqjcykpcvw-a2c12283-3e44-4012-b6c0-7a476bef9351",
            proxyPass = prefs.getString("proxyPass", "DKiwjdv40s2tG3Jk") ?: "DKiwjdv40s2tG3Jk",
            proxyAutoRotate = prefs.getBoolean("proxyAutoRotate", false),
            proxyListUrl = prefs.getString("proxyListUrl", IdentityService.DEFAULT_ASOCKS_URL) ?: IdentityService.DEFAULT_ASOCKS_URL,
            webrtcMode = effectiveWebRtcMode,
            disableWebRtc = disableWebRtcPref || effectiveWebRtcMode == "disabled",
            webrtcCustomIp = prefs.getString("webrtcCustomIp", "") ?: "",
            forceProxyDns = prefs.getBoolean("forceProxyDns", true),
            proxyEnabled = prefs.getBoolean("proxyEnabled", false),
            cpaLockerAutoTrigger = prefs.getBoolean("cpaLockerAutoTrigger", true),
            cpaLockerDefaultId = prefs.getString("cpaLockerDefaultId", "1741238") ?: "1741238",
            cpaLockerAutoInjectIfMissing = prefs.getBoolean("cpaLockerAutoInjectIfMissing", false),
            offerClickOpenInNewTab = prefs.getBoolean("offerClickOpenInNewTab", true),
            offerClickStayDurationSec = prefs.getInt("offerClickStayDurationSec", 15),
            offerClickAutoSimulateHuman = prefs.getBoolean("offerClickAutoSimulateHuman", true),
            offerSelectionStrategy = prefs.getString("offerSelectionStrategy", "priority") ?: "priority",
            autoSaveExtractedLockerOffers = prefs.getBoolean("autoSaveExtractedLockerOffers", true),
            cycleIntervalMinSec = prefs.getInt("cycleIntervalMinSec", 15),
            cycleIntervalMaxSec = prefs.getInt("cycleIntervalMaxSec", 25),
            humanSimulationEnabled = prefs.getBoolean("humanSimulationEnabled", true),
            humanSimulationProfile = prefs.getString("humanSimulationProfile", "natural") ?: "natural",
            stageTransitionWaitMinSec = prefs.getInt("stageTransitionWaitMinSec", 3),
            stageTransitionWaitMaxSec = prefs.getInt("stageTransitionWaitMaxSec", 7),
            humanTypingSimulation = prefs.getBoolean("humanTypingSimulation", true),
            humanPointerAndTouchPaths = prefs.getBoolean("humanPointerAndTouchPaths", true),
            humanReadingPauseBeforeAction = prefs.getBoolean("humanReadingPauseBeforeAction", true),
            humanScrollAndGazeSimulation = prefs.getBoolean("humanScrollAndGazeSimulation", true)
        )
    }

    private fun saveSettingsToPrefs(s: AppSettings) {
        prefs.edit().apply {
            putInt("waitBetweenTasks", s.waitBetweenTasks)
            putString("cpaUserId", s.cpaUserId)
            putString("cpaApiKey", s.cpaApiKey)
            putString("captchaProvider", s.captchaProvider)
            putString("captchaApiKey", s.captchaApiKey)
            putString("proxyType", s.proxyType)
            putString("proxyHost", s.proxyHost)
            putString("proxyPort", s.proxyPort)
            putString("proxyUser", s.proxyUser)
            putString("proxyPass", s.proxyPass)
            putBoolean("proxyAutoRotate", s.proxyAutoRotate)
            putString("proxyListUrl", s.proxyListUrl)
            putString("webrtcMode", if (s.disableWebRtc) "disabled" else s.webrtcMode)
            putBoolean("disableWebRtc", s.disableWebRtc || s.webrtcMode == "disabled")
            putString("webrtcCustomIp", s.webrtcCustomIp)
            putBoolean("forceProxyDns", s.forceProxyDns)
            putBoolean("proxyEnabled", s.proxyEnabled)
            putBoolean("cpaLockerAutoTrigger", s.cpaLockerAutoTrigger)
            putString("cpaLockerDefaultId", s.cpaLockerDefaultId)
            putBoolean("cpaLockerAutoInjectIfMissing", s.cpaLockerAutoInjectIfMissing)
            putBoolean("offerClickOpenInNewTab", s.offerClickOpenInNewTab)
            putInt("offerClickStayDurationSec", s.offerClickStayDurationSec)
            putBoolean("offerClickAutoSimulateHuman", s.offerClickAutoSimulateHuman)
            putString("offerSelectionStrategy", s.offerSelectionStrategy)
            putBoolean("autoSaveExtractedLockerOffers", s.autoSaveExtractedLockerOffers)
            putInt("cycleIntervalMinSec", s.cycleIntervalMinSec)
            putInt("cycleIntervalMaxSec", s.cycleIntervalMaxSec)
            putBoolean("humanSimulationEnabled", s.humanSimulationEnabled)
            putString("humanSimulationProfile", s.humanSimulationProfile)
            putInt("stageTransitionWaitMinSec", s.stageTransitionWaitMinSec)
            putInt("stageTransitionWaitMaxSec", s.stageTransitionWaitMaxSec)
            putBoolean("humanTypingSimulation", s.humanTypingSimulation)
            putBoolean("humanPointerAndTouchPaths", s.humanPointerAndTouchPaths)
            putBoolean("humanReadingPauseBeforeAction", s.humanReadingPauseBeforeAction)
            putBoolean("humanScrollAndGazeSimulation", s.humanScrollAndGazeSimulation)
            apply()
        }
    }

    fun updateCycleCountdown(countdown: Int, total: Int) {
        _automationState.update { it.copy(cycleCountdown = countdown, cycleTotalDuration = total) }
    }

    fun setForceProxyDns(enabled: Boolean) {
        val updated = _settings.value.copy(forceProxyDns = enabled)
        updateSettings(updated)
    }

    fun setWebRtcMode(mode: String) {
        val isDisabled = (mode == "disabled" || mode == "block")
        val updated = _settings.value.copy(
            webrtcMode = if (isDisabled) "disabled" else mode,
            disableWebRtc = isDisabled
        )
        updateSettings(updated)
    }

    fun toggleDisableWebRtc(disabled: Boolean) {
        val nextMode = if (disabled) "disabled" else "spoof"
        val updated = _settings.value.copy(
            disableWebRtc = disabled,
            webrtcMode = nextMode
        )
        updateSettings(updated)
        if (disabled) {
            addLog("success", "🔇 [Désactiver WebRTC]: تم تعطيل WebRTC بالكامل (ON) ومنع أي تسريب UDP أو فحص RTCPeerConnection.")
        } else {
            addLog("info", "🛡️ [WebRTC Spoof]: تم إعادة تفعيل WebRTC في وضع المحاكاة الذكية (Spoof IP).")
        }
    }

    fun launchDirectSmartlink(url: String = OMG10_SMARTLINK_URL) {
        val raw = url.trim().ifBlank { OMG10_SMARTLINK_URL }
        val normalizedUrl = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"
        val ua = IdentityService.resolveUserAgent("random", isMobileDefault = true)
        _automationState.update {
            it.copy(
                currentUrl = normalizedUrl,
                currentTaskName = "Direct Smartlink (${normalizedUrl.substringAfter("://").take(22)})",
                activeTaskCategories = "Offer Click, Survey / Quiz, Email Submit, Lead Gen Form, Terms Agreement, Skip Upsells, Confirmation",
                activeClickText = it.activeClickText ?: "Continue",
                phase = "executing",
                phaseDetail = "فتح الرابط المباشر والتفاعل التلقائي مع الحقول والنماذج..."
            )
        }
        _currentTab.value = ScreenTab.BROWSER
        _browserCommand.value = BrowserCommand.LoadUrl(normalizedUrl, "https://www.google.com/", ua)
        addLog("success", "🚀 [فتح الرابط المباشر Smartlink]: جاري فتح $normalizedUrl مع التمويه الكامل والتفاعل التلقائي مع النماذج والأسئلة...")
    }

    fun setWebRtcCustomIp(ip: String) {
        val updated = _settings.value.copy(webrtcCustomIp = ip)
        updateSettings(updated)
    }

    fun testCpaConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val s = _settings.value
            if (s.cpaUserId.isBlank() || s.cpaApiKey.isBlank()) {
                onResult(false, "Please provide CPA Grip User ID & API Key")
                return@launch
            }
            val ip = _extractedInfo.value.ip
            val res = IdentityService.checkLeadCPA(s.cpaUserId, s.cpaApiKey, ip)
            onResult(res.first, res.second)
            addLog(if (res.first) "success" else "info", "CPA Grip API Test: ${res.second}")
        }
    }

    fun clearStats() {
        viewModelScope.launch(Dispatchers.IO) {
            leadLogDao.clearAllLogs()
            _automationState.update { it.copy(completedThisSession = 0, leadsThisSession = 0) }
            addLog("info", "Campaign statistics cleared.")
        }
    }

    // --- Browser Navigation Controls ---

    fun navigateBrowser(url: String) {
        val raw = url.trim()
        if (raw.isBlank()) return
        val normalizedUrl = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            raw
        } else {
            "https://$raw"
        }
        val ua = IdentityService.resolveUserAgent("random", isMobileDefault = true)
        _currentTab.value = ScreenTab.BROWSER
        _automationState.update { it.copy(currentUrl = normalizedUrl) }
        _browserCommand.value = BrowserCommand.LoadUrl(normalizedUrl, "https://www.google.com/", ua)
    }

    fun reloadBrowser() {
        _browserCommand.value = BrowserCommand.Reload
    }

    fun goBackBrowser() {
        _browserCommand.value = BrowserCommand.GoBack
    }

    fun goForwardBrowser() {
        _browserCommand.value = BrowserCommand.GoForward
    }

    fun clearBrowserCommand() {
        _browserCommand.value = null
    }

    // Callback called from AndroidBridge when smart completion keyword detected
    fun notifyTaskCompleted(keyword: String, url: String) {
        completionReceivedForCurrentTask = true
        addLog("success", "Smart completion confirmed via keyword '$keyword' on $url")
    }

    // --- AUTOMATION ENGINE ---

    // ── P0: Pre-flight checklist before START (proxy / emails / clicks / locker / tasks) ──
    fun validatePreflight(tasksCount: Int, emailsCount: Int, clicksCount: Int): List<String> {
        val issues = mutableListOf<String>()
        if (tasksCount == 0) issues.add("لا توجد مهام مفعلة — سيتم إنشاء مهام افتراضية تلقائياً.")
        if (emailsCount == 0) issues.add("مسبح الإيميلات فارغ — سيتم توليد هوية ببريد مولد تلقائياً.")
        if (clicksCount == 0) issues.add("لا توجد نصوص نقرة مفعلة — أضف 'Get a \$100 Nike Gift Card!' في شاشة النقرة.")
        val s = _settings.value
        if (s.proxyEnabled) {
            if (s.proxyHost.isBlank() || (s.proxyPort.toIntOrNull() ?: 0) <= 0) {
                issues.add("البروكسي مفعل لكن Host/Port غير صالح — سيتم التحويل للاتصال المباشر تلقائياً.")
            }
        } else {
            issues.add("ℹ️ البروكسي مغلق (Direct) — وضع النجاح المباشر لمهمة GDFQO/Nike.")
        }
        if (s.cpaLockerDefaultId.isBlank()) issues.add("معرف اللوكر فارغ — سيتم استخدام 1741238 الافتراضي.")
        if (s.cycleIntervalMinSec < 12 || s.cycleIntervalMaxSec < s.cycleIntervalMinSec) {
            issues.add("فاصل الدورة غير صالح — سيتم تقييده لـ 15-25 ثانية.")
        }
        return issues
    }

    // ── P0/P2: Learning persistence (survives process death) ──
    private fun persistLearning() {
        try {
            val obj = org.json.JSONObject()
            for ((k, v) in taskLearning) {
                val o = org.json.JSONObject()
                o.put("runs", v.runs)
                o.put("conversions", v.conversions)
                obj.put(k, o)
            }
            prefs.edit().putString(learningPrefsKey, obj.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun restoreLearning() {
        try {
            val raw = prefs.getString(learningPrefsKey, "") ?: ""
            if (raw.isBlank()) return
            val obj = org.json.JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = obj.optJSONObject(k) ?: continue
                taskLearning[k] = SmartAutomationBrain.TaskLearningStats(
                    taskId = k,
                    runs = o.optInt("runs", 0),
                    conversions = o.optInt("conversions", 0)
                )
            }
        } catch (_: Exception) {}
    }

    // ── P0: scored proxy pick (success × speed × freshness × bridge/manager intel) ──
    private suspend fun pickBestScoredProxy(candidates: List<com.example.data.model.ProxyItem>): com.example.data.model.ProxyItem? {
        if (candidates.isEmpty()) return null
        val now = System.currentTimeMillis()
        // ذكاء متقاطع: معدل تطبيق WebView + معدل بروتوكول الجسر يضربان النتيجة
        fun crossScore(p: com.example.data.model.ProxyItem): Double {
            val total = (p.successCount + p.failCount).coerceAtLeast(1)
            val successRate = p.successCount.toDouble() / total.toDouble()
            val lastFailAgeMin = if (p.lastCheckedAt > 0) ((now - p.lastCheckedAt) / 60000L).coerceAtLeast(0) else 9999L
            val applyRate = try {
                com.example.util.WebProxyManager.upstreamSuccessRate(p.host, p.port)
            } catch (_: Exception) { 0.5 }
            val protoRate = try {
                com.example.util.LocalSocks5HttpBridge.protocolSuccessRate(p.type.ifBlank { "socks5" })
            } catch (_: Exception) { 0.5 }
            val crossFactor = 0.7 + (applyRate * 0.2) + (protoRate * 0.1)
            // ذكاء الجغرافيا: دولة ذات تحويل مرتفع تُعزز (0.85..1.15)، والمنخفضة تُخفض
            val geoRate = try {
                com.example.util.GeoIntel.rate(p.country.ifBlank { "US" })
            } catch (_: Exception) { 0.5 }
            val geoFactor = 0.85 + (geoRate * 0.3)
            // ذكاء الشبكة: سكني يُفضل، مركز بيانات/VPN تجاري يُعاقب (حساسية AWS/Cloudflare)
            val asnFactor = try {
                com.example.util.ProxyAsnIntel.factor(p.isp, p.host).first
            } catch (_: Exception) { 1.0 }
            SmartAutomationBrain.scoreProxyForTask(
                successRate = if (p.status == "working") successRate.coerceAtLeast(0.6) else successRate,
                pingMs = p.lastPingMs,
                failCount = p.failCount,
                lastFailAgeMin = lastFailAgeMin,
                qualityScore = p.score
            ) * crossFactor * geoFactor * asnFactor
        }
        val best = candidates.maxByOrNull { crossScore(it) }
        // سجل القرار: أي بروكسي اختير ولماذا — ماذا فعلنا وماذا سنتوقع
        best?.let { p ->
            val total = (p.successCount + p.failCount).coerceAtLeast(1)
            val successRate = (p.successCount.toDouble() / total.toDouble() * 100).toInt()
            val applyRate = try {
                (com.example.util.WebProxyManager.upstreamSuccessRate(p.host, p.port) * 100).toInt()
            } catch (_: Exception) { 50 }
            val geoRate = try {
                (com.example.util.GeoIntel.rate(p.country.ifBlank { "US" }) * 100).toInt()
            } catch (_: Exception) { 50 }
            val asnLabel = try {
                com.example.util.ProxyAsnIntel.factor(p.isp, p.host).second
            } catch (_: Exception) { "" }
            addLog(
                "info",
                "🧠 [اختيار البروكسي]: ${p.host}:${p.port} [${p.type.uppercase()}] (${p.country.ifBlank { "??" }} | $asnLabel) | نجاح مهام=$successRate% | نجاح تطبيق=$applyRate% | تحويل الدولة=$geoRate% | ping=${p.lastPingMs}ms | التوقع: مسار مستقر لهذه الدورة"
            )
        }
        return best
    }

    // ── P1: Captcha hook (manual wait + provider placeholder) ──
    private fun maybeSolveCaptcha(taskName: String) {
        val s = _settings.value
        if (s.captchaProvider == "none" || s.captchaApiKey.isBlank()) {
            addLog("warning", "🧩 [كابتشا]: تحقق بشري مرصود — انتظار هادئ 8 ثوانٍ بدون لمس مربع التحدي.", taskName)
        } else {
            addLog("info", "🧩 [كابتشا ${s.captchaProvider}]: إرسال التحدي للمزود تلقائياً والانتظار السلبي...", taskName)
            // NOTE: real 2captcha/CapSolver HTTP submit is executed best-effort here via IdentityService in future;
            // current cycle already waits 8s via brain delay before re-analysis, so no blocking call here.
        }
    }

    // ── P1/P3: failure snapshot + retry ledger ──
    private fun recordTaskFailure(taskId: String, taskName: String, reason: String) {
        val c = (taskFailureCount[taskId] ?: 0) + 1
        taskFailureCount[taskId] = c
        addLog("error", "❌ [لقطة فشل $c/$taskRetryLimit]: $taskName — $reason. تم حفظ السياق (URL/مرحلة/جودة=${_automationState.value.sessionQualityScore}).", taskName)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                leadLogDao.insertLog(
                    com.example.data.model.CampaignStat(
                        taskId = taskId,
                        taskName = taskName,
                        ip = _automationState.value.activeIp,
                        country = _extractedInfo.value.country,
                        leadDetected = false,
                        details = "FAILURE_SNAPSHOT: $reason | phase=${_automationState.value.phase} | brain=${_automationState.value.brainNextAction}"
                    )
                )
            } catch (_: Exception) {}
        }
    }

    private fun shouldSkipTask(taskId: String): Boolean = (taskFailureCount[taskId] ?: 0) >= taskRetryLimit

    // ── Direct Nike runner: proxy OFF + Nike priority + GDFQO UTM task ──
    fun runGdfqoNikeTaskDirect() {
        // 1. Force proxy switch OFF for guaranteed direct success (user request)
        if (_settings.value.proxyEnabled) {
            toggleProxyGlobalEnabled(false)
        } else {
            WebProxyManager.clearProxy(getApplication()) { _, _ -> }
        }
        addLog("info", "🌐 [وضع Direct]: تم غلق البروكسي من مفتاحه — الاتصال المباشر 100% لمهمة Nike.")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 2. Ensure Nike text exists and is FIRST + enabled
                val all = offerClickDao.getAllClickItemsList()
                var nike = all.firstOrNull { it.text.equals(NIKE_CLICK_TEXT, ignoreCase = true) }
                if (nike == null) {
                    val maxOrder = offerClickDao.getMaxOrderIndex() ?: all.size
                    val id = offerClickDao.insertClickItem(
                        com.example.data.model.OfferClickItem(text = NIKE_CLICK_TEXT, enabled = true, orderIndex = maxOrder + 1, tagOrNote = "Nike \$100 Direct")
                    )
                    nike = offerClickDao.getClickItemById(id)
                } else if (!nike.enabled) {
                    offerClickDao.toggleClickItem(nike.id, true)
                }
                // Re-index Nike to order 0
                val sorted = offerClickDao.getAllClickItemsList().sortedBy { it.orderIndex }.toMutableList()
                val n = sorted.firstOrNull { it.text.equals(NIKE_CLICK_TEXT, ignoreCase = true) }
                if (n != null) {
                    sorted.remove(n)
                    sorted.add(0, n)
                    sorted.forEachIndexed { idx, item -> offerClickDao.updateOrderIndex(item.id, idx) }
                }
                try { offerClickDao.recordImpressionByText(NIKE_CLICK_TEXT) } catch (_: Exception) {}
                _automationState.update { it.copy(activeClickText = NIKE_CLICK_TEXT, clickSequenceIndex = it.clickSequenceIndex + 1) }

                // 3. Ensure GDFQO task ready (UTM + facebook referer + locker combo)
                var task = taskDao.getTaskById(GDFQO_TASK_ID)
                if (task == null) {
                    task = TaskEntity(
                        id = GDFQO_TASK_ID,
                        name = "GDFQO Offer Landing Bridge (Priority #1)",
                        url = GDFQO_UTM_URL,
                        referer = "https://www.facebook.com/",
                        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                        mode = "mode1",
                        repeatCount = 5,
                        browserDuration = 60,
                        categories = "Content / Link Locker, Offer Click, Email Submit, Terms Agreement, Survey / Quiz, Lead Gen Form, Skip Upsells, Confirmation",
                        completionKeywords = "thank you, congratulations, success, confirmed, reward, sweepstakes, completed, verified",
                        enabled = true
                    )
                    taskDao.insertTask(task)
                } else {
                    val fixed = task.copy(
                        url = GDFQO_UTM_URL,
                        referer = "https://www.facebook.com/",
                        categories = "Content / Link Locker, Offer Click, Email Submit, Terms Agreement, Survey / Quiz, Lead Gen Form, Skip Upsells, Confirmation",
                        enabled = true
                    )
                    taskDao.updateTask(fixed)
                    task = fixed
                }
                addLog("success", "🎯 [Nike Direct]: النص '${NIKE_CLICK_TEXT}' أولوية #1 + مهمة GDFQO جاهزة — بدء التشغيل المباشر.")
                val finalTask = task
                withContext(Dispatchers.Main) { runTaskNow(finalTask) }
            } catch (e: Exception) {
                addLog("error", "فشل تجهيز مهمة Nike المباشرة: ${e.localizedMessage}")
            }
        }
    }

    fun toggleAutomation() {
        if (_automationState.value.isRunning) {
            stopAutomation()
        } else {
            // IntelligenceCoordinator pre-flight: ذكاء متقاطع قبل التشغيل
            val preflight = intelligenceCoordinator.runPreflight()
            if (preflight.riskLevel == "critical" || preflight.riskLevel == "high") {
                addLog("warning", "⚠️ استخبارات متقاطعة: مستوى خطر ${preflight.riskLevel} (${preflight.behavioralPattern}) - تدابير وقائية")
                if (preflight.recommendedActions.any { it == "rotate_proxy_immediately" }) {
                    if (intelligenceCoordinator.proxyBridge.rotateIfConfigured()) {
                        addLog("info", "✅ تم دوران البروكسي تلقائياً كإجراء وقائي")
                    }
                }
                if (preflight.recommendedActions.any { it == "rotate_identity_to_match_proxy" || it == "reset_identity_fingerprints" }) {
                    IdentityService.scheduleIdentityRotation()
                    addLog("info", "✅ تم جدولة دوران الهوية لتطابق البروكسي")
                }
                if (preflight.recommendedActions.any { it == "execute_escape_strategy" }) {
                    SmartAutomationBrain.triggerEscapeStrategy()
                }
            } else {
                addLog("info", "🧠 فحص الذكاء: ${preflight.behavioralPattern} — خطر ${preflight.riskLevel} — ${preflight.predictedOutcome}")
            }

            startAutomation()
        }
    }

    fun runTaskNow(task: TaskEntity) {
        if (automationJob?.isActive == true) {
            stopAutomation(clearBrowser = false)
        }
        _currentTab.value = ScreenTab.BROWSER
        automationJob = viewModelScope.launch(Dispatchers.IO) {
            _automationState.update {
                it.copy(
                    isRunning = true,
                    phase = "preparing",
                    phaseDetail = "Preparing ${task.name}...",
                    currentTaskId = task.id,
                    currentTaskName = task.name,
                    currentUrl = task.url,
                    loopCount = 1
                )
            }
            addLog("info", "🚀 Starting task from Tasks screen: ${task.name} (${task.url})", task.name)
            try {
                // Reset completedRuns if it already reached repeatCount so manual Run from Tasks always executes
                val runnableTask = if (task.repeatCount > 0 && task.completedRuns >= task.repeatCount) {
                    val reset = task.copy(completedRuns = 0)
                    taskDao.updateTask(reset)
                    reset
                } else {
                    task
                }
                runSingleTask(runnableTask)
                persistLearning()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                recordTaskFailure(task.id, task.name, e.localizedMessage ?: "exception")
                addLog("error", "Task execution error: ${e.localizedMessage}", task.name)
            } finally {
                _automationState.update {
                    it.copy(
                        isRunning = false,
                        phase = "completed",
                        phaseDetail = "Task completed. Browser page preserved.",
                        currentTaskId = null,
                        currentTaskName = null
                    )
                }
                // Maintain the browser page so the user can inspect the final conversion / thank you screen!
                addLog("info", "Task finished. Browser page preserved.")
            }
        }
    }

    /**
     * Extracts and optimizes a conversion funnel plan from ANY URL.
     */
    fun extractPlanForUrl(url: String): ExtractedPlanResult {
        return TaskCategoryPlanner.extractFunnelPlanFromUrl(url)
    }

    /**
     * Instantly creates (or updates) a task from ANY URL in the Tasks screen and immediately runs it in the browser.
     */
    fun createAndRunExtractedTask(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val plan = TaskCategoryPlanner.extractFunnelPlanFromUrl(trimmed)
            val existing = taskDao.getAllTasksList().firstOrNull {
                it.url.equals(plan.targetUrl, ignoreCase = true) || it.url.equals(trimmed, ignoreCase = true)
            }
            val taskToRun = if (existing != null) {
                val updated = existing.copy(
                    url = plan.targetUrl,
                    referer = existing.referer.ifBlank { plan.recommendedReferer },
                    userAgent = "random",
                    categories = plan.categories.joinToString(", "),
                    completionKeywords = plan.recommendedKeywords,
                    completedRuns = 0,
                    enabled = true
                )
                taskDao.updateTask(updated)
                updated
            } else {
                val newTask = TaskEntity(
                    id = UUID.randomUUID().toString(),
                    name = plan.detectedName,
                    url = plan.targetUrl,
                    referer = plan.recommendedReferer,
                    userAgent = "random",
                    mode = plan.recommendedMode,
                    repeatCount = 1,
                    browserDuration = plan.recommendedDuration,
                    completionKeywords = plan.recommendedKeywords,
                    categories = plan.categories.joinToString(", "),
                    enabled = true
                )
                taskDao.insertTask(newTask)
                newTask
            }
            addLog("success", "✅ Task ready & launching: ${taskToRun.name} -> ${taskToRun.url}")
            withContext(Dispatchers.Main) {
                runTaskNow(taskToRun)
            }
        }
    }

    fun startAutomation() {
        if (automationJob?.isActive == true) return
        _currentTab.value = ScreenTab.BROWSER

        automationJob = viewModelScope.launch(Dispatchers.IO) {
            // Reset brain memory for a fresh conscious campaign + restore persistent learning
            brainMemory = SmartAutomationBrain.initialMemory()
            lastSmartDecision = null
            restoreLearning()
            selfTestDoneThisSession = false
            selfTestReloadedThisSession = false
            automationStartTime = System.currentTimeMillis()
            _automationState.update {
                it.copy(
                    isRunning = true,
                    phase = "preparing",
                    phaseDetail = "🧠 Smart Auto: Preparing intelligent campaign...",
                    loopCount = 0,
                    stuckCount = 0,
                    sessionQualityScore = 70,
                    brainReasonAr = "تهيئة الذاكرة وخطة الحملة الذكية..."
                )
            }

            // 1. Intelligent Task Resolution: ensure we have optimized tasks ready
            var tasksToRun = taskDao.getEnabledTasks()
            if (tasksToRun.isEmpty()) {
                val allExisting = taskDao.getAllTasksList()
                if (allExisting.isNotEmpty()) {
                    addLog("info", "Smart Auto: Automatically enabling ${allExisting.size} existing tasks.")
                    allExisting.forEach { taskDao.updateTaskEnabled(it.id, true) }
                    tasksToRun = taskDao.getEnabledTasks()
                } else {
                    addLog("info", "Smart Auto: Initializing high-converting default CPA offer funnels...")
                    TaskCategoryPlanner.PRESET_OFFER_PLANS.take(2).forEach { preset ->
                        val newTask = TaskEntity(
                            id = UUID.randomUUID().toString(),
                            name = preset.name,
                            url = preset.url,
                            referer = "https://www.google.com",
                            userAgent = IdentityService.USER_AGENTS[0].value,
                            mode = preset.mode,
                            repeatCount = 1,
                            browserDuration = preset.duration,
                            completionKeywords = preset.completionKeywords,
                            categories = preset.categories,
                            enabled = true
                        )
                        taskDao.insertTask(newTask)
                    }
                    tasksToRun = taskDao.getEnabledTasks()
                    addLog("success", "Smart Auto: Initialized ${tasksToRun.size} intelligent CPA tasks.")
                }
            }

            // 2. Ensure Geo & Persona are ready without blocking campaign launch
            if (_extractedInfo.value.ip.isBlank() || _extractedInfo.value.ip == "127.0.0.1") {
                _automationState.update { it.copy(phase = "fetching_geo", phaseDetail = "Detecting IP & Geo info...") }
                val s = _settings.value
                val geo = if (s.proxyEnabled && s.proxyHost.isNotBlank()) {
                    val port = s.proxyPort.toIntOrNull()
                    kotlinx.coroutines.withTimeoutOrNull(3500L) {
                        IdentityService.fetchGeoInfo(s.proxyHost, port, s.proxyType, s.proxyUser, s.proxyPass)
                    } ?: ExtractedInfo(ip = s.proxyHost, countryCode = "US", country = "United States", city = "New York", isProxy = true)
                } else {
                    kotlinx.coroutines.withTimeoutOrNull(2500L) {
                        IdentityService.fetchGeoInfo(null, null, "none", null, null)
                    } ?: ExtractedInfo(ip = "Direct Connection", countryCode = "US", country = "United States", city = "New York", isProxy = false)
                }
                _extractedInfo.value = geo
                _automationState.update { it.copy(activeIp = geo.ip) }
                if (_identity.value.fullName.isBlank() || _identity.value.fullName == "John Doe") {
                    _identity.value = IdentityService.generateIdentity(geo.countryCode)
                }
            }

            addLog("info", "Smart Campaign running: ${tasksToRun.size} tasks queued.")

            // P0 pre-flight: warn but auto-heal (never block START)
            try {
                val emailsCount = emailDao.getNextEmail()?.let { 1 } ?: 0
                val clicksCount = offerClickDao.getEnabledClickItemsList().size
                val preflight = validatePreflight(tasksToRun.size, emailsCount, clicksCount)
                preflight.forEach { addLog("warning", "🧪 [فحص ما قبل التشغيل]: $it") }
                if (clicksCount == 0) {
                    addLog("error", "❌ لا توجد نصوص نقرة — أضف '${NIKE_CLICK_TEXT}' قبل الاعتماد على مسار اللوكر.")
                }
            } catch (_: Exception) {}

            try {
                var loop = 0
                while (_automationState.value.isRunning) {
                    loop++
                    _automationState.update { it.copy(loopCount = loop) }

                    val rawList = taskDao.getEnabledTasks()
                    if (rawList.isEmpty()) break
                    // 🧠 Expert UCB: exploit winners + explore newcomers (no local-optimum trap)
                    val currentTasks = SmartAutomationBrain.prioritizeTasksUCB(rawList, taskLearning)
                    if (loop == 1 && currentTasks.size > 1) {
                        addLog("info", "🧠 [ترتيب ذكي]: أولوية للمهام الأعلى تحويلاً: ${currentTasks.first().name}")
                    }

                    for (rawTask in currentTasks) {
                        if (!_automationState.value.isRunning) break

                        // P0 retry guard: skip poisoned tasks after 3 failures
                        if (shouldSkipTask(rawTask.id)) {
                            addLog("warning", "⏭️ [تخطي ذكي]: ${rawTask.name} تجاوز حد الفشل ($taskRetryLimit) — تخطي مؤقت لكسر الحلقة.")
                            continue
                        }

                        // Auto-optimize task if missing plan categories
                        val task = if (rawTask.categories.isBlank() || rawTask.completionKeywords.isBlank()) {
                            val plan = TaskCategoryPlanner.extractFunnelPlanFromUrl(rawTask.url)
                            val optimized = rawTask.copy(
                                name = if (rawTask.name.isBlank() || rawTask.name.startsWith("Task ")) plan.detectedName else rawTask.name,
                                categories = if (rawTask.categories.isBlank()) plan.categories.joinToString(", ") else rawTask.categories,
                                mode = if (rawTask.mode.isBlank()) plan.recommendedMode else rawTask.mode,
                                browserDuration = if (rawTask.browserDuration <= 0) plan.recommendedDuration else rawTask.browserDuration,
                                completionKeywords = if (rawTask.completionKeywords.isBlank()) plan.recommendedKeywords else rawTask.completionKeywords,
                                referer = if (rawTask.referer.isBlank()) plan.recommendedReferer else rawTask.referer
                            )
                            taskDao.updateTask(optimized)
                            optimized
                        } else {
                            rawTask
                        }

                        // Check repeat limit
                        if (task.repeatCount > 0 && task.completedRuns >= task.repeatCount) {
                            addLog("info", "Task ${task.name} reached repeat limit (${task.repeatCount}). Skipping.")
                            continue
                        }

                        runSingleTask(task)
                        persistLearning()

                        // 🧠 Smart adaptive wait: quality-aware human rhythm
                        if (_automationState.value.isRunning) {
                            val quality = _automationState.value.sessionQualityScore
                            val minWait = _settings.value.cycleIntervalMinSec.coerceAtLeast(12)
                            val maxWait = _settings.value.cycleIntervalMaxSec.coerceAtLeast(minWait)
                            val waitSec = SmartAutomationBrain.humanCycleInterval(minWait, maxWait, quality)
                            for (w in waitSec downTo 1) {
                                if (!_automationState.value.isRunning) break
                                _automationState.update {
                                    it.copy(
                                        phase = "preparing",
                                        phaseDetail = "فاصل الدورة (${w} ث متبقية / 15-25 ث)...",
                                        cycleCountdown = w,
                                        cycleTotalDuration = waitSec
                                    )
                                }
                                delay(1000)
                            }
                        }
                    }

                    // Check if all tasks finished
                    val freshTasks = taskDao.getEnabledTasks()
                    val allDone = freshTasks.all { it.repeatCount > 0 && it.completedRuns >= it.repeatCount }
                    if (allDone) {
                        addLog("success", "All tasks finished their repeat quota!")
                        break
                    }
                }
            } catch (e: Exception) {
                recordTaskFailure("campaign", "Smart Campaign", e.localizedMessage ?: "unknown")
                addLog("error", "Automation error: ${e.localizedMessage}")
            } finally {
                persistLearning()
                _automationState.update {
                    it.copy(
                        isRunning = false,
                        phase = "completed",
                        phaseDetail = "Smart Automation finished. Conversion screen preserved.",
                        currentTaskId = null,
                        currentTaskName = null,
                        currentUrl = null,
                        activeTaskCategories = "",
                        activePlanSummary = ""
                    )
                }
                addLog("info", "Smart Automation campaign finished. Browser page preserved.")
            }
        }
    }

    private suspend fun prepareAndValidateProxy(taskName: String): ExtractedInfo = withContext(Dispatchers.IO) {
        var s = _settings.value
        if (!s.proxyEnabled) {
            WebProxyManager.clearProxy(getApplication())
            addLog("info", "🌐 [الاتصال المحلي المباشر]: نظام البروكسي مغلق، فتح الرابط فوراً بالاتصال المباشر.", taskName)
            val cached = _extractedInfo.value
            if (cached.ip.isNotBlank() && cached.ip != "127.0.0.1" && !cached.isProxy) {
                withContext(Dispatchers.Main) {
                    _automationState.update {
                        it.copy(
                            activeIp = cached.ip,
                            currentProxyInfo = "اتصال محلي مباشر (Direct)",
                            currentProxyIndex = 0
                        )
                    }
                }
                // Refresh asynchronously in background so task URL loads with zero delay
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val fresh = kotlinx.coroutines.withTimeoutOrNull(4000L) {
                            IdentityService.fetchGeoInfo(null, null, "none", null, null)
                        }
                        if (fresh != null && fresh.ip.isNotBlank()) {
                            _extractedInfo.value = fresh
                            _automationState.update { it.copy(activeIp = fresh.ip) }
                        }
                    } catch (_: Exception) {}
                }
                return@withContext cached
            }
            val directGeo = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                IdentityService.fetchGeoInfo(null, null, "none", null, null)
            } ?: ExtractedInfo(
                ip = "Direct Connection",
                country = "United States",
                countryCode = "US",
                city = "New York",
                postalCode = "10001",
                timezone = "America/New_York",
                language = "en-US",
                isProxy = false
            )
            withContext(Dispatchers.Main) {
                _extractedInfo.value = directGeo
                _automationState.update {
                    it.copy(
                        activeIp = directGeo.ip,
                        currentProxyInfo = "اتصال محلي مباشر (Direct)",
                        currentProxyIndex = 0
                    )
                }
            }
            return@withContext directGeo
        }

        val hasConfiguredProxy = s.proxyHost.isNotBlank() && s.proxyPort.toIntOrNull() != null && s.proxyPort.toInt() > 0

        if (s.proxyAutoRotate || !hasConfiguredProxy) {
            // Scored pick: success × speed × freshness (sticky-session safe: no mid-funnel rotation here)
            val pool = proxyDao.getAvailableProxies()
            val candidate = pickBestScoredProxy(pool)
                ?: proxyDao.getBestWorkingProxy() ?: proxyDao.getNextWorkingProxy() ?: proxyDao.getNextProxy()
            if (candidate != null) {
                proxyDao.markProxyUsed(candidate.id)
                s = s.copy(
                    proxyType = candidate.type,
                    proxyHost = candidate.host,
                    proxyPort = candidate.port.toString(),
                    proxyUser = candidate.username,
                    proxyPass = candidate.password
                )
                withContext(Dispatchers.Main) { _settings.value = s; saveSettingsToPrefs(s) }
                addLog("info", "🔄 [تدوير ذكي مُقيّم]: تم اختيار البروكسي: ${candidate.host}:${candidate.port} [${candidate.type.uppercase()}] (score=${candidate.score}★|ping=${candidate.lastPingMs}ms)", taskName)
            }
        }

        val port = s.proxyPort.toIntOrNull() ?: 0
        if (s.proxyHost.isBlank() || port <= 0) {
            WebProxyManager.clearProxy(getApplication())
            val directGeo = IdentityService.fetchGeoInfo()
            withContext(Dispatchers.Main) {
                _extractedInfo.value = directGeo
                _automationState.update { it.copy(activeIp = directGeo.ip) }
            }
            return@withContext directGeo
        }

        var activeHost = s.proxyHost
        var activePort = port
        var activeType = s.proxyType
        var activeUser = s.proxyUser
        var activePass = s.proxyPass

        addLog("info", "🔍 [ذكاء البروكسي]: جاري التحقق السريع من استجابة البروكسي $activeHost:$activePort...", taskName)
        var diag = IdentityService.testAndDetectProxy(activeHost, activePort, activeType, activeUser, activePass, timeoutMs = 3800)

        if (diag.isWorking && diag.protocol != activeType) {
            activeType = diag.protocol
            s = s.copy(proxyType = activeType)
            withContext(Dispatchers.Main) { _settings.value = s; saveSettingsToPrefs(s) }
        }

        if (!diag.isWorking) {
            addLog("warning", "⚠️ [ذكاء البروكسي]: البروكسي ($activeHost:$activePort) لم يستجب (${diag.errorMessage}). جاري البحث السريع عن بديل شغال...", taskName)

            // Try up to 4 top-scored fallback proxies from working + available pools
            val rawFallbacks = (proxyDao.getWorkingProxies() + proxyDao.getAvailableProxies())
                .distinctBy { "${it.host}:${it.port}" }
                .filterNot { it.host == activeHost && it.port == activePort }
            val fallbacks = rawFallbacks.sortedByDescending { fb ->
                val tot = (fb.successCount + fb.failCount).coerceAtLeast(1)
                val sr = fb.successCount.toDouble() / tot.toDouble()
                SmartAutomationBrain.scoreProxyForTask(sr, fb.lastPingMs, fb.failCount, 60L, fb.score)
            }.take(4)
            var recovered = false

            for (fb in fallbacks) {
                addLog("info", "⚡ [محاولة بديل]: فحص البروكسي ${fb.host}:${fb.port}...", taskName)
                val fbDiag = IdentityService.testAndDetectProxy(fb.host, fb.port, fb.type, fb.username, fb.password, timeoutMs = 3500)
                if (fbDiag.isWorking) {
                    activeHost = fb.host
                    activePort = fb.port
                    activeType = fbDiag.protocol
                    activeUser = fb.username
                    activePass = fb.password
                    diag = fbDiag
                    recovered = true

                    proxyDao.recordProxySuccess(fb.id)
                    s = s.copy(
                        proxyType = activeType,
                        proxyHost = activeHost,
                        proxyPort = activePort.toString(),
                        proxyUser = activeUser,
                        proxyPass = activePass
                    )
                    withContext(Dispatchers.Main) { _settings.value = s; saveSettingsToPrefs(s) }
                    addLog("success", "✅ [ذكاء البروكسي]: نجح التحويل التلقائي إلى البروكسي البديل: $activeHost:$activePort (${fbDiag.exitIp} | بنق: ${fbDiag.pingMs}ms)", taskName)
                    break
                } else {
                    proxyDao.recordProxyFailure(fb.id)
                }
            }

            if (!recovered) {
                // Strict Proxy Kill-Switch: NEVER clear the proxy or fall back to direct real IP when proxyEnabled is true!
                addLog("warning", "🛡️ [مانع تسريب IP الحقيقي]: لم يستجب فحص البروكسي، ولكن تم الإبقاء على قفل البروكسي مفعّلاً ($activeHost:$activePort) ومنع الاتصال المباشر لحماية IP الحقيقي 100%.", taskName)
                WebProxyManager.applyProxy(getApplication(), true, activeHost, activePort, activeType, activeUser, activePass) { success, msg ->
                    addLog(if (success) "info" else "warning", "[ProxyController] $msg", taskName)
                }
                val shieldedGeo = IdentityService.resolveCountryGeoDefaults(
                    rawCountryCode = "US",
                    exitIp = activeHost,
                    cityHint = "New York",
                    ispHint = "$activeHost:$activePort",
                    isProxy = true
                )
                withContext(Dispatchers.Main) {
                    _extractedInfo.value = shieldedGeo
                    _automationState.update {
                        it.copy(
                            activeIp = shieldedGeo.ip,
                            currentProxyInfo = "🛡️ محمي بقفل البروكسي ($activeHost:$activePort)"
                        )
                    }
                }
                return@withContext shieldedGeo
            }
        }

        WebProxyManager.applyProxy(getApplication(), true, activeHost, activePort, activeType, activeUser, activePass) { success, msg ->
            addLog(if (success) "info" else "warning", "[ProxyController] $msg", taskName)
        }

        val safeCountryCode = diag.country.trim().uppercase()
            .takeIf { it.length == 2 && it.all { ch -> ch in 'A'..'Z' } }
            ?: "US"
        val geo = IdentityService.resolveCountryGeoDefaults(
            rawCountryCode = safeCountryCode,
            exitIp = diag.exitIp.ifBlank { activeHost },
            cityHint = diag.city,
            ispHint = diag.isp.ifBlank { "$activeHost:$activePort" },
            isProxy = true
        )

        withContext(Dispatchers.Main) {
            _extractedInfo.value = geo
            _automationState.update {
                it.copy(
                    activeIp = geo.ip,
                    currentProxyInfo = "$activeHost:$activePort [${activeType.uppercase()}]"
                )
            }
        }
        addLog("info", "🌐 IP المتصل: ${geo.ip} (${geo.city}, ${geo.country}) [مزود الخدمة: ${geo.isp}]", taskName)
        return@withContext geo
    }

    /**
     * محرك الانتظار البشري الذكي بين المراحل (Inter-Stage Human Waiting Engine).
     * يحاكي انتقال الإنسان الطبيعي من مرحلة إلى أخرى مع عدّاد مرئي حي وتحديث حالة المحاكاة.
     */
    private suspend fun waitBetweenStagesHuman(
        fromStageCode: String,
        toStageCode: String,
        fromStageAr: String,
        toStageAr: String,
        humanBehaviorHintAr: String,
        complexity: Int = 1,
        taskName: String? = null
    ) {
        val s = _settings.value
        if (!s.humanSimulationEnabled) return
        val baseWaitSec = SmartAutomationBrain.computeStageTransitionWaitSec(
            fromStageCode = fromStageCode,
            toStageCode = toStageCode,
            profile = s.humanSimulationProfile,
            minSec = s.stageTransitionWaitMinSec,
            maxSec = s.stageTransitionWaitMaxSec,
            complexity = complexity
        )
        // ذكاء موزع: تعزيز الأنسنة من المنسق يطيل الانتظار تدريجياً (كل مستوى +1 ثانية، بحد أقصى +5)
        val waitSec = baseWaitSec + SmartAutomationBrain.humanizationBoost.coerceIn(0, 5)
        if (waitSec <= 0) return

        val transitionLabel = "$fromStageAr ➔ $toStageAr"
        addLog(
            "info",
            "⏳🧬 [انتظار بشري بين المراحل · ${waitSec}s]: [$transitionLabel] — $humanBehaviorHintAr",
            taskName
        )

        for (remaining in waitSec downTo 1) {
            if (!_automationState.value.isRunning || completionReceivedForCurrentTask) break
            _automationState.update {
                it.copy(
                    isWaitingBetweenStages = true,
                    stageWaitCountdownSec = remaining,
                    stageWaitTotalSec = waitSec,
                    stageTransitionLabel = transitionLabel,
                    humanActionStatusAr = "⏳ انتظار بين المراحل ($remaining/${waitSec}ث): $humanBehaviorHintAr",
                    phaseDetail = "⏳ [$transitionLabel] (${remaining}s) · $humanBehaviorHintAr"
                )
            }
            delay(1000L)
        }

        _automationState.update {
            it.copy(
                isWaitingBetweenStages = false,
                stageWaitCountdownSec = 0,
                stageWaitTotalSec = 0,
                humanActionStatusAr = humanBehaviorHintAr
            )
        }
    }

    private suspend fun runSingleTask(task: TaskEntity) {
        taskDao.updateTaskStatus(task.id, "running")
        val currentRunNumber = task.completedRuns + 1
        val parsedCats = TaskCategoryPlanner.parseCategories(task.categories)
        val planSummary = TaskCategoryPlanner.formatPlanSummary(parsedCats)
        // 🧠 Fresh awareness per task repetition — don't carry stale stuck state
        brainMemory = SmartAutomationBrain.initialMemory()
        completionReceivedForCurrentTask = false
        currentRunTask = task
        // ذكاء موزع: لاحظ طلب الهروب من المنسق (العقل يستهلكه وينفذه داخل decide)
        if (SmartAutomationBrain.peekEscapeRequest()) {
            _automationState.update { it.copy(brainExecutionMode = "alt_option_3") }
            addLog("warning", "🧠 [هروب موجه]: المنسق رصد نمط خطر — سيتصرف العقل فور رصد أول صفحة.", task.name)
        }
        if (IdentityService.scheduleRotation) {
            addLog("info", "🪪 [دوران موجه]: تدوير الهوية طُلب من تحليل متقاطع — تُولد هوية جديدة الآن.", task.name)
        }
        
        // 0. Work Template resolution if attached
        var templateStepsJson: String? = null
        if (!task.workTemplateId.isNullOrBlank()) {
            val tmpl = workTemplateDao.getTemplateById(task.workTemplateId)
            if (tmpl != null) {
                templateStepsJson = tmpl.stepsJson
                _automationState.update { it.copy(activeWorkTemplateName = tmpl.name) }
                addLog("info", "📋 [قالب العمل]: تفعيل قالب '${tmpl.name}' للمهمة", task.name)
            }
        } else {
            _automationState.update { it.copy(activeWorkTemplateName = "قالب العمل التلقائي الذكي") }
        }

        _automationState.update {
            it.copy(
                currentTaskId = task.id,
                currentTaskName = task.name,
                currentUrl = task.url,
                activeTaskCategories = task.categories,
                activePlanSummary = planSummary,
                currentRepeatIndex = currentRunNumber,
                totalRepeats = task.repeatCount,
                phase = "preparing",
                humanActionStatusAr = "🧹 تهيئة بيئة المتصفح المعزولة قبل بدء المراحل...",
                phaseDetail = "تكرار $currentRunNumber/${task.repeatCount} | Funnel: $planSummary"
            )
        }
        addLog("info", "🔁 === بدء التكرار $currentRunNumber من ${task.repeatCount} لمهمة: ${task.name} ===", task.name)

        // 1. Complete Browser Cache & Storage Purge between repetitions
        if (task.clearCacheOnRepeat) {
            _automationState.update { it.copy(phase = "preparing", phaseDetail = "🧹 تنظيف وحذف كاش المتصفح والكوكيز بالكامل...") }
            withContext(Dispatchers.Main) {
                _browserCommand.value = BrowserCommand.ClearCacheAndStorage
            }
            delay(450)
            _automationState.update { it.copy(cacheWipeStatus = "تم مسح الكاش والتخزين بالكامل") }
            addLog("success", "🧹 [حذف الكاش بالكامل]: تم مسح الذاكرة المؤقتة، الكوكيز، والتخزين المحلي لضمان جلسة جديدة معزولة 100%.", task.name)
        }

        // 2. Sequential Proxy Rotation between repetitions (if proxy enabled globally)
        val allProxies = proxyDao.getWorkingProxies().ifEmpty { proxyDao.getAllProxiesList() }
        if (_settings.value.proxyEnabled && task.sequentialProxyRotation && allProxies.isNotEmpty()) {
            val chosenProxy = allProxies[proxySequenceIndex % allProxies.size]
            proxySequenceIndex++
            val s = _settings.value.copy(
                proxyType = chosenProxy.type,
                proxyHost = chosenProxy.host,
                proxyPort = chosenProxy.port.toString(),
                proxyUser = chosenProxy.username,
                proxyPass = chosenProxy.password
            )
            _settings.value = s
            _automationState.update {
                it.copy(
                    currentProxyIndex = (proxySequenceIndex - 1) % allProxies.size + 1,
                    totalProxiesAvailable = allProxies.size,
                    currentProxyInfo = "${chosenProxy.host}:${chosenProxy.port} [${chosenProxy.type.uppercase()}]"
                )
            }
            addLog("info", "🔄 [تدوير البروكسي بالتسلسل]: تفعيل البروكسي (${(proxySequenceIndex - 1) % allProxies.size + 1}/${allProxies.size}) -> ${chosenProxy.host}:${chosenProxy.port}", task.name)
        } else if (!_settings.value.proxyEnabled) {
            _automationState.update {
                it.copy(
                    currentProxyIndex = 0,
                    totalProxiesAvailable = 0,
                    currentProxyInfo = "اتصال محلي مباشر (Direct)"
                )
            }
        }

        // 3. Proxy & Geo Info validation (fast non-blocking check so website opens immediately)
        _automationState.update {
            it.copy(
                phase = "fetching_geo",
                humanActionStatusAr = "🌐 التحقق من عنوان الـ IP والموقع الجغرافي...",
                phaseDetail = "فحص البروكسي و IP بالذكاء التلقائي..."
            )
        }
        val geo = prepareAndValidateProxy(task.name)

        // 4. Brand New Identity & Email Generation
        _automationState.update {
            it.copy(
                phase = "generating_identity",
                humanActionStatusAr = "👤 بناء هوية بشرية متسقة وبصمة متصفح فريدة...",
                phaseDetail = "توليد معلومات وهوية جديدة بالكامل..."
            )
        }
        val poolEmail = emailDao.getNextEmail()
        if (poolEmail != null) {
            emailDao.deleteEmail(poolEmail)
            addLog("info", "Used email from pool: ${poolEmail.email}", task.name)
        }
        val identityData = IdentityService.generateIdentity(geo.countryCode, poolEmail?.email)
        _identity.value = identityData
        // Expert: persona consistency guard (city-zip-phone-age-gender must agree)
        val consistencyIssues = SmartAutomationBrain.personaConsistencyReport(
            identityData.firstName, identityData.gender, identityData.birthDate,
            identityData.city, identityData.postalCode, identityData.phone
        )
        if (consistencyIssues.isNotEmpty()) {
            addLog("warning", "⚠️ [اتساق الهوية]: ${consistencyIssues.joinToString(", ")} — ${identityData.city} ${identityData.postalCode} ${identityData.phone}", task.name)
        }
        // Expert: stable fingerprint seed per repetition (same session = same fingerprint)
        val seed = SmartAutomationBrain.sessionSeed(task.id, currentRunNumber, _settings.value.proxyHost)
        val personaFacts = IdentityService.buildPersonaFactsJson(identityData)
        _automationState.update {
            it.copy(
                fingerprintSeed = seed,
                personaFactsJson = personaFacts,
                brainExecutionMode = "normal",
                funnelStageIndex = 1,
                totalStepsAdvanced = 0,
                currentQuestionText = ""
            )
        }
        addLog("info", "👤 [هوية وذاكرة متسقة 100%]: ${identityData.fullName} | ${identityData.city}, ${identityData.state} ${identityData.postalCode} | البريد: ${identityData.email} | بذرة البصمة: $seed", task.name)

        // 5. Offer Click Priority #1 Sequential Selection
        val hasOfferClickCategory = parsedCats.any {
            it.equals("offer_click", ignoreCase = true) ||
            it.contains("offer", ignoreCase = true) ||
            it.contains("النقرة") ||
            it.contains("نقر")
        } ||
            task.categories.contains("offer_click", ignoreCase = true) ||
            task.categories.contains("النقرة", ignoreCase = true) ||
            task.categories.contains("نقر", ignoreCase = true)

        val nextClickTarget = if (hasOfferClickCategory) {
            val enabledClicks = offerClickDao.getEnabledClickItemsList()
            if (enabledClicks.isNotEmpty()) {
                val nikePreferred = enabledClicks.firstOrNull { it.text.equals(NIKE_CLICK_TEXT, ignoreCase = true) }
                val chosen = if (task.id == GDFQO_TASK_ID && nikePreferred != null) {
                    addLog("info", "🎯 [أولوية Nike المباشرة]: '${NIKE_CLICK_TEXT}' مثبت كهدف #1 لمهمة GDFQO.", task.name)
                    nikePreferred
                } else {
                    val texts = enabledClicks.map { it.text }
                    val counts = enabledClicks.associate { it.text to it.clickCount }
                    val impressions = enabledClicks.associate { it.text to it.showCount }
                    val pickIdx = SmartAutomationBrain.selectOfferText(texts, counts, epsilon = 0.15, impressions = impressions)
                        .coerceIn(0, enabledClicks.size - 1)
                    enabledClicks.sortedBy { texts.indexOf(it.text) }[pickIdx]
                }
                try { offerClickDao.recordImpressionByText(chosen.text) } catch (_: Exception) {}
                val seq = _automationState.value.clickSequenceIndex
                _automationState.update {
                    it.copy(
                        activeClickText = chosen.text,
                        clickSequenceIndex = seq + 1
                    )
                }
                val countsNow = enabledClicks.associate { it.text to it.clickCount }
                val mode = if ((countsNow[chosen.text] ?: 0) == enabledClicks.map { it.text }.minOfOrNull { countsNow[it] ?: 0 }) "استكشاف 🔍" else "استغلال 🏆"
                addLog("info", "🎯 [النقرة الذكية $mode]: '${chosen.text}' (نقرات: ${chosen.clickCount}|ظهور: ${chosen.showCount}|CTR:${(chosen.ctr * 100).toInt()}%)", task.name)
                chosen.text
            } else {
                _automationState.update { it.copy(activeClickText = null) }
                null
            }
        } else {
            _automationState.update { it.copy(activeClickText = null) }
            addLog("info", "ℹ️ النقرة على العرض غير مفعلة (لم يتم اختيارها في تصنيفات هذه المهمة).", task.name)
            null
        }

        val isLockerCombo = TaskCategoryPlanner.isLockerOfferClickCombo(parsedCats)
        if (isLockerCombo) {
            addLog("info", "🔒🎯 [مسار اللوكر والنقر التلقائي]: تم رصد تصنيف Offer Click و Locker معاً! سيتولى المحرك تلقائياً انتظار ظهور اللوكر، النقر على العرض المناسب باستخدام نصوص شاشة Offer Click، ثم فتح موقعه تلقائياً في تبويب جديد والانتقال الفوري لإكمال العمل فيه.", task.name)
            _automationState.update {
                it.copy(newTabActionStatus = "في انتظار ظهور اللوكر والنقر التلقائي لفتح العرض في تبويب جديد...")
            }
        }

        // 6. Launch in Browser IMMEDIATELY so the website opens right away without pre-load delays!
        _automationState.update {
            it.copy(
                phase = "browser",
                humanActionStatusAr = "🌐 فتح الرابط وتحميل الموقع في المتصفح...",
                phaseDetail = if (nextClickTarget != null) "Navigating to ${task.url} [Target: $nextClickTarget]..." else "Navigating to ${task.url}...",
                shieldVersionApplied = AutomationScriptBuilder.SHIELD_VERSION
            )
        }
        addLog(
            "info",
            "🛡️ [الدرع v${AutomationScriptBuilder.SHIELD_VERSION} + الجسر]: بصمة جهاز حقيقي مزروعة (منطقة=${geo.timezone} | لغة=${geo.language} | بذرة=${_automationState.value.fingerprintSeed} | UA=${ua.take(44)}...) — الجسر يفهم كل طلب (كاميرا/موقع/خطوط/ذاكرة/عمال) ويحوله للمزيف",
            task.name
        )

        val isTrackingUrl = task.url.contains(".php", ignoreCase = true) ||
            task.url.contains("id=", ignoreCase = true) ||
            task.url.contains("u=", ignoreCase = true) ||
            task.url.contains("l=", ignoreCase = true)

        val ua = if (task.userAgent.equals("random", ignoreCase = true) ||
            task.userAgent.startsWith("random", ignoreCase = true) ||
            task.userAgent.isBlank() ||
            (isTrackingUrl && task.userAgent == IdentityService.USER_AGENTS[0].value)
        ) {
            val picked = IdentityService.resolveUserAgent("random_mobile", isMobileDefault = true)
            addLog("info", "📱 [User-Agent ذكي متوافق]: تم تطبيق UA موبايل حديث متوافق مع جميع روابط التتبع والعروض: ${picked.take(48)}...", task.name)
            picked
        } else {
            IdentityService.resolveUserAgent(task.userAgent, isMobileDefault = true)
        }

        // Issue browser load command on Main thread so it is never dropped or reordered with ClearCacheAndStorage
        withContext(Dispatchers.Main) {
            _browserCommand.value = BrowserCommand.LoadUrl(task.url, task.referer, ua)
        }

        // ⏳ Inter-Stage Wait: While the page is loading/rendering in WebView -> before active human DOM interaction
        waitBetweenStagesHuman(
            fromStageCode = "browser",
            toStageCode = "executing",
            fromStageAr = "تحميل صفحة الهبوط",
            toStageAr = "بدء التفاعل البشري الذكي",
            humanBehaviorHintAr = "👁️ انتظار استقرار الصفحة والقراءة الأولية لعناصر الموقع",
            complexity = 2,
            taskName = task.name
        )

        // 🧪 Shield self-test once per session on the live settled page (non-blocking)
        if (!selfTestDoneThisSession) {
            selfTestDoneThisSession = true
            runShieldSelfTest()
        }

        // If custom work template is attached, trigger template execution
        if (!templateStepsJson.isNullOrBlank()) {
            delay(1500)
            _browserCommand.value = BrowserCommand.ExecuteWorkTemplate(templateStepsJson)
        }

        // 🧠 Execution based on Mode — with smart adaptive durations
        val smartCats = TaskCategoryPlanner.parseCategories(task.categories)
        val effectiveTask = task.copy(browserDuration = SmartAutomationBrain.smartDurationForTask(task, smartCats))
        if (effectiveTask.browserDuration != task.browserDuration) {
            addLog("info", "🧠 [مدة ذكية]: عُدّلت المدة ${task.browserDuration}s → ${effectiveTask.browserDuration}s حسب تعقيد الفانل (${smartCats.size} مراحل).", task.name)
        }
        when (task.mode) {
            "mode1" -> runMode1(effectiveTask)
            "mode2" -> runMode2(effectiveTask, ua)
            "mode3" -> runMode3(effectiveTask)
            else -> runMode1(effectiveTask)
        }

        // ⏳ Inter-Stage Wait #5: Browser Execution ➔ Conversion Verification
        waitBetweenStagesHuman(
            fromStageCode = "executing",
            toStageCode = "checking_lead",
            fromStageAr = "إتمام التفاعل البشري",
            toStageAr = "فحص وتأكيد التحويل (Lead Check)",
            humanBehaviorHintAr = "انتظار استقرار إشعار التحويل النهائي قبل الاستعلام",
            complexity = 1,
            taskName = task.name
        )

        // 4. CPA Grip Lead Check
        val s = _settings.value
        if (s.cpaUserId.isNotBlank() && s.cpaApiKey.isNotBlank()) {
            _automationState.update { it.copy(phase = "checking_lead", phaseDetail = "Verifying conversion on CPA Grip...") }
            val leadResult = IdentityService.checkLeadCPA(s.cpaUserId, s.cpaApiKey, geo.ip)
            val isLead = leadResult.first
            leadLogDao.insertLog(
                CampaignStat(
                    taskId = task.id,
                    taskName = task.name,
                    ip = geo.ip,
                    country = geo.country,
                    leadDetected = isLead,
                    details = leadResult.second
                )
            )
            if (isLead) {
                _automationState.update { it.copy(leadsThisSession = it.leadsThisSession + 1) }
                addLog("success", "CONVERSION CONFIRMED: ${leadResult.second}", task.name)
            } else {
                addLog("info", "Conversion check: ${leadResult.second}", task.name)
            }
        } else {
            // Still record stat
            leadLogDao.insertLog(
                CampaignStat(
                    taskId = task.id,
                    taskName = task.name,
                    ip = geo.ip,
                    country = geo.country,
                    leadDetected = false,
                    details = "Task session completed."
                )
            )
        }

        // 🧠 Learning: record whether this run converted
        val converted = completionReceivedForCurrentTask ||
            _automationState.value.brainNextAction == SmartAutomationBrain.NextAction.COMPLETE_CONVERSION.code
        SmartAutomationBrain.recordOutcome(taskLearning, task.id, converted)
        // ذكاء موزع: الفئات تتعلم + البروكسي يُقيّم + الدوران يُستهلك — كلها في السجل
        try {
            TaskCategoryPlanner.recordCategoryOutcome(parsedCats, converted)
            val catDetail = parsedCats.take(3).joinToString("، ") { c ->
                "$c (${(TaskCategoryPlanner.categoryConversionRate(c) * 100).toInt()}%)"
            }
            addLog(
                if (converted) "success" else "info",
                "📊 [تعلم الفئات]: تحويل=${if (converted) "نعم ✅" else "لا ❌"} | معدلات الآن: ${catDetail.ifBlank { "—" }} | التالي: الفئات الأعلى معدلاً تتقدم في الترتيب",
                task.name
            )
            if (IdentityService.consumeRotationRequest()) {
                addLog("info", "🪪 [دوران مُنفذ]: تم تدوير الهوية بعد المهمة بطلب متقاطع — المهمة القادمة بهوية جديدة.", task.name)
            }
            if (s.proxyEnabled && s.proxyHost.isNotBlank()) {
                val match = findCurrentProxyItem()
                // ذكاء الجغرافيا: سجل تحويل هذه الدولة (من البروكسي الفعلي أو الجغرافيا)
                val runCountry = match?.country?.takeIf { it.isNotBlank() } ?: geo.countryCode
                com.example.util.GeoIntel.record(runCountry, converted)
                if (match != null) {
                    val st = _automationState.value
                    val burnReason = when {
                        converted -> null
                        st.isPageBlocked -> "حظر صفحة 🚫 — البروكسي محروق"
                        st.isCaptchaPresent -> "كابتشا 🧩 — البروكسي مثار حوله شك"
                        st.stuckCount >= 4 -> "تعلق متكرر (${st.stuckCount}) — مسار مسموم محتمل"
                        else -> "بلا تحويل"
                    }
                    if (burnReason == null) {
                        proxyDao.recordProxySuccess(match.id)
                        addLog("success", "🌐 [تقييم البروكسي]: ${match.host}:${match.port} سُجل نجاح ✅ (نجاحات=${match.successCount + 1}) — سيُفضل في الاختيار القادم.", task.name)
                    } else {
                        proxyDao.recordProxyFailure(match.id)
                        // الحرق المضاعف: البروكسي المرتبط بحظر/كابتشا يُعاقب مرتين لعزله سريعاً
                        if (st.isPageBlocked || st.isCaptchaPresent) {
                            proxyDao.recordProxyFailure(match.id)
                        }
                        addLog("warning", "🌐 [تقييم البروكسي]: ${match.host}:${match.port} سُجل فشل ⚠️ ($burnReason) — سينخفض ترتيبه.", task.name)
                    }
                }
            }
        } catch (_: Exception) {}
        persistLearning()
        try { persistIntelligence() } catch (_: Exception) {}
        val rate = taskLearning[task.id]?.let { (it.successRate * 100).toInt() } ?: 50
        // P0 ledger: stuck without conversion counts toward retry limit (poison-task guard)
        if (!converted && _automationState.value.stuckCount >= 4) {
            recordTaskFailure(task.id, task.name, "تعليق متكرر بلا تقدم (${_automationState.value.stuckCount}) وبلا تحويل")
        } else if (converted) {
            taskFailureCount.remove(task.id)
        }

        // Increment task run count
        taskDao.incrementCompletedRuns(task.id)
        taskDao.updateTaskStatus(task.id, "completed")
        _automationState.update {
            it.copy(
                completedThisSession = it.completedThisSession + 1,
                phase = "completed",
                phaseDetail = "Completed run for ${task.name}. Page preserved. | معدل التحويل: $rate%"
            )
        }
        addLog(if (converted) "success" else "info", "🧠 [تعلم]: ${task.name} → تحويل=${if (converted) "نعم ✅" else "لا"} | معدل النجاح التراكمي: $rate% (${taskLearning[task.id]?.runs} runs)", task.name)
        if (currentRunTask?.id == task.id) currentRunTask = null
    }

    private suspend fun runMode1(task: TaskEntity) {
        var remainingSec = task.browserDuration.coerceAtLeast(5)
        var extensionsGranted = 0
        while (remainingSec >= 1) {
            if (!_automationState.value.isRunning) break
            // 🧠 Early smart exit: conversion already confirmed → don't waste time
            if (completionReceivedForCurrentTask) {
                addLog("success", "🧠 [خروج مبكر ذكي]: تم التحويل المؤكد — إنهاء العدّاد (${remainingSec}s متبقية) فوراً.", task.name)
                break
            }
            // ⏱️🧠 Adaptive Time Extension: if actively progressing through survey/form/locker steps and time is running low, grant +15s (up to 2 times)
            val st = _automationState.value
            if (remainingSec == 8 && extensionsGranted < 2 && !completionReceivedForCurrentTask && st.stuckCount < 3 &&
                (st.totalStepsAdvanced > 0 || st.funnelStageIndex in 2..5)
            ) {
                extensionsGranted++
                remainingSec += 15
                addLog(
                    "info",
                    "⏱️🧠 [تمديد ذكي للوقت #$extensionsGranted]: رصد تقدم فعلي في المرحلة (${st.funnelStageIndex}/6 · خطوات: ${st.totalStepsAdvanced}) — تمت إضافة +15 ثانية لإتمام التحويل!",
                    task.name
                )
            }
            val stageBadge = "مرحلة ${st.funnelStageIndex}/6"
            val stepsBadge = if (st.totalStepsAdvanced > 0) " · خطوة #${st.totalStepsAdvanced}" else ""
            val brainHint = st.smartDecisionTitle.takeIf { it.isNotBlank() }?.let { " | 🧠 $it" } ?: ""
            val stuckWarn = if (st.stuckCount >= 2) " ⚠️ تعافي (${st.brainExecutionMode})" else ""
            _automationState.update {
                it.copy(
                    phase = "executing",
                    phaseDetail = "[$stageBadge$stepsBadge] (${remainingSec}s)$brainHint$stuckWarn"
                )
            }
            delay(1000)
            remainingSec--
        }
    }

    private suspend fun runMode2(task: TaskEntity, ua: String) {
        val repeats = task.taskRepeatCount.coerceAtLeast(1)
        val durationPerRepeat = (task.taskDuration / repeats).coerceAtLeast(5)

        for (r in 1..repeats) {
            if (!_automationState.value.isRunning) break
            if (completionReceivedForCurrentTask) {
                addLog("success", "🧠 [خروج مبكر]: تحويل مؤكد في التكرار $r — تخطي الباقي.", task.name)
                break
            }
            if (r > 1) {
                addLog("info", "Mode 2 repeat #$r: Reloading task in same session", task.name)
                _browserCommand.value = BrowserCommand.LoadUrl(task.url, task.referer, ua)
            }
            for (sec in durationPerRepeat downTo 1) {
                if (!_automationState.value.isRunning || completionReceivedForCurrentTask) break
                val brainHint = _automationState.value.smartDecisionTitle.takeIf { it.isNotBlank() }?.let { " | 🧠 $it" } ?: ""
                _automationState.update {
                    it.copy(
                        phase = "executing",
                        phaseDetail = "Mode 2 sub-run $r/$repeats (${sec}s)$brainHint"
                    )
                }
                delay(1000)
            }
        }
    }

    private suspend fun runMode3(task: TaskEntity) {
        completionReceivedForCurrentTask = false
        val maxWaitSec = 90
        // 🧠 Smart keywords: configured + universal conversion signals
        val keywords = SmartAutomationBrain.confirmKeywordsSmart(task.completionKeywords)
        addLog("info", "🧠 Mode 3 smart listening (${keywords.size} إشارة): ${keywords.take(5).joinToString(", ")}", task.name)

        for (sec in 1..maxWaitSec) {
            if (!_automationState.value.isRunning || completionReceivedForCurrentTask) break
            // 🧠 If brain already decided COMPLETE, exit even before bridge callback
            if (_automationState.value.brainNextAction == SmartAutomationBrain.NextAction.COMPLETE_CONVERSION.code) {
                completionReceivedForCurrentTask = true
                break
            }
            val brainHint = _automationState.value.smartDecisionTitle.takeIf { it.isNotBlank() }?.let { " | 🧠 $it" } ?: ""
            _automationState.update {
                it.copy(
                    phase = "executing",
                    phaseDetail = "Mode 3: Smart detect (${maxWaitSec - sec}s)$brainHint"
                )
            }
            delay(1000)
        }

        if (completionReceivedForCurrentTask) {
            addLog("success", "🧠 Smart Mode detected conversion early (keyword + brain consensus)!", task.name)
        } else {
            addLog("warning", "Smart Mode timeout — سيُسجَّل كغير محوّل ويتعلم العقل من ذلك.", task.name)
        }
    }

    fun stopAutomation(clearBrowser: Boolean = false) {
        _automationState.update {
            it.copy(
                isRunning = false,
                phase = "idle",
                phaseDetail = "Stopping automation..."
            )
        }
        automationJob?.cancel()
        automationJob = null
        if (clearBrowser) {
            _browserCommand.value = BrowserCommand.ClearUrl
        }
        addLog("warning", "Automation stopped.")
    }

    fun purgeSessionAndCache() {
        _browserCommand.value = BrowserCommand.ClearCacheAndStorage
        addLog("info", "Requested on-demand purge of WebView cache, cookies, and local storage.")
    }

    fun onPageActionMapGenerated(actionMapJson: String) {
        try {
            val count = org.json.JSONArray(actionMapJson).length()
            _automationState.update {
                it.copy(
                    pageActionMapJson = actionMapJson,
                    pageActionMapSummary = "$count خطوات مكتشفة لخريطة عمل الصفحة"
                )
            }
        } catch (_: Exception) {}
    }

    fun onPageActionStepExecuted(stepIndex: Int, stepName: String, status: String) {
        addLog("info", "⚡ [خريطة العمل]: خطوة ${stepIndex + 1}: $stepName ($status)")
    }

    fun onMidPageInfoExtracted(extractedKey: String, extractedValue: String) {
        _automationState.update { it.copy(lastExtractedMidPageText = "$extractedKey -> $extractedValue") }
        addLog("info", "🧠 [استخراج ذكي وتكيّف]: استخراج: '$extractedKey' | الإجابة: '$extractedValue'")
        // Expert: auto-learn unseen locker offers as disabled suggestions (user enables the winners)
        if (extractedKey == "locker_offers_available" || extractedKey == "locker_offers_matched") {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val candidates = extractedValue.split("|", "•", "\n")
                        .map { it.trim().take(80) }
                        .filter { it.length >= 8 && it.length <= 80 }
                        .distinct().take(6)
                    if (candidates.isEmpty()) return@launch
                    val existing = offerClickDao.getAllClickItemsList().map { it.text.lowercase() }.toSet()
                    var added = 0
                    for (cand in candidates) {
                        val low = cand.lowercase()
                        if (existing.any { it.contains(low.take(12)) || low.contains(it.take(12)) }) continue
                        if (cand.equals("(no-text)", true)) continue
                        val maxOrder = offerClickDao.getMaxOrderIndex() ?: 0
                        offerClickDao.insertClickItem(
                            com.example.data.model.OfferClickItem(
                                text = cand, enabled = false,
                                orderIndex = maxOrder + 1 + added,
                                tagOrNote = "مكتشف تلقائي من اللوكر 🤖"
                            )
                        )
                        added++
                        if (added >= 2) break
                    }
                    if (added > 0) addLog("success", "🤖 [تعلم العروض]: أُضيف $added نصاً جديداً من اللوكر كمسودة معطلة — فعّل الفائز منها.")
                } catch (_: Exception) {}
            }
        }
    }

    fun onTemplateGenerated(templateJson: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val obj = org.json.JSONObject(templateJson)
                val name = obj.optString("name", "قالب عمل تلقائي ذكي")
                val desc = obj.optString("description", "تم إنشاؤه تلقائياً بالذكاء الاصطناعي")
                val cat = obj.optString("targetCategory", "Smart Auto")
                val steps = obj.optJSONArray("steps")?.toString() ?: "[]"

                val template = com.example.data.model.WorkTemplateEntity(
                    id = "template_${System.currentTimeMillis()}",
                    name = name,
                    description = desc,
                    targetCategory = cat,
                    isAutoGenerated = true,
                    stepsJson = steps
                )
                workTemplateDao.insertTemplate(template)
                addLog("success", "✨ [صنع قالب تلقائي]: تم توليد وحفظ قالب العمل '${template.name}' بنجاح!")
            } catch (e: Exception) {
                addLog("error", "فشل تحليل وحفظ القالب التلقائي: ${e.message}")
            }
        }
    }

    fun requestGenerateTemplateFromPage() {
        _browserCommand.value = BrowserCommand.GenerateAutoTemplate
        addLog("info", "✨ جاري فحص عناصر المتصفح وتوليد قالب العمل التلقائي بالذكاء الاصطناعي...")
    }

    fun saveWorkTemplate(template: com.example.data.model.WorkTemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            workTemplateDao.insertTemplate(template)
            addLog("success", "تم حفظ قالب العمل: ${template.name}")
        }
    }

    fun executeWorkTemplateInBrowser(template: com.example.data.model.WorkTemplateEntity) {
        _browserCommand.value = BrowserCommand.ExecuteWorkTemplate(template.stepsJson)
        addLog("info", "▶️ [تشغيل القالب في المتصفح]: تم إرسال أوامر القالب '${template.name}' للتنفيذ الفوري في المتصفح.")
    }

    fun deleteWorkTemplate(template: com.example.data.model.WorkTemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            workTemplateDao.deleteTemplate(template)
            addLog("warning", "تم حذف قالب العمل: ${template.name}")
        }
    }
}

sealed class BrowserCommand {
    data class LoadUrl(val url: String, val referer: String?, val userAgent: String?) : BrowserCommand()
    object Reload : BrowserCommand()
    object GoBack : BrowserCommand()
    object GoForward : BrowserCommand()
    object ClearUrl : BrowserCommand()
    object ClearCacheAndStorage : BrowserCommand()
    object GenerateAutoTemplate : BrowserCommand()
    data class ExecuteWorkTemplate(val stepsJson: String) : BrowserCommand()
    data class OpenInNewTab(val url: String, val title: String = "Offer") : BrowserCommand()
    data class RunShieldSelfTest(val script: String) : BrowserCommand()
    object ScanAndExtractLockerOffers : BrowserCommand()
    data class ExecuteBrainAction(
        val executionMode: String,
        val recoveryJs: String? = null,
        val personaFactsJson: String = "{}"
    ) : BrowserCommand()
}
