package com.example.service

import com.example.data.model.TaskEntity
import org.json.JSONObject
import kotlin.math.min
import kotlin.random.Random

/**
 * SmartAutomationBrain — العقل المركزي للأتمتة الذكية.
 *
 * يجعل الأتمتة:
 *  - فاهمة: تحلل الصفحة + السياق + التاريخ بدل رد فعل أعمى لكلمات مفتاحية.
 *  - واعية: تتذكر أين كانت، ماذا فعلت، هل تقدمت أم علقت، وتتعلم من الفشل.
 *  - متكيفة: تغير الإستراتيجية والتوقيت تلقائياً حسب تعقيد الصفحة وحالتها.
 */
object SmartAutomationBrain {

    // ── Intelligence API: ذكاء العقل — يستقبل من الجسر والهوية ويوجه ──
    @Volatile
    var escapeRequested: Boolean = false
        private set

    @Volatile
    var humanizationBoost: Int = 0

    @Volatile
    private var lastProxyIssueReason: String = ""

    @Volatile
    private var lastProxyIssueProtocol: String = ""

    fun recordProxyIssue(reason: String, protocol: String) {
        lastProxyIssueReason = reason
        lastProxyIssueProtocol = protocol
    }

    fun lastProxyIssue(): Pair<String, String> = lastProxyIssueReason to lastProxyIssueProtocol

    fun triggerEscapeStrategy() {
        escapeRequested = true
    }

    fun consumeEscapeRequest(): Boolean {
        val v = escapeRequested
        escapeRequested = false
        return v
    }

    fun peekEscapeRequest(): Boolean = escapeRequested

    /** تأخير مضخم بتعزيز الأنسنة من المنسق (+0.8 ثانية لكل مستوى) */
    fun boostedDelay(baseMs: Long): Long =
        baseMs + humanizationBoost.coerceIn(0, 5) * 800L

    fun increaseHumanizationFactor() {
        humanizationBoost = (humanizationBoost + 1).coerceAtMost(5)
    }

    // ── أنواع القرارات ──────────────────────────────────────────
    enum class NextAction(val code: String) {
        COMPLETE_CONVERSION("complete"),
        PASS_BRIDGE("pass_bridge"),
        CLICK_OFFER("click_offer"),
        WAIT_LOCKER("wait_locker"),
        FILL_FORM("fill_form"),
        ANSWER_SURVEY("answer_survey"),
        SKIP_UPSELL("skip_upsell"),
        CHECK_TERMS("check_terms"),
        WAIT_CAPTCHA("wait_captcha"),
        WAIT_LOAD("wait_load"),
        RELOAD_RETRY("reload_retry"),
        SWITCH_STRATEGY("switch_strategy"),
        EXPLORE_SCROLL("explore_scroll"),
        SUBMIT_ADVANCE("submit_advance")
    }

    data class SmartDecision(
        val action: NextAction,
        val titleAr: String,
        val titleEn: String,
        val reasonAr: String,
        val reasonEn: String,
        val confidence: Int, // 0..100
        val delayMsBeforeNextCycle: Long, // توقيت بشري تكيفي
        val prioritySteps: List<String>,
        val needsReload: Boolean = false,
        val needsProxySwitch: Boolean = false,
        val isStuck: Boolean = false,
        val executionMode: String = "normal", // "normal", "retry_react_events", "alt_option_2", "alt_option_3", "force_submit"
        val recoveryCommandJs: String? = null,
        val shouldExtendTaskTime: Boolean = false,
        val funnelStageIndex: Int = 1,
        val stepsAdvanced: Int = 0,
        val detectedQuestion: String = ""
    )

    data class PageMemory(
        val url: String,
        val category: String,
        val domSignature: String = "",
        val questionText: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    // ذاكرة الجلسة — تُحفظ في AppViewModel وتُمرر للعقل
    data class BrainMemory(
        val pageHistory: List<PageMemory> = emptyList(),
        val actionTrace: List<String> = emptyList(), // أكواد NextAction السابقة
        val consecutiveSamePage: Int = 0,
        val consecutiveNoProgress: Int = 0,
        val reloadAttemptsForUrl: Int = 0,
        val lastUrl: String = "",
        val lastCategory: String = "",
        val lastDomSignature: String = "",
        val lastQuestionText: String = "",
        val lastFilledFieldsCount: Int = 0,
        val totalStepsAdvanced: Int = 0,
        val sessionStartTime: Long = System.currentTimeMillis()
    )

    data class TaskLearningStats(
        val taskId: String,
        val runs: Int = 0,
        val conversions: Int = 0
    ) {
        val successRate: Double get() = if (runs == 0) 0.5 else conversions.toDouble() / runs.toDouble()
    }

    // ── كلمات الكشف الذكي ───────────────────────────────────────
    private val BLOCK_KEYWORDS = listOf(
        "access denied", "forbidden", "403 forbidden", "your ip has been blocked",
        "suspicious activity", "unusual traffic", "please verify you are a human",
        "attention required", "cloudflare ray", "error 1020", "ip banned"
    )
    private val CAPTCHA_KEYWORDS = listOf(
        "captcha", "recaptcha", "hcaptcha", "turnstile", "i'm not a robot",
        "select all images", "verify you are human", "checkbox.*robot"
    )
    private val LOADING_KEYWORDS = listOf(
        "loading", "please wait", "redirecting", "verifying your browser",
        "checking your browser", "just a moment"
    )
    // عبارات التحويل النهائي المؤكد فقط (بدون كلمات عامة مثل reward أو welcome تظهر في بداية الموقع)
    private val CONFIRM_EXTRA = listOf(
        "thank you for completing", "thank you for your submission", "submission received",
        "registration complete", "entry confirmed", "order received",
        "claim confirmed", "responses recorded", "ticket number",
        "application submitted", "survey completed", "تم التسجيل بنجاح", "شكراً لإكمالك"
    )

    /**
     * القرار الرئيسي: ماذا نفعل الآن؟ (حلقة مغلقة تربط جميع محركات الذكاء)
     */
    fun decide(
        report: TaskCategoryPlanner.PageAnalysisReport,
        rawJson: String,
        configuredCategories: List<String>,
        activeClickTarget: String?,
        memory: BrainMemory,
        taskLearning: TaskLearningStats? = null,
        blankWallEviction: Boolean = true
    ): SmartDecision {
        val extras = parseExtras(rawJson)
        val isBlocked = detectBlocked(report, extras)
        val isCaptcha = detectCaptcha(report, extras)
        val isLoading = detectLoading(report, extras)

        // حساب التقدم الهيكلي الفعلي (حتى لو لم يتغير الرابط في مواقع SPA والاستبيانات)
        val currentSig = report.domSignature.ifBlank {
            "${report.detectedCategory}:${report.fieldsCount}:${report.radioGroupsCount}:${report.selectCount}:${report.buttonsCount}:${report.primaryCtaText}"
        }
        val currentQ = report.currentQuestionText.trim()
        val domChanged = memory.lastDomSignature.isNotBlank() && currentSig != memory.lastDomSignature
        val questionChanged = memory.lastQuestionText.isNotBlank() && currentQ.isNotBlank() && currentQ != memory.lastQuestionText
        val newlyFilled = report.filledFieldsCount > memory.lastFilledFieldsCount
        val justProgressed = domChanged || questionChanged || newlyFilled

        val effectiveNoProgress = if (justProgressed) 0 else memory.consecutiveNoProgress
        val samePageRepeat = memory.consecutiveSamePage
        val isStuck = !justProgressed && ((samePageRepeat >= 2 && effectiveNoProgress >= 2) || effectiveNoProgress >= 3)
        val stepsSoFar = memory.totalStepsAdvanced + (if (justProgressed) 1 else 0)

        var executionMode = when {
            report.isConfirmationPage -> "confirm_lock"
            effectiveNoProgress == 1 -> "retry_react_events"
            effectiveNoProgress == 2 -> "alt_option_2"
            effectiveNoProgress == 3 -> "alt_option_3"
            effectiveNoProgress >= 4 -> "force_submit"
            else -> "normal"
        }
        // ذكاء التسلسلات: طريق ميت معروف → صعّد الوضع فوراً بدل إضاعة دورات
        if (executionMode != "confirm_lock") {
            val deadPenalty = SequenceIntel.deadPrefixPenalty(memory.actionTrace)
            if (deadPenalty >= 2) {
                executionMode = escalateMode(executionMode)
            }
        }

        // 1) محظور — لا تعاند، بدّل الهوية/البروكسي
        if (isBlocked) {
            return SmartDecision(
                action = NextAction.RELOAD_RETRY,
                titleAr = "🚫 رصد حظر / منع — تبديل ذكي للمسار",
                titleEn = "Block detected — smart evade",
                reasonAr = "الصفحة تظهر رسالة حظر أو رفض (${extras.matchedBlockKeyword}). القرار: انتظار قصير ثم إعادة تحميل بهوية/مسار جديد.",
                reasonEn = "Block wall detected. Rotate identity/proxy instead of hammering.",
                confidence = 92,
                delayMsBeforeNextCycle = boostedDelay(3500),
                prioritySteps = listOf("إيقاف التعبئة فوراً", "تسجيل سبب الحظر", "تدوير البروكسي", "إعادة المحاولة بهوية جديدة"),
                needsReload = true,
                needsProxySwitch = true,
                isStuck = true,
                executionMode = "normal",
                funnelStageIndex = report.funnelStageIndex,
                stepsAdvanced = stepsSoFar
            )
        }

        // 2) كابتشا — انتظر ولا تخرب الحل
        if (isCaptcha) {
            return SmartDecision(
                action = NextAction.WAIT_CAPTCHA,
                titleAr = "🧩 كابتشا / تحقق بشري — وضع الانتظار الذكي",
                titleEn = "Captcha — patient wait",
                reasonAr = "رُصد تحقق بشري (${extras.matchedCaptchaKeyword}). النقر العشوائي يفشل التحدي. القرار: تمرير هادئ وانتظار الحل ثم الاستئناف.",
                reasonEn = "Captcha challenge present. Wait patiently, don't interfere.",
                confidence = 90,
                delayMsBeforeNextCycle = boostedDelay(6500),
                prioritySteps = listOf("عدم لمس مربع الكابتشا", "تمرير خفيف طبيعي", "انتظار الحل التلقائي/اليدوي", "إعادة الفحص بعد الانتظار"),
                executionMode = "normal",
                shouldExtendTaskTime = true,
                funnelStageIndex = report.funnelStageIndex,
                stepsAdvanced = stepsSoFar
            )
        }

        // 3) تحويل مؤكد — أنهِ بذكاء (يُفحص قبل العالق حتى لا نعيد تحميل صفحة نجاح)
        if (report.isConfirmationPage && report.unfilledRequiredCount == 0 && report.radioGroupsCount == 0) {
            return SmartDecision(
                action = NextAction.COMPLETE_CONVERSION,
                titleAr = "🏆 تحويل مؤكد (المرحلة 6/6) — إغلاق ذكي للتكرار",
                titleEn = "Conversion confirmed",
                reasonAr = "رُصدت صفحة الشكر/النجاح النهائية بثقة ${report.confidence}% بدون حقول متبقية. القرار: حفظ الشاشة وتسجيل التحويل فوراً.",
                reasonEn = "Thank-you page confirmed. Stop and record lead.",
                confidence = min(99, report.confidence + 5),
                delayMsBeforeNextCycle = 1500,
                prioritySteps = listOf("تثبيت صفحة الإثبات", "تسجيل الإحصائية وتحديث ذاكرة النطاق", "إنهاء التكرار بنجاح"),
                executionMode = "confirm_lock",
                funnelStageIndex = 6,
                stepsAdvanced = stepsSoFar
            )
        }

        // 3.5) هروب موجه من المنسق — رؤية متقاطعة (بروكسي/هوية) تأمر بتغيير المسار الآن
        if (escapeRequested) {
            consumeEscapeRequest()
            val proxyNote = lastProxyIssueReason.takeIf { it.isNotBlank() }
                ?.let { " (مشكلة بروكسي مسجلة: $it)" } ?: ""
            return SmartDecision(
                action = NextAction.SWITCH_STRATEGY,
                titleAr = "🧠🚨 هروب موجه من الذكاء المتقاطع$proxyNote",
                titleEn = "Coordinator-directed escape",
                reasonAr = "المنسق رصد نمط خطر عبر الطبقات (بروكسي/بصمة/سلوك) وأمر بالهروب الفوري بدل الاستمرار العشوائي.$proxyNote",
                reasonEn = "Cross-layer insight ordered an immediate escape.",
                confidence = 88,
                delayMsBeforeNextCycle = boostedDelay(2600),
                prioritySteps = listOf("تدوير البروكسي", "إعادة تحميل بمسار جديد", "إعادة تقييم الصفحة بعد الاستقرار"),
                needsReload = true,
                needsProxySwitch = true,
                isStuck = true,
                executionMode = "alt_option_3",
                shouldExtendTaskTime = stepsSoFar > 0,
                funnelStageIndex = report.funnelStageIndex,
                stepsAdvanced = stepsSoFar,
                detectedQuestion = currentQ
            )
        }

        // 3.7) جدار الصفحة البيضاء: تحميل مكتمل بلا أي محتوى = رفض ASN/بصمة (شائع مع AWS/Cloudflare)
        // حذر: يُشترط تكرار الرؤية (صفحة تحميل حقيقية تبدو فارغة أول مرة)
        val isBlankWall = !isLoading &&
            !report.isConfirmationPage && !report.isRedirectBridge &&
            report.buttonsCount == 0 && report.fieldsCount == 0 &&
            report.radioGroupsCount == 0 && report.selectCount == 0 &&
            report.checkboxesCount == 0 && !report.hasLocker &&
            !report.hasOfferClickCandidate && report.title.trim().length < 4 &&
            report.url.isNotBlank() &&
            (samePageRepeat >= 1 || effectiveNoProgress >= 2)
        if (blankWallEviction && isBlankWall) {
            return SmartDecision(
                action = NextAction.RELOAD_RETRY,
                titleAr = "⬜ جدار الصفحة البيضاء — إخلاء المسار المحروق",
                titleEn = "Blank-wall eviction — rotate burned route",
                reasonAr = "اكتمل التحميل (${report.url.take(48)}) بلا أزرار ولا حقول ولا عنوان — نمط رفض الشبكة/البصمة. القرار: حرق البروكسي الحالي، التدوير، وإعادة التحميل.",
                reasonEn = "Loaded blank page = ASN/fingerprint refusal. Burn proxy and rotate.",
                confidence = 89,
                delayMsBeforeNextCycle = boostedDelay(3000),
                prioritySteps = listOf("تسجيل البروكسي كمحروق", "تدوير البروكسي فوراً", "فرض وضع الجوال عند التكرار", "إعادة التحميل بهوية جديدة"),
                needsReload = true,
                needsProxySwitch = true,
                isStuck = true,
                executionMode = "blank_wall_evict",
                shouldExtendTaskTime = false,
                funnelStageIndex = report.funnelStageIndex,
                stepsAdvanced = stepsSoFar,
                detectedQuestion = currentQ
            )
        }

        // 4) عالق — غيّر الإستراتيجية ونفّذ كود التعافي متعدد المراحل فوراً
        if (isStuck) {
            val alternative = pickEscapeStrategy(report, memory, effectiveNoProgress)
            val recoveryJs = buildStuckRecoveryScript(effectiveNoProgress, report.detectedCategory)
            val proxyKnownBad = lastProxyIssueReason.isNotBlank()
            return SmartDecision(
                action = NextAction.SWITCH_STRATEGY,
                titleAr = "🔄 ذكاء التعافي التلقائي (الطور $effectiveNoProgress) — $alternative",
                titleEn = "Stuck recovery level $effectiveNoProgress — switch strategy",
                reasonAr = "نفس البصمة الهيكلية (${report.detectedCategoryAr}) تكررت بدون انتقال. العقل غيّر وضع التنفيذ إلى ($executionMode): $alternative." +
                    if (proxyKnownBad) " مشكلة البروكسي المسجلة ($lastProxyIssueReason) ترجح تبديل المسار أيضاً." else "",
                reasonEn = "Loop detected. Executing recovery mode $executionMode: $alternative",
                confidence = 86,
                delayMsBeforeNextCycle = boostedDelay(2400),
                prioritySteps = listOf("تغيير وضع التنفيذ إلى $executionMode", alternative, "إطلاق أحداث React/Pointer الكاملة", "تفعيل زر الانتقال التالي") +
                    if (proxyKnownBad) listOf("تبديل البروكسي (مشكلة مسجلة)") else emptyList(),
                needsReload = memory.reloadAttemptsForUrl == 0 && effectiveNoProgress >= 5,
                needsProxySwitch = proxyKnownBad,
                isStuck = true,
                executionMode = executionMode,
                recoveryCommandJs = recoveryJs,
                shouldExtendTaskTime = stepsSoFar > 0,
                funnelStageIndex = report.funnelStageIndex,
                stepsAdvanced = stepsSoFar,
                detectedQuestion = currentQ
            )
        }

        // 5) جسر تتبع / تحويل (PHP / Smartlink Bridge)
        if (report.isRedirectBridge) {
            return SmartDecision(
                action = NextAction.PASS_BRIDGE,
                titleAr = "🚀 عبور جسر التتبع والتحويل التلقائي (المرحلة 1/6)",
                titleEn = "Pass tracking & redirect bridge",
                reasonAr = "صفحة توجيه وسيطة (${report.url.take(45)}). المحرك يستخرج رابط العرض النهائي أو ينقر زر المتابعة تلقائياً.",
                reasonEn = "Tracking bridge detected. Extracting target offer or clicking continue.",
                confidence = 94,
                delayMsBeforeNextCycle = 1800,
                prioritySteps = listOf("فحص تحويلات Meta/JS/PHP", "إرسال نماذج التوجيه الخفية", "نقر زر المتابعة للوصول للعرض"),
                executionMode = executionMode,
                recoveryCommandJs = buildBridgeAdvanceScript(),
                funnelStageIndex = 1,
                stepsAdvanced = stepsSoFar
            )
        }

        // 6) صفحة ما زالت تحمل
        if (isLoading || report.confidence < 40) {
            return SmartDecision(
                action = NextAction.WAIT_LOAD,
                titleAr = "⏳ الصفحة ما زالت تتحمل — انتظار ذكي",
                titleEn = "Page still loading",
                reasonAr = "المحتوى غير مكتمل (تحميل/إعادة توجيه). القرار: انتظار قصير ثم إعادة التحليل تلقائياً.",
                reasonEn = "Incomplete load. Wait then re-analyze.",
                confidence = 80,
                delayMsBeforeNextCycle = 2200,
                prioritySteps = listOf("انتظار اكتمال عناصر الـ DOM", "إعادة تحليل الصفحة"),
                executionMode = executionMode,
                funnelStageIndex = report.funnelStageIndex,
                stepsAdvanced = stepsSoFar
            )
        }

        // 7) لوكر — أولوية قصوى
        val hasLockerCombo = TaskCategoryPlanner.isLockerOfferClickCombo(configuredCategories) ||
            report.hasLocker || report.detectedCategory.contains("locker", ignoreCase = true)
        if (hasLockerCombo && report.hasLocker) {
            val target = activeClickTarget ?: "العرض الأول المتاح"
            return SmartDecision(
                action = NextAction.WAIT_LOCKER,
                titleAr = "🔒 لوكر نشط (المرحلة 2/6) — مطابقة ونقر ذكي",
                titleEn = "Locker — match & click",
                reasonAr = "اللوكر ظاهر (${report.summary}). الهدف: '$target'. القرار: مطابقة العروض والنقر ثم نقل العمل للتبويب الجديد فوراً.",
                reasonEn = "Locker visible. Match offer and transition to new tab.",
                confidence = 94,
                delayMsBeforeNextCycle = adaptiveDelay(report, base = 2200),
                prioritySteps = listOf("مسح عروض اللوكر", "مطابقة '$target'", "النقر وفتح تبويب جديد", "نقل التركيز للتبويب الجديد"),
                executionMode = executionMode,
                shouldExtendTaskTime = true,
                funnelStageIndex = 2,
                stepsAdvanced = stepsSoFar
            )
        }

        // 8) تخطي Upsell — لا تدفع أبداً
        if (report.hasSkipButtons) {
            return SmartDecision(
                action = NextAction.SKIP_UPSELL,
                titleAr = "⏭️ جدار عروض إضافية (المرحلة 5/6) — تخطٍ ذكي",
                titleEn = "Upsell wall — skip",
                reasonAr = "رُصدت أزرار (No Thanks / Skip / Pass). القرار: تخطي العروض الفرعية فوراً للوصول إلى صفحة التأكيد.",
                reasonEn = "Sponsor wall. Skip immediately.",
                confidence = 92,
                delayMsBeforeNextCycle = adaptiveDelay(report, base = 1800),
                prioritySteps = listOf("تحديد زر No Thanks/Skip", "النقر والتجاوز الفوري", "المتابعة للمرحلة النهائية"),
                executionMode = executionMode,
                shouldExtendTaskTime = true,
                funnelStageIndex = 5,
                stepsAdvanced = stepsSoFar
            )
        }

        // 9) استبيان — أجب بذكاء حسب السياق وحقائق الهوية
        if (report.radioGroupsCount >= 1 || report.selectCount >= 1 || currentQ.isNotBlank() ||
            report.detectedCategory.contains("survey", ignoreCase = true)
        ) {
            val qLabel = if (currentQ.isNotBlank()) "السؤال: '${currentQ.take(42)}'" else "${report.radioGroupsCount} مجموعات خيارات"
            val ctaLabel = report.primaryCtaText.ifBlank { "Next / Continue" }
            return SmartDecision(
                action = NextAction.ANSWER_SURVEY,
                titleAr = "📝 استبيان ذكي (المرحلة 3/6 · خطوة #${stepsSoFar + 1}) — إجابة متسقة",
                titleEn = "Survey — persona-coherent answer",
                reasonAr = "تم رصد سؤال تفاعلي ($qLabel). القرار: مطابقة منطوق السؤال مع ذاكرة الهوية (العمر، الدخل، السكن، الموقع) ثم نقر ($ctaLabel).",
                reasonEn = "Survey question detected. Match with Persona Facts and click $ctaLabel.",
                confidence = 91,
                delayMsBeforeNextCycle = adaptiveDelay(report, base = 2400),
                prioritySteps = listOf("تحليل منطوق السؤال ($qLabel)", "اختيار الإجابة المطابقة للهوية", "إطلاق أحداث React/Change", "النقر على ($ctaLabel)"),
                executionMode = executionMode,
                shouldExtendTaskTime = true,
                funnelStageIndex = 3,
                stepsAdvanced = stepsSoFar,
                detectedQuestion = currentQ
            )
        }

        // 10) نموذج بيانات — املأ بفهم الحقول
        if (report.emailFields >= 1 || report.textFields >= 1 ||
            report.detectedCategory.contains("lead", ignoreCase = true) ||
            report.detectedCategory.contains("email", ignoreCase = true)
        ) {
            val fieldsDesc = "متبقي ${report.unfilledRequiredCount} حقول، معبأ ${report.filledFieldsCount}"
            val ctaLabel = report.primaryCtaText.ifBlank { "Submit / Continue" }
            return SmartDecision(
                action = NextAction.FILL_FORM,
                titleAr = "📋 نموذج بيانات (المرحلة 4/6) — تعبئة وإرسال متكامل",
                titleEn = "Lead form — smart persona fill",
                reasonAr = "نموذج تسجيل ($fieldsDesc). القرار: ضخ بيانات الهوية المتوافقة جغرافياً مع تفعيل React ValueTracker والموافقة على الشروط ثم نقر ($ctaLabel).",
                reasonEn = "Form detected ($fieldsDesc). Fill persona fields and click $ctaLabel.",
                confidence = 90,
                delayMsBeforeNextCycle = adaptiveDelay(report, base = 2600),
                prioritySteps = listOf("مطابقة كل حقل (بريد/اسم/هاتف/عنوان/رمز بريدي)", "ضخ متوافق مع React/Vue", "تحديد مربعات الموافقة الإلزامية", "نقر زر ($ctaLabel)"),
                executionMode = executionMode,
                shouldExtendTaskTime = true,
                funnelStageIndex = 4,
                stepsAdvanced = stepsSoFar
            )
        }

        // 11) شروط معلقة
        if (report.checkboxesCount >= 1) {
            return SmartDecision(
                action = NextAction.CHECK_TERMS,
                titleAr = "📜 موافقة الشروط — تحديد واعٍ ومتابعة",
                titleEn = "Terms consent",
                reasonAr = "مربعات موافقة غير محددة (${report.checkboxesCount}). القرار: تحديد الإلزامي فقط ثم النقر على زر المتابعة.",
                reasonEn = "Unchecked boxes block progress. Check required only.",
                confidence = 86,
                delayMsBeforeNextCycle = 2000,
                prioritySteps = listOf("تمييز الإلزامي عن الدعائي", "تحديد الشروط والعمر", "النقر على زر المتابعة"),
                executionMode = executionMode,
                funnelStageIndex = report.funnelStageIndex,
                stepsAdvanced = stepsSoFar
            )
        }

        // 12) مرشح نقرة عرض
        if (report.hasOfferClickCandidate && activeClickTarget != null) {
            return SmartDecision(
                action = NextAction.CLICK_OFFER,
                titleAr = "🎯 عرض مطابق — نقرة بشرية محسوبة",
                titleEn = "Matching offer — calculated click",
                reasonAr = "النص المستهدف '$activeClickTarget' ظاهر في الصفحة. القرار: نقرة بشرية كاملة مع تتبع التبويب الجديد.",
                reasonEn = "Target text visible. Single calculated click.",
                confidence = 92,
                delayMsBeforeNextCycle = 2200,
                prioritySteps = listOf("تمرير حتى ظهور العنصر", "محاكاة المؤشر واللمس", "نقرة واحدة دقيقة", "انتظار فتح العرض"),
                executionMode = executionMode,
                shouldExtendTaskTime = true,
                funnelStageIndex = report.funnelStageIndex,
                stepsAdvanced = stepsSoFar
            )
        }

        // 13) صفحة هبوط تحتوي زر انتقال واضح (CTA)
        if (report.primaryCtaText.isNotBlank() || report.buttonsCount >= 1) {
            val cta = report.primaryCtaText.ifBlank { "Continue / Start" }
            return SmartDecision(
                action = NextAction.SUBMIT_ADVANCE,
                titleAr = "🚀 صفحة هبوط تفاعلية — تفعيل زر التقدم ($cta)",
                titleEn = "Interactive lander — click CTA ($cta)",
                reasonAr = "تم رصد زر انتقال رئيسي ($cta) في الصفحة. القرار: تمرير طبيعي وتفعيل الزر للانتقال لمرحلة الأسئلة أو التسجيل.",
                reasonEn = "Primary CTA ($cta) detected. Scroll and activate to advance funnel.",
                confidence = 82,
                delayMsBeforeNextCycle = adaptiveDelay(report, base = 2400),
                prioritySteps = listOf("تمرير سلس نحو زر ($cta)", "محاكاة تفاعل بشري", "النقر والانتقال للمرحلة التالية"),
                executionMode = executionMode,
                recoveryCommandJs = buildBridgeAdvanceScript(),
                funnelStageIndex = report.funnelStageIndex,
                stepsAdvanced = stepsSoFar
            )
        }

        // 14) افتراضي — استكشاف هادئ
        return SmartDecision(
            action = NextAction.EXPLORE_SCROLL,
            titleAr = "🌐 استكشاف ذكي وتحفيز عناصر الصفحة",
            titleEn = "Smart exploration",
            reasonAr = "فحص الصفحة واستكشاف العناصر التفاعلية بالتمرير الطبيعي ورصد أي أزرار أو نماذج تظهر ديناميكياً.",
            reasonEn = "Explore dynamically loaded elements.",
            confidence = 70,
            delayMsBeforeNextCycle = adaptiveDelay(report, base = 3000),
            prioritySteps = listOf("تمرير تدريجي", "رصد أزرار التقدم", "إعادة التحليل التلقائي"),
            executionMode = executionMode,
            funnelStageIndex = report.funnelStageIndex,
            stepsAdvanced = stepsSoFar
        )
    }

    // ── توقيت بشري تكيفي ومحاكاة الانتظار بين المراحل ──────────────────────
    fun adaptiveDelay(report: TaskCategoryPlanner.PageAnalysisReport, base: Long): Long {
        val complexity = report.fieldsCount + report.buttonsCount + report.radioGroupsCount * 2 + report.selectCount * 2
        val extra = min(complexity * 80L, 1800L)
        val jitter = Random.nextLong(250, 850)
        return boostedDelay(base + extra + jitter)
    }

    /**
     * يحسب مدة الانتظار البشري الذكي بين المراحل (بالثواني) مع توزيع طبيعي غير آلي
     * يتكيف مع نمط المحاكاة (careful / natural / fast_human) ونوع المرحلة.
     */
    fun computeStageTransitionWaitSec(
        fromStageCode: String,
        toStageCode: String,
        profile: String = "natural",
        minSec: Int = 3,
        maxSec: Int = 7,
        complexity: Int = 1
    ): Int {
        val safeMin = minSec.coerceIn(1, 20)
        val safeMax = maxSec.coerceIn(safeMin, 30)
        // Triangular / pseudo-Gaussian human distribution (two random draws averaged)
        val r1 = Random.nextDouble(safeMin.toDouble(), safeMax.toDouble() + 0.99)
        val r2 = Random.nextDouble(safeMin.toDouble(), safeMax.toDouble() + 0.99)
        var baseSec = (r1 + r2) / 2.0

        // Stage-specific weight adjustment
        val stageWeight = when (toStageCode) {
            "fetching_geo", "generating_identity" -> 0.65 // خطوات تهيئة النظام: انتظار بشري خفيف وسلس
            "browser" -> 0.85 // قبل فتح الموقع: وقفة استعداد طبيعية
            "locker_read" -> 1.10 // قراءة عروض اللوكر قبل النقر
            "new_tab_settle" -> 1.15 // انتظار استقرار تبويب العرض الجديد وقراءة العنوان
            "funnel_advance" -> 1.0 + (complexity.coerceIn(0, 6) * 0.06) // الانتقال بين مراحل الفانل والأسئلة
            "checking_lead" -> 0.90 // قبل فحص التحويل النهائي
            else -> 1.0
        }
        baseSec *= stageWeight

        // Profile multiplier
        val profileMultiplier = when (profile.lowercase()) {
            "careful" -> 1.35
            "fast_human" -> 0.68
            else -> 1.0
        }
        val finalSec = (baseSec * profileMultiplier).toInt()
        return finalSec.coerceIn(1, 30)
    }

    /**
     * يصف السلوك البشري الحي الجاري تنفيذه في المرحلة الحالية لعرضه في الواجهة والسجل.
     */
    fun describeHumanBehaviorForStage(
        action: NextAction,
        funnelStageIndex: Int,
        questionText: String = "",
        targetOffer: String? = null
    ): String {
        return when (action) {
            NextAction.PASS_BRIDGE ->
                "👁️ مسح بصري لصفحة التوجيه وتحريك المؤشر بمنحنى بشري نحو زر المتابعة"
            NextAction.WAIT_LOCKER, NextAction.CLICK_OFFER ->
                "🧐 قراءة عروض اللوكر ومقارنة العناوين بصرياً قبل النقر على (${(targetOffer ?: "العرض المستهدف").take(24)})"
            NextAction.ANSWER_SURVEY -> {
                val qShort = questionText.take(28).ifBlank { "سؤال الاستبيان" }
                "🧠 قراءة السؤال ('$qShort') والتفكير البشري قبل اختيار الإجابة"
            }
            NextAction.FILL_FORM ->
                "⌨️ كتابة بشرية حرفاً بحرف مع تذبذب السرعة وفواصل انتقال طبيعية بين الحقول"
            NextAction.CHECK_TERMS ->
                "📜 قراءة سطر الشروط والسن القانوني وتمرير المؤشر لتحديد المربع"
            NextAction.SKIP_UPSELL ->
                "👀 فحص العرض الفرعي وتوجيه المؤشر لنقر زر التخطي (No Thanks)"
            NextAction.COMPLETE_CONVERSION ->
                "✅ معاينة صفحة التأكيد النهائية وتثبيت جلسة التحويل البشري"
            NextAction.WAIT_CAPTCHA ->
                "🧩 توقف بشري هادئ بدون نقر عشوائي لحين اجتياز التحقق"
            NextAction.WAIT_LOAD ->
                "⏳ انتظار بشري طبيعي لاكتمال تحميل عناصر الصفحة"
            else ->
                "🧬 تصفح بشري متكامل (المرحلة $funnelStageIndex/6): تمرير تدريجي وحركة مؤشر طبيعية"
        }
    }

    fun computeHumanRealismScore(
        humanEnabled: Boolean,
        typingEnabled: Boolean,
        pointerEnabled: Boolean,
        readingEnabled: Boolean,
        scrollEnabled: Boolean,
        consecutiveNoProgress: Int
    ): Int {
        if (!humanEnabled) return 62
        var score = 76
        if (typingEnabled) score += 6
        if (pointerEnabled) score += 6
        if (readingEnabled) score += 6
        if (scrollEnabled) score += 5
        score -= (consecutiveNoProgress * 3).coerceAtMost(15)
        val jitter = Random.nextInt(-1, 2)
        return (score + jitter).coerceIn(60, 99)
    }

    fun humanCycleInterval(minSec: Int, maxSec: Int, qualityScore: Int): Int {
        // جلسة عالية الجودة → إيقاع أسرع قليلاً، جلسة متعثرة → إبطاء لتقليل البصمة
        val lo = minSec.coerceAtLeast(12)
        val hi = maxSec.coerceAtLeast(lo)
        if (lo >= hi) return lo
        val base = Random.nextInt(lo, hi + 1)
        return if (qualityScore < 40) base + 5 else base
    }

    // ── تحديث الذاكرة (يرصد التقدم الهيكلي في SPA والاستبيانات) ──
    fun updateMemory(
        prev: BrainMemory,
        report: TaskCategoryPlanner.PageAnalysisReport,
        decision: SmartDecision
    ): BrainMemory {
        val sameUrl = prev.lastUrl.isNotBlank() && normalizeUrl(prev.lastUrl) == normalizeUrl(report.url.ifBlank { prev.lastUrl })
        val currentSig = report.domSignature.ifBlank {
            "${report.detectedCategory}:${report.fieldsCount}:${report.radioGroupsCount}:${report.selectCount}:${report.buttonsCount}:${report.primaryCtaText}"
        }
        val currentQ = report.currentQuestionText.trim()

        val domChanged = prev.lastDomSignature.isNotBlank() && currentSig != prev.lastDomSignature
        val questionChanged = prev.lastQuestionText.isNotBlank() && currentQ.isNotBlank() && currentQ != prev.lastQuestionText
        val newlyFilled = report.filledFieldsCount > prev.lastFilledFieldsCount

        val progressed = !sameUrl || domChanged || questionChanged || newlyFilled ||
            decision.action == NextAction.COMPLETE_CONVERSION ||
            decision.action == NextAction.CLICK_OFFER

        val newNoProgress = if (progressed) 0 else prev.consecutiveNoProgress + 1
        val newStepsAdvanced = if (progressed && prev.lastUrl.isNotBlank()) prev.totalStepsAdvanced + 1 else prev.totalStepsAdvanced

        return prev.copy(
            pageHistory = (prev.pageHistory + PageMemory(
                url = report.url,
                category = report.detectedCategory,
                domSignature = currentSig,
                questionText = currentQ
            )).takeLast(30),
            actionTrace = (prev.actionTrace + decision.action.code).takeLast(30),
            consecutiveSamePage = if (sameUrl && !domChanged && !questionChanged) prev.consecutiveSamePage + 1 else 1,
            consecutiveNoProgress = newNoProgress,
            reloadAttemptsForUrl = if (decision.needsReload && sameUrl) prev.reloadAttemptsForUrl + 1 else if (!sameUrl) 0 else prev.reloadAttemptsForUrl,
            lastUrl = report.url.ifBlank { prev.lastUrl },
            lastCategory = report.detectedCategory,
            lastDomSignature = currentSig,
            lastQuestionText = currentQ,
            lastFilledFieldsCount = report.filledFieldsCount,
            totalStepsAdvanced = newStepsAdvanced
        )
    }

    fun initialMemory(): BrainMemory = BrainMemory()

    // ── ترتيب المهام بالتعلم ────────────────────────────────────
    fun prioritizeTasks(tasks: List<TaskEntity>, learning: Map<String, TaskLearningStats>): List<TaskEntity> {
        if (tasks.size <= 1) return tasks
        return tasks.sortedWith(
            compareByDescending<TaskEntity> { learning[it.id]?.successRate ?: 0.5 }
                .thenBy { it.completedRuns }
                .thenBy { it.createdAt }
        )
    }

    fun recordOutcome(
        learning: MutableMap<String, TaskLearningStats>,
        taskId: String,
        converted: Boolean
    ) {
        val cur = learning[taskId] ?: TaskLearningStats(taskId)
        learning[taskId] = cur.copy(runs = cur.runs + 1, conversions = cur.conversions + if (converted) 1 else 0)
    }

    // ── SequenceIntel: ذاكرة التسلسلات الفائزة والميتة (تتعلم الطريق لا النتيجة فقط) ──
    object SequenceIntel {
        private data class Seq(var wins: Int = 0, var losses: Int = 0)

        private val seqs = java.util.concurrent.ConcurrentHashMap<String, Seq>()

        @Volatile
        var lastDeadSeqNote: String = ""
            private set

        /** يقرأ الملاحظة ويمسحها (مرة واحدة لكل تصعيد — بلا إزعاج) */
        fun consumeDeadSeqNote(): String {
            val v = lastDeadSeqNote
            lastDeadSeqNote = ""
            return v
        }

        private fun keyOf(trace: List<String>): String = trace.takeLast(8).joinToString(">")

        /** يُستدعى نهاية كل مهمة: سجل التسلسل الكامل رابحاً أم خاسراً */
        fun record(trace: List<String>, converted: Boolean) {
            if (trace.size < 2) return
            // نوافذ متداخلة بطول 3..6 لالتقاط الأنماط القصيرة القاتلة/الفائزة
            for (len in 3..minOf(6, trace.size)) {
                val key = trace.takeLast(len).joinToString(">")
                val s = seqs.computeIfAbsent(key) { Seq() }
                synchronized(s) {
                    if (converted) s.wins++ else s.losses++
                }
            }
            if (seqs.size > 400) {
                seqs.entries.sortedBy { it.value.wins + it.value.losses }.take(seqs.size - 400)
                    .forEach { seqs.remove(it.key) }
            }
        }

        /**
         * كم تسلسلاً خاسراً مسجلاً يبدأ بالأثر الحالي (دون أي فوز مقابل)؟
         * يُستخدم في decide() لتصعيد الوضع مبكراً بدل تكرار طريق ميت معروف.
         */
        fun deadPrefixPenalty(trace: List<String>): Int {
            lastDeadSeqNote = ""
            if (trace.size < 2) return 0
            val prefix = keyOf(trace)
            var penalty = 0
            var example = ""
            for ((k, s) in seqs) {
                if (k == prefix || k.startsWith(prefix + ">")) {
                    synchronized(s) {
                        if (s.losses >= 2 && s.wins == 0) {
                            penalty += s.losses
                            if (example.isBlank()) example = k
                        }
                    }
                }
            }
            if (penalty >= 2) {
                lastDeadSeqNote = "تسلسل ميت معروف ($example) تكرر $penalty مرات بلا فوز — تصعيد مبكر"
            }
            return penalty
        }

        fun snapshot(): Map<String, Pair<Int, Int>> =
            seqs.entries.associate { it.key to Pair(it.value.wins, it.value.losses) }

        fun restore(snapshot: Map<String, Pair<Int, Int>>) {
            for ((k, v) in snapshot) {
                if (k.isNotBlank() && k.contains(">")) seqs[k] = Seq(v.first.coerceAtLeast(0), v.second.coerceAtLeast(0))
            }
        }
    }

    private fun escalateMode(mode: String): String = when (mode) {
        "normal" -> "retry_react_events"
        "retry_react_events" -> "alt_option_2"
        "alt_option_2" -> "alt_option_3"
        "alt_option_3" -> "force_submit"
        else -> mode
    }

    fun sessionQuality(memory: BrainMemory, conversions: Int, runs: Int): Int {
        var score = 70
        score -= memory.consecutiveNoProgress * 8
        if (runs > 0) score += (conversions * 100 / (runs + 1)) / 4
        if (memory.reloadAttemptsForUrl > 2) score -= 15
        return score.coerceIn(0, 100)
    }

    // ── كشف الحالات ─────────────────────────────────────────────
    private data class Extras(
        val matchedBlockKeyword: String = "",
        val matchedCaptchaKeyword: String = "",
        val matchedLoadingKeyword: String = "",
        val hasCaptchaElement: Boolean = false,
        val hasBlockElement: Boolean = false,
        val inputsCount: Int = 0
    )

    private fun parseExtras(rawJson: String): Extras {
        if (rawJson.isBlank()) return Extras()
        return try {
            val o = JSONObject(rawJson)
            Extras(
                matchedBlockKeyword = o.optString("blockKeyword", ""),
                matchedCaptchaKeyword = o.optString("captchaKeyword", ""),
                matchedLoadingKeyword = o.optString("loadingKeyword", ""),
                hasCaptchaElement = o.optBoolean("hasCaptcha", false),
                hasBlockElement = o.optBoolean("isBlocked", false),
                inputsCount = o.optInt("fieldsCount", 0)
            )
        } catch (_: Exception) { Extras() }
    }

    private fun detectBlocked(report: TaskCategoryPlanner.PageAnalysisReport, e: Extras): Boolean {
        if (e.hasBlockElement || e.matchedBlockKeyword.isNotBlank()) return true
        val hay = (report.summary + " " + report.title + " " + report.detectedCategory).lowercase()
        return BLOCK_KEYWORDS.any { hay.contains(it) }
    }

    private fun detectCaptcha(report: TaskCategoryPlanner.PageAnalysisReport, e: Extras): Boolean {
        if (e.hasCaptchaElement || e.matchedCaptchaKeyword.isNotBlank()) return true
        val hay = (report.summary + " " + report.title).lowercase()
        return CAPTCHA_KEYWORDS.any { hay.contains(it) }
    }

    private fun detectLoading(report: TaskCategoryPlanner.PageAnalysisReport, e: Extras): Boolean {
        if (e.matchedLoadingKeyword.isNotBlank()) return true
        val hay = (report.summary + " " + report.title).lowercase()
        return LOADING_KEYWORDS.any { hay.contains(it) }
    }

    private fun pickEscapeStrategy(
        report: TaskCategoryPlanner.PageAnalysisReport,
        memory: BrainMemory,
        stuckLevel: Int = 2
    ): String {
        val trace = memory.actionTrace.takeLast(5)
        return when {
            stuckLevel == 2 && (report.radioGroupsCount > 0 || trace.any { it == NextAction.ANSWER_SURVEY.code }) ->
                "اختيار الخيار البديل #2 وتفعيل أحداث React/Pointer"
            stuckLevel >= 3 && (report.radioGroupsCount > 0 || trace.any { it == NextAction.ANSWER_SURVEY.code }) ->
                "اختيار الخيار البديل #3 وفحص بطاقات الأسئلة التفاعلية"
            trace.count { it == NextAction.FILL_FORM.code } >= 2 ->
                "فك قفل زر الإرسال وتفعيل Submit مباشرة مع تصفير React ValueTracker"
            report.buttonsCount > 4 ->
                "فحص وتفعيل أزرار الانتقال البديلة وإغلاق النوافذ الحاجبة"
            memory.reloadAttemptsForUrl >= 1 ->
                "إرسال النموذج برمجياً أو فتح رابط المرحلة التالية"
            else -> "تبديل مسار النقر وإطلاق سلسلة أحداث بشرية كاملة"
        }
    }

    private fun normalizeUrl(u: String): String =
        u.lowercase().substringBefore("?").substringBefore("#").trimEnd('/')

    /** مدة ذكية للمهمة حسب تعقيد الفانل بدل الرقم الثابت */
    fun smartDurationForTask(task: TaskEntity, categories: List<String>): Int {
        val base = task.browserDuration.coerceAtLeast(20)
        val steps = TaskCategoryPlanner.orderCategories(categories).size
        val bonus = (steps * 6).coerceAtMost(30)
        val hasLocker = categories.any { it.contains("locker", true) || it.contains("قفل") || it.contains("لوكر") }
        val lockerBonus = if (hasLocker) 15 else 0
        return (base + bonus + lockerBonus).coerceAtMost(120)
    }

    fun confirmKeywordsSmart(configured: String): List<String> {
        val base = configured.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        val merged = (base + CONFIRM_EXTRA).distinct()
        return merged.take(14)
    }

    // ══ خبرة متقدمة: استغلال مقابل استكشاف (UCB1) ═══════════════
    // لا تختار دائماً الأعلى نجاحاً فتعلق في optimum محلي، بل تجرب أحياناً.
    fun prioritizeTasksUCB(tasks: List<TaskEntity>, learning: Map<String, TaskLearningStats>): List<TaskEntity> {
        if (tasks.size <= 1) return tasks
        val totalRuns = learning.values.sumOf { it.runs } + 1
        return tasks.sortedWith(
            compareByDescending<TaskEntity> { t ->
                // ذكاء متقاطع: معدل تحويل فئات المهمة يرفع/يخفض أولويتها (±0.15)
                val catBoost = try {
                    val cats = TaskCategoryPlanner.parseCategories(t.categories)
                    if (cats.isEmpty()) 0.0
                    else {
                        val avgCat = cats.map { TaskCategoryPlanner.categoryConversionRate(it) }.average()
                        (avgCat - 0.5) * 0.3
                    }
                } catch (_: Exception) { 0.0 }
                val s = learning[t.id]
                if (s == null || s.runs == 0) Double.MAX_VALUE - t.createdAt / 1e15 + catBoost // جرّب الجديد أولاً مرة واحدة
                else {
                    val avg = s.successRate
                    val bonus = kotlin.math.sqrt(2.0 * kotlin.math.ln(totalRuns.toDouble()) / s.runs.toDouble())
                    avg + 0.7 * bonus + catBoost // معامل الاستكشاف 0.7
                }
            }.thenBy { it.completedRuns }
        )
    }

    data class OfferChoice(val text: String, val clicks: Int, val impressions: Int = 0) {
        val ctr: Double get() = if (impressions == 0) 0.5 else clicks.toDouble() / (impressions + 1)
    }

    /** Epsilon-greedy للنصوص: 85% استغلال الأفضل CTR، 15% استكشاف نص جديد */
    fun selectOfferText(
        enabledTexts: List<String>,
        clickCounts: Map<String, Int>,
        epsilon: Double = 0.15,
        impressions: Map<String, Int> = emptyMap()
    ): Int {
        if (enabledTexts.isEmpty()) return -1
        if (enabledTexts.size == 1) return 0
        fun ctrOf(t: String): Double {
            val imp = impressions[t] ?: 0
            // بدون انطباعات موثقة: عدل لابلاس + إشارة النقرات الخام
            if (imp <= 0) return ((clickCounts[t] ?: 0) + 0.5) / 2.0
            return (clickCounts[t] ?: 0).toDouble() / (imp + 1).toDouble()
        }
        if (Random.nextDouble() < epsilon) {
            // استكشاف: الأقل ظهوراً (تغطية عادلة بدل الأقل نقرات)
            val least = enabledTexts.indices.minByOrNull { impressions[enabledTexts[it]] ?: 0 } ?: 0
            return least
        }
        // استغلال: الأعلى CTR (مع كسر التعادل عشوائياً)
        val maxCtr = enabledTexts.maxOf { ctrOf(it) }
        val best = enabledTexts.indices.filter { ctrOf(enabledTexts[it]) == maxCtr }
        return best.random()
    }

    /** تقييم البروكسي: نجاح × سرعة × حداثة، مع cooldown للفاشل */
    fun scoreProxyForTask(
        successRate: Double, pingMs: Long, failCount: Int, lastFailAgeMin: Long, qualityScore: Int
    ): Double {
        var score = successRate * 60.0
        score += when {
            pingMs <= 0 -> 0.0
            pingMs <= 400 -> 20.0
            pingMs <= 1200 -> 12.0
            pingMs <= 3000 -> 5.0
            else -> 0.0
        }
        score += (qualityScore / 100.0) * 10.0
        if (failCount > 0) {
            // عقوبة تتلاشى مع الزمن: فشل حديث = عقوبة كبيرة
            val decay = kotlin.math.exp(-lastFailAgeMin / 30.0)
            score -= failCount * 8.0 * decay
        }
        // Sticky bonus: لا تدور mid-funnel بدون سبب — يُطبق في AppViewModel
        return score.coerceIn(0.0, 100.0)
    }

    /** بذرة ثابتة لكل تكرار: نفس الجلسة = نفس البصمة، جلسة جديدة = بصمة جديدة */
    fun sessionSeed(taskId: String, runNumber: Int, proxyHost: String): Long {
        var h = 1125899906842597L
        val s = "$taskId|$runNumber|$proxyHost"
        for (c in s) h = 31 * h + c.code
        return h
    }

    /** فحص اتساق الهوية قبل الضخ: العمر مقابل DOB، الجنس مقابل الاسم، المدينة مقابل Zip */
    fun personaConsistencyReport(
        firstName: String, gender: String, birthDate: String, city: String, postalCode: String, phone: String
    ): List<String> {
        val issues = mutableListOf<String>()
        try {
            val femaleNames = setOf("mary","patricia","jennifer","linda","elizabeth","barbara","susan","jessica","sarah","emily","emma","olivia")
            val isFemaleName = femaleNames.contains(firstName.lowercase())
            val isFemaleGender = gender.equals("female", true)
            if (isFemaleName != isFemaleGender) issues.add("gender-name mismatch")
            val year = birthDate.substringBefore("-").toIntOrNull() ?: 1995
            val age = 2026 - year
            if (age < 18 || age > 70) issues.add("age out of range ($age)")
            val digits = phone.filter { it.isDigit() }
            if (digits.length < 10) issues.add("phone too short")
        } catch (_: Exception) {}
        return issues
    }

    /** حقائق الشخصية للاستبيان الممتد: إجابات متسقة عبر الصفحات */
    fun personaFacts(firstName: String, gender: String, birthDate: String, city: String): Map<String, String> {
        val year = birthDate.substringBefore("-").toIntOrNull() ?: 1995
        val age = (2026 - year).coerceIn(18, 70)
        val ageBand = when {
            age <= 24 -> "18-24"
            age <= 34 -> "25-34"
            age <= 44 -> "35-44"
            else -> "45+"
        }
        return mapOf(
            "age" to age.toString(),
            "ageBand" to ageBand,
            "adult" to "yes",
            "resident" to "yes",
            "gender" to gender.lowercase(),
            "firstName" to firstName
        )
    }

    /** منفعة المهمة: تحويل متوقع في الساعة مقابل حرق البروكسي */
    fun taskUtilityPerHour(successRate: Double, avgDurationSec: Int, proxyCost: Double = 1.0): Double {
        if (avgDurationSec <= 0) return 0.0
        return (successRate * 3600.0 / avgDurationSec) / proxyCost
    }

    /**
     * Multi-Stage Escalation Recovery JS Script when a page/step fails to advance.
     */
    fun buildStuckRecoveryScript(stuckLevel: Int, category: String): String {
        return """
(function() {
  try {
    var level = $stuckLevel;
    function fireHumanClick(el) {
      if (!el) return false;
      try {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        if (el.disabled) el.disabled = false;
        el.removeAttribute('disabled');
        var rect = el.getBoundingClientRect();
        var cx = rect.left + Math.max(4, rect.width / 2);
        var cy = rect.top + Math.max(4, rect.height / 2);
        var opts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0, buttons: 1 };
        el.dispatchEvent(new PointerEvent('pointerdown', opts));
        el.dispatchEvent(new MouseEvent('mousedown', opts));
        if (typeof el.focus === 'function') el.focus();
        el.dispatchEvent(new PointerEvent('pointerup', opts));
        el.dispatchEvent(new MouseEvent('mouseup', opts));
        el.dispatchEvent(new MouseEvent('click', opts));
        if (typeof el.click === 'function') el.click();
        return true;
      } catch(e) {
        try { el.click(); return true; } catch(e2) { return false; }
      }
    }

    // 1. Dismiss any blocking modal/cookie/overlay close buttons
    var closeBtns = document.querySelectorAll('[aria-label*="close" i], [class*="close" i], [id*="close" i], .modal-close, .popup-close, button[class*="dismiss" i], #onetrust-accept-btn-handler');
    for (var i = 0; i < closeBtns.length; i++) {
      var cb = closeBtns[i];
      if (cb && cb.offsetParent !== null) {
        var txt = (cb.innerText || cb.textContent || '').trim().toLowerCase();
        if (txt === 'x' || txt === '×' || txt === 'close' || txt === 'accept' || txt === 'agree' || txt === 'got it' || txt === 'إغلاق' || txt === 'موافق') {
          fireHumanClick(cb);
        }
      }
    }

    // 2. Check any unchecked consent/terms checkboxes
    var checkboxes = document.querySelectorAll('input[type="checkbox"]:not(:checked)');
    for (var c = 0; c < checkboxes.length; c++) {
      var box = checkboxes[c];
      if (box.offsetParent !== null) {
        fireHumanClick(box);
        box.checked = true;
        box.dispatchEvent(new Event('change', { bubbles: true }));
      }
    }

    // 3. Level 2+: Try alternative radio / survey option card choices
    if (level >= 2) {
      var radios = Array.from(document.querySelectorAll('input[type="radio"]')).filter(function(r) { return r.offsetParent !== null || r.parentElement; });
      if (radios.length > 1) {
        var pickIdx = (level - 1) % radios.length;
        var chosenRadio = radios[pickIdx];
        fireHumanClick(chosenRadio);
        chosenRadio.checked = true;
        chosenRadio.dispatchEvent(new Event('change', { bubbles: true }));
        if (chosenRadio.id) {
          var lbl = document.querySelector('label[for="' + chosenRadio.id + '"]');
          if (lbl) fireHumanClick(lbl);
        }
      }

      var optionCards = Array.from(document.querySelectorAll('[role="radio"], [role="option"], .quiz-option, .survey-option, .answer-option, .option-btn, [class*="answer-btn" i], [class*="choice" i]')).filter(function(el) {
        return el.offsetParent !== null && (el.innerText || '').trim().length > 0 && (el.innerText || '').trim().length < 90;
      });
      if (optionCards.length > 1) {
        var cardIdx = (level - 1) % optionCards.length;
        fireHumanClick(optionCards[cardIdx]);
      }
    }

    // 4. Find and trigger the primary CTA / Next / Submit button
    var allBtns = Array.from(document.querySelectorAll('button, input[type="submit"], input[type="button"], a[role="button"], a.btn, a[class*="btn" i], a[class*="button" i], div[role="button"]')).filter(function(el) {
      var r = el.getBoundingClientRect();
      return r.width > 30 && r.height > 18 && window.getComputedStyle(el).visibility !== 'hidden' && window.getComputedStyle(el).display !== 'none';
    });

    var priorityCtas = allBtns.filter(function(el) {
      var t = ((el.innerText || el.value || el.getAttribute('aria-label') || '') + '').trim().toLowerCase();
      return /continue|next|submit|claim|start|verify|confirm|proceed|get|enter|unlock|yes|متابعة|استمرار|إرسال|تأكيد|ابدأ|تخطي|skip|no thanks/i.test(t);
    });

    if (priorityCtas.length > 0) {
      var targetBtn = priorityCtas[(level - 1) % priorityCtas.length];
      fireHumanClick(targetBtn);
      return 'RECOVERY_CTA:' + (targetBtn.innerText || targetBtn.value || 'btn').trim().substring(0, 24);
    }

    // 5. Level 3+: Submit form or click fallback button
    if (level >= 3) {
      var forms = document.querySelectorAll('form');
      for (var f = 0; f < forms.length; f++) {
        var subBtn = forms[f].querySelector('[type="submit"], button:not([type="button"])');
        if (subBtn) {
          fireHumanClick(subBtn);
          return 'RECOVERY_FORM_BTN';
        }
      }
      if (allBtns.length > 0) {
        fireHumanClick(allBtns[(level - 1) % allBtns.length]);
        return 'RECOVERY_FALLBACK_BTN';
      }
    }

    window.scrollBy({ top: 320, behavior: 'smooth' });
    return 'RECOVERY_SCROLLED_L' + level;
  } catch (e) {
    return 'RECOVERY_ERR';
  }
})();
        """.trimIndent()
    }

    /**
     * Instant helper script for advancing through tracking bridges or interactive landers.
     */
    fun buildBridgeAdvanceScript(): String {
        return """
(function() {
  try {
    var btns = Array.from(document.querySelectorAll('button, input[type="submit"], input[type="button"], a[role="button"], a.btn, a[class*="btn" i], a[class*="button" i]')).filter(function(el) {
      var r = el.getBoundingClientRect();
      return r.width > 40 && r.height > 20 && el.offsetParent !== null;
    });
    for (var i = 0; i < btns.length; i++) {
      var b = btns[i];
      var txt = ((b.innerText || b.value || '') + '').trim().toLowerCase();
      if (/continue|next|start|claim|proceed|enter|get|verify|confirm|yes|متابعة|استمرار|ابدأ|تأكيد/i.test(txt)) {
        b.scrollIntoView({ behavior: 'smooth', block: 'center' });
        b.click();
        return 'BRIDGE_CLICKED:' + txt.substring(0, 20);
      }
    }
    return 'NO_BRIDGE_BTN';
  } catch(e) { return 'ERR'; }
})();
        """.trimIndent()
    }
}
