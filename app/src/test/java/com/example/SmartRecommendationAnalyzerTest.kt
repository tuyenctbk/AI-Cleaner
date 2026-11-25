package com.example

import com.example.data.ai.AiExecutionTier
import com.example.data.ai.LocalizedRecommendationStrings
import com.example.data.ai.MediaLibraryRecommendationAnalyzer
import com.example.data.ai.RecommendationActionMode
import com.example.data.ai.RecommendationCategory
import com.example.data.ai.RecommendationPriority
import com.example.data.model.DuplicateGroup
import com.example.data.model.MediaItem
import com.example.data.model.ScreenshotCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRecommendationAnalyzerTest {

    private val nowSeconds = 1_780_000_000L

    private val crispPhoto = MediaItem(
        id = 1L,
        title = "crisp.jpg",
        path = "/DCIM/Camera/crisp.jpg",
        uriString = "content://media/1",
        sizeBytes = 3_500_000L,
        dateModified = nowSeconds - 86_400L,
        mimeType = "image/jpeg",
        width = 4000,
        height = 3000,
        isBlurry = false,
        sharpnessScore = 88f,
        isBestShot = true
    )

    private val duplicateShot = MediaItem(
        id = 2L,
        title = "dup_blur.jpg",
        path = "/DCIM/Camera/dup_blur.jpg",
        uriString = "content://media/2",
        sizeBytes = 3_100_000L,
        dateModified = nowSeconds - 86_400L,
        mimeType = "image/jpeg",
        width = 4000,
        height = 3000,
        isBlurry = true,
        isDuplicate = true,
        isBestShot = false,
        sharpnessScore = 15f
    )

    private val standaloneBlurryPhoto = MediaItem(
        id = 3L,
        title = "shaky_night.jpg",
        path = "/DCIM/Camera/shaky_night.jpg",
        uriString = "content://media/3",
        sizeBytes = 4_200_000L,
        dateModified = nowSeconds - 3_600L,
        mimeType = "image/jpeg",
        width = 4000,
        height = 3000,
        isBlurry = true,
        sharpnessScore = 19f
    )

    private val lowResOtpScreenshot = MediaItem(
        id = 4L,
        title = "Screenshot_otp.png",
        path = "/Pictures/Screenshots/Screenshot_otp.png",
        uriString = "content://media/4",
        sizeBytes = 140_000L,
        dateModified = nowSeconds - 7_200L,
        mimeType = "image/png",
        width = 540,
        height = 960,
        isScreenshot = true,
        screenshotCategory = ScreenshotCategory.OTP_OR_CODE
    )

    private val receiptScreenshot = MediaItem(
        id = 5L,
        title = "Screenshot_receipt.png",
        path = "/Pictures/Screenshots/Screenshot_receipt.png",
        uriString = "content://media/5",
        sizeBytes = 180_000L,
        dateModified = nowSeconds - 10_000L,
        mimeType = "image/png",
        width = 540,
        height = 960,
        isScreenshot = true,
        screenshotCategory = ScreenshotCategory.RECEIPT_OR_DOC
    )

    private val large4kVideo = MediaItem(
        id = 6L,
        title = "trip_4k.mp4",
        path = "/DCIM/Camera/trip_4k.mp4",
        uriString = "content://media/6",
        sizeBytes = 120L * 1024L * 1024L,
        dateModified = nowSeconds - 100_000L,
        mimeType = "video/mp4",
        isLarge = true
    )

    private val agedDownloadImage = MediaItem(
        id = 7L,
        title = "old_flyer.jpg",
        path = "/Download/old_flyer.jpg",
        uriString = "content://media/7",
        sizeBytes = 850_000L,
        dateModified = nowSeconds - (90L * 86_400L),
        mimeType = "image/jpeg",
        width = 1200,
        height = 1600
    )

    private val lowResClutterPhoto = MediaItem(
        id = 8L,
        title = "thumb_saved.jpg",
        path = "/Pictures/Saved/thumb_saved.jpg",
        uriString = "content://media/8",
        sizeBytes = 90_000L,
        dateModified = nowSeconds - 86_400L,
        mimeType = "image/jpeg",
        width = 480,
        height = 480
    )

    private val testStrings = LocalizedRecommendationStrings(
        blurryTitle = { c, s -> "$c Blurry ($s)" },
        blurryReason = { c, avg, s -> "$c blurry at $avg% ($s)" },
        blurryAction = "Clean Blurry",
        lowResShotTitle = { c, s -> "$c Low-Res Shots ($s)" },
        lowResShotReason = { c, s -> "$c shots ($s)" },
        lowResShotAction = "Clean Shots",
        largeVideoTitle = { c, s -> "$c Videos ($s)" },
        largeVideoReason = { c, s -> "$c large videos ($s)" },
        largeVideoAction = "Compress Videos",
        oldDownloadTitle = { c, d, _ -> "$c Old Downloads ($d d)" },
        oldDownloadReason = { c, d, s -> "$c downloads >$d d ($s)" },
        oldDownloadAction = "Clean Downloads",
        lowResPhotoTitle = { c, s -> "$c Low-Res ($s)" },
        lowResPhotoReason = { c, s -> "$c low-res ($s)" },
        lowResPhotoAction = "Clean Low-Res"
    )

    @Test
    fun `buildLibraryDigest excludes duplicate non-best shots and protects receipt screenshots`() {
        val dupGroup = DuplicateGroup(
            id = "g1",
            label = "Exact Match",
            bestItemId = 1L,
            items = listOf(crispPhoto, duplicateShot),
            matchConfidence = 95
        )
        val digest = MediaLibraryRecommendationAnalyzer.buildLibraryDigest(
            allMedia = listOf(
                crispPhoto,
                duplicateShot,
                standaloneBlurryPhoto,
                lowResOtpScreenshot,
                receiptScreenshot,
                large4kVideo,
                agedDownloadImage,
                lowResClutterPhoto
            ),
            duplicateGroups = listOf(dupGroup),
            smartExpiryDays = 60,
            nowEpochSeconds = nowSeconds
        )

        // Duplicate item (id=2) must NOT be duplicated inside blurryPhotos
        assertEquals(1, digest.excludedDuplicateCount)
        assertEquals(listOf(3L), digest.blurryPhotos.map { it.id })

        // Low-quality screenshot (id=4) included, receipt screenshot (id=5) protected
        assertEquals(listOf(4L), digest.lowQualityScreenshots.map { it.id })
        assertFalse(digest.lowQualityScreenshots.any { it.id == 5L })

        assertEquals(listOf(6L), digest.largeVideos.map { it.id })
        assertEquals(listOf(7L), digest.oldDownloads.map { it.id })
        assertEquals(listOf(8L), digest.lowResPhotos.map { it.id })
        assertEquals(19f, digest.averageBlurrySharpness, 0.01f)
    }

    @Test
    fun `parseGeminiRecommendationsJson grounds Gemini output to real digest targets and skips empty categories`() {
        val digest = MediaLibraryRecommendationAnalyzer.buildLibraryDigest(
            allMedia = listOf(standaloneBlurryPhoto, lowResOtpScreenshot),
            duplicateGroups = emptyList(),
            smartExpiryDays = 60,
            nowEpochSeconds = nowSeconds
        )

        val geminiEnvelope = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "[{\"category\":\"BLURRY_PHOTOS\",\"priority\":\"HIGH\",\"actionMode\":\"REVIEW_AND_DELETE\",\"title\":\"Clear out-of-focus shots\",\"reason\":\"1 blurry photo detected with low sharpness.\",\"suggestedActionLabel\":\"Clean Blurry\",\"confidenceScore\":97},{\"category\":\"LARGE_VIDEOS\",\"priority\":\"HIGH\",\"actionMode\":\"COMPRESS_VIDEOS\",\"title\":\"Compress big videos\",\"reason\":\"Should be ignored because count is 0.\",\"suggestedActionLabel\":\"Compress\",\"confidenceScore\":90},{\"category\":\"LOW_QUALITY_SCREENSHOTS\",\"priority\":\"MEDIUM\",\"actionMode\":\"REVIEW_AND_DELETE\",\"title\":\"Delete expired OTP screen grab\",\"reason\":\"1 temporary OTP screenshot found safely.\",\"suggestedActionLabel\":\"Clean Shots\",\"confidenceScore\":93}]"
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val parsed = MediaLibraryRecommendationAnalyzer.parseGeminiRecommendationsJson(
            rawText = geminiEnvelope,
            digest = digest,
            executionTier = AiExecutionTier.PRIMARY_CLOUD
        )

        // LARGE_VIDEOS dropped because digest has 0 videos; BLURRY_PHOTOS & LOW_QUALITY_SCREENSHOTS retained with real IDs
        assertEquals(2, parsed.size)
        assertEquals(RecommendationCategory.BLURRY_PHOTOS, parsed[0].category)
        assertEquals(RecommendationPriority.HIGH, parsed[0].priority)
        assertEquals(listOf(3L), parsed[0].targetMediaIds)
        assertEquals(standaloneBlurryPhoto.sizeBytes, parsed[0].reclaimableBytes)
        assertEquals(AiExecutionTier.PRIMARY_CLOUD, parsed[0].executionTier)

        assertEquals(RecommendationCategory.LOW_QUALITY_SCREENSHOTS, parsed[1].category)
        assertEquals(listOf(4L), parsed[1].targetMediaIds)
    }

    @Test
    fun `buildDeterministicRecommendations produces all matching offline categories beyond duplicates`() {
        val digest = MediaLibraryRecommendationAnalyzer.buildLibraryDigest(
            allMedia = listOf(
                standaloneBlurryPhoto,
                lowResOtpScreenshot,
                large4kVideo,
                agedDownloadImage,
                lowResClutterPhoto
            ),
            duplicateGroups = emptyList(),
            smartExpiryDays = 60,
            nowEpochSeconds = nowSeconds
        )

        val recs = MediaLibraryRecommendationAnalyzer.buildDeterministicRecommendations(
            digest = digest,
            smartExpiryDays = 60,
            strings = testStrings
        )

        assertEquals(5, recs.size)
        assertTrue(recs.any { it.category == RecommendationCategory.BLURRY_PHOTOS && it.targetMediaIds == listOf(3L) })
        assertTrue(recs.any { it.category == RecommendationCategory.LOW_QUALITY_SCREENSHOTS && it.targetMediaIds == listOf(4L) })
        assertTrue(recs.any {
            it.category == RecommendationCategory.LARGE_VIDEOS &&
                it.actionMode == RecommendationActionMode.COMPRESS_VIDEOS
        })
        assertTrue(recs.any { it.category == RecommendationCategory.OLD_DOWNLOADS && it.targetMediaIds == listOf(7L) })
        assertTrue(recs.any { it.category == RecommendationCategory.LOW_RES_PHOTOS && it.targetMediaIds == listOf(8L) })
    }
}
