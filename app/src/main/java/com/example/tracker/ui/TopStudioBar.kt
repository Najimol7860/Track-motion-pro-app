package com.example.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.model.SampleClip
import com.example.ui.theme.*

@Composable
fun TopStudioBar(
    currentClip: SampleClip,
    showTrajectory: Boolean,
    showSearchRegion: Boolean,
    isStickyLocked: Boolean,
    onOpenFootagePicker: () -> Unit,
    onToggleTrajectory: () -> Unit,
    onToggleSearchRegion: () -> Unit,
    onToggleStickyLock: () -> Unit,
    onOpenAiAdvisor: () -> Unit,
    onOpenExportDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_studio_bar"),
        color = AeSlate950,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Brand + Current Footage Selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenFootagePicker() }
                    .background(AeSlate900)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("footage_selector_pill")
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CyanNeon)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "MOTION TRACKER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = CyanGlow,
                        letterSpacing = 1.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentClip.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Footage",
                            tint = AeSlate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Quick Canvas Overlays, AI Advisor, Sticky Lock & Export Action
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Advisor Sparkle Button
                IconButton(
                    onClick = onOpenAiAdvisor,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyanNeon.copy(alpha = 0.18f))
                        .border(1.dp, CyanNeon.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .testTag("open_ai_advisor_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Advisor",
                        tint = CyanNeon,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Sticky Object Lock Button
                IconButton(
                    onClick = onToggleStickyLock,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isStickyLocked) EmeraldLocked.copy(alpha = 0.2f) else AeSlate900)
                        .border(1.dp, if (isStickyLocked) EmeraldLocked.copy(alpha = 0.7f) else AeSlate800, RoundedCornerShape(6.dp))
                        .testTag("toggle_sticky_lock_top")
                ) {
                    Icon(
                        imageVector = if (isStickyLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Toggle Sticky Lock",
                        tint = if (isStickyLocked) EmeraldLocked else AeSlate400,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Trajectory Trail Toggle
                IconButton(
                    onClick = onToggleTrajectory,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (showTrajectory) CyanNeon.copy(alpha = 0.2f) else AeSlate900)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Toggle Trajectory Trail",
                        tint = if (showTrajectory) CyanNeon else AeSlate600,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Search Region Box Toggle
                IconButton(
                    onClick = onToggleSearchRegion,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (showSearchRegion) AmberKeyframe.copy(alpha = 0.2f) else AeSlate900)
                ) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Toggle Search Region",
                        tint = if (showSearchRegion) AmberGlow else AeSlate600,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // After Effects Export
                IconButton(
                    onClick = onOpenExportDialog,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AeSlate900)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Keyframes",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
