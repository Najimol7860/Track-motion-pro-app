package com.example.tracker.media

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.media.MediaMetadataRetriever
import com.example.tracker.model.SampleClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

object ProceduralFootageEngine {

    val sampleClips = listOf(
        SampleClip(
            id = "drift_car",
            title = "Hypercar Asphalt Drift",
            category = "Fast Motion & Smoke",
            description = "High-speed drift vehicle with tire smoke occlusion, scale perspective shifts, and rapid direction changes.",
            totalFrames = 100,
            fps = 30,
            previewBadge = "SMOKE & SPEED"
        ),
        SampleClip(
            id = "cyber_drone",
            title = "Cyber City Drone Recon",
            category = "Camera Shake & Lighting",
            description = "Surveillance drone hovering across illuminated skyscrapers with neon reflections and high-frequency jitter.",
            totalFrames = 120,
            fps = 30,
            previewBadge = "NIGHT & BLOOM"
        ),
        SampleClip(
            id = "skater_jump",
            title = "Extreme Skater 360 Flip",
            category = "Rotational & Occlusion",
            description = "Skater executing mid-air 360 rotation with foreground railing occlusions and fast vertical acceleration.",
            totalFrames = 90,
            fps = 30,
            previewBadge = "360° SPIN"
        ),
        SampleClip(
            id = "mech_target",
            title = "Armored Mech Core Lock",
            category = "Tactical VFX Tracking",
            description = "Robotic core with rotating holographic shield elements, energy pulses, and tactical motion.",
            totalFrames = 110,
            fps = 30,
            previewBadge = "HOLO SHIELD"
        ),
        SampleClip(
            id = "billboard_replace",
            title = "Cyber Billboard Perspective",
            category = "Planar 4-Point Screen Replace",
            description = "High-rise architectural screen with 3D perspective skew, camera push-in, and prominent corner markers for planar tracking.",
            totalFrames = 100,
            fps = 30,
            previewBadge = "PLANAR CORNER PIN"
        )
    )

    private val frameCache = mutableMapOf<String, Bitmap>()

    fun getFrame(clipId: String, frameIndex: Int, width: Int = 640, height: Int = 360): Bitmap {
        val key = "${clipId}_${frameIndex}_${width}x${height}"
        val cached = frameCache[key]
        if (cached != null && !cached.isRecycled) return cached

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        when (clipId) {
            "drift_car" -> renderDriftCarFrame(canvas, frameIndex, width, height)
            "cyber_drone" -> renderCyberDroneFrame(canvas, frameIndex, width, height)
            "skater_jump" -> renderSkaterFrame(canvas, frameIndex, width, height)
            "mech_target" -> renderMechFrame(canvas, frameIndex, width, height)
            "billboard_replace" -> renderBillboardFrame(canvas, frameIndex, width, height)
            else -> renderDriftCarFrame(canvas, frameIndex, width, height)
        }

        // Limit cache size to prevent memory pressure
        if (frameCache.size > 200) {
            val oldest = frameCache.keys.firstOrNull()
            if (oldest != null) {
                frameCache.remove(oldest)?.recycle()
            }
        }

        frameCache[key] = bitmap
        return bitmap
    }

    private fun renderDriftCarFrame(canvas: Canvas, frame: Int, w: Int, h: Int) {
        val t = frame / 100f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Asphalt Track Background with Speed Perspective
        val asphaltPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF151922.toInt(), 0xFF0D0F14.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), asphaltPaint)

        // Horizon & Sky gradient
        val skyPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * 0.4f, 0xFF1E293B.toInt(), 0xFF090D16.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, skyPaint)

        // Distant city lights
        paint.color = 0x44F59E0B.toInt()
        for (i in 0..12) {
            val bx = (i * 55 + sin((frame + i * 10).toDouble()) * 3).toFloat()
            val bh = 30f + (i * 17 % 40)
            canvas.drawRect(bx, h * 0.4f - bh, bx + 35f, h * 0.4f, paint)
        }

        // Perspective Road Markings
        paint.color = 0x33FFFFFF.toInt()
        paint.strokeWidth = 2f
        for (i in -4..12) {
            val laneOffset = ((frame * 6 + i * 60) % (w + 100)) - 50f
            canvas.drawLine(laneOffset, h * 0.75f, laneOffset + 40f, h.toFloat(), paint)
        }

        // Car Drift Physics Trajectory
        // The car moves dynamically across screen with realistic drift arc & scale change
        val carX = w * 0.2f + sin(t * Math.PI * 1.6).toFloat() * (w * 0.55f)
        val carY = h * 0.55f + cos(t * Math.PI * 1.8).toFloat() * (h * 0.2f)
        val carScale = 0.85f + 0.35f * sin(t * Math.PI).toFloat()
        val driftAngle = sin(t * Math.PI * 2.4).toFloat() * 26f - 8f

        // Tire Smoke Particles (Harder Tracking Challenge!)
        paint.style = Paint.Style.FILL
        for (s in 0..14) {
            val smokeAge = (frame + s * 7) % 30
            val smokeX = carX - 45f * carScale - smokeAge * 2.2f + sin((s * 13).toDouble()).toFloat() * 10f
            val smokeY = carY + 18f * carScale + cos((s * 7).toDouble()).toFloat() * 8f
            val smokeAlpha = ((30 - smokeAge) / 30f * 110).toInt().coerceIn(0, 160)
            paint.color = Color.argb(smokeAlpha, 180, 190, 210)
            canvas.drawCircle(smokeX, smokeY, (12f + smokeAge * 1.8f) * carScale, paint)
        }

        canvas.save()
        canvas.translate(carX, carY)
        canvas.rotate(driftAngle)
        canvas.scale(carScale, carScale)

        // Car Shadow
        paint.color = 0x77000000.toInt()
        canvas.drawOval(RectF(-70f, 15f, 70f, 32f), paint)

        // Car Body (Aerodynamic Cyber Sports Car)
        val bodyPath = Path().apply {
            moveTo(-65f, 10f)
            lineTo(-55f, -12f)
            lineTo(-25f, -16f)
            lineTo(-5f, -28f)
            lineTo(30f, -28f)
            lineTo(55f, -10f)
            lineTo(68f, 5f)
            lineTo(68f, 18f)
            lineTo(-65f, 18f)
            close()
        }
        val carPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(-65f, -28f, 68f, 18f, 0xFFE11D48.toInt(), 0xFFBE123C.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(bodyPath, carPaint)

        // Roof / Windshield (High Contrast Feature for Tracker!)
        val windshieldPath = Path().apply {
            moveTo(-20f, -15f)
            lineTo(-2f, -26f)
            lineTo(26f, -26f)
            lineTo(45f, -12f)
            lineTo(-20f, -12f)
            close()
        }
        paint.color = 0xFF0F172A.toInt()
        canvas.drawPath(windshieldPath, paint)

        // Distinct Feature Emblem / Spoiler (Anchor feature)
        paint.color = 0xFF00F0FF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawRect(-62f, -22f, -48f, -14f, paint) // Rear Spoiler Wing

        // Headlight Beam / Lens Flare
        paint.style = Paint.Style.FILL
        paint.color = 0xFF38BDF8.toInt()
        canvas.drawCircle(64f, 8f, 5f, paint)
        paint.color = 0x4438BDF8.toInt()
        canvas.drawCircle(64f, 8f, 16f, paint)

        // Wheels
        paint.color = 0xFF1E293B.toInt()
        canvas.drawCircle(-42f, 18f, 13f, paint)
        canvas.drawCircle(45f, 18f, 13f, paint)
        paint.color = 0xFFF59E0B.toInt()
        canvas.drawCircle(-42f, 18f, 5f, paint)
        canvas.drawCircle(45f, 18f, 5f, paint)

        canvas.restore()

        // Foreground Camera Obstacle / Street Light Pole (Temporary Occlusion!)
        if (frame in 42..54) {
            val poleX = w * 0.48f
            paint.color = 0xFF0F172A.toInt()
            canvas.drawRect(poleX - 12f, 0f, poleX + 12f, h.toFloat(), paint)
            paint.color = 0xFF334155.toInt()
            canvas.drawRect(poleX - 2f, 0f, poleX + 2f, h.toFloat(), paint)
        }
    }

    private fun renderCyberDroneFrame(canvas: Canvas, frame: Int, w: Int, h: Int) {
        val t = frame / 120f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Dark Cyberpunk Grid Cityscape
        canvas.drawColor(0xFF070A10.toInt())

        // Cyber Grid Lines
        paint.color = 0x1A00F0FF.toInt()
        paint.strokeWidth = 1f
        for (x in 0 until w step 40) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), paint)
        }
        for (y in 0 until h step 40) {
            canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), paint)
        }

        // Skyscrapers in Background
        paint.style = Paint.Style.FILL
        for (i in 0..8) {
            val sx = i * 75f - 10f
            val sh = 100f + (i * 29 % 130)
            paint.color = 0xFF0F172A.toInt()
            canvas.drawRect(sx, h - sh, sx + 60f, h.toFloat(), paint)

            // Windows
            paint.color = if (i % 2 == 0) 0x6600F0FF.toInt() else 0x66F59E0B.toInt()
            for (wy in (h - sh + 15).toInt() until h.toInt() step 20) {
                canvas.drawRect(sx + 10f, wy.toFloat(), sx + 22f, wy + 8f, paint)
                canvas.drawRect(sx + 35f, wy.toFloat(), sx + 47f, wy + 8f, paint)
            }
        }

        // Camera Shake simulation for realistic tracker challenge
        val shakeX = sin(frame * 1.7) * 2.2
        val shakeY = cos(frame * 2.1) * 1.8

        // Drone Autonomous Flight Coordinates
        val droneX = (w * 0.5f + sin(t * Math.PI * 2.2).toFloat() * (w * 0.38f) + shakeX).toFloat()
        val droneY = (h * 0.45f + cos(t * Math.PI * 3.1).toFloat() * (h * 0.22f) + shakeY).toFloat()
        val droneTilt = sin(t * Math.PI * 2.2).toFloat() * 20f
        val droneScale = 1.0f + 0.25f * sin(t * Math.PI * 1.5).toFloat()

        canvas.save()
        canvas.translate(droneX, droneY)
        canvas.rotate(droneTilt)
        canvas.scale(droneScale, droneScale)

        // Drone Quad Arms
        paint.color = 0xFF1E293B.toInt()
        paint.strokeWidth = 6f
        canvas.drawLine(-55f, -25f, 55f, 25f, paint)
        canvas.drawLine(-55f, 25f, 55f, -25f, paint)

        // Rotating Rotor Blades (High frequency motion)
        val rotorPhase = (frame * 65) % 360
        paint.color = 0x8800F0FF.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        val rotorOffsets = listOf(Pair(-55f, -25f), Pair(55f, -25f), Pair(-55f, 25f), Pair(55f, 25f))
        for (pt in rotorOffsets) {
            canvas.drawOval(RectF(pt.first - 24f, pt.second - 6f, pt.first + 24f, pt.second + 6f), paint)
        }

        // Drone Fuselage / Central Pod (Primary Track Target)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF0F172A.toInt()
        canvas.drawRoundRect(RectF(-28f, -18f, 28f, 18f), 8f, 8f, paint)

        // Glowing Optical Sensor Eye (High Contrast Track Target)
        paint.color = 0xFF00F0FF.toInt()
        canvas.drawCircle(0f, 0f, 10f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(0f, 0f, 4f, paint)

        // Red / Amber Navigation Beacon
        paint.color = 0xFFF43F5E.toInt()
        canvas.drawCircle(22f, 0f, 4f, paint)

        canvas.restore()
    }

    private fun renderSkaterFrame(canvas: Canvas, frame: Int, w: Int, h: Int) {
        val t = frame / 90f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Skate Park Ramp & Sky
        val skyPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1E1B4B.toInt(), 0xFF312E81.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), skyPaint)

        // Curved Concrete Bowl
        val rampPath = Path().apply {
            moveTo(0f, h * 0.85f)
            quadTo(w * 0.5f, h * 0.95f, w.toFloat(), h * 0.7f)
            lineTo(w.toFloat(), h.toFloat())
            lineTo(0f, h.toFloat())
            close()
        }
        paint.color = 0xFF334155.toInt()
        canvas.drawPath(rampPath, paint)

        // Skater Air Launch trajectory
        val jumpPhase = sin(t * Math.PI).toFloat()
        val skaterX = w * 0.15f + t * (w * 0.7f)
        val skaterY = h * 0.75f - jumpPhase * (h * 0.55f)
        val spinAngle = t * 360f // 360 rotation!

        canvas.save()
        canvas.translate(skaterX, skaterY)
        canvas.rotate(spinAngle)

        // Skateboard Deck
        paint.color = 0xFFF59E0B.toInt()
        canvas.drawRoundRect(RectF(-45f, 22f, 45f, 32f), 5f, 5f, paint)
        paint.color = 0xFF0F172A.toInt() // Grip tape
        canvas.drawRoundRect(RectF(-42f, 22f, 42f, 25f), 2f, 2f, paint)
        // Wheels
        paint.color = 0xFF00F0FF.toInt()
        canvas.drawCircle(-32f, 36f, 6f, paint)
        canvas.drawCircle(32f, 36f, 6f, paint)

        // Skater Figure (Stick/Silhouette with distinct contrasting helmet)
        paint.color = 0xFFE2E8F0.toInt()
        paint.strokeWidth = 8f
        paint.strokeCap = Paint.Cap.ROUND
        // Torso
        canvas.drawLine(0f, 15f, 0f, -25f, paint)
        // Legs
        canvas.drawLine(0f, 15f, -18f, 22f, paint)
        canvas.drawLine(0f, 15f, 18f, 22f, paint)
        // Arms
        canvas.drawLine(0f, -15f, -22f, -5f, paint)
        canvas.drawLine(0f, -15f, 22f, -30f, paint)

        // Helmet / Head (Primary Anchor Feature)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFF43F5E.toInt()
        canvas.drawCircle(0f, -35f, 12f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(4f, -37f, 3f, paint)

        canvas.restore()
    }

    private fun renderMechFrame(canvas: Canvas, frame: Int, w: Int, h: Int) {
        val t = frame / 110f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Tactical Lab Hologram Background
        canvas.drawColor(0xFF080C14.toInt())

        val cx = w * 0.5f + sin(t * Math.PI * 2.0).toFloat() * (w * 0.28f)
        val cy = h * 0.5f + cos(t * Math.PI * 1.6).toFloat() * (h * 0.20f)
        val scale = 0.9f + 0.3f * sin(t * Math.PI * 3.0).toFloat()

        // Radar sweep background rings
        paint.style = Paint.Style.STROKE
        paint.color = 0x2238BDF8.toInt()
        paint.strokeWidth = 1.5f
        canvas.drawCircle(w * 0.5f, h * 0.5f, 120f, paint)
        canvas.drawCircle(w * 0.5f, h * 0.5f, 200f, paint)

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)

        // Holographic Rotating Shields (Challenge for 2-Point and 4-Point planar tracking!)
        val shieldAngle = (frame * 4f) % 360
        canvas.save()
        canvas.rotate(shieldAngle)
        paint.color = 0xFF8B5CF6.toInt()
        paint.strokeWidth = 2.5f
        for (i in 0..3) {
            val a = Math.toRadians((i * 90).toDouble())
            val px = (cos(a) * 60).toFloat()
            val py = (sin(a) * 60).toFloat()
            canvas.drawRect(px - 10f, py - 10f, px + 10f, py + 10f, paint)
        }
        canvas.restore()

        // Mech Armor Chassis
        paint.style = Paint.Style.FILL
        paint.color = 0xFF1E293B.toInt()
        val chassis = Path().apply {
            moveTo(0f, -40f)
            lineTo(38f, -18f)
            lineTo(30f, 35f)
            lineTo(-30f, 35f)
            lineTo(-38f, -18f)
            close()
        }
        canvas.drawPath(chassis, paint)

        // Luminous Reactor Core (Anchor 1)
        paint.color = 0xFF00F0FF.toInt()
        canvas.drawCircle(0f, 0f, 16f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(0f, 0f, 8f, paint)

        // Corner Pin Anchor Nodes (For 4-Point Planar corner pinning)
        paint.color = 0xFFF59E0B.toInt()
        canvas.drawCircle(-32f, -16f, 4f, paint)
        canvas.drawCircle(32f, -16f, 4f, paint)
        canvas.drawCircle(26f, 30f, 4f, paint)
        canvas.drawCircle(-26f, 30f, 4f, paint)

        canvas.restore()
    }

    private fun renderBillboardFrame(canvas: Canvas, frame: Int, w: Int, h: Int) {
        val t = frame / 100f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Architectural City Skyline Background
        val bgPaint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF0A0F1D.toInt(), 0xFF05070B.toInt(), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Skyscraper building silhouettes
        paint.color = 0xFF141D33.toInt()
        canvas.drawRect(w * 0.05f, h * 0.15f, w * 0.35f, h.toFloat(), paint)
        paint.color = 0xFF0F172A.toInt()
        canvas.drawRect(w * 0.65f, h * 0.22f, w * 0.95f, h.toFloat(), paint)

        // Camera push-in & perspective drift simulation
        val pushIn = 1.0f + t * 0.22f
        val panX = sin(t * Math.PI).toFloat() * 25f
        val panY = cos(t * Math.PI * 0.8).toFloat() * 12f

        // Central High-Rise Perspective Billboard Building
        paint.color = 0xFF1E293B.toInt()
        val bldgPath = Path().apply {
            moveTo(w * 0.22f + panX, 0f)
            lineTo(w * 0.78f + panX, 0f)
            lineTo(w * 0.82f + panX, h.toFloat())
            lineTo(w * 0.18f + panX, h.toFloat())
            close()
        }
        canvas.drawPath(bldgPath, paint)

        // 3D Perspective Billboard Surface (4 Corners moving dynamically in perspective)
        val pTL_x = (w * 0.28f + panX) * pushIn - (pushIn - 1f) * (w * 0.5f)
        val pTL_y = (h * 0.22f + panY) * pushIn - (pushIn - 1f) * (h * 0.45f)

        val pTR_x = (w * 0.72f + panX) * pushIn - (pushIn - 1f) * (w * 0.5f)
        val pTR_y = (h * 0.18f + panY) * pushIn - (pushIn - 1f) * (h * 0.45f)

        val pBR_x = (w * 0.76f + panX) * pushIn - (pushIn - 1f) * (w * 0.5f)
        val pBR_y = (h * 0.68f + panY) * pushIn - (pushIn - 1f) * (h * 0.45f)

        val pBL_x = (w * 0.24f + panX) * pushIn - (pushIn - 1f) * (w * 0.5f)
        val pBL_y = (h * 0.72f + panY) * pushIn - (pushIn - 1f) * (h * 0.45f)

        val screenPath = Path().apply {
            moveTo(pTL_x, pTL_y)
            lineTo(pTR_x, pTR_y)
            lineTo(pBR_x, pBR_y)
            lineTo(pBL_x, pBL_y)
            close()
        }

        // Billboard Screen Glow Surface
        paint.style = Paint.Style.FILL
        paint.color = 0xFF0D1B2A.toInt()
        canvas.drawPath(screenPath, paint)

        // Screen Bezel Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = 0xFF00F0FF.toInt()
        canvas.drawPath(screenPath, paint)

        // Inner neon grid lines on billboard screen
        paint.strokeWidth = 1f
        paint.color = 0x5500F0FF.toInt()
        for (i in 1..4) {
            val u = i / 5f
            val topX = pTL_x + (pTR_x - pTL_x) * u
            val topY = pTL_y + (pTR_y - pTL_y) * u
            val botX = pBL_x + (pBR_x - pBL_x) * u
            val botY = pBL_y + (pBR_y - pBL_y) * u
            canvas.drawLine(topX, topY, botX, botY, paint)
        }

        // 4 High-Contrast Optical Flow Corner Anchors (Crosshairs)
        val corners = listOf(
            Pair(pTL_x, pTL_y),
            Pair(pTR_x, pTR_y),
            Pair(pBR_x, pBR_y),
            Pair(pBL_x, pBL_y)
        )

        paint.style = Paint.Style.FILL
        for (corner in corners) {
            val cx = corner.first
            val cy = corner.second
            paint.color = 0xFFF59E0B.toInt()
            canvas.drawCircle(cx, cy, 6f, paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(cx, cy, 3f, paint)
        }
    }
}
