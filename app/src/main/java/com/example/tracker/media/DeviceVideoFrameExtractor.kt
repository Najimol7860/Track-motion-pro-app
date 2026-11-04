package com.example.tracker.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceVideoFrameExtractor(private val context: Context) {

    private var retriever: MediaMetadataRetriever? = null
    private var durationUs: Long = 0L
    private var videoWidth: Int = 1920
    private var videoHeight: Int = 1080
    private val frameCache = mutableMapOf<Int, Bitmap>()

    suspend fun loadVideo(uri: Uri): Triple<Int, Int, Long>? = withContext(Dispatchers.IO) {
        try {
            retriever?.release()
            frameCache.clear()

            val mr = MediaMetadataRetriever()
            mr.setDataSource(context, uri)
            retriever = mr

            val durStr = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durMs = durStr?.toLongOrNull() ?: 5000L
            durationUs = durMs * 1000L

            val wStr = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            videoWidth = wStr?.toIntOrNull() ?: 1280
            videoHeight = hStr?.toIntOrNull() ?: 720

            val totalFrames = ((durMs / 1000.0) * 30).toInt().coerceIn(15, 300)
            Triple(videoWidth, videoHeight, totalFrames.toLong())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun extractFrame(frameIndex: Int, totalFrames: Int, targetWidth: Int = 640, targetHeight: Int = 360): Bitmap? = withContext(Dispatchers.IO) {
        val cached = frameCache[frameIndex]
        if (cached != null && !cached.isRecycled) return@withContext cached

        try {
            val mr = retriever ?: return@withContext null
            val timeUs = if (totalFrames > 0) (frameIndex.toFloat() / totalFrames * durationUs).toLong() else 0L
            val rawBitmap = mr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: return@withContext null

            val scaled = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
            if (scaled != rawBitmap) {
                rawBitmap.recycle()
            }

            if (frameCache.size > 120) {
                val oldest = frameCache.keys.firstOrNull()
                if (oldest != null) {
                    frameCache.remove(oldest)?.recycle()
                }
            }

            frameCache[frameIndex] = scaled
            scaled
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun release() {
        try {
            retriever?.release()
            frameCache.values.forEach { if (!it.isRecycled) it.recycle() }
            frameCache.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
