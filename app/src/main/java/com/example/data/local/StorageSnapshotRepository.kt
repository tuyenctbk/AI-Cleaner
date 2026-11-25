package com.example.data.local

import com.example.data.model.StorageStats
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StorageSnapshotRepository(private val dao: StorageSnapshotDao) {

    fun observeRecentSnapshots(limit: Int = 30): Flow<List<StorageSnapshotEntity>> =
        dao.observeRecentSnapshots(limit)

    suspend fun getRecentSnapshots(limit: Int = 30): List<StorageSnapshotEntity> =
        dao.getRecentSnapshots(limit)

    suspend fun recordSnapshot(stats: StorageStats, nowMillis: Long = System.currentTimeMillis()) {
        if (stats.totalBytes <= 0L) return
        val dateKey = formatDateKey(nowMillis)
        val entity = StorageSnapshotEntity(
            dateKey = dateKey,
            timestamp = nowMillis,
            totalBytes = stats.totalBytes,
            usedBytes = stats.usedBytes,
            freeBytes = stats.freeBytes,
            photoBytes = stats.photoBytes,
            videoBytes = stats.videoBytes,
            appBytes = stats.appBytes,
            junkBytes = stats.junkBytes
        )
        dao.upsertSnapshot(entity)

        // Retain up to 90 days of daily history
        val ninetyDaysAgo = nowMillis - (90L * 24 * 60 * 60 * 1000L)
        dao.pruneOlderThan(ninetyDaysAgo)
    }

    companion object {
        fun formatDateKey(timestampMillis: Long): String {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return formatter.format(Date(timestampMillis))
        }
    }
}
