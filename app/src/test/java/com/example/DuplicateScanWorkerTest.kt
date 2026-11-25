package com.example

import com.example.data.model.DuplicateGroup
import com.example.data.model.MediaItem
import com.example.data.scanner.MediaStoreDuplicateScanner
import com.example.data.scanner.PerceptualHasher
import com.example.worker.DuplicateScanWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateScanWorkerTest {

    private fun samplePhoto(
        id: Long,
        title: String,
        sizeBytes: Long,
        width: Int,
        height: Int,
        dHash: Long?,
        sharpnessScore: Float = 70f
    ) = MediaItem(
        id = id,
        title = title,
        path = "/storage/emulated/0/DCIM/Camera/$title",
        uriString = "content://media/external/images/media/$id",
        sizeBytes = sizeBytes,
        dateModified = 1_700_000_000_000L + id * 1_000L,
        mimeType = "image/jpeg",
        width = width,
        height = height,
        sharpnessScore = sharpnessScore,
        dHash = dHash
    )

    @Test
    fun buildFlaggedEntities_flagsCrossResolutionVisuallySimilarPhotosViaPerceptualHasher() {
        val originalHash = 0x5A5A_3C3C_F0F0_1234L
        val recompressedHash = originalHash xor 0b10101L // Hamming distance = 3 (<= 6)
        val distinctHash = originalHash.inv() // Hamming distance = 64

        val cameraShot = samplePhoto(
            id = 101L,
            title = "IMG_CAMERA.jpg",
            sizeBytes = 4_200_000L,
            width = 4000,
            height = 3000,
            dHash = originalHash,
            sharpnessScore = 88f
        )
        val resizedCopy = samplePhoto(
            id = 102L,
            title = "IMG_WA_SHARE.jpg",
            sizeBytes = 380_000L,
            width = 1600,
            height = 1200,
            dHash = recompressedHash,
            sharpnessScore = 55f
        )
        val unrelated = samplePhoto(
            id = 103L,
            title = "IMG_OTHER.jpg",
            sizeBytes = 3_100_000L,
            width = 4000,
            height = 3000,
            dHash = distinctHash,
            sharpnessScore = 80f
        )

        val groups: List<DuplicateGroup> = MediaStoreDuplicateScanner.findDuplicateGroups(
            listOf(cameraShot, resizedCopy, unrelated)
        )
        assertEquals(1, groups.size)

        val entities = DuplicateScanWorker.buildFlaggedEntities(groups, flaggedAtMillis = 123456789L)
        assertEquals(2, entities.size)

        val bestEntity = entities.first { it.mediaId == 101L }
        val flaggedDuplicate = entities.first { it.mediaId == 102L }

        assertTrue(bestEntity.isBestShot)
        assertEquals(0, bestEntity.hammingDistance)
        assertFalse(flaggedDuplicate.isBestShot)
        assertEquals(PerceptualHasher.hammingDistance(originalHash, recompressedHash), flaggedDuplicate.hammingDistance)
        assertEquals(3, flaggedDuplicate.hammingDistance)
        assertEquals(123456789L, flaggedDuplicate.flaggedAtMillis)
        assertFalse(flaggedDuplicate.isReviewed)
    }

    @Test
    fun selectHashCandidateIds_prioritizesDuplicateBucketsBeforeSingletons() {
        val items = listOf(
            samplePhoto(1L, "a.jpg", 1_000_000L, 4000, 3000, null),
            samplePhoto(2L, "b.jpg", 2_000_000L, 1920, 1080, null),
            samplePhoto(3L, "c.jpg", 2_000_000L, 1080, 1920, null),
            samplePhoto(4L, "d.jpg", 3_000_000L, 800, 600, null)
        )

        val selected = DuplicateScanWorker.selectHashCandidateIds(items, maxCandidates = 2)
        assertEquals(setOf(2L, 3L), selected)
    }
}
