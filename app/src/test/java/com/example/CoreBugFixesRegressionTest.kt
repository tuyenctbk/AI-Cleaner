package com.example

import com.example.data.ai.AiExecutionTier
import com.example.data.ai.MediaLibraryRecommendationAnalyzer
import com.example.data.ai.RecommendationCategory
import com.example.data.model.CleanRecord
import com.example.data.model.CloudPhotoItem
import com.example.data.model.DuplicateGroup
import com.example.data.model.MediaItem
import com.example.data.model.ScreenshotCategory
import com.example.data.model.ScreenshotClassificationSummary
import com.example.data.model.StorageStats
import com.example.ui.viewmodel.CleanerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBugFixesRegressionTest {

    @Test
    fun `CleanRecord assigns strictly unique IDs even when constructed within the same millisecond`() {
        val records = List(50) {
            CleanRecord(
                freedBytes = 1024L * (it + 1),
                itemsCount = 1,
                description = "Batch $it"
            )
        }
        assertEquals(50, records.map { it.id }.toSet().size)
    }

    @Test
    fun `CloudPhotoItem defaults to unverified and unselected for user safety`() {
        val item = CloudPhotoItem(
            id = 42L,
            title = "IMG_old.jpg",
            path = "/DCIM/Camera/IMG_old.jpg",
            sizeBytes = 2_000_000L,
            dateModified = 1_700_000_000_000L,
            cloudProvider = "Local Storage",
            cloudBackupDate = "Verify Backup"
        )
        assertFalse(item.isVerifiedCloudBackup)
        assertFalse(item.isSelected)
    }

    @Test
    fun `oldDownloads handles real MediaStore millisecond timestamps accurately`() {
        val nowSeconds = 1_780_000_000L
        val oldDownloadMs = MediaItem(
            id = 101L,
            title = "archive_flyer.jpg",
            path = "/storage/emulated/0/Download/archive_flyer.jpg",
            uriString = "content://media/101",
            sizeBytes = 1_500_000L,
            dateModified = (nowSeconds - 90L * 86_400L) * 1000L, // milliseconds like real MediaStore
            mimeType = "image/jpeg",
            width = 1200,
            height = 1600
        )
        val recentDownloadMs = MediaItem(
            id = 102L,
            title = "fresh_doc.jpg",
            path = "/storage/emulated/0/Download/fresh_doc.jpg",
            uriString = "content://media/102",
            sizeBytes = 1_200_000L,
            dateModified = (nowSeconds - 5L * 86_400L) * 1000L, // milliseconds, 5 days ago
            mimeType = "image/jpeg",
            width = 1200,
            height = 1600
        )

        val digest = MediaLibraryRecommendationAnalyzer.buildLibraryDigest(
            allMedia = listOf(oldDownloadMs, recentDownloadMs),
            duplicateGroups = emptyList(),
            smartExpiryDays = 60,
            nowEpochSeconds = nowSeconds
        )

        assertEquals(listOf(101L), digest.oldDownloads.map { it.id })
    }

    @Test
    fun `smartExpiryDays zero disables old downloads flagging`() {
        val nowSeconds = 1_780_000_000L
        val ancientDownload = MediaItem(
            id = 103L,
            title = "ancient.jpg",
            path = "/Download/ancient.jpg",
            uriString = "content://media/103",
            sizeBytes = 2_000_000L,
            dateModified = (nowSeconds - 365L * 86_400L) * 1000L,
            mimeType = "image/jpeg",
            width = 1200,
            height = 1600
        )

        val digest = MediaLibraryRecommendationAnalyzer.buildLibraryDigest(
            allMedia = listOf(ancientDownload),
            duplicateGroups = emptyList(),
            smartExpiryDays = 0,
            nowEpochSeconds = nowSeconds
        )

        assertTrue(digest.oldDownloads.isEmpty())
    }

    @Test
    fun `parseGeminiRecommendationsJson decodes unicode escape sequences cleanly`() {
        val blurry = MediaItem(
            id = 201L,
            title = "blur.jpg",
            path = "/DCIM/blur.jpg",
            uriString = "content://media/201",
            sizeBytes = 3_000_000L,
            dateModified = 1_780_000_000_000L,
            mimeType = "image/jpeg",
            width = 4000,
            height = 3000,
            isBlurry = true,
            sharpnessScore = 14f
        )
        val digest = MediaLibraryRecommendationAnalyzer.buildLibraryDigest(
            allMedia = listOf(blurry),
            duplicateGroups = emptyList(),
            smartExpiryDays = 60,
            nowEpochSeconds = 1_780_000_100L
        )
        val raw = """
            {"candidates":[{"content":{"parts":[{"text":"[{\"category\":\"BLURRY_PHOTOS\",\"priority\":\"HIGH\",\"actionMode\":\"REVIEW_AND_DELETE\",\"title\":\"Blurry 4000\\u00d73000 \\u2022 Clean\",\"reason\":\"Low focus shot\",\"suggestedActionLabel\":\"Clean\",\"confidenceScore\":94}]"}]}}]}
        """.trimIndent()

        val parsed = MediaLibraryRecommendationAnalyzer.parseGeminiRecommendationsJson(
            rawText = raw,
            digest = digest,
            executionTier = AiExecutionTier.PRIMARY_CLOUD
        )

        assertEquals(1, parsed.size)
        assertEquals(RecommendationCategory.BLURRY_PHOTOS, parsed[0].category)
        assertEquals("Blurry 4000×3000 • Clean", parsed[0].title)
    }

    @Test
    fun `CleanerUiState totalReclaimableBytes deduplicates overlapping duplicates and safe screenshots`() {
        val bestShot = MediaItem(
            id = 1L, title = "s1.png", path = "/s1.png", uriString = "content://media/1",
            sizeBytes = 500_000L, dateModified = 0L, mimeType = "image/png",
            isScreenshot = true, isBestShot = true
        )
        val dupScreenshot = MediaItem(
            id = 2L, title = "s2.png", path = "/s2.png", uriString = "content://media/2",
            sizeBytes = 500_000L, dateModified = 0L, mimeType = "image/png",
            isScreenshot = true, isBestShot = false, screenshotCategory = ScreenshotCategory.OTP_OR_CODE
        )
        val uniqueSafeShot = MediaItem(
            id = 3L, title = "s3.png", path = "/s3.png", uriString = "content://media/3",
            sizeBytes = 300_000L, dateModified = 0L, mimeType = "image/png",
            isScreenshot = true, screenshotCategory = ScreenshotCategory.OTP_OR_CODE
        )

        val state = CleanerUiState(
            storageStats = StorageStats(junkBytes = 200_000L),
            duplicateGroups = listOf(
                DuplicateGroup(id = "g1", label = "Dups", bestItemId = 1L, items = listOf(bestShot, dupScreenshot))
            ),
            screenshotSummary = ScreenshotClassificationSummary(
                otpItems = listOf(dupScreenshot, uniqueSafeShot)
            )
        )

        // 200KB junk + 500KB dup (id=2) + 300KB unique safe shot (id=3) = 1,000,000 bytes (id=2 NOT double counted)
        assertEquals(1_000_000L, state.totalReclaimableBytes)
    }
}
