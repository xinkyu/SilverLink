package com.silverlink.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.data.local.entity.MedicationLogEntity
import com.silverlink.app.data.local.entity.MoodLogEntity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

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
 * 缓存数据包装类
 */
private data class CachedData<T>(
    val data: T,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlMs: Long): Boolean = System.currentTimeMillis() - timestamp > ttlMs
}

/**
 * 历史记录ViewModel
 * 用于老人端查看服药和情绪历史（统一UI风格）
 * 
 * 性能优化：
 * - 内存缓存：情绪/用药数据缓存5分钟
 * - AI分析缓存：按时间范围缓存分析结果
 * - Stale-while-revalidate：显示缓存数据同时后台刷新
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val historyDao = AppDatabase.getInstance(application).historyDao()
    private val medicationDao = AppDatabase.getInstance(application).medicationDao()
    private val syncRepository = SyncRepository.getInstance(application)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }
    
    // ========== 缓存配置 ==========
    companion object {
        private const val CACHE_TTL_MS = 5 * 60 * 1000L  // 5分钟缓存过期时间
    }
    
    // 云端数据缓存
    private var cachedMoodLogs: CachedData<List<MoodLogData>>? = null
    private var cachedMedicationLogs: CachedData<List<MedicationLogData>>? = null
    private var cachedMedications: CachedData<List<MedicationData>>? = null
    
    // AI 分析结果缓存（按时间范围+日期缓存）
    private val analysisCache = mutableMapOf<String, String>()
    
    // 后台刷新状态（区分首次加载和后台刷新）
    private val _isBackgroundRefreshing = MutableStateFlow(false)
    val isBackgroundRefreshing: StateFlow<Boolean> = _isBackgroundRefreshing.asStateFlow()
    
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
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
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
    
    fun refresh() {
        // 强制刷新：清除缓存
        invalidateCache()
        loadData(forceRefresh = true)
    }
    
    /**
     * 清除所有缓存
     */
    private fun invalidateCache() {
        cachedMoodLogs = null
        cachedMedicationLogs = null
        cachedMedications = null
        analysisCache.clear()
    }
    
    /**
     * 获取缓存键（用于AI分析缓存）
     */
    private fun getAnalysisCacheKey(range: TimeRange, startDate: String): String {
        return "${range.name}_$startDate"
    }
    
    /**
     * 检查缓存是否有效
     */
    private fun hasFreshCache(): Boolean {
        return cachedMoodLogs?.isExpired(CACHE_TTL_MS) == false &&
               cachedMedicationLogs?.isExpired(CACHE_TTL_MS) == false &&
               cachedMedications?.isExpired(CACHE_TTL_MS) == false
    }
    
    private fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val (startDate, endDate) = getDateRange()
            val dateStr = startDate
            val rangeDays = when (_selectedRange.value) {
                TimeRange.DAY -> 1
                TimeRange.WEEK -> 7
                TimeRange.MONTH -> 30
                TimeRange.YEAR -> 365
            }
            val todayStr = dateFormat.format(Date())
            val daysForQuery = max(rangeDays, daysBetweenInclusive(startDate, todayStr))
            
            // 检查是否有有效缓存
            val hasCache = hasFreshCache()
            
            if (hasCache && !forceRefresh) {
                // 使用缓存数据立即渲染，不显示loading
                renderFromCache(startDate, endDate, _selectedRange.value)
                
                // 检查是否需要后台刷新
                val cacheAge = System.currentTimeMillis() - (cachedMoodLogs?.timestamp ?: 0)
                if (cacheAge > CACHE_TTL_MS / 2) {
                    // 缓存超过一半时间，后台刷新
                    _isBackgroundRefreshing.value = true
                    refreshFromCloud(daysForQuery, dateStr, startDate, endDate)
                    _isBackgroundRefreshing.value = false
                }
            } else {
                // 无缓存或强制刷新，显示loading
                _isLoading.value = true
                refreshFromCloud(daysForQuery, dateStr, startDate, endDate)
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 从缓存渲染数据
     */
    private fun renderFromCache(startDate: String, endDate: String, range: TimeRange) {
        val moodLogs = cachedMoodLogs?.data ?: emptyList()
        val medLogs = cachedMedicationLogs?.data ?: emptyList()
        val medications = cachedMedications?.data ?: emptyList()
        
        val points = processMoodData(moodLogs, startDate, endDate, range)
        processMedicationData(medLogs, medications, startDate, endDate, range)
        
        // 检查AI分析缓存
        if (range != TimeRange.DAY) {
            val cacheKey = getAnalysisCacheKey(range, startDate)
            val cachedAnalysis = analysisCache[cacheKey]
            if (cachedAnalysis != null) {
                _moodAnalysis.value = cachedAnalysis
            } else {
                // 无缓存，需要调用AI分析
                viewModelScope.launch {
                    analyzeMoodNotes(points, range, startDate)
                }
            }
        } else {
            _moodAnalysis.value = null
        }
    }
    
    /**
     * 从云端刷新数据并更新缓存
     */
    private suspend fun refreshFromCloud(
        daysForQuery: Int,
        dateStr: String,
        startDate: String,
        endDate: String
    ) {
        val range = _selectedRange.value
        
        // 从云端拉取数据
        val moodResult = syncRepository.getSelfMoodLogs(days = daysForQuery)
        val medicationResult = if (range == TimeRange.DAY) {
            syncRepository.getSelfMedicationLogs(date = dateStr)
        } else {
            syncRepository.getSelfMedicationLogs(date = null)
        }
        val medicationListResult = syncRepository.getSelfMedicationsList()

        // 更新缓存
        val moodLogs = moodResult.getOrDefault(emptyList())
        val medLogs = medicationResult.getOrDefault(emptyList())
        val medications = medicationListResult.getOrDefault(emptyList())
        
        cachedMoodLogs = CachedData(moodLogs)
        cachedMedicationLogs = CachedData(medLogs)
        cachedMedications = CachedData(medications)

        // 处理数据
        val points = processMoodData(moodLogs, startDate, endDate, range)
        processMedicationData(medLogs, medications, startDate, endDate, range)

        // 周/月/年范围：进行情绪备注分析
        if (range != TimeRange.DAY) {
            val cacheKey = getAnalysisCacheKey(range, startDate)
            if (!analysisCache.containsKey(cacheKey)) {
                analyzeMoodNotes(points, range, startDate)
            }
        } else {
            _moodAnalysis.value = null
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

        val latest = points.lastOrNull()
        _currentMood.value = latest?.mood
        _latestTime.value = latest?.time

        return points
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
        } catch (_: Exception) {
            "00:00"
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

    private suspend fun analyzeMoodNotes(points: List<MoodTimePoint>, range: TimeRange, startDate: String? = null) {
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
            
            // 缓存分析结果
            if (startDate != null) {
                val cacheKey = getAnalysisCacheKey(range, startDate)
                analysisCache[cacheKey] = content
            }
        } catch (e: Exception) {
            _moodAnalysis.value = "情绪分析暂不可用，请稍后重试。"
        } finally {
            _isAnalyzing.value = false
        }
    }
}
