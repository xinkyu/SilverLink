package com.silverlink.app.feature.watch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.silverlink.sdk.nearby.ConnectionCallback
import com.silverlink.sdk.nearby.DeviceDiscoveryCallback
import com.silverlink.sdk.nearby.DeviceInfo
import com.silverlink.sdk.nearby.MockNearbyBridge
import com.silverlink.sdk.nearby.NearbyBridge
import com.silverlink.shared.protocol.MessageSerializer
import com.silverlink.shared.protocol.WatchMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phone-side foreground service that maintains the connection with the watch
 * via NearbyBridge. Handles message routing to/from WatchSyncManager.
 */
class WatchConnectionService : Service() {

    companion object {
        private const val TAG = "WatchConnection"
        private const val CHANNEL_ID = "watch_connection"
        private const val NOTIFICATION_ID = 4001

        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        private val _connectedDevice = MutableStateFlow<DeviceInfo?>(null)
        val connectedDevice: StateFlow<DeviceInfo?> = _connectedDevice.asStateFlow()

        private val _discoveredDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
        val discoveredDevices: StateFlow<List<DeviceInfo>> = _discoveredDevices.asStateFlow()

        private var instance: WatchConnectionService? = null

        fun start(context: Context) {
            val intent = Intent(context, WatchConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchConnectionService::class.java))
        }

        fun sendMessage(message: WatchMessage): Boolean {
            return instance?.sendWatchMessage(message) ?: false
        }

        fun startDiscovery() {
            instance?.doStartDiscovery()
        }

        fun connectToDevice(deviceId: String) {
            instance?.doConnect(deviceId)
        }

        fun disconnect() {
            instance?.doDisconnect()
        }
    }

    enum class ConnectionState {
        DISCONNECTED, DISCOVERING, CONNECTING, CONNECTED
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var nearbyBridge: NearbyBridge
    private var syncManager: WatchSyncManager? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        val notification = buildNotification("手表连接服务运行中")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Use MockNearbyBridge for now; replace with real SDK bridge later
        nearbyBridge = MockNearbyBridge()

        // Set up message listener
        nearbyBridge.setMessageListener { data ->
            try {
                val message = MessageSerializer.deserialize(data)
                Log.d(TAG, "Received message: ${message::class.simpleName}")
                syncManager?.onMessageReceived(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize message", e)
            }
        }

        // Initialize sync manager
        syncManager = WatchSyncManager(this, nearbyBridge)

        Log.d(TAG, "WatchConnectionService started")
    }

    private fun doStartDiscovery() {
        _connectionState.value = ConnectionState.DISCOVERING
        _discoveredDevices.value = emptyList()
        nearbyBridge.startDiscovery(object : DeviceDiscoveryCallback {
            override fun onDeviceFound(device: DeviceInfo) {
                Log.d(TAG, "Device found: ${device.name} (${device.id})")
                val current = _discoveredDevices.value.toMutableList()
                if (current.none { it.id == device.id }) {
                    current.add(device)
                    _discoveredDevices.value = current
                }
            }

            override fun onDeviceLost(deviceId: String) {
                _discoveredDevices.value = _discoveredDevices.value.filter { it.id != deviceId }
            }

            override fun onDiscoveryError(error: String) {
                Log.e(TAG, "Discovery error: $error")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    private fun doConnect(deviceId: String) {
        nearbyBridge.stopDiscovery()
        _connectionState.value = ConnectionState.CONNECTING
        nearbyBridge.connect(deviceId, object : ConnectionCallback {
            override fun onConnected(device: DeviceInfo) {
                Log.d(TAG, "Connected to: ${device.name}")
                _connectionState.value = ConnectionState.CONNECTED
                _connectedDevice.value = device
                // Trigger initial sync
                syncManager?.onConnected()
            }

            override fun onDisconnected(reason: String) {
                Log.d(TAG, "Disconnected: $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                _connectedDevice.value = null
            }

            override fun onConnectionFailed(error: String) {
                Log.e(TAG, "Connection failed: $error")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        })
    }

    private fun doDisconnect() {
        nearbyBridge.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
    }

    private fun sendWatchMessage(message: WatchMessage): Boolean {
        if (!nearbyBridge.isConnected()) return false
        val data = MessageSerializer.serialize(message)
        return nearbyBridge.sendMessage(data).isSuccess
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "手表连接", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "维护与手表的连接" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SilverLink 手表")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        nearbyBridge.disconnect()
        serviceScope.cancel()
        instance = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
        Log.d(TAG, "WatchConnectionService stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
