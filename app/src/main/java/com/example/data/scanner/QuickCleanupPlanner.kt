package com.example.data.scanner

import com.example.data.model.AppCacheItem
import com.example.data.model.JunkCategory
import com.example.data.model.JunkType
import com.example.data.model.MediaItem

enum class QuickCleanupFileFilter {
    ALL,
    VIDEOS,
    PHOTOS,
    OTHER
}

data class QuickCleanupSummary(
    val cachedBuckets: List<AppCacheItem>,
    val extraJunkCategories: List<JunkCategory>,
    val cachedBytes: Long,
    val cachedItemCount: Int,
    val largeFiles: List<MediaItem>,
    val largeFilesBytes: Long,
    val totalReclaimableBytes: Long
)

object QuickCleanupPlanner {
    const val LARGE_FILE_THRESHOLD_BYTES: Long = 10L * 1024L * 1024L // 10 MB

    fun buildSummary(
        allMedia: List<MediaItem>,
        appCacheItems: List<AppCacheItem>,
        junkCategories: List<JunkCategory>,
        fallbackJunkBytes: Long = 0L,
        largeThresholdBytes: Long = LARGE_FILE_THRESHOLD_BYTES
    ): QuickCleanupSummary {
        val unclearedBuckets = appCacheItems
            .filter { !it.isCleared && it.cacheSizeBytes > 0L }
            .sortedByDescending { it.cacheSizeBytes }

        // Avoid double-counting cache/temp folders already represented in appCacheItems
        val nonOverlappingJunk = if (unclearedBuckets.isNotEmpty()) {
            junkCategories.filter {
                it.sizeBytes > 0L &&
                    it.type != JunkType.APP_CACHE &&
                    it.type != JunkType.TEMP_FILES
            }
        } else {
            junkCategories.filter { it.sizeBytes > 0L }
        }

        val bucketBytes = unclearedBuckets.sumOf { it.cacheSizeBytes }
        val extraJunkBytes = nonOverlappingJunk.sumOf { it.sizeBytes }
        val rawCachedBytes = bucketBytes + extraJunkBytes
        val cachedBytes = if (rawCachedBytes > 0L) {
            rawCachedBytes
        } else if (unclearedBuckets.isEmpty() && nonOverlappingJunk.isEmpty()) {
            fallbackJunkBytes.coerceAtLeast(0L)
        } else {
            0L
        }

        val cachedItemCount = unclearedBuckets.size + nonOverlappingJunk.sumOf { it.itemCount.coerceAtLeast(1) }

        val largeFiles = allMedia
            .filter { it.isLarge || it.sizeBytes >= largeThresholdBytes }
            .sortedByDescending { it.sizeBytes }

        val largeFilesBytes = largeFiles.sumOf { it.sizeBytes }

        return QuickCleanupSummary(
            cachedBuckets = unclearedBuckets,
            extraJunkCategories = nonOverlappingJunk,
            cachedBytes = cachedBytes,
            cachedItemCount = cachedItemCount,
            largeFiles = largeFiles,
            largeFilesBytes = largeFilesBytes,
            totalReclaimableBytes = cachedBytes + largeFilesBytes
        )
    }

    fun filterLargeFiles(
        largeFiles: List<MediaItem>,
        filter: QuickCleanupFileFilter
    ): List<MediaItem> {
        return when (filter) {
            QuickCleanupFileFilter.ALL -> largeFiles
            QuickCleanupFileFilter.VIDEOS -> largeFiles.filter { it.mimeType.startsWith("video/", ignoreCase = true) }
            QuickCleanupFileFilter.PHOTOS -> largeFiles.filter { it.mimeType.startsWith("image/", ignoreCase = true) }
            QuickCleanupFileFilter.OTHER -> largeFiles.filter {
                !it.mimeType.startsWith("video/", ignoreCase = true) &&
                    !it.mimeType.startsWith("image/", ignoreCase = true)
            }
        }
    }

    fun computeSelectedReclaimableBytes(
        summary: QuickCleanupSummary,
        includeCachedData: Boolean,
        selectedLargeFileIds: Set<Long>
    ): Long {
        val cacheContribution = if (includeCachedData) summary.cachedBytes else 0L
        val largeContribution = summary.largeFiles
            .filter { it.id in selectedLargeFileIds }
            .sumOf { it.sizeBytes }
        return cacheContribution + largeContribution
    }
}
