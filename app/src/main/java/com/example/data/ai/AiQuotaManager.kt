package com.example.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local Free-Tier Quota Ledger, Value Receipt tracker, and Android Keystore AES-GCM
 * encrypted BYOK (Bring Your Own Key) manager.
 *
 * Implements:
 * - Automatic midnight rollover reset (resets dailyUsed/graceClaimed while preserving ecoLiteMode & BYOK key)
 * - 80% Soft Nudge threshold detection
 * - One-time daily +3 Free Grace Credits claim
 * - Atomic deduct-with-refund so network/API failures never burn user credits
 * - Visible reset countdown until local midnight
 */
class AiQuotaManager(
    context: Context,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ai_cleaner_quota_ledger"
        private const val KEYSTORE_ALIAS = "ai_cleaner_byok_aes_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

        private const val KEY_DATE_ISO = "date_iso"
        private const val KEY_DAILY_USED = "daily_used"
        private const val KEY_BONUS_GRACE_CREDITS = "bonus_grace_credits"
        private const val KEY_GRACE_CLAIMED = "grace_claimed"
        private const val KEY_ECO_LITE_MODE = "eco_lite_mode"
        private const val KEY_FALLBACK_CASCADE_COUNT = "fallback_cascade_count"
        private const val KEY_FACES_SCORED = "value_faces_scored"
        private const val KEY_SCREENSHOTS_CLASSIFIED = "value_screenshots_classified"
        private const val KEY_ROUTINES_GENERATED = "value_routines_generated"
        private const val KEY_CACHE_HITS_SAVED = "value_cache_hits_saved"
        private const val KEY_BYOK_CIPHERTEXT = "byok_gemini_key_enc"
        private const val KEY_BYOK_IV = "byok_gemini_key_iv"
        private const val KEY_BYOK_FALLBACK_B64 = "byok_gemini_key_b64"

        fun formatIsoDate(nowMillis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date(nowMillis))
        }

        fun computeTimeUntilMidnight(nowMillis: Long): Pair<Int, Int> {
            val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val midnight = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMinutes = ((midnight.timeInMillis - now.timeInMillis) / 60_000L).coerceAtLeast(1L)
            val hours = (diffMinutes / 60L).toInt()
            val minutes = (diffMinutes % 60L).toInt()
            return Pair(hours, minutes)
        }
    }

    @Synchronized
    fun ensureCurrentPeriod() {
        val today = formatIsoDate(clockMillis())
        val storedDate = prefs.getString(KEY_DATE_ISO, null)
        if (storedDate != today) {
            // Automatic rollover reset: clear daily counters while preserving ecoLiteMode & BYOK key
            prefs.edit()
                .putString(KEY_DATE_ISO, today)
                .putInt(KEY_DAILY_USED, 0)
                .putInt(KEY_BONUS_GRACE_CREDITS, 0)
                .putBoolean(KEY_GRACE_CLAIMED, false)
                .putInt(KEY_FALLBACK_CASCADE_COUNT, 0)
                .putInt(KEY_FACES_SCORED, 0)
                .putInt(KEY_SCREENSHOTS_CLASSIFIED, 0)
                .putInt(KEY_ROUTINES_GENERATED, 0)
                .putInt(KEY_CACHE_HITS_SAVED, 0)
                .apply()
        }
    }

    @Synchronized
    fun getSnapshot(
        activeTier: AiExecutionTier? = null,
        isRateLimited: Boolean = false
    ): AiQuotaSnapshot {
        ensureCurrentPeriod()
        val now = clockMillis()
        val (hours, minutes) = computeTimeUntilMidnight(now)
        val hasByok = hasUserApiKey()
        val keySource = resolveKeySource()
        val ecoLite = prefs.getBoolean(KEY_ECO_LITE_MODE, false)
        val computedTier = activeTier ?: when {
            keySource == AiKeySource.NONE -> AiExecutionTier.ON_DEVICE_LOCAL
            ecoLite -> AiExecutionTier.ECONOMY_LITE
            else -> AiExecutionTier.PRIMARY_CLOUD
        }
        return AiQuotaSnapshot(
            dateIso = prefs.getString(KEY_DATE_ISO, formatIsoDate(now)) ?: formatIsoDate(now),
            dailyUsed = prefs.getInt(KEY_DAILY_USED, 0),
            baseDailyLimit = AiModelConfig.DAILY_STANDARD_FREE_LIMIT,
            bonusGraceCredits = prefs.getInt(KEY_BONUS_GRACE_CREDITS, 0),
            graceClaimedToday = prefs.getBoolean(KEY_GRACE_CLAIMED, false),
            ecoLiteMode = ecoLite,
            fallbackCascadeCount = prefs.getInt(KEY_FALLBACK_CASCADE_COUNT, 0),
            hasUserByokKey = hasByok,
            keySource = keySource,
            activeTier = computedTier,
            isRateLimitedOrExhausted = isRateLimited,
            resetHours = hours,
            resetMinutes = minutes,
            facesScoredCount = prefs.getInt(KEY_FACES_SCORED, 0),
            screenshotsClassifiedCount = prefs.getInt(KEY_SCREENSHOTS_CLASSIFIED, 0),
            routinesGeneratedCount = prefs.getInt(KEY_ROUTINES_GENERATED, 0),
            cacheHitsSavedCount = prefs.getInt(KEY_CACHE_HITS_SAVED, 0)
        )
    }

    /**
     * Atomically reserves 1 cloud credit if available (BYOK users bypass free-tier caps).
     * Always pair with [refundCloudCredit] if the upstream request fails or times out.
     */
    @Synchronized
    fun tryConsumeCloudCredit(): Boolean {
        ensureCurrentPeriod()
        if (hasUserApiKey()) {
            // BYOK direct pass-through: track count for stats without blocking on free limit
            val used = prefs.getInt(KEY_DAILY_USED, 0)
            prefs.edit().putInt(KEY_DAILY_USED, used + 1).apply()
            return true
        }
        val used = prefs.getInt(KEY_DAILY_USED, 0)
        val effectiveLimit = AiModelConfig.DAILY_STANDARD_FREE_LIMIT + prefs.getInt(KEY_BONUS_GRACE_CREDITS, 0)
        if (used >= effectiveLimit) return false
        prefs.edit().putInt(KEY_DAILY_USED, used + 1).apply()
        return true
    }

    @Synchronized
    fun refundCloudCredit() {
        ensureCurrentPeriod()
        val used = prefs.getInt(KEY_DAILY_USED, 0)
        if (used > 0) {
            prefs.edit().putInt(KEY_DAILY_USED, used - 1).apply()
        }
    }

    /**
     * Stage 2 Recovery Suggestion 1: Claim one-time daily +3 Free Grace Credits.
     */
    @Synchronized
    fun claimGraceCredits(): Boolean {
        ensureCurrentPeriod()
        if (prefs.getBoolean(KEY_GRACE_CLAIMED, false)) return false
        val currentBonus = prefs.getInt(KEY_BONUS_GRACE_CREDITS, 0)
        prefs.edit()
            .putBoolean(KEY_GRACE_CLAIMED, true)
            .putInt(KEY_BONUS_GRACE_CREDITS, currentBonus + AiModelConfig.GRACE_CREDITS_BONUS)
            .apply()
        return true
    }

    @Synchronized
    fun setEcoLiteMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ECO_LITE_MODE, enabled).apply()
    }

    @Synchronized
    fun isEcoLiteMode(): Boolean = prefs.getBoolean(KEY_ECO_LITE_MODE, false)

    @Synchronized
    fun recordFallbackCascade() {
        ensureCurrentPeriod()
        val count = prefs.getInt(KEY_FALLBACK_CASCADE_COUNT, 0)
        prefs.edit().putInt(KEY_FALLBACK_CASCADE_COUNT, count + 1).apply()
    }

    @Synchronized
    fun recordValueFacesScored(count: Int) {
        if (count <= 0) return
        ensureCurrentPeriod()
        val cur = prefs.getInt(KEY_FACES_SCORED, 0)
        prefs.edit().putInt(KEY_FACES_SCORED, cur + count).apply()
    }

    @Synchronized
    fun recordValueScreenshotsClassified(count: Int) {
        if (count <= 0) return
        ensureCurrentPeriod()
        val cur = prefs.getInt(KEY_SCREENSHOTS_CLASSIFIED, 0)
        prefs.edit().putInt(KEY_SCREENSHOTS_CLASSIFIED, cur + count).apply()
    }

    @Synchronized
    fun recordValueRoutineGenerated(fromCache: Boolean) {
        ensureCurrentPeriod()
        val routines = prefs.getInt(KEY_ROUTINES_GENERATED, 0)
        val cacheHits = prefs.getInt(KEY_CACHE_HITS_SAVED, 0)
        prefs.edit()
            .putInt(KEY_ROUTINES_GENERATED, routines + 1)
            .putInt(KEY_CACHE_HITS_SAVED, if (fromCache) cacheHits + 1 else cacheHits)
            .apply()
    }

    fun resolveActiveApiKey(): Pair<String, AiKeySource> {
        val byok = getUserApiKey()
        if (byok.isNotBlank()) {
            return Pair(byok, AiKeySource.USER_BYOK)
        }
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return Pair(buildKey, AiKeySource.BUILD_CONFIG)
        }
        return Pair("", AiKeySource.NONE)
    }

    fun resolveKeySource(): AiKeySource = resolveActiveApiKey().second

    fun hasUserApiKey(): Boolean = getUserApiKey().isNotBlank()

    fun saveUserApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            clearUserApiKey()
            return
        }
        try {
            val secretKey = getOrCreateAesKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
            prefs.edit()
                .putString(KEY_BYOK_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_BYOK_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .remove(KEY_BYOK_FALLBACK_B64)
                .apply()
        } catch (_: Exception) {
            // Safe fallback only for environments without AndroidKeyStore (e.g., JVM unit tests)
            val encoded = Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            prefs.edit()
                .putString(KEY_BYOK_FALLBACK_B64, encoded)
                .remove(KEY_BYOK_CIPHERTEXT)
                .remove(KEY_BYOK_IV)
                .apply()
        }
    }

    fun getUserApiKey(): String {
        val cipherTextB64 = prefs.getString(KEY_BYOK_CIPHERTEXT, null)
        val ivB64 = prefs.getString(KEY_BYOK_IV, null)
        if (!cipherTextB64.isNullOrBlank() && !ivB64.isNullOrBlank()) {
            try {
                val secretKey = getOrCreateAesKey()
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                val spec = GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP))
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val decrypted = cipher.doFinal(Base64.decode(cipherTextB64, Base64.NO_WRAP))
                return String(decrypted, Charsets.UTF_8)
            } catch (_: Exception) {
                return ""
            }
        }
        val fallbackB64 = prefs.getString(KEY_BYOK_FALLBACK_B64, null)
        if (!fallbackB64.isNullOrBlank()) {
            return try {
                String(Base64.decode(fallbackB64, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
        }
        return ""
    }

    fun getMaskedUserApiKey(): String {
        val key = getUserApiKey()
        if (key.length <= 8) return if (key.isNotEmpty()) "••••" else ""
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }

    fun clearUserApiKey() {
        prefs.edit()
            .remove(KEY_BYOK_CIPHERTEXT)
            .remove(KEY_BYOK_IV)
            .remove(KEY_BYOK_FALLBACK_B64)
            .apply()
    }

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
