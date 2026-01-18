package com.silverlink.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.data.local.entity.MedicationLogEntity
import com.silverlink.app.data.local.entity.MoodLogEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 某天的健康摘要
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
 * 用于老人端查看服药和情绪历史
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val historyDao = AppDatabase.getInstance(application).historyDao()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    // 选中的日期
    private val _selectedDate = MutableStateFlow(dateFormat.format(java.util.Date()))
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()
    
    // 当月摘要列表（用于日历显示）
    private val _monthSummaries = MutableStateFlow<Map<String, DaySummary>>(emptyMap())
    val monthSummaries: StateFlow<Map<String, DaySummary>> = _monthSummaries.asStateFlow()
    
    // 选中日期的服药记录
    private val _medicationLogs = MutableStateFlow<List<MedicationLogEntity>>(emptyList())
    val medicationLogs: StateFlow<List<MedicationLogEntity>> = _medicationLogs.asStateFlow()
    
    // 选中日期的情绪记录
    private val _moodLogs = MutableStateFlow<List<MoodLogEntity>>(emptyList())
    val moodLogs: StateFlow<List<MoodLogEntity>> = _moodLogs.asStateFlow()
    
    // 当前显示的年月
    private val _currentMonth = MutableStateFlow(Calendar.getInstance())
    val currentMonth: StateFlow<Calendar> = _currentMonth.asStateFlow()
    
    init {
        loadCurrentMonthSummaries()
        loadSelectedDateLogs()
    }
    
    /**
     * 选择日期
     */
    fun selectDate(date: String) {
        _selectedDate.value = date
        loadSelectedDateLogs()
    }
    
    /**
     * 上个月
     */
    fun previousMonth() {
        val newMonth = _currentMonth.value.clone() as Calendar
        newMonth.add(Calendar.MONTH, -1)
        _currentMonth.value = newMonth
        loadCurrentMonthSummaries()
    }
    
    /**
     * 下个月
     */
    fun nextMonth() {
        val newMonth = _currentMonth.value.clone() as Calendar
        newMonth.add(Calendar.MONTH, 1)
        _currentMonth.value = newMonth
        loadCurrentMonthSummaries()
    }
    
    /**
     * 加载当月的每日摘要
     */
    private fun loadCurrentMonthSummaries() {
        viewModelScope.launch {
            val calendar = _currentMonth.value.clone() as Calendar
            
            // 月初
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val startDate = dateFormat.format(calendar.time)
            
            // 月末
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val endDate = dateFormat.format(calendar.time)
            
            val medicationLogs = historyDao.getMedicationLogsByDateRange(startDate, endDate)
            val moodLogs = historyDao.getMoodLogsByDateRange(startDate, endDate)
            
            // 按日期分组
            val medicationByDate = medicationLogs.groupBy { it.date }
            val moodByDate = moodLogs.groupBy { it.date }
            
            val allDates = (medicationByDate.keys + moodByDate.keys).distinct()
            
            val summaries = allDates.associateWith { date ->
                val meds = medicationByDate[date] ?: emptyList()
                val moods = moodByDate[date] ?: emptyList()
                
                DaySummary(
                    date = date,
                    takenCount = meds.count { it.status == "taken" },
                    totalCount = meds.size,
                    dominantMood = moods.maxByOrNull { m -> moods.count { it.mood == m.mood } }?.mood,
                    hasMedicationLogs = meds.isNotEmpty(),
                    hasMoodLogs = moods.isNotEmpty()
                )
            }
            
            _monthSummaries.value = summaries
        }
    }
    
    /**
     * 加载选中日期的详细记录
     */
    private fun loadSelectedDateLogs() {
        viewModelScope.launch {
            val date = _selectedDate.value
            _medicationLogs.value = historyDao.getMedicationLogsByDate(date)
            _moodLogs.value = historyDao.getMoodLogsByDate(date)
        }
    }
    
    /**
     * 刷新数据
     */
    fun refresh() {
        loadCurrentMonthSummaries()
        loadSelectedDateLogs()
    }
}
