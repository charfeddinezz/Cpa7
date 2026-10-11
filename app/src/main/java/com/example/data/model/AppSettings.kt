package com.example.data.model

data class AppSettings(
    val waitBetweenTasks: Int = 5,
    val cpaUserId: String = "",
    val cpaApiKey: String = "",
    val captchaProvider: String = "none", // none, 2captcha, anticaptcha, capsolver, deathbycaptcha
    val captchaApiKey: String = "",
    val proxyType: String = "none", // none, http, https, socks4, socks5
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyUser: String = "",
    val proxyPass: String = "",
    val proxyAutoRotate: Boolean = false,
    val proxyListUrl: String = "",
    val webrtcMode: String = "spoof", // spoof, disabled, real
    val disableWebRtc: Boolean = false, // Master toggle switch: Désactiver WebRTC (when true, webrtcMode is forced to "disabled")
    val webrtcCustomIp: String = "",
    val forceProxyDns: Boolean = true, // Force DNS resolution through proxy & prevent DNS leaks
    val proxyEnabled: Boolean = false, // Master switch: when false, proxies are disabled & normal direct local connection is used (DEFAULT OFF for direct success)
    val cpaLockerAutoTrigger: Boolean = true, // Auto-trigger call_locker() and polyfill document.write on locker detection
    val cpaLockerDefaultId: String = "1741238", // Default Locker ID if custom or missing (live-detected on gdfqo.blogspot.com)
    val cpaLockerAutoInjectIfMissing: Boolean = false, // Automatically inject locker script if not found on page
    val offerClickOpenInNewTab: Boolean = true, // When offer clicked in locker, automatically open in new tab and switch
    val offerClickStayDurationSec: Int = 15, // Stay duration on the new offer tab simulating real interaction
    val offerClickAutoSimulateHuman: Boolean = true, // Auto scroll and simulate human activity on the newly opened offer tab
    val offerSelectionStrategy: String = "priority", // "priority", "first", "random"
    val autoSaveExtractedLockerOffers: Boolean = true, // Automatically save newly discovered locker offer texts into Offer Click without duplicates
    val cycleIntervalMinSec: Int = 15, // الحد الأدنى لفاصل دورة الحلقة التكرارية (بين 15 و 25 ثانية)
    val cycleIntervalMaxSec: Int = 25, // الحد الأقصى لفاصل دورة الحلقة التكرارية
    // ── Complete Human Simulation Intelligence & Inter-Stage Waiting Settings ──
    val humanSimulationEnabled: Boolean = true, // تفعيل ذكاء المحاكاة البشرية الشامل في كل شيء
    val humanSimulationProfile: String = "natural", // "careful" (حذر جداً), "natural" (بشري طبيعي), "fast_human" (بشري سريع)
    val stageTransitionWaitMinSec: Int = 3, // الحد الأدنى للانتظار الذكي بين المراحل (ثوانٍ)
    val stageTransitionWaitMaxSec: Int = 7, // الحد الأقصى للانتظار الذكي بين المراحل (ثوانٍ)
    val humanTypingSimulation: Boolean = true, // الكتابة البشرية حرفاً بحرف مع تذبذب السرعة وتصحيح الأخطاء
    val humanPointerAndTouchPaths: Boolean = true, // مسارات الماوس واللمس بمنحنيات Bezier وقانون Fitts
    val humanReadingPauseBeforeAction: Boolean = true, // وقفات القراءة والتفكير البشري قبل النقر والإرسال
    val humanScrollAndGazeSimulation: Boolean = true, // التمرير البشري التدريجي مع التوقف للقراءة
    // ── Tracker Shield: تعامل تلقائي مع متتبعات إعادة الجلسات (hotjar وأخواتها) ──
    val trackerShieldEnabled: Boolean = true, // تفعيل درع المتتبعات
    val trackerMode: String = "deceive", // "deceive" (حظر غير مرئي + stubs مزيفة), "block" (حظر فقط), "allow" (سماح)
    // ── Kill-switches: مفاتيح أمان لكل وحدة ذكاء تجريبية ──
    val blankWallEviction: Boolean = true, // كشف الجدار الأبيض وإخلاؤه
    val evolutionaryMutations: Boolean = true, // الطفرات التطورية عند الفشل
    val surveillanceBlocking: Boolean = true, // حظر متتبعات إعادة الجلسات
    val xhrCorrection: Boolean = true // تصحيح ترويسات XHR/fetch
)
