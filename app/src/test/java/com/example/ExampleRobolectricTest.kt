package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.tracker.engine.OpticalFlowTrackerEngine
import com.example.tracker.model.FrameKeyframe
import com.example.tracker.model.SmoothingMode
import com.example.tracker.model.TrackerPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Motion Tracker", appName)
    }

    @Test
    fun `test keyframe trajectory smoothing`() {
        val keyframes = mapOf(
            0 to FrameKeyframe(0, 0L, listOf(TrackerPoint(0, 100f, 100f)), 100f, 100f),
            1 to FrameKeyframe(1, 33L, listOf(TrackerPoint(0, 110f, 105f)), 110f, 105f),
            2 to FrameKeyframe(2, 66L, listOf(TrackerPoint(0, 120f, 110f)), 120f, 110f)
        )
        val smoothed = OpticalFlowTrackerEngine.smoothKeyframeTrajectory(keyframes, SmoothingMode.MEDIUM)
        assertEquals(3, smoothed.size)
        assertNotNull(smoothed[1])
    }

    @Test
    fun `test sticky object anchor locking`() {
        val testBitmap = android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)
        // Draw a solid high-contrast object box
        val canvas = android.graphics.Canvas(testBitmap)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
        canvas.drawRect(80f, 80f, 120f, 120f, paint)

        val anchor = OpticalFlowTrackerEngine.findStickyObjectAnchor(testBitmap, 95f, 95f, 35f)
        assertNotNull(anchor)
        // Anchor should be calculated near object center
        org.junit.Assert.assertTrue(anchor.first >= 75f && anchor.first <= 125f)
        org.junit.Assert.assertTrue(anchor.second >= 75f && anchor.second <= 125f)
    }

    @Test
    fun `test planar bilinear interpolation and centroid calculation`() {
        val p0 = androidx.compose.ui.geometry.Offset(100f, 100f) // TL
        val p1 = androidx.compose.ui.geometry.Offset(300f, 100f) // TR
        val p2 = androidx.compose.ui.geometry.Offset(300f, 200f) // BR
        val p3 = androidx.compose.ui.geometry.Offset(100f, 200f) // BL

        // Center point at u=0.5, v=0.5
        val center = com.example.tracker.ui.evaluatePlanarBilinear(p0, p1, p2, p3, 0.5f, 0.5f)
        assertEquals(200f, center.x, 0.01f)
        assertEquals(150f, center.y, 0.01f)

        // Centroid
        val centroid = com.example.tracker.ui.computePlanarCentroid(listOf(p0, p1, p2, p3))
        assertEquals(200f, centroid.x, 0.01f)
        assertEquals(150f, centroid.y, 0.01f)

        // Convexity check
        val isConvex = com.example.tracker.ui.isPlanarQuadConvex(listOf(p0, p1, p2, p3))
        org.junit.Assert.assertTrue(isConvex)

        // Inverted bowtie non-convex check
        val bowtie = listOf(p0, p2, p1, p3) // Cross diagonal swap
        val isBowtieConvex = com.example.tracker.ui.isPlanarQuadConvex(bowtie)
        org.junit.Assert.assertFalse(isBowtieConvex)
    }

    @Test
    fun `test planar preset setup`() {
        var updatedPoints: List<TrackerPoint>? = null
        com.example.tracker.ui.applyPlanarPreset(
            preset = com.example.tracker.ui.PlanarPreset.SCREEN_16_9,
            canvasSize = androidx.compose.ui.geometry.Size(640f, 360f),
            frameBitmap = null,
            onPointsChanged = { updatedPoints = it }
        )
        assertNotNull(updatedPoints)
        assertEquals(4, updatedPoints?.size)
        assertEquals("Top Left", updatedPoints?.get(0)?.name)
    }
}
