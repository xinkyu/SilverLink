package com.silverlink.app.feature.falldetection

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 位置助手
 * 获取当前位置并生成高德地图链接
 */
class LocationHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationHelper"
        private const val LOCATION_TIMEOUT_MS = 10000L
    }
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    /**
     * 获取当前位置
     * @return Location对象，如果获取失败返回null
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission")
            return null
        }
        
        return try {
            // 先尝试获取最后已知位置
            val lastLocation = getLastKnownLocation()
            if (lastLocation != null && isLocationFresh(lastLocation)) {
                Log.d(TAG, "Using last known location")
                return lastLocation
            }
            
            // 否则请求新的位置
            requestNewLocation()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location", e)
            null
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }
    
    @SuppressLint("MissingPermission")
    private suspend fun requestNewLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            )
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdates(1)
                .build()
            
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    continuation.resume(result.lastLocation)
                }
            }
            
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
            
            continuation.invokeOnCancellation {
                fusedLocationClient.removeLocationUpdates(callback)
            }
        }
    }
    
    /**
     * 检查位置是否新鲜（5分钟内）
     */
    private fun isLocationFresh(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs < 5 * 60 * 1000 // 5分钟
    }
    
    /**
     * 检查是否有位置权限
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 生成高德地图位置链接
     * @param location 位置对象
     * @return 高德地图链接
     */
    fun generateAmapLink(location: Location): String {
        val lat = location.latitude
        val lng = location.longitude
        // 高德地图标准链接格式
        return "https://uri.amap.com/marker?position=$lng,$lat&name=紧急位置&coordinate=wgs84"
    }
    
    /**
     * 生成位置描述文本（用于SMS）
     */
    fun generateLocationText(location: Location?): String {
        if (location == null) {
            return "（位置获取失败）"
        }
        val lat = String.format("%.6f", location.latitude)
        val lng = String.format("%.6f", location.longitude)
        val amapLink = generateAmapLink(location)
        return "位置：$amapLink (经度:$lng, 纬度:$lat)"
    }
}
