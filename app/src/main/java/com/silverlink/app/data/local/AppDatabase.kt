package com.silverlink.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.silverlink.app.data.local.dao.CognitiveLogDao
import com.silverlink.app.data.local.dao.EmergencyContactDao
import com.silverlink.app.data.local.dao.HistoryDao
import com.silverlink.app.data.local.dao.MedicationDao
import com.silverlink.app.data.local.dao.MemoryPhotoDao
import com.silverlink.app.data.local.entity.CognitiveLogEntity
import com.silverlink.app.data.local.entity.EmergencyContactEntity
import com.silverlink.app.data.local.entity.Medication
import com.silverlink.app.data.local.entity.MedicationLogEntity
import com.silverlink.app.data.local.entity.MemoryPhotoEntity
import com.silverlink.app.data.local.entity.MoodLogEntity

/**
 * 应用数据库
 */
@Database(
    entities = [
        Medication::class, 
        ConversationEntity::class,
        ChatMessageEntity::class,
        MedicationLogEntity::class,
        MoodLogEntity::class,
        MemoryPhotoEntity::class,
        CognitiveLogEntity::class,
        EmergencyContactEntity::class,
        MemoryRecordEntity::class,
        UserProfileMemoryEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun medicationDao(): MedicationDao
    abstract fun chatDao(): ChatDao
    abstract fun historyDao(): HistoryDao
    abstract fun memoryPhotoDao(): MemoryPhotoDao
    abstract fun cognitiveLogDao(): CognitiveLogDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun memoryDao(): MemoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS memory_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceConversationId INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        keywordsText TEXT NOT NULL,
                        importance REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastAccessAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_profile_memory (
                        `key` TEXT NOT NULL,
                        value TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(`key`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memory_records_sourceConversationId ON memory_records(sourceConversationId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memory_records_createdAt ON memory_records(createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_memory_records_lastAccessAt ON memory_records(lastAccessAt)")
            }
        }
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "silverlink_database"
                )
                    .addMigrations(MIGRATION_6_7)
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
