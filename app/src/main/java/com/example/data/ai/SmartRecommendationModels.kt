package com.example.data.ai

import com.example.data.model.MediaItem
import com.example.data.model.formatFileSize

enum class RecommendationCategory {
    BLURRY_PHOTOS,
    LOW_QUALITY_SCREENSHOTS,
    LARGE_VIDEOS,
    OLD_DOWNLOADS,
    LOW_RES_PHOTOS
}

enum class RecommendationPriority {
    HIGH,
    MEDIUM,
    LOW
}

enum class RecommendationActionMode {
    REVIEW_AND_DELETE,
    OPEN_SWIPE_BLUR,
    COMPRESS_VIDEOS
}

data class SmartRecommendation(
    val id: String,
    val category: RecommendationCategory,
    val priority: RecommendationPriority,
    val actionMode: RecommendationActionMode,
    val title: String,
    val reason: String,
    val suggestedActionLabel: String,
    val confidenceScore: Int,
    val targetMediaIds: List<Long>,
    val itemCount: Int,
    val reclaimableBytes: Long,
    val executionTier: AiExecutionTier,
    val isApplied: Boolean = false
) {
    val formattedSavings: String
        get() = formatFileSize(reclaimableBytes)
}

data class MediaLibraryDigest(
    val blurryPhotos: List<MediaItem> = emptyList(),
    val lowQualityScreenshots: List<MediaItem> = emptyList(),
    val largeVideos: List<MediaItem> = emptyList(),
    val oldDownloads: List<MediaItem> = emptyList(),
    val lowResPhotos: List<MediaItem> = emptyList(),
    val averageBlurrySharpness: Float = 0f,
    val excludedDuplicateCount: Int = 0
) {
    val blurryBytes: Long
        get() = blurryPhotos.sumOf { it.sizeBytes }

    val lowQualityScreenshotBytes: Long
        get() = lowQualityScreenshots.sumOf { it.sizeBytes }

    val largeVideosBytes: Long
        get() = largeVideos.sumOf { it.sizeBytes }

    val oldDownloadsBytes: Long
        get() = oldDownloads.sumOf { it.sizeBytes }

    val lowResPhotosBytes: Long
        get() = lowResPhotos.sumOf { it.sizeBytes }

    val allActionableItemsCount: Int
        get() = blurryPhotos.size +
            lowQualityScreenshots.size +
            largeVideos.size +
            oldDownloads.size +
            lowResPhotos.size

    val totalReclaimableBytes: Long
        get() = blurryBytes +
            lowQualityScreenshotBytes +
            largeVideosBytes +
            oldDownloadsBytes +
            lowResPhotosBytes

    fun hasCategoryItems(category: RecommendationCategory): Boolean {
        return when (category) {
            RecommendationCategory.BLURRY_PHOTOS -> blurryPhotos.isNotEmpty()
            RecommendationCategory.LOW_QUALITY_SCREENSHOTS -> lowQualityScreenshots.isNotEmpty()
            RecommendationCategory.LARGE_VIDEOS -> largeVideos.isNotEmpty()
            RecommendationCategory.OLD_DOWNLOADS -> oldDownloads.isNotEmpty()
            RecommendationCategory.LOW_RES_PHOTOS -> lowResPhotos.isNotEmpty()
        }
    }
}

data class SmartRecommendationAnalysisResult(
    val recommendations: List<SmartRecommendation>,
    val digest: MediaLibraryDigest,
    val executionTier: AiExecutionTier,
    val fromCache: Boolean = false,
    val cascadedToLite: Boolean = false,
    val hitRateLimit: Boolean = false,
    val errorMessage: String? = null
)
