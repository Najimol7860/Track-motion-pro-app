package com.example.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tracker.viewmodel.MotionTrackerViewModel
import com.example.ui.theme.AeSlate950

@Composable
fun MotionTrackerScreen(
    viewModel: MotionTrackerViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showFootageSheet by remember { mutableStateOf(false) }
    var showAddLayerDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissToast()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(AeSlate950)) {
        val isLandscape = maxWidth > maxHeight && maxWidth >= 580.dp

        if (isLandscape) {
            // Landscape Studio Layout (Canonical Split View for Tablets & Landscape Phones)
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("motion_tracker_screen"),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopStudioBar(
                        currentClip = uiState.currentClip,
                        showTrajectory = uiState.showTrajectoryTrail,
                        showSearchRegion = uiState.showSearchRegionOverlay,
                        isStickyLocked = uiState.trackerSettings.stickyObjectLock,
                        onOpenFootagePicker = { showFootageSheet = true },
                        onToggleTrajectory = { viewModel.toggleTrajectoryTrail() },
                        onToggleSearchRegion = { viewModel.toggleSearchRegionOverlay() },
                        onToggleStickyLock = { viewModel.toggleStickyObjectLock() },
                        onOpenAiAdvisor = { viewModel.setTab(com.example.tracker.viewmodel.StudioTab.AI_ADVISOR) },
                        onOpenExportDialog = { showExportDialog = true }
                    )
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding()
                ) {
                    // Left Column: Video Viewport & Timeline
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            TrackingCanvas(
                                frameBitmap = uiState.currentFrameBitmap,
                                currentPoints = uiState.currentPoints,
                                keyframes = uiState.keyframes,
                                currentFrameIndex = uiState.currentFrameIndex,
                                trackingMode = uiState.trackingMode,
                                attachedLayers = uiState.attachedLayers,
                                selectedLayerId = uiState.selectedLayerId,
                                showTrajectory = uiState.showTrajectoryTrail,
                                showSearchRegion = uiState.showSearchRegionOverlay,
                                onPointMoved = { idx, x, y -> viewModel.updatePointPosition(idx, x, y) },
                                onPointTap = { idx -> viewModel.autoSnapPointToHarrisCorner(idx) },
                                onStickToObject = { idx, x, y -> viewModel.lockAndStickToObject(idx, x, y) },
                                onPointsChanged = { points -> viewModel.updateAllPoints(points) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Transport Bar
                        TimelineTransportBar(
                            currentFrame = uiState.currentFrameIndex,
                            totalFrames = uiState.currentClip.totalFrames,
                            fps = uiState.currentClip.fps,
                            isPlaying = uiState.isPlaying,
                            isTracking = uiState.isTracking,
                            confidence = uiState.currentConfidence,
                            statusText = uiState.trackingStatusText,
                            keyframes = uiState.keyframes,
                            onFrameScrub = { viewModel.loadFrame(it) },
                            onPlayPause = { viewModel.playPause() },
                            onStepBack = { viewModel.stepFrame(forward = false) },
                            onStepForward = { viewModel.stepFrame(forward = true) },
                            onTrackForward = { viewModel.startContinuousTracking(forward = true) },
                            onTrackBackward = { viewModel.startContinuousTracking(forward = false) },
                            onAutoSnapFeature = { viewModel.autoSnapPointToHarrisCorner(0) }
                        )
                    }

                    // Vertical Divider
                    VerticalDivider(color = com.example.ui.theme.AeSlate800, thickness = 1.dp)

                    // Right Supporting Pane: Control Deck
                    Box(
                        modifier = Modifier
                            .widthIn(min = 340.dp, max = 400.dp)
                            .fillMaxHeight()
                    ) {
                        TrackerControlDeck(
                            activeTab = uiState.activeTab,
                            onTabSelected = { viewModel.setTab(it) },
                            trackingMode = uiState.trackingMode,
                            onModeSelected = { viewModel.setTrackingMode(it) },
                            settings = uiState.trackerSettings,
                            onSettingsChanged = { viewModel.updateTrackerSettings(it) },
                            attachedLayers = uiState.attachedLayers,
                            selectedLayerId = uiState.selectedLayerId,
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onAddLayerClick = { showAddLayerDialog = true },
                            onUpdateLayer = { viewModel.updateAttachedLayer(it) },
                            onDeleteLayer = { viewModel.deleteAttachedLayer(it) },
                            keyframes = uiState.keyframes,
                            onClearKeyframes = { viewModel.clearKeyframes() },
                            onExportAfterEffects = { showExportDialog = true },
                            savedProjects = uiState.savedProjects,
                            onSaveProject = { viewModel.saveProject(it) },
                            onLoadProject = { viewModel.loadProject(it) },
                            onDeleteProject = { viewModel.deleteProject(it) },
                            currentProjectTitle = uiState.currentProjectTitle,
                            aiReport = uiState.aiReport,
                            isAnalyzingAi = uiState.isAnalyzingAi,
                            currentClip = uiState.currentClip,
                            onRunAiAnalysis = { viewModel.analyzeFootageWithAi() },
                            onApplyAiRecommendations = { viewModel.applyAiRecommendations(it) },
                            onToggleStickyLock = { viewModel.toggleStickyObjectLock() },
                            onSnapToTarget = { viewModel.autoSnapPointToHarrisCorner(0) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            // Standard Portrait Mobile Layout
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("motion_tracker_screen"),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopStudioBar(
                        currentClip = uiState.currentClip,
                        showTrajectory = uiState.showTrajectoryTrail,
                        showSearchRegion = uiState.showSearchRegionOverlay,
                        isStickyLocked = uiState.trackerSettings.stickyObjectLock,
                        onOpenFootagePicker = { showFootageSheet = true },
                        onToggleTrajectory = { viewModel.toggleTrajectoryTrail() },
                        onToggleSearchRegion = { viewModel.toggleSearchRegionOverlay() },
                        onToggleStickyLock = { viewModel.toggleStickyObjectLock() },
                        onOpenAiAdvisor = { viewModel.setTab(com.example.tracker.viewmodel.StudioTab.AI_ADVISOR) },
                        onOpenExportDialog = { showExportDialog = true }
                    )
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        // Transport Timeline Controls
                        TimelineTransportBar(
                            currentFrame = uiState.currentFrameIndex,
                            totalFrames = uiState.currentClip.totalFrames,
                            fps = uiState.currentClip.fps,
                            isPlaying = uiState.isPlaying,
                            isTracking = uiState.isTracking,
                            confidence = uiState.currentConfidence,
                            statusText = uiState.trackingStatusText,
                            keyframes = uiState.keyframes,
                            onFrameScrub = { viewModel.loadFrame(it) },
                            onPlayPause = { viewModel.playPause() },
                            onStepBack = { viewModel.stepFrame(forward = false) },
                            onStepForward = { viewModel.stepFrame(forward = true) },
                            onTrackForward = { viewModel.startContinuousTracking(forward = true) },
                            onTrackBackward = { viewModel.startContinuousTracking(forward = false) },
                            onAutoSnapFeature = { viewModel.autoSnapPointToHarrisCorner(0) }
                        )

                        // Bottom Control Deck (Tabs: Tracker, AI Advisor, Layers, Curves, Projects)
                        TrackerControlDeck(
                            activeTab = uiState.activeTab,
                            onTabSelected = { viewModel.setTab(it) },
                            trackingMode = uiState.trackingMode,
                            onModeSelected = { viewModel.setTrackingMode(it) },
                            settings = uiState.trackerSettings,
                            onSettingsChanged = { viewModel.updateTrackerSettings(it) },
                            attachedLayers = uiState.attachedLayers,
                            selectedLayerId = uiState.selectedLayerId,
                            onSelectLayer = { viewModel.selectLayer(it) },
                            onAddLayerClick = { showAddLayerDialog = true },
                            onUpdateLayer = { viewModel.updateAttachedLayer(it) },
                            onDeleteLayer = { viewModel.deleteAttachedLayer(it) },
                            keyframes = uiState.keyframes,
                            onClearKeyframes = { viewModel.clearKeyframes() },
                            onExportAfterEffects = { showExportDialog = true },
                            savedProjects = uiState.savedProjects,
                            onSaveProject = { viewModel.saveProject(it) },
                            onLoadProject = { viewModel.loadProject(it) },
                            onDeleteProject = { viewModel.deleteProject(it) },
                            currentProjectTitle = uiState.currentProjectTitle,
                            aiReport = uiState.aiReport,
                            isAnalyzingAi = uiState.isAnalyzingAi,
                            currentClip = uiState.currentClip,
                            onRunAiAnalysis = { viewModel.analyzeFootageWithAi() },
                            onApplyAiRecommendations = { viewModel.applyAiRecommendations(it) },
                            onToggleStickyLock = { viewModel.toggleStickyObjectLock() },
                            onSnapToTarget = { viewModel.autoSnapPointToHarrisCorner(0) }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Interactive Tracking Canvas Viewport
                    TrackingCanvas(
                        frameBitmap = uiState.currentFrameBitmap,
                        currentPoints = uiState.currentPoints,
                        keyframes = uiState.keyframes,
                        currentFrameIndex = uiState.currentFrameIndex,
                        trackingMode = uiState.trackingMode,
                        attachedLayers = uiState.attachedLayers,
                        selectedLayerId = uiState.selectedLayerId,
                        showTrajectory = uiState.showTrajectoryTrail,
                        showSearchRegion = uiState.showSearchRegionOverlay,
                        onPointMoved = { idx, x, y -> viewModel.updatePointPosition(idx, x, y) },
                        onPointTap = { idx -> viewModel.autoSnapPointToHarrisCorner(idx) },
                        onStickToObject = { idx, x, y -> viewModel.lockAndStickToObject(idx, x, y) },
                        onPointsChanged = { points -> viewModel.updateAllPoints(points) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // Modal Sheets & Dialogs
    if (showFootageSheet) {
        FootagePickerSheet(
            currentClipId = uiState.currentClip.id,
            onClipSelected = { viewModel.selectClip(it) },
            onCustomVideoPicked = { viewModel.loadCustomVideo(it) },
            onDismiss = { showFootageSheet = false }
        )
    }

    if (showAddLayerDialog) {
        AddLayerDialog(
            onDismiss = { showAddLayerDialog = false },
            onAddLayer = { type, name, text, colorHex ->
                viewModel.addAttachedLayer(type, name, text, colorHex)
            }
        )
    }

    if (showExportDialog) {
        ExportDialog(
            exportData = viewModel.getAfterEffectsExport(),
            onDismiss = { showExportDialog = false },
            onCopied = { viewModel.saveProject("AE Export Clip") }
        )
    }
}
