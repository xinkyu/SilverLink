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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.silverlink.app.MainActivity
import com.silverlink.app.R
import com.silverlink.app.data.local.UserPreferences
import kotlinx.coroutines.*
import java.util.Locale

/**
 * 位置追踪前台服务
 * 
 * 功能：
 * - 每5分钟获取一次位置并上传到云端
 * - 支持后台运行（前台服务）
 * - 自动反地理编码获取地址
 * - 仅使用原生 Android LocationManager 以保证在无 GMS 环境中的稳定性
 */
class LocationTrackingService : Service(), LocationListener {
    
    companion object {
        private const val TAG = "LocationTrackingService"
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 3001
        
        // 位置更新间隔：5分钟
        private const val LOCATION_UPDATE_INTERVAL_MS = 5 * 60 * 1000L
        // 最小更新距离：0米（只按时间）
        private const val LOCATION_MIN_DISTANCE_M = 0f
        
        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
        
        fun hasLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        
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
    
    private lateinit var userPreferences: UserPreferences
    private lateinit var syncRepository: com.silverlink.app.data.repository.SyncRepository
    private var locationManager: LocationManager? = null
    private var isListening = false
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created - Native Location")
        
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
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        
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

        if (!isLocationEnabled()) {
            Log.w(TAG, "系统定位开关未开启，等待用户开启后再获取位置")
        }
        
        val lm = locationManager ?: return
        
        // 尝试获取最新缓存位置
        var bestLastLocation: Location? = null
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { bestLastLocation = it }
        }
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (gpsLoc != null && (bestLastLocation == null || gpsLoc.time > bestLastLocation!!.time)) {
                bestLastLocation = gpsLoc
            }
        }
        
        if (bestLastLocation != null) {
            Log.d(TAG, "获取到缓存位置: lat=${bestLastLocation!!.latitude}, lng=${bestLastLocation!!.longitude}, time=${bestLastLocation!!.time}")
            uploadLocation(bestLastLocation!!.latitude, bestLastLocation!!.longitude, bestLastLocation!!.accuracy)
        } else {
            Log.d(TAG, "无缓存位置，等待监听回调...")
        }

        // 注册原生监听器
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL_MS,
                    LOCATION_MIN_DISTANCE_M,
                    this
                )
                isListening = true
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL_MS,
                    LOCATION_MIN_DISTANCE_M,
                    this
                )
                isListening = true
            }
            Log.d(TAG, "原生位置追踪已启动，间隔=${LOCATION_UPDATE_INTERVAL_MS / 1000}秒")
        } catch (e: Exception) {
            Log.e(TAG, "注册位置监听失败: ${e.message}")
        }
    }
    
    // ==================== LocationListener 回调 ====================
    
    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "原生位置更新: lat=${location.latitude}, lng=${location.longitude}, accuracy=${location.accuracy}, provider=${location.provider}")
        uploadLocation(location.latitude, location.longitude, location.accuracy)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    
    override fun onProviderEnabled(provider: String) {}
    
    override fun onProviderDisabled(provider: String) {}
    
    // ==========================================================
    
    private fun uploadLocation(latitude: Double, longitude: Double, accuracy: Float) {
        serviceScope.launch {
            try {
                // 获取设备ID
                val deviceId = getCurrentDeviceId()
                if (deviceId.isBlank()) {
                    Log.w(TAG, "设备ID为空，跳过上传")
                    return@launch
                }
                
                // 优化：先上传一次经纬度数据，确保家人端能立即同步到位置（反地理编码可能耗时数秒）
                val quickResult = syncRepository.uploadLocation(
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = accuracy,
                    address = ""
                )
                
                if (quickResult.isSuccess) {
                    Log.d(TAG, "快速位置上传成功")
                } else {
                    Log.e(TAG, "快速位置上传失败: ${quickResult.exceptionOrNull()?.message}")
                }
                
                // 异步进行耗时的反地理编码获取地址
                val address = getAddressFromLocation(latitude, longitude)
                
                // 如果获取到了地址，再补充上传一次
                if (address.isNotBlank()) {
                    val addressResult = syncRepository.uploadLocation(
                        latitude = latitude,
                        longitude = longitude,
                        accuracy = accuracy,
                        address = address
                    )
                    if (addressResult.isSuccess) {
                        Log.d(TAG, "带地址的位置上传成功: $address")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "位置上传异常: ${e.message}", e)
            }
        }
    }
    
    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 理论应使用异步方法，这里出于兼容返回空，依赖地图逆编码
                ""
            } else {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(this, Locale.CHINA)
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
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
    
    @SuppressLint("HardwareIds")
    private fun getCurrentDeviceId(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }

    private fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        
        if (isListening) {
            locationManager?.removeUpdates(this)
            isListening = false
        }
        
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
