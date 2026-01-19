package com.silverlink.app.ui.memory

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.remote.CloudBaseService
import com.silverlink.app.data.remote.MemoryPhotoData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "MemoryGalleryVM"

/**
 * 老人端记忆画廊 ViewModel
 */
class MemoryGalleryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _photos = MutableStateFlow<List<MemoryPhotoData>>(emptyList())
    val photos: StateFlow<List<MemoryPhotoData>> = _photos.asStateFlow()
    
    private val _currentPhotoIndex = MutableStateFlow(0)
    val currentPhotoIndex: StateFlow<Int> = _currentPhotoIndex.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // 同步获取设备 ID，确保在 loadPhotos 调用前就已就绪
    private val elderDeviceId: String = android.provider.Settings.Secure.getString(
        application.contentResolver,
        android.provider.Settings.Secure.ANDROID_ID
    )
    
    init {
        Log.d(TAG, "初始化，elderDeviceId=$elderDeviceId")
    }
    
    /**
     * 加载照片列表
     */
    fun loadPhotos() {
        Log.d(TAG, "开始加载照片，elderDeviceId=$elderDeviceId")
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = CloudBaseService.getMemoryPhotos(
                    elderDeviceId = elderDeviceId,
                    pageSize = 100  // 加载更多照片
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
     * 搜索照片（通过语音查询）
     */
    fun searchPhotos(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = CloudBaseService.searchMemoryPhotos(
                    elderDeviceId = elderDeviceId,
                    query = query
                )
                result.onSuccess { photoList ->
                    if (photoList.isNotEmpty()) {
                        _photos.value = photoList
                        _currentPhotoIndex.value = 0
                        Log.d(TAG, "搜索到 ${photoList.size} 张照片")
                    } else {
                        _errorMessage.value = "没有找到相关照片"
                    }
                }.onFailure { e ->
                    Log.e(TAG, "搜索照片失败", e)
                    _errorMessage.value = "搜索失败: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 下一张照片
     */
    fun nextPhoto() {
        if (_currentPhotoIndex.value < _photos.value.size - 1) {
            _currentPhotoIndex.value++
        }
    }
    
    /**
     * 上一张照片
     */
    fun previousPhoto() {
        if (_currentPhotoIndex.value > 0) {
            _currentPhotoIndex.value--
        }
    }
    
    /**
     * 跳转到指定照片
     */
    fun goToPhoto(index: Int) {
        if (index in 0 until _photos.value.size) {
            _currentPhotoIndex.value = index
        }
    }
    
    /**
     * 获取当前照片
     */
    fun getCurrentPhoto(): MemoryPhotoData? {
        return _photos.value.getOrNull(_currentPhotoIndex.value)
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
