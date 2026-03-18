package com.silverlink.sdk.health

import android.content.Context

object HealthServiceBridgeFactory {

    @Volatile
    private var cached: HealthServiceBridge? = null
    @Volatile
    private var forceMock: Boolean = false

    fun setForceMock(enabled: Boolean) {
        synchronized(this) {
            forceMock = enabled
            cached = null
        }
    }

    fun get(context: Context): HealthServiceBridge {
        val existing = cached
        if (existing != null) return existing

        return synchronized(this) {
            cached ?: run {
                val real = OppoHealthServiceBridge(context.applicationContext)
                val bridge = if (forceMock) {
                    MockHealthServiceBridge()
                } else if (real.isAvailable()) {
                    real
                } else {
                    MockHealthServiceBridge()
                }
                cached = bridge
                bridge
            }
        }
    }
}
