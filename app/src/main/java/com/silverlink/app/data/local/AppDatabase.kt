package com.silverlink.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.silverlink.app.data.local.dao.CognitiveLogDao
import com.silverlink.app.data.local.dao.HistoryDao
import com.silverlink.app.data.local.dao.MedicationDao
import com.silverlink.app.data.local.dao.MemoryPhotoDao
import com.silverlink.app.data.local.entity.CognitiveLogEntity
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
        CognitiveLogEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun medicationDao(): MedicationDao
    abstract fun chatDao(): ChatDao
    abstract fun historyDao(): HistoryDao
    abstract fun memoryPhotoDao(): MemoryPhotoDao
    abstract fun cognitiveLogDao(): CognitiveLogDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "silverlink_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
