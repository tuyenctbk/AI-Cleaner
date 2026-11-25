package com.example

import com.example.data.model.AppCacheItem
import com.example.data.model.JunkCategory
import com.example.data.model.JunkType
import com.example.data.model.MediaItem
import com.example.data.scanner.QuickCleanupFileFilter
import com.example.data.scanner.QuickCleanupPlanner
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickCleanupPlannerTest {

    private val smallPhoto = MediaItem(
        id = 1L,
        title = "small.jpg",
        path = "/DCIM/small.jpg",
        uriString = "content://media/1",
        sizeBytes = 2L * 1024L * 1024L,
        dateModified = 100L,
        mimeType = "image/jpeg",
        isLarge = false
    )

    private val largeVideo = MediaItem(
        id = 2L,
        title = "vacation_4k.mp4",
        path = "/Movies/vacation_4k.mp4",
        uriString = "content://media/2",
        sizeBytes = 420L * 1024L * 1024L,
        dateModified = 200L,
        mimeType = "video/mp4",
        isLarge = true
    )

    private val largeRawPhoto = MediaItem(
        id = 3L,
        title = "raw_shot.dng",
        path = "/DCIM/raw_shot.dng",
        uriString = "content://media/3",
        sizeBytes = 25L * 1024L * 1024L,
        dateModified = 300L,
        mimeType = "image/x-adobe-dng",
        isLarge = false
    )

    private val largeArchive = MediaItem(
        id = 4L,
        title = "backup.zip",
        path = "/Download/backup.zip",
        uriString = "content://media/4",
        sizeBytes = 55L * 1024L * 1024L,
        dateModified = 400L,
        mimeType = "application/zip",
        isLarge = true
    )

    @Test
    fun `buildSummary deduplicates overlapping cache and temp junk categories when cache buckets exist`() {
        val cacheBuckets = listOf(
            AppCacheItem(
                id = "cache_internal",
                appName = "Internal Cache",
                packageName = "com.example",
                appSizeBytes = 0L,
                cacheSizeBytes = 12_000L,
                category = "Cache",
                isCleared = false
            ),
            AppCacheItem(
                id = "cache_cleared",
                appName = "Already Cleared",
                packageName = "com.example",
                appSizeBytes = 0L,
                cacheSizeBytes = 99_000L,
                category = "Cache",
                isCleared = true
            )
        )
        val junk = listOf(
            JunkCategory(type = JunkType.APP_CACHE, title = "App Cache", description = "", sizeBytes = 12_000L, itemCount = 3, items = emptyList()),
            JunkCategory(type = JunkType.TEMP_FILES, title = "Temp Files", description = "", sizeBytes = 4_000L, itemCount = 2, items = emptyList()),
            JunkCategory(type = JunkType.EMPTY_FOLDERS, title = "Empty Folders", description = "", sizeBytes = 2_500L, itemCount = 5, items = emptyList()),
            JunkCategory(type = JunkType.APK_PACKAGES, title = "Old APKs", description = "", sizeBytes = 8_000L, itemCount = 1, items = emptyList())
        )

        val summary = QuickCleanupPlanner.buildSummary(
            allMedia = listOf(smallPhoto, largeVideo, largeRawPhoto, largeArchive),
            appCacheItems = cacheBuckets,
            junkCategories = junk,
            fallbackJunkBytes = 50_000L
        )

        // Cached bytes = 12,000 (uncleared bucket) + 2,500 (EMPTY_FOLDERS) + 8,000 (APK_PACKAGES) = 22,500
        assertEquals(22_500L, summary.cachedBytes)
        assertEquals(1, summary.cachedBuckets.size)
        assertEquals(2, summary.extraJunkCategories.size)
        // 1 bucket + 5 empty folders + 1 apk = 7 cached items
        assertEquals(7, summary.cachedItemCount)

        // Large files sorted descending by size: largeVideo (420MB), largeArchive (55MB), largeRawPhoto (25MB)
        assertEquals(listOf(2L, 4L, 3L), summary.largeFiles.map { it.id })
        val expectedLargeBytes = largeVideo.sizeBytes + largeArchive.sizeBytes + largeRawPhoto.sizeBytes
        assertEquals(expectedLargeBytes, summary.largeFilesBytes)
        assertEquals(22_500L + expectedLargeBytes, summary.totalReclaimableBytes)
    }

    @Test
    fun `buildSummary uses fallbackJunkBytes only when both buckets and junk categories are empty`() {
        val summaryWithFallback = QuickCleanupPlanner.buildSummary(
            allMedia = emptyList(),
            appCacheItems = emptyList(),
            junkCategories = emptyList(),
            fallbackJunkBytes = 33_000L
        )
        assertEquals(33_000L, summaryWithFallback.cachedBytes)
        assertEquals(33_000L, summaryWithFallback.totalReclaimableBytes)

        val apkOnly = QuickCleanupPlanner.buildSummary(
            allMedia = emptyList(),
            appCacheItems = emptyList(),
            junkCategories = listOf(
                JunkCategory(type = JunkType.APK_PACKAGES, title = "Old APKs", description = "", sizeBytes = 5_000L, itemCount = 1, items = emptyList())
            ),
            fallbackJunkBytes = 99_000L
        )
        assertEquals(5_000L, apkOnly.cachedBytes)
    }

    @Test
    fun `filterLargeFiles partitions videos, photos, and other files accurately`() {
        val largeFiles = listOf(largeVideo, largeArchive, largeRawPhoto)

        assertEquals(listOf(largeVideo, largeArchive, largeRawPhoto), QuickCleanupPlanner.filterLargeFiles(largeFiles, QuickCleanupFileFilter.ALL))
        assertEquals(listOf(largeVideo), QuickCleanupPlanner.filterLargeFiles(largeFiles, QuickCleanupFileFilter.VIDEOS))
        assertEquals(listOf(largeRawPhoto), QuickCleanupPlanner.filterLargeFiles(largeFiles, QuickCleanupFileFilter.PHOTOS))
        assertEquals(listOf(largeArchive), QuickCleanupPlanner.filterLargeFiles(largeFiles, QuickCleanupFileFilter.OTHER))
    }

    @Test
    fun `computeSelectedReclaimableBytes reflects toggled cache and selected large file subset`() {
        val summary = QuickCleanupPlanner.buildSummary(
            allMedia = listOf(largeVideo, largeArchive, largeRawPhoto),
            appCacheItems = listOf(
                AppCacheItem(
                    id = "b1",
                    appName = "Cache",
                    packageName = "com.example",
                    appSizeBytes = 0L,
                    cacheSizeBytes = 10_000L,
                    category = "Cache",
                    isCleared = false
                )
            ),
            junkCategories = emptyList()
        )

        assertEquals(
            10_000L + largeVideo.sizeBytes + largeRawPhoto.sizeBytes,
            QuickCleanupPlanner.computeSelectedReclaimableBytes(summary, includeCachedData = true, selectedLargeFileIds = setOf(2L, 3L))
        )
        assertEquals(
            largeArchive.sizeBytes,
            QuickCleanupPlanner.computeSelectedReclaimableBytes(summary, includeCachedData = false, selectedLargeFileIds = setOf(4L))
        )
        assertEquals(
            10_000L,
            QuickCleanupPlanner.computeSelectedReclaimableBytes(summary, includeCachedData = true, selectedLargeFileIds = emptySet())
        )
        assertEquals(
            0L,
            QuickCleanupPlanner.computeSelectedReclaimableBytes(summary, includeCachedData = false, selectedLargeFileIds = emptySet())
        )
    }
}
