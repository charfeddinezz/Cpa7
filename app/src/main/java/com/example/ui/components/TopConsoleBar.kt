package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutomationState
import com.example.ui.theme.CpaBg
import com.example.ui.theme.CpaBorder
import com.example.ui.theme.CpaCard
import com.example.ui.theme.CpaError
import com.example.ui.theme.CpaPrimary
import com.example.ui.theme.CpaSuccess
import com.example.ui.theme.CpaText
import com.example.ui.theme.CpaTextMuted

@Composable
fun TopConsoleBar(
    automationState: AutomationState,
    disableWebRtc: Boolean = false,
    onToggleAutomation: () -> Unit,
    onRefreshGeo: () -> Unit,
    onToggleDisableWebRtc: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CpaCard)
            .border(1.dp, CpaBorder)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Title and IP + WebRTC badges
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (automationState.isRunning) CpaSuccess else CpaPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SMART AUTOMATOR",
                        color = CpaText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Runs:${automationState.completedThisSession} • Leads:${automationState.leadsThisSession}",
                        color = CpaTextMuted,
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // IP indicator + Désactiver WebRTC quick badge
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CpaBg)
                            .clickable { onRefreshGeo() }
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "IP: ${automationState.activeIp}",
                            color = CpaTextMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh IP",
                            tint = CpaPrimary,
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (disableWebRtc) CpaError.copy(alpha = 0.2f) else CpaBg)
                            .border(0.5.dp, if (disableWebRtc) CpaError else CpaPrimary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .clickable { onToggleDisableWebRtc?.invoke(!disableWebRtc) }
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (disableWebRtc) "🔇 WebRTC: OFF" else "🛡️ WebRTC: SPOOF",
                            color = if (disableWebRtc) CpaError else CpaSuccess,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Right: Start / Stop Button
            Button(
                onClick = onToggleAutomation,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (automationState.isRunning) CpaError else CpaPrimary,
                    contentColor = if (automationState.isRunning) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("toggle_automation_button")
            ) {
                Icon(
                    imageVector = if (automationState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (automationState.isRunning) "Stop" else "Start",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (automationState.isRunning) "STOP" else "START",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
