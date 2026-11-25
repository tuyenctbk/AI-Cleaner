package com.example.data.ai

import android.content.Context
import com.example.R
import com.example.data.model.AiInsightRoutine
import com.example.data.model.AiRoutineAction
import com.example.data.model.StorageStats
import com.example.data.model.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 3-Tier Model-Agnostic AI Engine for AI Cleaner:
 * - Tier 1: Primary Standard Cloud (AiModelConfig.PRIMARY_REASONING_MODEL)
 * - Tier 2: High-Efficiency Economy Lite (AiModelConfig.ECONOMY_LITE_MODEL) — auto-activated on >=80% quota, Eco Lite Mode, or HTTP 429
 * - Tier 3: Zero-Cost On-Device Deterministic Analyzer (works offline, enriched with ML Kit OCR & Face stats)
 *
 * Includes 45-second fingerprint deduplication cache and atomic credit deduct-with-refund via AiQuotaManager.
 */
class GeminiCleanerService(
    private val context: Context,
    private val quotaManager: AiQuotaManager = AiQuotaManager(context)
) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private data class CachedEntry(
        val timestampMillis: Long,
        val routines: List<AiInsightRoutine>
    )

    private val fingerprintCache = ConcurrentHashMap<String, CachedEntry>()

    private data class CloudCallOutcome(
        val routines: List<AiInsightRoutine>?,
        val httpCode: Int
    )

    suspend fun generateStorageRoutines(
        stats: StorageStats,
        cloudPhotosCount: Int,
        cloudPhotosBytes: Long = 0L,
        duplicateBytes: Long = 0L,
        screenshotBytes: Long = 0L,
        safeScreenshotCount: Int = 0,
        safeScreenshotBytes: Long = 0L,
        largeMediaBytes: Long = 0L,
        customQuery: String = "",
        forceEcoLite: Boolean = false,
        forceLocal: Boolean = false,
        bypassCache: Boolean = false
    ): AiGenerationResult = withContext(Dispatchers.IO) {
        val fingerprint = buildRequestFingerprint(
            stats = stats,
            cloudPhotosCount = cloudPhotosCount,
            safeScreenshotCount = safeScreenshotCount,
            customQuery = customQuery,
            forceEcoLite = forceEcoLite,
            forceLocal = forceLocal
        )

        // Token Hygiene: 45-second Fingerprint Deduplication Cache (0 extra cloud tokens)
        if (!bypassCache) {
            val cached = fingerprintCache[fingerprint]
            if (cached != null && (System.currentTimeMillis() - cached.timestampMillis) <= AiModelConfig.FINGERPRINT_CACHE_TTL_MS) {
                quotaManager.recordValueRoutineGenerated(fromCache = true)
                return@withContext AiGenerationResult(
                    routines = cached.routines,
                    executionTier = AiExecutionTier.CACHED,
                    fromCache = true
                )
            }
        }

        val (apiKey, keySource) = quotaManager.resolveActiveApiKey()

        // Explicit local request or no cloud key configured -> Tier 3 On-Device Zero-Cost Engine
        if (forceLocal || keySource == AiKeySource.NONE || apiKey.isBlank()) {
            val localRoutines = generateLocalSmartFallbackRoutines(
                stats = stats,
                cloudPhotosCount = cloudPhotosCount,
                cloudPhotosBytes = cloudPhotosBytes,
                duplicateBytes = duplicateBytes,
                screenshotBytes = screenshotBytes,
                safeScreenshotCount = safeScreenshotCount,
                safeScreenshotBytes = safeScreenshotBytes,
                largeMediaBytes = largeMediaBytes,
                customQuery = customQuery
            )
            cacheResult(fingerprint, localRoutines)
            quotaManager.recordValueRoutineGenerated(fromCache = false)
            return@withContext AiGenerationResult(
                routines = localRoutines,
                executionTier = AiExecutionTier.ON_DEVICE_LOCAL
            )
        }

        // Check Quota Ledger (atomic deduct-with-refund; BYOK passes through directly)
        val snapshotBefore = quotaManager.getSnapshot()
        if (!quotaManager.tryConsumeCloudCredit()) {
            val localRoutines = generateLocalSmartFallbackRoutines(
                stats = stats,
                cloudPhotosCount = cloudPhotosCount,
                cloudPhotosBytes = cloudPhotosBytes,
                duplicateBytes = duplicateBytes,
                screenshotBytes = screenshotBytes,
                safeScreenshotCount = safeScreenshotCount,
                safeScreenshotBytes = safeScreenshotBytes,
                largeMediaBytes = largeMediaBytes,
                customQuery = customQuery
            )
            quotaManager.recordValueRoutineGenerated(fromCache = false)
            return@withContext AiGenerationResult(
                routines = localRoutines,
                executionTier = AiExecutionTier.ON_DEVICE_LOCAL,
                hitRateLimit = true
            )
        }

        val prompt = buildStoragePrompt(
            stats = stats,
            cloudPhotosCount = cloudPhotosCount,
            cloudPhotosBytes = cloudPhotosBytes,
            duplicateBytes = duplicateBytes,
            screenshotBytes = screenshotBytes,
            safeScreenshotCount = safeScreenshotCount,
            safeScreenshotBytes = safeScreenshotBytes,
            largeMediaBytes = largeMediaBytes,
            customQuery = customQuery
        )

        // Decide starting tier: Tier 2 (Economy Lite) if Eco Lite mode, forced, or >=80% quota used
        val startWithLite = forceEcoLite ||
            snapshotBefore.ecoLiteMode ||
            (!snapshotBefore.hasUserByokKey && snapshotBefore.usageRatio >= AiModelConfig.SOFT_NUDGE_THRESHOLD_RATIO)

        var hitRateLimit = false
        var cascadedToLite = false

        if (!startWithLite) {
            val primaryOutcome = executeCloudCall(AiModelConfig.PRIMARY_REASONING_MODEL, apiKey, prompt)
            if (!primaryOutcome.routines.isNullOrEmpty()) {
                cacheResult(fingerprint, primaryOutcome.routines)
                quotaManager.recordValueRoutineGenerated(fromCache = false)
                return@withContext AiGenerationResult(
                    routines = primaryOutcome.routines,
                    executionTier = AiExecutionTier.PRIMARY_CLOUD
                )
            }
            if (primaryOutcome.httpCode == 429) {
                hitRateLimit = true
            }
            // Auto-Cascade Tier 1 -> Tier 2 Economy Lite
            cascadedToLite = true
            quotaManager.recordFallbackCascade()
        }

        // Tier 2: High-Efficiency Economy Lite
        val liteOutcome = executeCloudCall(AiModelConfig.ECONOMY_LITE_MODEL, apiKey, prompt)
        if (!liteOutcome.routines.isNullOrEmpty()) {
            cacheResult(fingerprint, liteOutcome.routines)
            quotaManager.recordValueRoutineGenerated(fromCache = false)
            return@withContext AiGenerationResult(
                routines = liteOutcome.routines,
                executionTier = AiExecutionTier.ECONOMY_LITE,
                cascadedToLite = cascadedToLite,
                hitRateLimit = hitRateLimit
            )
        }

        if (liteOutcome.httpCode == 429) {
            hitRateLimit = true
        }

        // Atomic Refund: network/API failure must NEVER burn user quota
        quotaManager.refundCloudCredit()
        quotaManager.recordFallbackCascade()

        // Tier 3: Zero-Cost On-Device Deterministic Fallback
        val localRoutines = generateLocalSmartFallbackRoutines(
            stats = stats,
            cloudPhotosCount = cloudPhotosCount,
            cloudPhotosBytes = cloudPhotosBytes,
            duplicateBytes = duplicateBytes,
            screenshotBytes = screenshotBytes,
            safeScreenshotCount = safeScreenshotCount,
            safeScreenshotBytes = safeScreenshotBytes,
            largeMediaBytes = largeMediaBytes,
            customQuery = customQuery
        )
        cacheResult(fingerprint, localRoutines)
        quotaManager.recordValueRoutineGenerated(fromCache = false)
        return@withContext AiGenerationResult(
            routines = localRoutines,
            executionTier = AiExecutionTier.ON_DEVICE_LOCAL,
            cascadedToLite = cascadedToLite,
            hitRateLimit = hitRateLimit
        )
    }

    /**
     * Live connection verifier for Settings BYOK API key input using the Economy Lite tier.
     */
    suspend fun testApiKeyConnection(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.length < 16) return@withContext false
        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Reply with OK")))
                ))
                put("generationConfig", JSONObject().put("maxOutputTokens", 4))
            }
            val request = Request.Builder()
                .url(AiModelConfig.endpointForModel(AiModelConfig.ECONOMY_LITE_MODEL, trimmed))
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun executeCloudCall(modelId: String, apiKey: String, prompt: String): CloudCallOutcome {
        return try {
            val requestJson = JSONObject().apply {
                val contentsArr = JSONArray().apply {
                    val partsArr = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put(JSONObject().put("parts", partsArr))
                }
                put("contents", contentsArr)
            }

            val request = Request.Builder()
                .url(AiModelConfig.endpointForModel(modelId, apiKey))
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val code = response.code
                val responseBodyStr = response.body?.string()
                if (response.isSuccessful && !responseBodyStr.isNullOrBlank()) {
                    val routines = parseGeminiResponse(responseBodyStr)
                    CloudCallOutcome(routines = routines.ifEmpty { null }, httpCode = code)
                } else {
                    CloudCallOutcome(routines = null, httpCode = code)
                }
            }
        } catch (_: Exception) {
            CloudCallOutcome(routines = null, httpCode = -1)
        }
    }

    private fun buildStoragePrompt(
        stats: StorageStats,
        cloudPhotosCount: Int,
        cloudPhotosBytes: Long,
        duplicateBytes: Long,
        screenshotBytes: Long,
        safeScreenshotCount: Int,
        safeScreenshotBytes: Long,
        largeMediaBytes: Long,
        customQuery: String
    ): String {
        return """
            You are an expert AI Storage Cleaner Assistant for Android.
            Analyze the following real device storage metrics:
            - Total Storage: ${stats.formattedTotal}
            - Used Storage: ${stats.formattedUsed} (${stats.usedPercent}% used)
            - Free Storage: ${stats.formattedFree}
            - Junk Caches: ${stats.formattedJunk}
            - Photos: ${stats.photoCount} files (${formatFileSize(stats.photoBytes)})
            - Videos: ${stats.videoCount} files (${formatFileSize(stats.videoBytes)})
            - Cloud Backed Up Local Photos: $cloudPhotosCount files (${formatFileSize(cloudPhotosBytes)})
            - Duplicate Photos: ${stats.duplicateCount} files (${formatFileSize(duplicateBytes)})
            - Screenshots: ${stats.screenshotCount} files (${formatFileSize(screenshotBytes)})
            - Safe-to-Clean OTP/Meme Screenshots (ML Kit OCR): $safeScreenshotCount files (${formatFileSize(safeScreenshotBytes)})
            - Large Files: ${stats.largeFileCount} files (${formatFileSize(largeMediaBytes)})
            - User Custom Query: "$customQuery"

            Generate up to 3 smart, actionable cleanup routines using ONLY the actual byte sizes above.
            Valid actionType values: DELETE_CLOUD_BACKUPS, DELETE_DUPLICATES, DELETE_SAFE_SCREENSHOTS, DELETE_SCREENSHOTS, COMPRESS_4K_VIDEOS, CLEAR_WHATSAPP_CACHE, SAFE_QUICK_CLEAN.
            Return ONLY a valid JSON array with objects containing:
            [
              {
                "id": "routine_1",
                "title": "Short catchy title",
                "summary": "1 sentence description",
                "potentialSavingsBytes": 0,
                "confidenceScore": 95,
                "categoryTag": "Category",
                "actionType": "SAFE_QUICK_CLEAN",
                "explanation": "Why this is safe and effective",
                "itemCount": 1
              }
            ]
        """.trimIndent()
    }

    private fun buildRequestFingerprint(
        stats: StorageStats,
        cloudPhotosCount: Int,
        safeScreenshotCount: Int,
        customQuery: String,
        forceEcoLite: Boolean,
        forceLocal: Boolean
    ): String {
        val raw = "${stats.usedBytes}|${stats.junkBytes}|${stats.duplicateCount}|${stats.screenshotCount}|$safeScreenshotCount|$cloudPhotosCount|${customQuery.trim().lowercase()}|$forceEcoLite|$forceLocal"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cacheResult(fingerprint: String, routines: List<AiInsightRoutine>) {
        if (fingerprintCache.size > 16) {
            fingerprintCache.clear()
        }
        fingerprintCache[fingerprint] = CachedEntry(System.currentTimeMillis(), routines)
    }

    private fun parseGeminiResponse(jsonStr: String): List<AiInsightRoutine> {
        val list = mutableListOf<AiInsightRoutine>()
        try {
            val rootObj = JSONObject(jsonStr)
            val candidates = rootObj.optJSONArray("candidates") ?: return list
            val firstCandidate = candidates.optJSONObject(0) ?: return list
            val content = firstCandidate.optJSONObject("content") ?: return list
            val parts = content.optJSONArray("parts") ?: return list
            val text = parts.optJSONObject(0)?.optString("text") ?: return list

            val startIdx = text.indexOf('[')
            val endIdx = text.lastIndexOf(']')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val arrayStr = text.substring(startIdx, endIdx + 1)
                val jsonArr = JSONArray(arrayStr)
                for (i in 0 until jsonArr.length()) {
                    val item = jsonArr.optJSONObject(i) ?: continue
                    val actionStr = item.optString("actionType", "SAFE_QUICK_CLEAN")
                    val action = try {
                        AiRoutineAction.valueOf(actionStr)
                    } catch (_: Exception) {
                        AiRoutineAction.SAFE_QUICK_CLEAN
                    }

                    list.add(
                        AiInsightRoutine(
                            id = item.optString("id", "routine_$i"),
                            title = item.optString("title", context.getString(R.string.ai_fallback_title)),
                            summary = item.optString("summary", context.getString(R.string.ai_fallback_summary)),
                            potentialSavingsBytes = item.optLong("potentialSavingsBytes", 0L).coerceAtLeast(0L),
                            confidenceScore = item.optInt("confidenceScore", 95).coerceIn(0, 100),
                            categoryTag = item.optString("categoryTag", context.getString(R.string.ai_fallback_tag)),
                            actionType = action,
                            explanation = item.optString("explanation", context.getString(R.string.ai_fallback_explanation)),
                            itemCount = item.optInt("itemCount", 0).coerceAtLeast(0)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore malformed JSON responses
        }
        return list
    }

    fun generateLocalSmartFallbackRoutines(
        stats: StorageStats,
        cloudPhotosCount: Int,
        cloudPhotosBytes: Long = 0L,
        duplicateBytes: Long = 0L,
        screenshotBytes: Long = 0L,
        safeScreenshotCount: Int = 0,
        safeScreenshotBytes: Long = 0L,
        largeMediaBytes: Long = 0L,
        customQuery: String = ""
    ): List<AiInsightRoutine> {
        val routines = mutableListOf<AiInsightRoutine>()

        // 1. On-Device ML Kit Safe OTP/Meme Screenshot Routine (keeps receipts & documents!)
        if (safeScreenshotCount > 0 && safeScreenshotBytes > 0L) {
            val formattedSafe = formatFileSize(safeScreenshotBytes)
            routines.add(
                AiInsightRoutine(
                    id = "routine_mlkit_safe_screenshots",
                    title = context.getString(R.string.ai_local_safe_screenshots_title_fmt, safeScreenshotCount, formattedSafe),
                    summary = context.getString(R.string.ai_local_safe_screenshots_summary),
                    potentialSavingsBytes = safeScreenshotBytes,
                    confidenceScore = 99,
                    categoryTag = context.getString(R.string.ai_local_safe_screenshots_tag),
                    actionType = AiRoutineAction.DELETE_SAFE_SCREENSHOTS,
                    explanation = context.getString(R.string.ai_local_safe_screenshots_explanation_fmt, safeScreenshotCount, formattedSafe),
                    itemCount = safeScreenshotCount
                )
            )
        }

        if (cloudPhotosCount > 0 && cloudPhotosBytes > 0L) {
            val formattedCloud = formatFileSize(cloudPhotosBytes)
            routines.add(
                AiInsightRoutine(
                    id = "routine_cloud_backup",
                    title = context.getString(R.string.ai_local_cloud_title_fmt, cloudPhotosCount),
                    summary = context.getString(R.string.ai_local_cloud_summary),
                    potentialSavingsBytes = cloudPhotosBytes,
                    confidenceScore = 99,
                    categoryTag = context.getString(R.string.ai_local_cloud_tag),
                    actionType = AiRoutineAction.DELETE_CLOUD_BACKUPS,
                    explanation = context.getString(R.string.ai_local_cloud_explanation_fmt, cloudPhotosCount, formattedCloud),
                    itemCount = cloudPhotosCount
                )
            )
        }

        if (stats.junkBytes > 0L) {
            routines.add(
                AiInsightRoutine(
                    id = "routine_safe_quick_clean",
                    title = context.getString(R.string.ai_local_quick_title_fmt, stats.formattedJunk),
                    summary = context.getString(R.string.ai_local_quick_summary),
                    potentialSavingsBytes = stats.junkBytes,
                    confidenceScore = 100,
                    categoryTag = context.getString(R.string.ai_local_quick_tag),
                    actionType = AiRoutineAction.SAFE_QUICK_CLEAN,
                    explanation = context.getString(R.string.ai_local_quick_explanation_fmt, stats.formattedJunk),
                    itemCount = 1
                )
            )
        }

        if (stats.duplicateCount > 0 && duplicateBytes > 0L) {
            val formattedDup = formatFileSize(duplicateBytes)
            routines.add(
                AiInsightRoutine(
                    id = "routine_duplicates",
                    title = context.getString(R.string.ai_local_dup_title_fmt, stats.duplicateCount, formattedDup),
                    summary = context.getString(R.string.ai_local_dup_summary),
                    potentialSavingsBytes = duplicateBytes,
                    confidenceScore = 98,
                    categoryTag = context.getString(R.string.ai_local_dup_tag),
                    actionType = AiRoutineAction.DELETE_DUPLICATES,
                    explanation = context.getString(R.string.ai_local_dup_explanation_fmt, stats.duplicateCount),
                    itemCount = stats.duplicateCount
                )
            )
        }

        val heavySourceBytes = if (largeMediaBytes > 0L) largeMediaBytes else stats.videoBytes
        if (stats.largeFileCount > 0 && heavySourceBytes > 0L) {
            val compressSavings = (heavySourceBytes * 0.5f).toLong()
            routines.add(
                AiInsightRoutine(
                    id = "routine_4k_compress",
                    title = context.getString(R.string.ai_local_compress_title_fmt, stats.largeFileCount, formatFileSize(compressSavings)),
                    summary = context.getString(R.string.ai_local_compress_summary),
                    potentialSavingsBytes = compressSavings,
                    confidenceScore = 96,
                    categoryTag = context.getString(R.string.ai_local_compress_tag),
                    actionType = AiRoutineAction.COMPRESS_4K_VIDEOS,
                    explanation = context.getString(
                        R.string.ai_local_compress_explanation_fmt,
                        stats.largeFileCount,
                        formatFileSize(heavySourceBytes),
                        formatFileSize(compressSavings)
                    ),
                    itemCount = stats.largeFileCount
                )
            )
        }

        if (stats.screenshotCount > 0 && screenshotBytes > 0L && safeScreenshotCount == 0) {
            val formattedShots = formatFileSize(screenshotBytes)
            routines.add(
                AiInsightRoutine(
                    id = "routine_old_screenshots",
                    title = context.getString(R.string.ai_local_screenshots_title_fmt, stats.screenshotCount, formattedShots),
                    summary = context.getString(R.string.ai_local_screenshots_summary),
                    potentialSavingsBytes = screenshotBytes,
                    confidenceScore = 94,
                    categoryTag = context.getString(R.string.ai_local_screenshots_tag),
                    actionType = AiRoutineAction.DELETE_SCREENSHOTS,
                    explanation = context.getString(R.string.ai_local_screenshots_explanation_fmt, stats.screenshotCount, formattedShots),
                    itemCount = stats.screenshotCount
                )
            )
        }

        if (customQuery.isNotBlank()) {
            val lower = customQuery.lowercase()
            val (matchedAction, querySavings, itemCount) = when {
                lower.contains("otp") || lower.contains("meme") || lower.contains("code") -> Triple(
                    if (safeScreenshotCount > 0) AiRoutineAction.DELETE_SAFE_SCREENSHOTS else AiRoutineAction.DELETE_SCREENSHOTS,
                    if (safeScreenshotCount > 0) safeScreenshotBytes else screenshotBytes,
                    if (safeScreenshotCount > 0) safeScreenshotCount else stats.screenshotCount
                )
                lower.contains("screenshot") || lower.contains("receipt") -> Triple(
                    AiRoutineAction.DELETE_SCREENSHOTS,
                    screenshotBytes,
                    stats.screenshotCount
                )
                lower.contains("duplicate") || lower.contains("similar") || lower.contains("blur") || lower.contains("blink") -> Triple(
                    AiRoutineAction.DELETE_DUPLICATES,
                    duplicateBytes,
                    stats.duplicateCount
                )
                lower.contains("video") || lower.contains("compress") || lower.contains("4k") -> Triple(
                    AiRoutineAction.COMPRESS_4K_VIDEOS,
                    (heavySourceBytes * 0.5f).toLong(),
                    stats.largeFileCount
                )
                lower.contains("cloud") || lower.contains("backup") || lower.contains("google photo") -> Triple(
                    AiRoutineAction.DELETE_CLOUD_BACKUPS,
                    cloudPhotosBytes,
                    cloudPhotosCount
                )
                lower.contains("whatsapp") || lower.contains("cache") || lower.contains("app") -> Triple(
                    AiRoutineAction.CLEAR_WHATSAPP_CACHE,
                    stats.junkBytes,
                    if (stats.junkBytes > 0L) 1 else 0
                )
                else -> Triple(AiRoutineAction.SAFE_QUICK_CLEAN, stats.junkBytes, if (stats.junkBytes > 0L) 1 else 0)
            }
            routines.add(
                0,
                AiInsightRoutine(
                    id = "routine_custom_query",
                    title = context.getString(R.string.ai_local_custom_title_fmt, customQuery),
                    summary = context.getString(R.string.ai_local_custom_summary),
                    potentialSavingsBytes = querySavings,
                    confidenceScore = 97,
                    categoryTag = context.getString(R.string.ai_local_custom_tag),
                    actionType = matchedAction,
                    explanation = context.getString(R.string.ai_local_custom_explanation_fmt, customQuery),
                    itemCount = itemCount
                )
            )
        }

        if (routines.isEmpty()) {
            routines.add(
                AiInsightRoutine(
                    id = "routine_system_optimized",
                    title = context.getString(R.string.ai_local_healthy_title),
                    summary = context.getString(R.string.ai_local_healthy_summary),
                    potentialSavingsBytes = 0L,
                    confidenceScore = 100,
                    categoryTag = context.getString(R.string.ai_local_healthy_tag),
                    actionType = AiRoutineAction.SAFE_QUICK_CLEAN,
                    explanation = context.getString(R.string.ai_local_healthy_explanation),
                    itemCount = 0
                )
            )
        }

        return routines
    }
}
