package com.example.worker

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.CleanerDatabase
import com.example.data.local.FlaggedDuplicateEntity
import com.example.data.local.FlaggedDuplicateRepository
import com.example.data.model.DuplicateGroup
import com.example.data.model.MediaItem
import com.example.data.scanner.MediaStoreDuplicateScanner
import com.example.data.scanner.PerceptualHasher
import com.example.notification.StorageNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Background WorkManager worker that scans the user's MediaStore.Images gallery,
 * computes 8x8 grayscale perceptual dHashes via [PerceptualHasher], groups duplicate
 * and visually similar photos by Hamming distance, persists flagged items to Room
 * for user review, and posts a review notification when new matches are found.
 */
class DuplicateScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("ai_cleaner_prefs", Context.MODE_PRIVATE)
            val triggerSource = inputData.getString(KEY_TRIGGER_SOURCE) ?: SOURCE_PERIODIC
            val isPaused = prefs.getBoolean("KEY_IS_CLEANING_PAUSED", false)
            if (isPaused && triggerSource != SOURCE_MANUAL) {
                return@withContext Result.success()
            }

            if (!hasMediaReadPermission(applicationContext)) {
                return@withContext Result.success()
            }

            val vaultIds = (prefs.getString("KEY_VAULT_IDS", "") ?: "")
                .split(",")
                .mapNotNull { it.trim().toLongOrNull() }
                .toSet()

            val galleryImages = queryGalleryImages(applicationContext, vaultIds)
            if (galleryImages.size < 2) {
                val repo = FlaggedDuplicateRepository(
                    CleanerDatabase.getInstance(applicationContext).flaggedDuplicateDao(),
                    applicationContext
                )
                repo.replaceFlaggedResults(emptyList())
                return@withContext Result.success()
            }

            val candidateIds = selectHashCandidateIds(galleryImages, MAX_HASH_CANDIDATES)
            val hashedImages = galleryImages.map { item ->
                if (item.id in candidateIds && item.uriString.isNotBlank()) {
                    val computedHash = PerceptualHasher.computeDHash(
                        applicationContext,
                        Uri.parse(item.uriString)
                    )
                    if (computedHash != null) item.copy(dHash = computedHash) else item
                } else {
                    item
                }
            }

            val localizedLabels = MediaStoreDuplicateScanner.createLocalizedLabels(applicationContext)
            val duplicateGroups = MediaStoreDuplicateScanner.findDuplicateGroups(
                allMedia = hashedImages,
                labels = localizedLabels
            )

            val nowMillis = System.currentTimeMillis()
            val flaggedEntities = buildFlaggedEntities(duplicateGroups, nowMillis)
            val repo = FlaggedDuplicateRepository(
                CleanerDatabase.getInstance(applicationContext).flaggedDuplicateDao(),
                applicationContext
            )
            repo.replaceFlaggedResults(flaggedEntities, nowMillis)

            val nonBestFlagged = flaggedEntities.filter { !it.isBestShot }
            if (nonBestFlagged.isNotEmpty()) {
                val reclaimableBytes = nonBestFlagged.sumOf { it.sizeBytes }
                StorageNotificationManager.sendDuplicateReviewNotification(
                    context = applicationContext,
                    flaggedCount = nonBestFlagged.size,
                    groupCount = duplicateGroups.size,
                    reclaimableBytes = reclaimableBytes
                )
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_TRIGGER_SOURCE = "trigger_source"
        const val SOURCE_PERIODIC = "periodic"
        const val SOURCE_MANUAL = "manual"
        private const val MAX_GALLERY_IMAGES = 500
        private const val MAX_HASH_CANDIDATES = 240

        private fun hasMediaReadPermission(context: Context): Boolean {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                        PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                        PackageManager.PERMISSION_GRANTED
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                        PackageManager.PERMISSION_GRANTED
                }
                else -> {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED
                }
            }
        }

        private fun queryGalleryImages(context: Context, vaultIds: Set<Long>): List<MediaItem> {
            val images = mutableListOf<MediaItem>()
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

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.SIZE} >= ?",
                arrayOf(MediaStoreDuplicateScanner.MIN_DUPLICATE_SIZE_BYTES.toString()),
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
                while (cursor.moveToNext() && count < MAX_GALLERY_IMAGES) {
                    val id = cursor.getLong(idCol)
                    if (id in vaultIds) continue
                    val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                    val path = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val date = cursor.getLong(dateCol) * 1000L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    var width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    var height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val orientation = if (orientationCol != -1) cursor.getInt(orientationCol) else 0
                    if ((orientation == 90 || orientation == 270) && width > 0 && height > 0) {
                        val tmp = width
                        width = height
                        height = tmp
                    }

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    images.add(
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
                            isScreenshot = name.contains("screenshot", ignoreCase = true) ||
                                path.contains("screenshot", ignoreCase = true),
                            isLarge = size > 8 * 1024 * 1024L
                        )
                    )
                    count++
                }
            }
            return images
        }

        internal fun selectHashCandidateIds(images: List<MediaItem>, maxCandidates: Int = MAX_HASH_CANDIDATES): Set<Long> {
            val candidates = LinkedHashSet<Long>()
            images.groupBy { it.sizeBytes }.values.filter { it.size >= 2 }.forEach { bucket ->
                bucket.forEach { if (candidates.size < maxCandidates) candidates.add(it.id) }
            }
            images.filter { it.width > 0 && it.height > 0 }
                .groupBy { Pair(min(it.width, it.height), max(it.width, it.height)) }
                .values
                .filter { it.size >= 2 }
                .forEach { bucket ->
                    bucket.forEach { if (candidates.size < maxCandidates) candidates.add(it.id) }
                }
            for (item in images) {
                if (candidates.size >= maxCandidates) break
                candidates.add(item.id)
            }
            return candidates
        }

        /**
         * Maps detected [DuplicateGroup] clusters into persisted [FlaggedDuplicateEntity] rows
         * with exact dHash Hamming distances computed relative to each group's Best Shot.
         */
        fun buildFlaggedEntities(
            groups: List<DuplicateGroup>,
            flaggedAtMillis: Long = System.currentTimeMillis()
        ): List<FlaggedDuplicateEntity> {
            if (groups.isEmpty()) return emptyList()
            val entities = mutableListOf<FlaggedDuplicateEntity>()
            val seenIds = mutableSetOf<Long>()

            for (group in groups) {
                val bestItem = group.items.firstOrNull { it.isBestShot }
                    ?: group.items.firstOrNull { it.id == group.bestItemId }
                    ?: group.items.firstOrNull()
                val bestHash = bestItem?.dHash

                for (item in group.items) {
                    if (!seenIds.add(item.id)) continue
                    val distance = when {
                        item.id == bestItem?.id -> 0
                        bestHash != null && item.dHash != null -> PerceptualHasher.hammingDistance(bestHash, item.dHash)
                        else -> -1
                    }
                    entities.add(
                        FlaggedDuplicateEntity(
                            mediaId = item.id,
                            groupId = group.id,
                            title = item.title,
                            uriString = item.uriString,
                            sizeBytes = item.sizeBytes,
                            width = item.width,
                            height = item.height,
                            dHash = item.dHash,
                            hammingDistance = distance,
                            matchReason = group.matchReason,
                            isBestShot = item.isBestShot || item.id == bestItem?.id,
                            flaggedAtMillis = flaggedAtMillis,
                            isReviewed = false
                        )
                    )
                }
            }
            return entities
        }
    }
}
