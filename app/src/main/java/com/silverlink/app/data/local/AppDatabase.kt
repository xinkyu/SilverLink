package com.silverlink.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.silverlink.app.data.local.dao.MedicationDao
import com.silverlink.app.data.local.entity.Medication

@Database(entities = [Medication::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
}
