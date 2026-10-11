package com.example.service

/**
 * AgentServer — خادم اكواد الوكيل: يشغّل المهمة (يفحص ← يكرر ← يصلح DOM)
 * ويُحاسَب في النهاية على نجاح التحويلة وتحويل الصيد فقط.
 *
 * الدورة: OBSERVE (تحليل الصفحة) → REASON (قرار العقل) → ACT (أمر متصفح)
 * → VERIFY (هل تقدمنا؟) → REPAIR (إصلاح DOM عند التعثر) → SCORE (المحاسبة).
 */
object AgentServer {

    enum class AgentPhase(val code: String) {
        IDLE("idle"),
        OBSERVING("observing"),
        REASONING("reasoning"),
        ACTING("acting"),
        VERIFYING("verifying"),
        REPAIRING("repairing"),
        SCORED("scored"),
        STOPPED("stopped")
    }

    data class AgentScore(
        val total: Int = 0, // 0..100 — مربوط حصرياً بالتحويل
        val converted: Boolean = false,
        val leadConfirmed: Boolean = false,
        val steps: Int = 0,
        val repairs: Int = 0,
        val grade: String = "F",
        val detailAr: String = ""
    )

    data class AgentState(
        val phase: AgentPhase = AgentPhase.IDLE,
        val missionName: String = "",
        val taskId: String? = null,
        val steps: Int = 0,
        val repairs: Int = 0,
        val trace: List<String> = emptyList(),
        val lastScore: AgentScore? = null
    )

    /** سجل محاسبة: نتيجة + المهمة + الوقت (يُحفظ آخر 20) */
    data class AgentRecord(
        val score: AgentScore,
        val taskName: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    /** بعد كم تحليلاً بلا نقرة على الهدف نطلق إصلاح DOM؟ */
    const val HEAL_AFTER_SAME_TARGET = 3

    /** حد خطوات المهمة الواحدة قبل الإيقاف الإجباري */
    const val MAX_MISSION_STEPS = 40

    /**
     * المحاسبة النهائية — التحويل هو كل شيء:
     * - بلا تحويل: سقف 25 (يُخصم منه التعثر) — الفشل فشل مهما كانت المحاولات.
     * - تحويل بلا تأكيد صيد: 70 + كفاءة حتى 10.
     * - تحويل + صيد مؤكد: 90 + كفاءة حتى 10.
     */
    fun scoreMission(
        steps: Int,
        repairs: Int,
        converted: Boolean,
        leadConfirmed: Boolean
    ): AgentScore {
        if (!converted) {
            val total = (25 - (steps / 4).coerceAtMost(15) - (repairs * 2).coerceAtMost(10)).coerceAtLeast(0)
            return AgentScore(
                total = total,
                converted = false,
                leadConfirmed = false,
                steps = steps,
                repairs = repairs,
                grade = if (total >= 15) "D" else "F",
                detailAr = "فشل التحويلة — المحاسبة صفرية التسامح: $steps خطوات و$repairs إصلاحاً دون تحويل."
            )
        }
        val efficiency = (10 - (steps / 6).coerceAtMost(6) - (repairs / 2).coerceAtMost(4)).coerceAtLeast(0)
        val total = if (leadConfirmed) (90 + efficiency).coerceAtMost(100) else (70 + efficiency).coerceAtMost(89)
        return AgentScore(
            total = total,
            converted = true,
            leadConfirmed = leadConfirmed,
            steps = steps,
            repairs = repairs,
            grade = when {
                total >= 95 -> "A+"
                total >= 90 -> "A"
                total >= 80 -> "B"
                else -> "C"
            },
            detailAr = if (leadConfirmed) {
                "تحويلة مؤكدة بالصيد 🏆 ($steps خطوات، $repairs إصلاحات) — وكيل ناجح."
            } else {
                "تحويلة بلا تأكيد صيد ($steps خطوات) — ناجح جزئياً."
            }
        )
    }

    fun phaseLabelAr(phase: AgentPhase): String = when (phase) {
        AgentPhase.IDLE -> "خامل"
        AgentPhase.OBSERVING -> "👁️ يفحص الصفحة"
        AgentPhase.REASONING -> "🧠 يقرر الخطوة"
        AgentPhase.ACTING -> "🖱️ ينفذ الإجراء"
        AgentPhase.VERIFYING -> "🔍 يتحقق من التقدم"
        AgentPhase.REPAIRING -> "🔧 يصلح DOM"
        AgentPhase.SCORED -> "🏁 تمت المحاسبة"
        AgentPhase.STOPPED -> "⏸️ متوقف"
    }
}
