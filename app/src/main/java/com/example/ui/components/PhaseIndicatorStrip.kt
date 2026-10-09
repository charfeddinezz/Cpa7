package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutomationState
import com.example.ui.theme.CpaBorder
import com.example.ui.theme.CpaPrimary
import com.example.ui.theme.CpaPrimaryBorder
import com.example.ui.theme.CpaPrimaryDim
import com.example.ui.theme.CpaText
import com.example.ui.theme.CpaTextMuted

@Composable
fun PhaseIndicatorStrip(
    automationState: AutomationState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val (icon, color) = when (automationState.phase) {
        "preparing" -> Pair(Icons.Default.Settings, CpaPrimary)
        "fetching_geo" -> Pair(Icons.Default.Language, CpaPrimary)
        "generating_identity" -> Pair(Icons.Default.Person, CpaPrimary)
        "browser" -> Pair(Icons.Default.Language, CpaPrimary)
        "executing" -> Pair(Icons.Default.PlayArrow, CpaPrimary)
        "stage_waiting" -> Pair(Icons.Default.HourglassEmpty, Color(0xFFFFB300))
        "checking_lead" -> Pair(Icons.Default.Search, CpaPrimary)
        "completed" -> Pair(Icons.Default.CheckCircle, CpaPrimary)
        else -> Pair(Icons.Default.HourglassEmpty, CpaTextMuted)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CpaPrimaryDim)
            .border(1.dp, CpaPrimaryBorder)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (automationState.isWaitingBetweenStages) Color(0xFFFFB300).copy(alpha = 0.22f) else CpaPrimaryBorder),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (automationState.isWaitingBetweenStages) Icons.Default.HourglassEmpty else icon,
                        contentDescription = "Phase Icon",
                        tint = if (automationState.isWaitingBetweenStages) Color(0xFFFFB300) else color,
                        modifier = Modifier
                            .size(14.dp)
                            .then(if (automationState.isRunning) Modifier.alpha(alpha) else Modifier)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (automationState.isWaitingBetweenStages) {
                                "STAGE WAIT • ${automationState.stageTransitionLabel.ifBlank { "انتظار بين المراحل" }}"
                            } else {
                                automationState.phase.replace("_", " ").uppercase()
                            },
                            color = if (automationState.isWaitingBetweenStages) Color(0xFFFFB300) else CpaPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = automationState.phaseDetail,
                        color = CpaText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Human Realism Score Badge
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF00B8D4).copy(alpha = 0.16f))
                    .border(0.5.dp, Color(0xFF00B8D4).copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "🧠 بشري ${automationState.humanRealismScore}%",
                    color = Color(0xFF00E5FF),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (automationState.isWaitingBetweenStages && automationState.stageWaitCountdownSec > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFB300).copy(alpha = 0.22f))
                        .border(0.8.dp, Color(0xFFFFB300), RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⏳ انتظار ${automationState.stageWaitCountdownSec}/${automationState.stageWaitTotalSec} ث",
                        color = Color(0xFFFFD54F),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            if (automationState.currentTaskName != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CpaPrimaryBorder)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = automationState.currentTaskName,
                        color = CpaPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (automationState.isRunning && automationState.cycleCountdown > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF00E676).copy(alpha = 0.2f))
                        .border(0.5.dp, Color(0xFF00E676), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "🔁 ${automationState.cycleCountdown} ث",
                        color = Color(0xFF00E676),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Sub-strip showing live human action status & stage transition waiting progress bar
        if (automationState.isRunning || automationState.isWaitingBetweenStages) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        if (automationState.isWaitingBetweenStages) Color(0xFFFFB300).copy(alpha = 0.12f)
                        else Color(0xFF00B8D4).copy(alpha = 0.10f)
                    )
                    .border(
                        0.5.dp,
                        if (automationState.isWaitingBetweenStages) Color(0xFFFFB300).copy(alpha = 0.45f)
                        else Color(0xFF00B8D4).copy(alpha = 0.35f),
                        RoundedCornerShape(5.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (automationState.isWaitingBetweenStages) {
                        "⏳ فاصل الانتقال بين المراحل (${automationState.stageTransitionLabel}): ${automationState.humanActionStatusAr}"
                    } else {
                        "🧑 المحاكي البشري: ${automationState.humanActionStatusAr}"
                    },
                    color = if (automationState.isWaitingBetweenStages) Color(0xFFFFE082) else Color(0xFF84FFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (automationState.isWaitingBetweenStages && automationState.stageWaitTotalSec > 0) {
                    val progressFraction = (
                        (automationState.stageWaitTotalSec - automationState.stageWaitCountdownSec + 1).toFloat() /
                            automationState.stageWaitTotalSec.coerceAtLeast(1).toFloat()
                        ).coerceIn(0.08f, 1f)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .size(width = 64.dp, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .size(width = 64.dp, height = 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFFFB300))
                        )
                    }
                }
            }
        }
    }
}
