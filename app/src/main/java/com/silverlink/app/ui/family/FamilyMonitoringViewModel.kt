package com.silverlink.app.ui.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.remote.MedicationData
import com.silverlink.app.data.remote.MedicationLogData
import com.silverlink.app.data.remote.MoodLogData
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.Input
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.data.remote.model.QwenRequest
import com.silverlink.app.data.repository.SyncRepository
import com.silverlink.app.ui.components.ChartType
import com.silverlink.app.ui.components.MedicationStatus
import com.silverlink.app.ui.components.MedicationSummary
import com.silverlink.app.ui.components.MoodTimePoint
import com.silverlink.app.ui.components.TimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

/**
 * 家人端某天的摘要数据（保留兼容）
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
 * 从云端获取已配对长辈的服药和情绪记录（统一UI风格）
 */
class FamilyMonitoringViewModel(application: Application) : AndroidViewModel(application) {
    
    private val syncRepository = SyncRepository.getInstance(application)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }
    
    // 加载状态
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()
    
    // 是否已配对
    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()
    
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

    // 用药统计（周/月/年）
    private val _medicationSummary = MutableStateFlow<MedicationSummary?>(null)
    val medicationSummary: StateFlow<MedicationSummary?> = _medicationSummary.asStateFlow()
    
    // 当前情绪
    private val _currentMood = MutableStateFlow<String?>(null)
    val currentMood: StateFlow<String?> = _currentMood.asStateFlow()
    
    // 最新时间
    private val _latestTime = MutableStateFlow<String?>(null)
    val latestTime: StateFlow<String?> = _latestTime.asStateFlow()
    
    // 选中的情绪点（用于显示详情）
    private val _selectedMoodPoint = MutableStateFlow<MoodTimePoint?>(null)
    val selectedMoodPoint: StateFlow<MoodTimePoint?> = _selectedMoodPoint.asStateFlow()

    // 情绪分析（周/月/年）
    private val _moodAnalysis = MutableStateFlow<String?>(null)
    val moodAnalysis: StateFlow<String?> = _moodAnalysis.asStateFlow()

    // 情绪分析加载状态
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()
    
    // 长辈名字
    private val _elderName = MutableStateFlow("")
    val elderName: StateFlow<String> = _elderName.asStateFlow()
    
    init {
        loadData()
    }
    
    fun setTimeRange(range: TimeRange) {
        _selectedRange.value = range
        _selectedMoodPoint.value = null
        loadData()
    }
    
    fun setSelectedDate(date: Date) {
        _selectedDate.value = date
        _selectedMoodPoint.value = null
        loadData()
    }
    
    fun setChartType(type: ChartType) {
        _chartType.value = type
    }
    
    fun selectMoodPoint(point: MoodTimePoint?) {
        _selectedMoodPoint.value = point
    }
    
    /**
     * 加载数据
     */
    fun loadData() {
        viewModelScope.launch {
            _loadingState.value = LoadingState.Loading
            
            val dateStr = dateFormat.format(_selectedDate.value)
            val (startDate, endDate) = getDateRange()
            
            // 获取服药记录
            val medicationResult = if (_selectedRange.value == TimeRange.DAY) {
                syncRepository.getElderMedicationLogs(date = dateStr)
            } else {
                syncRepository.getElderMedicationLogs(date = null)
            }
            val medicationListResult = syncRepository.getElderMedicationsList()
            // 获取情绪记录（根据时间范围确定天数）
            val rangeDays = when (_selectedRange.value) {
                TimeRange.DAY -> 1
                TimeRange.WEEK -> 7
                TimeRange.MONTH -> 30
                TimeRange.YEAR -> 365
            }
            val todayStr = dateFormat.format(Date())
            val daysForQuery = max(rangeDays, daysBetweenInclusive(startDate, todayStr))
            val moodResult = syncRepository.getElderMoodLogs(days = daysForQuery)
            
            if (medicationResult.isFailure && moodResult.isFailure) {
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
            
            // 处理情绪数据
            val allMoodLogs = moodResult.getOrDefault(emptyList())
            val points = processMoodData(allMoodLogs, startDate, endDate, _selectedRange.value)
            
            // 处理用药数据
            val allMedLogs = medicationResult.getOrDefault(emptyList())
            val allMedications = medicationListResult.getOrDefault(emptyList())
            processMedicationData(allMedLogs, allMedications, startDate, endDate, _selectedRange.value)
            
            // 周/月/年范围：进行情绪备注分析
            if (_selectedRange.value != TimeRange.DAY) {
                analyzeMoodNotes(points, _selectedRange.value)
            } else {
                _moodAnalysis.value = null
            }

            _loadingState.value = LoadingState.Success()
        }
    }
    
    private fun processMoodData(
        moodLogs: List<MoodLogData>,
        startDate: String,
        endDate: String,
        range: TimeRange
    ): List<MoodTimePoint> {
        val filteredLogs = if (range == TimeRange.DAY) {
            moodLogs.filter { it.date == startDate }
        } else {
            moodLogs.filter { it.date >= startDate && it.date <= endDate }
        }
        
        val points = filteredLogs.map { log ->
            val timestamp = parseCreatedAtToMillis(log.createdAt)
            val time = formatTimeFromCreatedAt(log.createdAt, timestamp)
            
            MoodTimePoint(
                time = time,
                mood = log.mood,
                note = log.note,
                timestamp = timestamp ?: 0L
            )
        }.sortedBy { it.timestamp }
        
        _moodPoints.value = points
        
        // 设置当前情绪（最新的）
        val latest = points.lastOrNull()
        _currentMood.value = latest?.mood
        _latestTime.value = latest?.time

        return points
    }

    private fun parseCreatedAtToMillis(raw: String): Long? {
        val numeric = raw.trim().toLongOrNull()
        if (numeric != null) {
            return if (numeric in 1L..9_999_999_999L) numeric * 1000L else numeric
        }

        val patterns = listOf(
            "EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)",
            "EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss"
        )

        patterns.forEach { pattern ->
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
                sdf.isLenient = true
                sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                val date = sdf.parse(raw)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun formatTimeFromCreatedAt(raw: String, millis: Long?): String {
        return try {
            if (raw.contains("GMT+0800") || raw.contains("China Standard Time")) {
                val regex = Regex("\\d{2}:\\d{2}")
                return regex.find(raw)?.value ?: "00:00"
            }

            if (raw.contains("T") && raw.contains("Z")) {
                val match = Regex("T(\\d{2}):(\\d{2})").find(raw)
                if (match != null) {
                    val hour = match.groupValues[1].toIntOrNull() ?: 0
                    val minute = match.groupValues[2].toIntOrNull() ?: 0
                    val converted = (hour + 8) % 24
                    return String.format(Locale.getDefault(), "%02d:%02d", converted, minute)
                }
            }

            if (millis != null && millis > 0) {
                timeFormat.format(Date(millis))
            } else {
                val regex = Regex("\\d{2}:\\d{2}")
                regex.find(raw)?.value ?: "00:00"
            }
        } catch (e: Exception) {
            "00:00"
        }
    }

    private suspend fun analyzeMoodNotes(points: List<MoodTimePoint>, range: TimeRange) {
        val notesByMood = points
            .filter { it.note.isNotBlank() }
            .groupBy { it.mood.uppercase() }

        if (notesByMood.isEmpty()) {
            _moodAnalysis.value = "暂无可分析的情绪备注"
            return
        }

        _isAnalyzing.value = true

        try {
            val rangeLabel = when (range) {
                TimeRange.WEEK -> "本周"
                TimeRange.MONTH -> "本月"
                TimeRange.YEAR -> "本年"
                else -> "近期"
            }

            val notesSection = buildString {
                notesByMood.forEach { (mood, pointsForMood) ->
                    append("情绪: ").append(mood).append("\n")
                    pointsForMood.take(8).forEachIndexed { index, point ->
                        append("  ").append(index + 1).append(". ")
                            .append(point.note.replace("\n", " "))
                            .append("\n")
                    }
                    append("\n")
                }
            }

            val prompt = """
你是一名养老照护助手。请根据以下情绪备注，分析${rangeLabel}老人主要情绪分布的可能原因，并给出简短的关怀建议。
要求：
1) 按情绪分类输出原因分析（比如愉悦/平静/不愉悦/焦虑/生气等）。
2) 每个情绪给出1-2条原因推测（不要涉及诊断）。
3) 最后给出1-2条总体关怀建议。

情绪备注：
$notesSection
""".trimIndent()

            val request = QwenRequest(
                input = Input(
                    messages = listOf(
                        Message(role = "user", content = prompt)
                    )
                )
            )

            val response = RetrofitClient.api.chat(request)
            val content = response.output.choices?.firstOrNull()?.message?.content
                ?: response.output.text
                ?: "暂无分析结果"

            _moodAnalysis.value = content
        } catch (_: Exception) {
            _moodAnalysis.value = "情绪分析暂不可用，请稍后重试。"
        } finally {
            _isAnalyzing.value = false
        }
    }
    
    private fun processMedicationData(
        medLogs: List<MedicationLogData>,
        medications: List<MedicationData>,
        startDate: String,
        endDate: String,
        range: TimeRange
    ) {
        val filteredLogs = if (range == TimeRange.DAY) {
            medLogs.filter { it.date == startDate }
        } else {
            medLogs.filter { it.date >= startDate && it.date <= endDate }
        }
        val logsByMedication = filteredLogs.groupBy { it.medicationName }

        val statusesFromList = medications.map { med ->
            val logs = logsByMedication[med.name] ?: emptyList()
            val takenTimes = logs.filter { it.status == "taken" }.map { it.scheduledTime }.toSet()
            val times = med.times.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            
            MedicationStatus(
                name = med.name,
                dosage = med.dosage,
                times = times,
                takenTimes = takenTimes
            )
        }

        // 兜底：如果列表为空但有日志，仍然显示日志中的药品
        val statusesFromLogs = logsByMedication.map { (name, logs) ->
            val firstLog = logs.first()
            val takenTimes = logs.filter { it.status == "taken" }.map { it.scheduledTime }.toSet()
            val allTimes = logs.map { it.scheduledTime }.distinct()
            
            MedicationStatus(
                name = name,
                dosage = firstLog.dosage,
                times = allTimes,
                takenTimes = takenTimes
            )
        }

        _medicationStatuses.value = if (statusesFromList.isNotEmpty()) {
            statusesFromList
        } else {
            statusesFromLogs
        }

        // 统计
        val daysCount = daysBetweenInclusive(startDate, endDate).coerceAtLeast(1)
        val totalCount = medications.sumOf { med ->
            med.times.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size * daysCount
        }
        val takenCount = filteredLogs.count { it.status == "taken" }

        val takenByMed = filteredLogs
            .filter { it.status == "taken" }
            .groupBy { it.medicationName }
            .mapValues { it.value.size }

        val missedByMedication = medications.mapNotNull { med ->
            val timesCount = med.times.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size
            val expected = timesCount * daysCount
            val taken = takenByMed[med.name] ?: 0
            val missed = (expected - taken).coerceAtLeast(0)
            if (missed > 0) med.name to missed else null
        }

        _medicationSummary.value = MedicationSummary(
            takenCount = takenCount,
            totalCount = totalCount,
            missedByMedication = missedByMedication
        )
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

    private fun daysBetweenInclusive(startDate: String, endDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val start = sdf.parse(startDate) ?: return 1
            val end = sdf.parse(endDate) ?: return 1
            val diff = end.time - start.time
            (diff / (24 * 60 * 60 * 1000L)).toInt() + 1
        } catch (_: Exception) {
            1
        }
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

