package com.example.data.ai

import com.example.data.model.DuplicateGroup
import com.example.data.model.MediaItem
import com.example.data.model.ScreenshotCategory
import com.example.data.model.formatFileSize

data class LocalizedRecommendationStrings(
    val blurryTitle: (count: Int, size: String) -> String,
    val blurryReason: (count: Int, avgSharpness: Int, size: String) -> String,
    val blurryAction: String,
    val lowResShotTitle: (count: Int, size: String) -> String,
    val lowResShotReason: (count: Int, size: String) -> String,
    val lowResShotAction: String,
    val largeVideoTitle: (count: Int, size: String) -> String,
    val largeVideoReason: (count: Int, size: String) -> String,
    val largeVideoAction: String,
    val oldDownloadTitle: (count: Int, days: Int, size: String) -> String,
    val oldDownloadReason: (count: Int, days: Int, size: String) -> String,
    val oldDownloadAction: String,
    val lowResPhotoTitle: (count: Int, size: String) -> String,
    val lowResPhotoReason: (count: Int, size: String) -> String,
    val lowResPhotoAction: String
)

/**
 * Pure Kotlin analyzer & JSON parser for media library cleanup recommendations beyond duplicates:
 * detects blurry photos, low-quality/ephemeral screenshots (while protecting receipts),
 * oversized videos, aging downloads, and low-resolution clutter.
 */
object MediaLibraryRecommendationAnalyzer {

    const val LARGE_VIDEO_THRESHOLD_BYTES = 20L * 1024L * 1024L
    const val SMALL_SCREENSHOT_BYTES_THRESHOLD = 250_000L
    const val LOW_RES_SCREENSHOT_MAX_EDGE = 720
    const val LOW_RES_PHOTO_MAX_EDGE = 640

    fun buildLibraryDigest(
        allMedia: List<MediaItem>,
        duplicateGroups: List<DuplicateGroup>,
        smartExpiryDays: Int = 60,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): MediaLibraryDigest {
        // Focus strictly beyond duplicates by excluding duplicate non-best-shot items
        val duplicateNonBestIds = duplicateGroups
            .flatMap { group -> group.items.filter { !it.isBestShot }.map { it.id } }
            .toSet()

        val claimedIds = HashSet<Long>(duplicateNonBestIds)

        val blurryPhotos = allMedia
            .filter { item ->
                item.id !in claimedIds &&
                    !item.isScreenshot &&
                    item.mimeType.startsWith("image/", ignoreCase = true) &&
                    item.isBlurry
            }
            .sortedWith(compareBy<MediaItem> { it.sharpnessScore }.thenByDescending { it.sizeBytes })
        claimedIds.addAll(blurryPhotos.map { it.id })

        val lowQualityScreenshots = allMedia
            .filter { item ->
                if (item.id in claimedIds || !item.isScreenshot) return@filter false
                if (item.screenshotCategory == ScreenshotCategory.RECEIPT_OR_DOC) return@filter false

                val isEphemeralOcr = item.screenshotCategory == ScreenshotCategory.OTP_OR_CODE ||
                    item.screenshotCategory == ScreenshotCategory.CHAT_OR_MEME
                val isLowDimensions = (item.width in 1..LOW_RES_SCREENSHOT_MAX_EDGE) ||
                    (item.height in 1..LOW_RES_SCREENSHOT_MAX_EDGE)
                val isTinyCompressedShot = item.sizeBytes in 1L until SMALL_SCREENSHOT_BYTES_THRESHOLD
                item.isBlurry || isEphemeralOcr || isLowDimensions || isTinyCompressedShot
            }
            .sortedByDescending { it.sizeBytes }
        claimedIds.addAll(lowQualityScreenshots.map { it.id })

        val largeVideos = allMedia
            .filter { item ->
                item.id !in claimedIds &&
                    item.mimeType.startsWith("video/", ignoreCase = true) &&
                    (item.isLarge || item.sizeBytes >= LARGE_VIDEO_THRESHOLD_BYTES)
            }
            .sortedByDescending { it.sizeBytes }
        claimedIds.addAll(largeVideos.map { it.id })

        val expiryCutoffEpochSeconds = nowEpochSeconds - (smartExpiryDays.coerceAtLeast(1) * 86_400L)
        val oldDownloads = allMedia
            .filter { item ->
                item.id !in claimedIds &&
                    item.screenshotCategory != ScreenshotCategory.RECEIPT_OR_DOC &&
                    item.path.contains("Download", ignoreCase = true) &&
                    item.dateModified in 1L..expiryCutoffEpochSeconds
            }
            .sortedBy { it.dateModified }
        claimedIds.addAll(oldDownloads.map { it.id })

        val lowResPhotos = allMedia
            .filter { item ->
                item.id !in claimedIds &&
                    !item.isScreenshot &&
                    !item.isBlurry &&
                    item.mimeType.startsWith("image/", ignoreCase = true) &&
                    item.width in 1..LOW_RES_PHOTO_MAX_EDGE &&
                    item.height in 1..LOW_RES_PHOTO_MAX_EDGE
            }
            .sortedBy { it.dateModified }

        val avgBlurrySharpness = if (blurryPhotos.isNotEmpty()) {
            blurryPhotos.map { it.sharpnessScore }.average().toFloat()
        } else {
            0f
        }

        return MediaLibraryDigest(
            blurryPhotos = blurryPhotos,
            lowQualityScreenshots = lowQualityScreenshots,
            largeVideos = largeVideos,
            oldDownloads = oldDownloads,
            lowResPhotos = lowResPhotos,
            averageBlurrySharpness = avgBlurrySharpness,
            excludedDuplicateCount = duplicateNonBestIds.size
        )
    }

    fun resolveTargetsForCategory(
        category: RecommendationCategory,
        digest: MediaLibraryDigest
    ): List<MediaItem> {
        return when (category) {
            RecommendationCategory.BLURRY_PHOTOS -> digest.blurryPhotos
            RecommendationCategory.LOW_QUALITY_SCREENSHOTS -> digest.lowQualityScreenshots
            RecommendationCategory.LARGE_VIDEOS -> digest.largeVideos
            RecommendationCategory.OLD_DOWNLOADS -> digest.oldDownloads
            RecommendationCategory.LOW_RES_PHOTOS -> digest.lowResPhotos
        }
    }

    fun buildPrompt(digest: MediaLibraryDigest, smartExpiryDays: Int): String {
        val blurrySamples = digest.blurryPhotos.take(5).joinToString("; ") {
            "${it.title} (${it.width}x${it.height}, sharpness=${it.sharpnessScore.toInt()}, ${it.formattedSize})"
        }
        val shotSamples = digest.lowQualityScreenshots.take(5).joinToString("; ") {
            "${it.title} (${it.width}x${it.height}, tag=${it.screenshotCategory.name}, ${it.formattedSize})"
        }
        val videoSamples = digest.largeVideos.take(5).joinToString("; ") {
            "${it.title} (${it.formattedSize})"
        }
        val downloadSamples = digest.oldDownloads.take(5).joinToString("; ") {
            "${it.title} (${it.formattedSize})"
        }
        val lowResSamples = digest.lowResPhotos.take(5).joinToString("; ") {
            "${it.title} (${it.width}x${it.height}, ${it.formattedSize})"
        }

        return """
            You are an Android AI Storage Coach. Analyze the user's media library findings (excluding ${digest.excludedDuplicateCount} duplicate photos handled separately) and generate specific, high-value cleanup recommendations:

            1. BLURRY_PHOTOS: ${digest.blurryPhotos.size} photos (${formatFileSize(digest.blurryBytes)}), avg sharpness=${digest.averageBlurrySharpness.toInt()}. Samples: [$blurrySamples]
            2. LOW_QUALITY_SCREENSHOTS: ${digest.lowQualityScreenshots.size} screenshots (${formatFileSize(digest.lowQualityScreenshotBytes)}, receipts safely excluded). Samples: [$shotSamples]
            3. LARGE_VIDEOS: ${digest.largeVideos.size} oversized videos >= 20MB (${formatFileSize(digest.largeVideosBytes)}). Samples: [$videoSamples]
            4. OLD_DOWNLOADS: ${digest.oldDownloads.size} aged downloads > $smartExpiryDays days (${formatFileSize(digest.oldDownloadsBytes)}). Samples: [$downloadSamples]
            5. LOW_RES_PHOTOS: ${digest.lowResPhotos.size} small/compressed photos <= ${LOW_RES_PHOTO_MAX_EDGE}px (${formatFileSize(digest.lowResPhotosBytes)}). Samples: [$lowResSamples]

            Rules:
            - Only include categories that have count > 0 above.
            - Keep titles concise (under 45 chars) and reasons specific to the real numbers/samples above (1-2 crisp sentences).
            - Return ONLY valid JSON matching:
            [
              {
                "category": "BLURRY_PHOTOS",
                "priority": "HIGH",
                "actionMode": "REVIEW_AND_DELETE",
                "title": "Remove blurry & out-of-focus photos",
                "reason": "Specific actionable rationale mentioning count and space saved.",
                "suggestedActionLabel": "Clean Blurry Shots",
                "confidenceScore": 94
              }
            ]
        """.trimIndent()
    }

    fun parseGeminiRecommendationsJson(
        rawText: String,
        digest: MediaLibraryDigest,
        executionTier: AiExecutionTier
    ): List<SmartRecommendation> {
        val arrayText = extractJsonArrayString(rawText) ?: return emptyList()
        val parsed = mutableListOf<SmartRecommendation>()
        val seenCategories = HashSet<RecommendationCategory>()

        val objectBlocks = splitJsonObjects(arrayText)
        for (block in objectBlocks) {
            val categoryStr = extractJsonStringField(block, "category") ?: continue
            val category = try {
                RecommendationCategory.valueOf(categoryStr.trim().uppercase())
            } catch (_: Exception) {
                continue
            }
            if (!seenCategories.add(category)) continue
            val targets = resolveTargetsForCategory(category, digest)
            if (targets.isEmpty()) continue

            val priority = parsePriority(extractJsonStringField(block, "priority") ?: "MEDIUM")
            val defaultActionMode = when (category) {
                RecommendationCategory.LARGE_VIDEOS -> RecommendationActionMode.COMPRESS_VIDEOS
                else -> RecommendationActionMode.REVIEW_AND_DELETE
            }
            val actionMode = parseActionMode(extractJsonStringField(block, "actionMode").orEmpty(), defaultActionMode)
            val title = extractJsonStringField(block, "title")?.trim().orEmpty()
            val reason = extractJsonStringField(block, "reason")?.trim().orEmpty()
            val actionLabel = extractJsonStringField(block, "suggestedActionLabel")?.trim().orEmpty()
            if (title.isBlank() || reason.isBlank()) continue

            val confidence = (extractJsonIntField(block, "confidenceScore") ?: 92).coerceIn(50, 100)

            parsed.add(
                SmartRecommendation(
                    id = "gemini_${category.name.lowercase()}",
                    category = category,
                    priority = priority,
                    actionMode = actionMode,
                    title = title,
                    reason = reason,
                    suggestedActionLabel = actionLabel.ifBlank { category.name },
                    confidenceScore = confidence,
                    targetMediaIds = targets.map { it.id },
                    itemCount = targets.size,
                    reclaimableBytes = targets.sumOf { it.sizeBytes },
                    executionTier = executionTier
                )
            )
        }
        return parsed
    }

    fun buildDeterministicRecommendations(
        digest: MediaLibraryDigest,
        smartExpiryDays: Int,
        strings: LocalizedRecommendationStrings,
        executionTier: AiExecutionTier = AiExecutionTier.ON_DEVICE_LOCAL
    ): List<SmartRecommendation> {
        val list = mutableListOf<SmartRecommendation>()

        if (digest.blurryPhotos.isNotEmpty()) {
            val formatted = formatFileSize(digest.blurryBytes)
            list.add(
                SmartRecommendation(
                    id = "rec_blurry_photos",
                    category = RecommendationCategory.BLURRY_PHOTOS,
                    priority = if (digest.blurryPhotos.size >= 5 || digest.blurryBytes >= 25_000_000L) {
                        RecommendationPriority.HIGH
                    } else {
                        RecommendationPriority.MEDIUM
                    },
                    actionMode = RecommendationActionMode.REVIEW_AND_DELETE,
                    title = strings.blurryTitle(digest.blurryPhotos.size, formatted),
                    reason = strings.blurryReason(
                        digest.blurryPhotos.size,
                        digest.averageBlurrySharpness.toInt(),
                        formatted
                    ),
                    suggestedActionLabel = strings.blurryAction,
                    confidenceScore = 96,
                    targetMediaIds = digest.blurryPhotos.map { it.id },
                    itemCount = digest.blurryPhotos.size,
                    reclaimableBytes = digest.blurryBytes,
                    executionTier = executionTier
                )
            )
        }

        if (digest.lowQualityScreenshots.isNotEmpty()) {
            val formatted = formatFileSize(digest.lowQualityScreenshotBytes)
            list.add(
                SmartRecommendation(
                    id = "rec_low_quality_screenshots",
                    category = RecommendationCategory.LOW_QUALITY_SCREENSHOTS,
                    priority = if (digest.lowQualityScreenshots.size >= 6) {
                        RecommendationPriority.HIGH
                    } else {
                        RecommendationPriority.MEDIUM
                    },
                    actionMode = RecommendationActionMode.REVIEW_AND_DELETE,
                    title = strings.lowResShotTitle(digest.lowQualityScreenshots.size, formatted),
                    reason = strings.lowResShotReason(digest.lowQualityScreenshots.size, formatted),
                    suggestedActionLabel = strings.lowResShotAction,
                    confidenceScore = 95,
                    targetMediaIds = digest.lowQualityScreenshots.map { it.id },
                    itemCount = digest.lowQualityScreenshots.size,
                    reclaimableBytes = digest.lowQualityScreenshotBytes,
                    executionTier = executionTier
                )
            )
        }

        if (digest.largeVideos.isNotEmpty()) {
            val formatted = formatFileSize(digest.largeVideosBytes)
            list.add(
                SmartRecommendation(
                    id = "rec_large_videos",
                    category = RecommendationCategory.LARGE_VIDEOS,
                    priority = RecommendationPriority.HIGH,
                    actionMode = RecommendationActionMode.COMPRESS_VIDEOS,
                    title = strings.largeVideoTitle(digest.largeVideos.size, formatted),
                    reason = strings.largeVideoReason(digest.largeVideos.size, formatted),
                    suggestedActionLabel = strings.largeVideoAction,
                    confidenceScore = 94,
                    targetMediaIds = digest.largeVideos.map { it.id },
                    itemCount = digest.largeVideos.size,
                    reclaimableBytes = digest.largeVideosBytes,
                    executionTier = executionTier
                )
            )
        }

        if (digest.oldDownloads.isNotEmpty()) {
            val formatted = formatFileSize(digest.oldDownloadsBytes)
            list.add(
                SmartRecommendation(
                    id = "rec_old_downloads",
                    category = RecommendationCategory.OLD_DOWNLOADS,
                    priority = RecommendationPriority.MEDIUM,
                    actionMode = RecommendationActionMode.REVIEW_AND_DELETE,
                    title = strings.oldDownloadTitle(digest.oldDownloads.size, smartExpiryDays, formatted),
                    reason = strings.oldDownloadReason(digest.oldDownloads.size, smartExpiryDays, formatted),
                    suggestedActionLabel = strings.oldDownloadAction,
                    confidenceScore = 91,
                    targetMediaIds = digest.oldDownloads.map { it.id },
                    itemCount = digest.oldDownloads.size,
                    reclaimableBytes = digest.oldDownloadsBytes,
                    executionTier = executionTier
                )
            )
        }

        if (digest.lowResPhotos.isNotEmpty()) {
            val formatted = formatFileSize(digest.lowResPhotosBytes)
            list.add(
                SmartRecommendation(
                    id = "rec_low_res_photos",
                    category = RecommendationCategory.LOW_RES_PHOTOS,
                    priority = RecommendationPriority.LOW,
                    actionMode = RecommendationActionMode.REVIEW_AND_DELETE,
                    title = strings.lowResPhotoTitle(digest.lowResPhotos.size, formatted),
                    reason = strings.lowResPhotoReason(digest.lowResPhotos.size, formatted),
                    suggestedActionLabel = strings.lowResPhotoAction,
                    confidenceScore = 89,
                    targetMediaIds = digest.lowResPhotos.map { it.id },
                    itemCount = digest.lowResPhotos.size,
                    reclaimableBytes = digest.lowResPhotosBytes,
                    executionTier = executionTier
                )
            )
        }

        return list
    }

    private fun extractJsonArrayString(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.startsWith("{")) {
            val innerText = extractJsonStringField(trimmed, "text")
            if (!innerText.isNullOrBlank()) {
                return extractJsonArrayString(innerText)
            }
        }
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start != -1 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return null
    }

    private fun splitJsonObjects(arrayText: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escape = false
        var objStart = -1

        for (i in arrayText.indices) {
            val ch = arrayText[i]
            if (escape) {
                escape = false
                continue
            }
            if (ch == '\\') {
                escape = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') {
                    if (depth == 0) objStart = i
                    depth++
                } else if (ch == '}') {
                    depth--
                    if (depth == 0 && objStart != -1) {
                        objects.add(arrayText.substring(objStart, i + 1))
                        objStart = -1
                    }
                }
            }
        }
        return objects
    }

    private fun extractJsonStringField(jsonObjText: String, key: String): String? {
        val keyToken = "\"$key\""
        var searchFrom = 0
        while (true) {
            val keyIdx = jsonObjText.indexOf(keyToken, searchFrom)
            if (keyIdx == -1) return null
            val colonIdx = jsonObjText.indexOf(':', keyIdx + keyToken.length)
            if (colonIdx == -1) return null
            var valStart = colonIdx + 1
            while (valStart < jsonObjText.length && jsonObjText[valStart].isWhitespace()) {
                valStart++
            }
            if (valStart >= jsonObjText.length || jsonObjText[valStart] != '"') {
                searchFrom = colonIdx + 1
                continue
            }
            val sb = StringBuilder()
            var i = valStart + 1
            var escaped = false
            while (i < jsonObjText.length) {
                val c = jsonObjText[i]
                if (escaped) {
                    when (c) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(c)
                    }
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    return sb.toString()
                } else {
                    sb.append(c)
                }
                i++
            }
            return null
        }
    }

    private fun extractJsonIntField(jsonObjText: String, key: String): Int? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
        return regex.find(jsonObjText)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun parsePriority(raw: String): RecommendationPriority {
        return try {
            RecommendationPriority.valueOf(raw.trim().uppercase())
        } catch (_: Exception) {
            RecommendationPriority.MEDIUM
        }
    }

    private fun parseActionMode(
        raw: String,
        fallback: RecommendationActionMode
    ): RecommendationActionMode {
        if (raw.isBlank()) return fallback
        return try {
            RecommendationActionMode.valueOf(raw.trim().uppercase())
        } catch (_: Exception) {
            fallback
        }
    }
}
