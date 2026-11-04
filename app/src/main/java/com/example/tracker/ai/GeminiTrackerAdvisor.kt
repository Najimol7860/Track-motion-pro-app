package com.example.tracker.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.tracker.engine.OpticalFlowTrackerEngine
import com.example.tracker.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object GeminiTrackerAdvisor {

    private const val MODEL_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Analyze video footage frame & telemetry for challenging tracking scenarios.
     * Uses Gemini multimodal API with smart local fallback.
     */
    suspend fun analyzeTrackingScenarios(
        bitmap: Bitmap?,
        currentPoint: TrackerPoint?,
        motionHistory: List<TrackerPoint>,
        clip: SampleClip,
        currentSettings: TrackerSettings
    ): AiDifficultyReport = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }

        // 1. Calculate local deterministic diagnostics
        val diag = if (bitmap != null) {
            OpticalFlowTrackerEngine.analyzeFrameDiagnostics(bitmap, currentPoint, motionHistory)
        } else {
            OpticalFlowTrackerEngine.FrameDiagnostics(0.3f, 0.4f, 15f, 0f, 0f, 0f, 0.7f)
        }

        // 2. Try Gemini Multimodal analysis if API key is present
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY" && bitmap != null) {
            try {
                val report = callGeminiAdvisor(bitmap, currentPoint, motionHistory, clip, currentSettings, diag, apiKey)
                if (report != null) {
                    return@withContext report
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback to smart on-device heuristic engine
        return@withContext buildLocalHeuristicReport(clip, diag, currentSettings, currentPoint)
    }

    private fun callGeminiAdvisor(
        bitmap: Bitmap,
        currentPoint: TrackerPoint?,
        motionHistory: List<TrackerPoint>,
        clip: SampleClip,
        currentSettings: TrackerSettings,
        diag: OpticalFlowTrackerEngine.FrameDiagnostics,
        apiKey: String
    ): AiDifficultyReport? {
        val base64Img = bitmapToBase64(bitmap, maxWidth = 480)

        val prompt = """
You are an expert VFX Motion Tracking Supervisor specializing in After Effects optical flow, planar tracking, and complex feature extraction.
Analyze this video footage frame and diagnostic telemetry for difficult tracking scenarios.

Footage Context:
- Clip: "${clip.title}" (${clip.category})
- Resolution: ${bitmap.width}x${bitmap.height}
- Active Track Point: (${currentPoint?.x?.toInt() ?: 0}, ${currentPoint?.y?.toInt() ?: 0})
- Local Texture Contrast: ${String.format("%.2f", diag.contrastStd)} (0-1)
- Laplacian Blur/Sharpness: ${String.format("%.1f", diag.laplacianBlurScore)}
- Motion Velocity: ${String.format("%.1f", diag.averageVelocity)} px/frame (Peak: ${String.format("%.1f", diag.maxVelocity)})
- Occlusion/Dropout Risk: ${String.format("%.0f", diag.estimatedOcclusionRisk * 100)}%

Identify tracking bottlenecks (e.g. Motion Blur, Whip-pan Velocity, Homogeneous/Featureless Surface, Intermittent Occlusion/Obstacle, Lighting Shifts, Perspective Shear, Rotational Roll).
Return a valid JSON object ONLY with this exact schema:
{
  "difficultyScore": 6.8,
  "difficultyLevel": "DIFFICULT",
  "summaryTitle": "Fast Whip-Pan & Motion Blur Detected",
  "summaryAnalysis": "Detailed 1-2 sentence diagnosis of why this object is challenging to track.",
  "workflowAdvice": "Concrete, actionable step-by-step VFX tip to guarantee solid lock.",
  "challenges": [
    {
      "id": "whip_pan",
      "title": "High Velocity Whip-Pan",
      "category": "Motion Dynamics",
      "severity": "HIGH",
      "description": "Rapid acceleration exceeds standard search bounds.",
      "recommendedFix": "Increase search radius to 95px and activate Adaptive Velocity Search."
    }
  ],
  "recommendedSettings": {
    "searchRadius": 95.0,
    "featureRadius": 32.0,
    "adaptiveSearch": true,
    "occlusionRecovery": true,
    "subpixelRefinement": true,
    "smoothingMode": "MEDIUM",
    "snapToHarrisCorners": true,
    "stickyObjectLock": true,
    "confidenceThreshold": 0.52
  },
  "recommendedMode": "POINT_1",
  "confidenceEstimate": 0.88
}
Allowed difficultyLevel: OPTIMAL, MODERATE, DIFFICULT, EXTREME.
Allowed recommendedMode: POINT_1, TRANSFORM_2, PLANAR_4, ROI_BOX.
Allowed smoothingMode: RAW, LIGHT, MEDIUM, HEAVY.
""".trimIndent()

        val jsonBody = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val partsArr = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "image/jpeg")
                                put("data", base64Img)
                            })
                        })
                    }
                    put("parts", partsArr)
                }
                put(contentObj)
            }
            put("contents", contentsArr)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.3)
            })
        }

        val request = Request.Builder()
            .url("$MODEL_ENDPOINT?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val respStr = response.body?.string() ?: return null
        val root = JSONObject(respStr)
        val candidates = root.optJSONArray("candidates") ?: return null
        val firstCand = candidates.optJSONObject(0) ?: return null
        val content = firstCand.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        val textPart = parts.optJSONObject(0)?.optString("text") ?: return null

        return parseGeminiResponseJson(textPart, currentSettings)
    }

    private fun parseGeminiResponseJson(jsonText: String, fallbackSettings: TrackerSettings): AiDifficultyReport {
        val obj = JSONObject(jsonText.trim().removeSurrounding("```json", "```").trim())

        val score = obj.optDouble("difficultyScore", 4.5).toFloat()
        val levelStr = obj.optString("difficultyLevel", "MODERATE")
        val level = try { TrackingDifficultyLevel.valueOf(levelStr) } catch (e: Exception) { TrackingDifficultyLevel.MODERATE }

        val summaryTitle = obj.optString("summaryTitle", "Footage Analysis Complete")
        val summaryAnalysis = obj.optString("summaryAnalysis", "AI evaluated optical flow patterns.")
        val workflowAdvice = obj.optString("workflowAdvice", "Adjust search radius and enable Kalman prediction.")

        val challengesList = mutableListOf<TrackingChallengeTag>()
        val challengesArr = obj.optJSONArray("challenges")
        if (challengesArr != null) {
            for (i in 0 until challengesArr.length()) {
                val cObj = challengesArr.getJSONObject(i)
                challengesList.add(
                    TrackingChallengeTag(
                        id = cObj.optString("id", "c_$i"),
                        title = cObj.optString("title", "Tracking Challenge"),
                        category = cObj.optString("category", "General"),
                        severity = cObj.optString("severity", "MODERATE"),
                        description = cObj.optString("description", ""),
                        recommendedFix = cObj.optString("recommendedFix", "")
                    )
                )
            }
        }

        val settingsObj = obj.optJSONObject("recommendedSettings")
        val recommendedSettings = if (settingsObj != null) {
            TrackerSettings(
                searchRadius = settingsObj.optDouble("searchRadius", fallbackSettings.searchRadius.toDouble()).toFloat(),
                featureRadius = settingsObj.optDouble("featureRadius", fallbackSettings.featureRadius.toDouble()).toFloat(),
                adaptiveSearch = settingsObj.optBoolean("adaptiveSearch", true),
                occlusionRecovery = settingsObj.optBoolean("occlusionRecovery", true),
                subpixelRefinement = settingsObj.optBoolean("subpixelRefinement", true),
                smoothingMode = try {
                    SmoothingMode.valueOf(settingsObj.optString("smoothingMode", "MEDIUM"))
                } catch (e: Exception) {
                    SmoothingMode.MEDIUM
                },
                snapToHarrisCorners = settingsObj.optBoolean("snapToHarrisCorners", true),
                stickyObjectLock = settingsObj.optBoolean("stickyObjectLock", true),
                confidenceThreshold = settingsObj.optDouble("confidenceThreshold", 0.55).toFloat(),
                multiScalePyramid = true
            )
        } else {
            fallbackSettings
        }

        val modeStr = obj.optString("recommendedMode", "POINT_1")
        val recommendedMode = try { TrackingMode.valueOf(modeStr) } catch (e: Exception) { TrackingMode.POINT_1 }
        val confEst = obj.optDouble("confidenceEstimate", 0.90).toFloat()

        return AiDifficultyReport(
            difficultyLevel = level,
            difficultyScore = score,
            summaryTitle = summaryTitle,
            summaryAnalysis = summaryAnalysis,
            workflowAdvice = workflowAdvice,
            detectedChallenges = challengesList,
            recommendedSettings = recommendedSettings,
            recommendedMode = recommendedMode,
            confidenceEstimate = confEst,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Smart local heuristic advisor fallback
     */
    fun buildLocalHeuristicReport(
        clip: SampleClip,
        diag: OpticalFlowTrackerEngine.FrameDiagnostics,
        currentSettings: TrackerSettings,
        currentPoint: TrackerPoint?
    ): AiDifficultyReport {
        val challenges = mutableListOf<TrackingChallengeTag>()
        var difficultyScore = 2.0f

        // Check 1: High Velocity / Whip-Pan
        if (diag.maxVelocity > 15f || clip.category.contains("Motion", ignoreCase = true) || clip.id == "drift_car" || clip.id == "drone_flyby") {
            difficultyScore += 3.2f
            challenges.add(
                TrackingChallengeTag(
                    id = "high_velocity",
                    title = "High-Speed Motion & Whip-Pan",
                    category = "Kinematics",
                    severity = "HIGH",
                    description = "Fast object translation exceeds default 70px search box.",
                    recommendedFix = "Expand search radius to 95px and turn on Adaptive Velocity Search."
                )
            )
        }

        // Check 2: Low contrast or blur
        if (diag.contrastStd < 0.18f || diag.laplacianBlurScore < 10f || clip.id == "drone_flyby") {
            difficultyScore += 2.4f
            challenges.add(
                TrackingChallengeTag(
                    id = "low_contrast",
                    title = "Low Texture Contrast / Motion Blur",
                    category = "Image Salience",
                    severity = "MODERATE",
                    description = "Surrounding pixel gradients are smooth, risking pattern drift.",
                    recommendedFix = "Increase feature template size to 36px and snap to highest Harris corner."
                )
            )
        }

        // Check 3: Occlusion & Crossing Obstacles
        if (diag.estimatedOcclusionRisk > 0.3f || clip.id == "cyber_runner" || clip.id == "skater_rail") {
            difficultyScore += 2.6f
            challenges.add(
                TrackingChallengeTag(
                    id = "occlusion_risk",
                    title = "Intermittent Occlusion Risk",
                    category = "Obstruction",
                    severity = "HIGH",
                    description = "Foreground obstacles or limb cross-overs may obstruct the feature template.",
                    recommendedFix = "Enable Kalman Occlusion Recovery momentum to bridge lost frames."
                )
            )
        }

        // Check 4: Planar & Perspective Deformation
        if (clip.id == "billboard_replace" || clip.category.contains("Planar", ignoreCase = true)) {
            difficultyScore += 1.8f
            challenges.add(
                TrackingChallengeTag(
                    id = "perspective_warp",
                    title = "Perspective Surface Skew",
                    category = "Geometry",
                    severity = "MODERATE",
                    description = "Surface undergoes 3D rotation and geometric distortion.",
                    recommendedFix = "Switch to 4-Point Planar (Corner Pin) mode for exact perspective tracking."
                )
            )
        }

        val level = when {
            difficultyScore >= 7.0f -> TrackingDifficultyLevel.EXTREME
            difficultyScore >= 5.0f -> TrackingDifficultyLevel.DIFFICULT
            difficultyScore >= 3.0f -> TrackingDifficultyLevel.MODERATE
            else -> TrackingDifficultyLevel.OPTIMAL
        }

        val recMode = if (clip.id == "billboard_replace") TrackingMode.PLANAR_4
        else if (difficultyScore >= 6.0f && clip.id == "skater_rail") TrackingMode.TRANSFORM_2
        else TrackingMode.POINT_1

        val recSearchRadius = (if (diag.maxVelocity > 15f) 95f else 75f).coerceIn(60f, 130f)
        val recFeatureRadius = (if (diag.contrastStd < 0.2f) 34f else 28f).coerceIn(20f, 48f)

        val recommendedSettings = currentSettings.copy(
            searchRadius = recSearchRadius,
            featureRadius = recFeatureRadius,
            adaptiveSearch = true,
            occlusionRecovery = true,
            subpixelRefinement = true,
            stickyObjectLock = true,
            smoothingMode = if (difficultyScore >= 5.0f) SmoothingMode.HEAVY else SmoothingMode.MEDIUM,
            snapToHarrisCorners = true
        )

        val summaryTitle = when (level) {
            TrackingDifficultyLevel.EXTREME -> "High Complexity: Fast Motion & Occlusion"
            TrackingDifficultyLevel.DIFFICULT -> "Challenging Motion Profile Detected"
            TrackingDifficultyLevel.MODERATE -> "Moderate Optical Flow Variance"
            TrackingDifficultyLevel.OPTIMAL -> "Clean High-Contrast Tracking Target"
        }

        val summaryAnalysis = "Evaluated frame dynamics (${clip.title}). Detected ${challenges.size} specific tracking factors with a difficulty rating of ${String.format("%.1f", difficultyScore)}/10."
        val workflowAdvice = if (challenges.isNotEmpty()) {
            "Recommendation: ${challenges.first().recommendedFix} Lock tracker onto the highest-contrast vertex."
        } else {
            "Target has optimal contrast and steady trajectory. 1-Point tracking with Subpixel Interpolation will lock firmly."
        }

        return AiDifficultyReport(
            difficultyLevel = level,
            difficultyScore = difficultyScore.coerceIn(1.0f, 9.8f),
            summaryTitle = summaryTitle,
            summaryAnalysis = summaryAnalysis,
            workflowAdvice = workflowAdvice,
            detectedChallenges = challenges,
            recommendedSettings = recommendedSettings,
            recommendedMode = recMode,
            confidenceEstimate = (1.0f - (difficultyScore * 0.05f)).coerceIn(0.65f, 0.98f)
        )
    }

    private fun bitmapToBase64(bitmap: Bitmap, maxWidth: Int = 480): String {
        val scale = (maxWidth.toFloat() / bitmap.width.toFloat()).coerceAtMost(1.0f)
        val scaledW = (bitmap.width * scale).toInt()
        val scaledH = (bitmap.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
