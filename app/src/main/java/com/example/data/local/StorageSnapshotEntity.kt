package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Daily persisted device storage snapshot captured via StatFs + MediaStore.
 * One row per calendar day (keyed by yyyy-MM-dd) so Storage Trends can plot
 * genuine day-by-day historical usage and post-clean drops.
 */
@Entity(tableName = "storage_snapshots")
data class StorageSnapshotEntity(
    @PrimaryKey
    val dateKey: String, // Format: yyyy-MM-dd
    val timestamp: Long,
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val photoBytes: Long,
    val videoBytes: Long,
    val appBytes: Long,
    val junkBytes: Long
)
