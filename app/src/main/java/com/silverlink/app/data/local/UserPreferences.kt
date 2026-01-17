package com.silverlink.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * 用户角色类型
 */
enum class UserRole {
    NONE,       // 未选择
    ELDER,      // 长辈
    FAMILY      // 家人
}

/**
 * 配对信息
 */
data class PairingInfo(
    val code: String,           // 6位配对码
    val elderName: String,      // 长辈称呼
    val timestamp: Long         // 创建时间
)

/**
 * 用户配置数据类
 */
data class UserConfig(
    val role: UserRole = UserRole.NONE,
    val isActivated: Boolean = false,
    val elderName: String = "",        // 长辈称呼，如"王爷爷"
    val elderProfile: String = "",     // 长辈信息简介（家乡/兴趣/健康等）
    val pairingCode: String = "",      // 配对码
    val pairedDeviceId: String = ""    // 已配对设备ID
)

/**
 * 用户配置管理器
 * 使用 SharedPreferences 存储用户的角色和配置信息
 */
class UserPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    // 配对信息存储（模拟服务器存储）
    private val pairingPrefs: SharedPreferences = context.getSharedPreferences(
        PAIRING_PREFS_NAME, Context.MODE_PRIVATE
    )
    
    private val _userConfig = MutableStateFlow(loadConfig())
    val userConfig: StateFlow<UserConfig> = _userConfig.asStateFlow()
    
    /**
     * 从 SharedPreferences 加载配置
     */
    private fun loadConfig(): UserConfig {
        return UserConfig(
            role = UserRole.valueOf(prefs.getString(KEY_ROLE, UserRole.NONE.name) ?: UserRole.NONE.name),
            isActivated = prefs.getBoolean(KEY_ACTIVATED, false),
            elderName = prefs.getString(KEY_ELDER_NAME, "") ?: "",
            elderProfile = prefs.getString(KEY_ELDER_PROFILE, "") ?: "",
            pairingCode = prefs.getString(KEY_PAIRING_CODE, "") ?: "",
            pairedDeviceId = prefs.getString(KEY_PAIRED_DEVICE_ID, "") ?: ""
        )
    }
    
    /**
     * 保存用户角色
     */
    fun setRole(role: UserRole) {
        prefs.edit().putString(KEY_ROLE, role.name).apply()
        _userConfig.value = _userConfig.value.copy(role = role)
    }
    
    /**
     * 设置激活状态
     */
    fun setActivated(activated: Boolean) {
        prefs.edit().putBoolean(KEY_ACTIVATED, activated).apply()
        _userConfig.value = _userConfig.value.copy(isActivated = activated)
    }
    
    /**
     * 设置长辈称呼
     */
    fun setElderName(name: String) {
        prefs.edit().putString(KEY_ELDER_NAME, name).apply()
        _userConfig.value = _userConfig.value.copy(elderName = name)
    }

    /**
     * 设置长辈信息简介
     */
    fun setElderProfile(profile: String) {
        prefs.edit().putString(KEY_ELDER_PROFILE, profile).apply()
        _userConfig.value = _userConfig.value.copy(elderProfile = profile)
    }
    
    /**
     * 生成配对码并保存配对信息（6位数字）
     * @param elderName 长辈称呼
     * @return 格式化的配对码（xxx xxx）
     */
    fun generatePairingCode(elderName: String): String {
        val code = (100000..999999).random().toString()
        // 格式化为 xxx xxx
        val formattedCode = "${code.substring(0, 3)} ${code.substring(3, 6)}"
        
        // 保存到用户配置
        prefs.edit().putString(KEY_PAIRING_CODE, formattedCode).apply()
        _userConfig.value = _userConfig.value.copy(pairingCode = formattedCode)
        
        // 保存配对信息到配对存储（模拟服务器）
        savePairingInfo(code, elderName)
        
        return formattedCode
    }
    
    /**
     * 保存配对信息（模拟服务器存储）
     */
    private fun savePairingInfo(code: String, elderName: String) {
        val pairingInfo = JSONObject().apply {
            put("code", code)
            put("elderName", elderName)
            put("timestamp", System.currentTimeMillis())
        }
        pairingPrefs.edit().putString(code, pairingInfo.toString()).apply()
    }
    
    /**
     * 验证配对码并获取配对信息
     * @return 配对信息，如果验证失败返回 null
     */
    fun verifyAndGetPairingInfo(inputCode: String): PairingInfo? {
        val cleanCode = inputCode.replace(" ", "").replace("-", "")
        if (cleanCode.length != 6) return null
        
        val pairingJson = pairingPrefs.getString(cleanCode, null) ?: return null
        
        return try {
            val json = JSONObject(pairingJson)
            PairingInfo(
                code = json.getString("code"),
                elderName = json.getString("elderName"),
                timestamp = json.getLong("timestamp")
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 生成二维码内容（包含配对码和长辈称呼）
     */
    fun generateQRContent(code: String, elderName: String, elderProfile: String = ""): String {
        val json = JSONObject().apply {
            put("code", code.replace(" ", ""))
            put("name", elderName)
            if (elderProfile.isNotBlank()) {
                put("profile", elderProfile)
            }
            put("app", "SilverLink")
        }
        return "silverlink://${Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)}"
    }
    
    /**
     * 解析二维码内容
     * @return 配对信息，解析失败返回 null
     */
    fun parseQRContent(content: String): PairingInfo? {
        return try {
            if (!content.startsWith("silverlink://")) return null
            val base64Data = content.removePrefix("silverlink://")
            val jsonStr = String(Base64.decode(base64Data, Base64.NO_WRAP))
            val json = JSONObject(jsonStr)
            PairingInfo(
                code = json.getString("code"),
                elderName = json.getString("name"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 设置配对码（用于长辈端输入）
     */
    fun setPairingCode(code: String) {
        prefs.edit().putString(KEY_PAIRING_CODE, code).apply()
        _userConfig.value = _userConfig.value.copy(pairingCode = code)
    }
    
    /**
     * 完成家人端配置
     */
    fun completeFamilySetup(elderName: String, elderProfile: String = ""): String {
        val code = generatePairingCode(elderName)
        prefs.edit()
            .putString(KEY_ROLE, UserRole.FAMILY.name)
            .putString(KEY_ELDER_NAME, elderName)
            .putString(KEY_ELDER_PROFILE, elderProfile)
            .putString(KEY_PAIRING_CODE, code)
            .putBoolean(KEY_ACTIVATED, true)
            .apply()
        _userConfig.value = UserConfig(
            role = UserRole.FAMILY,
            isActivated = true,
            elderName = elderName,
            elderProfile = elderProfile,
            pairingCode = code
        )
        return code
    }
    
    /**
     * 完成长辈端激活
     */
    fun completeElderActivation(elderName: String) {
        prefs.edit()
            .putString(KEY_ROLE, UserRole.ELDER.name)
            .putString(KEY_ELDER_NAME, elderName)
            .putBoolean(KEY_ACTIVATED, true)
            .apply()
        _userConfig.value = UserConfig(
            role = UserRole.ELDER,
            isActivated = true,
            elderName = elderName,
            elderProfile = _userConfig.value.elderProfile
        )
    }
    
    /**
     * 重置所有配置（用于调试或退出登录）
     */
    fun reset() {
        prefs.edit().clear().apply()
        _userConfig.value = UserConfig()
    }
    
    /**
     * 清除配对信息（用于调试）
     */
    fun clearPairingData() {
        pairingPrefs.edit().clear().apply()
    }
    
    companion object {
        private const val PREFS_NAME = "silverlink_user_prefs"
        private const val PAIRING_PREFS_NAME = "silverlink_pairing_data"
        private const val KEY_ROLE = "user_role"
        private const val KEY_ACTIVATED = "is_activated"
        private const val KEY_ELDER_NAME = "elder_name"
        private const val KEY_ELDER_PROFILE = "elder_profile"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_PAIRED_DEVICE_ID = "paired_device_id"
        
        @Volatile
        private var instance: UserPreferences? = null
        
        fun getInstance(context: Context): UserPreferences {
            return instance ?: synchronized(this) {
                instance ?: UserPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
