package com.example.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.model.*
import com.example.ui.theme.*

@Composable
fun AiAdvisorPanel(
    aiReport: AiDifficultyReport?,
    isAnalyzing: Boolean,
    currentClip: SampleClip,
    currentSettings: TrackerSettings,
    onRunAiAnalysis: () -> Unit,
    onApplyRecommendations: (AiDifficultyReport) -> Unit,
    onToggleStickyLock: () -> Unit,
    onSnapToTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AeSlate950)
            .padding(16.dp)
            .testTag("ai_advisor_panel"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header & AI Action Bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AeSlate900),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(listOf(CyanNeon, VioletLayer))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(CyanNeon.copy(alpha = 0.15f))
                                    .border(1.dp, CyanNeon.copy(alpha = glowAlpha), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Advisor",
                                    tint = CyanNeon,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "AI TRACKING ADVISOR",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Footage Scenario & Optical Flow Diagnostic Engine",
                                    color = AeSlate400,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "Analyzes high-velocity whip pans, texture salience, edge gradients, and occlusion obstacles to compute optimal tracking parameters.",
                        color = AeSlate300,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = onRunAiAnalysis,
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanNeon,
                            contentColor = AeSlate950
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("run_ai_analysis_button")
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AeSlate950,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ANALYZING FOOTAGE TELEMETRY...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (aiReport != null) "RE-ANALYZE CURRENT FRAME" else "ANALYZE FOOTAGE WITH AI",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. Sticky Object Locking Control
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AeSlate900),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (currentSettings.stickyObjectLock) EmeraldLocked.copy(alpha = 0.8f) else AeSlate700
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (currentSettings.stickyObjectLock) EmeraldLocked.copy(alpha = 0.2f)
                                    else AeSlate800
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (currentSettings.stickyObjectLock) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Sticky Lock",
                                tint = if (currentSettings.stickyObjectLock) EmeraldLocked else AeSlate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Sticky Object Lock",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentSettings.stickyObjectLock) "Locked rigidly to object centroid" else "Manual drag without magnetic snap",
                                color = if (currentSettings.stickyObjectLock) EmeraldLocked else AeSlate400,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = currentSettings.stickyObjectLock,
                        onCheckedChange = { onToggleStickyLock() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldLocked,
                            checkedTrackColor = EmeraldLocked.copy(alpha = 0.3f),
                            uncheckedThumbColor = AeSlate400,
                            uncheckedTrackColor = AeSlate800
                        ),
                        modifier = Modifier.testTag("sticky_object_lock_toggle")
                    )
                }
            }
        }

        // 3. AI Diagnostic Report Content
        if (aiReport != null) {
            val badgeColor = Color(aiReport.difficultyLevel.badgeColor)

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AeSlate900),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = badgeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = aiReport.difficultyLevel.title.uppercase(),
                                    color = badgeColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Difficulty:",
                                    color = AeSlate400,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${String.format("%.1f", aiReport.difficultyScore)} / 10",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Text(
                            text = aiReport.summaryTitle,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = aiReport.summaryAnalysis,
                            color = AeSlate300,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        Surface(
                            color = AeSlate950,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AeSlate800),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = AmberKeyframe,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = aiReport.workflowAdvice,
                                    color = AmberKeyframe,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // 4. Detected Bottlenecks List
            if (aiReport.detectedChallenges.isNotEmpty()) {
                item {
                    Text(
                        text = "DETECTED TRACKING CHALLENGES (${aiReport.detectedChallenges.size})",
                        color = AeSlate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                items(aiReport.detectedChallenges) { challenge ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AeSlate900),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AeSlate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = challenge.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                val sevColor = when (challenge.severity.uppercase()) {
                                    "HIGH", "CRITICAL" -> CrimsonReticle
                                    "MODERATE" -> AmberKeyframe
                                    else -> EmeraldLocked
                                }

                                Surface(
                                    color = sevColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${challenge.severity} IMPACT",
                                        color = sevColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = challenge.description,
                                color = AeSlate400,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = challenge.recommendedFix,
                                    color = CyanNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 5. Recommended Settings Card & One-Click Apply
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AeSlate900),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECOMMENDED SETTINGS",
                                color = CyanNeon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Est. Confidence: ${(aiReport.confidenceEstimate * 100).toInt()}%",
                                color = EmeraldLocked,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Settings Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingPill(
                                label = "Search Area",
                                value = "${aiReport.recommendedSettings.searchRadius.toInt()} px",
                                modifier = Modifier.weight(1f)
                            )
                            SettingPill(
                                label = "Feature Box",
                                value = "${aiReport.recommendedSettings.featureRadius.toInt()} px",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SettingPill(
                                label = "Mode",
                                value = aiReport.recommendedMode.displayName,
                                modifier = Modifier.weight(1f)
                            )
                            SettingPill(
                                label = "Smoothing",
                                value = aiReport.recommendedSettings.smoothingMode.label.split(" ").first(),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = { onApplyRecommendations(aiReport) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanNeon,
                                contentColor = AeSlate950
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("apply_ai_recommendations_button")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "APPLY AI RECOMMENDATIONS",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Placeholder when analysis hasn't been run yet
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AeSlate900.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AeSlate800),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = AeSlate500,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Ready to Diagnose Footage",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap 'Analyze Footage with AI' above to detect whip-pan speeds, low contrast areas, and occlusion obstacles with customized parameter presets.",
                            color = AeSlate400,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AeSlate950,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AeSlate800),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = label.uppercase(),
                color = AeSlate400,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}
