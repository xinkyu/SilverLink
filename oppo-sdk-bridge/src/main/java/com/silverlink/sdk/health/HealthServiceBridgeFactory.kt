package com.silverlink.sdk.health

import android.content.Context

object HealthServiceBridgeFactory {

    @Volatile
    private var cached: HealthServiceBridge? = null

    fun get(context: Context): HealthServiceBridge {
        val existing = cached
        if (existing != null) return existing

        return synchronized(this) {
            cached ?: run {
                val real = OppoHealthServiceBridge(context.applicationContext)
                val bridge = if (real.isAvailable()) real else MockHealthServiceBridge()
                cached = bridge
                bridge
            }
        }
    }
}
