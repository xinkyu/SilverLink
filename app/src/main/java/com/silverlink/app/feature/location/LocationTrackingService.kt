package com.silverlink.app.feature.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.silverlink.app.MainActivity
import com.silverlink.app.R
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.remote.CloudBaseService
import kotlinx.coroutines.*
import java.util.Locale

/**
 * 位置追踪前台服务
 * 
 * 功能：
 * - 每5分钟获取一次位置并上传到云端
 * - 支持后台运行（前台服务）
 * - 自动反地理编码获取地址
 */
class LocationTrackingService : Service() {
    
    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 3001
        
        // 位置更新间隔：5分钟
        private const val LOCATION_UPDATE_INTERVAL_MS = 5 * 60 * 1000L
        // 最快更新间隔：2分钟（防止过于频繁）
        private const val LOCATION_FASTEST_INTERVAL_MS = 2 * 60 * 1000L
        
        /**
         * 启动位置追踪服务
         */
        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止位置追踪服务
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
        
        /**
         * 检查是否有位置权限
         */
        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        
        /**
         * 检查是否有后台位置权限（Android 10+）
         */
        fun hasBackgroundLocationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Android 10 以下不需要单独的后台位置权限
            }
        }
    }
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var userPreferences: UserPreferences
    private lateinit var syncRepository: com.silverlink.app.data.repository.SyncRepository
    private var locationCallback: LocationCallback? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 启动前台服务
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // 初始化
        userPreferences = UserPreferences.getInstance(applicationContext)
        syncRepository = com.silverlink.app.data.repository.SyncRepository.getInstance(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // 开始位置追踪
        startLocationUpdates()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "位置共享",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "定期共享位置给家人"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("位置共享已开启")
            .setContentText("家人可以查看您的位置")
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission(this)) {
            Log.w(TAG, "没有位置权限，无法启动位置追踪")
            stopSelf()
            return
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "位置更新: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}")
                    uploadLocation(location.latitude, location.longitude, location.accuracy)
                }
            }
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            Looper.getMainLooper()
        )
        
        Log.d(TAG, "位置追踪已启动，间隔=${LOCATION_UPDATE_INTERVAL_MS / 1000}秒")
        
        // 立即获取一次位置
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                Log.d(TAG, "初始位置: lat=${it.latitude}, lng=${it.longitude}")
                uploadLocation(it.latitude, it.longitude, it.accuracy)
            }
        }
    }
    
    /**
     * 上传位置到云端
     */
    private fun uploadLocation(latitude: Double, longitude: Double, accuracy: Float) {
        serviceScope.launch {
            try {
                // 获取设备ID
                val deviceId = getCurrentDeviceId()
                if (deviceId.isBlank()) {
                    Log.w(TAG, "设备ID为空，跳过上传")
                    return@launch
                }
                
                // 反地理编码获取地址
                val address = getAddressFromLocation(latitude, longitude)
                
                // 上传到云端
                val result = syncRepository.uploadLocation(
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = accuracy,
                    address = address
                )
                
                if (result.isSuccess) {
                    Log.d(TAG, "位置上传成功: $address")
                } else {
                    Log.e(TAG, "位置上传失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "位置上传异常: ${e.message}", e)
            }
        }
    }
    
    /**
     * 反地理编码获取地址
     */
    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用异步方法，这里简化处理
                ""
            } else {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(this, Locale.CHINA)
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    // 拼接地址
                    buildString {
                        addr.adminArea?.let { append(it) }  // 省
                        addr.locality?.let { append(it) }    // 市
                        addr.subLocality?.let { append(it) } // 区
                        addr.thoroughfare?.let { append(it) } // 街道
                        addr.featureName?.let { 
                            if (it != addr.thoroughfare) append(it) // 门牌号
                        }
                    }.ifBlank { addr.getAddressLine(0) ?: "" }
                } else {
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "反地理编码失败: ${e.message}")
            ""
        }
    }
    
    /**
     * 获取设备ID
     */
    @SuppressLint("HardwareIds")
    private fun getCurrentDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        
        // 移除位置更新
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        
        // 取消所有协程
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
