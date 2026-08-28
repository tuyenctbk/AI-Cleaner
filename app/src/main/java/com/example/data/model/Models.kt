package com.example.data.model

import com.example.ui.viewmodel.MainScreenTab

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
    val sampleImageIndex: Int = 0, // Fallback visual index if local file doesn't have thumbnail
    var isSelected: Boolean = false
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)
}

data class FolderStorageItem(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val category: String, // e.g. "Camera", "4K Videos", "App Caches", "Downloads", "Messaging"
    val isSystemFolder: Boolean = false,
    val sampleFileTypes: String = "MP4, JPG"
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)
}

data class DuplicateGroup(
    val id: String,
    val label: String,
    val bestItemId: Long,
    val items: List<MediaItem>
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
    var isSelected: Boolean = true
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)
}

data class StorageStats(
    val totalBytes: Long = 512L * 1024 * 1024 * 1024,
    val usedBytes: Long = 104L * 1024 * 1024 * 1024,
    val freeBytes: Long = 408L * 1024 * 1024 * 1024,
    val photoBytes: Long = 42L * 1024 * 1024 * 1024,
    val videoBytes: Long = 38L * 1024 * 1024 * 1024,
    val audioBytes: Long = 6L * 1024 * 1024 * 1024,
    val docBytes: Long = 4L * 1024 * 1024 * 1024,
    val appBytes: Long = 10L * 1024 * 1024 * 1024,
    val systemBytes: Long = 4L * 1024 * 1024 * 1024,
    val junkBytes: Long = 3200L * 1024 * 1024,
    val photoCount: Int = 2598,
    val videoCount: Int = 266,
    val duplicateCount: Int = 24,
    val blurryCount: Int = 18,
    val screenshotCount: Int = 42,
    val largeFileCount: Int = 15,
    val healthScore: Int = 88
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) else 0.2f

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

enum class CompressionQuality(val label: String, val factor: Float, val savingPercentage: Int) {
    HIGH("High Quality", 0.65f, 35),
    MEDIUM("Balanced", 0.35f, 65),
    MAX_SAVINGS("Max Space Saver", 0.15f, 85)
}

data class CompressibleMedia(
    val id: Long,
    val mediaItem: MediaItem,
    var quality: CompressionQuality = CompressionQuality.MEDIUM,
    var isSelected: Boolean = true,
    var isProcessed: Boolean = false,
    var compressedSizeBytes: Long = 0
) {
    val estimatedSavingsBytes: Long
        get() = (mediaItem.sizeBytes * (1f - quality.factor)).toLong()

    val estimatedCompressedBytes: Long
        get() = (mediaItem.sizeBytes * quality.factor).toLong()
}

data class CleanRecord(
    val id: Long = System.currentTimeMillis(),
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
    CACHE_ACCUMULATION
}

data class CleanupTip(
    val id: String,
    val title: String,
    val description: String,
    val potentialSavingsBytes: Long,
    val category: TipCategory,
    val actionLabel: String,
    val targetTab: MainScreenTab? = null
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
    val lastScanDate: Long = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
)

data class CloudPhotoItem(
    val id: Long,
    val title: String,
    val path: String,
    val sizeBytes: Long,
    val dateModified: Long,
    val cloudProvider: String = "Google Photos",
    val cloudBackupDate: String = "Backed Up • Safe to Delete",
    val sampleImageIndex: Int = 0,
    var isSelected: Boolean = true
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
    val totalAccumulatedBytes: Long = 8L * 1024 * 1024 * 1024,
    val topGrowthCategory: String = "4K Video Recordings (+4.8 GB)",
    val highestBurstDay: String = "Aug 18 (+3.2 GB)",
    val averageDailyGrowthMb: Int = 280,
    val trendPoints: List<StorageTrendPoint> = emptyList()
) {
    val formattedTotalAccumulated: String
        get() = formatFileSize(totalAccumulatedBytes)
}

enum class AiRoutineAction {
    DELETE_SCREENSHOTS,
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
    val categoryTag: String = "AI Recommendation",
    val actionType: AiRoutineAction = AiRoutineAction.SAFE_QUICK_CLEAN,
    val explanation: String,
    val itemCount: Int = 1,
    var isExecuted: Boolean = false
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
        tb >= 1.0 -> String.format("%.2f TB", tb)
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
