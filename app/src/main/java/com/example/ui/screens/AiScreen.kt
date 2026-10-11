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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppSettings
import com.example.service.AgentServer
import com.example.ui.AppViewModel
import com.example.ui.theme.CpaBg
import com.example.ui.theme.CpaBorder
import com.example.ui.theme.CpaCard
import com.example.ui.theme.CpaError
import com.example.ui.theme.CpaPrimary
import com.example.ui.theme.CpaPrimaryBorder
import com.example.ui.theme.CpaSuccess
import com.example.ui.theme.CpaText
import com.example.ui.theme.CpaTextMuted

@Composable
fun AiScreen(
    settings: AppSettings,
    aiConsole: List<String>,
    totalScore: Int,
    ledger: Map<String, AppViewModel.AiTaskLedger>,
    tasks: List<com.example.data.model.TaskEntity>,
    onRunFixRound: () -> Unit,
    onToggleAutoFix: (Boolean) -> Unit,
    onClearConsole: () -> Unit,
    agentState: AgentServer.AgentState = AgentServer.AgentState(),
    agentHistory: List<AgentServer.AgentRecord> = emptyList(),
    onStartMission: (com.example.data.model.TaskEntity) -> Unit = {},
    onStopMission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // NOTE: parent re-passes fresh aiConsole/ledger on every flow emission — no local refresh needed.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CpaBg),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Score header ──
        item {
            SectionCard(title = "AI AGENT · الوكيل المفتوح", icon = Icons.Default.Psychology) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("رصيد الوكيل", color = CpaTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "$totalScore pts",
                            color = CpaPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "إصلاح +5 · تحويل +100 · جولة -1",
                            color = CpaTextMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("تلقائي", color = CpaText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = settings.aiAutoFix,
                                onCheckedChange = { onToggleAutoFix(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CpaPrimary,
                                    checkedTrackColor = CpaPrimaryBorder,
                                    uncheckedThumbColor = CpaTextMuted,
                                    uncheckedTrackColor = CpaCard
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CpaPrimary)
                                .clickable { onRunFixRound() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("جولة إصلاح", color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        // ── Mission server: run a whole task through the agent (inspect→repeat→heal→score) ──
        item {
            val missionActive = agentState.phase != AgentServer.AgentPhase.IDLE &&
                agentState.phase != AgentServer.AgentPhase.SCORED &&
                agentState.phase != AgentServer.AgentPhase.STOPPED
            SectionCard(title = "MISSION SERVER · خادم المهام", icon = Icons.Default.Psychology) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            agentState.missionName.ifBlank { "لا مهمة جارية" },
                            color = CpaText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            "${AgentServer.phaseLabelAr(agentState.phase)} · خطوات ${agentState.steps} · إصلاحات ${agentState.repairs}",
                            color = CpaTextMuted,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        agentState.lastScore?.let { sc ->
                            Text(
                                "آخر محاسبة: ${sc.total}/100 (${sc.grade}) ${if (sc.converted) "🏆" else "❌"}",
                                color = if (sc.converted) CpaSuccess else CpaTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    if (missionActive) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CpaError)
                                .clickable { onStopMission() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("إيقاف ⏸️", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("شغّل مهمة عبر الوكيل (يفحص ← يكرر ← يصلح DOM ← يُحاسَب بالتحويل):", color = CpaTextMuted, fontSize = 10.5.sp)
                Spacer(modifier = Modifier.height(4.dp))
                tasks.filter { it.enabled }.take(8).forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t.name,
                            color = CpaText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (missionActive) CpaCard else CpaPrimary)
                                .clickable(enabled = !missionActive) { onStartMission(t) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (missionActive) CpaTextMuted else Color.Black, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("وكيل 🤖", color = if (missionActive) CpaTextMuted else Color.Black, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                // Live mission trace
                if (agentState.trace.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("TRACE · الأثر الحي (${agentState.trace.size}):", color = CpaPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    agentState.trace.takeLast(8).forEach { line ->
                        Text(line, color = CpaText, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
                // Accountability history (conversion-scored)
                if (agentHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("ACCOUNTABILITY · سجل المحاسبة:", color = CpaPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    agentHistory.take(6).forEach { rec ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                rec.taskName.ifBlank { "مهمة" },
                                color = CpaText,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                "${rec.score.total} (${rec.score.grade}) ${if (rec.score.converted) "🏆" else "❌"}",
                                color = if (rec.score.converted) CpaSuccess else CpaTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }

        // ── Ledger per task ──
        if (ledger.isNotEmpty()) {
            item {
                SectionCard(title = "LEDGER · دفتر المحاسبة", icon = Icons.Default.Psychology) {
                    ledger.entries.sortedByDescending { it.value.score }.take(10).forEach { (taskId, entry) ->
                        val name = tasks.firstOrNull { it.id == taskId }?.name ?: taskId.take(14)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, color = CpaText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(
                                    "جولات ${entry.rounds} · ✅${entry.fixesOk} ❌${entry.fixesFail} · ${if (entry.converted) "حوّل 🏆" else "بلا تحويل"}",
                                    color = CpaTextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                "${entry.score}",
                                color = if (entry.score >= 0) CpaSuccess else CpaTextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        // ── Live console ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CONSOLE · الكونسول (${aiConsole.size})",
                    color = CpaPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = { onClearConsole() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = CpaTextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (aiConsole.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CpaCard)
                        .border(1.dp, CpaBorder, RoundedCornerShape(10.dp))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "لا نشاط بعد — افتح صفحة ثم اضغط «جولة إصلاح» أو فعّل الوضع التلقائي.",
                        color = CpaTextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(aiConsole) { line ->
                val accent = when {
                    line.contains("✅") || line.contains("🏆") -> CpaSuccess
                    line.contains("❌") || line.contains("⚠️") -> CpaPrimary
                    else -> CpaTextMuted
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CpaCard)
                        .border(0.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(line, color = CpaText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}
