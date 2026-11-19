package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ContactEntity::class,
        CallLogEntity::class,
        CallForwardingRuleEntity::class,
        SpamRuleEntity::class,
        CallRecordingEntity::class
    ],
    version = 6,
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE call_logs ADD COLUMN quickNote TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): VoxLineDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoxLineDatabase::class.java,
                    "voxline_encrypted_vault.db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
