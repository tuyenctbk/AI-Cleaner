package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.CleanerDatabase
import com.example.data.local.StorageSnapshotRepository
import com.example.data.scanner.StorageScanner
import com.example.notification.StorageNotificationManager
import com.example.widget.StorageAppWidgetProvider

/**
 * WorkManager background worker that executes scheduled storage maintenance:
 * - Captures daily real StatFs + MediaStore snapshots into Room for Storage Trends
 * - Sends low-storage warning alerts (<10% free)
 * - Runs scheduled junk/duplicate/large-file estimation and notifies if reclaimable
 *   space exceeds the user's configured MB threshold.
 */
class StorageMaintenanceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_MODE = "mode"
        const val MODE_DAILY_SNAPSHOT = "daily_snapshot"
        const val MODE_WEEKLY_SCAN = "weekly_scan"
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = applicationContext.getSharedPreferences("ai_cleaner_prefs", Context.MODE_PRIVATE)
            val isPaused = prefs.getBoolean("KEY_IS_CLEANING_PAUSED", false)
            if (isPaused) return Result.success()

            val scanner = StorageScanner(applicationContext)
            val mode = inputData.getString(KEY_MODE) ?: MODE_DAILY_SNAPSHOT

            // 1. Capture real storage telemetry and persist daily Room snapshot
            val stats = if (mode == MODE_WEEKLY_SCAN) {
                scanner.getDeviceStorageStats()
            } else {
                StorageScanner.getQuickStorageStats(applicationContext)
            }

            val repository = StorageSnapshotRepository(
                CleanerDatabase.getInstance(applicationContext).storageSnapshotDao()
            )
            repository.recordSnapshot(stats)

            // 2. Refresh Jetpack Glance home-screen widget if installed
            com.example.widget.StorageGlanceWidget.updateAllWidgets(applicationContext)

            // 3. Low storage alert (< 10% free)
            if (stats.totalBytes > 0L && (stats.freeBytes.toFloat() / stats.totalBytes.toFloat()) < 0.10f) {
                StorageNotificationManager.sendLowStorageNotification(
                    applicationContext,
                    stats.freeBytes,
                    stats.totalBytes
                )
            }

            // 4. Weekly scheduled deep scan threshold check
            if (mode == MODE_WEEKLY_SCAN) {
                val thresholdMb = prefs.getInt("KEY_SCHED_THRESHOLD_MB", 500)
                val scanJunk = prefs.getBoolean("KEY_SCHED_JUNK", true)
                val scanDuplicates = prefs.getBoolean("KEY_SCHED_DUPLICATES", true)
                val scanLargeFiles = prefs.getBoolean("KEY_SCHED_LARGE", true)

                var reclaimableBytes = 0L
                if (scanJunk) {
                    val junk = scanner.scanJunkFiles(simulateProgressDelay = false) { _, _ -> }
                    reclaimableBytes += junk.sumOf { it.sizeBytes }
                }
                if (scanDuplicates || scanLargeFiles) {
                    val media = scanner.getMediaItems()
                    val countedMediaIds = mutableSetOf<Long>()
                    if (scanDuplicates) {
                        val duplicates = scanner.getDuplicateGroups(media)
                        duplicates.forEach { group ->
                            group.items.filter { !it.isBestShot }.forEach { dup ->
                                if (countedMediaIds.add(dup.id)) {
                                    reclaimableBytes += dup.sizeBytes
                                }
                            }
                        }
                    }
                    if (scanLargeFiles) {
                        reclaimableBytes += media
                            .filter { it.isLarge && it.id !in countedMediaIds }
                            .sumOf { it.sizeBytes }
                    }
                }

                prefs.edit().putLong("KEY_SCHED_LAST_DATE", System.currentTimeMillis()).apply()

                val thresholdBytes = thresholdMb.toLong() * 1024L * 1024L
                if (reclaimableBytes >= thresholdBytes && reclaimableBytes > 0L) {
                    StorageNotificationManager.sendScheduledScanNotification(
                        applicationContext,
                        reclaimableBytes
                    )
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
