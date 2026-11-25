package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists duplicate or visually similar MediaStore images flagged in the background by
 * [com.example.worker.DuplicateScanWorker] using [com.example.data.scanner.PerceptualHasher].
 */
@Entity(tableName = "flagged_duplicates")
data class FlaggedDuplicateEntity(
    @PrimaryKey
    val mediaId: Long,
    val groupId: String,
    val title: String,
    val uriString: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dHash: Long?,
    val hammingDistance: Int,
    val matchReason: String,
    val isBestShot: Boolean,
    val flaggedAtMillis: Long,
    val isReviewed: Boolean = false
)
