package com.silverlink.sdk.nearby

/**
 * OPPO 近距离通讯 SDK 抽象接口
 *
 * SDK 到达后实现此接口，替换 MockNearbyBridge
 */
interface NearbyBridge {
    fun startDiscovery(callback: DeviceDiscoveryCallback)
    fun stopDiscovery()
    fun connect(deviceId: String, callback: ConnectionCallback)
    fun disconnect()
    fun sendMessage(message: ByteArray): Result<Unit>
    fun setMessageListener(listener: (ByteArray) -> Unit)
    fun isConnected(): Boolean
}

interface DeviceDiscoveryCallback {
    fun onDeviceFound(device: DeviceInfo)
    fun onDeviceLost(deviceId: String)
    fun onDiscoveryError(error: String)
}

interface ConnectionCallback {
    fun onConnected(device: DeviceInfo)
    fun onDisconnected(reason: String)
    fun onConnectionFailed(error: String)
}
