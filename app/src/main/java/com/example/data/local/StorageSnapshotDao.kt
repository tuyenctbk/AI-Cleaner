package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageSnapshotDao {
    @Query("SELECT * FROM storage_snapshots ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentSnapshots(limit: Int = 30): Flow<List<StorageSnapshotEntity>>

    @Query("SELECT * FROM storage_snapshots ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSnapshots(limit: Int = 30): List<StorageSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: StorageSnapshotEntity)

    @Query("DELETE FROM storage_snapshots WHERE timestamp < :cutoffTimestamp")
    suspend fun pruneOlderThan(cutoffTimestamp: Long)
}
