package com.example.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.data.model.MediaItem
import com.example.data.model.StorageStats
import com.example.ui.theme.ColorApps
import com.example.ui.theme.ColorDocs
import com.example.ui.theme.ColorPhotos
import com.example.ui.theme.ColorVideos
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class StorageFileTypeCategory {
    PHOTOS,
    VIDEOS,
    APPS,
    DOCUMENTS
}

enum class ChartVisualStyle {
    PIE,
    DONUT
}

enum class StorageChartScope {
    FILE_TYPES,
    ALL_STORAGE
}

data class FileTypeBreakdownEntry(
    val category: StorageFileTypeCategory,
    @get:StringRes val labelRes: Int,
    val bytes: Long,
    val itemCount: Int?,
    val percentageOfCategorized: Float,
    val percentageOfDevice: Float,
    val color: Color
)

data class SliceAngleSpan(
    val index: Int,
    val startAngleDeg: Float,
    val sweepAngleDeg: Float
)

object StorageAnalyzerCalculator {

    private val docExtensions = setOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx")

    fun countDocumentItems(allMedia: List<MediaItem>): Int {
        return allMedia.count { item ->
            val mime = item.mimeType.lowercase()
            val ext = item.title.substringAfterLast('.', "").lowercase()
            mime.contains("pdf") ||
                mime.contains("msword") ||
                mime.contains("officedocument") ||
                mime.startsWith("text/") ||
                ext in docExtensions
        }
    }

    fun buildFileTypeBreakdown(
        stats: StorageStats,
        docCount: Int? = null
    ): List<FileTypeBreakdownEntry> {
        val photoBytes = stats.photoBytes.coerceAtLeast(0L)
        val videoBytes = stats.videoBytes.coerceAtLeast(0L)
        val appBytes = stats.appBytes.coerceAtLeast(0L)
        val docBytes = stats.docBytes.coerceAtLeast(0L)

        val categorizedTotal = photoBytes + videoBytes + appBytes + docBytes
        val deviceTotal = stats.totalBytes.coerceAtLeast(0L)

        fun shareOf(value: Long, total: Long): Float {
            return if (total > 0L) (value.toFloat() / total.toFloat()) * 100f else 0f
        }

        return listOf(
            FileTypeBreakdownEntry(
                category = StorageFileTypeCategory.PHOTOS,
                labelRes = R.string.category_photos,
                bytes = photoBytes,
                itemCount = stats.photoCount.coerceAtLeast(0),
                percentageOfCategorized = shareOf(photoBytes, categorizedTotal),
                percentageOfDevice = shareOf(photoBytes, deviceTotal),
                color = ColorPhotos
            ),
            FileTypeBreakdownEntry(
                category = StorageFileTypeCategory.VIDEOS,
                labelRes = R.string.category_videos,
                bytes = videoBytes,
                itemCount = stats.videoCount.coerceAtLeast(0),
                percentageOfCategorized = shareOf(videoBytes, categorizedTotal),
                percentageOfDevice = shareOf(videoBytes, deviceTotal),
                color = ColorVideos
            ),
            FileTypeBreakdownEntry(
                category = StorageFileTypeCategory.APPS,
                labelRes = R.string.storage_cat_apps,
                bytes = appBytes,
                itemCount = null,
                percentageOfCategorized = shareOf(appBytes, categorizedTotal),
                percentageOfDevice = shareOf(appBytes, deviceTotal),
                color = ColorApps
            ),
            FileTypeBreakdownEntry(
                category = StorageFileTypeCategory.DOCUMENTS,
                labelRes = R.string.storage_cat_docs,
                bytes = docBytes,
                itemCount = docCount,
                percentageOfCategorized = shareOf(docBytes, categorizedTotal),
                percentageOfDevice = shareOf(docBytes, deviceTotal),
                color = ColorDocs
            )
        )
    }

    fun computeSliceAngles(sliceBytes: List<Long>): List<SliceAngleSpan> {
        val sanitized = sliceBytes.map { it.coerceAtLeast(0L) }
        val total = sanitized.sum()
        if (total <= 0L) {
            return sanitized.indices.map { idx -> SliceAngleSpan(idx, -90f, 0f) }
        }

        var currentStart = -90f
        return sanitized.mapIndexed { idx, bytes ->
            val sweep = (bytes.toFloat() / total.toFloat()) * 360f
            val span = SliceAngleSpan(
                index = idx,
                startAngleDeg = currentStart,
                sweepAngleDeg = sweep
            )
            currentStart += sweep
            span
        }
    }

    /**
     * Resolves a Canvas tap coordinate `(tapX, tapY)` into the index of the slice
     * under the pointer, accounting for Pie (`innerRadiusPx = 0f`) or Donut (`innerRadiusPx > 0f`).
     * Returns -1 when outside the chart ring/disc or when total bytes == 0.
     */
    fun findSliceIndexAtTap(
        tapX: Float,
        tapY: Float,
        width: Float,
        height: Float,
        outerRadiusPx: Float,
        innerRadiusPx: Float,
        sliceBytes: List<Long>
    ): Int {
        val total = sliceBytes.sumOf { it.coerceAtLeast(0L) }
        if (total <= 0L || outerRadiusPx <= 0f) return -1

        val cx = width / 2f
        val cy = height / 2f
        val dx = tapX - cx
        val dy = tapY - cy
        val distance = hypot(dx, dy)

        if (distance > outerRadiusPx || distance < innerRadiusPx) {
            return -1
        }

        // Convert atan2 (-180..180 where 0 is 3 o'clock) to clockwise degrees from 12 o'clock (-90°)
        val angleRad = atan2(dy.toDouble(), dx.toDouble())
        val angleDegFrom3OClock = Math.toDegrees(angleRad).toFloat()
        val clockwiseFromTop = ((angleDegFrom3OClock + 90f) % 360f + 360f) % 360f

        var accumulated = 0f
        sliceBytes.forEachIndexed { index, rawBytes ->
            val bytes = rawBytes.coerceAtLeast(0L)
            if (bytes > 0L) {
                val sweep = (bytes.toFloat() / total.toFloat()) * 360f
                val end = accumulated + sweep
                if (clockwiseFromTop >= accumulated && clockwiseFromTop <= end) {
                    return index
                }
                accumulated = end
            }
        }
        return -1
    }

    fun computeSliceCenterOffset(
        startAngleDeg: Float,
        sweepAngleDeg: Float,
        distancePx: Float
    ): Pair<Float, Float> {
        val midAngleRad = ((startAngleDeg + sweepAngleDeg / 2f) * PI / 180.0)
        return Pair(
            (cos(midAngleRad) * distancePx).toFloat(),
            (sin(midAngleRad) * distancePx).toFloat()
        )
    }
}
