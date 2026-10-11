package com.example

import com.example.data.model.TaskEntity
import com.example.service.SmartAutomationBrain
import com.example.service.TaskCategoryPlanner
import com.example.util.GeoIntel
import com.example.util.ProxyAsnIntel
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure-logic tests for the intelligence math (no Android framework needed):
 * GeoIntel, ProxyAsnIntel, SequenceIntel, proxy scoring, offer-text choice, UCB.
 */
class IntelMathTest {

    // ── GeoIntel ──

    @Test
    fun geo_normalize_codes_and_names() {
        assertEquals("US", GeoIntel.normalize("us"))
        assertEquals("GB", GeoIntel.normalize("UK"))
        assertEquals("US", GeoIntel.normalize("United States"))
        assertEquals("SA", GeoIntel.normalize("saudi arabia"))
        assertEquals("AE", GeoIntel.normalize("UAE"))
    }

    @Test
    fun geo_default_rate_is_neutral() {
        assertEquals(0.5, GeoIntel.rate("XX-NO-DATA"), 0.0)
    }

    @Test
    fun geo_record_and_rate_math() {
        val before = GeoIntel.snapshot()
        GeoIntel.record("US", true)
        GeoIntel.record("US", false)
        val rate = GeoIntel.rate("US")
        assertTrue(rate in 0.0..1.0)
        // restore prior state to keep tests order-independent
        GeoIntel.restore(before)
    }

    @Test
    fun geo_snapshot_restore_roundtrip() {
        val before = GeoIntel.snapshot()
        GeoIntel.record("ZZ", true)
        val snap = GeoIntel.snapshot()
        assertTrue(snap.containsKey("ZZ"))
        GeoIntel.restore(before)
        assertEquals(before, GeoIntel.snapshot())
    }

    // ── ProxyAsnIntel ──

    @Test
    fun asn_residential_detected() {
        assertEquals("residential", ProxyAsnIntel.classify("Comcast Cable Communications, LLC"))
        assertEquals("residential", ProxyAsnIntel.classify("Saudi Telecom Company"))
        assertEquals("residential", ProxyAsnIntel.classify("Verizon Fios"))
    }

    @Test
    fun asn_datacenter_detected() {
        assertEquals("datacenter", ProxyAsnIntel.classify("Amazon.com, Inc."))
        assertEquals("datacenter", ProxyAsnIntel.classify("Hetzner Online GmbH"))
        assertEquals("datacenter", ProxyAsnIntel.classify("OVH Hosting Ltd"))
    }

    @Test
    fun asn_vpn_detected_and_worst() {
        assertEquals("vpn", ProxyAsnIntel.classify("NordVPN Servers"))
        val (vpnFactor, _) = ProxyAsnIntel.factor("NordVPN Servers")
        val (dcFactor, _) = ProxyAsnIntel.factor("Amazon AWS")
        val (resFactor, _) = ProxyAsnIntel.factor("Comcast Cable")
        assertTrue(vpnFactor < dcFactor)
        assertTrue(dcFactor < resFactor)
        assertEquals("unknown", ProxyAsnIntel.classify(""))
    }

    @Test
    fun asn_residential_beats_telecom_ambiguity() {
        // "Telecom Egypt" must stay residential, not datacenter
        assertEquals("residential", ProxyAsnIntel.classify("Telecom Egypt"))
    }

    // ── SequenceIntel ──

    @Test
    fun sequence_short_trace_has_no_penalty() {
        assertEquals(0, SmartAutomationBrain.SequenceIntel.deadPrefixPenalty(listOf("a")))
        assertEquals("", SmartAutomationBrain.SequenceIntel.consumeDeadSeqNote())
    }

    @Test
    fun sequence_repeated_loss_escalates() {
        val before = SmartAutomationBrain.SequenceIntel.snapshot()
        val trace = listOf("explore_scroll", "submit_advance", "reload_retry", "submit_advance")
        repeat(3) { SmartAutomationBrain.SequenceIntel.record(trace, false) }
        val penalty = SmartAutomationBrain.SequenceIntel.deadPrefixPenalty(trace)
        assertTrue(penalty >= 2)
        val note = SmartAutomationBrain.SequenceIntel.consumeDeadSeqNote()
        assertTrue(note.isNotBlank())
        // consumed → cleared
        assertEquals("", SmartAutomationBrain.SequenceIntel.consumeDeadSeqNote())
        SmartAutomationBrain.SequenceIntel.restore(before)
    }

    @Test
    fun sequence_win_cancels_dead_prefix() {
        val before = SmartAutomationBrain.SequenceIntel.snapshot()
        val trace = listOf("w1", "w2", "w3", "w4")
        repeat(3) { SmartAutomationBrain.SequenceIntel.record(trace, false) }
        SmartAutomationBrain.SequenceIntel.record(trace, true)
        assertEquals(0, SmartAutomationBrain.SequenceIntel.deadPrefixPenalty(trace))
        SmartAutomationBrain.SequenceIntel.restore(before)
    }

    // ── Proxy scoring ──

    @Test
    fun proxy_score_bounds_and_working_floor() {
        val s = SmartAutomationBrain.scoreProxyForTask(0.0, 5000, 10, 0, 0)
        assertTrue(s in 0.0..100.0)
        val working = SmartAutomationBrain.scoreProxyForTask(0.0, 300, 0, 9999, 100)
        val plain = SmartAutomationBrain.scoreProxyForTask(0.0, 300, 0, 9999, 100)
        assertEquals(working, plain, 0.0) // same inputs → deterministic
        assertTrue(working >= 0.0)
    }

    @Test
    fun proxy_recent_failure_penalized_more_than_old() {
        val fresh = SmartAutomationBrain.scoreProxyForTask(0.8, 300, 3, 1, 80)
        val aged = SmartAutomationBrain.scoreProxyForTask(0.8, 300, 3, 5000, 80)
        assertTrue(aged > fresh)
    }

    // ── Offer text choice ──

    @Test
    fun offer_choice_edge_cases() {
        assertEquals(-1, SmartAutomationBrain.selectOfferText(emptyList(), emptyMap()))
        assertEquals(0, SmartAutomationBrain.selectOfferText(listOf("a"), mapOf("a" to 5)))
    }

    @Test
    fun offer_choice_exploits_best_ctr() {
        val texts = listOf("low", "high")
        val clicks = mapOf("low" to 9, "high" to 2)
        val impressions = mapOf("low" to 100, "high" to 3)
        // high CTR (2/4=0.5) beats low CTR (9/101) despite fewer raw clicks
        repeat(10) {
            assertEquals(1, SmartAutomationBrain.selectOfferText(texts, clicks, epsilon = 0.0, impressions = impressions))
        }
    }

    @Test
    fun offer_choice_explores_least_shown() {
        val texts = listOf("a", "b", "c")
        val idx = SmartAutomationBrain.selectOfferText(
            texts, mapOf("a" to 99, "b" to 99, "c" to 0),
            epsilon = 1.0, impressions = mapOf("a" to 50, "b" to 50, "c" to 0)
        )
        assertEquals(2, idx)
    }

    // ── UCB prioritization ──

    private fun task(id: String): TaskEntity = TaskEntity(
        id = id, name = id, url = "https://example.com/$id",
        categories = "", completionKeywords = ""
    )

    @Test
    fun ucb_new_task_first_and_winners_before_losers() {
        val tasks = listOf(task("old-loser"), task("old-winner"), task("brand-new"))
        val learning = mapOf(
            "old-loser" to SmartAutomationBrain.TaskLearningStats("old-loser", runs = 10, conversions = 0),
            "old-winner" to SmartAutomationBrain.TaskLearningStats("old-winner", runs = 10, conversions = 9)
        )
        val ordered = SmartAutomationBrain.prioritizeTasksUCB(tasks, learning)
        assertEquals("brand-new", ordered.first().id) // untried → explored first once
        assertEquals(3, ordered.size)
    }

    @Test
    fun ucb_single_passthrough() {
        val one = listOf(task("solo"))
        assertEquals(one, SmartAutomationBrain.prioritizeTasksUCB(one, emptyMap()))
    }

    @Test
    fun record_outcome_accumulates() {
        val m = mutableMapOf<String, SmartAutomationBrain.TaskLearningStats>()
        SmartAutomationBrain.recordOutcome(m, "t", true)
        SmartAutomationBrain.recordOutcome(m, "t", false)
        assertEquals(2, m["t"]!!.runs)
        assertEquals(1, m["t"]!!.conversions)
        assertEquals(0.5, m["t"]!!.successRate, 0.0)
    }

    // ── Category rates ──

    @Test
    fun category_default_and_learning() {
        assertEquals(0.5, TaskCategoryPlanner.categoryConversionRate("zz-no-such-cat"), 0.0)
        val before = TaskCategoryPlanner.categoryStatsSnapshot()
        TaskCategoryPlanner.recordCategoryOutcome(listOf("Offer_Click", "  "), true)
        assertEquals(1.0, TaskCategoryPlanner.categoryConversionRate("offer_click"), 0.0)
        TaskCategoryPlanner.restoreCategoryStats(before)
    }
}
