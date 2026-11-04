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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.model.FrameKeyframe
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TimelineTransportBar(
    currentFrame: Int,
    totalFrames: Int,
    fps: Int,
    isPlaying: Boolean,
    isTracking: Boolean,
    confidence: Float,
    statusText: String,
    keyframes: Map<Int, FrameKeyframe>,
    onFrameScrub: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onTrackForward: () -> Unit,
    onTrackBackward: () -> Unit,
    onAutoSnapFeature: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("timeline_transport_bar"),
        color = AeSlate900,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Top Row: Timecode & Realtime Confidence Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timecode
                val sec = currentFrame / fps
                val frameRem = currentFrame % fps
                val timecode = String.format("%02d:%02d:%02d", sec / 60, sec % 60, frameRem)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AeSlate800)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = timecode,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CyanNeon,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "F $currentFrame / ${totalFrames - 1}",
                        color = AeSlate400,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                // Tracking Status & Confidence Meter
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val confPercent = (confidence * 100).roundToInt()
                    val statusColor = when {
                        isTracking -> CrimsonReticle
                        confidence >= 0.8f -> EmeraldLocked
                        confidence >= 0.5f -> AmberGlow
                        else -> CrimsonReticle
                    }

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }

            // Timeline Scrubber Slider with Keyframe notches
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Slider(
                    value = currentFrame.toFloat(),
                    onValueChange = { onFrameScrub(it.roundToInt()) },
                    valueRange = 0f..(totalFrames - 1).coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanNeon,
                        activeTrackColor = CyanNeon,
                        inactiveTrackColor = AeSlate800
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("frame_slider")
                )
            }

            // Transport Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Auto Snap to Harris feature
                FilledTonalButton(
                    onClick = onAutoSnapFeature,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AeSlate800,
                        contentColor = AmberGlow
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("snap_feature_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Snap to Feature",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Snap Feature", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Core After Effects Tracking Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Track Back Continuous (<<)
                    IconButton(
                        onClick = onTrackBackward,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTracking) AeSlate700 else AeSlate800)
                            .testTag("track_backward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Track Backward",
                            tint = CyanGlow,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Step 1 Frame Back (|<)
                    IconButton(
                        onClick = onStepBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AeSlate800)
                            .testTag("step_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Step Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Play / Pause (Center Primary)
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CyanNeon)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = AeSlate950,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Step 1 Frame Forward (>|)
                    IconButton(
                        onClick = onStepForward,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AeSlate800)
                            .testTag("step_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Step Forward",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Track Forward Continuous (>>) [Iconic AE Tracker Action]
                    Button(
                        onClick = onTrackForward,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTracking) CrimsonReticle else AmberKeyframe,
                            contentColor = AeSlate950
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .testTag("track_forward_button")
                    ) {
                        Icon(
                            imageVector = if (isTracking) Icons.Default.Stop else Icons.Default.FastForward,
                            contentDescription = "Track Forward",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isTracking) "STOP" else "TRACK >>",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
