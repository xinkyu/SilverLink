package com.silverlink.app.ui.falldetection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.entity.EmergencyContact
import com.silverlink.app.feature.falldetection.FallDetectionManager
import com.silverlink.app.feature.falldetection.FallDetectionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 跌倒检测功能的ViewModel
 */
class FallDetectionViewModel(application: Application) : AndroidViewModel(application) {

    private val emergencyContactDao = SilverLinkApp.database.emergencyContactDao()
    private val userPrefs = UserPreferences.getInstance(application)
    private val fallDetectionManager = FallDetectionManager.getInstance(application)

    // 紧急联系人列表
    val emergencyContacts = emergencyContactDao.getAllContacts()

    // 跌倒检测开关状态
    private val _isDetectionEnabled = MutableStateFlow(userPrefs.isFallDetectionEnabled())
    val isDetectionEnabled: StateFlow<Boolean> = _isDetectionEnabled.asStateFlow()

    // 服务运行状态
    private val _isServiceRunning = MutableStateFlow(FallDetectionService.isRunning)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // 编辑中的联系人
    private val _editingContact = MutableStateFlow<EmergencyContact?>(null)
    val editingContact: StateFlow<EmergencyContact?> = _editingContact.asStateFlow()

    // 显示添加联系人对话框
    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    // 权限状态
    private val _hasRequiredPermissions = MutableStateFlow(fallDetectionManager.hasRequiredPermissions())
    val hasRequiredPermissions: StateFlow<Boolean> = _hasRequiredPermissions.asStateFlow()

    /**
     * 切换跌倒检测状态
     */
    fun toggleDetection() {
        val newState = fallDetectionManager.toggleDetection()
        _isDetectionEnabled.value = newState
        _isServiceRunning.value = FallDetectionService.isRunning
    }

    /**
     * 启用跌倒检测
     */
    fun enableDetection() {
        if (fallDetectionManager.startDetection()) {
            _isDetectionEnabled.value = true
            _isServiceRunning.value = true
        }
    }

    /**
     * 禁用跌倒检测
     */
    fun disableDetection() {
        fallDetectionManager.stopDetection()
        _isDetectionEnabled.value = false
        _isServiceRunning.value = false
    }

    /**
     * 刷新服务状态
     */
    fun refreshServiceStatus() {
        _isServiceRunning.value = FallDetectionService.isRunning
        _isDetectionEnabled.value = userPrefs.isFallDetectionEnabled()
        _hasRequiredPermissions.value = fallDetectionManager.hasRequiredPermissions()
    }

    /**
     * 添加紧急联系人
     */
    fun addContact(name: String, phone: String, relationship: String, isPrimary: Boolean) {
        viewModelScope.launch {
            val contact = EmergencyContact(
                name = name,
                phone = phone,
                relationship = relationship,
                isPrimary = isPrimary
            )
            
            if (isPrimary) {
                // 先清除其他主要联系人标记
                emergencyContactDao.clearPrimaryStatus()
            }
            
            emergencyContactDao.insertContact(contact)
            _showAddDialog.value = false
        }
    }

    /**
     * 更新紧急联系人
     */
    fun updateContact(contact: EmergencyContact) {
        viewModelScope.launch {
            if (contact.isPrimary) {
                emergencyContactDao.clearPrimaryStatus()
            }
            emergencyContactDao.updateContact(contact)
            _editingContact.value = null
        }
    }

    /**
     * 删除紧急联系人
     */
    fun deleteContact(contact: EmergencyContact) {
        viewModelScope.launch {
            emergencyContactDao.deleteContact(contact)
        }
    }

    /**
     * 设置为主要联系人
     */
    fun setPrimaryContact(contactId: Int) {
        viewModelScope.launch {
            emergencyContactDao.setPrimaryContact(contactId)
        }
    }

    /**
     * 显示添加联系人对话框
     */
    fun showAddContactDialog() {
        _showAddDialog.value = true
    }

    /**
     * 隐藏添加联系人对话框
     */
    fun hideAddContactDialog() {
        _showAddDialog.value = false
    }

    /**
     * 开始编辑联系人
     */
    fun startEditingContact(contact: EmergencyContact) {
        _editingContact.value = contact
    }

    /**
     * 取消编辑联系人
     */
    fun cancelEditing() {
        _editingContact.value = null
    }

    /**
     * 获取未授予的权限
     */
    fun getMissingPermissions(): List<String> {
        return fallDetectionManager.getMissingPermissions()
    }

    /**
     * 获取所需权限
     */
    fun getRequiredPermissions(): Array<String> {
        return fallDetectionManager.getRequiredPermissions()
    }
}
