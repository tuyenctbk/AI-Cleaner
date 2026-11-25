package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StorageSnapshotEntity::class, FlaggedDuplicateEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CleanerDatabase : RoomDatabase() {
    abstract fun storageSnapshotDao(): StorageSnapshotDao
    abstract fun flaggedDuplicateDao(): FlaggedDuplicateDao

    companion object {
        @Volatile
        private var INSTANCE: CleanerDatabase? = null

        fun getInstance(context: Context): CleanerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CleanerDatabase::class.java,
                    "ai_cleaner_storage.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
