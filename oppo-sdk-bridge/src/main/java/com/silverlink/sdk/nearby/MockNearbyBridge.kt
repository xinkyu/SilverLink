package com.silverlink.sdk.nearby

/**
 * Mock 实现，用于开发期间模拟近距离通讯
 */
class MockNearbyBridge : NearbyBridge {

    private var messageListener: ((ByteArray) -> Unit)? = null
    private var connected = false

    override fun startDiscovery(callback: DeviceDiscoveryCallback) {
        // Simulate finding a mock watch device
        callback.onDeviceFound(
            DeviceInfo(
                id = "mock-watch-001",
                name = "OPPO Watch Mock",
                type = DeviceType.WATCH
            )
        )
    }

    override fun stopDiscovery() {
        // No-op for mock
    }

    override fun connect(deviceId: String, callback: ConnectionCallback) {
        connected = true
        callback.onConnected(
            DeviceInfo(
                id = deviceId,
                name = "OPPO Watch Mock",
                type = DeviceType.WATCH
            )
        )
    }

    override fun disconnect() {
        connected = false
    }

    override fun sendMessage(message: ByteArray): Result<Unit> {
        return if (connected) {
            // In mock mode, echo back to simulate receiving
            messageListener?.invoke(message)
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Not connected"))
        }
    }

    override fun setMessageListener(listener: (ByteArray) -> Unit) {
        messageListener = listener
    }

    override fun isConnected(): Boolean = connected
}
