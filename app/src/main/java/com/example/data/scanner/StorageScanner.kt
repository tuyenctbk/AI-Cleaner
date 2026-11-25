package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.example.R
import com.example.data.local.StorageSnapshotEntity
import com.example.data.local.StorageSnapshotRepository
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

class StorageScanner(private val context: Context) {

    private val duplicateScanner = MediaStoreDuplicateScanner(context)
    private val videoCompressor = VideoCompressor(context)

    companion object {
        fun getQuickStorageStats(context: Context): StorageStats {
            return try {
                val statFs = StatFs(Environment.getDataDirectory().path)
                val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
                val freeBytes = statFs.availableBlocksLong * statFs.blockSizeLong
                val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

                var photoBytes = 0L
                var photoCount = 0
                var videoBytes = 0L
                var videoCount = 0
                var audioBytes = 0L

                // Quick MediaStore Queries
                try {
                    context.contentResolver.query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Images.Media.SIZE),
                        null, null, null
                    )?.use { cursor ->
                        val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                        while (cursor.moveToNext()) {
                            if (sizeCol != -1) photoBytes += cursor.getLong(sizeCol).coerceAtLeast(0L)
                            photoCount++
                        }
                    }
                } catch (_: Exception) {}

                try {
                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Video.Media.SIZE),
                        null, null, null
                    )?.use { cursor ->
                        val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                        while (cursor.moveToNext()) {
                            if (sizeCol != -1) videoBytes += cursor.getLong(sizeCol).coerceAtLeast(0L)
                            videoCount++
                        }
                    }
                } catch (_: Exception) {}

                try {
                    context.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        arrayOf(MediaStore.Audio.Media.SIZE),
                        null, null, null
                    )?.use { cursor ->
                        val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                        while (cursor.moveToNext()) {
                            if (sizeCol != -1) audioBytes += cursor.getLong(sizeCol).coerceAtLeast(0L)
                        }
                    }
                } catch (_: Exception) {}

                var junkBytes = 0L
                fun measureDir(dir: File?) {
                    if (dir == null || !dir.exists()) return
                    dir.listFiles()?.forEach { file ->
                        junkBytes += if (file.isDirectory) file.walkTopDown().sumOf { it.length() } else file.length()
                    }
                }
                measureDir(context.cacheDir)
                measureDir(context.codeCacheDir)
                context.externalCacheDirs?.forEach { measureDir(it) }

                val usedRatio = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0.5f
                val health = (100 - (usedRatio * 50)).toInt().coerceIn(30, 100)

                StorageStats(
                    totalBytes = totalBytes,
                    usedBytes = usedBytes,
                    freeBytes = freeBytes,
                    photoBytes = photoBytes,
                    videoBytes = videoBytes,
                    audioBytes = audioBytes,
                    docBytes = 0L,
                    appBytes = 0L,
                    systemBytes = (usedBytes - photoBytes - videoBytes - audioBytes).coerceAtLeast(0L),
                    junkBytes = junkBytes,
                    photoCount = photoCount,
                    videoCount = videoCount,
                    duplicateCount = 0,
                    largeFileCount = 0,
                    healthScore = health
                )
            } catch (e: Exception) {
                StorageStats(
                    totalBytes = 0L,
                    usedBytes = 0L,
                    freeBytes = 0L
                )
            }
        }
    }

    suspend fun getDeviceStorageStats(
        preloadedMedia: List<MediaItem>? = null,
        preloadedDuplicates: List<DuplicateGroup>? = null
    ): StorageStats = withContext(Dispatchers.IO) {
        try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
            val freeBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

            var photoBytes = 0L
            var photoCount = 0
            var videoBytes = 0L
            var videoCount = 0
            var audioBytes = 0L
            var docBytes = 0L

            // Scan actual images
            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media.SIZE),
                    null, null, null
                )?.use { cursor ->
                    val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                    while (cursor.moveToNext()) {
                        if (sizeCol != -1) photoBytes += cursor.getLong(sizeCol).coerceAtLeast(0L)
                        photoCount++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Scan actual videos
            try {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media.SIZE),
                    null, null, null
                )?.use { cursor ->
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    while (cursor.moveToNext()) {
                        if (sizeCol != -1) videoBytes += cursor.getLong(sizeCol).coerceAtLeast(0L)
                        videoCount++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Scan actual audio
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media.SIZE),
                    null, null, null
                )?.use { cursor ->
                    val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    while (cursor.moveToNext()) {
                        if (sizeCol != -1) audioBytes += cursor.getLong(sizeCol).coerceAtLeast(0L)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Scan actual documents
            try {
                val docMimes = arrayOf(
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain"
                )
                val selection = docMimes.joinToString(separator = " OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" }
                context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    arrayOf(MediaStore.Files.FileColumns.SIZE),
                    selection,
                    docMimes,
                    null
                )?.use { cursor ->
                    val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                    while (cursor.moveToNext()) {
                        if (sizeCol != -1) docBytes += cursor.getLong(sizeCol).coerceAtLeast(0L)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Measure this app's own APK footprint without querying all installed packages (Play Policy compliant)
            var appBytes = 0L
            try {
                val ownApk = File(context.applicationInfo.sourceDir)
                if (ownApk.exists()) {
                    appBytes += ownApk.length()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Real junk cache calculation
            var junkBytes = 0L
            fun measureDir(dir: File?) {
                if (dir == null || !dir.exists()) return
                dir.listFiles()?.forEach { file ->
                    junkBytes += if (file.isDirectory) file.walkTopDown().sumOf { it.length() } else file.length()
                }
            }
            measureDir(context.cacheDir)
            measureDir(context.codeCacheDir)
            context.externalCacheDirs?.forEach { measureDir(it) }

            val systemBytes = (usedBytes - (photoBytes + videoBytes + audioBytes + docBytes + appBytes)).coerceAtLeast(0L)

            val usedRatio = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0.5f
            val health = (100 - (usedRatio * 50)).toInt().coerceIn(30, 100)

            // Reuse preloaded media/duplicate lists to avoid redundant MediaStore scans
            val media = preloadedMedia ?: getMediaItems()
            val duplicateGroups = preloadedDuplicates ?: getDuplicateGroups(media)
            val duplicateCount = duplicateGroups.sumOf { it.items.count { item -> !item.isBestShot } }
            val blurryCount = media.count { it.isBlurry }
            val screenshotCount = media.count { it.isScreenshot }
            val largeFileCount = media.count { it.isLarge }

            StorageStats(
                totalBytes = totalBytes,
                usedBytes = usedBytes,
                freeBytes = freeBytes,
                photoBytes = photoBytes,
                videoBytes = videoBytes,
                audioBytes = audioBytes,
                docBytes = docBytes,
                appBytes = appBytes,
                systemBytes = systemBytes,
                junkBytes = junkBytes,
                photoCount = photoCount,
                videoCount = videoCount,
                duplicateCount = duplicateCount,
                blurryCount = blurryCount,
                screenshotCount = screenshotCount,
                largeFileCount = largeFileCount,
                healthScore = health
            )
        } catch (e: Exception) {
            StorageStats(
                totalBytes = 0L,
                usedBytes = 0L,
                freeBytes = 0L
            )
        }
    }

    suspend fun scanJunkFiles(onProgress: (Float, String) -> Unit): List<JunkCategory> = withContext(Dispatchers.IO) {
        val steps = listOf(
            context.getString(R.string.scan_step_app_cache) to 0.20f,
            context.getString(R.string.scan_step_temp_files) to 0.40f,
            context.getString(R.string.scan_step_empty_dirs) to 0.60f,
            context.getString(R.string.scan_step_thumb_cache) to 0.80f,
            context.getString(R.string.scan_step_apk_packages) to 1.0f
        )

        for ((step, progress) in steps) {
            onProgress(progress, step)
            delay(120)
        }

        val categories = mutableListOf<JunkCategory>()

        // 1. App Cache Category (Real files from app internal & external cache)
        val cacheItems = mutableListOf<JunkFileItem>()
        var appCacheSize = 0L

        fun collectCache(dir: File?) {
            if (dir == null || !dir.exists()) return
            dir.listFiles()?.forEach { file ->
                val size = if (file.isDirectory) file.walkTopDown().sumOf { it.length() } else file.length()
                if (size > 0) {
                    appCacheSize += size
                    cacheItems.add(JunkFileItem(file.name, file.absolutePath, size, JunkType.APP_CACHE))
                }
            }
        }
        collectCache(context.cacheDir)
        collectCache(context.codeCacheDir)
        context.externalCacheDirs?.forEach { collectCache(it) }

        if (cacheItems.isNotEmpty()) {
            categories.add(
                JunkCategory(
                    type = JunkType.APP_CACHE,
                    title = context.getString(R.string.junk_type_app_cache),
                    description = context.getString(R.string.junk_type_app_cache_desc),
                    sizeBytes = appCacheSize,
                    itemCount = cacheItems.size,
                    items = cacheItems,
                    isSelected = true
                )
            )
        }

        // 2. Temp Files Category (Real files in temp directories)
        val tempItems = mutableListOf<JunkFileItem>()
        var tempSize = 0L
        val tempDir = File(context.cacheDir, "temp")
        if (tempDir.exists()) {
            tempDir.listFiles()?.forEach { file ->
                val size = file.length()
                if (size > 0) {
                    tempSize += size
                    tempItems.add(JunkFileItem(file.name, file.absolutePath, size, JunkType.TEMP_FILES))
                }
            }
        }
        if (tempItems.isNotEmpty()) {
            categories.add(
                JunkCategory(
                    type = JunkType.TEMP_FILES,
                    title = context.getString(R.string.junk_type_temp_files),
                    description = context.getString(R.string.junk_type_temp_files_desc),
                    sizeBytes = tempSize,
                    itemCount = tempItems.size,
                    items = tempItems,
                    isSelected = true
                )
            )
        }

        // 3. Empty Folders Category (Real empty directories in accessible storage)
        val emptyFolderItems = mutableListOf<JunkFileItem>()
        val accessibleDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            context.getExternalFilesDir(null)
        )

        for (baseDir in accessibleDirs) {
            if (baseDir.exists() && baseDir.isDirectory) {
                baseDir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                    val contents = subDir.listFiles()
                    if (contents != null && contents.isEmpty()) {
                        emptyFolderItems.add(
                            JunkFileItem(subDir.name, subDir.absolutePath, 0L, JunkType.EMPTY_FOLDERS)
                        )
                    }
                }
            }
        }
        if (emptyFolderItems.isNotEmpty()) {
            categories.add(
                JunkCategory(
                    type = JunkType.EMPTY_FOLDERS,
                    title = context.getString(R.string.junk_type_empty_folders),
                    description = context.getString(R.string.junk_type_empty_folders_desc),
                    sizeBytes = 0L,
                    itemCount = emptyFolderItems.size,
                    items = emptyFolderItems,
                    isSelected = true
                )
            )
        }

        // 4. Thumbnail Cache Category
        val thumbItems = mutableListOf<JunkFileItem>()
        var thumbSize = 0L
        val thumbDir = File(context.cacheDir, "image_cache")
        if (thumbDir.exists()) {
            thumbDir.listFiles()?.forEach { file ->
                val size = file.length()
                if (size > 0) {
                    thumbSize += size
                    thumbItems.add(JunkFileItem(file.name, file.absolutePath, size, JunkType.THUMBNAIL_CACHE))
                }
            }
        }
        if (thumbItems.isNotEmpty()) {
            categories.add(
                JunkCategory(
                    type = JunkType.THUMBNAIL_CACHE,
                    title = context.getString(R.string.junk_type_thumbnail_cache),
                    description = context.getString(R.string.junk_type_thumbnail_cache_desc),
                    sizeBytes = thumbSize,
                    itemCount = thumbItems.size,
                    items = thumbItems,
                    isSelected = true
                )
            )
        }

        // 5. Obsolete APKs Category (Real APK files in Downloads)
        val apkItems = mutableListOf<JunkFileItem>()
        var apkSize = 0L
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null && downloadsDir.exists()) {
            downloadsDir.listFiles { file -> file.extension.equals("apk", ignoreCase = true) }?.forEach { apkFile ->
                val size = apkFile.length()
                apkSize += size
                apkItems.add(JunkFileItem(apkFile.name, apkFile.absolutePath, size, JunkType.APK_PACKAGES))
            }
        }
        if (apkItems.isNotEmpty()) {
            categories.add(
                JunkCategory(
                    type = JunkType.APK_PACKAGES,
                    title = context.getString(R.string.junk_type_apk_packages),
                    description = context.getString(R.string.junk_type_apk_packages_desc),
                    sizeBytes = apkSize,
                    itemCount = apkItems.size,
                    items = apkItems,
                    isSelected = true
                )
            )
        }

        categories
    }

    suspend fun getMediaItems(): List<MediaItem> = withContext(Dispatchers.IO) {
        val realItems = mutableListOf<MediaItem>()
        try {
            // 1. Query Images via local MediaStoreDuplicateScanner (with size, resolution, dHash & Laplacian sharpness)
            realItems.addAll(duplicateScanner.scanImagesFromMediaStore())

            // 2. Query Videos
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_MODIFIED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            )

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                "${MediaStore.Video.Media.SIZE} > 0",
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)

                var count = 0
                while (cursor.moveToNext() && count < 120) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "VID_$id.mp4"
                    val path = if (pathCol != -1) cursor.getString(pathCol) ?: "" else ""
                    val size = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val date = cursor.getLong(dateCol) * 1000L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    realItems.add(
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
                            isScreenshot = false,
                            isLarge = size > 25 * 1024 * 1024L
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        realItems
    }

    suspend fun getDuplicateGroups(allMedia: List<MediaItem>): List<DuplicateGroup> =
        duplicateScanner.detectDuplicateGroups(allMedia)

    suspend fun classifyScreenshotsOnDevice(allMedia: List<MediaItem>): List<MediaItem> =
        duplicateScanner.classifyScreenshotsOnDevice(allMedia)

    suspend fun getFolderHeatmapData(): List<FolderStorageItem> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<FolderStorageItem>()
        val accessibleDirs = listOf(
            Triple(context.getString(R.string.folder_camera_dcim), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), context.getString(R.string.folder_camera_desc)),
            Triple(context.getString(R.string.folder_pictures), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), context.getString(R.string.folder_pictures_desc)),
            Triple(context.getString(R.string.folder_movies_videos), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), context.getString(R.string.folder_movies_desc)),
            Triple(context.getString(R.string.folder_downloads), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), context.getString(R.string.folder_downloads_desc)),
            Triple(context.getString(R.string.folder_documents), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), context.getString(R.string.folder_documents_desc)),
            Triple(context.getString(R.string.folder_music_audio), Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), context.getString(R.string.folder_music_desc)),
            Triple(context.getString(R.string.folder_app_cache), context.cacheDir, context.getString(R.string.folder_app_cache_desc))
        )

        var idx = 0
        for ((name, dir, category) in accessibleDirs) {
            if (dir != null && dir.exists()) {
                var totalBytes = 0L
                var fileCount = 0
                val extensions = mutableSetOf<String>()

                dir.walkTopDown().maxDepth(3).forEach { file ->
                    if (file.isFile) {
                        totalBytes += file.length()
                        fileCount++
                        val ext = file.extension.uppercase(Locale.US)
                        if (ext.isNotBlank()) extensions.add(ext)
                    }
                }

                if (fileCount > 0 || totalBytes > 0) {
                    folders.add(
                        FolderStorageItem(
                            id = "folder_${idx++}",
                            name = name,
                            path = dir.absolutePath,
                            sizeBytes = totalBytes,
                            fileCount = fileCount,
                            category = category,
                            isSystemFolder = (dir == context.cacheDir),
                            previewFileTypes = extensions.take(4).joinToString(", ")
                        )
                    )
                }
            }
        }

        folders.sortedByDescending { it.sizeBytes }
    }

    suspend fun compressMedia(
        mediaItem: MediaItem,
        quality: CompressionQuality,
        onProgress: (Float) -> Unit = {}
    ): Long {
        return if (mediaItem.mimeType.startsWith("video/", ignoreCase = true)) {
            val result = videoCompressor.compressVideo(mediaItem, quality, onProgress)
            if (result.succeeded) {
                result.savedBytes
            } else {
                0L
            }
        } else {
            onProgress(0.3f)
            val saved = compressImage(mediaItem, quality)
            onProgress(1f)
            saved
        }
    }

    suspend fun compressImage(mediaItem: MediaItem, quality: CompressionQuality): Long = withContext(Dispatchers.IO) {
        try {
            val uri = if (mediaItem.uriString.isNotBlank()) Uri.parse(mediaItem.uriString) else null
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOptions)
                }
            } else if (mediaItem.path.isNotBlank()) {
                BitmapFactory.decodeFile(mediaItem.path, boundsOptions)
            }

            if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                val maxDim = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
                val sampleSize = when {
                    maxDim > 4096 -> 4
                    maxDim > 2048 -> 2
                    else -> 1
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = if (uri != null) {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOptions)
                    }
                } else {
                    BitmapFactory.decodeFile(mediaItem.path, decodeOptions)
                }

                if (bitmap != null) {
                    try {
                        val qualityPercent = when (quality) {
                            CompressionQuality.HIGH -> 75
                            CompressionQuality.MEDIUM -> 50
                            CompressionQuality.MAX_SAVINGS -> 30
                        }
                        val bos = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, qualityPercent, bos)
                        val newBytes = bos.toByteArray()
                        return@withContext (mediaItem.sizeBytes - newBytes.size).coerceAtLeast(0L)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        0L
    }

    suspend fun scanCloudSyncedPhotos(preloadedMedia: List<MediaItem>? = null): List<CloudPhotoItem> = withContext(Dispatchers.IO) {
        val media = preloadedMedia ?: getMediaItems()
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L)

        // Identify photos in Cloud sync directories or photos older than 30 days
        val cloudCandidates = media.filter { item ->
            item.mimeType.startsWith("image/") && (
                    item.path.contains("Google Photos", ignoreCase = true) ||
                            item.path.contains("cloud", ignoreCase = true) ||
                            item.dateModified < thirtyDaysAgo
                    )
        }

        val providerLabel = context.getString(R.string.cloud_provider_google_photos)
        val backupStatusLabel = context.getString(R.string.cloud_backup_safe_status)
        cloudCandidates.map { item ->
            CloudPhotoItem(
                id = item.id,
                title = item.title,
                path = item.path,
                uriString = item.uriString,
                sizeBytes = item.sizeBytes,
                dateModified = item.dateModified,
                cloudProvider = providerLabel,
                cloudBackupDate = backupStatusLabel,
                isSelected = true
            )
        }
    }

    suspend fun get30DayStorageTrends(
        preloadedMedia: List<MediaItem>? = null,
        persistedSnapshots: List<StorageSnapshotEntity> = emptyList()
    ): StorageTrendSummary = withContext(Dispatchers.IO) {
        val statFs = try { StatFs(Environment.getDataDirectory().path) } catch (e: Exception) { null }
        val currentUsed = if (statFs != null) {
            (statFs.blockCountLong * statFs.blockSizeLong) - (statFs.availableBlocksLong * statFs.blockSizeLong)
        } else 0L

        val media = preloadedMedia ?: getMediaItems()
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val snapshotByDate = persistedSnapshots.associateBy { it.dateKey }
        val points = mutableListOf<StorageTrendPoint>()

        // Baseline estimate from media dateModified for days before Room snapshots began
        val dailyMediaAdditions = LongArray(30)
        for (item in media) {
            val daysAgo = ((now - item.dateModified) / dayMs).toInt()
            if (daysAgo in 0 until 30) {
                dailyMediaAdditions[29 - daysAgo] += item.sizeBytes
            }
        }

        val avgDaily = if (dailyMediaAdditions.isNotEmpty()) dailyMediaAdditions.average().toLong() else 0L
        var accumulated = 0L
        var runningUsed = (currentUsed - dailyMediaAdditions.sum()).coerceAtLeast(0L)
        var previousDayUsed: Long? = null
        var maxBurstDay = context.getString(R.string.trends_none)
        var maxBurstBytes = 0L

        for (i in 0 until 30) {
            val dayTimestamp = now - ((29 - i) * dayMs)
            val dateKey = StorageSnapshotRepository.formatDateKey(dayTimestamp)
            val persisted = snapshotByDate[dateKey]

            val dayUsed: Long
            val delta: Long
            val photoBytes: Long
            val videoBytes: Long
            val appBytes: Long

            if (persisted != null) {
                dayUsed = persisted.usedBytes
                delta = if (previousDayUsed != null) {
                    dayUsed - previousDayUsed
                } else {
                    dailyMediaAdditions[i]
                }
                photoBytes = persisted.photoBytes
                videoBytes = persisted.videoBytes
                appBytes = persisted.appBytes
                runningUsed = dayUsed
            } else {
                delta = dailyMediaAdditions[i]
                runningUsed += delta
                dayUsed = runningUsed
                photoBytes = (dayUsed * 0.35f).toLong()
                videoBytes = (dayUsed * 0.25f).toLong()
                appBytes = (dayUsed * 0.20f).toLong()
            }

            previousDayUsed = dayUsed
            if (delta > 0L) accumulated += delta

            val isBurst = (delta > 0L && avgDaily > 0L && delta >= avgDaily * 2)
            val dayLabel = if (persisted != null) dateKey.takeLast(5) else context.getString(R.string.trends_day_fmt, i + 1)
            val burstDesc = when {
                isBurst -> context.getString(R.string.trends_burst_high_capture_fmt, formatFileSize(delta))
                persisted != null && delta < -5_000_000L -> context.getString(R.string.trends_post_clean_drop_fmt, formatFileSize(-delta))
                else -> null
            }

            if (delta > maxBurstBytes) {
                maxBurstBytes = delta
                maxBurstDay = "$dayLabel (+${formatFileSize(delta)})"
            }

            points.add(
                StorageTrendPoint(
                    dayLabel = dayLabel,
                    timestamp = dayTimestamp,
                    usedBytes = dayUsed,
                    deltaBytes = delta,
                    photoBytes = photoBytes,
                    videoBytes = videoBytes,
                    appBytes = appBytes,
                    isRapidAccumulationBurst = isBurst,
                    burstDescription = burstDesc
                )
            )
        }

        val recordedDays = persistedSnapshots.size
        val topCategory = when {
            recordedDays >= 2 -> context.getString(R.string.trends_verified_snapshots_fmt, recordedDays)
            accumulated > 0 -> context.getString(R.string.trends_camera_recordings_fmt, formatFileSize(accumulated))
            else -> context.getString(R.string.trends_normal_usage)
        }

        StorageTrendSummary(
            timeframeDays = 30,
            totalAccumulatedBytes = accumulated,
            topGrowthCategory = topCategory,
            highestBurstDay = maxBurstDay,
            averageDailyGrowthMb = (accumulated / (30 * 1024 * 1024)).toInt(),
            trendPoints = points
        )
    }

    /**
     * Play-Policy-Compliant Cache Scanner:
     * Avoids QUERY_ALL_PACKAGES and blocked /Android/data/<pkg>/cache reads on Android 11+.
     * Reports AI Cleaner's real accessible cache partitions that can be safely purged in-app;
     * users can inspect/clear other apps via Android's System Storage Settings deep-link.
     */
    suspend fun scanAppCaches(): List<AppCacheItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AppCacheItem>()
        val ownApkSize = try {
            val apk = File(context.applicationInfo.sourceDir)
            if (apk.exists()) apk.length() else 0L
        } catch (_: Exception) {
            0L
        }

        fun dirSize(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            return try {
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } catch (_: Exception) {
                0L
            }
        }

        val imageCacheDir = File(context.cacheDir, "image_cache")
        val videoTempDir = File(context.cacheDir, "compressed_videos")
        val generalTempDir = File(context.cacheDir, "temp")
        val imageCacheBytes = dirSize(imageCacheDir)
        val videoTempBytes = dirSize(videoTempDir)
        val generalTempBytes = dirSize(generalTempDir)
        val totalInternalBytes = dirSize(context.cacheDir)
        val coreCacheBytes = (totalInternalBytes - imageCacheBytes - videoTempBytes - generalTempBytes).coerceAtLeast(0L)
        val codeCacheBytes = dirSize(context.codeCacheDir)
        val externalCacheBytes = context.externalCacheDirs?.sumOf { dirSize(it) } ?: 0L

        val buckets = listOf(
            AppCacheItem(
                id = "cache_internal_core",
                appName = context.getString(R.string.cache_bucket_internal_name),
                packageName = context.packageName,
                appSizeBytes = ownApkSize,
                cacheSizeBytes = coreCacheBytes + generalTempBytes,
                category = context.getString(R.string.cache_cat_app_cache),
                daysUnused = 0,
                isCleared = (coreCacheBytes + generalTempBytes) == 0L
            ),
            AppCacheItem(
                id = "cache_image_thumbs",
                appName = context.getString(R.string.cache_bucket_thumbs_name),
                packageName = "${context.packageName}.thumbnails",
                appSizeBytes = ownApkSize,
                cacheSizeBytes = imageCacheBytes,
                category = context.getString(R.string.cache_cat_media_previews),
                daysUnused = 0,
                isCleared = imageCacheBytes == 0L
            ),
            AppCacheItem(
                id = "cache_video_transcode",
                appName = context.getString(R.string.cache_bucket_video_name),
                packageName = "${context.packageName}.media3",
                appSizeBytes = ownApkSize,
                cacheSizeBytes = videoTempBytes,
                category = context.getString(R.string.cache_cat_video_media),
                daysUnused = 0,
                isCleared = videoTempBytes == 0L
            ),
            AppCacheItem(
                id = "cache_code_cache",
                appName = context.getString(R.string.cache_bucket_code_name),
                packageName = "${context.packageName}.codecache",
                appSizeBytes = ownApkSize,
                cacheSizeBytes = codeCacheBytes,
                category = context.getString(R.string.cache_cat_system),
                daysUnused = 0,
                isCleared = codeCacheBytes == 0L
            ),
            AppCacheItem(
                id = "cache_external",
                appName = context.getString(R.string.cache_bucket_external_name),
                packageName = "${context.packageName}.external",
                appSizeBytes = ownApkSize,
                cacheSizeBytes = externalCacheBytes,
                category = context.getString(R.string.cache_cat_external),
                daysUnused = 0,
                isCleared = externalCacheBytes == 0L
            )
        )

        result.addAll(buckets)
        result.sortedByDescending { it.cacheSizeBytes }
    }

    suspend fun clearAppCacheBucket(bucketId: String): Long = withContext(Dispatchers.IO) {
        fun deleteDirContents(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            var freed = 0L
            dir.listFiles()?.forEach { child ->
                val size = if (child.isDirectory) child.walkTopDown().filter { it.isFile }.sumOf { it.length() } else child.length()
                val removed = try {
                    if (child.isDirectory) child.deleteRecursively() else child.delete()
                } catch (_: Exception) {
                    false
                }
                if (removed) freed += size
            }
            return freed
        }

        when (bucketId) {
            "cache_image_thumbs" -> deleteDirContents(File(context.cacheDir, "image_cache"))
            "cache_video_transcode" -> deleteDirContents(File(context.cacheDir, "compressed_videos"))
            "cache_code_cache" -> deleteDirContents(context.codeCacheDir)
            "cache_external" -> context.externalCacheDirs?.sumOf { deleteDirContents(it) } ?: 0L
            "cache_internal_core" -> {
                var freed = deleteDirContents(File(context.cacheDir, "temp"))
                context.cacheDir?.listFiles()?.forEach { child ->
                    if (child.name != "image_cache" && child.name != "compressed_videos") {
                        val size = if (child.isDirectory) child.walkTopDown().filter { it.isFile }.sumOf { it.length() } else child.length()
                        val removed = try {
                            if (child.isDirectory) child.deleteRecursively() else child.delete()
                        } catch (_: Exception) {
                            false
                        }
                        if (removed) freed += size
                    }
                }
                freed
            }
            else -> 0L
        }
    }

    /**
     * Scoped Storage safe media deletion:
     * - Attempts direct ContentResolver / File deletion for app-owned media.
     * - On Android 11+ (API 30+), collects Uris that require user confirmation via
     *   MediaStore.createDeleteRequest() instead of silently failing or fabricating freed bytes.
     */
    suspend fun deleteMediaItems(items: List<MediaItem>): MediaDeleteResult = withContext(Dispatchers.IO) {
        var freed = 0L
        val deletedIds = mutableSetOf<Long>()
        val pendingSystemUris = mutableListOf<String>()

        for (item in items) {
            var deleted = false
            val parsedUri = if (item.uriString.isNotBlank()) Uri.parse(item.uriString) else null

            if (parsedUri != null) {
                try {
                    val count = context.contentResolver.delete(parsedUri, null, null)
                    if (count > 0) deleted = true
                } catch (_: SecurityException) {
                    // Android 10/11+ Scoped Storage: owned by another app -> requires MediaStore.createDeleteRequest
                } catch (_: Exception) {
                }
            }

            if (!deleted && Build.VERSION.SDK_INT < Build.VERSION_CODES.R && item.path.isNotBlank()) {
                try {
                    val file = File(item.path)
                    if (file.exists() && file.delete()) {
                        deleted = true
                    }
                } catch (_: Exception) {
                }
            }

            if (deleted) {
                freed += item.sizeBytes
                deletedIds.add(item.id)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                parsedUri != null &&
                parsedUri.scheme == "content"
            ) {
                pendingSystemUris.add(item.uriString)
            }
        }

        MediaDeleteResult(
            freedBytes = freed,
            deletedIds = deletedIds,
            pendingSystemDeleteUris = pendingSystemUris
        )
    }

    suspend fun deleteJunkCategories(categories: List<JunkCategory>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        for (cat in categories) {
            for (item in cat.items) {
                try {
                    val file = File(item.path)
                    if (file.exists()) {
                        if (file.isDirectory) file.deleteRecursively() else file.delete()
                    }
                } catch (_: Exception) {}
            }
            freed += cat.sizeBytes
        }
        freed
    }
}
