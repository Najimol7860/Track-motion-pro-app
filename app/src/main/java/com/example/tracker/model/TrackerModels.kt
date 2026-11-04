package com.example.tracker.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.AmberKeyframe
import com.example.ui.theme.VioletLayer
import com.example.ui.theme.CrimsonReticle
import com.example.ui.theme.EmeraldLocked

enum class TrackingMode(val displayName: String, val pointCount: Int, val description: String) {
    POINT_1("1-Point (Position)", 1, "Track single feature anchor (X, Y)"),
    TRANSFORM_2("2-Point (Pos/Rot/Scale)", 2, "Track 2 points for translation, rotation angle, and scale"),
    PLANAR_4("4-Point (Corner Pin)", 4, "Track 4 corners for perspective surface replacement"),
    ROI_BOX("ROI Bounding Box", 1, "Track target area with adaptive multi-scale cross-correlation")
}

enum class PointStatus {
    LOCKED,      // High confidence match
    PREDICTED,   // Occluded/low contrast; Kalman velocity predictor active
    LOST,        // Confidence below minimum threshold
    MANUAL,      // Manually adjusted by user
    STICKY_LOCKED // Firmly stuck to object feature anchor
}

data class TrackerPoint(
    val id: Int = 0,
    val x: Float, // Normalized (0.0 to 1.0) or Canvas pixel coordinate
    val y: Float,
    val searchRadius: Float = 60f,  // Search window radius in px
    val featureRadius: Float = 24f, // Feature template radius in px
    val confidence: Float = 1.0f,   // 0.0 to 1.0
    val status: PointStatus = PointStatus.LOCKED,
    val name: String = "Track Point $id",
    val isStickyLocked: Boolean = false,
    val stickTargetName: String? = null
)

data class FrameKeyframe(
    val frameIndex: Int,
    val timestampMs: Long,
    val points: List<TrackerPoint>,
    val centerX: Float,
    val centerY: Float,
    val scale: Float = 1.0f,
    val rotationDeg: Float = 0.0f,
    val overallConfidence: Float = 1.0f,
    val isUserKeyframe: Boolean = false
)

enum class LayerType(val title: String, val defaultLabel: String) {
    HUD_CALLOUT("Cyber HUD Callout", "TARGET // 01"),
    TEXT_3D("Floating 3D Text", "MOTION LOCKED"),
    PRIVACY_BLUR("Privacy Censor Mask", "BLUR MASK"),
    AURA_GLOW("Energy Ring / Aura", "PULSE RING"),
    STICKER_EMOJI("VFX Badge / Sticker", "🚀"),
    NULL_AXIS("3D Null Object Gimbal", "NULL 1")
}

data class AttachedLayer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: LayerType,
    val isVisible: Boolean = true,
    val textContent: String = "",
    val emojiIcon: String = "🎯",
    val offsetX: Float = 0f,
    val offsetY: Float = -70f,
    val scale: Float = 1.0f,
    val rotationOffsetDeg: Float = 0f,
    val opacity: Float = 1.0f,
    val primaryColorHex: Long = 0xFF00F0FF,
    val showLeaderLine: Boolean = true,
    val blurRadius: Float = 18f,
    val showTelemetry: Boolean = true
)

enum class SmoothingMode(val label: String, val strength: Float) {
    RAW("Raw (No Smoothing)", 0.0f),
    LIGHT("Light (3-Frame Filter)", 0.3f),
    MEDIUM("Medium (5-Frame Gaussian)", 0.6f),
    HEAVY("Heavy (9-Frame Damped)", 0.9f)
}

data class TrackerSettings(
    val searchRadius: Float = 70f,
    val featureRadius: Float = 28f,
    val adaptiveSearch: Boolean = true, // Auto-expands window during high velocity
    val occlusionRecovery: Boolean = true, // Kalman motion momentum predictor
    val subpixelRefinement: Boolean = true, // Bilinear parabolic refinement
    val smoothingMode: SmoothingMode = SmoothingMode.MEDIUM,
    val snapToHarrisCorners: Boolean = true, // Snaps crosshair to strongest gradient
    val stickyObjectLock: Boolean = true, // Automatically anchors and sticks rigidly to target object
    val confidenceThreshold: Float = 0.55f,
    val multiScalePyramid: Boolean = true
)

enum class TrackingDifficultyLevel(val title: String, val badgeColor: Long) {
    OPTIMAL("Optimal Tracking", 0xFF10B981),
    MODERATE("Moderate Challenge", 0xFF00F0FF),
    DIFFICULT("Difficult Scenario", 0xFFF59E0B),
    EXTREME("Extreme Tracking Challenge", 0xFFF43F5E)
}

data class TrackingChallengeTag(
    val id: String,
    val title: String,
    val category: String,
    val severity: String, // "HIGH", "MODERATE", "LOW"
    val description: String,
    val recommendedFix: String
)

data class AiDifficultyReport(
    val difficultyLevel: TrackingDifficultyLevel = TrackingDifficultyLevel.OPTIMAL,
    val difficultyScore: Float = 2.5f, // 0.0 to 10.0 scale
    val summaryTitle: String = "Footage Analyzed",
    val summaryAnalysis: String = "Object features detected with good contrast.",
    val workflowAdvice: String = "Track anchor locked to high-contrast corner.",
    val detectedChallenges: List<TrackingChallengeTag> = emptyList(),
    val recommendedSettings: TrackerSettings = TrackerSettings(),
    val recommendedMode: TrackingMode = TrackingMode.POINT_1,
    val confidenceEstimate: Float = 0.92f,
    val timestamp: Long = System.currentTimeMillis()
)

data class SampleClip(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val totalFrames: Int = 120,
    val fps: Int = 30,
    val previewBadge: String = "FAST MOTION",
    val customVideoUri: String? = null
)
