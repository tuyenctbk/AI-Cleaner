package com.example.data.model

import androidx.annotation.StringRes
import com.example.R
import com.example.ui.viewmodel.MainScreenTab

enum class ScreenshotCategory {
    NONE,
    OTP_OR_CODE,
    RECEIPT_OR_DOC,
    CHAT_OR_MEME,
    GENERAL
}

data class MediaItem(
    val id: Long,
    val title: String,
    val path: String,
    val uriString: String,
    val sizeBytes: Long,
    val dateModified: Long,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val isDuplicate: Boolean = false,
    val isBlurry: Boolean = false,
    val isScreenshot: Boolean = false,
    val isLarge: Boolean = false,
    val isBestShot: Boolean = false,
    val sharpnessScore: Float = 0f,
    val dHash: Long? = null,
    val faceCount: Int = 0,
    val faceQualityScore: Float = 0f,
    val hasBlinkingFace: Boolean = false,
    val screenshotCategory: ScreenshotCategory = ScreenshotCategory.NONE,
    val isSelected: Boolean = false
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)
}

data class ScreenshotClassificationSummary(
    val otpItems: List<MediaItem> = emptyList(),
    val chatMemeItems: List<MediaItem> = emptyList(),
    val receiptDocItems: List<MediaItem> = emptyList(),
    val generalItems: List<MediaItem> = emptyList(),
    val isAnalyzing: Boolean = false,
    val hasRun: Boolean = false
) {
    val safeCleanableItems: List<MediaItem>
        get() = otpItems + chatMemeItems

    val safeCleanableBytes: Long
        get() = safeCleanableItems.sumOf { it.sizeBytes }

    val receiptDocBytes: Long
        get() = receiptDocItems.sumOf { it.sizeBytes }

    val totalClassified: Int
        get() = otpItems.size + chatMemeItems.size + receiptDocItems.size + generalItems.size
}

data class FolderStorageItem(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val category: String, // e.g. "Camera", "4K Videos", "App Caches", "Downloads", "Messaging"
    val isSystemFolder: Boolean = false,
    val previewFileTypes: String = ""
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)
}

data class DuplicateGroup(
    val id: String,
    val label: String,
    val bestItemId: Long,
    val items: List<MediaItem>,
    val matchReason: String = "",
    val matchConfidence: Int = 95
) {
    val totalSizeBytes: Long
        get() = items.sumOf { it.sizeBytes }

    val selectableSizeBytes: Long
        get() = items.filter { !it.isBestShot }.sumOf { it.sizeBytes }

    val selectedCount: Int
        get() = items.count { it.isSelected }

    val selectedSizeBytes: Long
        get() = items.filter { it.isSelected }.sumOf { it.sizeBytes }
}

enum class JunkType {
    APP_CACHE,
    TEMP_FILES,
    EMPTY_FOLDERS,
    THUMBNAIL_CACHE,
    RESIDUAL_FILES,
    OLD_LOGS,
    APK_PACKAGES
}

data class JunkFileItem(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val type: JunkType
)

data class JunkCategory(
    val type: JunkType,
    val title: String,
    val description: String,
    val sizeBytes: Long,
    val itemCount: Int,
    val items: List<JunkFileItem>,
    val isSelected: Boolean = true
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)
}

data class StorageStats(
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val photoBytes: Long = 0L,
    val videoBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val docBytes: Long = 0L,
    val appBytes: Long = 0L,
    val systemBytes: Long = 0L,
    val junkBytes: Long = 0L,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val duplicateCount: Int = 0,
    val blurryCount: Int = 0,
    val screenshotCount: Int = 0,
    val largeFileCount: Int = 0,
    val healthScore: Int = 100
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) else 0f

    val usedPercent: Int
        get() = (usedPercentage * 100).toInt()

    val formattedUsed: String
        get() = formatFileSize(usedBytes)

    val formattedTotal: String
        get() = formatFileSize(totalBytes)

    val formattedFree: String
        get() = formatFileSize(freeBytes)

    val formattedJunk: String
        get() = formatFileSize(junkBytes)
}

enum class CompressionQuality(
    @param:StringRes val labelRes: Int,
    val factor: Float,
    val savingPercentage: Int
) {
    HIGH(R.string.compressor_quality_high, 0.65f, 35),
    MEDIUM(R.string.compressor_quality_medium, 0.35f, 65),
    MAX_SAVINGS(R.string.compressor_quality_max_savings, 0.15f, 85)
}

data class CompressibleMedia(
    val id: Long,
    val mediaItem: MediaItem,
    val quality: CompressionQuality = CompressionQuality.MEDIUM,
    val isSelected: Boolean = true,
    val isProcessed: Boolean = false,
    val compressedSizeBytes: Long = 0
) {
    val estimatedSavingsBytes: Long
        get() = (mediaItem.sizeBytes * (1f - quality.factor)).toLong()

    val estimatedCompressedBytes: Long
        get() = (mediaItem.sizeBytes * quality.factor).toLong()
}

private val cleanRecordIdCounter = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() * 1000L)

data class CleanRecord(
    val id: Long = cleanRecordIdCounter.incrementAndGet(),
    val timestamp: Long = System.currentTimeMillis(),
    val freedBytes: Long,
    val itemsCount: Int,
    val description: String
)

data class AppCacheItem(
    val id: String,
    val appName: String,
    val packageName: String,
    val appSizeBytes: Long,
    val cacheSizeBytes: Long,
    val category: String,
    val daysUnused: Int = 0,
    val isCleared: Boolean = false
) {
    val formattedAppSize: String
        get() = formatFileSize(appSizeBytes)

    val formattedCacheSize: String
        get() = if (isCleared) "0 B" else formatFileSize(cacheSizeBytes)
}

enum class TipCategory {
    APP_USAGE,
    CLOUD_SYNC,
    HEAVY_MEDIA,
    CACHE_ACCUMULATION,
    SIMILAR_PHOTOS
}

data class CleanupTip(
    val id: String,
    val title: String,
    val description: String,
    val potentialSavingsBytes: Long,
    val category: TipCategory,
    val actionLabel: String,
    val targetTab: MainScreenTab? = null,
    val itemCount: Int = 0
) {
    val formattedSavings: String
        get() = formatFileSize(potentialSavingsBytes)
}

data class SmartScheduleSettings(
    val isEnabled: Boolean = true,
    val dayOfWeek: String = "Sunday",
    val scanTime: String = "02:00 AM",
    val thresholdMb: Int = 500,
    val scanJunk: Boolean = true,
    val scanDuplicates: Boolean = true,
    val scanLargeFiles: Boolean = true,
    val requiresCharging: Boolean = true,
    val lastScanDate: Long = 0L
)

data class MediaDeleteResult(
    val freedBytes: Long,
    val deletedIds: Set<Long>,
    val pendingSystemDeleteUris: List<String> = emptyList()
)

data class CloudPhotoItem(
    val id: Long,
    val title: String,
    val path: String,
    val uriString: String = "",
    val sizeBytes: Long,
    val dateModified: Long,
    val cloudProvider: String = "",
    val cloudBackupDate: String = "",
    val isVerifiedCloudBackup: Boolean = false,
    val isSelected: Boolean = false
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)
}

data class StorageTrendPoint(
    val dayLabel: String,
    val timestamp: Long,
    val usedBytes: Long,
    val deltaBytes: Long,
    val photoBytes: Long,
    val videoBytes: Long,
    val appBytes: Long,
    val isRapidAccumulationBurst: Boolean = false,
    val burstDescription: String? = null
) {
    val formattedUsed: String
        get() = formatFileSize(usedBytes)
    val formattedDelta: String
        get() = if (deltaBytes >= 0) "+${formatFileSize(deltaBytes)}" else "-${formatFileSize(-deltaBytes)}"
}

data class StorageTrendSummary(
    val timeframeDays: Int = 30,
    val totalAccumulatedBytes: Long = 0L,
    val topGrowthCategory: String = "",
    val highestBurstDay: String = "",
    val averageDailyGrowthMb: Int = 0,
    val trendPoints: List<StorageTrendPoint> = emptyList()
) {
    val formattedTotalAccumulated: String
        get() = formatFileSize(totalAccumulatedBytes)
}

enum class AiRoutineAction {
    DELETE_SCREENSHOTS,
    DELETE_SAFE_SCREENSHOTS,
    DELETE_DUPLICATES,
    COMPRESS_4K_VIDEOS,
    CLEAR_WHATSAPP_CACHE,
    DELETE_CLOUD_BACKUPS,
    SAFE_QUICK_CLEAN
}

data class AiInsightRoutine(
    val id: String,
    val title: String,
    val summary: String,
    val potentialSavingsBytes: Long,
    val confidenceScore: Int = 95,
    val categoryTag: String = "",
    val actionType: AiRoutineAction = AiRoutineAction.SAFE_QUICK_CLEAN,
    val explanation: String,
    val itemCount: Int = 1,
    val isExecuted: Boolean = false
) {
    val formattedSavings: String
        get() = formatFileSize(potentialSavingsBytes)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    val tb = gb / 1024.0

    return when {
        tb >= 1.0 -> String.format(java.util.Locale.US, "%.2f TB", tb)
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}
