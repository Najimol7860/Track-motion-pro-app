package com.example.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.example.tracker.data.db.ProjectEntity
import com.example.tracker.model.*
import com.example.tracker.viewmodel.StudioTab
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun TrackerControlDeck(
    activeTab: StudioTab,
    onTabSelected: (StudioTab) -> Unit,
    trackingMode: TrackingMode,
    onModeSelected: (TrackingMode) -> Unit,
    settings: TrackerSettings,
    onSettingsChanged: (TrackerSettings) -> Unit,
    attachedLayers: List<AttachedLayer>,
    selectedLayerId: String?,
    onSelectLayer: (String?) -> Unit,
    onAddLayerClick: () -> Unit,
    onUpdateLayer: (AttachedLayer) -> Unit,
    onDeleteLayer: (String) -> Unit,
    keyframes: Map<Int, FrameKeyframe>,
    onClearKeyframes: () -> Unit,
    onExportAfterEffects: () -> Unit,
    savedProjects: List<ProjectEntity>,
    onSaveProject: (String) -> Unit,
    onLoadProject: (ProjectEntity) -> Unit,
    onDeleteProject: (Long) -> Unit,
    currentProjectTitle: String,
    aiReport: AiDifficultyReport? = null,
    isAnalyzingAi: Boolean = false,
    currentClip: SampleClip,
    onRunAiAnalysis: () -> Unit,
    onApplyAiRecommendations: (AiDifficultyReport) -> Unit,
    onToggleStickyLock: () -> Unit,
    onSnapToTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tracker_control_deck"),
        color = AeSlate900,
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Tab Selector Navigation
            TabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = AeSlate950,
                contentColor = CyanNeon,
                indicator = { tabPositions ->
                    if (activeTab.ordinal < tabPositions.size) {
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[activeTab.ordinal])
                                .height(3.dp)
                                .background(CyanNeon)
                        )
                    }
                },
                divider = { HorizontalDivider(color = AeSlate800, thickness = 1.dp) }
            ) {
                StudioTab.values().forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { onTabSelected(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (tab == StudioTab.AI_ADVISOR) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (activeTab == tab) CyanNeon else AmberKeyframe,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeTab == tab) CyanNeon else if (tab == StudioTab.AI_ADVISOR) AmberKeyframe else AeSlate400
                                )
                            }
                        }
                    )
                }
            }

            // Tab Content Deck
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                when (activeTab) {
                    StudioTab.TRACKER -> TrackerSettingsTab(
                        trackingMode = trackingMode,
                        onModeSelected = onModeSelected,
                        settings = settings,
                        onSettingsChanged = onSettingsChanged,
                        onClearKeyframes = onClearKeyframes,
                        keyframeCount = keyframes.size,
                        onOpenAiAdvisor = { onTabSelected(StudioTab.AI_ADVISOR) }
                    )
                    StudioTab.AI_ADVISOR -> AiAdvisorPanel(
                        aiReport = aiReport,
                        isAnalyzing = isAnalyzingAi,
                        currentClip = currentClip,
                        currentSettings = settings,
                        onRunAiAnalysis = onRunAiAnalysis,
                        onApplyRecommendations = onApplyAiRecommendations,
                        onToggleStickyLock = onToggleStickyLock,
                        onSnapToTarget = onSnapToTarget
                    )
                    StudioTab.LAYERS -> LayersManagerTab(
                        layers = attachedLayers,
                        selectedLayerId = selectedLayerId,
                        onSelectLayer = onSelectLayer,
                        onAddLayerClick = onAddLayerClick,
                        onUpdateLayer = onUpdateLayer,
                        onDeleteLayer = onDeleteLayer
                    )
                    StudioTab.GRAPH -> MotionGraphTab(
                        keyframes = keyframes,
                        onExportAfterEffects = onExportAfterEffects
                    )
                    StudioTab.PROJECTS -> ProjectsTab(
                        savedProjects = savedProjects,
                        currentTitle = currentProjectTitle,
                        onSaveProject = onSaveProject,
                        onLoadProject = onLoadProject,
                        onDeleteProject = onDeleteProject
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackerSettingsTab(
    trackingMode: TrackingMode,
    onModeSelected: (TrackingMode) -> Unit,
    settings: TrackerSettings,
    onSettingsChanged: (TrackerSettings) -> Unit,
    onClearKeyframes: () -> Unit,
    keyframeCount: Int,
    onOpenAiAdvisor: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 0. AI Advisor Quick Suggestion Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AeSlate850),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAiAdvisor() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "AI TRACKING ADVISOR",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Diagnose challenging scenarios & get parameter recommendations",
                                color = AeSlate300,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open AI Advisor",
                        tint = CyanNeon,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 1. Tracking Mode Chips
        item {
            Text(
                text = "TRACKING MODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AeSlate400
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TrackingMode.values()) { mode ->
                    FilterChip(
                        selected = trackingMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.displayName, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanNeon.copy(alpha = 0.2f),
                            selectedLabelColor = CyanNeon,
                            containerColor = AeSlate800,
                            labelColor = AeSlate200
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = trackingMode == mode,
                            borderColor = if (trackingMode == mode) CyanNeon else AeSlate700
                        )
                    )
                }
            }
        }

        // Planar 4-Point Input & Dynamic Grid Banner
        if (trackingMode == TrackingMode.PLANAR_4) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AeSlate850),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("planar_input_info_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.GridOn, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Text(
                                text = "DYNAMIC PLANAR GRID OVERLAY ACTIVE",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "Interactive 4-point perspective quad input with dynamic subdivision mesh (4x4 to 16x16), 2.5x precision touch loupe, 3D surface normal projection, and real-time area/skew diagnostics.",
                            color = AeSlate300,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CyanNeon.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("🎯 Corner Pinning", color = CyanGlow, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AmberKeyframe.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AmberKeyframe.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("🔍 2.5x Touch Loupe", color = AmberGlow, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = VioletLayer.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, VioletLayerGlow.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("📐 Bilinear Mesh", color = VioletLayerGlow, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Search & Feature Dimensions
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AeSlate850),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Search Window Radius", fontSize = 12.sp, color = AeSlate200)
                        Text("${settings.searchRadius.roundToInt()} px", fontSize = 12.sp, color = CyanNeon, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = settings.searchRadius,
                        onValueChange = { onSettingsChanged(settings.copy(searchRadius = it)) },
                        valueRange = 30f..140f,
                        colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Feature Template Size", fontSize = 12.sp, color = AeSlate200)
                        Text("${settings.featureRadius.roundToInt()} px", fontSize = 12.sp, color = AmberKeyframe, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = settings.featureRadius,
                        onValueChange = { onSettingsChanged(settings.copy(featureRadius = it)) },
                        valueRange = 12f..55f,
                        colors = SliderDefaults.colors(thumbColor = AmberKeyframe, activeTrackColor = AmberKeyframe)
                    )
                }
            }
        }

        // 3. AI Robustness & Harder Object Tracking Boosters
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AeSlate850),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "HARD OBJECT TRACKING BOOSTERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AmberGlow
                    )

                    // Sticky Object Lock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sticky Object Lock", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EmeraldLocked)
                            Text("Snaps and sticks rigidly to object centroid / gradient boundary on selection", fontSize = 10.sp, color = AeSlate400)
                        }
                        Switch(
                            checked = settings.stickyObjectLock,
                            onCheckedChange = { onSettingsChanged(settings.copy(stickyObjectLock = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldLocked, checkedTrackColor = AeSlate800)
                        )
                    }

                    // Adaptive Velocity Expansion
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Adaptive Velocity Search", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Expands search window automatically during whip pans / fast acceleration", fontSize = 10.sp, color = AeSlate400)
                        }
                        Switch(
                            checked = settings.adaptiveSearch,
                            onCheckedChange = { onSettingsChanged(settings.copy(adaptiveSearch = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = AeSlate800)
                        )
                    }

                    // Kalman Occlusion Predictor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kalman Occlusion Recovery", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Uses motion momentum to bridge track across obstacles & smoke", fontSize = 10.sp, color = AeSlate400)
                        }
                        Switch(
                            checked = settings.occlusionRecovery,
                            onCheckedChange = { onSettingsChanged(settings.copy(occlusionRecovery = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = AmberGlow, checkedTrackColor = AeSlate800)
                        )
                    }

                    // Subpixel Parabolic Refinement
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Subpixel Interpolation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Bilinear parabolic curve fit (0.05px anti-jitter precision)", fontSize = 10.sp, color = AeSlate400)
                        }
                        Switch(
                            checked = settings.subpixelRefinement,
                            onCheckedChange = { onSettingsChanged(settings.copy(subpixelRefinement = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldLocked, checkedTrackColor = AeSlate800)
                        )
                    }
                }
            }
        }

        // 4. Trajectory Smoothing
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AeSlate850),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("TRAJECTORY SMOOTHING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AeSlate400)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(SmoothingMode.values()) { sm ->
                            FilterChip(
                                selected = settings.smoothingMode == sm,
                                onClick = { onSettingsChanged(settings.copy(smoothingMode = sm)) },
                                label = { Text(sm.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = VioletLayer.copy(alpha = 0.3f),
                                    selectedLabelColor = VioletLayerGlow,
                                    containerColor = AeSlate800,
                                    labelColor = AeSlate200
                                )
                            )
                        }
                    }
                }
            }
        }

        // 5. Clear Track Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Keyframes: $keyframeCount",
                    color = AeSlate400,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                OutlinedButton(
                    onClick = onClearKeyframes,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonReticle),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(brush = androidx.compose.ui.graphics.SolidColor(CrimsonReticle.copy(alpha = 0.5f))),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear Track", fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LayersManagerTab(
    layers: List<AttachedLayer>,
    selectedLayerId: String?,
    onSelectLayer: (String?) -> Unit,
    onAddLayerClick: () -> Unit,
    onUpdateLayer: (AttachedLayer) -> Unit,
    onDeleteLayer: (String) -> Unit
) {
    val selectedLayer = layers.find { it.id == selectedLayerId }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Column: Layers List
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
        ) {
            Button(
                onClick = onAddLayerClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = AeSlate950),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("add_layer_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Layer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(layers) { layer ->
                    val isSel = layer.id == selectedLayerId
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) AeSlate700 else AeSlate850
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectLayer(layer.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(layer.primaryColorHex))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = layer.name,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) CyanNeon else Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = layer.type.title,
                                        fontSize = 9.sp,
                                        color = AeSlate400
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onUpdateLayer(layer.copy(isVisible = !layer.isVisible)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Visibility",
                                    tint = if (layer.isVisible) CyanGlow else AeSlate600,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Right Column: Layer Property Inspector
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .background(AeSlate850, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (selectedLayer != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LAYER PROPERTIES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberGlow
                            )
                            IconButton(
                                onClick = { onDeleteLayer(selectedLayer.id) },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonReticle, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    // Content text
                    if (selectedLayer.type == LayerType.HUD_CALLOUT || selectedLayer.type == LayerType.TEXT_3D) {
                        item {
                            OutlinedTextField(
                                value = selectedLayer.textContent,
                                onValueChange = { onUpdateLayer(selectedLayer.copy(textContent = it)) },
                                label = { Text("Display Text", fontSize = 10.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Offset X / Y
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Offset X", fontSize = 10.sp, color = AeSlate200)
                            Text("${selectedLayer.offsetX.roundToInt()} px", fontSize = 10.sp, color = CyanNeon)
                        }
                        Slider(
                            value = selectedLayer.offsetX,
                            onValueChange = { onUpdateLayer(selectedLayer.copy(offsetX = it)) },
                            valueRange = -150f..150f,
                            colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon)
                        )
                    }

                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Offset Y", fontSize = 10.sp, color = AeSlate200)
                            Text("${selectedLayer.offsetY.roundToInt()} px", fontSize = 10.sp, color = CyanNeon)
                        }
                        Slider(
                            value = selectedLayer.offsetY,
                            onValueChange = { onUpdateLayer(selectedLayer.copy(offsetY = it)) },
                            valueRange = -150f..150f,
                            colors = SliderDefaults.colors(thumbColor = CyanNeon, activeTrackColor = CyanNeon)
                        )
                    }

                    // Scale & Rotation
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Layer Scale", fontSize = 10.sp, color = AeSlate200)
                            Text(String.format("%.1fx", selectedLayer.scale), fontSize = 10.sp, color = AmberKeyframe)
                        }
                        Slider(
                            value = selectedLayer.scale,
                            onValueChange = { onUpdateLayer(selectedLayer.copy(scale = it)) },
                            valueRange = 0.5f..2.5f,
                            colors = SliderDefaults.colors(thumbColor = AmberKeyframe, activeTrackColor = AmberKeyframe)
                        )
                    }

                    // Leader line toggle
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Show Leader Line", fontSize = 11.sp, color = AeSlate200)
                            Checkbox(
                                checked = selectedLayer.showLeaderLine,
                                onCheckedChange = { onUpdateLayer(selectedLayer.copy(showLeaderLine = it)) },
                                colors = CheckboxDefaults.colors(checkedColor = CyanNeon)
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a layer to inspect", color = AeSlate400, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MotionGraphTab(
    keyframes: Map<Int, FrameKeyframe>,
    onExportAfterEffects: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRAJECTORY CURVES & AE EXPORT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyanNeon
            )
            Button(
                onClick = onExportAfterEffects,
                colors = ButtonDefaults.buttonColors(containerColor = AmberKeyframe, contentColor = AeSlate950),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp).testTag("export_ae_button")
            ) {
                Icon(Icons.Default.Code, contentDescription = "Export", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export AE Keyframes", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Live Keyframe Stats Grid
        Card(
            colors = CardDefaults.cardColors(containerColor = AeSlate850),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TRACKED", fontSize = 10.sp, color = AeSlate400)
                    Text("${keyframes.size} kf", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val avgConf = if (keyframes.isNotEmpty()) (keyframes.values.map { it.overallConfidence }.average() * 100).toInt() else 100
                    Text("AVG MATCH", fontSize = 10.sp, color = AeSlate400)
                    Text("$avgConf%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EmeraldLocked)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FORMAT", fontSize = 10.sp, color = AeSlate400)
                    Text("AE 8.0 Script", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AmberKeyframe)
                }
            }
        }

        // Visual Mini Motion Curve Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(AeSlate950, RoundedCornerShape(8.dp))
                .border(1.dp, AeSlate800, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                if (keyframes.size < 2) return@Canvas
                val sorted = keyframes.entries.sortedBy { it.key }
                val minX = sorted.minOf { it.value.centerX }
                val maxX = sorted.maxOf { it.value.centerX }.coerceAtLeast(minX + 1f)
                val minY = sorted.minOf { it.value.centerY }
                val maxY = sorted.maxOf { it.value.centerY }.coerceAtLeast(minY + 1f)

                val pathX = androidx.compose.ui.graphics.Path()
                val pathY = androidx.compose.ui.graphics.Path()

                sorted.forEachIndexed { i, entry ->
                    val cx = (i.toFloat() / (sorted.size - 1)) * size.width
                    val cyX = size.height * (1f - (entry.value.centerX - minX) / (maxX - minX))
                    val cyY = size.height * (1f - (entry.value.centerY - minY) / (maxY - minY))

                    if (i == 0) {
                        pathX.moveTo(cx, cyX)
                        pathY.moveTo(cx, cyY)
                    } else {
                        pathX.lineTo(cx, cyX)
                        pathY.lineTo(cx, cyY)
                    }
                }

                // Draw X Curve (Cyan)
                drawPath(pathX, color = CyanNeon, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                // Draw Y Curve (Crimson)
                drawPath(pathY, color = CrimsonReticle, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(CyanNeon, CircleShape))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Pos X", color = CyanNeon, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(CrimsonReticle, CircleShape))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Pos Y", color = CrimsonReticle, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun ProjectsTab(
    savedProjects: List<ProjectEntity>,
    currentTitle: String,
    onSaveProject: (String) -> Unit,
    onLoadProject: (ProjectEntity) -> Unit,
    onDeleteProject: (Long) -> Unit
) {
    var titleInput by remember(currentTitle) { mutableStateOf(currentTitle) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Save Project Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Project Name", fontSize = 10.sp) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { onSaveProject(titleInput) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = AeSlate950),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = "Save")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Text(
            text = "SAVED PROJECTS (${savedProjects.size})",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AeSlate400
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (savedProjects.isEmpty()) {
                item {
                    Text(
                        text = "No saved tracking projects yet. Tap 'Save' above to store your keyframes & layers.",
                        fontSize = 11.sp,
                        color = AeSlate600,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(savedProjects) { proj ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AeSlate850),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(proj.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("${proj.trackingMode} • ${proj.totalFrames} frames", fontSize = 10.sp, color = AeSlate400)
                            }
                            Row {
                                FilledTonalButton(
                                    onClick = { onLoadProject(proj) },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = AeSlate800, contentColor = CyanNeon),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Open", fontSize = 10.sp)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onDeleteProject(proj.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonReticle, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
