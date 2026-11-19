package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ContactEntity::class,
        CallLogEntity::class,
        CallForwardingRuleEntity::class,
        SpamRuleEntity::class,
        CallRecordingEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(CallHistoryTypeConverters::class)
abstract class VoxLineDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun forwardingRuleDao(): ForwardingRuleDao
    abstract fun spamRuleDao(): SpamRuleDao
    abstract fun callRecordingDao(): CallRecordingDao

    companion object {
        @Volatile
        private var INSTANCE: VoxLineDatabase? = null

        fun getInstance(context: Context): VoxLineDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoxLineDatabase::class.java,
                    "voxline_encrypted_vault.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
