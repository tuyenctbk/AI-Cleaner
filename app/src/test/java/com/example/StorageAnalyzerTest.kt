package com.example

import com.example.data.model.MediaItem
import com.example.data.model.StorageStats
import com.example.ui.components.StorageAnalyzerCalculator
import com.example.ui.components.StorageFileTypeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAnalyzerTest {

    @Test
    fun `buildFileTypeBreakdown returns all four file types with accurate percentages`() {
        val stats = StorageStats(
            totalBytes = 100_000L,
            usedBytes = 80_000L,
            freeBytes = 20_000L,
            photoBytes = 40_000L,
            videoBytes = 20_000L,
            appBytes = 15_000L,
            docBytes = 5_000L,
            photoCount = 120,
            videoCount = 14
        )

        val entries = StorageAnalyzerCalculator.buildFileTypeBreakdown(stats = stats, docCount = 8)

        assertEquals(4, entries.size)
        assertEquals(StorageFileTypeCategory.PHOTOS, entries[0].category)
        assertEquals(StorageFileTypeCategory.VIDEOS, entries[1].category)
        assertEquals(StorageFileTypeCategory.APPS, entries[2].category)
        assertEquals(StorageFileTypeCategory.DOCUMENTS, entries[3].category)

        // Categorized total = 40k + 20k + 15k + 5k = 80k
        assertEquals(50f, entries[0].percentageOfCategorized, 0.01f)
        assertEquals(25f, entries[1].percentageOfCategorized, 0.01f)
        assertEquals(18.75f, entries[2].percentageOfCategorized, 0.01f)
        assertEquals(6.25f, entries[3].percentageOfCategorized, 0.01f)

        // Device percentages
        assertEquals(40f, entries[0].percentageOfDevice, 0.01f)
        assertEquals(20f, entries[1].percentageOfDevice, 0.01f)
        assertEquals(15f, entries[2].percentageOfDevice, 0.01f)
        assertEquals(5f, entries[3].percentageOfDevice, 0.01f)

        assertEquals(120, entries[0].itemCount)
        assertEquals(14, entries[1].itemCount)
        assertEquals(null, entries[2].itemCount)
        assertEquals(8, entries[3].itemCount)
    }

    @Test
    fun `buildFileTypeBreakdown handles zero bytes gracefully`() {
        val stats = StorageStats()
        val entries = StorageAnalyzerCalculator.buildFileTypeBreakdown(stats)

        assertEquals(4, entries.size)
        entries.forEach { entry ->
            assertEquals(0L, entry.bytes)
            assertEquals(0f, entry.percentageOfCategorized, 0.001f)
            assertEquals(0f, entry.percentageOfDevice, 0.001f)
        }
    }

    @Test
    fun `computeSliceAngles spans full circle starting from -90 degrees`() {
        val spans = StorageAnalyzerCalculator.computeSliceAngles(listOf(50L, 25L, 25L))

        assertEquals(3, spans.size)
        assertEquals(-90f, spans[0].startAngleDeg, 0.01f)
        assertEquals(180f, spans[0].sweepAngleDeg, 0.01f)

        assertEquals(90f, spans[1].startAngleDeg, 0.01f)
        assertEquals(90f, spans[1].sweepAngleDeg, 0.01f)

        assertEquals(180f, spans[2].startAngleDeg, 0.01f)
        assertEquals(90f, spans[2].sweepAngleDeg, 0.01f)

        val totalSweep = spans.sumOf { it.sweepAngleDeg.toDouble() }
        assertEquals(360.0, totalSweep, 0.05)
    }

    @Test
    fun `findSliceIndexAtTap resolves pie wedges and donut ring boundaries accurately`() {
        val slices = listOf(25L, 25L, 25L, 25L) // 4 quadrants clockwise from 12 o'clock
        val width = 200f
        val height = 200f
        val outerR = 90f

        // Pie mode: innerR = 0f
        // Top-right quadrant (1:30 direction -> x=140, y=60) -> slice 0
        assertEquals(0, StorageAnalyzerCalculator.findSliceIndexAtTap(140f, 60f, width, height, outerR, 0f, slices))
        // Bottom-right quadrant (4:30 direction -> x=140, y=140) -> slice 1
        assertEquals(1, StorageAnalyzerCalculator.findSliceIndexAtTap(140f, 140f, width, height, outerR, 0f, slices))
        // Bottom-left quadrant (7:30 direction -> x=60, y=140) -> slice 2
        assertEquals(2, StorageAnalyzerCalculator.findSliceIndexAtTap(60f, 140f, width, height, outerR, 0f, slices))
        // Top-left quadrant (10:30 direction -> x=60, y=60) -> slice 3
        assertEquals(3, StorageAnalyzerCalculator.findSliceIndexAtTap(60f, 60f, width, height, outerR, 0f, slices))

        // Outside outer radius -> -1
        assertEquals(-1, StorageAnalyzerCalculator.findSliceIndexAtTap(199f, 199f, width, height, outerR, 0f, slices))

        // Donut mode: center tap inside inner hole (innerR = 50f) -> -1
        assertEquals(-1, StorageAnalyzerCalculator.findSliceIndexAtTap(110f, 95f, width, height, outerR, 50f, slices))
        // Donut mode: ring tap at r ~ 70 in top-right -> slice 0
        assertEquals(0, StorageAnalyzerCalculator.findSliceIndexAtTap(150f, 50f, width, height, outerR, 50f, slices))
    }

    @Test
    fun `countDocumentItems counts pdfs, office docs, and text files only`() {
        val media = listOf(
            MediaItem(1L, "vacation.jpg", "/DCIM/vacation.jpg", "u1", 100L, 1L, "image/jpeg"),
            MediaItem(2L, "invoice.pdf", "/Documents/invoice.pdf", "u2", 200L, 1L, "application/pdf"),
            MediaItem(3L, "notes.txt", "/Documents/notes.txt", "u3", 50L, 1L, "text/plain"),
            MediaItem(4L, "clip.mp4", "/Movies/clip.mp4", "u4", 500L, 1L, "video/mp4"),
            MediaItem(5L, "report.docx", "/Download/report.docx", "u5", 300L, 1L, "application/octet-stream")
        )

        val count = StorageAnalyzerCalculator.countDocumentItems(media)
        assertTrue(count == 3)
    }
}
