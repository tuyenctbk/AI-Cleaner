package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FlaggedDuplicateDao {
    @Query("SELECT * FROM flagged_duplicates WHERE isReviewed = 0 ORDER BY flaggedAtMillis DESC, groupId ASC, isBestShot DESC")
    fun observeUnreviewed(): Flow<List<FlaggedDuplicateEntity>>

    @Query("SELECT * FROM flagged_duplicates WHERE isReviewed = 0 ORDER BY flaggedAtMillis DESC, groupId ASC, isBestShot DESC")
    suspend fun getUnreviewed(): List<FlaggedDuplicateEntity>

    @Query("DELETE FROM flagged_duplicates")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FlaggedDuplicateEntity>)

    @Transaction
    suspend fun replaceAllFlagged(items: List<FlaggedDuplicateEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }

    @Query("UPDATE flagged_duplicates SET isReviewed = 1 WHERE isReviewed = 0")
    suspend fun markAllReviewed()

    @Query("DELETE FROM flagged_duplicates WHERE mediaId IN (:ids)")
    suspend fun deleteByMediaIds(ids: List<Long>)
}
