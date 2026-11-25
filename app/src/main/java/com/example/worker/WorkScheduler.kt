package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.model.SmartScheduleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Coordinates Android WorkManager periodic and on-demand work requests for:
 * 1) Daily persistent Room storage snapshot + low-storage guard
 * 2) Weekly Smart Scheduler background maintenance scan with battery/charging constraints
 * 3) Background perceptual dHash MediaStore gallery scan for duplicate/similar images
 */
object WorkScheduler {
    private const val DAILY_SNAPSHOT_WORK_NAME = "ai_cleaner_daily_storage_snapshot"
    private const val WEEKLY_SCAN_WORK_NAME = "ai_cleaner_weekly_smart_scan"
    private const val DUPLICATE_SCAN_WORK_NAME = "ai_cleaner_periodic_duplicate_scan"
    private const val IMMEDIATE_DUPLICATE_SCAN_WORK_NAME = "ai_cleaner_immediate_duplicate_scan"

    fun syncPeriodicWork(
        context: Context,
        settings: SmartScheduleSettings,
        isCleaningPaused: Boolean
    ) {
        val workManager = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (_: Exception) {
            return
        }

        if (isCleaningPaused) {
            workManager.cancelUniqueWork(DAILY_SNAPSHOT_WORK_NAME)
            workManager.cancelUniqueWork(WEEKLY_SCAN_WORK_NAME)
            workManager.cancelUniqueWork(DUPLICATE_SCAN_WORK_NAME)
            return
        }

        // 1. Lightweight Daily Snapshot & Low-Storage Check (24h, battery-not-low)
        val dailyConstraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val dailyRequest = PeriodicWorkRequestBuilder<StorageMaintenanceWorker>(
            24L, TimeUnit.HOURS
        )
            .setConstraints(dailyConstraints)
            .setInputData(workDataOf(StorageMaintenanceWorker.KEY_MODE to StorageMaintenanceWorker.MODE_DAILY_SNAPSHOT))
            .build()

        workManager.enqueueUniquePeriodicWork(
            DAILY_SNAPSHOT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyRequest
        )

        // 2. Weekly Smart Maintenance Scan
        if (settings.isEnabled) {
            val weeklyConstraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(settings.requiresCharging)
                .build()

            val initialDelayMs = computeInitialDelayMillis(settings.dayOfWeek, settings.scanTime)
            val weeklyRequest = PeriodicWorkRequestBuilder<StorageMaintenanceWorker>(
                7L, TimeUnit.DAYS
            )
                .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                .setConstraints(weeklyConstraints)
                .setInputData(workDataOf(StorageMaintenanceWorker.KEY_MODE to StorageMaintenanceWorker.MODE_WEEKLY_SCAN))
                .build()

            workManager.enqueueUniquePeriodicWork(
                WEEKLY_SCAN_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                weeklyRequest
            )
        } else {
            workManager.cancelUniqueWork(WEEKLY_SCAN_WORK_NAME)
        }

        // 3. Background Perceptual dHash Duplicate & Similar Photo Scan (24h)
        if (settings.scanDuplicates) {
            val dupConstraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(settings.requiresCharging)
                .build()

            val dupRequest = PeriodicWorkRequestBuilder<DuplicateScanWorker>(
                24L, TimeUnit.HOURS
            )
                .setConstraints(dupConstraints)
                .setInputData(workDataOf(DuplicateScanWorker.KEY_TRIGGER_SOURCE to DuplicateScanWorker.SOURCE_PERIODIC))
                .build()

            workManager.enqueueUniquePeriodicWork(
                DUPLICATE_SCAN_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                dupRequest
            )
        } else {
            workManager.cancelUniqueWork(DUPLICATE_SCAN_WORK_NAME)
        }
    }

    fun enqueueImmediateDuplicateScan(context: Context) {
        val workManager = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (_: Exception) {
            return
        }

        val request = OneTimeWorkRequestBuilder<DuplicateScanWorker>()
            .setInputData(workDataOf(DuplicateScanWorker.KEY_TRIGGER_SOURCE to DuplicateScanWorker.SOURCE_MANUAL))
            .build()

        workManager.enqueueUniqueWork(
            IMMEDIATE_DUPLICATE_SCAN_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun observeDuplicateScanWork(context: Context): Flow<Boolean> {
        val workManager = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (_: Exception) {
            return flowOf(false)
        }

        return workManager.getWorkInfosForUniqueWorkFlow(IMMEDIATE_DUPLICATE_SCAN_WORK_NAME)
            .map { workInfos ->
                workInfos.any { info ->
                    info.state == WorkInfo.State.ENQUEUED ||
                        info.state == WorkInfo.State.RUNNING ||
                        info.state == WorkInfo.State.BLOCKED
                }
            }
    }

    internal fun computeInitialDelayMillis(dayOfWeek: String, scanTime: String, nowMillis: Long = System.currentTimeMillis()): Long {
        val targetDay = when (dayOfWeek.lowercase()) {
            "sunday" -> Calendar.SUNDAY
            "monday" -> Calendar.MONDAY
            "tuesday" -> Calendar.TUESDAY
            "wednesday" -> Calendar.WEDNESDAY
            "thursday" -> Calendar.THURSDAY
            "friday" -> Calendar.FRIDAY
            "saturday" -> Calendar.SATURDAY
            else -> Calendar.SUNDAY
        }
        val (hour24, minute) = parseScanTime(scanTime)

        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_WEEK, targetDay)
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    }

    private fun parseScanTime(scanTime: String): Pair<Int, Int> {
        // Expects formats like "02:00 AM", "11:00 PM"
        val parts = scanTime.trim().split(" ")
        val timeParts = parts.getOrNull(0)?.split(":")
        val rawHour = timeParts?.getOrNull(0)?.toIntOrNull() ?: 2
        val minute = timeParts?.getOrNull(1)?.toIntOrNull() ?: 0
        val isPm = parts.getOrNull(1)?.equals("PM", ignoreCase = true) == true
        val hour24 = when {
            isPm && rawHour < 12 -> rawHour + 12
            !isPm && rawHour == 12 -> 0
            else -> rawHour.coerceIn(0, 23)
        }
        return Pair(hour24, minute.coerceIn(0, 59))
    }
}
