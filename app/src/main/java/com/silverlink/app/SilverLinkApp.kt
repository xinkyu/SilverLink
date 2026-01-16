package com.silverlink.app

import android.app.Application
import androidx.room.Room
import com.silverlink.app.data.local.AppDatabase

class SilverLinkApp : Application() {
    
    companion object {
        lateinit var database: AppDatabase
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Room Database
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "silverlink-db"
        ).build()
    }
}
