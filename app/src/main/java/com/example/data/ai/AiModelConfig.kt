package com.example.data.ai

import com.example.data.model.AiInsightRoutine

/**
 * Centralized model-agnostic capability configuration.
 * Never hardcode model IDs inside UI screens or ViewModels; update capability roles here.
 */
object AiModelConfig {
    // Tier 1 — Primary Standard Reasoning (healthy quota < 80% and Eco Lite off)
    const val PRIMARY_REASONING_MODEL = "gemini-2.5-flash"

    // Tier 2 — High-Efficiency Economy Lite (auto-activated at >=80% quota, on 429, or Eco Lite mode)
    const val ECONOMY_LITE_MODEL = "gemini-2.5-flash-lite"

    // Tier 3 — Zero-Cost On-Device / Local Deterministic Engine (ML Kit + Local Rules)
    const val ON_DEVICE_ENGINE_ID = "on-device-mlkit-local"

    const val GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    // Token & Request Hygiene guardrails
    const val FINGERPRINT_CACHE_TTL_MS = 45_000L
    const val MAX_UPLOAD_BITMAP_DIM_PX = 768
    const val DAILY_STANDARD_FREE_LIMIT = 10
    const val SOFT_NUDGE_THRESHOLD_RATIO = 0.80f
    const val GRACE_CREDITS_BONUS = 3
    const val ECO_LITE_QUOTA_MULTIPLIER = 3

    fun endpointForModel(modelId: String, apiKey: String): String {
        return "$GEMINI_API_BASE_URL/$modelId:generateContent?key=$apiKey"
    }
}

enum class AiExecutionTier {
    PRIMARY_CLOUD,
    ECONOMY_LITE,
    ON_DEVICE_LOCAL,
    CACHED
}

enum class AiKeySource {
    NONE,
    USER_BYOK,
    BUILD_CONFIG
}

data class AiQuotaSnapshot(
    val dateIso: String = "",
    val dailyUsed: Int = 0,
    val baseDailyLimit: Int = AiModelConfig.DAILY_STANDARD_FREE_LIMIT,
    val bonusGraceCredits: Int = 0,
    val graceClaimedToday: Boolean = false,
    val ecoLiteMode: Boolean = false,
    val fallbackCascadeCount: Int = 0,
    val hasUserByokKey: Boolean = false,
    val keySource: AiKeySource = AiKeySource.NONE,
    val activeTier: AiExecutionTier = AiExecutionTier.ON_DEVICE_LOCAL,
    val isRateLimitedOrExhausted: Boolean = false,
    val resetHours: Int = 0,
    val resetMinutes: Int = 0,
    // Daily AI Value Receipt metrics
    val facesScoredCount: Int = 0,
    val screenshotsClassifiedCount: Int = 0,
    val routinesGeneratedCount: Int = 0,
    val cacheHitsSavedCount: Int = 0
) {
    val effectiveLimit: Int
        get() = baseDailyLimit + bonusGraceCredits

    val remainingCredits: Int
        get() = (effectiveLimit - dailyUsed).coerceAtLeast(0)

    val usageRatio: Float
        get() = if (effectiveLimit > 0) (dailyUsed.toFloat() / effectiveLimit.toFloat()).coerceIn(0f, 1f) else 0f

    val usagePercent: Int
        get() = (usageRatio * 100f).toInt()

    val isCloudKeyConfigured: Boolean
        get() = keySource != AiKeySource.NONE

    val shouldShowSoftNudge: Boolean
        get() = isCloudKeyConfigured &&
            !hasUserByokKey &&
            !ecoLiteMode &&
            usageRatio >= AiModelConfig.SOFT_NUDGE_THRESHOLD_RATIO &&
            remainingCredits > 0 &&
            !isRateLimitedOrExhausted

    val shouldShowQuotaRecovery: Boolean
        get() = isRateLimitedOrExhausted || (isCloudKeyConfigured && !hasUserByokKey && remainingCredits <= 0)
}

data class AiGenerationResult(
    val routines: List<AiInsightRoutine>,
    val executionTier: AiExecutionTier,
    val fromCache: Boolean = false,
    val cascadedToLite: Boolean = false,
    val hitRateLimit: Boolean = false
)

enum class ApiKeyValidationStatus {
    IDLE,
    TESTING,
    VALID,
    INVALID
}
