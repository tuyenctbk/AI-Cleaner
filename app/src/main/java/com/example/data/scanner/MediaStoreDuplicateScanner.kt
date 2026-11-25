package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.example.R
import com.example.data.ai.AiQuotaManager
import com.example.data.ai.OnDeviceVisionAnalyzer
import com.example.data.model.DuplicateGroup
import com.example.data.model.MediaItem
import com.example.data.model.ScreenshotCategory
import com.example.data.model.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Local MediaStore duplicate image scanner that uses file size and resolution
 * as primary fast heuristics, augmented by 64-bit perceptual difference hashing
 * (dHash, Hamming distance <= 6) and Laplacian variance sharpness scoring for
 * accurate blur detection and Best Shot selection.
 */
class MediaStoreDuplicateScanner(private val context: Context) {

    data class DuplicateLabels(
        val exactLabel: (Int, Int, Int) -> String = { w, h, count -> "Exact Duplicates • ${w}×${h} ($count files)" },
        val exactReasonWithDHash: (String) -> String = { size -> "Exact Size ($size) & Resolution + dHash" },
        val exactReason: (String) -> String = { size -> "Exact Size ($size) & Resolution" },
        val similarLabel: (Int, Int, Int) -> String = { w, h, count -> "Visually Similar • ${w}×${h} ($count shots)" },
        val burstLabel: (Int, Int, Int) -> String = { w, h, count -> "Burst Series • ${w}×${h} ($count shots)" },
        val similarReason: () -> String = { "Same Resolution + Visual dHash" },
        val burstReason: () -> String = { "Same Resolution + Burst Timing" },
        val sizeLabel: (String, Int) -> String = { size, count -> "Duplicate Files • $size ($count copies)" },
        val sizeReason: (String) -> String = { size -> "Exact File Size ($size)" }
    )

    companion object {
        const val MIN_DUPLICATE_SIZE_BYTES = 10_240L // 10 KB minimum to avoid tiny icons/thumbnails
        const val DHASH_NEAR_DUPLICATE_MAX_DISTANCE = PerceptualHasher.DEFAULT_SIMILARITY_THRESHOLD
        const val DHASH_EXACT_COLLISION_MAX_DISTANCE = 10
        const val BURST_TIME_WINDOW_MS = 15_000L
        const val BLUR_SHARPNESS_THRESHOLD = 26f
        private const val MAX_IMAGES_TO_SCAN = 500
        private const val MAX_VISUAL_FINGERPRINT_CANDIDATES = 180

        /**
         * Computes Hamming distance between two 64-bit perceptual dHashes.
         */
        fun hammingDistance(hashA: Long, hashB: Long): Int =
            PerceptualHasher.hammingDistance(hashA, hashB)

        /**
         * Computes a 64-bit perceptual difference hash (dHash) from a 9x8 grayscale luminance matrix.
         * Expects [gray9x8] of length 72 in row-major order (width = 9, height = 8).
         */
        fun computeDHashFromGray9x8(gray9x8: IntArray): Long =
            PerceptualHasher.computeDHashFromGray9x8(gray9x8)

        /**
         * Computes normalized 0..100 sharpness score using the 3x3 Laplacian variance operator:
         * [ 0,  1,  0 ]
         * [ 1, -4,  1 ]
         * [ 0,  1,  0 ]
         * Higher variance indicates crisp edges; low variance indicates motion blur / out-of-focus shots.
         */
        fun computeLaplacianSharpnessScore(grayPixels: IntArray, width: Int, height: Int): Float {
            if (width < 3 || height < 3 || grayPixels.size < width * height) return 0f
            var sum = 0.0
            var sumSq = 0.0
            var count = 0
            for (y in 1 until height - 1) {
                val row = y * width
                val prevRow = (y - 1) * width
                val nextRow = (y + 1) * width
                for (x in 1 until width - 1) {
                    val laplacian = (
                        grayPixels[prevRow + x] +
                            grayPixels[nextRow + x] +
                            grayPixels[row + x - 1] +
                            grayPixels[row + x + 1] -
                            4 * grayPixels[row + x]
                        ).toDouble()
                    sum += laplacian
                    sumSq += laplacian * laplacian
                    count++
                }
            }
            if (count == 0) return 0f
            val mean = sum / count
            val variance = (sumSq / count - mean * mean).coerceAtLeast(0.0)
            // Logarithmic normalization maps typical photo Laplacian variance [0..1500+] to [0..100]
            val normalized = (ln(variance + 1.0) / ln(1500.0) * 100.0).toFloat()
            return normalized.coerceIn(0f, 100f)
        }

        /**
         * Pure duplicate-identification engine using File Size and Resolution as primary heuristics,
         * refined by dHash Hamming distance and Laplacian sharpness Best Shot ranking.
         * Fully deterministic and unit-testable on the JVM without Android framework dependencies.
         */
        fun findDuplicateGroups(
            allMedia: List<MediaItem>,
            labels: DuplicateLabels = DuplicateLabels()
        ): List<DuplicateGroup> {
            val images = allMedia.filter {
                it.mimeType.startsWith("image/", ignoreCase = true) && it.sizeBytes >= MIN_DUPLICATE_SIZE_BYTES
            }
            if (images.size < 2) return emptyList()

            val groups = mutableListOf<DuplicateGroup>()
            val assignedIds = mutableSetOf<Long>()
            var groupIndex = 0

            // ------------------------------------------------------------------
            // TIER 1 (Primary Heuristic): Identical File Size + Exact Resolution
            // ------------------------------------------------------------------
            val sizedWithResolution = images.filter { it.width > 0 && it.height > 0 }
            val exactSizeResBuckets = sizedWithResolution.groupBy { item ->
                // Normalize orientation so rotated copies (e.g. 4000x3000 vs 3000x4000) still match
                val shortSide = min(item.width, item.height)
                val longSide = max(item.width, item.height)
                Triple(item.sizeBytes, shortSide, longSide)
            }

            for ((key, bucket) in exactSizeResBuckets) {
                if (bucket.size < 2) continue
                val verifiedSubClusters = splitByDHashIfAvailable(bucket, DHASH_EXACT_COLLISION_MAX_DISTANCE)
                for (cluster in verifiedSubClusters) {
                    if (cluster.size < 2) continue
                    val best = selectBestShot(cluster)
                    val marked = markGroupItems(cluster, best.id)
                    marked.forEach { assignedIds.add(it.id) }
                    val hasDHash = cluster.any { it.dHash != null }
                    val formattedSize = formatFileSize(key.first)
                    groups.add(
                        DuplicateGroup(
                            id = "dup_exact_${groupIndex++}",
                            label = labels.exactLabel(key.third, key.second, marked.size),
                            bestItemId = best.id,
                            items = marked,
                            matchReason = if (hasDHash) {
                                labels.exactReasonWithDHash(formattedSize)
                            } else {
                                labels.exactReason(formattedSize)
                            },
                            matchConfidence = if (hasDHash) 99 else 96
                        )
                    )
                }
            }

            // ------------------------------------------------------------------
            // TIER 2 (Primary Resolution + Perceptual dHash / Burst Similarity)
            // Catches re-saved, re-compressed, or burst shots sharing the same resolution.
            // ------------------------------------------------------------------
            val remainingWithResolution = sizedWithResolution.filter { it.id !in assignedIds }
            val resolutionBuckets = remainingWithResolution.groupBy { item ->
                Pair(min(item.width, item.height), max(item.width, item.height))
            }

            for ((resKey, bucket) in resolutionBuckets) {
                if (bucket.size < 2) continue
                val visited = BooleanArray(bucket.size)

                for (i in bucket.indices) {
                    if (visited[i]) continue
                    val seed = bucket[i]
                    val cluster = mutableListOf(seed)
                    visited[i] = true

                    for (j in i + 1 until bucket.size) {
                        if (visited[j]) continue
                        val candidate = bucket[j]
                        if (areVisuallyOrTemporallySimilar(seed, candidate)) {
                            cluster.add(candidate)
                            visited[j] = true
                        }
                    }

                    if (cluster.size >= 2) {
                        val best = selectBestShot(cluster)
                        val marked = markGroupItems(cluster, best.id)
                        marked.forEach { assignedIds.add(it.id) }
                        val usedDHash = cluster.count { it.dHash != null } >= 2
                        groups.add(
                            DuplicateGroup(
                                id = "dup_similar_${groupIndex++}",
                                label = if (usedDHash) {
                                    labels.similarLabel(resKey.second, resKey.first, marked.size)
                                } else {
                                    labels.burstLabel(resKey.second, resKey.first, marked.size)
                                },
                                bestItemId = best.id,
                                items = marked,
                                matchReason = if (usedDHash) labels.similarReason() else labels.burstReason(),
                                matchConfidence = if (usedDHash) 93 else 86
                            )
                        )
                    }
                }
            }

            // ------------------------------------------------------------------
            // TIER 2B (Cross-Resolution Perceptual dHash Matching)
            // Catches resized/recompressed copies (e.g., WhatsApp 1600x1200 vs Camera 4000x3000).
            // ------------------------------------------------------------------
            val crossResCandidates = sizedWithResolution
                .filter { it.id !in assignedIds && it.dHash != null && it.dHash != 0L }
            if (crossResCandidates.size >= 2) {
                val crossVisited = BooleanArray(crossResCandidates.size)
                for (i in crossResCandidates.indices) {
                    if (crossVisited[i]) continue
                    val seed = crossResCandidates[i]
                    val seedHash = seed.dHash ?: continue
                    val seedAspect = max(seed.width, seed.height).toFloat() / min(seed.width, seed.height).coerceAtLeast(1)
                    val cluster = mutableListOf(seed)
                    crossVisited[i] = true

                    for (j in i + 1 until crossResCandidates.size) {
                        if (crossVisited[j]) continue
                        val cand = crossResCandidates[j]
                        val candHash = cand.dHash ?: continue
                        val candAspect = max(cand.width, cand.height).toFloat() / min(cand.width, cand.height).coerceAtLeast(1)
                        val aspectDiffRatio = abs(seedAspect - candAspect) / max(seedAspect, candAspect).coerceAtLeast(1f)
                        if (aspectDiffRatio <= 0.15f &&
                            PerceptualHasher.areSimilar(seedHash, candHash, DHASH_NEAR_DUPLICATE_MAX_DISTANCE)
                        ) {
                            cluster.add(cand)
                            crossVisited[j] = true
                        }
                    }

                    if (cluster.size >= 2) {
                        val best = selectBestShot(cluster)
                        val marked = markGroupItems(cluster, best.id)
                        marked.forEach { assignedIds.add(it.id) }
                        groups.add(
                            DuplicateGroup(
                                id = "dup_similar_${groupIndex++}",
                                label = labels.similarLabel(best.width, best.height, marked.size),
                                bestItemId = best.id,
                                items = marked,
                                matchReason = labels.similarReason(),
                                matchConfidence = 91
                            )
                        )
                    }
                }
            }

            // ------------------------------------------------------------------
            // TIER 3 (Fallback): Identical File Size when MediaStore resolution is 0x0
            // ------------------------------------------------------------------
            val unknownResRemaining = images.filter { it.id !in assignedIds && (it.width <= 0 || it.height <= 0) }
            val fallbackSizeBuckets = unknownResRemaining.groupBy { it.sizeBytes }
            for ((sizeBytes, bucket) in fallbackSizeBuckets) {
                if (bucket.size < 2) continue
                val verified = splitByDHashIfAvailable(bucket, DHASH_EXACT_COLLISION_MAX_DISTANCE)
                for (cluster in verified) {
                    if (cluster.size < 2) continue
                    val best = selectBestShot(cluster)
                    val marked = markGroupItems(cluster, best.id)
                    marked.forEach { assignedIds.add(it.id) }
                    val formattedSize = formatFileSize(sizeBytes)
                    groups.add(
                        DuplicateGroup(
                            id = "dup_size_${groupIndex++}",
                            label = labels.sizeLabel(formattedSize, marked.size),
                            bestItemId = best.id,
                            items = marked,
                            matchReason = labels.sizeReason(formattedSize),
                            matchConfidence = 90
                        )
                    )
                }
            }

            return groups.sortedByDescending { it.selectableSizeBytes }
        }

        private fun splitByDHashIfAvailable(bucket: List<MediaItem>, maxDistance: Int): List<List<MediaItem>> {
            val hashed = bucket.filter { it.dHash != null }
            if (hashed.size < 2) return listOf(bucket)

            val clusters = mutableListOf<MutableList<MediaItem>>()
            for (item in bucket) {
                val hash = item.dHash
                if (hash == null) {
                    // Unhashed items remain compatible with first cluster (same exact size + res)
                    if (clusters.isEmpty()) clusters.add(mutableListOf(item)) else clusters.first().add(item)
                    continue
                }
                val match = clusters.firstOrNull { cluster ->
                    val repHash = cluster.firstOrNull { it.dHash != null }?.dHash
                    repHash == null || hammingDistance(repHash, hash) <= maxDistance
                }
                if (match != null) {
                    match.add(item)
                } else {
                    clusters.add(mutableListOf(item))
                }
            }
            return clusters.filter { it.size >= 2 }
        }

        private fun areVisuallyOrTemporallySimilar(a: MediaItem, b: MediaItem): Boolean {
            if (a.dHash != null && b.dHash != null) {
                return hammingDistance(a.dHash, b.dHash) <= DHASH_NEAR_DUPLICATE_MAX_DISTANCE
            }
            // Fallback when bitmaps cannot be decoded: burst timestamp + close file size
            val timeDiff = abs(a.dateModified - b.dateModified)
            val maxSize = max(a.sizeBytes, b.sizeBytes).coerceAtLeast(1L)
            val sizeDeltaRatio = abs(a.sizeBytes - b.sizeBytes).toDouble() / maxSize.toDouble()
            return timeDiff <= BURST_TIME_WINDOW_MS && sizeDeltaRatio <= 0.15
        }

        /**
         * Ranks candidates inside a duplicate group and chooses the sharpest, eyes-open,
         * smiling, highest-quality original shot.
         */
        fun selectBestShot(items: List<MediaItem>): MediaItem {
            return items.maxWith(
                compareBy<MediaItem>(
                    { !it.isBlurry },
                    { !it.hasBlinkingFace },
                    { it.faceQualityScore },
                    { it.sharpnessScore },
                    { it.width.toLong() * it.height.toLong() },
                    { it.sizeBytes },
                    { !looksLikeCopyFilename(it.title) },
                    { -it.dateModified } // Prefer earlier original capture when quality is tied
                )
            )
        }

        private fun looksLikeCopyFilename(title: String): Boolean {
            val lower = title.lowercase()
            return lower.contains("copy") ||
                lower.contains("(1)") ||
                lower.contains("(2)") ||
                lower.contains("_1.") ||
                lower.contains(" - copy")
        }

        private fun markGroupItems(items: List<MediaItem>, bestId: Long): List<MediaItem> {
            val ordered = items.sortedByDescending { it.id == bestId }
            return ordered.map { item ->
                if (item.id == bestId) {
                    item.copy(isDuplicate = true, isBestShot = true, isSelected = false)
                } else {
                    item.copy(isDuplicate = true, isBestShot = false, isSelected = true)
                }
            }
        }

        fun createLocalizedLabels(context: Context): DuplicateLabels = DuplicateLabels(
            exactLabel = { w, h, count -> context.getString(R.string.dup_label_exact_fmt, w, h, count) },
            exactReasonWithDHash = { size -> context.getString(R.string.dup_reason_exact_dhash_fmt, size) },
            exactReason = { size -> context.getString(R.string.dup_reason_exact_fmt, size) },
            similarLabel = { w, h, count -> context.getString(R.string.dup_label_similar_fmt, w, h, count) },
            burstLabel = { w, h, count -> context.getString(R.string.dup_label_burst_fmt, w, h, count) },
            similarReason = { context.getString(R.string.dup_reason_similar_dhash) },
            burstReason = { context.getString(R.string.dup_reason_burst) },
            sizeLabel = { size, count -> context.getString(R.string.dup_label_size_fmt, size, count) },
            sizeReason = { size -> context.getString(R.string.dup_reason_size_fmt, size) }
        )
    }

    /**
     * Queries MediaStore.Images.Media for local images, fills missing dimensions via
     * BitmapFactory bounds decoding, and computes perceptual dHash + Laplacian variance
     * sharpness scores for candidate duplicate/blur analysis.
     */
    suspend fun scanImagesFromMediaStore(): List<MediaItem> = withContext(Dispatchers.IO) {
        val rawImages = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION
        )

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.SIZE} > 0",
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val orientationCol = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION)

                var count = 0
                while (cursor.moveToNext() && count < MAX_IMAGES_TO_SCAN) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                    val path = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val date = cursor.getLong(dateCol) * 1000L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    var width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    var height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val orientation = if (orientationCol != -1) cursor.getInt(orientationCol) else 0

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    // Fallback to BitmapFactory bounds decoding if MediaStore lacks dimensions
                    if (width <= 0 || height <= 0) {
                        val decodedDims = decodeImageDimensions(uri)
                        if (decodedDims != null) {
                            width = decodedDims.first
                            height = decodedDims.second
                        }
                    }

                    // Normalize rotated EXIF dimensions
                    if ((orientation == 90 || orientation == 270) && width > 0 && height > 0) {
                        val tmp = width
                        width = height
                        height = tmp
                    }

                    val isScreenshot = name.contains("screenshot", ignoreCase = true) ||
                        path.contains("screenshot", ignoreCase = true)
                    val isLarge = size > 8 * 1024 * 1024L

                    rawImages.add(
                        MediaItem(
                            id = id,
                            title = name,
                            path = path,
                            uriString = uri.toString(),
                            sizeBytes = size,
                            dateModified = date,
                            mimeType = mime,
                            width = width,
                            height = height,
                            isScreenshot = isScreenshot,
                            isLarge = isLarge,
                            isBlurry = (width in 1..640 && height in 1..480)
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Compute perceptual dHash + Laplacian variance sharpness for:
        // 1) All images that share a size or resolution bucket (potential duplicates)
        // 2) Recent gallery photos (up to MAX_VISUAL_FINGERPRINT_CANDIDATES) for accurate blur detection
        val candidateIds = identifyVisualAnalysisCandidates(rawImages)
        if (candidateIds.isEmpty()) return@withContext rawImages

        rawImages.map { item ->
            if (item.id in candidateIds && item.uriString.isNotBlank()) {
                val analysis = analyzeImageBitmap(Uri.parse(item.uriString))
                if (analysis != null) {
                    val isBlurry = analysis.sharpnessScore < BLUR_SHARPNESS_THRESHOLD
                    item.copy(
                        dHash = analysis.dHash,
                        sharpnessScore = analysis.sharpnessScore,
                        isBlurry = isBlurry
                    )
                } else {
                    item
                }
            } else {
                item
            }
        }
    }

    /**
     * Enriches already-loaded MediaItems that lack dHash/sharpness (if needed) and runs
     * the deterministic duplicate grouping engine.
     */
    suspend fun detectDuplicateGroups(allMedia: List<MediaItem>): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val images = allMedia.filter { it.mimeType.startsWith("image/", ignoreCase = true) }
        if (images.size < 2) return@withContext emptyList()

        // Ensure candidate images have dHash + sharpness computed if they came from a caller without them
        val candidateIds = identifyVisualAnalysisCandidates(images)
        val enrichedImages = images.map { item ->
            if (item.id in candidateIds && item.dHash == null && item.uriString.isNotBlank()) {
                val analysis = analyzeImageBitmap(Uri.parse(item.uriString))
                if (analysis != null) {
                    item.copy(
                        dHash = analysis.dHash,
                        sharpnessScore = analysis.sharpnessScore,
                        isBlurry = analysis.sharpnessScore < BLUR_SHARPNESS_THRESHOLD
                    )
                } else {
                    item
                }
            } else {
                item
            }
        }

        val localizedLabels = createLocalizedLabels(context)
        val initialGroups = findDuplicateGroups(enrichedImages, localizedLabels)
        if (initialGroups.isEmpty()) return@withContext emptyList()

        // On-Device ML Kit Face Quality Refinement (eyes open + smile + head pose) for Smart Best Shot
        val visionAnalyzer = OnDeviceVisionAnalyzer(context)
        val quotaManager = AiQuotaManager(context)
        var facesAnalyzedImages = 0
        try {
            val refinedGroups = initialGroups.map { group ->
                val scoredItems = group.items.map { item ->
                    if (facesAnalyzedImages < 36 && item.uriString.isNotBlank() && item.faceCount == 0 && item.faceQualityScore == 0f) {
                        val faceResult = visionAnalyzer.analyzeFaceQuality(Uri.parse(item.uriString))
                        if (faceResult != null && faceResult.faceCount > 0) {
                            facesAnalyzedImages++
                            item.copy(
                                faceCount = faceResult.faceCount,
                                faceQualityScore = faceResult.qualityScore,
                                hasBlinkingFace = faceResult.hasBlinkingFace
                            )
                        } else {
                            item
                        }
                    } else {
                        item
                    }
                }
                val best = selectBestShot(scoredItems)
                val marked = markGroupItems(scoredItems, best.id)
                group.copy(bestItemId = best.id, items = marked)
            }
            if (facesAnalyzedImages > 0) {
                quotaManager.recordValueFacesScored(facesAnalyzedImages)
            }
            refinedGroups
        } catch (_: Exception) {
            initialGroups
        } finally {
            visionAnalyzer.close()
        }
    }

    /**
     * Classifies screenshots on-device using 100% free offline ML Kit OCR Text Recognition
     * into OTP/Codes (safe to delete), Chats/Memes (bulk cleanable), Receipts/Docs (protected), or General.
     */
    suspend fun classifyScreenshotsOnDevice(
        allMedia: List<MediaItem>,
        maxToAnalyze: Int = 40
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val screenshots = allMedia.filter { it.isScreenshot }
        if (screenshots.isEmpty()) return@withContext allMedia

        val visionAnalyzer = OnDeviceVisionAnalyzer(context)
        val quotaManager = AiQuotaManager(context)
        var analyzedCount = 0
        val classifiedById = HashMap<Long, ScreenshotCategory>()

        try {
            for (shot in screenshots.take(maxToAnalyze)) {
                if (shot.screenshotCategory != ScreenshotCategory.NONE) {
                    classifiedById[shot.id] = shot.screenshotCategory
                    continue
                }
                val category = if (shot.uriString.isNotBlank()) {
                    visionAnalyzer.classifyScreenshot(Uri.parse(shot.uriString), shot.title)
                } else {
                    OnDeviceVisionAnalyzer.classifyScreenshotText("", shot.title)
                }
                classifiedById[shot.id] = category
                analyzedCount++
            }
            if (analyzedCount > 0) {
                quotaManager.recordValueScreenshotsClassified(analyzedCount)
            }
        } catch (_: Exception) {
            // Leave unclassified items safely as GENERAL/NONE
        } finally {
            visionAnalyzer.close()
        }

        allMedia.map { item ->
            val cat = classifiedById[item.id]
            if (cat != null) item.copy(screenshotCategory = cat) else item
        }
    }

    private fun identifyVisualAnalysisCandidates(images: List<MediaItem>): Set<Long> {
        val candidates = LinkedHashSet<Long>()
        // Prioritize images sharing file size
        images.groupBy { it.sizeBytes }
            .values
            .filter { it.size >= 2 }
            .forEach { bucket -> bucket.forEach { candidates.add(it.id) } }

        // Next prioritize images sharing resolution (for dHash visual matching)
        images.filter { it.width > 0 && it.height > 0 }
            .groupBy { Pair(min(it.width, it.height), max(it.width, it.height)) }
            .values
            .filter { it.size >= 2 }
            .forEach { bucket ->
                for (item in bucket) {
                    if (candidates.size >= MAX_VISUAL_FINGERPRINT_CANDIDATES) break
                    candidates.add(item.id)
                }
            }

        // Finally include the most recent photos for accurate blur detection in Swipe Clean
        for (item in images.take(60)) {
            if (candidates.size >= MAX_VISUAL_FINGERPRINT_CANDIDATES) break
            candidates.add(item.id)
        }
        return candidates
    }

    private fun decodeImageDimensions(uri: Uri): Pair<Int, Int>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    Pair(opts.outWidth, opts.outHeight)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class VisualAnalysis(
        val dHash: Long,
        val sharpnessScore: Float
    )

    private fun analyzeImageBitmap(uri: Uri): VisualAnalysis? {
        return try {
            // 1. Probe bounds
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 2. Decode downscaled thumbnail (~128px longest side) for fast O(1) memory analysis
            val longest = max(bounds.outWidth, bounds.outHeight)
            var inSampleSize = 1
            while (longest / (inSampleSize * 2) >= 128) {
                inSampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            try {
                // Compute Laplacian sharpness on downscaled bitmap (<=128px)
                val laplacianW = min(bitmap.width, 128).coerceAtLeast(3)
                val laplacianH = min(bitmap.height, 128).coerceAtLeast(3)
                val sharpBitmap = if (bitmap.width > 128 || bitmap.height > 128) {
                    Bitmap.createScaledBitmap(bitmap, laplacianW, laplacianH, true)
                } else {
                    bitmap
                }
                val sharpnessScore = try {
                    val pixels = IntArray(sharpBitmap.width * sharpBitmap.height)
                    sharpBitmap.getPixels(pixels, 0, sharpBitmap.width, 0, 0, sharpBitmap.width, sharpBitmap.height)
                    val gray = PerceptualHasher.argbToGrayscale(pixels)
                    computeLaplacianSharpnessScore(gray, sharpBitmap.width, sharpBitmap.height)
                } finally {
                    if (sharpBitmap !== bitmap) sharpBitmap.recycle()
                }

                // Compute 8x8 grayscale dHash via PerceptualHasher
                val dHash = PerceptualHasher.computeDHash(bitmap)

                VisualAnalysis(dHash = dHash, sharpnessScore = sharpnessScore)
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            null
        }
    }
}
