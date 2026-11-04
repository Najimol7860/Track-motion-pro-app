package com.example.tracker.engine

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.example.tracker.model.*
import kotlin.math.*

object OpticalFlowTrackerEngine {

    /**
     * Harris Corner response detector to automatically snap track point to strongest feature
     */
    fun findBestFeaturePoint(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        roiRadius: Float = 30f
    ): Pair<Float, Float> {
        val width = bitmap.width
        val height = bitmap.height
        val startX = (centerX - roiRadius).toInt().coerceIn(1, width - 2)
        val endX = (centerX + roiRadius).toInt().coerceIn(1, width - 2)
        val startY = (centerY - roiRadius).toInt().coerceIn(1, height - 2)
        val endY = (centerY + roiRadius).toInt().coerceIn(1, height - 2)

        if (endX <= startX || endY <= startY) return Pair(centerX, centerY)

        var maxCornerScore = -1f
        var bestX = centerX
        var bestY = centerY

        // Step through candidate pixels
        for (y in (startY + 2) until (endY - 2) step 2) {
            for (x in (startX + 2) until (endX - 2) step 2) {
                var ixx = 0f
                var iyy = 0f
                var ixy = 0f

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val px = x + dx
                        val py = y + dy
                        val lumLeft = getLuminance(bitmap, px - 1, py)
                        val lumRight = getLuminance(bitmap, px + 1, py)
                        val lumTop = getLuminance(bitmap, px, py - 1)
                        val lumBottom = getLuminance(bitmap, px, py + 1)

                        val gx = (lumRight - lumLeft) * 0.5f
                        val gy = (lumBottom - lumTop) * 0.5f

                        ixx += gx * gx
                        iyy += gy * gy
                        ixy += gx * gy
                    }
                }

                val det = ixx * iyy - ixy * ixy
                val trace = ixx + iyy
                val harrisScore = det - 0.04f * (trace * trace)

                // Distance penalty to favor features close to user tap
                val dist = hypot((x - centerX).toDouble(), (y - centerY).toDouble()).toFloat()
                val weightedScore = harrisScore / (1.0f + (dist / roiRadius) * 0.5f)

                if (weightedScore > maxCornerScore) {
                    maxCornerScore = weightedScore
                    bestX = x.toFloat()
                    bestY = y.toFloat()
                }
            }
        }

        return if (maxCornerScore > 0.001f) Pair(bestX, bestY) else Pair(centerX, centerY)
    }

    /**
     * Sticky Object Centroid & Gradient Anchor Snapping.
     * When user taps or places tracker on an object, this computes the object's
     * high-contrast centroid and salience boundary so the tracker firmly "sticks" to the object.
     */
    fun findStickyObjectAnchor(
        bitmap: Bitmap,
        touchX: Float,
        touchY: Float,
        searchRadius: Float = 40f
    ): Pair<Float, Float> {
        val width = bitmap.width
        val height = bitmap.height
        val minX = (touchX - searchRadius).toInt().coerceIn(2, width - 3)
        val maxX = (touchX + searchRadius).toInt().coerceIn(2, width - 3)
        val minY = (touchY - searchRadius).toInt().coerceIn(2, height - 3)
        val maxY = (touchY + searchRadius).toInt().coerceIn(2, height - 3)

        if (maxX <= minX || maxY <= minY) return Pair(touchX, touchY)

        var totalWeight = 0f
        var weightedSumX = 0f
        var weightedSumY = 0f

        var maxGradient = 0f
        var maxGradX = touchX
        var maxGradY = touchY

        for (y in minY..maxY step 2) {
            for (x in minX..maxX step 2) {
                val lumL = getLuminance(bitmap, x - 1, y)
                val lumR = getLuminance(bitmap, x + 1, y)
                val lumT = getLuminance(bitmap, x, y - 1)
                val lumB = getLuminance(bitmap, x, y + 1)

                val gx = lumR - lumL
                val gy = lumB - lumT
                val gradMag = sqrt(gx * gx + gy * gy)

                val dist = hypot((x - touchX).toDouble(), (y - touchY).toDouble()).toFloat()
                val spatialWeight = exp(-(dist * dist) / (2f * (searchRadius * 0.45f).pow(2f)))

                val combinedWeight = gradMag * spatialWeight
                if (combinedWeight > 0.01f) {
                    weightedSumX += x * combinedWeight
                    weightedSumY += y * combinedWeight
                    totalWeight += combinedWeight
                }

                if (gradMag > maxGradient && dist < searchRadius * 0.75f) {
                    maxGradient = gradMag
                    maxGradX = x.toFloat()
                    maxGradY = y.toFloat()
                }
            }
        }

        if (totalWeight > 0.05f) {
            val centroidX = weightedSumX / totalWeight
            val centroidY = weightedSumY / totalWeight
            // Blend 60% centroid + 40% sharpest gradient edge
            val finalX = centroidX * 0.6f + maxGradX * 0.4f
            val finalY = centroidY * 0.6f + maxGradY * 0.4f
            return Pair(finalX, finalY)
        }

        // Fallback to Harris Corner detector
        return findBestFeaturePoint(bitmap, touchX, touchY, searchRadius)
    }

    /**
     * Real-time footage telemetry diagnostic analyzer
     */
    data class FrameDiagnostics(
        val contrastStd: Float,
        val textureEntropy: Float,
        val laplacianBlurScore: Float,
        val averageVelocity: Float,
        val maxVelocity: Float,
        val estimatedOcclusionRisk: Float,
        val lightingUniformity: Float
    )

    fun analyzeFrameDiagnostics(
        bitmap: Bitmap,
        currentPoint: TrackerPoint?,
        motionHistory: List<TrackerPoint>
    ): FrameDiagnostics {
        val width = bitmap.width
        val height = bitmap.height
        val pt = currentPoint ?: TrackerPoint(x = width * 0.5f, y = height * 0.5f)

        val roiR = pt.featureRadius.toInt().coerceIn(10, 45)
        val cx = pt.x.toInt().coerceIn(roiR + 2, width - roiR - 2)
        val cy = pt.y.toInt().coerceIn(roiR + 2, height - roiR - 2)

        var lumSum = 0f
        var sampleCount = 0
        val lums = FloatArray((2 * roiR + 1) * (2 * roiR + 1))
        var idx = 0

        var laplacianSum = 0f

        for (dy in -roiR..roiR) {
            for (dx in -roiR..roiR) {
                val l = getLuminance(bitmap, cx + dx, cy + dy)
                lums[idx++] = l
                lumSum += l
                sampleCount++

                // Laplacian 3x3 filter
                val centerL = l
                val lL = getLuminance(bitmap, cx + dx - 1, cy + dy)
                val lR = getLuminance(bitmap, cx + dx + 1, cy + dy)
                val lT = getLuminance(bitmap, cx + dx, cy + dy - 1)
                val lB = getLuminance(bitmap, cx + dx, cy + dy + 1)
                val lap = abs(4f * centerL - lL - lR - lT - lB)
                laplacianSum += lap
            }
        }

        val meanLum = if (sampleCount > 0) lumSum / sampleCount else 0.5f
        var varSum = 0f
        for (i in 0 until idx) {
            val d = lums[i] - meanLum
            varSum += d * d
        }
        val contrastStd = sqrt(varSum / sampleCount.coerceAtLeast(1).toDouble()).toFloat()
        val blurScore = if (sampleCount > 0) laplacianSum / sampleCount else 0f

        // Motion velocity analysis from history
        var avgVel = 0f
        var maxVel = 0f
        if (motionHistory.size >= 2) {
            var velSum = 0f
            for (i in 1 until motionHistory.size) {
                val p1 = motionHistory[i - 1]
                val p2 = motionHistory[i]
                val v = hypot((p2.x - p1.x).toDouble(), (p2.y - p1.y).toDouble()).toFloat()
                velSum += v
                if (v > maxVel) maxVel = v
            }
            avgVel = velSum / (motionHistory.size - 1)
        }

        // Occlusion risk based on recent confidence drops
        val recentLowConfCount = motionHistory.takeLast(5).count { it.confidence < 0.6f }
        val occlusionRisk = (recentLowConfCount / 5f).coerceIn(0f, 1f)

        return FrameDiagnostics(
            contrastStd = contrastStd,
            textureEntropy = (contrastStd * 10f).coerceIn(0f, 1f),
            laplacianBlurScore = blurScore,
            averageVelocity = avgVel,
            maxVelocity = maxVel,
            estimatedOcclusionRisk = occlusionRisk,
            lightingUniformity = 1.0f - contrastStd.coerceIn(0f, 0.8f)
        )
    }

    /**
     * High efficiency Normalized Cross Correlation (NCC) + Motion Prediction Tracker
     */
    fun trackPointNextFrame(
        prevBitmap: Bitmap,
        currBitmap: Bitmap,
        prevPoint: TrackerPoint,
        motionHistory: List<TrackerPoint>,
        settings: TrackerSettings
    ): TrackerPoint {
        val width = currBitmap.width
        val height = currBitmap.height

        // Calculate motion momentum (Kalman / Velocity vector)
        var velocityX = 0f
        var velocityY = 0f
        if (motionHistory.size >= 2) {
            val pLast = motionHistory.last()
            val pPrev = motionHistory[motionHistory.size - 2]
            velocityX = (pLast.x - pPrev.x) * 0.85f
            velocityY = (pLast.y - pPrev.y) * 0.85f
        }

        // Adaptive Search Window adjustment based on velocity
        val speed = hypot(velocityX.toDouble(), velocityY.toDouble()).toFloat()
        val effectiveSearchRadius = if (settings.adaptiveSearch) {
            (settings.searchRadius + speed * 1.2f).coerceIn(settings.searchRadius, settings.searchRadius * 2.2f)
        } else {
            settings.searchRadius
        }

        val featR = settings.featureRadius.toInt().coerceIn(6, 40)
        val templateX = prevPoint.x.toInt().coerceIn(featR + 1, prevBitmap.width - featR - 1)
        val templateY = prevPoint.y.toInt().coerceIn(featR + 1, prevBitmap.height - featR - 1)

        // Extract template luminance statistics
        val templateLums = FloatArray((2 * featR + 1) * (2 * featR + 1))
        var tSum = 0f
        var idx = 0
        for (dy in -featR..featR) {
            for (dx in -featR..featR) {
                val lum = getLuminance(prevBitmap, templateX + dx, templateY + dy)
                templateLums[idx++] = lum
                tSum += lum
            }
        }
        val tMean = tSum / templateLums.size
        var tVar = 0f
        for (i in templateLums.indices) {
            templateLums[i] -= tMean
            tVar += templateLums[i] * templateLums[i]
        }
        val tStd = sqrt(tVar.toDouble()).toFloat().coerceAtLeast(0.0001f)

        // Center search around previous point + velocity prediction
        val predictedCenterX = (prevPoint.x + velocityX).coerceIn(featR.toFloat(), (width - featR).toFloat())
        val predictedCenterY = (prevPoint.y + velocityY).coerceIn(featR.toFloat(), (height - featR).toFloat())

        val searchMinX = (predictedCenterX - effectiveSearchRadius).toInt().coerceIn(featR + 1, width - featR - 1)
        val searchMaxX = (predictedCenterX + effectiveSearchRadius).toInt().coerceIn(featR + 1, width - featR - 1)
        val searchMinY = (predictedCenterY - effectiveSearchRadius).toInt().coerceIn(featR + 1, height - featR - 1)
        val searchMaxY = (predictedCenterY + effectiveSearchRadius).toInt().coerceIn(featR + 1, height - featR - 1)

        var bestScore = -1.0f
        var bestMatchX = predictedCenterX
        var bestMatchY = predictedCenterY

        val step = if (effectiveSearchRadius > 60) 2 else 1

        for (cy in searchMinY..searchMaxY step step) {
            for (cx in searchMinX..searchMaxX step step) {
                var cSum = 0f
                var cIdx = 0
                for (dy in -featR..featR) {
                    for (dx in -featR..featR) {
                        cSum += getLuminance(currBitmap, cx + dx, cy + dy)
                    }
                }
                val cMean = cSum / templateLums.size

                var crossProduct = 0f
                var cVar = 0f
                cIdx = 0
                for (dy in -featR..featR) {
                    for (dx in -featR..featR) {
                        val cLum = getLuminance(currBitmap, cx + dx, cy + dy) - cMean
                        crossProduct += templateLums[cIdx++] * cLum
                        cVar += cLum * cLum
                    }
                }
                val cStd = sqrt(cVar.toDouble()).toFloat().coerceAtLeast(0.0001f)
                val ncc = crossProduct / (tStd * cStd)

                // Proximity weighting to suppress false distant matches
                val distFromPred = hypot((cx - predictedCenterX).toDouble(), (cy - predictedCenterY).toDouble()).toFloat()
                val distPenalty = 1.0f - (distFromPred / effectiveSearchRadius) * 0.15f
                val finalScore = ncc * distPenalty

                if (finalScore > bestScore) {
                    bestScore = finalScore
                    bestMatchX = cx.toFloat()
                    bestMatchY = cy.toFloat()
                }
            }
        }

        // Sub-pixel bilinear refinement
        if (settings.subpixelRefinement && bestScore > 0.3f) {
            val subX = bestMatchX.toInt().coerceIn(featR + 2, width - featR - 2)
            val subY = bestMatchY.toInt().coerceIn(featR + 2, height - featR - 2)
            val scoreL = computeQuickNcc(currBitmap, subX - 1, subY, templateLums, tStd, featR)
            val scoreR = computeQuickNcc(currBitmap, subX + 1, subY, templateLums, tStd, featR)
            val scoreT = computeQuickNcc(currBitmap, subX, subY - 1, templateLums, tStd, featR)
            val scoreB = computeQuickNcc(currBitmap, subX, subY + 1, templateLums, tStd, featR)

            val denomX = (2f * bestScore - scoreL - scoreR).coerceAtLeast(0.0001f)
            val denomY = (2f * bestScore - scoreT - scoreB).coerceAtLeast(0.0001f)
            val deltaX = ((scoreR - scoreL) / (2f * denomX)).coerceIn(-0.5f, 0.5f)
            val deltaY = ((scoreB - scoreT) / (2f * denomY)).coerceIn(-0.5f, 0.5f)

            bestMatchX += deltaX
            bestMatchY += deltaY
        }

        // Check against confidence threshold and apply Kalman occlusion predictor
        val normalizedConfidence = ((bestScore + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)
        val isOccludedOrLost = normalizedConfidence < settings.confidenceThreshold

        return if (isOccludedOrLost && settings.occlusionRecovery) {
            // Target occluded or lost: use 2nd-order momentum prediction
            TrackerPoint(
                id = prevPoint.id,
                x = predictedCenterX,
                y = predictedCenterY,
                searchRadius = effectiveSearchRadius,
                featureRadius = prevPoint.featureRadius,
                confidence = normalizedConfidence * 0.8f,
                status = PointStatus.PREDICTED,
                name = prevPoint.name
            )
        } else if (isOccludedOrLost) {
            TrackerPoint(
                id = prevPoint.id,
                x = bestMatchX,
                y = bestMatchY,
                searchRadius = effectiveSearchRadius,
                featureRadius = prevPoint.featureRadius,
                confidence = normalizedConfidence,
                status = PointStatus.LOST,
                name = prevPoint.name
            )
        } else {
            TrackerPoint(
                id = prevPoint.id,
                x = bestMatchX,
                y = bestMatchY,
                searchRadius = settings.searchRadius,
                featureRadius = prevPoint.featureRadius,
                confidence = normalizedConfidence,
                status = PointStatus.LOCKED,
                name = prevPoint.name
            )
        }
    }

    private fun computeQuickNcc(
        bitmap: Bitmap,
        cx: Int,
        cy: Int,
        templateLums: FloatArray,
        tStd: Float,
        featR: Int
    ): Float {
        var cSum = 0f
        for (dy in -featR..featR) {
            for (dx in -featR..featR) {
                cSum += getLuminance(bitmap, cx + dx, cy + dy)
            }
        }
        val cMean = cSum / templateLums.size
        var crossProduct = 0f
        var cVar = 0f
        var cIdx = 0
        for (dy in -featR..featR) {
            for (dx in -featR..featR) {
                val cLum = getLuminance(bitmap, cx + dx, cy + dy) - cMean
                crossProduct += templateLums[cIdx++] * cLum
                cVar += cLum * cLum
            }
        }
        val cStd = sqrt(cVar.toDouble()).toFloat().coerceAtLeast(0.0001f)
        return crossProduct / (tStd * cStd)
    }

    private fun getLuminance(bitmap: Bitmap, x: Int, y: Int): Float {
        val px = x.coerceIn(0, bitmap.width - 1)
        val py = y.coerceIn(0, bitmap.height - 1)
        val pixel = bitmap.getPixel(px, py)
        val r = AndroidColor.red(pixel) / 255f
        val g = AndroidColor.green(pixel) / 255f
        val b = AndroidColor.blue(pixel) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /**
     * Computes 2D Transform metrics (Centroid, Scale ratio, Rotation angle) from points
     */
    fun computeTransform(
        points: List<TrackerPoint>,
        initialPoints: List<TrackerPoint>? = null
    ): Triple<Pair<Float, Float>, Float, Float> {
        if (points.isEmpty()) return Triple(Pair(0f, 0f), 1f, 0f)

        if (points.size == 1) {
            return Triple(Pair(points[0].x, points[0].y), 1f, 0f)
        }

        // 2-point mode (or multi-point)
        val p1 = points[0]
        val p2 = points[1]
        val centerX = (p1.x + p2.x) * 0.5f
        val centerY = (p1.y + p2.y) * 0.5f

        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val currentDist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        val currentAngleRad = atan2(dy.toDouble(), dx.toDouble()).toFloat()

        var scale = 1.0f
        var rotationDeg = Math.toDegrees(currentAngleRad.toDouble()).toFloat()

        if (initialPoints != null && initialPoints.size >= 2) {
            val i1 = initialPoints[0]
            val i2 = initialPoints[1]
            val initDx = i2.x - i1.x
            val initDy = i2.y - i1.y
            val initDist = hypot(initDx.toDouble(), initDy.toDouble()).toFloat().coerceAtLeast(1f)
            val initAngleRad = atan2(initDy.toDouble(), initDx.toDouble()).toFloat()
            scale = currentDist / initDist
            val deltaAngleRad = currentAngleRad - initAngleRad
            rotationDeg = Math.toDegrees(deltaAngleRad.toDouble()).toFloat()
        }

        return Triple(Pair(centerX, centerY), scale, rotationDeg)
    }

    /**
     * Apply Gaussian Smoothing filter along keyframe trajectory
     */
    fun smoothKeyframeTrajectory(
        keyframes: Map<Int, FrameKeyframe>,
        mode: SmoothingMode
    ): Map<Int, FrameKeyframe> {
        if (mode == SmoothingMode.RAW || keyframes.size <= 2) return keyframes

        val sortedFrames = keyframes.keys.sorted()
        val radius = when (mode) {
            SmoothingMode.LIGHT -> 1
            SmoothingMode.MEDIUM -> 2
            SmoothingMode.HEAVY -> 4
            SmoothingMode.RAW -> 0
        }

        val result = mutableMapOf<Int, FrameKeyframe>()

        for (i in sortedFrames.indices) {
            val frameIdx = sortedFrames[i]
            val currentKeyframe = keyframes[frameIdx] ?: continue

            var sumWeight = 0f
            var avgCenterX = 0f
            var avgCenterY = 0f
            var avgScale = 0f
            var avgRot = 0f

            for (r in -radius..radius) {
                val neighborIdx = i + r
                if (neighborIdx in sortedFrames.indices) {
                    val nFrame = sortedFrames[neighborIdx]
                    val kf = keyframes[nFrame] ?: continue
                    val weight = exp(-(r * r).toDouble() / (2.0 * (radius * 0.5 + 0.5).pow(2.0))).toFloat()
                    sumWeight += weight
                    avgCenterX += kf.centerX * weight
                    avgCenterY += kf.centerY * weight
                    avgScale += kf.scale * weight
                    avgRot += kf.rotationDeg * weight
                }
            }

            val finalCenterX = avgCenterX / sumWeight
            val finalCenterY = avgCenterY / sumWeight
            val finalScale = avgScale / sumWeight
            val finalRot = avgRot / sumWeight

            val smoothedPoints = currentKeyframe.points.mapIndexed { pIdx, p ->
                val offsetX = p.x - currentKeyframe.centerX
                val offsetY = p.y - currentKeyframe.centerY
                p.copy(x = finalCenterX + offsetX, y = finalCenterY + offsetY)
            }

            result[frameIdx] = currentKeyframe.copy(
                centerX = finalCenterX,
                centerY = finalCenterY,
                scale = finalScale,
                rotationDeg = finalRot,
                points = smoothedPoints
            )
        }

        return result
    }

    /**
     * Generate standard Adobe After Effects 8.0 Keyframe Data format
     */
    fun exportToAfterEffectsFormat(
        projectName: String,
        keyframes: Map<Int, FrameKeyframe>,
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 30
    ): String {
        val sortedFrames = keyframes.keys.sorted()
        val sb = StringBuilder()
        sb.appendLine("Adobe After Effects 8.0 Keyframe Data")
        sb.appendLine("\tUnits Per Second\t$fps")
        sb.appendLine("\tSource Width\t$width")
        sb.appendLine("\tSource Height\t$height")
        sb.appendLine("\tSource Pixel Aspect Ratio\t1")
        sb.appendLine("\tComp Pixel Aspect Ratio\t1")
        sb.appendLine()
        sb.appendLine("Transform\tPosition")
        sb.appendLine("\tFrame\tX pixels\tY pixels\tZ pixels")
        for (f in sortedFrames) {
            val kf = keyframes[f] ?: continue
            val posX = String.format("%.2f", kf.centerX)
            val posY = String.format("%.2f", kf.centerY)
            sb.appendLine("\t$f\t$posX\t$posY\t0")
        }
        sb.appendLine()
        sb.appendLine("Transform\tScale")
        sb.appendLine("\tFrame\tX percent\tY percent\tZ percent")
        for (f in sortedFrames) {
            val kf = keyframes[f] ?: continue
            val sPct = String.format("%.2f", kf.scale * 100f)
            sb.appendLine("\t$f\t$sPct\t$sPct\t100")
        }
        sb.appendLine()
        sb.appendLine("Transform\tRotation")
        sb.appendLine("\tFrame\tdegrees")
        for (f in sortedFrames) {
            val kf = keyframes[f] ?: continue
            val rot = String.format("%.2f", kf.rotationDeg)
            sb.appendLine("\t$f\t$rot")
        }
        sb.appendLine()
        sb.appendLine("End of Keyframe Data")
        return sb.toString()
    }
}
