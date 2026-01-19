package com.silverlink.app.ui.memory

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.remote.CloudBaseService
import com.silverlink.app.data.remote.MemoryPhotoData
import com.silverlink.app.feature.memory.ImageUtils
import com.silverlink.app.feature.memory.PhotoAnalysisService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "MemoryLibraryVM"

/**
 * 上传状态
 */
sealed class UploadState {
    object Idle : UploadState()
    object Analyzing : UploadState()      // AI 分析中
    object Uploading : UploadState()      // 上传中
    object Success : UploadState()        // 上传成功
    data class Error(val message: String) : UploadState()
}

/**
 * 家人端记忆库 ViewModel
 */
class MemoryLibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val photoAnalysisService = PhotoAnalysisService()
    
    private val _photos = MutableStateFlow<List<MemoryPhotoData>>(emptyList())
    val photos: StateFlow<List<MemoryPhotoData>> = _photos.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // AI 分析结果缓存
    private val _aiAnalysisResult = MutableStateFlow<PhotoAnalysisResult?>(null)
    val aiAnalysisResult: StateFlow<PhotoAnalysisResult?> = _aiAnalysisResult.asStateFlow()
    
    private var elderDeviceId: String? = null
    
    // 同步获取家人设备 ID
    private val familyDeviceId: String = android.provider.Settings.Secure.getString(
        application.contentResolver,
        android.provider.Settings.Secure.ANDROID_ID
    )
    
    private var isInitialized = false
    
    init {
        Log.d(TAG, "初始化，familyDeviceId=$familyDeviceId")
        // 自动初始化并加载照片
        viewModelScope.launch {
            ensureInitialized()
            loadPhotos()
        }
    }
    
    /**
     * 确保已获取配对的长辈设备 ID
     */
    private suspend fun ensureInitialized() {
        if (isInitialized) return
        
        try {
            elderDeviceId = CloudBaseService.getPairedElderDeviceId(familyDeviceId).getOrNull()
            Log.d(TAG, "获取配对长辈 ID: elderDeviceId=$elderDeviceId")
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "获取配对长辈 ID 失败", e)
        }
    }
    
    /**
     * 加载照片列表
     */
    fun loadPhotos() {
        val elderId = elderDeviceId
        if (elderId == null) {
            Log.w(TAG, "未配对，无法加载照片")
            return
        }
        
        Log.d(TAG, "开始加载照片，elderDeviceId=$elderId, familyDeviceId=$familyDeviceId")
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = CloudBaseService.getMemoryPhotos(
                    elderDeviceId = elderId,
                    familyDeviceId = familyDeviceId
                )
                result.onSuccess { photoList ->
                    _photos.value = photoList
                    Log.d(TAG, "加载了 ${photoList.size} 张照片")
                    if (photoList.isNotEmpty()) {
                        Log.d(TAG, "第一张照片 URL: ${photoList.first().imageUrl.take(100)}")
                    }
                }.onFailure { e ->
                    Log.e(TAG, "加载照片失败", e)
                    _errorMessage.value = "加载照片失败: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 分析照片（AI 生成描述）
     */
    fun analyzePhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Analyzing
            try {
                val result = photoAnalysisService.analyzePhoto(bitmap)
                result.onSuccess { analysis ->
                    _aiAnalysisResult.value = PhotoAnalysisResult(
                        description = analysis.description,
                        people = analysis.people,
                        location = analysis.location,
                        tags = analysis.tags
                    )
                    _uploadState.value = UploadState.Idle
                    Log.d(TAG, "AI 分析完成: ${analysis.description.take(50)}")
                }.onFailure { e ->
                    Log.e(TAG, "AI 分析失败", e)
                    _uploadState.value = UploadState.Idle
                    // 分析失败不影响上传，用户可以手动录入
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI 分析异常", e)
                _uploadState.value = UploadState.Idle
            }
        }
    }
    
    /**
     * 上传照片（直传 COS，支持高清大图）
     */
    fun uploadPhoto(
        bitmap: Bitmap,
        description: String,
        people: String,
        location: String,
        takenDate: String
    ) {
        val elderId = elderDeviceId
        val familyId = familyDeviceId
        
        if (elderId == null || familyId == null) {
            _uploadState.value = UploadState.Error("未配对，无法上传照片")
            return
        }
        
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            try {
                // 将 Bitmap 转换为 JPEG 字节数组（智能压缩：小图不压缩，大图适当压缩）
                val imageBytes = ImageUtils.bitmapToJpegBytes(bitmap)
                Log.d(TAG, "图片大小: ${imageBytes.size / 1024}KB")

                // 获取 AI 描述（如果有）
                val aiDesc = _aiAnalysisResult.value?.description ?: ""
                val tags = _aiAnalysisResult.value?.tags?.joinToString(",") ?: ""
                
                // 使用直传 COS 方式上传
                val result = CloudBaseService.uploadPhotoDirectToCOS(
                    elderDeviceId = elderId,
                    familyDeviceId = familyId,
                    imageBytes = imageBytes,
                    description = description,
                    aiDescription = aiDesc,
                    takenDate = takenDate.ifBlank { null },
                    location = location.ifBlank { null },
                    people = people.ifBlank { null },
                    tags = tags.ifBlank { null }
                )
                
                result.onSuccess {
                    Log.d(TAG, "照片上传成功: ${it.id}")
                    _uploadState.value = UploadState.Success
                    _aiAnalysisResult.value = null
                    // 刷新照片列表
                    loadPhotos()
                }.onFailure { e ->
                    Log.e(TAG, "照片上传失败", e)
                    _uploadState.value = UploadState.Error(e.message ?: "上传失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "照片上传异常", e)
                _uploadState.value = UploadState.Error(e.message ?: "上传失败")
            }
        }
    }
    
    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
        _aiAnalysisResult.value = null
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}

/**
 * AI 分析结果
 */
data class PhotoAnalysisResult(
    val description: String,
    val people: List<String>,
    val location: String?,
    val tags: List<String>
)
