package com.silverlink.app.ui.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserRole
import com.silverlink.app.data.remote.AlertData
import com.silverlink.app.data.remote.MedicationData
import com.silverlink.app.data.remote.MedicationLogData
import com.silverlink.app.data.remote.MoodLogData
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.Input
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.data.remote.model.QwenRequest
import com.silverlink.app.data.repository.SyncRepository
import com.silverlink.app.ui.components.ChartType
import com.silverlink.app.ui.components.LocationCard
import com.silverlink.app.ui.components.MedicationStatus
import com.silverlink.app.data.remote.LocationData
import com.silverlink.app.data.remote.LocationQueryResult
import com.silverlink.app.ui.components.MedicationSummary
import com.silverlink.app.ui.components.MoodTimePoint
import com.silverlink.app.ui.components.TimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
 * 缓存数据包装类
 */
private data class CachedData<T>(
    val data: T,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlMs: Long): Boolean = System.currentTimeMillis() - timestamp > ttlMs
}

/**
 * 家人端监控ViewModel
 * 从云端获取已配对长辈的服药和情绪记录（统一UI风格）
 * 
 * 性能优化：
 * - 内存缓存：情绪/用药数据缓存5分钟
 * - AI分析缓存：按时间范围缓存分析结果
 * - Stale-while-revalidate：显示缓存数据同时后台刷新
 */
class FamilyMonitoringViewModel(application: Application) : AndroidViewModel(application) {
    
    private val syncRepository = SyncRepository.getInstance(application)
    private val userPreferences = UserPreferences.getInstance(application)
    val isFamilyRole: Boolean = userPreferences.userConfig.value.role == UserRole.FAMILY
    val showLocationSection: Boolean = isFamilyRole
    val showAlertsSection: Boolean = isFamilyRole
    val pageTitle: String = if (isFamilyRole) "长辈健康" else "健康概览"
    val heroTitlePrefix: String = if (isFamilyRole) "长辈" else "今日"
    val medicationDialogTitle: String = if (isFamilyRole) "为长辈添加药品" else "添加药品"
    val medicationDialogSubtitle: String = if (isFamilyRole) "添加的药品将同步到长辈设备" else "保存后会同步到当前设备"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
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
    private val overviewAnalysisCache = mutableMapOf<String, String>()
    
    // 后台刷新状态
    private val _isBackgroundRefreshing = MutableStateFlow(false)
    val isBackgroundRefreshing: StateFlow<Boolean> = _isBackgroundRefreshing.asStateFlow()
    
    // 加载状态
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()
    
    // 是否已配对
    private val _isPaired = MutableStateFlow(!isFamilyRole)
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

    private val _overviewAnalysis = MutableStateFlow<String?>(null)
    val overviewAnalysis: StateFlow<String?> = _overviewAnalysis.asStateFlow()

    private val _isOverviewAnalyzing = MutableStateFlow(false)
    val isOverviewAnalyzing: StateFlow<Boolean> = _isOverviewAnalyzing.asStateFlow()
    
    // 长辈名字
    private val _elderName = MutableStateFlow("")
    val elderName: StateFlow<String> = _elderName.asStateFlow()
    
    // 警报列表
    private val _alerts = MutableStateFlow<List<AlertData>>(emptyList())
    val alerts: StateFlow<List<AlertData>> = _alerts.asStateFlow()
    
    // 认知评估报告
    private val _cognitiveReport = MutableStateFlow<com.silverlink.app.data.remote.CognitiveReportData?>(null)
    val cognitiveReport: StateFlow<com.silverlink.app.data.remote.CognitiveReportData?> = _cognitiveReport.asStateFlow()
    
    // 认知报告加载状态
    private val _isCognitiveLoading = MutableStateFlow(false)
    val isCognitiveLoading: StateFlow<Boolean> = _isCognitiveLoading.asStateFlow()

    // 认知评估 AI 分析
    private val _cognitiveAnalysis = MutableStateFlow<String?>(null)
    val cognitiveAnalysis: StateFlow<String?> = _cognitiveAnalysis.asStateFlow()

    // 认知评估 AI 分析加载状态
    private val _isCognitiveAnalyzing = MutableStateFlow(false)
    val isCognitiveAnalyzing: StateFlow<Boolean> = _isCognitiveAnalyzing.asStateFlow()

    private var lastCognitiveAnalysisKey: String? = null
    
    // 警报轮询Job
    private var alertPollingJob: kotlinx.coroutines.Job? = null
    
    // 位置相关状态
    private val _elderLocation = MutableStateFlow<LocationData?>(null)
    val elderLocation: StateFlow<LocationData?> = _elderLocation.asStateFlow()
    
    private val _locationHistory = MutableStateFlow<List<LocationData>>(emptyList())
    val locationHistory: StateFlow<List<LocationData>> = _locationHistory.asStateFlow()
    
    private val _isLocationLoading = MutableStateFlow(false)
    val isLocationLoading: StateFlow<Boolean> = _isLocationLoading.asStateFlow()
    
    // 地址解析结果
    private val _currentAddress = MutableStateFlow<String?>(null)
    val currentAddress: StateFlow<String?> = _currentAddress.asStateFlow()
    
    init {
        loadData()
        if (isFamilyRole) {
            startAlertPolling()
        }
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
     * 开始轮询警报（60秒一次）
     */
    private fun startAlertPolling() {
        if (!isFamilyRole) return
        alertPollingJob?.cancel()
        alertPollingJob = viewModelScope.launch {
            while (true) {
                fetchAlerts()
                fetchLocation() // 同时也轮询位置
                delay(30_000L) // 改为30秒检查一次（位置需要更频繁）
            }
        }
    }
    
    /**
     * 获取长辈位置
     */
    private suspend fun fetchLocation() {
        if (!isFamilyRole || !isPaired.value) return
        
        try {
            // 直接调用 Repository 方法，它会在内部处理配对ID的获取
            val result = syncRepository.getElderLocation()
            
            if (result.isSuccess) {
                val data = result.getOrNull()
                if (data != null) {
                    _elderLocation.value = data.latest
                    _locationHistory.value = data.history
                    
                    if (data.latest != null && !data.latest.address.isNullOrEmpty()) {
                        _currentAddress.value = data.latest.address
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FamilyMonitoringViewModel", "获取位置失败: ${e.message}")
        }
    }

    /**
     * 刷新位置（UI调用）
     */
    fun refreshLocation() {
        viewModelScope.launch {
            _isLocationLoading.value = true
            fetchLocation()
            _isLocationLoading.value = false
        }
    }
    
    /**
     * 获取未读警报
     */
    private suspend fun fetchAlerts() {
        if (!isFamilyRole) return
        try {
            val result = syncRepository.getAlerts(unreadOnly = true)
            if (result.isSuccess) {
                _alerts.value = result.getOrDefault(emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("FamilyMonitoringViewModel", "获取警报失败: ${e.message}")
        }
    }
    
    /**
     * 关闭/忽略警报
     */
    fun dismissAlert(alertId: String) {
        viewModelScope.launch {
            try {
                val result = syncRepository.dismissAlert(alertId)
                if (result.isSuccess) {
                    // 从列表中移除
                    _alerts.value = _alerts.value.filter { it.id != alertId }
                }
            } catch (e: Exception) {
                android.util.Log.e("FamilyMonitoringViewModel", "关闭警报失败: ${e.message}")
            }
        }
    }
    
    /**
     * 清除所有缓存
     */
    private fun invalidateCache() {
        cachedMoodLogs = null
        cachedMedicationLogs = null
        cachedMedications = null
        analysisCache.clear()
        overviewAnalysisCache.clear()
    }
    
    /**
     * 获取缓存键（用于AI分析缓存）
     */
    private fun getAnalysisCacheKey(range: TimeRange, startDate: String): String {
        return "${range.name}_$startDate"
    }

    private fun daysForSelectedRange(): Int {
        return when (_selectedRange.value) {
            TimeRange.DAY -> 1
            TimeRange.WEEK -> 7
            TimeRange.MONTH -> 30
            TimeRange.YEAR -> 365
        }
    }

    private fun rangeLabel(range: TimeRange): String {
        return when (range) {
            TimeRange.DAY -> "今天"
            TimeRange.WEEK -> "本周"
            TimeRange.MONTH -> "本月"
            TimeRange.YEAR -> "今年"
        }
    }

    private fun analysisSubjectInstruction(): String {
        return if (isFamilyRole) {
            val elderName = userPreferences.userConfig.value.elderName.trim().ifBlank { "长辈" }
            "全文使用“$elderName”称呼长辈，内容要先说明${elderName}当前情况与可能原因，再给家人具体可执行的陪伴建议，不使用“老人/老年人/长者”等词。"
        } else {
            "全文使用第二人称“您”表达，可适当使用“您可能”“您的情况”这样的说法，不使用“老人/老年人/长者”等词。"
        }
    }
    
    /**
     * 启动位置轮询（由 UI 调用）
     */
    fun startLocationPolling() {
        if (!isFamilyRole) return
        // 实际上轮询已经在 startAlertPolling 中处理了
        // 这里我们可以立即触发一次刷新
        refreshLocation()
    }
    
    /**
     * 检查缓存是否有效
     */
    private fun hasFreshCache(): Boolean {
        return cachedMoodLogs?.isExpired(CACHE_TTL_MS) == false &&
               cachedMedicationLogs?.isExpired(CACHE_TTL_MS) == false &&
               cachedMedications?.isExpired(CACHE_TTL_MS) == false
    }
    
    /**
     * 加载数据
     */
    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val dateStr = dateFormat.format(_selectedDate.value)
            val (startDate, endDate) = getDateRange()
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
                _loadingState.value = LoadingState.Loading
                refreshFromCloud(daysForQuery, dateStr, startDate, endDate)
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

        val overviewCacheKey = "overview_${getAnalysisCacheKey(range, startDate)}"
        val cachedOverview = overviewAnalysisCache[overviewCacheKey]
        if (cachedOverview != null) {
            _overviewAnalysis.value = cachedOverview
        } else {
            viewModelScope.launch {
                fetchCognitiveReport(daysForSelectedRange())
                analyzeOverview(points, range, startDate)
            }
        }
        
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
        
        _loadingState.value = LoadingState.Success()
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

        val medicationResult = if (isFamilyRole) {
            if (range == TimeRange.DAY) syncRepository.getElderMedicationLogs(date = dateStr)
            else syncRepository.getElderMedicationLogs(date = null)
        } else {
            if (range == TimeRange.DAY) syncRepository.getSelfMedicationLogs(date = dateStr)
            else syncRepository.getSelfMedicationLogs(date = null)
        }
        val medicationListResult = if (isFamilyRole) {
            syncRepository.getElderMedicationsList()
        } else {
            syncRepository.getSelfMedicationsList()
        }
        val moodResult = if (isFamilyRole) {
            syncRepository.getElderMoodLogs(days = daysForQuery)
        } else {
            syncRepository.getSelfMoodLogs(days = daysForQuery)
        }

        if (isFamilyRole && medicationResult.isFailure && moodResult.isFailure) {
            val errorMsg = medicationResult.exceptionOrNull()?.message ?: "未知错误"
            if (errorMsg.contains("未找到已配对")) {
                _isPaired.value = false
                _loadingState.value = LoadingState.Error("未找到已配对的长辈")
            } else {
                _loadingState.value = LoadingState.Error(errorMsg)
            }
            return
        }
        
        _isPaired.value = true
        
        // 更新缓存
        val allMoodLogs = moodResult.getOrDefault(emptyList())
        val allMedLogs = medicationResult.getOrDefault(emptyList())
        val allMedications = medicationListResult.getOrDefault(emptyList())
        
        cachedMoodLogs = CachedData(allMoodLogs)
        cachedMedicationLogs = CachedData(allMedLogs)
        cachedMedications = CachedData(allMedications)
        
        // 处理情绪数据
        val points = processMoodData(allMoodLogs, startDate, endDate, range)
        
        // 处理用药数据
        processMedicationData(allMedLogs, allMedications, startDate, endDate, range)
        
        // 周/月/年范围：进行情绪备注分析
        if (range != TimeRange.DAY) {
            val cacheKey = getAnalysisCacheKey(range, startDate)
            if (!analysisCache.containsKey(cacheKey)) {
                analyzeMoodNotes(points, range, startDate)
            }
        } else {
            _moodAnalysis.value = null
        }
        
        // 加载认知评估报告
        fetchCognitiveReport(daysForSelectedRange())
        analyzeOverview(points, range, startDate)

        _loadingState.value = LoadingState.Success()
    }
    
    /**
     * 获取认知评估报告
     */
    private suspend fun fetchCognitiveReport(days: Int) {
        _isCognitiveLoading.value = true
        try {
            val result = if (isFamilyRole) {
                syncRepository.getCognitiveReport(days = days)
            } else {
                syncRepository.getSelfCognitiveReport(days = days)
            }
            result.onSuccess { report ->
                _cognitiveReport.value = report
                if (report != null && report.totalQuestions > 0) {
                    val key = "${report.startDate}|${report.endDate}|${report.totalQuestions}|${report.correctAnswers}|${report.averageResponseTimeMs}|${report.trend}"
                    if (lastCognitiveAnalysisKey != key) {
                        analyzeCognitiveReport(report)
                        lastCognitiveAnalysisKey = key
                    }
                } else {
                    _cognitiveAnalysis.value = null
                }
            }.onFailure { e ->
                android.util.Log.e("FamilyMonitoringViewModel", "获取认知报告失败: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("FamilyMonitoringViewModel", "获取认知报告异常: ${e.message}")
        } finally {
            _isCognitiveLoading.value = false
        }
    }

    private suspend fun analyzeCognitiveReport(report: com.silverlink.app.data.remote.CognitiveReportData) {
        if (RetrofitClient.getApiKey().isBlank()) {
            _cognitiveAnalysis.value = "AI 功能未配置，请在 local.properties 填写 QWEN_API_KEY"
            return
        }

        _isCognitiveAnalyzing.value = true

        try {
            val elderName = userPreferences.userConfig.value.elderName.trim().ifBlank { "长辈" }
            val ratePercent = (report.correctRate * 100).toInt()
            val trendText = when (report.trend) {
                "improving" -> "进步中"
                "declining" -> "需关注"
                else -> "保持稳定"
            }

            val prompt = if (isFamilyRole) """
你是一名养老照护助手。请基于以下认知评估统计，给出简短分析与建议。
要求：
1) 用1-2句总结${elderName}最近的认知表现（正确率/平均用时/趋势），主语是${elderName}。
2) 给出1-2条明确写给家人的日常关怀与训练建议，可直接执行，使用“建议家人/家属……”这类表达。
3) 避免诊断，语气简洁温和。
4) 全文围绕${elderName}展开，不使用第二人称“您”。

统计数据：
- 时间范围：${report.startDate} ~ ${report.endDate}
- 总题数：${report.totalQuestions}
- 答对：${report.correctAnswers}
- 正确率：${ratePercent}%
- 平均用时：${report.averageResponseTimeMs / 1000}秒
- 趋势：$trendText
""".trimIndent() else """
你是一名养老照护助手。请基于以下认知评估统计，给出面向本人的简短说明与建议。
要求：
1) 用1-2句总结您最近的认知表现。
2) 给出1-2条可直接执行的训练或生活建议（避免诊断）。
3) ${analysisSubjectInstruction()}

统计数据：
- 时间范围：${report.startDate} ~ ${report.endDate}
- 总题数：${report.totalQuestions}
- 答对：${report.correctAnswers}
- 正确率：${ratePercent}%
- 平均用时：${report.averageResponseTimeMs / 1000}秒
- 趋势：$trendText
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

            _cognitiveAnalysis.value = content
        } catch (e: Exception) {
            android.util.Log.e("FamilyMonitoringViewModel", "认知分析失败: ${e.message}")
            _cognitiveAnalysis.value = "认知分析暂不可用，请稍后重试。"
        } finally {
            _isCognitiveAnalyzing.value = false
        }
    }

    private suspend fun analyzeOverview(
        points: List<MoodTimePoint>,
        range: TimeRange,
        startDate: String
    ) {
        if (RetrofitClient.getApiKey().isBlank()) {
            _overviewAnalysis.value = "AI 功能未配置，请在 local.properties 填写 QWEN_API_KEY"
            return
        }

        val cacheKey = "overview_${getAnalysisCacheKey(range, startDate)}"
        overviewAnalysisCache[cacheKey]?.let {
            _overviewAnalysis.value = it
            return
        }

        _isOverviewAnalyzing.value = true
        try {
            val moodSummary = if (points.isEmpty()) {
                "暂无情绪记录"
            } else {
                points.groupBy { it.mood.uppercase() }
                    .entries
                    .sortedByDescending { it.value.size }
                    .joinToString(separator = "；") { (mood, logs) ->
                        "${mood}:${logs.size}次"
                    }
            }
            val medicationSummaryText = medicationSummary.value?.let {
                "已完成 ${it.takenCount}/${it.totalCount} 次；漏服重点: ${
                    it.missedByMedication.take(3).joinToString("；") { pair -> "${pair.first}${pair.second}次" }.ifBlank { "暂无" }
                }"
            } ?: "暂无用药记录"
            val cognitiveSummaryText = cognitiveReport.value?.let {
                "答对 ${it.correctAnswers}/${it.totalQuestions} 题；正确率 ${(it.correctRate * 100).toInt()}%；平均用时 ${it.averageResponseTimeMs / 1000} 秒；趋势 ${it.trend}"
            } ?: "暂无认知评估记录"
            val latestMoodText = currentMood.value?.let { "最近情绪 ${it}" } ?: "最近情绪暂无"
            val prompt = """
你是一名养老照护助手。请根据以下${rangeLabel(range)}健康概览数据，输出一段简短说明和建议。
要求：
1) 先用1-2句总结${rangeLabel(range)}整体情况。
2) 再给出2-3条简短建议，避免诊断。
3) ${analysisSubjectInstruction()}
4) 语言自然，不要分很多小标题。

数据：
- ${latestMoodText}
- 情绪汇总：$moodSummary
- 用药汇总：$medicationSummaryText
- 认知评估：$cognitiveSummaryText
""".trimIndent()

            val request = QwenRequest(
                input = Input(messages = listOf(Message(role = "user", content = prompt)))
            )
            val response = RetrofitClient.api.chat(request)
            val content = response.output.choices?.firstOrNull()?.message?.content
                ?: response.output.text
                ?: "暂无概览分析结果"
            _overviewAnalysis.value = content
            overviewAnalysisCache[cacheKey] = content
        } catch (e: Exception) {
            android.util.Log.e("FamilyMonitoringViewModel", "概览分析失败: ${e.message}")
            _overviewAnalysis.value = "概览分析暂不可用，请稍后重试。"
        } finally {
            _isOverviewAnalyzing.value = false
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

    private suspend fun analyzeMoodNotes(points: List<MoodTimePoint>, range: TimeRange, startDate: String? = null) {
        val notesByMood = points
            .filter { it.note.isNotBlank() }
            .groupBy { it.mood.uppercase() }

        if (notesByMood.isEmpty()) {
            _moodAnalysis.value = "暂无可分析的情绪备注"
            return
        }

        if (RetrofitClient.getApiKey().isBlank()) {
            _moodAnalysis.value = "AI 功能未配置，请在 local.properties 填写 QWEN_API_KEY"
            return
        }

        _isAnalyzing.value = true

        try {
            val elderName = userPreferences.userConfig.value.elderName.trim().ifBlank { "长辈" }
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

            val prompt = if (isFamilyRole) """
你是一名养老照护助手。请根据以下情绪备注，向家人说明${rangeLabel}${elderName}的情绪情况、可能原因，并给出简短的关怀建议。
要求：
1) 先概括${elderName}这段时间的整体情绪状态，像在对家人做情况说明。
2) 按情绪分类说明可能原因（比如愉悦/平静/不愉快/焦虑/生气等），原因推测不要涉及诊断。
3) 如果备注里能看出思念家人、孤独、作息变化、身体不适或事件触发，要明确说出来。
4) 最后给出1-3条面向家人可执行的建议，风格类似“你们可以多陪她聊聊近况”“可以主动安排视频通话”。
5) 语言自然，有温度，不要写成医疗报告。
6) ${analysisSubjectInstruction()}

情绪备注：
$notesSection
""".trimIndent() else """
你是一名养老照护助手。请根据以下情绪备注，分析${rangeLabel}您主要情绪分布的可能原因，并给出简短的关怀建议。
要求：
1) 按情绪分类输出原因分析（比如愉悦/平静/不愉快/焦虑/生气等）。
2) 每个情绪给出1-2条原因推测（不要涉及诊断）。
3) 最后给出1-2条总体建议，面向本人可执行。
4) ${analysisSubjectInstruction()}

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
            android.util.Log.e("FamilyMonitoringViewModel", "情绪分析失败: ${e.message}")
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
        val mergedMedications = mergeMedicationsByNameAndDosage(medications)
        val filteredLogs = if (range == TimeRange.DAY) {
            medLogs.filter { it.date == startDate }
        } else {
            medLogs.filter { it.date >= startDate && it.date <= endDate }
        }
        val logsByMedication = filteredLogs.groupBy { it.medicationName }

        // 用于统计的去重（同一天同一时间点同一药品多条记录只计一次）
        val uniqueLogsForStats = filteredLogs.distinctBy { log ->
            "${log.date}|${log.medicationName}|${log.scheduledTime}|${log.status}"
        }

        val statusesFromList = mergedMedications.map { med ->
            val logs = logsByMedication[med.name] ?: emptyList()
            val times = med.times.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val timeSet = times.toSet()
            val takenTimes = logs
                .filter { it.status == "taken" }
                .map { it.scheduledTime }
                .filter { it in timeSet }
                .toSet()
            
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
        val totalCount = mergedMedications.sumOf { med ->
            med.times.split(",").map { it.trim() }.filter { it.isNotEmpty() }.size * daysCount
        }
        val takenCount = uniqueLogsForStats.count { it.status == "taken" }

        val takenByMed = uniqueLogsForStats
            .filter { it.status == "taken" }
            .groupBy { it.medicationName }
            .mapValues { it.value.size }

        val missedByMedication = mergedMedications.mapNotNull { med ->
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

    private fun normalizeTimes(raw: String): List<String> {
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun mergeMedicationsByNameAndDosage(
        medications: List<MedicationData>
    ): List<MedicationData> {
        return medications
            .groupBy { "${it.name.trim()}__${it.dosage.trim()}" }
            .map { (_, group) ->
                val first = group.first()
                val mergedTimes = group
                    .flatMap { normalizeTimes(it.times) }
                    .distinct()
                    .sorted()
                    .joinToString(",")

                first.copy(
                    name = first.name.trim(),
                    dosage = first.dosage.trim(),
                    times = mergedTimes
                )
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
        // 强制刷新：清除缓存
        invalidateCache()
        loadData(forceRefresh = true)
        
        // 立即刷新位置
        if (isFamilyRole) {
            viewModelScope.launch {
                fetchLocation()
            }
        }
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

            val normalizedTimes = normalizeTimes(times)

            // 先检查是否已存在同名同剂量药品，存在则合并时间并更新
            val existing = (if (isFamilyRole) {
                syncRepository.getElderMedicationsList()
            } else {
                syncRepository.getSelfMedicationsList()
            })
                .getOrDefault(emptyList())
                .firstOrNull {
                    it.name.trim() == name.trim() && it.dosage.trim() == dosage.trim()
                }

            val result = if (existing != null) {
                val mergedTimes = (normalizeTimes(existing.times) + normalizedTimes)
                    .distinct()
                    .sorted()
                    .joinToString(",")

                if (isFamilyRole) {
                    syncRepository.updateMedicationTimesForPairedElder(
                        name = name,
                        dosage = dosage,
                        times = mergedTimes
                    )
                } else {
                    syncRepository.updateMedicationTimesForElder(
                        name = name,
                        dosage = dosage,
                        times = mergedTimes
                    )
                }
            } else {
                if (isFamilyRole) {
                    syncRepository.addMedicationForElder(
                        name = name,
                        dosage = dosage,
                        times = normalizedTimes.joinToString(",")
                    )
                } else {
                    syncRepository.addMedicationForSelf(
                        name = name,
                        dosage = dosage,
                        times = normalizedTimes.joinToString(",")
                    )
                }
            }

            if (result.isSuccess) {
                _addMedicationState.value = LoadingState.Success("保存成功")
                // 刷新数据
                loadData()
            } else {
                _addMedicationState.value = LoadingState.Error(
                    result.exceptionOrNull()?.message ?: "保存失败"
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
    
    override fun onCleared() {
        super.onCleared()
        alertPollingJob?.cancel()
    }
}

