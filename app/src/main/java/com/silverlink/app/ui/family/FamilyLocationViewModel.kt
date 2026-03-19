package com.silverlink.app.ui.family

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.local.FamilyGeofenceRuntimeState
import com.silverlink.app.data.local.FamilyGeofenceSettings
import com.silverlink.app.data.local.GeofenceBoundaryStatus
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.remote.LocationData
import com.silverlink.app.data.repository.SyncRepository
import com.silverlink.app.feature.location.FamilyGeofenceMonitor
import com.silverlink.app.feature.location.FamilyGeofenceNotifier
import com.silverlink.app.feature.location.GeofenceEvaluationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class FamilyLocationViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "FamilyLocationVM"
    }

    private val syncRepository = SyncRepository.getInstance(application)
    private val userPreferences = UserPreferences.getInstance(application)
    private val elderName: String
        get() = userPreferences.userConfig.value.elderName.ifBlank { "长辈" }

    private val _isPaired = MutableStateFlow(true)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _isLocationLoading = MutableStateFlow(false)
    val isLocationLoading: StateFlow<Boolean> = _isLocationLoading.asStateFlow()

    private val _elderLocation = MutableStateFlow<LocationData?>(null)
    val elderLocation: StateFlow<LocationData?> = _elderLocation.asStateFlow()

    private val _locationHistory = MutableStateFlow<List<LocationData>>(emptyList())
    val locationHistory: StateFlow<List<LocationData>> = _locationHistory.asStateFlow()

    private val _settings = MutableStateFlow(userPreferences.getFamilyGeofenceSettings())
    val settings: StateFlow<FamilyGeofenceSettings> = _settings.asStateFlow()

    private val _runtimeState = MutableStateFlow(userPreferences.getFamilyGeofenceRuntimeState())
    val runtimeState: StateFlow<FamilyGeofenceRuntimeState> = _runtimeState.asStateFlow()

    private val _evaluation = MutableStateFlow(GeofenceEvaluationResult(runtimeState = _runtimeState.value))
    val evaluation: StateFlow<GeofenceEvaluationResult> = _evaluation.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var pollingJob: Job? = null

    init {
        refreshLocation()
    }

    fun startMonitoring() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            refreshLocation()
            while (isActive) {
                delay(30_000L)
                refreshLocation(showLoading = false)
            }
        }
    }

    fun stopMonitoring() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun consumeToastMessage() {
        _toastMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun refreshLocation(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _isLocationLoading.value = true
            }
            try {
                Log.d(TAG, "refreshLocation start, showLoading=$showLoading")
                val result = syncRepository.getElderLocation()
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    _isPaired.value = true
                    _elderLocation.value = data?.latest
                    _locationHistory.value = data?.history.orEmpty()
                    _errorMessage.value = null
                    Log.d(
                        TAG,
                        "refreshLocation success, latest=${data?.latest != null}, historyCount=${data?.history?.size ?: 0}, accuracy=${data?.latest?.accuracy ?: 0f}"
                    )
                    evaluateGeofence(data?.latest)
                } else {
                    val message = result.exceptionOrNull()?.message ?: "位置获取失败"
                    if (message.contains("未找到已配对")) {
                        _isPaired.value = false
                        Log.w(TAG, "refreshLocation unpaired: $message")
                    } else {
                        _errorMessage.value = message
                        Log.e(TAG, "refreshLocation failed: $message")
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "位置获取失败"
                Log.e(TAG, "refreshLocation exception", e)
            } finally {
                if (showLoading) {
                    _isLocationLoading.value = false
                }
            }
        }
    }

    fun updateMonitoringEnabled(enabled: Boolean) {
        persistSettings(_settings.value.copy(enabled = enabled))
        if (!enabled) {
            _evaluation.value = GeofenceEvaluationResult(runtimeState = _runtimeState.value)
        } else {
            reevaluateLatestLocation()
        }
    }

    fun saveFenceDefinition(
        latitudeInput: String,
        longitudeInput: String,
        radiusInput: String
    ): Boolean {
        val latitude = latitudeInput.trim().toDoubleOrNull()
        val longitude = longitudeInput.trim().toDoubleOrNull()
        val radius = radiusInput.trim().toFloatOrNull()

        when {
            latitude == null || latitude !in -90.0..90.0 -> {
                _toastMessage.value = "请输入有效的守护中心纬度"
                return false
            }
            longitude == null || longitude !in -180.0..180.0 -> {
                _toastMessage.value = "请输入有效的守护中心经度"
                return false
            }
            radius == null || radius !in 50f..5000f -> {
                _toastMessage.value = "守护半径请设置在 50 到 5000 米之间"
                return false
            }
        }

        persistSettings(
            _settings.value.copy(
                centerLatitude = latitude,
                centerLongitude = longitude,
                radiusMeters = radius
            )
        )
        reevaluateLatestLocation()
        _toastMessage.value = "防走失守护范围已更新"
        return true
    }

    fun useLatestLocationAsFenceCenter() {
        val latest = _elderLocation.value
        if (latest == null) {
            _toastMessage.value = "还没有可用位置，暂时无法设置圆心"
            return
        }
        persistSettings(
            _settings.value.copy(
                centerLatitude = latest.latitude,
                centerLongitude = latest.longitude
            )
        )
        reevaluateLatestLocation()
        _toastMessage.value = "已将当前位置设为守护中心"
    }

    fun updateNotifyOnExit(enabled: Boolean) {
        persistSettings(_settings.value.copy(notifyOnExit = enabled))
    }

    fun updateNotifyOnEnter(enabled: Boolean) {
        persistSettings(_settings.value.copy(notifyOnEnter = enabled))
    }

    fun updateDwellMinutes(minutes: Int) {
        persistSettings(_settings.value.copy(dwellMinutes = minutes.coerceIn(3, 5)))
        reevaluateLatestLocation()
    }

    fun updateQuietHoursEnabled(enabled: Boolean) {
        persistSettings(_settings.value.copy(quietHoursEnabled = enabled))
    }

    fun updateLowFrequencyEnabled(enabled: Boolean) {
        persistSettings(_settings.value.copy(lowFrequencyEnabled = enabled))
    }

    private fun persistSettings(newSettings: FamilyGeofenceSettings) {
        userPreferences.setFamilyGeofenceSettings(newSettings)
        _settings.value = userPreferences.getFamilyGeofenceSettings()
        _runtimeState.value = userPreferences.getFamilyGeofenceRuntimeState()
    }

    private fun reevaluateLatestLocation() {
        viewModelScope.launch {
            evaluateGeofence(_elderLocation.value)
        }
    }

    private suspend fun evaluateGeofence(latestLocation: LocationData?) {
        val result = FamilyGeofenceMonitor.evaluate(
            settings = _settings.value,
            runtimeState = _runtimeState.value,
            latestLocation = latestLocation
        )
        _runtimeState.value = result.runtimeState
        _evaluation.value = result
        userPreferences.setFamilyGeofenceRuntimeState(result.runtimeState)
        Log.d(
            TAG,
            "evaluateGeofence status=${result.stableStatus}, pending=${result.pendingStatus}, distance=${result.distanceMeters}, uncertain=${result.isUncertain}, enter=${result.shouldNotifyEnter}, exit=${result.shouldNotifyExit}"
        )

        when {
            result.shouldNotifyExit -> sendFenceAlert(
                isExitAlert = true,
                message = buildFenceAlertMessage(isExitAlert = true, result = result)
            )
            result.shouldNotifyEnter -> sendFenceAlert(
                isExitAlert = false,
                message = buildFenceAlertMessage(isExitAlert = false, result = result)
            )
        }
    }

    private suspend fun sendFenceAlert(isExitAlert: Boolean, message: String) {
        val title = if (isExitAlert) "防走失守护离开提醒" else "防走失守护返回提醒"
        Log.w(TAG, "sendFenceAlert title=$title, message=$message")
        FamilyGeofenceNotifier.notify(
            context = getApplication(),
            title = title,
            message = message,
            isExitAlert = isExitAlert
        )
        syncRepository.sendAlertForPairedElder(
            alertType = if (isExitAlert) "geofence_exit" else "geofence_enter",
            message = message,
            elderName = elderName
        )
    }

    private fun buildFenceAlertMessage(
        isExitAlert: Boolean,
        result: GeofenceEvaluationResult
    ): String {
        val distanceText = result.distanceMeters?.let { "${it.toInt()}米" } ?: "未知距离"
        return if (isExitAlert) {
            "${elderName}已连续${_settings.value.dwellMinutes}分钟离开守护范围，当前距离守护中心约$distanceText。"
        } else {
            "${elderName}已重新回到守护范围内，当前距离守护中心约$distanceText。"
        }
    }

    fun currentStatusLabel(): String {
        if (!_settings.value.enabled) return "监测已关闭"
        if (!_settings.value.hasCenter) return "待设置守护范围"
        return when (_evaluation.value.stableStatus) {
            GeofenceBoundaryStatus.INSIDE -> "在守护中"
            GeofenceBoundaryStatus.OUTSIDE -> "已离开守护范围"
            GeofenceBoundaryStatus.UNKNOWN -> "等待判断"
        }
    }

    fun pendingStatusLabel(): String? {
        return when (_evaluation.value.pendingStatus) {
            GeofenceBoundaryStatus.INSIDE -> "正在确认回到守护范围"
            GeofenceBoundaryStatus.OUTSIDE -> "正在确认离开守护范围"
            else -> null
        }
    }

    fun distanceSummary(): String {
        if (!_settings.value.hasCenter) return "请先设置守护中心和守护半径"
        val distance = _evaluation.value.distanceMeters ?: return "暂无距离数据"
        val radius = _settings.value.radiusMeters
        return String.format(Locale.CHINA, "距离守护中心 %.0f 米，守护半径 %.0f 米", distance, radius)
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}
