package com.silverlink.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.data.local.entity.MedicationLogEntity
import com.silverlink.app.data.local.entity.MoodLogEntity
import com.silverlink.app.data.repository.SyncRepository
import com.silverlink.app.ui.components.ChartType
import com.silverlink.app.ui.components.MedicationStatus
import com.silverlink.app.ui.components.MoodTimePoint
import com.silverlink.app.ui.components.TimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 某天的健康摘要（保留兼容旧代码）
 */
data class DaySummary(
    val date: String,               // "2026-01-18"
    val takenCount: Int,            // 已服用药品数
    val totalCount: Int,            // 计划服药数
    val dominantMood: String?,      // 主导情绪
    val hasMedicationLogs: Boolean,
    val hasMoodLogs: Boolean
)

/**
 * 历史记录ViewModel
 * 用于老人端查看服药和情绪历史（统一UI风格）
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val historyDao = AppDatabase.getInstance(application).historyDao()
    private val medicationDao = AppDatabase.getInstance(application).medicationDao()
    private val syncRepository = SyncRepository.getInstance(application)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }
    
    // 时间范围
    private val _selectedRange = MutableStateFlow(TimeRange.DAY)
    val selectedRange: StateFlow<TimeRange> = _selectedRange.asStateFlow()
    
    // 选中的日期
    private val _selectedDate = MutableStateFlow(Date())
    val selectedDate: StateFlow<Date> = _selectedDate.asStateFlow()
    
    // 图表类型
    private val _chartType = MutableStateFlow(ChartType.MOOD)
    val chartType: StateFlow<ChartType> = _chartType.asStateFlow()
    
    // 情绪数据点
    private val _moodPoints = MutableStateFlow<List<MoodTimePoint>>(emptyList())
    val moodPoints: StateFlow<List<MoodTimePoint>> = _moodPoints.asStateFlow()
    
    // 药品服用状态
    private val _medicationStatuses = MutableStateFlow<List<MedicationStatus>>(emptyList())
    val medicationStatuses: StateFlow<List<MedicationStatus>> = _medicationStatuses.asStateFlow()
    
    // 当前情绪
    private val _currentMood = MutableStateFlow<String?>(null)
    val currentMood: StateFlow<String?> = _currentMood.asStateFlow()
    
    // 最新时间
    private val _latestTime = MutableStateFlow<String?>(null)
    val latestTime: StateFlow<String?> = _latestTime.asStateFlow()
    
    // 选中的情绪点（用于显示详情）
    private val _selectedMoodPoint = MutableStateFlow<MoodTimePoint?>(null)
    val selectedMoodPoint: StateFlow<MoodTimePoint?> = _selectedMoodPoint.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadData()
    }
    
    fun setTimeRange(range: TimeRange) {
        _selectedRange.value = range
        loadData()
    }
    
    fun setSelectedDate(date: Date) {
        _selectedDate.value = date
        loadData()
    }
    
    fun setChartType(type: ChartType) {
        _chartType.value = type
    }
    
    fun selectMoodPoint(point: MoodTimePoint?) {
        _selectedMoodPoint.value = point
    }
    
    fun refresh() {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            val (startDate, endDate) = getDateRange()
            
            // 加载情绪记录
            loadMoodData(startDate, endDate)
            
            // 加载用药记录
            loadMedicationData(startDate)
            
            _isLoading.value = false
        }
    }
    
    private fun getDateRange(): Pair<String, String> {
        val cal = Calendar.getInstance()
        cal.time = _selectedDate.value
        
        return when (_selectedRange.value) {
            TimeRange.DAY -> {
                val date = dateFormat.format(cal.time)
                date to date
            }
            TimeRange.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = dateFormat.format(cal.time)
                cal.add(Calendar.DAY_OF_WEEK, 6)
                val end = dateFormat.format(cal.time)
                start to end
            }
            TimeRange.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = dateFormat.format(cal.time)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val end = dateFormat.format(cal.time)
                start to end
            }
            TimeRange.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                val start = dateFormat.format(cal.time)
                cal.set(Calendar.DAY_OF_YEAR, cal.getActualMaximum(Calendar.DAY_OF_YEAR))
                val end = dateFormat.format(cal.time)
                start to end
            }
        }
    }
    
    private suspend fun loadMoodData(startDate: String, endDate: String) {
        val moodLogs = if (startDate == endDate) {
            historyDao.getMoodLogsByDate(startDate)
        } else {
            historyDao.getMoodLogsByDateRange(startDate, endDate)
        }
        
        val points = moodLogs.map { log ->
            val time = formatTimeFromTimestamp(log.createdAt)
            
            MoodTimePoint(
                time = time,
                mood = log.mood,
                note = log.note,
                timestamp = log.createdAt
            )
        }.sortedBy { it.timestamp }
        
        _moodPoints.value = points
        
        // 设置当前情绪（最新的）
        val latest = points.lastOrNull()
        _currentMood.value = latest?.mood
        _latestTime.value = latest?.time
    }

    private fun formatTimeFromTimestamp(rawTimestamp: Long): String {
        return try {
            val millis = if (rawTimestamp in 1L..9_999_999_999L) {
                rawTimestamp * 1000L
            } else {
                rawTimestamp
            }
            val formatted = timeFormat.format(Date(millis))
            normalizeTime(formatted)
        } catch (e: Exception) {
            "00:00"
        }
    }

    private fun normalizeTime(raw: String): String {
        val regex = Regex("\\d{2}:\\d{2}")
        return regex.find(raw)?.value ?: "00:00"
    }
    
    private suspend fun loadMedicationData(date: String) {
        if (syncRepository.isElderDevice()) {
            syncRepository.syncMedicationsFromCloud(medicationDao)
        }
        val medicationLogs = historyDao.getMedicationLogsByDate(date)
        val allMedications = medicationDao.getAllMedications().first()
        
        // 按药品分组
        val logsByMedication = medicationLogs.groupBy { it.medicationName }
        
        val statuses = allMedications.map { med ->
            val logs = logsByMedication[med.name] ?: emptyList()
            val takenTimes = logs.filter { it.status == "taken" }.map { it.scheduledTime }.toSet()
            
            MedicationStatus(
                name = med.name,
                dosage = med.dosage,
                times = med.getTimeList(),
                takenTimes = takenTimes
            )
        }
        
        _medicationStatuses.value = statuses
    }
}
