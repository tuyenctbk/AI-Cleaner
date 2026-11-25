package com.example.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class FlaggedDuplicateSummary(
    val flaggedItemCount: Int = 0,
    val groupCount: Int = 0,
    val reclaimableBytes: Long = 0L,
    val lastScanMillis: Long = 0L
)

class FlaggedDuplicateRepository(
    private val dao: FlaggedDuplicateDao,
    context: Context
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "ai_cleaner_prefs",
        Context.MODE_PRIVATE
    )

    fun observeUnreviewed(): Flow<List<FlaggedDuplicateEntity>> = dao.observeUnreviewed()

    fun observeSummary(): Flow<FlaggedDuplicateSummary> = dao.observeUnreviewed().map { items ->
        val nonBest = items.filter { !it.isBestShot }
        val lastScan = prefs.getLong(KEY_LAST_DUP_SCAN_DATE, 0L)
        FlaggedDuplicateSummary(
            flaggedItemCount = nonBest.size,
            groupCount = items.map { it.groupId }.distinct().size,
            reclaimableBytes = nonBest.sumOf { it.sizeBytes },
            lastScanMillis = lastScan
        )
    }

    suspend fun getUnreviewed(): List<FlaggedDuplicateEntity> = dao.getUnreviewed()

    suspend fun replaceFlaggedResults(
        items: List<FlaggedDuplicateEntity>,
        scanTimestampMillis: Long = System.currentTimeMillis()
    ) {
        dao.replaceAllFlagged(items)
        prefs.edit().putLong(KEY_LAST_DUP_SCAN_DATE, scanTimestampMillis).apply()
    }

    suspend fun markAllReviewed() {
        dao.markAllReviewed()
    }

    suspend fun deleteByMediaIds(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            dao.deleteByMediaIds(ids)
        }
    }

    fun getLastScanTimestamp(): Long = prefs.getLong(KEY_LAST_DUP_SCAN_DATE, 0L)

    companion object {
        const val KEY_LAST_DUP_SCAN_DATE = "KEY_LAST_DUP_SCAN_DATE"
    }
}
