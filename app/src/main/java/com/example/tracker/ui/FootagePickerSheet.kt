package com.example.tracker.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.tracker.media.ProceduralFootageEngine
import com.example.tracker.model.SampleClip
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootagePickerSheet(
    currentClipId: String,
    onClipSelected: (SampleClip) -> Unit,
    onCustomVideoPicked: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onCustomVideoPicked(uri)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AeSlate900,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FOOTAGE SELECTOR",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanNeon
                    )
                    Text(
                        text = "Choose action clip or import device video for tracking",
                        fontSize = 11.sp,
                        color = AeSlate400
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AeSlate400)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Device Video Import Button
            Card(
                colors = CardDefaults.cardColors(containerColor = AeSlate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { videoPickerLauncher.launch("video/*") }
                    .testTag("import_video_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyanNeon.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VideoFile, contentDescription = "Device Video", tint = CyanNeon)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Import From Gallery / Files", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Extract real frames from any MP4 / WebM video on phone", fontSize = 11.sp, color = AeSlate400)
                    }
                    Icon(Icons.Default.ArrowForwardIos, contentDescription = "Pick", tint = AeSlate400, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "PRESET HARD-TRACKING CHALLENGE CLIPS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AmberGlow
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 280.dp)
            ) {
                items(ProceduralFootageEngine.sampleClips) { clip ->
                    val isSelected = clip.id == currentClipId
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AeSlate850 else AeSlate800
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onClipSelected(clip)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = clip.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) CyanNeon else Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AmberKeyframe.copy(alpha = 0.2f))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(clip.previewBadge, fontSize = 9.sp, color = AmberGlow, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(clip.description, fontSize = 11.sp, color = AeSlate400, maxLines = 2)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("${clip.totalFrames} frames • ${clip.fps} FPS", fontSize = 10.sp, color = AeSlate600)
                            }

                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = CyanNeon, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
