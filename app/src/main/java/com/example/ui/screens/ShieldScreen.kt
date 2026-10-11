package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.data.model.AppSettings
import com.example.data.model.AutomationState
import com.example.service.AutomationScriptBuilder
import com.example.ui.AppViewModel
import com.example.ui.theme.CpaBg
import com.example.ui.theme.CpaBorder
import com.example.ui.theme.CpaCard
import com.example.ui.theme.CpaError
import com.example.ui.theme.CpaPrimary
import com.example.ui.theme.CpaPrimaryBorder
import com.example.ui.theme.CpaPrimaryDim
import com.example.ui.theme.CpaSuccess
import com.example.ui.theme.CpaText
import com.example.ui.theme.CpaTextMuted
import com.example.ui.theme.CpaWarning
import kotlinx.coroutines.delay

@Composable
fun ShieldScreen(
    settings: AppSettings,
    automationState: AutomationState,
    onToggleTrackerShield: (Boolean) -> Unit,
    onSetTrackerMode: (String) -> Unit,
    onRunSelfTest: () -> Unit,
    onRotateProxy: () -> Unit,
    onTriggerEscape: () -> Unit,
    onNudgeHumanization: (Int) -> Unit,
    onSetKillSwitch: (String, Boolean) -> Unit,
    statsProvider: () -> AppViewModel.ShieldStats,
    modifier: Modifier = Modifier
) {
    var tick by remember { mutableIntStateOf(0) }
    var stats by remember { mutableStateOf(statsProvider()) }
    LaunchedEffect(tick) {
        delay(100)
        stats = try { statsProvider() } catch (_: Exception) { stats }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            tick++
        }
    }

    val riskColor = when (automationState.intelRiskLevel) {
        "critical" -> CpaError
        "high" -> Color(0xFFE65100)
        "medium" -> CpaWarning
        else -> CpaSuccess
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CpaBg),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1) الحالة العامة ──
        item {
            SectionCard(title = "SHIELD STATUS · حالة الدرع", icon = Icons.Default.Shield) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill("الخطر", automationState.intelRiskLevel, riskColor, Modifier.weight(1f))
                    StatusPill("النمط", automationState.intelPattern.take(18), CpaPrimary, Modifier.weight(1f))
                    StatusPill(
                        "الدرع",
                        if (automationState.shieldVersionApplied > 0) "v${automationState.shieldVersionApplied}" else "—",
                        if (automationState.shieldVersionApplied >= AutomationScriptBuilder.SHIELD_VERSION) CpaSuccess else CpaWarning,
                        Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                InfoRow("Anomalies (Session)", "${stats.anomalies}", isMonospace = true)
                InfoRow("Humanization Boost", "+${stats.humanBoost}", isMonospace = true)
                InfoRow("Proxy", stats.proxySummary.ifBlank { "Direct" }, isMonospace = true)
            }
        }

        // ── 2) التحكم بالمتتبعات (hotjar وأخواتها) ──
        item {
            SectionCard(title = "TRACKER CONTROL · المتتبعات", icon = Icons.Default.Block) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("درع المتتبعات", color = CpaText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "حظر التسجيل + خداع الصفحة",
                            color = CpaTextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                    Switch(
                        checked = settings.trackerShieldEnabled,
                        onCheckedChange = { onToggleTrackerShield(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CpaPrimary,
                            checkedTrackColor = CpaPrimaryBorder,
                            uncheckedThumbColor = CpaTextMuted,
                            uncheckedTrackColor = CpaCard
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModeButton("🎭 خداع", "deceive", settings.trackerMode == "deceive", { onSetTrackerMode("deceive") }, Modifier.weight(1f))
                    ModeButton("⛔ حظر", "block", settings.trackerMode == "block", { onSetTrackerMode("block") }, Modifier.weight(1f))
                    ModeButton("✅ سماح", "allow", settings.trackerMode == "allow", { onSetTrackerMode("allow") }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (settings.trackerMode) {
                        "deceive" -> "خداع: حظر شبكي + stubs مزيفة — الموقع يظن التتبع يعمل (غير مرئي) ✅"
                        "block" -> "حظر: حظر شبكي فقط — قد تكشفه كواشف المانعات ⚠️"
                        else -> "سماح: لا حظر — للمقارنة والتشخيص فقط"
                    },
                    color = CpaTextMuted,
                    fontSize = 10.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("النطاقات المغطاة (${MainActivity.CustomWebViewClient.SURVEILLANCE_VENDORS.size}):", color = CpaTextMuted, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                VendorChips(MainActivity.CustomWebViewClient.SURVEILLANCE_VENDORS.sorted())
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("محظور هذه الجلسة: ${stats.blockedTotal}", color = CpaSuccess, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CpaPrimary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تحديث تلقائي", color = CpaTextMuted, fontSize = 10.sp)
                    }
                }
                if (stats.blockedTrackers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    stats.blockedTrackers.entries.sortedByDescending { it.value }.take(6).forEach { (domain, count) ->
                        InfoRow(domain, "×$count", isMonospace = true)
                    }
                }
            }
        }

        // ── 3) البصمة والاختبار الذاتي ──
        item {
            SectionCard(title = "FINGERPRINT · البصمة", icon = Icons.Default.Visibility) {
                InfoRow("Shield Version", "v${AutomationScriptBuilder.SHIELD_VERSION} (applied v${automationState.shieldVersionApplied})", isMonospace = true)
                InfoRow("Fingerprint Seed", "#${automationState.fingerprintSeed}", isMonospace = true)
                InfoRow(
                    "Last Self-Test",
                    automationState.lastSelfTestSummary.ifBlank { "لم يُشغّل بعد" }
                )
                Spacer(modifier = Modifier.height(8.dp))
                ActionButton("🧪 تشغيل الاختبار الذاتي", CpaPrimary, Color.Black) { onRunSelfTest() }
            }
        }

        // ── 4) الشبكة والجغرافيا ──
        item {
            SectionCard(title = "NETWORK & GEO · الشبكة", icon = Icons.Default.Shield) {
                InfoRow("Hostile Hosts", stats.hostileHosts.ifBlank { "—" })
                InfoRow("Top Converting Geos", stats.topGeos.ifBlank { "—" })
                InfoRow("Site Fingerprinting", "${stats.lastShieldScore} pts", isMonospace = true)
                Spacer(modifier = Modifier.height(8.dp))
                ActionButton("🔄 تدوير البروكسي الآن", CpaCard, CpaPrimary, border = CpaPrimaryBorder) { onRotateProxy() }
            }
        }

        // ── 5) مفاتيح القتل (تعطيل فوري لأي وحدة) ──
        item {
            SectionCard(title = "KILL-SWITCHES · مفاتيح الأمان", icon = Icons.Default.Warning) {
                KillRow("إخلاء الجدار الأبيض", settings.blankWallEviction) { onSetKillSwitch("blankWall", it) }
                KillRow("الطفرات التطورية", settings.evolutionaryMutations) { onSetKillSwitch("mutations", it) }
                KillRow("حظر المراقبة", settings.surveillanceBlocking) { onSetKillSwitch("surveillance", it) }
                KillRow("تصحيح XHR", settings.xhrCorrection) { onSetKillSwitch("xhr", it) }
            }
        }

        // ── 6) إجراءات الذكاء ──
        item {
            SectionCard(title = "INTELLIGENCE ACTIONS · الذكاء", icon = Icons.Default.PlayArrow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("مستوى الأنسنة +${stats.humanBoost}", color = CpaText, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniButton("-", { onNudgeHumanization(-1) })
                        MiniButton("+", { onNudgeHumanization(1) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                ActionButton("🚨 تفعيل الهروب الآن", CpaError, Color.White) { onTriggerEscape() }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CpaWarning, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "الهروب يبدل المسار ويعيد التحميل عند أول تحليل صفحة.",
                        color = CpaTextMuted,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun KillRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CpaText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CpaPrimary,
                checkedTrackColor = CpaPrimaryBorder,
                uncheckedThumbColor = CpaTextMuted,
                uncheckedTrackColor = CpaCard
            )
        )
    }
}

@Composable
private fun StatusPill(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CpaBg)
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = CpaTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
    }
}

@Composable
private fun ModeButton(label: String, mode: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CpaPrimary else CpaBg)
            .border(1.dp, if (selected) CpaPrimary else CpaBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else CpaTextMuted,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun VendorChips(vendors: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        vendors.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { v ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(CpaPrimaryDim)
                            .border(0.5.dp, CpaPrimaryBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(v, color = CpaPrimary, fontSize = 8.5.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, bg: Color, fg: Color, border: Color? = null, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(8.dp)) else Modifier)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MiniButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CpaCard)
            .border(1.dp, CpaPrimaryBorder, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = CpaPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}
