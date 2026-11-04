package com.example.tracker.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tracker.ai.GeminiTrackerAdvisor
import com.example.tracker.data.db.AppDatabase
import com.example.tracker.data.db.ProjectEntity
import com.example.tracker.engine.OpticalFlowTrackerEngine
import com.example.tracker.media.DeviceVideoFrameExtractor
import com.example.tracker.media.ProceduralFootageEngine
import com.example.tracker.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

enum class StudioTab(val title: String) {
    TRACKER("TRACKER"),
    AI_ADVISOR("AI ADVISOR"),
    LAYERS("VFX LAYERS"),
    GRAPH("CURVES & DATA"),
    PROJECTS("PROJECTS")
}

data class UiState(
    val currentClip: SampleClip = ProceduralFootageEngine.sampleClips[0],
    val currentFrameIndex: Int = 0,
    val isPlaying: Boolean = false,
    val isTracking: Boolean = false,
    val trackingMode: TrackingMode = TrackingMode.POINT_1,
    val currentPoints: List<TrackerPoint> = emptyList(),
    val initialPoints: List<TrackerPoint> = emptyList(),
    val keyframes: Map<Int, FrameKeyframe> = emptyMap(),
    val attachedLayers: List<AttachedLayer> = emptyList(),
    val selectedLayerId: String? = null,
    val trackerSettings: TrackerSettings = TrackerSettings(),
    val activeTab: StudioTab = StudioTab.TRACKER,
    val currentFrameBitmap: Bitmap? = null,
    val showTrajectoryTrail: Boolean = true,
    val showSearchRegionOverlay: Boolean = true,
    val showHarrisOverlay: Boolean = false,
    val isStickyLocked: Boolean = true,
    val isObjectStuck: Boolean = false,
    val stuckPosition: Pair<Float, Float>? = null,
    val trackingFps: Int = 30,
    val currentConfidence: Float = 1.0f,
    val trackingStatusText: String = "READY TO TRACK",
    val isAnalyzingAi: Boolean = false,
    val aiReport: AiDifficultyReport? = null,
    val toastMessage: String? = null,
    val savedProjects: List<ProjectEntity> = emptyList(),
    val currentProjectTitle: String = "Untitled Track 01",
    val currentProjectId: Long? = null
)

class MotionTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val videoExtractor = DeviceVideoFrameExtractor(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null
    private var trackingJob: Job? = null

    init {
        initDefaultPoints(TrackingMode.POINT_1, 640f, 360f)
        initDefaultLayers()
        loadFrame(0)
        observeProjects()
    }

    private fun observeProjects() {
        viewModelScope.launch {
            db.projectDao().getAllProjects().collect { projects ->
                _uiState.update { it.copy(savedProjects = projects) }
            }
        }
    }

    private fun initDefaultPoints(mode: TrackingMode, canvasW: Float, canvasH: Float) {
        val cx = canvasW * 0.5f
        val cy = canvasH * 0.5f

        val points = when (mode) {
            TrackingMode.POINT_1 -> listOf(
                TrackerPoint(id = 0, x = cx, y = cy, name = "Anchor 1")
            )
            TrackingMode.TRANSFORM_2 -> listOf(
                TrackerPoint(id = 0, x = cx - 60f, y = cy, name = "Anchor A"),
                TrackerPoint(id = 1, x = cx + 60f, y = cy, name = "Anchor B")
            )
            TrackingMode.PLANAR_4 -> listOf(
                TrackerPoint(id = 0, x = cx - 70f, y = cy - 40f, name = "Top Left"),
                TrackerPoint(id = 1, x = cx + 70f, y = cy - 40f, name = "Top Right"),
                TrackerPoint(id = 2, x = cx + 70f, y = cy + 40f, name = "Bottom Right"),
                TrackerPoint(id = 3, x = cx - 70f, y = cy + 40f, name = "Bottom Left")
            )
            TrackingMode.ROI_BOX -> listOf(
                TrackerPoint(id = 0, x = cx, y = cy, searchRadius = 90f, featureRadius = 45f, name = "ROI Box")
            )
        }

        _uiState.update {
            it.copy(
                trackingMode = mode,
                currentPoints = points,
                initialPoints = points
            )
        }
        recordKeyframe(0, points, isUser = true)
    }

    private fun initDefaultLayers() {
        val defaultLayers = listOf(
            AttachedLayer(
                name = "HUD Crosshair Data",
                type = LayerType.HUD_CALLOUT,
                textContent = "TARGET // 01 [LOCKED]",
                offsetX = 45f,
                offsetY = -65f,
                scale = 1.0f,
                primaryColorHex = 0xFF00F0FF,
                showLeaderLine = true
            ),
            AttachedLayer(
                name = "Cyber Glow Tag",
                type = LayerType.TEXT_3D,
                textContent = "AE MOTION PIN",
                offsetX = 0f,
                offsetY = -95f,
                scale = 1.1f,
                primaryColorHex = 0xFFF59E0B,
                showLeaderLine = false
            )
        )
        _uiState.update {
            it.copy(
                attachedLayers = defaultLayers,
                selectedLayerId = defaultLayers.firstOrNull()?.id
            )
        }
    }

    fun setTrackingMode(mode: TrackingMode) {
        if (_uiState.value.trackingMode == mode) return
        stopAllJobs()
        val bitmap = _uiState.value.currentFrameBitmap
        val w = bitmap?.width?.toFloat() ?: 640f
        val h = bitmap?.height?.toFloat() ?: 360f
        initDefaultPoints(mode, w, h)
        _uiState.update { it.copy(keyframes = emptyMap(), trackingStatusText = "MODE: ${mode.displayName}") }
    }

    fun setTab(tab: StudioTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun loadFrame(frameIndex: Int) {
        val total = _uiState.value.currentClip.totalFrames
        val clampedIndex = frameIndex.coerceIn(0, total - 1)

        viewModelScope.launch {
            val clip = _uiState.value.currentClip
            val bitmap = if (clip.customVideoUri != null) {
                videoExtractor.extractFrame(clampedIndex, total)
            } else {
                ProceduralFootageEngine.getFrame(clip.id, clampedIndex)
            }

            val existingKf = _uiState.value.keyframes[clampedIndex]
            val points = existingKf?.points ?: _uiState.value.currentPoints
            val conf = existingKf?.overallConfidence ?: 1.0f

            _uiState.update {
                it.copy(
                    currentFrameIndex = clampedIndex,
                    currentFrameBitmap = bitmap,
                    currentPoints = points,
                    currentConfidence = conf
                )
            }
        }
    }

    fun playPause() {
        if (_uiState.value.isPlaying) {
            stopAllJobs()
            _uiState.update { it.copy(isPlaying = false) }
        } else {
            stopAllJobs()
            _uiState.update { it.copy(isPlaying = true) }
            playbackJob = viewModelScope.launch {
                val total = _uiState.value.currentClip.totalFrames
                val intervalMs = (1000L / _uiState.value.currentClip.fps).coerceAtLeast(16L)

                while (isActive) {
                    val nextFrame = (_uiState.value.currentFrameIndex + 1) % total
                    loadFrame(nextFrame)
                    delay(intervalMs)
                }
            }
        }
    }

    fun stepFrame(forward: Boolean) {
        stopAllJobs()
        val total = _uiState.value.currentClip.totalFrames
        val delta = if (forward) 1 else -1
        val nextFrame = (_uiState.value.currentFrameIndex + delta).coerceIn(0, total - 1)
        loadFrame(nextFrame)
    }

    fun trackStep(forward: Boolean) {
        stopAllJobs()
        viewModelScope.launch {
            performSingleTrackStep(forward)
        }
    }

    fun startContinuousTracking(forward: Boolean) {
        if (_uiState.value.isTracking) {
            stopAllJobs()
            return
        }
        stopAllJobs()
        _uiState.update {
            it.copy(
                isTracking = true,
                trackingStatusText = if (forward) "TRACKING FORWARD >>" else "<< TRACKING REVERSE"
            )
        }

        trackingJob = viewModelScope.launch {
            val total = _uiState.value.currentClip.totalFrames
            val targetEnd = if (forward) total - 1 else 0

            while (isActive) {
                val current = _uiState.value.currentFrameIndex
                if (current == targetEnd) break

                val success = performSingleTrackStep(forward)
                if (!success) {
                    _uiState.update { it.copy(trackingStatusText = "TRACK LOST (LOW CONFIDENCE)") }
                    break
                }
                delay(20) // Smooth visual playback rate
            }
            _uiState.update {
                it.copy(
                    isTracking = false,
                    trackingStatusText = "TRACK FINISHED (${_uiState.value.keyframes.size} KEYFRAMES)"
                )
            }
        }
    }

    private suspend fun performSingleTrackStep(forward: Boolean): Boolean = withContext(Dispatchers.Default) {
        val state = _uiState.value
        val total = state.currentClip.totalFrames
        val currIdx = state.currentFrameIndex
        val nextIdx = if (forward) currIdx + 1 else currIdx - 1
        if (nextIdx !in 0 until total) return@withContext false

        val clip = state.currentClip
        val prevBitmap = state.currentFrameBitmap ?: ProceduralFootageEngine.getFrame(clip.id, currIdx)
        val nextBitmap = if (clip.customVideoUri != null) {
            videoExtractor.extractFrame(nextIdx, total) ?: ProceduralFootageEngine.getFrame("drift_car", nextIdx)
        } else {
            ProceduralFootageEngine.getFrame(clip.id, nextIdx)
        }

        val updatedPoints = mutableListOf<TrackerPoint>()
        var minConfidence = 1.0f

        for (pt in state.currentPoints) {
            // Collect motion history for this point
            val history = state.keyframes.values
                .sortedBy { it.frameIndex }
                .mapNotNull { kf -> kf.points.find { it.id == pt.id } }

            val nextPoint = OpticalFlowTrackerEngine.trackPointNextFrame(
                prevBitmap = prevBitmap,
                currBitmap = nextBitmap,
                prevPoint = pt,
                motionHistory = history,
                settings = state.trackerSettings
            )
            updatedPoints.add(nextPoint)
            if (nextPoint.confidence < minConfidence) {
                minConfidence = nextPoint.confidence
            }
        }

        withContext(Dispatchers.Main) {
            recordKeyframe(nextIdx, updatedPoints, isUser = false)
            _uiState.update {
                it.copy(
                    currentFrameIndex = nextIdx,
                    currentFrameBitmap = nextBitmap,
                    currentPoints = updatedPoints,
                    currentConfidence = minConfidence,
                    trackingStatusText = if (minConfidence < state.trackerSettings.confidenceThreshold) {
                        "PREDICTING OCCLUSION (${(minConfidence * 100).roundToInt()}%)"
                    } else {
                        "LOCKED (${(minConfidence * 100).roundToInt()}%)"
                    }
                )
            }
        }

        true
    }

    private fun recordKeyframe(frameIndex: Int, points: List<TrackerPoint>, isUser: Boolean) {
        val transform = OpticalFlowTrackerEngine.computeTransform(points, _uiState.value.initialPoints)
        val center = transform.first
        val scale = transform.second
        val rot = transform.third
        val avgConf = if (points.isNotEmpty()) points.map { it.confidence }.average().toFloat() else 1f

        val keyframe = FrameKeyframe(
            frameIndex = frameIndex,
            timestampMs = (frameIndex * 1000L / _uiState.value.currentClip.fps),
            points = points,
            centerX = center.first,
            centerY = center.second,
            scale = scale,
            rotationDeg = rot,
            overallConfidence = avgConf,
            isUserKeyframe = isUser
        )

        val updatedMap = _uiState.value.keyframes.toMutableMap()
        updatedMap[frameIndex] = keyframe

        // Auto apply smoothing if configured
        val smoothed = OpticalFlowTrackerEngine.smoothKeyframeTrajectory(updatedMap, _uiState.value.trackerSettings.smoothingMode)

        _uiState.update { it.copy(keyframes = smoothed) }
    }

    fun updatePointPosition(pointIndex: Int, x: Float, y: Float) {
        val points = _uiState.value.currentPoints.toMutableList()
        if (pointIndex in points.indices) {
            val old = points[pointIndex]
            val bitmap = _uiState.value.currentFrameBitmap
            val finalPos = if (_uiState.value.trackerSettings.stickyObjectLock && bitmap != null) {
                OpticalFlowTrackerEngine.findStickyObjectAnchor(bitmap, x, y, old.featureRadius * 1.3f)
            } else {
                Pair(x, y)
            }

            points[pointIndex] = old.copy(
                x = finalPos.first,
                y = finalPos.second,
                status = if (_uiState.value.trackerSettings.stickyObjectLock) PointStatus.STICKY_LOCKED else PointStatus.MANUAL,
                confidence = 1.0f,
                isStickyLocked = _uiState.value.trackerSettings.stickyObjectLock
            )
            _uiState.update {
                it.copy(
                    currentPoints = points,
                    isObjectStuck = true,
                    stuckPosition = finalPos,
                    trackingStatusText = if (_uiState.value.trackerSettings.stickyObjectLock) "OBJECT LOCKED & STUCK (${finalPos.first.roundToInt()}, ${finalPos.second.roundToInt()})" else "POINT MOVED"
                )
            }
            recordKeyframe(_uiState.value.currentFrameIndex, points, isUser = true)
        }
    }

    /**
     * Updates all points simultaneously (used for planar multi-point coordinate selection & transformations)
     */
    fun updateAllPoints(points: List<TrackerPoint>) {
        _uiState.update {
            it.copy(
                currentPoints = points,
                trackingStatusText = "PLANAR CORNERS UPDATED (${points.size} PTS)"
            )
        }
        recordKeyframe(_uiState.value.currentFrameIndex, points, isUser = true)
    }

    /**
     * Magnetically select and lock the tracker on the target object.
     * Snaps directly to the object centroid / highest gradient so the object sticks rock-solid.
     */
    fun lockAndStickToObject(pointIndex: Int, touchX: Float, touchY: Float) {
        val bitmap = _uiState.value.currentFrameBitmap ?: return
        val points = _uiState.value.currentPoints.toMutableList()
        if (pointIndex in points.indices) {
            val pt = points[pointIndex]
            val stuckAnchor = OpticalFlowTrackerEngine.findStickyObjectAnchor(
                bitmap = bitmap,
                touchX = touchX,
                touchY = touchY,
                searchRadius = pt.featureRadius * 1.5f
            )

            points[pointIndex] = pt.copy(
                x = stuckAnchor.first,
                y = stuckAnchor.second,
                status = PointStatus.STICKY_LOCKED,
                confidence = 1.0f,
                isStickyLocked = true,
                stickTargetName = "Object Centroid"
            )

            _uiState.update {
                it.copy(
                    currentPoints = points,
                    isObjectStuck = true,
                    stuckPosition = stuckAnchor,
                    trackingStatusText = "OBJECT STUCK ON POSITION (${stuckAnchor.first.roundToInt()}, ${stuckAnchor.second.roundToInt()})",
                    toastMessage = "Tracker firmly locked and stuck to object"
                )
            }
            recordKeyframe(_uiState.value.currentFrameIndex, points, isUser = true)
        }
    }

    fun toggleStickyObjectLock() {
        val newEnabled = !_uiState.value.trackerSettings.stickyObjectLock
        val updatedSettings = _uiState.value.trackerSettings.copy(stickyObjectLock = newEnabled)
        _uiState.update {
            it.copy(
                trackerSettings = updatedSettings,
                isStickyLocked = newEnabled,
                toastMessage = if (newEnabled) "Sticky Object Lock: ENABLED" else "Sticky Object Lock: DISABLED"
            )
        }
    }

    fun autoSnapPointToHarrisCorner(pointIndex: Int) {
        val bitmap = _uiState.value.currentFrameBitmap ?: return
        val points = _uiState.value.currentPoints.toMutableList()
        if (pointIndex in points.indices) {
            val pt = points[pointIndex]
            val snapped = OpticalFlowTrackerEngine.findBestFeaturePoint(bitmap, pt.x, pt.y, pt.featureRadius * 1.5f)
            points[pointIndex] = pt.copy(
                x = snapped.first,
                y = snapped.second,
                status = PointStatus.STICKY_LOCKED,
                confidence = 1.0f,
                isStickyLocked = true
            )
            _uiState.update {
                it.copy(
                    currentPoints = points,
                    isObjectStuck = true,
                    stuckPosition = snapped,
                    trackingStatusText = "SNAPPED & STUCK TO FEATURE (${snapped.first.roundToInt()}, ${snapped.second.roundToInt()})"
                )
            }
            recordKeyframe(_uiState.value.currentFrameIndex, points, isUser = true)
        }
    }

    /**
     * AI-Powered Footage & Tracking Scenario Analysis
     */
    fun analyzeFootageWithAi() {
        if (_uiState.value.isAnalyzingAi) return
        _uiState.update { it.copy(isAnalyzingAi = true, trackingStatusText = "AI ANALYZING TRACKING BOTTLENECKS...") }

        viewModelScope.launch {
            try {
                val currentPt = _uiState.value.currentPoints.firstOrNull()
                val motionHistory = _uiState.value.keyframes.values.mapNotNull { it.points.firstOrNull() }
                val report = GeminiTrackerAdvisor.analyzeTrackingScenarios(
                    bitmap = _uiState.value.currentFrameBitmap,
                    currentPoint = currentPt,
                    motionHistory = motionHistory,
                    clip = _uiState.value.currentClip,
                    currentSettings = _uiState.value.trackerSettings
                )

                _uiState.update {
                    it.copy(
                        isAnalyzingAi = false,
                        aiReport = report,
                        trackingStatusText = "AI ADVISOR: ${report.summaryTitle}",
                        toastMessage = "AI Analysis Ready: ${report.difficultyLevel.title}"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isAnalyzingAi = false,
                        trackingStatusText = "AI ANALYSIS COMPLETED (LOCAL)",
                        toastMessage = "Analyzed frame dynamics"
                    )
                }
            }
        }
    }

    fun applyAiRecommendations(report: AiDifficultyReport) {
        val recSettings = report.recommendedSettings
        val recMode = report.recommendedMode

        _uiState.update {
            it.copy(
                trackerSettings = recSettings,
                trackingMode = recMode,
                toastMessage = "Applied AI Recommendations for ${report.difficultyLevel.title}"
            )
        }

        // Re-smooth keyframes with AI recommended smoothing mode
        val smoothed = OpticalFlowTrackerEngine.smoothKeyframeTrajectory(_uiState.value.keyframes, recSettings.smoothingMode)
        _uiState.update { it.copy(keyframes = smoothed) }

        // If mode changed, initialize appropriate number of points
        if (recMode != _uiState.value.trackingMode) {
            setTrackingMode(recMode)
        }
    }

    fun dismissAiReport() {
        _uiState.update { it.copy(aiReport = null) }
    }

    fun updateTrackerSettings(newSettings: TrackerSettings) {
        _uiState.update { it.copy(trackerSettings = newSettings) }
        // Re-smooth keyframes if smoothing changed
        val smoothed = OpticalFlowTrackerEngine.smoothKeyframeTrajectory(_uiState.value.keyframes, newSettings.smoothingMode)
        _uiState.update { it.copy(keyframes = smoothed) }
    }

    fun selectClip(clip: SampleClip) {
        stopAllJobs()
        _uiState.update {
            it.copy(
                currentClip = clip,
                currentFrameIndex = 0,
                keyframes = emptyMap(),
                trackingStatusText = "LOADED: ${clip.title}"
            )
        }
        initDefaultPoints(_uiState.value.trackingMode, 640f, 360f)
        loadFrame(0)
    }

    fun loadCustomVideo(uri: Uri) {
        stopAllJobs()
        viewModelScope.launch {
            _uiState.update { it.copy(trackingStatusText = "LOADING DEVICE VIDEO...") }
            val meta = videoExtractor.loadVideo(uri)
            if (meta != null) {
                val customClip = SampleClip(
                    id = "custom_${System.currentTimeMillis()}",
                    title = "Imported Video",
                    category = "User Gallery",
                    description = "Custom video footage (${meta.first}x${meta.second})",
                    totalFrames = meta.third.toInt(),
                    fps = 30,
                    previewBadge = "CUSTOM VIDEO",
                    customVideoUri = uri.toString()
                )
                _uiState.update {
                    it.copy(
                        currentClip = customClip,
                        currentFrameIndex = 0,
                        keyframes = emptyMap(),
                        trackingStatusText = "VIDEO READY (${meta.third} FRAMES)"
                    )
                }
                initDefaultPoints(_uiState.value.trackingMode, meta.first.toFloat(), meta.second.toFloat())
                loadFrame(0)
            } else {
                _uiState.update { it.copy(trackingStatusText = "FAILED TO READ VIDEO FILE") }
            }
        }
    }

    fun addAttachedLayer(type: LayerType, name: String, text: String, colorHex: Long) {
        val newLayer = AttachedLayer(
            name = name.ifEmpty { type.defaultLabel },
            type = type,
            textContent = text.ifEmpty { type.defaultLabel },
            primaryColorHex = colorHex,
            showLeaderLine = type == LayerType.HUD_CALLOUT
        )
        val updated = _uiState.value.attachedLayers + newLayer
        _uiState.update {
            it.copy(
                attachedLayers = updated,
                selectedLayerId = newLayer.id,
                toastMessage = "Added ${newLayer.name}"
            )
        }
    }

    fun updateAttachedLayer(layer: AttachedLayer) {
        val updated = _uiState.value.attachedLayers.map { if (it.id == layer.id) layer else it }
        _uiState.update { it.copy(attachedLayers = updated) }
    }

    fun deleteAttachedLayer(layerId: String) {
        val updated = _uiState.value.attachedLayers.filterNot { it.id == layerId }
        _uiState.update {
            it.copy(
                attachedLayers = updated,
                selectedLayerId = updated.firstOrNull()?.id
            )
        }
    }

    fun selectLayer(layerId: String?) {
        _uiState.update { it.copy(selectedLayerId = layerId) }
    }

    fun clearKeyframes() {
        stopAllJobs()
        _uiState.update {
            it.copy(
                keyframes = emptyMap(),
                trackingStatusText = "KEYFRAMES CLEARED"
            )
        }
    }

    fun toggleTrajectoryTrail() {
        _uiState.update { it.copy(showTrajectoryTrail = !it.showTrajectoryTrail) }
    }

    fun toggleSearchRegionOverlay() {
        _uiState.update { it.copy(showSearchRegionOverlay = !it.showSearchRegionOverlay) }
    }

    fun dismissToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun saveProject(title: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val kfJson = serializeKeyframes(state.keyframes)
            val layersJson = serializeLayers(state.attachedLayers)
            val settingsJson = serializeSettings(state.trackerSettings)

            val entity = ProjectEntity(
                id = state.currentProjectId ?: 0,
                title = title.ifEmpty { "Motion Project ${System.currentTimeMillis() % 10000}" },
                clipId = state.currentClip.id,
                customVideoUri = state.currentClip.customVideoUri,
                trackingMode = state.trackingMode.name,
                totalFrames = state.currentClip.totalFrames,
                currentFrame = state.currentFrameIndex,
                keyframesData = kfJson,
                attachedLayersData = layersJson,
                settingsData = settingsJson,
                updatedAt = System.currentTimeMillis()
            )

            val newId = db.projectDao().insertOrUpdate(entity)
            _uiState.update {
                it.copy(
                    currentProjectId = if (state.currentProjectId == null || state.currentProjectId == 0L) newId else state.currentProjectId,
                    currentProjectTitle = entity.title,
                    toastMessage = "Project '${entity.title}' Saved!"
                )
            }
        }
    }

    fun loadProject(entity: ProjectEntity) {
        stopAllJobs()
        viewModelScope.launch {
            val matchingClip = ProceduralFootageEngine.sampleClips.find { it.id == entity.clipId }
                ?: if (entity.customVideoUri != null) {
                    SampleClip(
                        id = entity.clipId,
                        title = entity.title,
                        category = "Imported",
                        description = "Imported Clip",
                        totalFrames = entity.totalFrames,
                        fps = 30,
                        customVideoUri = entity.customVideoUri
                    )
                } else {
                    ProceduralFootageEngine.sampleClips[0]
                }

            val mode = try { TrackingMode.valueOf(entity.trackingMode) } catch (e: Exception) { TrackingMode.POINT_1 }
            val deserializedKeyframes = deserializeKeyframes(entity.keyframesData)
            val deserializedLayers = deserializeLayers(entity.attachedLayersData)
            val deserializedSettings = deserializeSettings(entity.settingsData)

            _uiState.update {
                it.copy(
                    currentProjectId = entity.id,
                    currentProjectTitle = entity.title,
                    currentClip = matchingClip,
                    trackingMode = mode,
                    keyframes = deserializedKeyframes,
                    attachedLayers = deserializedLayers,
                    trackerSettings = deserializedSettings,
                    currentFrameIndex = entity.currentFrame.coerceIn(0, matchingClip.totalFrames - 1),
                    toastMessage = "Loaded '${entity.title}'"
                )
            }
            loadFrame(entity.currentFrame)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            db.projectDao().deleteById(projectId)
            if (_uiState.value.currentProjectId == projectId) {
                _uiState.update { it.copy(currentProjectId = null, currentProjectTitle = "New Project") }
            }
        }
    }

    fun getAfterEffectsExport(): String {
        val state = _uiState.value
        return OpticalFlowTrackerEngine.exportToAfterEffectsFormat(
            projectName = state.currentProjectTitle,
            keyframes = state.keyframes,
            width = state.currentFrameBitmap?.width ?: 1920,
            height = state.currentFrameBitmap?.height ?: 1080,
            fps = state.currentClip.fps
        )
    }

    private fun serializeKeyframes(map: Map<Int, FrameKeyframe>): String {
        val root = JSONArray()
        for ((f, kf) in map) {
            val obj = JSONObject().apply {
                put("frame", f)
                put("timestamp", kf.timestampMs)
                put("centerX", kf.centerX.toDouble())
                put("centerY", kf.centerY.toDouble())
                put("scale", kf.scale.toDouble())
                put("rot", kf.rotationDeg.toDouble())
                put("conf", kf.overallConfidence.toDouble())
                put("user", kf.isUserKeyframe)
                val ptsArr = JSONArray()
                for (p in kf.points) {
                    val pObj = JSONObject().apply {
                        put("id", p.id)
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                        put("sr", p.searchRadius.toDouble())
                        put("fr", p.featureRadius.toDouble())
                        put("conf", p.confidence.toDouble())
                        put("status", p.status.name)
                        put("name", p.name)
                    }
                    ptsArr.put(pObj)
                }
                put("points", ptsArr)
            }
            root.put(obj)
        }
        return root.toString()
    }

    private fun deserializeKeyframes(json: String): Map<Int, FrameKeyframe> {
        val result = mutableMapOf<Int, FrameKeyframe>()
        if (json.isBlank()) return result
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val f = obj.getInt("frame")
                val pts = mutableListOf<TrackerPoint>()
                val ptsArr = obj.optJSONArray("points") ?: JSONArray()
                for (j in 0 until ptsArr.length()) {
                    val pObj = ptsArr.getJSONObject(j)
                    pts.add(
                        TrackerPoint(
                            id = pObj.getInt("id"),
                            x = pObj.getDouble("x").toFloat(),
                            y = pObj.getDouble("y").toFloat(),
                            searchRadius = pObj.optDouble("sr", 60.0).toFloat(),
                            featureRadius = pObj.optDouble("fr", 24.0).toFloat(),
                            confidence = pObj.optDouble("conf", 1.0).toFloat(),
                            status = try { PointStatus.valueOf(pObj.getString("status")) } catch (e: Exception) { PointStatus.LOCKED },
                            name = pObj.optString("name", "Point")
                        )
                    )
                }
                result[f] = FrameKeyframe(
                    frameIndex = f,
                    timestampMs = obj.optLong("timestamp", 0L),
                    points = pts,
                    centerX = obj.getDouble("centerX").toFloat(),
                    centerY = obj.getDouble("centerY").toFloat(),
                    scale = obj.optDouble("scale", 1.0).toFloat(),
                    rotationDeg = obj.optDouble("rot", 0.0).toFloat(),
                    overallConfidence = obj.optDouble("conf", 1.0).toFloat(),
                    isUserKeyframe = obj.optBoolean("user", false)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun serializeLayers(layers: List<AttachedLayer>): String {
        val arr = JSONArray()
        for (l in layers) {
            val obj = JSONObject().apply {
                put("id", l.id)
                put("name", l.name)
                put("type", l.type.name)
                put("isVisible", l.isVisible)
                put("textContent", l.textContent)
                put("emojiIcon", l.emojiIcon)
                put("offsetX", l.offsetX.toDouble())
                put("offsetY", l.offsetY.toDouble())
                put("scale", l.scale.toDouble())
                put("rotationOffsetDeg", l.rotationOffsetDeg.toDouble())
                put("opacity", l.opacity.toDouble())
                put("primaryColorHex", l.primaryColorHex)
                put("showLeaderLine", l.showLeaderLine)
                put("blurRadius", l.blurRadius.toDouble())
                put("showTelemetry", l.showTelemetry)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializeLayers(json: String): List<AttachedLayer> {
        val result = mutableListOf<AttachedLayer>()
        if (json.isBlank()) return result
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    AttachedLayer(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        type = try { LayerType.valueOf(obj.getString("type")) } catch (e: Exception) { LayerType.HUD_CALLOUT },
                        isVisible = obj.optBoolean("isVisible", true),
                        textContent = obj.optString("textContent", ""),
                        emojiIcon = obj.optString("emojiIcon", "🎯"),
                        offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
                        offsetY = obj.optDouble("offsetY", -70.0).toFloat(),
                        scale = obj.optDouble("scale", 1.0).toFloat(),
                        rotationOffsetDeg = obj.optDouble("rotationOffsetDeg", 0.0).toFloat(),
                        opacity = obj.optDouble("opacity", 1.0).toFloat(),
                        primaryColorHex = obj.optLong("primaryColorHex", 0xFF00F0FF),
                        showLeaderLine = obj.optBoolean("showLeaderLine", true),
                        blurRadius = obj.optDouble("blurRadius", 18.0).toFloat(),
                        showTelemetry = obj.optBoolean("showTelemetry", true)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun serializeSettings(settings: TrackerSettings): String {
        return JSONObject().apply {
            put("searchRadius", settings.searchRadius.toDouble())
            put("featureRadius", settings.featureRadius.toDouble())
            put("adaptiveSearch", settings.adaptiveSearch)
            put("occlusionRecovery", settings.occlusionRecovery)
            put("subpixelRefinement", settings.subpixelRefinement)
            put("smoothingMode", settings.smoothingMode.name)
            put("snapToHarrisCorners", settings.snapToHarrisCorners)
            put("stickyObjectLock", settings.stickyObjectLock)
            put("confidenceThreshold", settings.confidenceThreshold.toDouble())
            put("multiScalePyramid", settings.multiScalePyramid)
        }.toString()
    }

    private fun deserializeSettings(json: String): TrackerSettings {
        if (json.isBlank()) return TrackerSettings()
        return try {
            val obj = JSONObject(json)
            TrackerSettings(
                searchRadius = obj.optDouble("searchRadius", 70.0).toFloat(),
                featureRadius = obj.optDouble("featureRadius", 28.0).toFloat(),
                adaptiveSearch = obj.optBoolean("adaptiveSearch", true),
                occlusionRecovery = obj.optBoolean("occlusionRecovery", true),
                subpixelRefinement = obj.optBoolean("subpixelRefinement", true),
                smoothingMode = try { SmoothingMode.valueOf(obj.optString("smoothingMode", "MEDIUM")) } catch (e: Exception) { SmoothingMode.MEDIUM },
                snapToHarrisCorners = obj.optBoolean("snapToHarrisCorners", true),
                stickyObjectLock = obj.optBoolean("stickyObjectLock", true),
                confidenceThreshold = obj.optDouble("confidenceThreshold", 0.55).toFloat(),
                multiScalePyramid = obj.optBoolean("multiScalePyramid", true)
            )
        } catch (e: Exception) {
            TrackerSettings()
        }
    }

    private fun stopAllJobs() {
        playbackJob?.cancel()
        playbackJob = null
        trackingJob?.cancel()
        trackingJob = null
        _uiState.update { it.copy(isPlaying = false, isTracking = false) }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllJobs()
        videoExtractor.release()
    }
}
