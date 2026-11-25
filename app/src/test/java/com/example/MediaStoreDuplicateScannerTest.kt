package com.example

import com.example.data.model.MediaItem
import com.example.data.scanner.MediaStoreDuplicateScanner
import com.example.worker.WorkScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreDuplicateScannerTest {

    private fun image(
        id: Long,
        title: String = "IMG_$id.jpg",
        sizeBytes: Long = 2_400_000L,
        width: Int = 4000,
        height: Int = 3000,
        dateModified: Long = 1_700_000_000_000L,
        sharpnessScore: Float = 65f,
        isBlurry: Boolean = false,
        dHash: Long? = null
    ) = MediaItem(
        id = id,
        title = title,
        path = "/storage/emulated/0/DCIM/Camera/$title",
        uriString = "content://media/external/images/media/$id",
        sizeBytes = sizeBytes,
        dateModified = dateModified,
        mimeType = "image/jpeg",
        width = width,
        height = height,
        sharpnessScore = sharpnessScore,
        isBlurry = isBlurry,
        dHash = dHash
    )

    @Test
    fun tier1_groupsIdenticalFileSizeAndResolution_includingRotatedCopies() {
        val original = image(id = 1, title = "IMG_001.jpg", sizeBytes = 3_145_728L, width = 4000, height = 3000, dateModified = 1_700_000_000_000L)
        val rotatedCopy = image(id = 2, title = "IMG_001 (1).jpg", sizeBytes = 3_145_728L, width = 3000, height = 4000, dateModified = 1_700_000_060_000L)
        val distinctSize = image(id = 3, title = "IMG_002.jpg", sizeBytes = 4_000_000L, width = 4000, height = 3000, dateModified = 1_700_000_500_000L)

        val groups = MediaStoreDuplicateScanner.findDuplicateGroups(listOf(original, rotatedCopy, distinctSize))

        assertEquals(1, groups.size)
        val group = groups.first()
        assertEquals(2, group.items.size)
        assertEquals(1L, group.bestItemId)
        assertTrue(group.items.first { it.id == 1L }.isBestShot)
        assertFalse(group.items.first { it.id == 1L }.isSelected)
        assertTrue(group.items.first { it.id == 2L }.isSelected)
        assertTrue(group.matchReason.contains("Exact Size"))
    }

    @Test
    fun tier1_splitsFalsePositiveSameSizeAndResolutionWhenDHashDiverges() {
        val photoA = image(id = 10, sizeBytes = 2_000_000L, width = 4000, height = 3000, dHash = 0x0000_0000_0000_0000L)
        val photoB = image(id = 11, sizeBytes = 2_000_000L, width = 4000, height = 3000, dHash = -1L) // 64 bits different

        val groups = MediaStoreDuplicateScanner.findDuplicateGroups(listOf(photoA, photoB))

        assertTrue("Images with Hamming distance 64 must not be grouped as duplicates", groups.isEmpty())
    }

    @Test
    fun tier2_groupsSameResolutionRecompressedPhotosWithinDHashThreshold() {
        val baseHash = 0x0F0F_0F0F_0F0F_0F0FL
        val nearHash = baseHash xor 0b101101L // Hamming distance = 4 (<= 6)
        val highQuality = image(id = 20, title = "VACATION.jpg", sizeBytes = 3_800_000L, width = 4032, height = 3024, sharpnessScore = 82f, dHash = baseHash)
        val whatsappRecompressed = image(id = 21, title = "IMG-WA001.jpg", sizeBytes = 420_000L, width = 4032, height = 3024, sharpnessScore = 54f, dHash = nearHash)

        val groups = MediaStoreDuplicateScanner.findDuplicateGroups(listOf(highQuality, whatsappRecompressed))

        assertEquals(1, groups.size)
        assertEquals(20L, groups.first().bestItemId)
        assertTrue(groups.first().matchReason.contains("dHash"))
    }

    @Test
    fun bestShotSelection_prefersSharpFocusOverLargerBlurryShot() {
        val hash = 0x1234_5678_9ABC_DEF0L
        val blurryLarge = image(id = 30, sizeBytes = 5_000_000L, width = 4000, height = 3000, sharpnessScore = 15f, isBlurry = true, dHash = hash)
        val sharpSlightlySmaller = image(id = 31, sizeBytes = 4_200_000L, width = 4000, height = 3000, sharpnessScore = 84f, isBlurry = false, dHash = hash xor 0b11L)

        val groups = MediaStoreDuplicateScanner.findDuplicateGroups(listOf(blurryLarge, sharpSlightlySmaller))

        assertEquals(1, groups.size)
        assertEquals(31L, groups.first().bestItemId)
        assertTrue(groups.first().items.first { it.id == 30L }.isSelected)
        assertFalse(groups.first().items.first { it.id == 31L }.isSelected)
    }

    @Test
    fun laplacianSharpness_ranksHighContrastEdgesFarAboveFlatBlurredFrame() {
        val size = 32
        val flatBlurred = IntArray(size * size) { 128 }
        val crispEdges = IntArray(size * size) { idx ->
            val x = idx % size
            val y = idx / size
            if ((x + y) % 2 == 0) 240 else 15
        }

        val blurScore = MediaStoreDuplicateScanner.computeLaplacianSharpnessScore(flatBlurred, size, size)
        val sharpScore = MediaStoreDuplicateScanner.computeLaplacianSharpnessScore(crispEdges, size, size)

        assertTrue("Expected flat blurred score ($blurScore) < 5", blurScore < 5f)
        assertTrue("Expected crisp checkerboard score ($sharpScore) > 80", sharpScore > 80f)
    }

    @Test
    fun dHashComputation_isDeterministicAndReflectsHorizontalGradients() {
        // Monotonically decreasing left-to-right -> all 64 bits set (-1L)
        val decreasing = IntArray(72) { idx -> 255 - (idx % 9) * 25 }
        val allOnes = MediaStoreDuplicateScanner.computeDHashFromGray9x8(decreasing)
        assertEquals(-1L, allOnes)

        // Monotonically increasing left-to-right -> all 64 bits zero (0L)
        val increasing = IntArray(72) { idx -> (idx % 9) * 25 }
        val allZeros = MediaStoreDuplicateScanner.computeDHashFromGray9x8(increasing)
        assertEquals(0L, allZeros)
        assertEquals(64, MediaStoreDuplicateScanner.hammingDistance(allOnes, allZeros))
    }

    @Test
    fun ignoresTinyFilesBelow10KbAndGroupsUnknownResolutionByExactSize() {
        val tinyA = image(id = 40, sizeBytes = 4_096L, width = 100, height = 100)
        val tinyB = image(id = 41, sizeBytes = 4_096L, width = 100, height = 100)
        val unknownResA = image(id = 42, sizeBytes = 1_500_000L, width = 0, height = 0)
        val unknownResB = image(id = 43, sizeBytes = 1_500_000L, width = 0, height = 0)

        val groups = MediaStoreDuplicateScanner.findDuplicateGroups(listOf(tinyA, tinyB, unknownResA, unknownResB))

        assertEquals(1, groups.size)
        assertEquals(setOf(42L, 43L), groups.first().items.map { it.id }.toSet())
    }

    @Test
    fun workScheduler_initialDelayWithinOneWeekWindow() {
        val delayMs = WorkScheduler.computeInitialDelayMillis("Sunday", "02:00 AM")
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L
        assertTrue("Initial delay should be >= 1 minute", delayMs >= 60_000L)
        assertTrue("Initial delay should be <= 7 days", delayMs <= sevenDaysMs)
    }
}
