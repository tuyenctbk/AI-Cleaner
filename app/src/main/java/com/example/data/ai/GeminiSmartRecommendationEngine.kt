package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.R
import com.example.data.model.DuplicateGroup
import com.example.data.model.MediaItem
import com.example.data.model.formatFileSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Gemini-powered Smart Recommendations engine:
 * Analyzes media library metadata + sample blurry/low-res thumbnails beyond duplicates
 * using the 3-tier fallback pipeline (Primary Cloud -> Economy Lite -> On-Device Deterministic).
 */
class GeminiSmartRecommendationEngine(
    private val context: Context,
    private val quotaManager: AiQuotaManager = AiQuotaManager(context)
) {

    private companion object {
        const val TAG = "SmartRecommendations"
        const val MAX_SAMPLE_THUMBNAILS = 3
        const val SAMPLE_MAX_DIM_PX = 384
        const val SAMPLE_JPEG_QUALITY = 70
        const val AI_TIMEOUT_MS = 45_000L
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private data class CachedRecommendationResult(
        val timestampMillis: Long,
        val recommendations: List<SmartRecommendation>
    )

    private val fingerprintCache = ConcurrentHashMap<String, CachedRecommendationResult>()

    private data class CloudCallOutcome(
        val recommendations: List<SmartRecommendation>?,
        val httpCode: Int,
        val errorMessage: String? = null
    )

    suspend fun analyzeLibrary(
        allMedia: List<MediaItem>,
        duplicateGroups: List<DuplicateGroup>,
        smartExpiryDays: Int = 60,
        forceEcoLite: Boolean = false,
        forceLocal: Boolean = false,
        bypassCache: Boolean = false
    ): SmartRecommendationAnalysisResult = withContext(Dispatchers.IO) {
        val digest = MediaLibraryRecommendationAnalyzer.buildLibraryDigest(
            allMedia = allMedia,
            duplicateGroups = duplicateGroups,
            smartExpiryDays = smartExpiryDays
        )
        val strings = loadLocalizedStrings()

        if (digest.allActionableItemsCount == 0) {
            return@withContext SmartRecommendationAnalysisResult(
                recommendations = emptyList(),
                digest = digest,
                executionTier = AiExecutionTier.ON_DEVICE_LOCAL
            )
        }

        val fingerprint = buildFingerprint(digest, smartExpiryDays, forceEcoLite, forceLocal)
        if (!bypassCache) {
            val cached = fingerprintCache[fingerprint]
            if (cached != null && (System.currentTimeMillis() - cached.timestampMillis) <= AiModelConfig.FINGERPRINT_CACHE_TTL_MS) {
                quotaManager.recordValueRoutineGenerated(fromCache = true)
                return@withContext SmartRecommendationAnalysisResult(
                    recommendations = cached.recommendations,
                    digest = digest,
                    executionTier = AiExecutionTier.CACHED,
                    fromCache = true
                )
            }
        }

        val (apiKey, keySource) = quotaManager.resolveActiveApiKey()

        // Tier 3 zero-cost path when offline, unconfigured, or forced local
        if (forceLocal || keySource == AiKeySource.NONE || apiKey.isBlank()) {
            val local = MediaLibraryRecommendationAnalyzer.buildDeterministicRecommendations(
                digest = digest,
                smartExpiryDays = smartExpiryDays,
                strings = strings,
                executionTier = AiExecutionTier.ON_DEVICE_LOCAL
            )
            cacheResult(fingerprint, local)
            quotaManager.recordValueRoutineGenerated(fromCache = false)
            return@withContext SmartRecommendationAnalysisResult(
                recommendations = local,
                digest = digest,
                executionTier = AiExecutionTier.ON_DEVICE_LOCAL
            )
        }

        val snapshotBefore = quotaManager.getSnapshot()
        if (!quotaManager.tryConsumeCloudCredit()) {
            val local = MediaLibraryRecommendationAnalyzer.buildDeterministicRecommendations(
                digest = digest,
                smartExpiryDays = smartExpiryDays,
                strings = strings,
                executionTier = AiExecutionTier.ON_DEVICE_LOCAL
            )
            return@withContext SmartRecommendationAnalysisResult(
                recommendations = local,
                digest = digest,
                executionTier = AiExecutionTier.ON_DEVICE_LOCAL,
                hitRateLimit = true
            )
        }

        val prompt = MediaLibraryRecommendationAnalyzer.buildPrompt(digest, smartExpiryDays)
        val sampleThumbnailsB64 = encodeSampleThumbnails(digest)

        val startWithLite = forceEcoLite ||
            snapshotBefore.ecoLiteMode ||
            (!snapshotBefore.hasUserByokKey && snapshotBefore.usageRatio >= AiModelConfig.SOFT_NUDGE_THRESHOLD_RATIO)

        var hitRateLimit = false
        var cascadedToLite = false

        try {
            if (!startWithLite) {
                val primaryOutcome = withTimeout(AI_TIMEOUT_MS) {
                    executeCloudCall(
                        modelId = AiModelConfig.PRIMARY_REASONING_MODEL,
                        apiKey = apiKey,
                        prompt = prompt,
                        sampleImagesB64 = sampleThumbnailsB64,
                        digest = digest,
                        tier = AiExecutionTier.PRIMARY_CLOUD
                    )
                }
                if (!primaryOutcome.recommendations.isNullOrEmpty()) {
                    val complete = mergeWithDeterministicCoverage(
                        geminiList = primaryOutcome.recommendations,
                        digest = digest,
                        smartExpiryDays = smartExpiryDays,
                        strings = strings,
                        tier = AiExecutionTier.PRIMARY_CLOUD
                    )
                    cacheResult(fingerprint, complete)
                    quotaManager.recordValueRoutineGenerated(fromCache = false)
                    return@withContext SmartRecommendationAnalysisResult(
                        recommendations = complete,
                        digest = digest,
                        executionTier = AiExecutionTier.PRIMARY_CLOUD
                    )
                }
                if (primaryOutcome.httpCode == 429) hitRateLimit = true
                if (!primaryOutcome.errorMessage.isNullOrBlank()) {
                    Log.w(TAG, "Primary cloud model fallback: ${primaryOutcome.errorMessage}")
                }
                cascadedToLite = true
                quotaManager.recordFallbackCascade()
            }

            // Tier 2: High-Efficiency Economy Lite
            val liteOutcome = withTimeout(AI_TIMEOUT_MS) {
                executeCloudCall(
                    modelId = AiModelConfig.ECONOMY_LITE_MODEL,
                    apiKey = apiKey,
                    prompt = prompt,
                    sampleImagesB64 = sampleThumbnailsB64,
                    digest = digest,
                    tier = AiExecutionTier.ECONOMY_LITE
                )
            }
            if (!liteOutcome.recommendations.isNullOrEmpty()) {
                val complete = mergeWithDeterministicCoverage(
                    geminiList = liteOutcome.recommendations,
                    digest = digest,
                    smartExpiryDays = smartExpiryDays,
                    strings = strings,
                    tier = AiExecutionTier.ECONOMY_LITE
                )
                cacheResult(fingerprint, complete)
                quotaManager.recordValueRoutineGenerated(fromCache = false)
                return@withContext SmartRecommendationAnalysisResult(
                    recommendations = complete,
                    digest = digest,
                    executionTier = AiExecutionTier.ECONOMY_LITE,
                    cascadedToLite = cascadedToLite,
                    hitRateLimit = hitRateLimit
                )
            }
            if (liteOutcome.httpCode == 429) hitRateLimit = true
            if (!liteOutcome.errorMessage.isNullOrBlank()) {
                Log.w(TAG, "Economy lite model fallback: ${liteOutcome.errorMessage}")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Smart recommendations timed out", e)
        } catch (e: CancellationException) {
            quotaManager.refundCloudCredit()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Smart recommendations cloud analysis failed", e)
        }

        // Atomic Refund on cloud failure + seamless Tier 3 On-Device Fallback
        quotaManager.refundCloudCredit()
        quotaManager.recordFallbackCascade()

        val fallback = MediaLibraryRecommendationAnalyzer.buildDeterministicRecommendations(
            digest = digest,
            smartExpiryDays = smartExpiryDays,
            strings = strings,
            executionTier = AiExecutionTier.ON_DEVICE_LOCAL
        )
        cacheResult(fingerprint, fallback)
        quotaManager.recordValueRoutineGenerated(fromCache = false)

        return@withContext SmartRecommendationAnalysisResult(
            recommendations = fallback,
            digest = digest,
            executionTier = AiExecutionTier.ON_DEVICE_LOCAL,
            cascadedToLite = cascadedToLite,
            hitRateLimit = hitRateLimit,
            errorMessage = null
        )
    }

    private fun executeCloudCall(
        modelId: String,
        apiKey: String,
        prompt: String,
        sampleImagesB64: List<String>,
        digest: MediaLibraryDigest,
        tier: AiExecutionTier
    ): CloudCallOutcome {
        return try {
            val partsArr = JSONArray().apply {
                put(JSONObject().put("text", prompt))
                sampleImagesB64.forEach { b64 ->
                    put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject()
                                .put("mime_type", "image/jpeg")
                                .put("data", b64)
                        )
                    )
                }
            }

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", partsArr)))
                put(
                    "generationConfig",
                    JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("temperature", 0.2)
                )
            }

            val request = Request.Builder()
                .url(AiModelConfig.endpointForModel(modelId, apiKey))
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful && bodyStr.isNotBlank()) {
                    val parsed = MediaLibraryRecommendationAnalyzer.parseGeminiRecommendationsJson(
                        rawText = bodyStr,
                        digest = digest,
                        executionTier = tier
                    )
                    if (parsed.isNotEmpty()) {
                        CloudCallOutcome(recommendations = parsed, httpCode = response.code)
                    } else {
                        CloudCallOutcome(
                            recommendations = null,
                            httpCode = response.code,
                            errorMessage = "Empty or unrecognized Gemini JSON payload"
                        )
                    }
                } else {
                    CloudCallOutcome(
                        recommendations = null,
                        httpCode = response.code,
                        errorMessage = "HTTP ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud call error on $modelId", e)
            CloudCallOutcome(recommendations = null, httpCode = -1, errorMessage = e.localizedMessage)
        }
    }

    private fun mergeWithDeterministicCoverage(
        geminiList: List<SmartRecommendation>,
        digest: MediaLibraryDigest,
        smartExpiryDays: Int,
        strings: LocalizedRecommendationStrings,
        tier: AiExecutionTier
    ): List<SmartRecommendation> {
        val covered = geminiList.map { it.category }.toSet()
        val extraLocal = MediaLibraryRecommendationAnalyzer
            .buildDeterministicRecommendations(digest, smartExpiryDays, strings, tier)
            .filter { it.category !in covered }
        return (geminiList + extraLocal)
            .sortedBy { it.priority.ordinal }
    }

    private fun encodeSampleThumbnails(digest: MediaLibraryDigest): List<String> {
        val candidates = (digest.blurryPhotos.take(2) + digest.lowQualityScreenshots.take(2))
            .distinctBy { it.id }
            .take(MAX_SAMPLE_THUMBNAILS)
        val encoded = mutableListOf<String>()
        for (item in candidates) {
            val uriStr = item.uriString
            if (uriStr.isBlank()) continue
            val bitmap = decodeSampledBitmap(Uri.parse(uriStr), SAMPLE_MAX_DIM_PX) ?: continue
            try {
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, SAMPLE_JPEG_QUALITY, out)
                    encoded.add(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                }
            } catch (_: Exception) {
                // Skip unreadable thumbnail gracefully
            } finally {
                bitmap.recycle()
            }
        }
        return encoded
    }

    private fun decodeSampledBitmap(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val longest = max(bounds.outWidth, bounds.outHeight)
            var inSampleSize = 1
            while (longest / (inSampleSize * 2) >= maxDim) {
                inSampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildFingerprint(
        digest: MediaLibraryDigest,
        smartExpiryDays: Int,
        forceEcoLite: Boolean,
        forceLocal: Boolean
    ): String {
        val raw = "${digest.blurryPhotos.size}:${digest.blurryBytes}|" +
            "${digest.lowQualityScreenshots.size}:${digest.lowQualityScreenshotBytes}|" +
            "${digest.largeVideos.size}:${digest.largeVideosBytes}|" +
            "${digest.oldDownloads.size}:${digest.oldDownloadsBytes}|" +
            "${digest.lowResPhotos.size}:$smartExpiryDays|$forceEcoLite|$forceLocal"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun cacheResult(fingerprint: String, list: List<SmartRecommendation>) {
        if (fingerprintCache.size > 12) fingerprintCache.clear()
        fingerprintCache[fingerprint] = CachedRecommendationResult(System.currentTimeMillis(), list)
    }

    private fun loadLocalizedStrings(): LocalizedRecommendationStrings {
        return LocalizedRecommendationStrings(
            blurryTitle = { count, size -> context.getString(R.string.smart_rec_blurry_title_fmt, count, size) },
            blurryReason = { count, avg, size -> context.getString(R.string.smart_rec_blurry_reason_fmt, count, avg, size) },
            blurryAction = context.getString(R.string.smart_rec_blurry_action),
            lowResShotTitle = { count, size -> context.getString(R.string.smart_rec_shot_title_fmt, count, size) },
            lowResShotReason = { count, size -> context.getString(R.string.smart_rec_shot_reason_fmt, count, size) },
            lowResShotAction = context.getString(R.string.smart_rec_shot_action),
            largeVideoTitle = { count, size -> context.getString(R.string.smart_rec_video_title_fmt, count, size) },
            largeVideoReason = { count, size -> context.getString(R.string.smart_rec_video_reason_fmt, count, size) },
            largeVideoAction = context.getString(R.string.smart_rec_video_action),
            oldDownloadTitle = { count, days, _ -> context.getString(R.string.smart_rec_download_title_fmt, count, days) },
            oldDownloadReason = { count, days, size -> context.getString(R.string.smart_rec_download_reason_fmt, count, days, size) },
            oldDownloadAction = context.getString(R.string.smart_rec_download_action),
            lowResPhotoTitle = { count, size -> context.getString(R.string.smart_rec_lowres_title_fmt, count, size) },
            lowResPhotoReason = { count, size -> context.getString(R.string.smart_rec_lowres_reason_fmt, count, size) },
            lowResPhotoAction = context.getString(R.string.smart_rec_lowres_action)
        )
    }
}
