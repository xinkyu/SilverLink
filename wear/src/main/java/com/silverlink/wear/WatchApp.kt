package com.silverlink.wear

import android.app.Application
import com.silverlink.wear.data.WatchPreferences

class WatchApp : Application() {

    lateinit var watchPreferences: WatchPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        watchPreferences = WatchPreferences(this)
    }

    companion object {
        lateinit var instance: WatchApp
            private set
    }
}
