package com.silverlink.app.ui.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.remote.MedicationLogData
import com.silverlink.app.data.remote.MoodLogData
import com.silverlink.app.data.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 家人端某天的摘要数据
 */
data class FamilyDaySummary(
    val date: String,
    val takenCount: Int,
    val totalCount: Int,
    val dominantMood: String?,
    val hasMedicationLogs: Boolean,
    val hasMoodLogs: Boolean
)

/**
 * 加载状态
 */
sealed class LoadingState {
    object Idle : LoadingState()
    object Loading : LoadingState()
    data class Success(val message: String = "") : LoadingState()
    data class Error(val message: String) : LoadingState()
}

/**
 * 家人端监控ViewModel
 * 从云端获取已配对长辈的服药和情绪记录
 */
class FamilyMonitoringViewModel(application: Application) : AndroidViewModel(application) {
    
    private val syncRepository = SyncRepository.getInstance(application)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    // 加载状态
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()
    
    // 是否已配对
    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()
    
    // 选中的日期
    private val _selectedDate = MutableStateFlow(dateFormat.format(java.util.Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()
    
    // 当月摘要
    private val _monthSummaries = MutableStateFlow<Map<String, FamilyDaySummary>>(emptyMap())
    val monthSummaries: StateFlow<Map<String, FamilyDaySummary>> = _monthSummaries.asStateFlow()
    
    // 选中日期的服药记录
    private val _medicationLogs = MutableStateFlow<List<MedicationLogData>>(emptyList())
    val medicationLogs: StateFlow<List<MedicationLogData>> = _medicationLogs.asStateFlow()
    
    // 选中日期的情绪记录
    private val _moodLogs = MutableStateFlow<List<MoodLogData>>(emptyList())
    val moodLogs: StateFlow<List<MoodLogData>> = _moodLogs.asStateFlow()
    
    // 当前显示的年月
    private val _currentMonth = MutableStateFlow(Calendar.getInstance())
    val currentMonth: StateFlow<Calendar> = _currentMonth.asStateFlow()
    
    // 长辈名字
    private val _elderName = MutableStateFlow("")
    val elderName: StateFlow<String> = _elderName.asStateFlow()
    
    init {
        loadData()
    }
    
    /**
     * 加载数据
     */
    fun loadData() {
        viewModelScope.launch {
            _loadingState.value = LoadingState.Loading
            
            // 获取服药记录（会自动获取配对的长辈设备ID）
            val medicationResult = syncRepository.getElderMedicationLogs(date = null)
            val moodResult = syncRepository.getElderMoodLogs(days = 30)
            
            if (medicationResult.isFailure && moodResult.isFailure) {
                // 两者都失败，可能未配对
                val errorMsg = medicationResult.exceptionOrNull()?.message ?: "未知错误"
                if (errorMsg.contains("未找到已配对")) {
                    _isPaired.value = false
                    _loadingState.value = LoadingState.Error("未找到已配对的长辈")
                } else {
                    _loadingState.value = LoadingState.Error(errorMsg)
                }
                return@launch
            }
            
            _isPaired.value = true
            
            // 处理服药记录
            val allMedLogs = medicationResult.getOrDefault(emptyList())
            val allMoodLogs = moodResult.getOrDefault(emptyList())
            
            // 按日期分组生成摘要
            val medByDate = allMedLogs.groupBy { it.date }
            val moodByDate = allMoodLogs.groupBy { it.date }
            
            val allDates = (medByDate.keys + moodByDate.keys).distinct()
            
            val summaries = allDates.associateWith { date ->
                val meds = medByDate[date] ?: emptyList()
                val moods = moodByDate[date] ?: emptyList()
                
                FamilyDaySummary(
                    date = date,
                    takenCount = meds.count { it.status == "taken" },
                    totalCount = meds.size,
                    dominantMood = moods.maxByOrNull { m -> moods.count { it.mood == m.mood } }?.mood,
                    hasMedicationLogs = meds.isNotEmpty(),
                    hasMoodLogs = moods.isNotEmpty()
                )
            }
            
            _monthSummaries.value = summaries
            
            // 加载选中日期的详细记录
            loadSelectedDateLogs(allMedLogs, allMoodLogs)
            
            _loadingState.value = LoadingState.Success()
        }
    }
    
    /**
     * 选择日期
     */
    fun selectDate(date: String) {
        _selectedDate.value = date
        // 从已加载的数据中筛选
        viewModelScope.launch {
            val medicationResult = syncRepository.getElderMedicationLogs(date = date)
            val moodResult = syncRepository.getElderMoodLogs(days = 30)
            
            if (medicationResult.isSuccess) {
                _medicationLogs.value = medicationResult.getOrDefault(emptyList())
                    .filter { it.date == date }
            }
            
            if (moodResult.isSuccess) {
                _moodLogs.value = moodResult.getOrDefault(emptyList())
                    .filter { it.date == date }
            }
        }
    }
    
    /**
     * 加载选中日期的记录
     */
    private fun loadSelectedDateLogs(
        allMedLogs: List<MedicationLogData>,
        allMoodLogs: List<MoodLogData>
    ) {
        val date = _selectedDate.value
        _medicationLogs.value = allMedLogs.filter { it.date == date }
        _moodLogs.value = allMoodLogs.filter { it.date == date }
    }
    
    /**
     * 上个月
     */
    fun previousMonth() {
        val newMonth = _currentMonth.value.clone() as Calendar
        newMonth.add(Calendar.MONTH, -1)
        _currentMonth.value = newMonth
    }
    
    /**
     * 下个月
     */
    fun nextMonth() {
        val newMonth = _currentMonth.value.clone() as Calendar
        newMonth.add(Calendar.MONTH, 1)
        _currentMonth.value = newMonth
    }
    
    /**
     * 刷新
     */
    fun refresh() {
        loadData()
    }
    
    // ==================== 药品管理 ====================
    
    // 添加药品状态
    private val _addMedicationState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val addMedicationState: StateFlow<LoadingState> = _addMedicationState.asStateFlow()
    
    /**
     * 为长辈添加药品
     */
    fun addMedication(name: String, dosage: String, times: String) {
        viewModelScope.launch {
            _addMedicationState.value = LoadingState.Loading
            
            val result = syncRepository.addMedicationForElder(
                name = name,
                dosage = dosage,
                times = times
            )
            
            if (result.isSuccess) {
                _addMedicationState.value = LoadingState.Success("添加成功")
                // 刷新数据
                loadData()
            } else {
                _addMedicationState.value = LoadingState.Error(
                    result.exceptionOrNull()?.message ?: "添加失败"
                )
            }
        }
    }
    
    /**
     * 重置添加状态
     */
    fun resetAddMedicationState() {
        _addMedicationState.value = LoadingState.Idle
    }
}

