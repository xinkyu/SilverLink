package com.silverlink.app.ui.reminder

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserRole
import com.silverlink.app.data.local.entity.Medication
import com.silverlink.app.data.local.entity.MedicationLogEntity
import com.silverlink.app.data.repository.SyncRepository
import com.silverlink.app.feature.reminder.AlarmScheduler
import com.silverlink.app.feature.reminder.MedicationRecognitionService
import com.silverlink.app.feature.reminder.RecognizedMedication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 识别状态
 */
sealed class RecognitionState {
    object Idle : RecognitionState()
    object Loading : RecognitionState()
    data class Success(val medication: RecognizedMedication) : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}

/**
 * 同步状态
 */
sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val count: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    
    private val dao = SilverLinkApp.database.medicationDao()
    private val historyDao = SilverLinkApp.database.historyDao()
    private val alarmScheduler = AlarmScheduler(application)
    private val recognitionService = MedicationRecognitionService()
    private val syncRepository = SyncRepository.getInstance(application)
    private val userPreferences = UserPreferences.getInstance(application)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications.asStateFlow()

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _takenTimes = MutableStateFlow<Map<Int, Set<String>>>(emptyMap())
    val takenTimes: StateFlow<Map<Int, Set<String>>> = _takenTimes.asStateFlow()

    init {
        viewModelScope.launch {
            // 监听本地药品变化
            dao.getAllMedications().collect { list ->
                _medications.value = list
                loadTodayTakenTimes()
            }
        }
        
        // 如果是老人端，初始化时从云端同步药品
        viewModelScope.launch {
            if (userPreferences.userConfig.value.role == UserRole.ELDER) {
                syncMedicationsFromCloud()
                // 同步今日服药记录，恢复打勾状态
                syncMedicationLogsFromCloud(dateFormat.format(java.util.Date()))
            }
        }
    }
    
    /**
     * 从云端同步药品到本地（老人端调用）
     * 家人端添加的药品会通过此方法同步到老人端
     */
    fun syncMedicationsFromCloud() {
        if (userPreferences.userConfig.value.role != UserRole.ELDER) {
            Log.d("ReminderViewModel", "非老人端，跳过同步")
            return
        }
        
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val result = syncRepository.syncMedicationsFromCloud(dao)
                result.fold(
                    onSuccess = { count ->
                        Log.d("ReminderViewModel", "同步成功，新增 $count 条药品")
                        _syncState.value = SyncState.Success(count)
                        
                        // 同步完成后为所有药品重新设置闹钟（包含时间合并后的新增时间点）
                        val currentMeds = dao.getAllMedications().first()
                        currentMeds.forEach { med ->
                            alarmScheduler.scheduleAll(med)
                        }
                    },
                    onFailure = { error ->
                        Log.e("ReminderViewModel", "同步失败: ${error.message}")
                        _syncState.value = SyncState.Error(error.message ?: "同步失败")
                    }
                )
            } catch (e: Exception) {
                Log.e("ReminderViewModel", "同步异常: ${e.message}")
                _syncState.value = SyncState.Error(e.message ?: "同步异常")
            }
        }
    }
    
    /**
     * 刷新药品列表（包括从云端同步）
     */
    fun refresh() {
        syncMedicationsFromCloud()
        syncMedicationLogsFromCloud(dateFormat.format(java.util.Date()))
    }

    /**
     * 从云端同步服药记录到本地（老人端调用）
     */
    fun syncMedicationLogsFromCloud(date: String? = null) {
        if (userPreferences.userConfig.value.role != UserRole.ELDER) {
            return
        }

        viewModelScope.launch {
            try {
                syncRepository.syncMedicationLogsFromCloud(historyDao, date)
            } catch (_: Exception) {
                // 忽略同步失败，不影响本地显示
            } finally {
                loadTodayTakenTimes()
            }
        }
    }

    fun addMedication(name: String, dosage: String, times: List<String>) {
        viewModelScope.launch {
            val timesStr = times.joinToString(",")
            val med = Medication(
                name = name,
                dosage = dosage,
                times = timesStr,
                isTakenToday = false
            )
            val id = dao.insertMedication(med)
            val savedMed = med.copy(id = id.toInt())
            alarmScheduler.scheduleAll(savedMed)
        }
    }

    fun updateMedication(medication: Medication, name: String, dosage: String, times: List<String>) {
        viewModelScope.launch {
            // 先取消旧的闹钟
            alarmScheduler.cancelAll(medication)
            
            // 更新药品信息
            val timesStr = times.joinToString(",")
            val updated = medication.copy(
                name = name,
                dosage = dosage,
                times = timesStr
            )
            dao.updateMedication(updated)
            
            // 设置新的闹钟
            alarmScheduler.scheduleAll(updated)

            // 老人端修改时间后同步到云端，避免被云端旧数据覆盖
            if (userPreferences.userConfig.value.role == UserRole.ELDER) {
                syncRepository.updateMedicationTimesForElder(
                    name = updated.name,
                    dosage = updated.dosage,
                    times = updated.times
                )
            }
        }
    }

    fun toggleTaken(medication: Medication) {
        viewModelScope.launch {
            val updated = medication.copy(isTakenToday = !medication.isTakenToday)
            dao.updateMedication(updated)
        }
    }
    
    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            alarmScheduler.cancelAll(medication)
            dao.deleteMedication(medication)
        }
    }

    /**
     * 标记某个时间点已服药，并写入本地历史记录与云端同步
     */
    fun markMedicationTimeTaken(medication: Medication, time: String) {
        if (medication.id <= 0 || time.isBlank()) return

        viewModelScope.launch {
            // 先乐观更新 UI，避免点击卡顿
            val currentMap = _takenTimes.value
            val currentSet = currentMap[medication.id].orEmpty()
            if (!currentSet.contains(time)) {
                val updatedSet = currentSet.toMutableSet().apply { add(time) }
                val updatedMap = currentMap.toMutableMap().apply { put(medication.id, updatedSet) }
                _takenTimes.value = updatedMap
            }

            val date = dateFormat.format(java.util.Date())
            val inserted = withContext(Dispatchers.IO) {
                val existingCount = historyDao.getMedicationLogCount(date, medication.id, time)
                if (existingCount > 0) return@withContext false

                val logEntity = MedicationLogEntity(
                    medicationId = medication.id,
                    medicationName = medication.name,
                    dosage = medication.dosage,
                    scheduledTime = time,
                    status = "taken",
                    date = date
                )
                historyDao.insertMedicationLog(logEntity)

                syncRepository.syncMedicationTaken(
                    medicationId = medication.id,
                    medicationName = medication.name,
                    dosage = medication.dosage,
                    scheduledTime = time,
                    status = "taken"
                )
                true
            }

            if (inserted) {
                loadTodayTakenTimes()
            }
        }
    }

    private fun loadTodayTakenTimes() {
        viewModelScope.launch {
            val date = dateFormat.format(java.util.Date())
            val result = withContext(Dispatchers.IO) {
                val logs = historyDao.getMedicationLogsByDate(date)
                val map = logs.groupBy { it.medicationId }
                    .mapValues { entry -> entry.value.map { it.scheduledTime }.toSet() }

                val currentMeds = dao.getAllMedications().first()
                Triple(map, currentMeds, date)
            }

            val map = result.first
            val currentMeds = result.second

            _takenTimes.value = map

            // 更新药品的 isTakenToday 状态
            withContext(Dispatchers.IO) {
                currentMeds.forEach { med ->
                    val takenSet = map[med.id].orEmpty()
                    val allTaken = med.getTimeList().all { it in takenSet }
                    if (med.isTakenToday != allTaken) {
                        dao.updateMedication(med.copy(isTakenToday = allTaken))
                    }
                }
            }
        }
    }

    /**
     * 使用 AI 识别药品图片
     */
    fun recognizeMedication(bitmap: Bitmap) {
        viewModelScope.launch {
            _recognitionState.value = RecognitionState.Loading
            
            val result = recognitionService.recognizeMedication(bitmap)
            
            result.fold(
                onSuccess = { recognized ->
                    _recognitionState.value = RecognitionState.Success(recognized)
                },
                onFailure = { error ->
                    _recognitionState.value = RecognitionState.Error(
                        error.message ?: "识别失败，请重试"
                    )
                }
            )
        }
    }

    /**
     * 重置识别状态
     */
    fun resetRecognitionState() {
        _recognitionState.value = RecognitionState.Idle
    }

    /**
     * 保存识别到的药品
     */
    fun saveRecognizedMedication(name: String, dosage: String, times: List<String>) {
        addMedication(name, dosage, times)
        resetRecognitionState()
    }
}
