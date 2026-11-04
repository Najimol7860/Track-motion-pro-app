package com.example.tracker.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.model.*
import com.example.ui.theme.*
import kotlin.math.*

@OptIn(ExperimentalTextApi::class)
@Composable
fun TrackingCanvas(
    frameBitmap: Bitmap?,
    currentPoints: List<TrackerPoint>,
    keyframes: Map<Int, FrameKeyframe>,
    currentFrameIndex: Int,
    trackingMode: TrackingMode,
    attachedLayers: List<AttachedLayer>,
    selectedLayerId: String?,
    showTrajectory: Boolean,
    showSearchRegion: Boolean,
    onPointMoved: (index: Int, x: Float, y: Float) -> Unit,
    onPointTap: (index: Int) -> Unit,
    onStickToObject: ((index: Int, x: Float, y: Float) -> Unit)? = null,
    onPointsChanged: ((List<TrackerPoint>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var draggingPointIndex by remember { mutableStateOf<Int?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var isPlanarOverlayActive by remember { mutableStateOf(true) }

    val isPlanarMode = trackingMode == TrackingMode.PLANAR_4

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AeSlate950)
            .testTag("tracking_canvas")
            .then(
                if (!isPlanarMode || !isPlanarOverlayActive) {
                    Modifier
                        .pointerInput(currentPoints, canvasSize, frameBitmap) {
                            detectTapGestures { tapOffset ->
                                if (frameBitmap == null || canvasSize.width == 0f) return@detectTapGestures
                                val scaleX = canvasSize.width / frameBitmap.width.toFloat()
                                val scaleY = canvasSize.height / frameBitmap.height.toFloat()

                                // Check if tapped near any existing point
                                val hitIndex = currentPoints.indexOfFirst { pt ->
                                    val screenX = pt.x * scaleX
                                    val screenY = pt.y * scaleY
                                    hypot((tapOffset.x - screenX).toDouble(), (tapOffset.y - screenY).toDouble()) < 60f
                                }

                                if (hitIndex != -1) {
                                    onPointTap(hitIndex)
                                } else if (currentPoints.isNotEmpty()) {
                                    // User selected an object position on canvas -> firmly stick tracker to object
                                    val bitmapX = tapOffset.x / scaleX
                                    val bitmapY = tapOffset.y / scaleY
                                    if (onStickToObject != null) {
                                        onStickToObject(0, bitmapX, bitmapY)
                                    } else {
                                        onPointMoved(0, bitmapX, bitmapY)
                                    }
                                }
                            }
                        }
                        .pointerInput(currentPoints, canvasSize, frameBitmap) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    if (frameBitmap == null || canvasSize.width == 0f) return@detectDragGestures
                                    val scaleX = canvasSize.width / frameBitmap.width.toFloat()
                                    val scaleY = canvasSize.height / frameBitmap.height.toFloat()

                                    val hitIndex = currentPoints.indexOfFirst { pt ->
                                        val screenX = pt.x * scaleX
                                        val screenY = pt.y * scaleY
                                        hypot((startOffset.x - screenX).toDouble(), (startOffset.y - screenY).toDouble()) < 70f
                                    }
                                    draggingPointIndex = if (hitIndex != -1) hitIndex else 0
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val idx = draggingPointIndex ?: return@detectDragGestures
                                    if (frameBitmap == null || canvasSize.width == 0f) return@detectDragGestures
                                    val scaleX = canvasSize.width / frameBitmap.width.toFloat()
                                    val scaleY = canvasSize.height / frameBitmap.height.toFloat()

                                    val newBitmapX = (change.position.x / scaleX).coerceIn(0f, frameBitmap.width.toFloat())
                                    val newBitmapY = (change.position.y / scaleY).coerceIn(0f, frameBitmap.height.toFloat())
                                    onPointMoved(idx, newBitmapX, newBitmapY)
                                },
                                onDragEnd = { draggingPointIndex = null },
                                onDragCancel = { draggingPointIndex = null }
                            )
                        }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            canvasSize = size
            if (size.width <= 0 || size.height <= 0) return@Canvas

            // 1. Draw Video Frame Bitmap
            if (frameBitmap != null && !frameBitmap.isRecycled) {
                drawImage(
                    image = frameBitmap.asImageBitmap(),
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Medium
                )
            } else {
                // Placeholder dark grid
                drawRect(color = AeSlate900)
            }

            val scaleX = if (frameBitmap != null) size.width / frameBitmap.width.toFloat() else 1f
            val scaleY = if (frameBitmap != null) size.height / frameBitmap.height.toFloat() else 1f

            // 2. Draw Trajectory Trail
            if (showTrajectory && keyframes.size >= 2) {
                drawMotionTrajectory(
                    keyframes = keyframes,
                    currentFrame = currentFrameIndex,
                    scaleX = scaleX,
                    scaleY = scaleY
                )
            }

            // 3. Draw 4-Point Planar Quad Polygon if in PLANAR_4 mode (when overlay is not active)
            if (trackingMode == TrackingMode.PLANAR_4 && currentPoints.size >= 4 && !isPlanarOverlayActive) {
                drawPlanarQuad(currentPoints, scaleX, scaleY)
            }

            // 4. Draw After Effects Tracker Reticles for each point (when planar overlay is not active)
            if (!isPlanarMode || !isPlanarOverlayActive) {
                for ((idx, pt) in currentPoints.withIndex()) {
                    val screenX = pt.x * scaleX
                    val screenY = pt.y * scaleY
                    val isDragging = draggingPointIndex == idx

                    drawAeTrackerReticle(
                        point = pt,
                        screenX = screenX,
                        screenY = screenY,
                        scaleX = scaleX,
                        scaleY = scaleY,
                        showSearchRegion = showSearchRegion,
                        isDragging = isDragging,
                        textMeasurer = textMeasurer
                    )
                }
            }

            // 5. Draw Attached VFX Layers (Positioned and transformed by current tracked frame)
            val currentKeyframe = keyframes[currentFrameIndex]
            val trackCenterX = (currentKeyframe?.centerX ?: currentPoints.firstOrNull()?.x ?: (canvasSize.width * 0.5f / scaleX)) * scaleX
            val trackCenterY = (currentKeyframe?.centerY ?: currentPoints.firstOrNull()?.y ?: (canvasSize.height * 0.5f / scaleY)) * scaleY
            val trackScale = currentKeyframe?.scale ?: 1.0f
            val trackRot = currentKeyframe?.rotationDeg ?: 0.0f

            for (layer in attachedLayers.filter { it.isVisible }) {
                drawAttachedVfxLayer(
                    layer = layer,
                    centerX = trackCenterX,
                    centerY = trackCenterY,
                    trackScale = trackScale,
                    trackRotation = trackRot,
                    isSelected = layer.id == selectedLayerId,
                    textMeasurer = textMeasurer,
                    currentFrame = currentFrameIndex
                )
            }
        }

        // Dedicated Dynamic Planar Tracking Grid Overlay View
        if (isPlanarMode && isPlanarOverlayActive) {
            PlanarTrackingGridOverlay(
                points = currentPoints,
                onPointsChanged = { updated ->
                    if (onPointsChanged != null) {
                        onPointsChanged(updated)
                    } else {
                        updated.forEachIndexed { i, pt -> onPointMoved(i, pt.x, pt.y) }
                    }
                },
                frameBitmap = frameBitmap,
                onPointTapped = onPointTap,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Planar Grid Overlay Mode Switch Button (floating at top-end of canvas)
        if (isPlanarMode) {
            Surface(
                onClick = { isPlanarOverlayActive = !isPlanarOverlayActive },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                color = if (isPlanarOverlayActive) CyanNeon.copy(alpha = 0.25f) else AeSlate900.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isPlanarOverlayActive) CyanNeon else AeSlate700),
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .testTag("toggle_planar_grid_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Toggle Planar Grid",
                        tint = if (isPlanarOverlayActive) CyanNeon else AeSlate400,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlanarOverlayActive) "PLANAR GRID ON" else "GRID OFF",
                        color = if (isPlanarOverlayActive) CyanNeon else AeSlate400,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawMotionTrajectory(
    keyframes: Map<Int, FrameKeyframe>,
    currentFrame: Int,
    scaleX: Float,
    scaleY: Float
) {
    val sorted = keyframes.entries.sortedBy { it.key }
    if (sorted.isEmpty()) return

    val pathPast = Path()
    val pathFuture = Path()
    var pastStarted = false
    var futureStarted = false

    for (entry in sorted) {
        val f = entry.key
        val kf = entry.value
        val px = kf.centerX * scaleX
        val py = kf.centerY * scaleY

        if (f <= currentFrame) {
            if (!pastStarted) {
                pathPast.moveTo(px, py)
                pastStarted = true
            } else {
                pathPast.lineTo(px, py)
            }
        } else {
            if (!futureStarted) {
                val lastPast = sorted.findLast { it.key <= currentFrame }?.value
                if (lastPast != null) {
                    pathFuture.moveTo(lastPast.centerX * scaleX, lastPast.centerY * scaleY)
                } else {
                    pathFuture.moveTo(px, py)
                }
                futureStarted = true
            }
            pathFuture.lineTo(px, py)
        }

        // Draw individual keyframe markers
        val isCurrent = f == currentFrame
        val dotRadius = if (isCurrent) 6f else 3.5f
        val dotColor = if (isCurrent) CrimsonReticle else if (f < currentFrame) CyanNeon else AmberKeyframe

        drawCircle(
            color = dotColor,
            radius = dotRadius,
            center = Offset(px, py)
        )
    }

    if (pastStarted) {
        // Past Trajectory (Bright Cyan with subtle glow)
        drawPath(
            path = pathPast,
            color = CyanGlow.copy(alpha = 0.4f),
            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = pathPast,
            color = CyanNeon,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    if (futureStarted) {
        // Future Trajectory (Dashed / Amber)
        drawPath(
            path = pathFuture,
            color = AmberKeyframe.copy(alpha = 0.6f),
            style = Stroke(
                width = 2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        )
    }
}

private fun DrawScope.drawPlanarQuad(
    points: List<TrackerPoint>,
    scaleX: Float,
    scaleY: Float
) {
    if (points.size < 4) return
    val quadPath = Path().apply {
        moveTo(points[0].x * scaleX, points[0].y * scaleY)
        lineTo(points[1].x * scaleX, points[1].y * scaleY)
        lineTo(points[2].x * scaleX, points[2].y * scaleY)
        lineTo(points[3].x * scaleX, points[3].y * scaleY)
        close()
    }

    // Semi-transparent planar surface
    drawPath(
        path = quadPath,
        color = VioletLayer.copy(alpha = 0.15f)
    )
    drawPath(
        path = quadPath,
        color = VioletLayerGlow,
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)))
    )

    // Diagonal guidelines
    drawLine(
        color = VioletLayer.copy(alpha = 0.35f),
        start = Offset(points[0].x * scaleX, points[0].y * scaleY),
        end = Offset(points[2].x * scaleX, points[2].y * scaleY),
        strokeWidth = 1f
    )
    drawLine(
        color = VioletLayer.copy(alpha = 0.35f),
        start = Offset(points[1].x * scaleX, points[1].y * scaleY),
        end = Offset(points[3].x * scaleX, points[3].y * scaleY),
        strokeWidth = 1f
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawAeTrackerReticle(
    point: TrackerPoint,
    screenX: Float,
    screenY: Float,
    scaleX: Float,
    scaleY: Float,
    showSearchRegion: Boolean,
    isDragging: Boolean,
    textMeasurer: TextMeasurer
) {
    val searchRx = point.searchRadius * scaleX
    val searchRy = point.searchRadius * scaleY
    val featRx = point.featureRadius * scaleX
    val featRy = point.featureRadius * scaleY

    val reticleColor = when (point.status) {
        PointStatus.LOCKED -> CyanNeon
        PointStatus.PREDICTED -> AmberGlow
        PointStatus.LOST -> CrimsonReticle
        PointStatus.MANUAL -> EmeraldLocked
        PointStatus.STICKY_LOCKED -> CyanNeon
    }

    // Magnetic Object Lock Halo Ring
    if (point.isStickyLocked || point.status == PointStatus.STICKY_LOCKED) {
        drawCircle(
            color = CyanNeon.copy(alpha = 0.25f),
            radius = featRx * 1.6f,
            center = Offset(screenX, screenY),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        )
        drawCircle(
            color = EmeraldLocked.copy(alpha = 0.4f),
            radius = featRx * 1.25f,
            center = Offset(screenX, screenY),
            style = Stroke(width = 1.5f)
        )
    }

    // 1. Outer Search Region Box (Dashed Cyan / Amber)
    if (showSearchRegion) {
        drawRect(
            color = reticleColor.copy(alpha = if (isDragging) 0.6f else 0.35f),
            topLeft = Offset(screenX - searchRx, screenY - searchRy),
            size = Size(searchRx * 2, searchRy * 2),
            style = Stroke(
                width = if (isDragging) 2.5f else 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            )
        )

        // Search Region Corner Brackets
        val bracketLen = 14f
        val left = screenX - searchRx
        val right = screenX + searchRx
        val top = screenY - searchRy
        val bottom = screenY + searchRy

        // Top-Left
        drawLine(reticleColor, Offset(left, top), Offset(left + bracketLen, top), 2.5f)
        drawLine(reticleColor, Offset(left, top), Offset(left, top + bracketLen), 2.5f)
        // Top-Right
        drawLine(reticleColor, Offset(right, top), Offset(right - bracketLen, top), 2.5f)
        drawLine(reticleColor, Offset(right, top), Offset(right, top + bracketLen), 2.5f)
        // Bottom-Left
        drawLine(reticleColor, Offset(left, bottom), Offset(left + bracketLen, bottom), 2.5f)
        drawLine(reticleColor, Offset(left, bottom), Offset(left, bottom - bracketLen), 2.5f)
        // Bottom-Right
        drawLine(reticleColor, Offset(right, bottom), Offset(right - bracketLen, bottom), 2.5f)
        drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - bracketLen), 2.5f)
    }

    // 2. Inner Feature Region Box (Solid Amber Box)
    drawRect(
        color = AmberKeyframe,
        topLeft = Offset(screenX - featRx, screenY - featRy),
        size = Size(featRx * 2, featRy * 2),
        style = Stroke(width = 1.8f)
    )

    // 3. Center Crosshair (After Effects iconic crimson crosshair)
    val crossLen = 16f
    // Horizontal cross
    drawLine(
        color = CrimsonReticle,
        start = Offset(screenX - crossLen, screenY),
        end = Offset(screenX + crossLen, screenY),
        strokeWidth = 2.2f,
        cap = StrokeCap.Round
    )
    // Vertical cross
    drawLine(
        color = CrimsonReticle,
        start = Offset(screenX, screenY - crossLen),
        end = Offset(screenX, screenY + crossLen),
        strokeWidth = 2.2f,
        cap = StrokeCap.Round
    )

    // Center Anchor Core Dot
    drawCircle(color = Color.White, radius = 2.5f, center = Offset(screenX, screenY))

    // 4. Point Tag & Status Badge
    val statusLabel = "${point.name} • ${point.status.name} [${(point.confidence * 100).toInt()}%]"
    val textLayout = textMeasurer.measure(
        text = AnnotatedString(statusLabel),
        style = TextStyle(
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    )

    val labelX = screenX + featRx + 8f
    val labelY = screenY - featRy - 4f

    drawRoundRect(
        color = AeSlate950.copy(alpha = 0.85f),
        topLeft = Offset(labelX - 4f, labelY - 2f),
        size = Size(textLayout.size.width + 8f, textLayout.size.height + 4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = reticleColor,
        topLeft = Offset(labelX - 4f, labelY - 2f),
        size = Size(textLayout.size.width + 8f, textLayout.size.height + 4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
        style = Stroke(width = 1f)
    )

    drawText(
        textLayoutResult = textLayout,
        topLeft = Offset(labelX, labelY)
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawAttachedVfxLayer(
    layer: AttachedLayer,
    centerX: Float,
    centerY: Float,
    trackScale: Float,
    trackRotation: Float,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    currentFrame: Int
) {
    val layerColor = Color(layer.primaryColorHex)
    val effectiveScale = layer.scale * trackScale
    val effectiveRot = layer.rotationOffsetDeg + trackRotation

    val layerX = centerX + layer.offsetX * trackScale
    val layerY = centerY + layer.offsetY * trackScale

    // 1. Leader Line from Track Center to Attached Layer
    if (layer.showLeaderLine) {
        // Glowing anchor dot at track origin
        drawCircle(color = layerColor, radius = 3.5f, center = Offset(centerX, centerY))

        val path = Path().apply {
            moveTo(centerX, centerY)
            val midX = (centerX + layerX) * 0.5f + 15f
            val midY = (centerY + layerY) * 0.5f - 10f
            lineTo(midX, midY)
            lineTo(layerX, layerY)
        }
        drawLine(
            color = layerColor.copy(alpha = layer.opacity * 0.7f),
            start = Offset(centerX, centerY),
            end = Offset(layerX, layerY),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
        )
    }

    // 2. Draw Specific Layer Type with Transformations
    rotate(degrees = effectiveRot, pivot = Offset(layerX, layerY)) {
        scale(scaleX = effectiveScale, scaleY = effectiveScale, pivot = Offset(layerX, layerY)) {
            when (layer.type) {
                LayerType.HUD_CALLOUT -> drawCyberHudCallout(layer, layerX, layerY, layerColor, isSelected, textMeasurer, currentFrame)
                LayerType.TEXT_3D -> draw3dFloatingText(layer, layerX, layerY, layerColor, isSelected, textMeasurer)
                LayerType.PRIVACY_BLUR -> drawPrivacyBlurMask(layer, layerX, layerY, layerColor, isSelected)
                LayerType.AURA_GLOW -> drawEnergyAuraRing(layer, layerX, layerY, layerColor, currentFrame)
                LayerType.STICKER_EMOJI -> drawVfxBadgeEmoji(layer, layerX, layerY, isSelected, textMeasurer)
                LayerType.NULL_AXIS -> drawNull3dGimbal(layer, layerX, layerY, isSelected, textMeasurer)
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawCyberHudCallout(
    layer: AttachedLayer,
    x: Float,
    y: Float,
    color: Color,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    currentFrame: Int
) {
    val w = 150f
    val h = 48f
    val halfW = w * 0.5f
    val halfH = h * 0.5f

    // Backdrop
    drawRoundRect(
        color = AeSlate950.copy(alpha = layer.opacity * 0.9f),
        topLeft = Offset(x - halfW, y - halfH),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
    )

    // Corner Sci-Fi Brackets
    val bracketLen = 12f
    val strokeW = if (isSelected) 2.5f else 1.8f
    val borderCol = if (isSelected) AmberGlow else color.copy(alpha = layer.opacity)

    // Top-Left
    drawLine(borderCol, Offset(x - halfW, y - halfH), Offset(x - halfW + bracketLen, y - halfH), strokeW)
    drawLine(borderCol, Offset(x - halfW, y - halfH), Offset(x - halfW, y - halfH + bracketLen), strokeW)
    // Top-Right
    drawLine(borderCol, Offset(x + halfW, y - halfH), Offset(x + halfW - bracketLen, y - halfH), strokeW)
    drawLine(borderCol, Offset(x + halfW, y - halfH), Offset(x + halfW, y - halfH + bracketLen), strokeW)
    // Bottom-Left
    drawLine(borderCol, Offset(x - halfW, y + halfH), Offset(x - halfW + bracketLen, y + halfH), strokeW)
    drawLine(borderCol, Offset(x - halfW, y + halfH), Offset(x - halfW, y + halfH - bracketLen), strokeW)
    // Bottom-Right
    drawLine(borderCol, Offset(x + halfW, y + halfH), Offset(x + halfW - bracketLen, y + halfH), strokeW)
    drawLine(borderCol, Offset(x + halfW, y + halfH), Offset(x + halfW, y + halfH - bracketLen), strokeW)

    // Text Header
    val titleText = textMeasurer.measure(
        text = AnnotatedString(layer.textContent.ifEmpty { "TARGET LOCK" }),
        style = TextStyle(
            color = color,
            fontSize = 11.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp
        )
    )
    drawText(titleText, topLeft = Offset(x - halfW + 10f, y - halfH + 6f))

    // Real-time Telemetry Data Line
    val telemetry = "POS (${x.toInt()}, ${y.toInt()}) • F$currentFrame"
    val subText = textMeasurer.measure(
        text = AnnotatedString(telemetry),
        style = TextStyle(color = AeSlate400, fontSize = 8.5.sp)
    )
    drawText(subText, topLeft = Offset(x - halfW + 10f, y - halfH + 26f))
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.draw3dFloatingText(
    layer: AttachedLayer,
    x: Float,
    y: Float,
    color: Color,
    isSelected: Boolean,
    textMeasurer: TextMeasurer
) {
    val textResult = textMeasurer.measure(
        text = AnnotatedString(layer.textContent.ifEmpty { "MOTION TEXT" }),
        style = TextStyle(
            color = color.copy(alpha = layer.opacity),
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
            shadow = Shadow(color = color.copy(alpha = 0.8f), blurRadius = 14f)
        )
    )

    val halfW = textResult.size.width * 0.5f
    val halfH = textResult.size.height * 0.5f

    // 3D Shadow layer
    val shadowText = textMeasurer.measure(
        text = AnnotatedString(layer.textContent.ifEmpty { "MOTION TEXT" }),
        style = TextStyle(
            color = Color.Black.copy(alpha = 0.7f),
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
        )
    )
    drawText(shadowText, topLeft = Offset(x - halfW + 3f, y - halfH + 3f))

    // Main Glow Text
    drawText(textResult, topLeft = Offset(x - halfW, y - halfH))

    if (isSelected) {
        drawRect(
            color = AmberGlow,
            topLeft = Offset(x - halfW - 6f, y - halfH - 4f),
            size = Size(textResult.size.width + 12f, textResult.size.height + 8f),
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
        )
    }
}

private fun DrawScope.drawPrivacyBlurMask(
    layer: AttachedLayer,
    x: Float,
    y: Float,
    color: Color,
    isSelected: Boolean
) {
    val radius = layer.blurRadius * 2.8f
    val gridSize = 10f

    // Pixelation / Censor Mosaic Grid
    for (gx in (-radius).toInt()..(radius).toInt() step gridSize.toInt()) {
        for (gy in (-radius).toInt()..(radius).toInt() step gridSize.toInt()) {
            if (hypot(gx.toFloat().toDouble(), gy.toFloat().toDouble()) <= radius) {
                val shade = ((gx + gy) * 17) % 50 + 80
                drawRect(
                    color = Color(shade, shade, shade, (layer.opacity * 240).toInt()),
                    topLeft = Offset(x + gx, y + gy),
                    size = Size(gridSize - 1f, gridSize - 1f)
                )
            }
        }
    }

    // Outer Censor Ring
    drawCircle(
        color = if (isSelected) AmberGlow else CrimsonReticle,
        radius = radius,
        center = Offset(x, y),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    )
}

private fun DrawScope.drawEnergyAuraRing(
    layer: AttachedLayer,
    x: Float,
    y: Float,
    color: Color,
    currentFrame: Int
) {
    val pulse = sin(currentFrame * 0.15).toFloat() * 8f
    val r1 = 36f + pulse
    val r2 = 52f + pulse * 1.3f

    drawCircle(
        color = color.copy(alpha = layer.opacity * 0.35f),
        radius = r2,
        center = Offset(x, y),
        style = Stroke(width = 4f)
    )
    drawCircle(
        color = color.copy(alpha = layer.opacity * 0.8f),
        radius = r1,
        center = Offset(x, y),
        style = Stroke(width = 2.5f)
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawVfxBadgeEmoji(
    layer: AttachedLayer,
    x: Float,
    y: Float,
    isSelected: Boolean,
    textMeasurer: TextMeasurer
) {
    val emoji = layer.emojiIcon.ifEmpty { "🚀" }
    val emojiResult = textMeasurer.measure(
        text = AnnotatedString(emoji),
        style = TextStyle(fontSize = 32.sp)
    )
    val halfW = emojiResult.size.width * 0.5f
    val halfH = emojiResult.size.height * 0.5f

    drawText(emojiResult, topLeft = Offset(x - halfW, y - halfH))

    if (isSelected) {
        drawCircle(
            color = AmberGlow,
            radius = halfW + 10f,
            center = Offset(x, y),
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNull3dGimbal(
    layer: AttachedLayer,
    x: Float,
    y: Float,
    isSelected: Boolean,
    textMeasurer: TextMeasurer
) {
    val len = 42f

    // X Axis (Red)
    drawLine(CrimsonReticle, Offset(x, y), Offset(x + len, y), 3f, StrokeCap.Round)
    drawCircle(CrimsonReticle, 3f, Offset(x + len, y))

    // Y Axis (Green)
    drawLine(EmeraldLocked, Offset(x, y), Offset(x, y + len), 3f, StrokeCap.Round)
    drawCircle(EmeraldLocked, 3f, Offset(x, y + len))

    // Z Axis (Blue/Cyan)
    drawLine(CyanNeon, Offset(x, y), Offset(x - len * 0.6f, y - len * 0.6f), 3f, StrokeCap.Round)
    drawCircle(CyanNeon, 3f, Offset(x - len * 0.6f, y - len * 0.6f))

    // Center Null Pivot
    drawCircle(Color.White, 4f, Offset(x, y))

    val label = textMeasurer.measure(
        text = AnnotatedString("NULL // 3D"),
        style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    )
    drawText(label, topLeft = Offset(x + 10f, y - 20f))
}
