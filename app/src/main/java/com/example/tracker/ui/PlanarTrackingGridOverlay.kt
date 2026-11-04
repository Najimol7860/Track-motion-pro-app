package com.example.tracker.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.engine.OpticalFlowTrackerEngine
import com.example.tracker.model.TrackerPoint
import com.example.ui.theme.*
import kotlin.math.*

/**
 * Interaction Mode for Planar Tracking Coordinate Selection
 */
enum class PlanarInteractionMode(val title: String, val icon: String) {
    EDIT_CORNERS("Corner Pin", "🎯"),
    SEQUENTIAL_PLACEMENT("Pick 4 Points", "✏️"),
    TRANSLATE_PLANE("Move Plane", "✥"),
    TRANSFORM_PLANE("Scale & Rot", "🔄")
}

/**
 * Grid Density Presets for the Dynamic Perspective Overlay
 */
enum class PlanarGridDensity(val divisions: Int, val label: String) {
    OFF(0, "Grid Off"),
    COARSE(4, "4 × 4"),
    MEDIUM(8, "8 × 8"),
    FINE(12, "12 × 12"),
    ULTRA(16, "16 × 16")
}

/**
 * Presets for Quick Planar Quad Geometry Setup
 */
enum class PlanarPreset(val label: String, val icon: String) {
    SCREEN_16_9("16:9 Screen", "🖥️"),
    SCREEN_9_16("9:16 Mobile", "📱"),
    BILLBOARD_TRAPEZOID("Wall Perspective", "🏢"),
    ISOMETRIC_FLOOR("Floor Surface", "📐"),
    FILL_FRAME("Frame Bounds", "⛶")
}

/**
 * Custom Compose View that renders a high-precision dynamic perspective grid overlay
 * and handles multi-point coordinate selection for planar tracking input.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun PlanarTrackingGridOverlay(
    points: List<TrackerPoint>,
    onPointsChanged: (List<TrackerPoint>) -> Unit,
    frameBitmap: Bitmap? = null,
    modifier: Modifier = Modifier,
    initialGridDensity: PlanarGridDensity = PlanarGridDensity.MEDIUM,
    showBackgroundRefGrid: Boolean = true,
    showSurfaceNormal: Boolean = true,
    showScanlineAnimation: Boolean = true,
    enableLoupeMagnifier: Boolean = true,
    enableHarrisCornerSnapping: Boolean = true,
    onPointTapped: ((Int) -> Unit)? = null
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Internal State
    var interactionMode by remember { mutableStateOf(PlanarInteractionMode.EDIT_CORNERS) }
    var gridDensity by remember { mutableStateOf(initialGridDensity) }
    var isMagnifierEnabled by remember { mutableStateOf(enableLoupeMagnifier) }
    var isSnappingEnabled by remember { mutableStateOf(enableHarrisCornerSnapping) }
    var isScanningEnabled by remember { mutableStateOf(showScanlineAnimation) }
    var isMetricsExpanded by remember { mutableStateOf(false) }

    // Active Drag & Loupe tracking
    var draggingPointIndex by remember { mutableStateOf<Int?>(null) }
    var dragTouchScreenOffset by remember { mutableStateOf<Offset?>(null) }
    var selectedPointIndex by remember { mutableStateOf(0) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Sequential point picking state (when in SEQUENTIAL_PLACEMENT mode)
    val sequentialPoints = remember { mutableStateListOf<Offset>() }

    // Transform drag start anchors for MOVE_PLANE / TRANSFORM_PLANE
    var planeDragStartOffset by remember { mutableStateOf<Offset?>(null) }
    var planeDragInitialPoints by remember { mutableStateOf<List<TrackerPoint>>(emptyList()) }

    // Animated Scanline Sweep across the planar surface
    val infiniteTransition = rememberInfiniteTransition(label = "PlanarScanline")
    val scanPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ScanPhase"
    )

    // Animated glow pulse for active vertex reticles
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseGlow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("planar_tracking_grid_overlay")
    ) {
        // Main Interactive Planar Canvas View
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("planar_canvas_viewport")
                .pointerInput(points, interactionMode, isSnappingEnabled, frameBitmap) {
                    detectTapGestures { tapPos ->
                        if (canvasSize.width <= 0f || canvasSize.height <= 0f) return@detectTapGestures

                        val scaleX = if (frameBitmap != null && frameBitmap.width > 0) canvasSize.width / frameBitmap.width.toFloat() else 1f
                        val scaleY = if (frameBitmap != null && frameBitmap.height > 0) canvasSize.height / frameBitmap.height.toFloat() else 1f

                        when (interactionMode) {
                            PlanarInteractionMode.SEQUENTIAL_PLACEMENT -> {
                                // Add point to sequential list
                                val bitmapX = (tapPos.x / scaleX).coerceIn(0f, (frameBitmap?.width?.toFloat() ?: canvasSize.width))
                                val bitmapY = (tapPos.y / scaleY).coerceIn(0f, (frameBitmap?.height?.toFloat() ?: canvasSize.height))

                                val finalPos = if (isSnappingEnabled && frameBitmap != null) {
                                    OpticalFlowTrackerEngine.findBestFeaturePoint(frameBitmap, bitmapX, bitmapY, roiRadius = 24f)
                                } else {
                                    Pair(bitmapX, bitmapY)
                                }

                                sequentialPoints.add(Offset(finalPos.first, finalPos.second))

                                if (sequentialPoints.size == 4) {
                                    // Finished 4-point placement! Commit to points
                                    val newPoints = sequentialPoints.mapIndexed { idx, offset ->
                                        val name = when (idx) {
                                            0 -> "Top Left"
                                            1 -> "Top Right"
                                            2 -> "Bottom Right"
                                            else -> "Bottom Left"
                                        }
                                        TrackerPoint(id = idx, x = offset.x, y = offset.y, name = name)
                                    }
                                    onPointsChanged(newPoints)
                                    sequentialPoints.clear()
                                    interactionMode = PlanarInteractionMode.EDIT_CORNERS
                                }
                            }
                            else -> {
                                // Check if tapped near an existing corner
                                val hitIdx = points.indexOfFirst { pt ->
                                    val sx = pt.x * scaleX
                                    val sy = pt.y * scaleY
                                    hypot((tapPos.x - sx).toDouble(), (tapPos.y - sy).toDouble()) < 50f
                                }

                                if (hitIdx != -1) {
                                    selectedPointIndex = hitIdx
                                    onPointTapped?.invoke(hitIdx)
                                } else if (points.size >= 4) {
                                    // Check if tapped inside quad -> select plane or closest point
                                    val closestIdx = points.indices.minByOrNull { idx ->
                                        val sx = points[idx].x * scaleX
                                        val sy = points[idx].y * scaleY
                                        hypot((tapPos.x - sx).toDouble(), (tapPos.y - sy).toDouble())
                                    } ?: 0
                                    selectedPointIndex = closestIdx
                                }
                            }
                        }
                    }
                }
                .pointerInput(points, interactionMode, isSnappingEnabled, frameBitmap) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            if (canvasSize.width <= 0f || canvasSize.height <= 0f) return@detectDragGestures
                            val scaleX = if (frameBitmap != null && frameBitmap.width > 0) canvasSize.width / frameBitmap.width.toFloat() else 1f
                            val scaleY = if (frameBitmap != null && frameBitmap.height > 0) canvasSize.height / frameBitmap.height.toFloat() else 1f

                            when (interactionMode) {
                                PlanarInteractionMode.EDIT_CORNERS -> {
                                    val hitIdx = points.indexOfFirst { pt ->
                                        val sx = pt.x * scaleX
                                        val sy = pt.y * scaleY
                                        hypot((startOffset.x - sx).toDouble(), (startOffset.y - sy).toDouble()) < 64f
                                    }
                                    val targetIdx = if (hitIdx != -1) hitIdx else selectedPointIndex
                                    draggingPointIndex = targetIdx
                                    selectedPointIndex = targetIdx
                                    dragTouchScreenOffset = startOffset
                                }
                                PlanarInteractionMode.TRANSLATE_PLANE, PlanarInteractionMode.TRANSFORM_PLANE -> {
                                    planeDragStartOffset = startOffset
                                    planeDragInitialPoints = points.map { it.copy() }
                                }
                                else -> Unit
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (canvasSize.width <= 0f || canvasSize.height <= 0f) return@detectDragGestures
                            val scaleX = if (frameBitmap != null && frameBitmap.width > 0) canvasSize.width / frameBitmap.width.toFloat() else 1f
                            val scaleY = if (frameBitmap != null && frameBitmap.height > 0) canvasSize.height / frameBitmap.height.toFloat() else 1f

                            when (interactionMode) {
                                PlanarInteractionMode.EDIT_CORNERS -> {
                                    val idx = draggingPointIndex ?: return@detectDragGestures
                                    dragTouchScreenOffset = change.position

                                    val rawBitmapX = (change.position.x / scaleX).coerceIn(0f, (frameBitmap?.width?.toFloat() ?: canvasSize.width))
                                    val rawBitmapY = (change.position.y / scaleY).coerceIn(0f, (frameBitmap?.height?.toFloat() ?: canvasSize.height))

                                    val finalPos = if (isSnappingEnabled && frameBitmap != null) {
                                        OpticalFlowTrackerEngine.findBestFeaturePoint(frameBitmap, rawBitmapX, rawBitmapY, roiRadius = 20f)
                                    } else {
                                        Pair(rawBitmapX, rawBitmapY)
                                    }

                                    val updated = points.toMutableList()
                                    if (idx in updated.indices) {
                                        updated[idx] = updated[idx].copy(x = finalPos.first, y = finalPos.second)
                                        onPointsChanged(updated)
                                    }
                                }
                                PlanarInteractionMode.TRANSLATE_PLANE -> {
                                    val start = planeDragStartOffset ?: return@detectDragGestures
                                    val deltaX = (change.position.x - start.x) / scaleX
                                    val deltaY = (change.position.y - start.y) / scaleY

                                    val updated = planeDragInitialPoints.map { pt ->
                                        pt.copy(
                                            x = (pt.x + deltaX).coerceIn(0f, (frameBitmap?.width?.toFloat() ?: canvasSize.width)),
                                            y = (pt.y + deltaY).coerceIn(0f, (frameBitmap?.height?.toFloat() ?: canvasSize.height))
                                        )
                                    }
                                    onPointsChanged(updated)
                                }
                                PlanarInteractionMode.TRANSFORM_PLANE -> {
                                    val start = planeDragStartOffset ?: return@detectDragGestures
                                    if (planeDragInitialPoints.isEmpty()) return@detectDragGestures

                                    // Compute center of initial points
                                    val cx = planeDragInitialPoints.map { it.x }.average().toFloat()
                                    val cy = planeDragInitialPoints.map { it.y }.average().toFloat()
                                    val screenCx = cx * scaleX
                                    val screenCy = cy * scaleY

                                    val initialDist = hypot((start.x - screenCx).toDouble(), (start.y - screenCy).toDouble()).toFloat().coerceAtLeast(10f)
                                    val currDist = hypot((change.position.x - screenCx).toDouble(), (change.position.y - screenCy).toDouble()).toFloat().coerceAtLeast(10f)
                                    val scaleFactor = (currDist / initialDist).coerceIn(0.2f, 4.0f)

                                    val initialAngle = atan2((start.y - screenCy).toDouble(), (start.x - screenCx).toDouble()).toFloat()
                                    val currAngle = atan2((change.position.y - screenCy).toDouble(), (change.position.x - screenCx).toDouble()).toFloat()
                                    val angleDelta = currAngle - initialAngle

                                    val updated = planeDragInitialPoints.map { pt ->
                                        val relX = pt.x - cx
                                        val relY = pt.y - cy
                                        val scaledX = relX * scaleFactor
                                        val scaledY = relY * scaleFactor
                                        val cosA = cos(angleDelta.toDouble()).toFloat()
                                        val sinA = sin(angleDelta.toDouble()).toFloat()
                                        val rotX = scaledX * cosA - scaledY * sinA
                                        val rotY = scaledX * sinA + scaledY * cosA
                                        pt.copy(x = cx + rotX, y = cy + rotY)
                                    }
                                    onPointsChanged(updated)
                                }
                                else -> Unit
                            }
                        },
                        onDragEnd = {
                            draggingPointIndex = null
                            dragTouchScreenOffset = null
                            planeDragStartOffset = null
                        },
                        onDragCancel = {
                            draggingPointIndex = null
                            dragTouchScreenOffset = null
                            planeDragStartOffset = null
                        }
                    )
                }
        ) {
            canvasSize = size
            if (size.width <= 0f || size.height <= 0f) return@Canvas

            val scaleX = if (frameBitmap != null && frameBitmap.width > 0) size.width / frameBitmap.width.toFloat() else 1f
            val scaleY = if (frameBitmap != null && frameBitmap.height > 0) size.height / frameBitmap.height.toFloat() else 1f

            // 1. Draw Global Reference Grid (Cartesian alignment ticks)
            if (showBackgroundRefGrid) {
                drawGlobalReferenceGrid(size)
            }

            // 2. Sequential Placement Rubber Banding
            if (interactionMode == PlanarInteractionMode.SEQUENTIAL_PLACEMENT) {
                drawSequentialPlacementPreview(
                    placedPoints = sequentialPoints,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    pulseGlow = pulseGlow,
                    textMeasurer = textMeasurer
                )
            }

            // 3. Draw Dynamic Perspective Quad Grid Overlay
            if (points.size >= 4) {
                val screenPoints = points.take(4).map { Offset(it.x * scaleX, it.y * scaleY) }

                // Check convexity / bowtie self-intersection
                val isConvex = isPlanarQuadConvex(screenPoints)

                // Planar Surface Fill & Border
                drawPlanarSurfaceMesh(
                    points = screenPoints,
                    isConvex = isConvex,
                    gridDensity = gridDensity,
                    scanPhase = if (isScanningEnabled) scanPhase else 0f,
                    pulseGlow = pulseGlow,
                    textMeasurer = textMeasurer
                )

                // Surface Normal 3D Axis
                if (showSurfaceNormal && isConvex) {
                    drawPlanarSurfaceNormal(screenPoints, textMeasurer)
                }

                // Quad Edge Dimensions & Interior Angles
                drawPlanarEdgeDimensions(screenPoints, textMeasurer)

                // 4. Interactive Corner Pin Handles & Reticles
                for ((idx, pt) in points.take(4).withIndex()) {
                    val sx = pt.x * scaleX
                    val sy = pt.y * scaleY
                    val isDragging = draggingPointIndex == idx
                    val isSelected = selectedPointIndex == idx

                    drawPlanarCornerReticle(
                        index = idx,
                        name = pt.name,
                        screenX = sx,
                        screenY = sy,
                        isDragging = isDragging,
                        isSelected = isSelected,
                        pulseGlow = pulseGlow,
                        textMeasurer = textMeasurer
                    )
                }

                // 5. Centroid Pivot for Plane Translation / Transformation
                val centroid = computePlanarCentroid(screenPoints)
                drawPlanarCentroidPivot(
                    centroid = centroid,
                    mode = interactionMode,
                    pulseGlow = pulseGlow,
                    textMeasurer = textMeasurer
                )
            }

            // 6. Draw Precision Loupe / Magnifier Lens if actively dragging a point
            if (isMagnifierEnabled && draggingPointIndex != null && dragTouchScreenOffset != null && frameBitmap != null) {
                val ptIdx = draggingPointIndex ?: 0
                if (ptIdx in points.indices) {
                    val activePt = points[ptIdx]
                    val touchOffset = dragTouchScreenOffset ?: Offset.Zero
                    drawPrecisionLoupe(
                        frameBitmap = frameBitmap,
                        point = activePt,
                        touchOffset = touchOffset,
                        canvasSize = size,
                        scaleX = scaleX,
                        scaleY = scaleY,
                        textMeasurer = textMeasurer
                    )
                }
            }
        }

        // Top Planar Studio HUD Overlay (Mode selector & grid controls)
        PlanarTopControlOverlay(
            interactionMode = interactionMode,
            onModeChanged = { mode ->
                if (mode == PlanarInteractionMode.SEQUENTIAL_PLACEMENT) {
                    sequentialPoints.clear()
                }
                interactionMode = mode
            },
            gridDensity = gridDensity,
            onDensityChanged = { gridDensity = it },
            isSnappingEnabled = isSnappingEnabled,
            onToggleSnapping = { isSnappingEnabled = !isSnappingEnabled },
            isMagnifierEnabled = isMagnifierEnabled,
            onToggleMagnifier = { isMagnifierEnabled = !isMagnifierEnabled },
            isScanningEnabled = isScanningEnabled,
            onToggleScanning = { isScanningEnabled = !isScanningEnabled },
            isMetricsExpanded = isMetricsExpanded,
            onToggleMetrics = { isMetricsExpanded = !isMetricsExpanded },
            onApplyPreset = { preset ->
                applyPlanarPreset(preset, canvasSize, frameBitmap, onPointsChanged)
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp, start = 10.dp, end = 10.dp)
        )

        // Bottom Collapsible Planar Telemetry & Coordinate Inspector HUD
        if (isMetricsExpanded && points.size >= 4) {
            PlanarMetricsInspectorCard(
                points = points,
                frameBitmap = frameBitmap,
                selectedIndex = selectedPointIndex,
                onSelectIndex = { selectedPointIndex = it },
                onNudgePoint = { idx, dx, dy ->
                    val updated = points.toMutableList()
                    if (idx in updated.indices) {
                        val maxW = frameBitmap?.width?.toFloat() ?: canvasSize.width
                        val maxH = frameBitmap?.height?.toFloat() ?: canvasSize.height
                        updated[idx] = updated[idx].copy(
                            x = (updated[idx].x + dx).coerceIn(0f, maxW),
                            y = (updated[idx].y + dy).coerceIn(0f, maxH)
                        )
                        onPointsChanged(updated)
                    }
                },
                onSnapSelectedCorner = {
                    if (frameBitmap != null && selectedPointIndex in points.indices) {
                        val pt = points[selectedPointIndex]
                        val snapped = OpticalFlowTrackerEngine.findBestFeaturePoint(frameBitmap, pt.x, pt.y, 30f)
                        val updated = points.toMutableList()
                        updated[selectedPointIndex] = pt.copy(x = snapped.first, y = snapped.second)
                        onPointsChanged(updated)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
            )
        }

        // Sequential Mode Instruction Prompt Banner
        if (interactionMode == PlanarInteractionMode.SEQUENTIAL_PLACEMENT) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .testTag("sequential_instruction_banner"),
                shape = RoundedCornerShape(24.dp),
                color = AeSlate900.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CyanNeon),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val stepNames = listOf("Top-Left (TL)", "Top-Right (TR)", "Bottom-Right (BR)", "Bottom-Left (BL)")
                    val currentStep = sequentialPoints.size
                    CircularProgressIndicator(
                        progress = { (currentStep / 4f) },
                        modifier = Modifier.size(18.dp),
                        color = CyanNeon,
                        strokeWidth = 2.5.dp,
                        trackColor = AeSlate800
                    )
                    Text(
                        text = if (currentStep < 4) "Tap corner ${currentStep + 1}/4: ${stepNames[currentStep]}" else "Planar Surface Locked!",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            sequentialPoints.clear()
                            interactionMode = PlanarInteractionMode.EDIT_CORNERS
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Cancel", color = CrimsonReticle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// CANVAS DRAWING IMPLEMENTATIONS
// -----------------------------------------------------------------------------------------

/**
 * Draws background reference grid with subtle cartesian ticks and center cross
 */
private fun DrawScope.drawGlobalReferenceGrid(size: Size) {
    val step = 40f
    val gridColor = AeSlate700.copy(alpha = 0.22f)
    val majorGridColor = CyanNeon.copy(alpha = 0.15f)

    // Vertical lines
    var x = 0f
    var colIdx = 0
    while (x <= size.width) {
        val col = if (colIdx % 4 == 0) majorGridColor else gridColor
        drawLine(
            color = col,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = if (colIdx % 4 == 0) 1.2f else 0.8f
        )
        x += step
        colIdx++
    }

    // Horizontal lines
    var y = 0f
    var rowIdx = 0
    while (y <= size.height) {
        val col = if (rowIdx % 4 == 0) majorGridColor else gridColor
        drawLine(
            color = col,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = if (rowIdx % 4 == 0) 1.2f else 0.8f
        )
        y += step
        rowIdx++
    }

    // Center Crosshairs
    val midX = size.width * 0.5f
    val midY = size.height * 0.5f
    drawLine(
        color = AmberKeyframe.copy(alpha = 0.35f),
        start = Offset(midX - 20f, midY),
        end = Offset(midX + 20f, midY),
        strokeWidth = 1.5f
    )
    drawLine(
        color = AmberKeyframe.copy(alpha = 0.35f),
        start = Offset(midX, midY - 20f),
        end = Offset(midX, midY + 20f),
        strokeWidth = 1.5f
    )
}

/**
 * Evaluates Bilinear Interpolation for 4 corner coordinates at (u, v):
 * P(u, v) = (1-u)(1-v)*P0 + u(1-v)*P1 + u*v*P2 + (1-u)*v*P3
 * Order: P0: TL, P1: TR, P2: BR, P3: BL
 */
fun evaluatePlanarBilinear(
    p0: Offset,
    p1: Offset,
    p2: Offset,
    p3: Offset,
    u: Float,
    v: Float
): Offset {
    val oneMinusU = 1f - u
    val oneMinusV = 1f - v

    val w0 = oneMinusU * oneMinusV
    val w1 = u * oneMinusV
    val w2 = u * v
    val w3 = oneMinusU * v

    val x = w0 * p0.x + w1 * p1.x + w2 * p2.x + w3 * p3.x
    val y = w0 * p0.y + w1 * p1.y + w2 * p2.y + w3 * p3.y

    return Offset(x, y)
}

/**
 * Draws the dynamic perspective mesh surface, subdivision grid, and animated scanline
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPlanarSurfaceMesh(
    points: List<Offset>,
    isConvex: Boolean,
    gridDensity: PlanarGridDensity,
    scanPhase: Float,
    pulseGlow: Float,
    textMeasurer: TextMeasurer
) {
    val p0 = points[0] // TL
    val p1 = points[1] // TR
    val p2 = points[2] // BR
    val p3 = points[3] // BL

    val quadPath = Path().apply {
        moveTo(p0.x, p0.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        lineTo(p3.x, p3.y)
        close()
    }

    val primaryColor = if (isConvex) CyanNeon else CrimsonReticle
    val secondaryColor = if (isConvex) VioletLayerGlow else AmberKeyframe

    // 1. Semi-transparent surface fill with futuristic gradient
    drawPath(
        path = quadPath,
        brush = Brush.radialGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.16f),
                secondaryColor.copy(alpha = 0.08f),
                Color.Transparent
            ),
            center = evaluatePlanarBilinear(p0, p1, p2, p3, 0.5f, 0.5f),
            radius = hypot((p2.x - p0.x).toDouble(), (p2.y - p0.y).toDouble()).toFloat() * 0.6f
        )
    )

    // 2. Outer Perimeter Border (Glowing Cyber Line)
    drawPath(
        path = quadPath,
        color = primaryColor.copy(alpha = 0.35f),
        style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = quadPath,
        color = primaryColor,
        style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 3. Perspective Subdivision Grid Lines
    val divisions = gridDensity.divisions
    if (divisions > 0 && isConvex) {
        val stepsPerLine = 12

        // U-lines (connecting top and bottom edges)
        for (i in 1 until divisions) {
            val u = i.toFloat() / divisions
            val isCenterU = (i * 2 == divisions)
            val linePath = Path()

            for (s in 0..stepsPerLine) {
                val v = s.toFloat() / stepsPerLine
                val pt = evaluatePlanarBilinear(p0, p1, p2, p3, u, v)
                if (s == 0) linePath.moveTo(pt.x, pt.y) else linePath.lineTo(pt.x, pt.y)
            }

            val strokeCol = if (isCenterU) AmberKeyframe else primaryColor.copy(alpha = 0.45f)
            val strokeW = if (isCenterU) 2.2f else 1.0f

            drawPath(
                path = linePath,
                color = strokeCol,
                style = Stroke(
                    width = strokeW,
                    pathEffect = if (!isCenterU && divisions >= 8) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
                )
            )
        }

        // V-lines (connecting left and right edges)
        for (j in 1 until divisions) {
            val v = j.toFloat() / divisions
            val isCenterV = (j * 2 == divisions)
            val linePath = Path()

            for (s in 0..stepsPerLine) {
                val u = s.toFloat() / stepsPerLine
                val pt = evaluatePlanarBilinear(p0, p1, p2, p3, u, v)
                if (s == 0) linePath.moveTo(pt.x, pt.y) else linePath.lineTo(pt.x, pt.y)
            }

            val strokeCol = if (isCenterV) EmeraldLocked else primaryColor.copy(alpha = 0.45f)
            val strokeW = if (isCenterV) 2.2f else 1.0f

            drawPath(
                path = linePath,
                color = strokeCol,
                style = Stroke(
                    width = strokeW,
                    pathEffect = if (!isCenterV && divisions >= 8) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null
                )
            )
        }
    }

    // 4. Perspective Vanishing Diagonals
    drawLine(
        color = secondaryColor.copy(alpha = 0.35f),
        start = p0,
        end = p2,
        strokeWidth = 1.2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    )
    drawLine(
        color = secondaryColor.copy(alpha = 0.35f),
        start = p1,
        end = p3,
        strokeWidth = 1.2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    )

    // 5. Animated Radar / Cyber Laser Scanline sweep across V-axis
    if (scanPhase > 0f && isConvex) {
        val scanPath = Path()
        val steps = 14
        for (s in 0..steps) {
            val u = s.toFloat() / steps
            val pt = evaluatePlanarBilinear(p0, p1, p2, p3, u, scanPhase)
            if (s == 0) scanPath.moveTo(pt.x, pt.y) else scanPath.lineTo(pt.x, pt.y)
        }

        // Glow trail
        drawPath(
            path = scanPath,
            color = CyanGlow.copy(alpha = 0.75f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        drawPath(
            path = scanPath,
            color = Color.White,
            style = Stroke(width = 1.8f, cap = StrokeCap.Round)
        )
    }

    // Bowtie Warning if quad is inverted or self-intersecting
    if (!isConvex) {
        val center = evaluatePlanarBilinear(p0, p1, p2, p3, 0.5f, 0.5f)
        val warnLayout = textMeasurer.measure(
            text = AnnotatedString("⚠️ SELF-INTERSECTING / NON-CONVEX QUAD"),
            style = TextStyle(color = CrimsonReticle, fontSize = 11.sp, fontWeight = FontWeight.Black)
        )
        drawRoundRect(
            color = AeSlate950.copy(alpha = 0.9f),
            topLeft = Offset(center.x - warnLayout.size.width * 0.5f - 6f, center.y - 12f),
            size = Size(warnLayout.size.width + 12f, warnLayout.size.height + 6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )
        drawText(warnLayout, topLeft = Offset(center.x - warnLayout.size.width * 0.5f, center.y - 9f))
    }
}

/**
 * Draws 3D surface normal vector projecting from planar center to show 3D perspective orientation
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPlanarSurfaceNormal(points: List<Offset>, textMeasurer: TextMeasurer) {
    val p0 = points[0]
    val p1 = points[1]
    val p2 = points[2]
    val p3 = points[3]

    val center = evaluatePlanarBilinear(p0, p1, p2, p3, 0.5f, 0.5f)
    val rightPt = evaluatePlanarBilinear(p0, p1, p2, p3, 0.7f, 0.5f)
    val bottomPt = evaluatePlanarBilinear(p0, p1, p2, p3, 0.5f, 0.7f)

    // Tangents in U and V
    val tuX = rightPt.x - center.x
    val tuY = rightPt.y - center.y
    val tvX = bottomPt.x - center.x
    val tvY = bottomPt.y - center.y

    // 2D Perpendicular to simulate 3D projection normal
    val normalLength = 48f
    val crossZ = (tuX * tvY - tuY * tvX)
    val perpX = -tvY
    val perpY = tvX
    val perpLen = hypot(perpX.toDouble(), perpY.toDouble()).toFloat().coerceAtLeast(0.001f)
    val normDirX = (perpX / perpLen) * (if (crossZ >= 0) 1f else -1f)
    val normDirY = (perpY / perpLen) * (if (crossZ >= 0) 1f else -1f)

    val normalEnd = Offset(center.x + normDirX * normalLength, center.y + normDirY * normalLength)

    // Draw Normal Stem
    drawLine(
        color = CyanNeon,
        start = center,
        end = normalEnd,
        strokeWidth = 2.5f,
        cap = StrokeCap.Round
    )

    // Arrowhead
    val arrowLen = 10f
    val angle = atan2(normDirY.toDouble(), normDirX.toDouble()).toFloat()
    val arrowAngle1 = angle + Math.PI.toFloat() * 0.82f
    val arrowAngle2 = angle - Math.PI.toFloat() * 0.82f

    val arrowP1 = Offset(normalEnd.x + cos(arrowAngle1) * arrowLen, normalEnd.y + sin(arrowAngle1) * arrowLen)
    val arrowP2 = Offset(normalEnd.x + cos(arrowAngle2) * arrowLen, normalEnd.y + sin(arrowAngle2) * arrowLen)

    val arrowPath = Path().apply {
        moveTo(normalEnd.x, normalEnd.y)
        lineTo(arrowP1.x, arrowP1.y)
        lineTo(arrowP2.x, arrowP2.y)
        close()
    }
    drawPath(arrowPath, color = CyanNeon)

    // Surface Normal Label
    val normLabel = textMeasurer.measure(
        text = AnnotatedString("SURFACE NORMAL"),
        style = TextStyle(color = CyanNeon, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    )
    drawText(normLabel, topLeft = Offset(normalEnd.x + 8f, normalEnd.y - 8f))
}

/**
 * Draws edge distance labels, interior corner angles, and perspective diagnostic tags
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPlanarEdgeDimensions(points: List<Offset>, textMeasurer: TextMeasurer) {
    val labels = listOf("TOP", "RIGHT", "BOTTOM", "LEFT")

    for (i in 0 until 4) {
        val pStart = points[i]
        val pEnd = points[(i + 1) % 4]

        val edgeLen = hypot((pEnd.x - pStart.x).toDouble(), (pEnd.y - pStart.y).toDouble()).toInt()
        val midX = (pStart.x + pEnd.x) * 0.5f
        val midY = (pStart.y + pEnd.y) * 0.5f

        // Draw small badge on each edge
        val labelStr = "${labels[i]} ${edgeLen}px"
        val layout = textMeasurer.measure(
            text = AnnotatedString(labelStr),
            style = TextStyle(color = AeSlate300, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold)
        )

        val badgeW = layout.size.width + 8f
        val badgeH = layout.size.height + 4f

        drawRoundRect(
            color = AeSlate950.copy(alpha = 0.85f),
            topLeft = Offset(midX - badgeW * 0.5f, midY - badgeH * 0.5f),
            size = Size(badgeW, badgeH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
        )
        drawRoundRect(
            color = AeSlate700,
            topLeft = Offset(midX - badgeW * 0.5f, midY - badgeH * 0.5f),
            size = Size(badgeW, badgeH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
            style = Stroke(width = 0.8f)
        )
        drawText(layout, topLeft = Offset(midX - layout.size.width * 0.5f, midY - layout.size.height * 0.5f))
    }
}

/**
 * Draws iconic After Effects / Mocha Corner Pin reticle at each vertex
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPlanarCornerReticle(
    index: Int,
    name: String,
    screenX: Float,
    screenY: Float,
    isDragging: Boolean,
    isSelected: Boolean,
    pulseGlow: Float,
    textMeasurer: TextMeasurer
) {
    val cornerLabels = listOf("TL [0]", "TR [1]", "BR [2]", "BL [3]")
    val labelText = if (index in cornerLabels.indices) cornerLabels[index] else "P$index"

    val ringColor = when {
        isDragging -> AmberGlow
        isSelected -> CyanNeon
        else -> CyanGlow
    }

    val baseRadius = if (isDragging) 18f else 13f

    // Outer Magnetic Glow Ring
    drawCircle(
        color = ringColor.copy(alpha = if (isDragging) 0.35f else 0.18f * pulseGlow),
        radius = baseRadius * 1.7f,
        center = Offset(screenX, screenY)
    )

    // Inner Corner Ring
    drawCircle(
        color = ringColor,
        radius = baseRadius,
        center = Offset(screenX, screenY),
        style = Stroke(width = if (isDragging) 2.5f else 1.8f)
    )

    // Corner Crosshairs
    val armLen = 9f
    drawLine(Color.White, Offset(screenX - armLen, screenY), Offset(screenX + armLen, screenY), 1.5f)
    drawLine(Color.White, Offset(screenX, screenY - armLen), Offset(screenX, screenY + armLen), 1.5f)

    // Center Core Dot
    drawCircle(color = if (isDragging) AmberGlow else Color.White, radius = 3f, center = Offset(screenX, screenY))

    // Corner ID Badge
    val tagLayout = textMeasurer.measure(
        text = AnnotatedString(labelText),
        style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
    )

    val badgeX = screenX + (if (index % 3 == 0) -tagLayout.size.width - 16f else 16f)
    val badgeY = screenY + (if (index < 2) -tagLayout.size.height - 12f else 12f)

    drawRoundRect(
        color = AeSlate950.copy(alpha = 0.9f),
        topLeft = Offset(badgeX - 4f, badgeY - 2f),
        size = Size(tagLayout.size.width + 8f, tagLayout.size.height + 4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = ringColor,
        topLeft = Offset(badgeX - 4f, badgeY - 2f),
        size = Size(tagLayout.size.width + 8f, tagLayout.size.height + 4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
        style = Stroke(width = 1f)
    )
    drawText(tagLayout, topLeft = Offset(badgeX, badgeY))
}

/**
 * Centroid handle for whole-plane move / transform
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPlanarCentroidPivot(
    centroid: Offset,
    mode: PlanarInteractionMode,
    pulseGlow: Float,
    textMeasurer: TextMeasurer
) {
    val isMoveMode = mode == PlanarInteractionMode.TRANSLATE_PLANE || mode == PlanarInteractionMode.TRANSFORM_PLANE
    val pivotColor = if (isMoveMode) AmberGlow else VioletLayerGlow

    // Glowing halo
    drawCircle(
        color = pivotColor.copy(alpha = 0.25f * pulseGlow),
        radius = 24f,
        center = centroid
    )

    // Outer Pivot Ring
    drawCircle(
        color = pivotColor,
        radius = 16f,
        center = centroid,
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
    )

    // 4-Way Compass Cross
    val d = 10f
    drawLine(pivotColor, Offset(centroid.x - d, centroid.y), Offset(centroid.x + d, centroid.y), 2f, StrokeCap.Round)
    drawLine(pivotColor, Offset(centroid.x, centroid.y - d), Offset(centroid.x, centroid.y + d), 2f, StrokeCap.Round)
    drawCircle(Color.White, 3.5f, centroid)

    val label = if (mode == PlanarInteractionMode.TRANSLATE_PLANE) "DRAG PLANE" else "PIVOT"
    val layout = textMeasurer.measure(
        text = AnnotatedString(label),
        style = TextStyle(color = pivotColor, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
    )
    drawText(layout, topLeft = Offset(centroid.x - layout.size.width * 0.5f, centroid.y + 18f))
}

/**
 * Sequential mode preview: connects tapped points with dashed rubber-band guides
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawSequentialPlacementPreview(
    placedPoints: List<Offset>,
    scaleX: Float,
    scaleY: Float,
    pulseGlow: Float,
    textMeasurer: TextMeasurer
) {
    if (placedPoints.isEmpty()) return

    val screenOffsets = placedPoints.map { Offset(it.x * scaleX, it.y * scaleY) }

    // Connect existing points with bright dashed line
    for (i in 0 until screenOffsets.size - 1) {
        drawLine(
            color = CyanNeon,
            start = screenOffsets[i],
            end = screenOffsets[i + 1],
            strokeWidth = 2.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
    }

    // Draw markers for placed points
    val names = listOf("TL [1/4]", "TR [2/4]", "BR [3/4]", "BL [4/4]")
    for ((idx, offset) in screenOffsets.withIndex()) {
        drawCircle(color = CyanNeon.copy(alpha = 0.3f), radius = 16f * pulseGlow, center = offset)
        drawCircle(color = CyanNeon, radius = 8f, center = offset, style = Stroke(width = 2f))
        drawCircle(color = Color.White, radius = 3.5f, center = offset)

        val name = if (idx in names.indices) names[idx] else "Pt ${idx + 1}"
        val layout = textMeasurer.measure(
            text = AnnotatedString(name),
            style = TextStyle(color = CyanNeon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
        drawText(layout, topLeft = Offset(offset.x + 12f, offset.y - 8f))
    }
}

/**
 * 2.5x Loupe / Magnifier Lens hovering above user's fingertip for sub-pixel precision corner pinning
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPrecisionLoupe(
    frameBitmap: Bitmap,
    point: TrackerPoint,
    touchOffset: Offset,
    canvasSize: Size,
    scaleX: Float,
    scaleY: Float,
    textMeasurer: TextMeasurer
) {
    val loupeRadius = 60f
    // Offset loupe vertically so finger doesn't block it
    val loupeCenterY = if (touchOffset.y > loupeRadius * 2.5f) touchOffset.y - loupeRadius * 1.8f else touchOffset.y + loupeRadius * 1.8f
    val loupeCenterX = touchOffset.x.coerceIn(loupeRadius + 10f, canvasSize.width - loupeRadius - 10f)
    val loupeCenter = Offset(loupeCenterX, loupeCenterY)

    // Loupe Leader stem line connecting finger to loupe
    drawLine(
        color = AmberGlow.copy(alpha = 0.6f),
        start = touchOffset,
        end = loupeCenter,
        strokeWidth = 1.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
    )

    // Clip to circle and draw magnified sub-image
    val zoom = 2.5f
    val srcRadius = loupeRadius / (zoom * scaleX)
    val srcX = (point.x - srcRadius).toInt().coerceIn(0, frameBitmap.width - 1)
    val srcY = (point.y - srcRadius).toInt().coerceIn(0, frameBitmap.height - 1)
    val srcW = (srcRadius * 2).toInt().coerceIn(1, frameBitmap.width - srcX)
    val srcH = (srcRadius * 2).toInt().coerceIn(1, frameBitmap.height - srcY)

    // Backdrop
    drawCircle(color = AeSlate950, radius = loupeRadius + 4f, center = loupeCenter)

    clipPath(Path().apply { addOval(Rect(loupeCenter, loupeRadius)) }) {
        // Draw zoomed bitmap patch
        try {
            val patch = Bitmap.createBitmap(frameBitmap, srcX, srcY, srcW, srcH)
            drawImage(
                image = patch.asImageBitmap(),
                dstOffset = IntOffset((loupeCenter.x - loupeRadius).toInt(), (loupeCenter.y - loupeRadius).toInt()),
                dstSize = IntSize((loupeRadius * 2).toInt(), (loupeRadius * 2).toInt()),
                filterQuality = FilterQuality.None // Pixel-sharp grid
            )
        } catch (_: Exception) {
            drawRect(color = AeSlate900)
        }

        // Loupe Subpixel Crosshair
        drawLine(CrimsonReticle, Offset(loupeCenter.x - loupeRadius, loupeCenter.y), Offset(loupeCenter.x + loupeRadius, loupeCenter.y), 1.5f)
        drawLine(CrimsonReticle, Offset(loupeCenter.x, loupeCenter.y - loupeRadius), Offset(loupeCenter.x, loupeCenter.y + loupeRadius), 1.5f)
        drawCircle(color = Color.White, radius = 2.5f, center = loupeCenter)
    }

    // Outer High-Tech Lens Bezel
    drawCircle(
        color = AmberGlow,
        radius = loupeRadius,
        center = loupeCenter,
        style = Stroke(width = 3f)
    )

    // Coordinate Readout on Loupe Bezel
    val coordText = "X: ${point.x.toInt()}  Y: ${point.y.toInt()}"
    val coordLayout = textMeasurer.measure(
        text = AnnotatedString(coordText),
        style = TextStyle(color = AmberGlow, fontSize = 8.5.sp, fontWeight = FontWeight.Black)
    )
    drawRoundRect(
        color = AeSlate950.copy(alpha = 0.95f),
        topLeft = Offset(loupeCenter.x - coordLayout.size.width * 0.5f - 4f, loupeCenter.y + loupeRadius - 10f),
        size = Size(coordLayout.size.width + 8f, coordLayout.size.height + 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
    )
    drawText(coordLayout, topLeft = Offset(loupeCenter.x - coordLayout.size.width * 0.5f, loupeCenter.y + loupeRadius - 9f))
}

// -----------------------------------------------------------------------------------------
// COMPOSABLE UI CONTROLS & HUD OVERLAYS
// -----------------------------------------------------------------------------------------

/**
 * Top floating toolbar with Mode selection, Grid Density toggle, Snapping, Loupe, and Presets
 */
@Composable
private fun PlanarTopControlOverlay(
    interactionMode: PlanarInteractionMode,
    onModeChanged: (PlanarInteractionMode) -> Unit,
    gridDensity: PlanarGridDensity,
    onDensityChanged: (PlanarGridDensity) -> Unit,
    isSnappingEnabled: Boolean,
    onToggleSnapping: () -> Unit,
    isMagnifierEnabled: Boolean,
    onToggleMagnifier: () -> Unit,
    isScanningEnabled: Boolean,
    onToggleScanning: () -> Unit,
    isMetricsExpanded: Boolean,
    onToggleMetrics: () -> Unit,
    onApplyPreset: (PlanarPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPresetsMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("planar_top_control_overlay"),
        shape = RoundedCornerShape(12.dp),
        color = AeSlate900.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(1.dp, AeSlate700),
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // Row 1: Mode Switcher Chips & Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Mode selector chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlanarInteractionMode.values().forEach { mode ->
                        val isSelected = interactionMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { onModeChanged(mode) },
                            label = {
                                Text(
                                    text = "${mode.icon} ${mode.title}",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanNeon.copy(alpha = 0.2f),
                                selectedLabelColor = CyanNeon,
                                containerColor = AeSlate950,
                                labelColor = AeSlate300
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = AeSlate800,
                                selectedBorderColor = CyanNeon,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.dp
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // Presets Dropdown
                Box {
                    IconButton(
                        onClick = { showPresetsMenu = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = "Planar Presets",
                            tint = AmberGlow,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showPresetsMenu,
                        onDismissRequest = { showPresetsMenu = false },
                        modifier = Modifier.background(AeSlate900)
                    ) {
                        Text(
                            text = "PLANAR PRESETS",
                            color = AeSlate400,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        PlanarPreset.values().forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(preset.icon, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(preset.label, color = Color.White, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    onApplyPreset(preset)
                                    showPresetsMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Grid Density Cycle, Snapping, Loupe, Scan, and Inspector Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Grid density toggle button
                Surface(
                    onClick = {
                        val densities = PlanarGridDensity.values()
                        val nextIdx = (gridDensity.ordinal + 1) % densities.size
                        onDensityChanged(densities[nextIdx])
                    },
                    shape = RoundedCornerShape(4.dp),
                    color = AeSlate950,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AeSlate700)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Mesh: ${gridDensity.label}",
                            color = CyanGlow,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Toggles (Snap, Loupe, Laser Scan, Telemetry HUD)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Snap Toggle
                    IconButton(
                        onClick = onToggleSnapping,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSnappingEnabled) EmeraldLocked.copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterCenterFocus,
                            contentDescription = "Harris Snapping",
                            tint = if (isSnappingEnabled) EmeraldLocked else AeSlate500,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Loupe Toggle
                    IconButton(
                        onClick = onToggleMagnifier,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isMagnifierEnabled) AmberGlow.copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Precision Loupe",
                            tint = if (isMagnifierEnabled) AmberGlow else AeSlate500,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Scanline Toggle
                    IconButton(
                        onClick = onToggleScanning,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isScanningEnabled) CyanNeon.copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Waves,
                            contentDescription = "Radar Scanline",
                            tint = if (isScanningEnabled) CyanNeon else AeSlate500,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Metrics Telemetry Toggle
                    IconButton(
                        onClick = onToggleMetrics,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isMetricsExpanded) VioletLayerGlow.copy(alpha = 0.2f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Planar Metrics",
                            tint = if (isMetricsExpanded) VioletLayerGlow else AeSlate500,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom expandable HUD card with exact corner coordinates, aspect ratio, area, skew, and sub-pixel nudge
 */
@Composable
private fun PlanarMetricsInspectorCard(
    points: List<TrackerPoint>,
    frameBitmap: Bitmap?,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onNudgePoint: (Int, Float, Float) -> Unit,
    onSnapSelectedCorner: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (points.size < 4) return

    val p0 = points[0]
    val p1 = points[1]
    val p2 = points[2]
    val p3 = points[3]

    // Calculate Area (Shoelace formula)
    val area = 0.5f * abs(
        (p0.x * p1.y - p0.y * p1.x) +
        (p1.x * p2.y - p1.y * p2.x) +
        (p2.x * p3.y - p2.y * p3.x) +
        (p3.x * p0.y - p3.y * p0.x)
    )

    // Aspect ratio estimation (top edge / left edge)
    val topLen = hypot((p1.x - p0.x).toDouble(), (p1.y - p0.y).toDouble()).toFloat().coerceAtLeast(1f)
    val leftLen = hypot((p3.x - p0.x).toDouble(), (p3.y - p0.y).toDouble()).toFloat().coerceAtLeast(1f)
    val aspect = topLen / leftLen

    // Diagonal skew ratio (d1 / d2)
    val d1 = hypot((p2.x - p0.x).toDouble(), (p2.y - p0.y).toDouble()).toFloat().coerceAtLeast(1f)
    val d2 = hypot((p3.x - p1.x).toDouble(), (p3.y - p1.y).toDouble()).toFloat().coerceAtLeast(1f)
    val skewRatio = d1 / d2

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("planar_metrics_inspector"),
        shape = RoundedCornerShape(12.dp),
        color = AeSlate900.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, AeSlate700),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLANAR METRICS & CALIBRATION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = VioletLayerGlow,
                    letterSpacing = 1.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Area: ${area.toInt()}px²",
                        fontSize = 9.sp,
                        color = AeSlate300,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Aspect: ${String.format("%.2f", aspect)}:1",
                        fontSize = 9.sp,
                        color = AmberGlow,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Skew: ${String.format("%.2f", skewRatio)}",
                        fontSize = 9.sp,
                        color = CyanGlow,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4 Corner Selection Tabs
            val cornerNames = listOf("TL [0]", "TR [1]", "BR [2]", "BL [3]")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                points.take(4).forEachIndexed { idx, pt ->
                    val isSel = selectedIndex == idx
                    Surface(
                        onClick = { onSelectIndex(idx) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) CyanNeon.copy(alpha = 0.2f) else AeSlate950,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) CyanNeon else AeSlate800)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = cornerNames[idx],
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) CyanNeon else AeSlate400
                            )
                            Text(
                                text = "${pt.x.toInt()}, ${pt.y.toInt()}",
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Micro-nudge coordinate calibration controls for selected corner
            val cur = points.getOrNull(selectedIndex) ?: points[0]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Numeric coordinates
                Text(
                    text = "Selected: ${cur.name} (${String.format("%.1f", cur.x)}, ${String.format("%.1f", cur.y)})",
                    fontSize = 9.sp,
                    color = AeSlate300,
                    fontFamily = FontFamily.Monospace
                )

                // Nudge buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSnapSelectedCorner,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Adjust, contentDescription = "Snap Corner", tint = EmeraldLocked, modifier = Modifier.size(14.dp))
                    }
                    // Left
                    NudgeButton(label = "◀") { onNudgePoint(selectedIndex, -1f, 0f) }
                    // Right
                    NudgeButton(label = "▶") { onNudgePoint(selectedIndex, 1f, 0f) }
                    // Up
                    NudgeButton(label = "▲") { onNudgePoint(selectedIndex, 0f, -1f) }
                    // Down
                    NudgeButton(label = "▼") { onNudgePoint(selectedIndex, 0f, 1f) }
                }
            }
        }
    }
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = AeSlate800,
        border = androidx.compose.foundation.BorderStroke(1.dp, AeSlate700),
        modifier = Modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -----------------------------------------------------------------------------------------
// GEOMETRIC HELPERS & PRESET ENGINE
// -----------------------------------------------------------------------------------------

/**
 * Checks whether 4 points form a non-degenerate convex quadrilateral
 */
fun isPlanarQuadConvex(points: List<Offset>): Boolean {
    if (points.size < 4) return false
    var sign = 0
    for (i in 0 until 4) {
        val p1 = points[i]
        val p2 = points[(i + 1) % 4]
        val p3 = points[(i + 2) % 4]

        val dx1 = p2.x - p1.x
        val dy1 = p2.y - p1.y
        val dx2 = p3.x - p2.x
        val dy2 = p3.y - p2.y

        val cross = dx1 * dy2 - dy1 * dx2
        if (abs(cross) > 1e-4) {
            val currSign = if (cross > 0) 1 else -1
            if (sign == 0) sign = currSign
            else if (sign != currSign) return false
        }
    }
    return sign != 0
}

/**
 * Computes Centroid of 4 planar points
 */
fun computePlanarCentroid(points: List<Offset>): Offset {
    if (points.isEmpty()) return Offset.Zero
    val avgX = points.map { it.x }.average().toFloat()
    val avgY = points.map { it.y }.average().toFloat()
    return Offset(avgX, avgY)
}

/**
 * Applies quick planar geometry presets (e.g. 16:9 Screen, Wall, Floor)
 */
fun applyPlanarPreset(
    preset: PlanarPreset,
    canvasSize: Size,
    frameBitmap: Bitmap?,
    onPointsChanged: (List<TrackerPoint>) -> Unit
) {
    val w = frameBitmap?.width?.toFloat() ?: if (canvasSize.width > 0f) canvasSize.width else 640f
    val h = frameBitmap?.height?.toFloat() ?: if (canvasSize.height > 0f) canvasSize.height else 360f

    val cx = w * 0.5f
    val cy = h * 0.5f

    val newPoints = when (preset) {
        PlanarPreset.SCREEN_16_9 -> {
            val halfW = w * 0.28f
            val halfH = halfW / (16f / 9f)
            listOf(
                TrackerPoint(0, cx - halfW, cy - halfH, name = "Top Left"),
                TrackerPoint(1, cx + halfW, cy - halfH, name = "Top Right"),
                TrackerPoint(2, cx + halfW, cy + halfH, name = "Bottom Right"),
                TrackerPoint(3, cx - halfW, cy + halfH, name = "Bottom Left")
            )
        }
        PlanarPreset.SCREEN_9_16 -> {
            val halfH = h * 0.32f
            val halfW = halfH * (9f / 16f)
            listOf(
                TrackerPoint(0, cx - halfW, cy - halfH, name = "Top Left"),
                TrackerPoint(1, cx + halfW, cy - halfH, name = "Top Right"),
                TrackerPoint(2, cx + halfW, cy + halfH, name = "Bottom Right"),
                TrackerPoint(3, cx - halfW, cy + halfH, name = "Bottom Left")
            )
        }
        PlanarPreset.BILLBOARD_TRAPEZOID -> {
            // Realistic perspective wall/billboard with perspective convergence
            listOf(
                TrackerPoint(0, cx - w * 0.32f, cy - h * 0.30f, name = "Top Left"),
                TrackerPoint(1, cx + w * 0.25f, cy - h * 0.18f, name = "Top Right"),
                TrackerPoint(2, cx + w * 0.25f, cy + h * 0.22f, name = "Bottom Right"),
                TrackerPoint(3, cx - w * 0.32f, cy + h * 0.35f, name = "Bottom Left")
            )
        }
        PlanarPreset.ISOMETRIC_FLOOR -> {
            // Perspective floor plane
            listOf(
                TrackerPoint(0, cx - w * 0.18f, cy - h * 0.05f, name = "Top Left"),
                TrackerPoint(1, cx + w * 0.18f, cy - h * 0.05f, name = "Top Right"),
                TrackerPoint(2, cx + w * 0.38f, cy + h * 0.35f, name = "Bottom Right"),
                TrackerPoint(3, cx - w * 0.38f, cy + h * 0.35f, name = "Bottom Left")
            )
        }
        PlanarPreset.FILL_FRAME -> {
            listOf(
                TrackerPoint(0, w * 0.08f, h * 0.08f, name = "Top Left"),
                TrackerPoint(1, w * 0.92f, h * 0.08f, name = "Top Right"),
                TrackerPoint(2, w * 0.92f, h * 0.92f, name = "Bottom Right"),
                TrackerPoint(3, w * 0.08f, h * 0.92f, name = "Bottom Left")
            )
        }
    }

    onPointsChanged(newPoints)
}
