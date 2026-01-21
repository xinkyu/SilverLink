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
 * 方言类型
 * 使用 CosyVoice cosyvoice-v3-plus 模型支持的方言
 */
enum class Dialect(
    val displayName: String,
    val languageCode: String,  // CosyVoice language parameter
    val promptHint: String
) {
    NONE("无（普通话）", "zh", "请用标准普通话回答"),
    CANTONESE("广东话", "yue", "请用粤语的口吻和词汇（如\"点解\"、\"系咪\"、\"唔该\"）与老人聊天"),
    DONGBEI("东北话", "db", "请用东北话的口吻和词汇（如\"嘎哈呢\"、\"老铁\"、\"贼好\"）与老人聊天"),
    GANSU("甘肃话", "gs", "请用甘肃话的口吻与老人聊天"),
    GUIZHOU("贵州话", "gz", "请用贵州话的口吻与老人聊天"),
    HENAN("河南话", "hn", "请用河南话的口吻（如\"中\"、\"咋弄\"）与老人聊天"),
    HUBEI("湖北话", "hb", "请用湖北话的口吻与老人聊天"),
    JIANGXI("江西话", "jx", "请用江西话的口吻与老人聊天"),
    MINNAN("闽南话", "mn", "请用闽南话的口吻与老人聊天"),
    NINGXIA("宁夏话", "nx", "请用宁夏话的口吻与老人聊天"),
    SHANXI("山西话", "sx", "请用山西话的口吻与老人聊天"),
    SHAANXI("陕西话", "snx", "请用陕西话的口吻（如\"额\"、\"咋咧\"）与老人聊天"),
    SHANDONG("山东话", "sd", "请用山东话的口吻与老人聊天"),
    SHANGHAI("上海话", "sh", "请用上海话的口吻（如\"侬好\"、\"阿拉\"）与老人聊天"),
    SICHUAN("四川话", "sc", "请用四川话的口吻和词汇（如\"咋个样\"、\"巴适\"、\"莫得\"）与老人聊天"),
    TIANJIN("天津话", "tj", "请用天津话的口吻（如\"嘛呢\"、\"倍儿\"）与老人聊天"),
    YUNNAN("云南话", "yn", "请用云南话的口吻与老人聊天");

    companion object {
        fun fromName(name: String): Dialect {
            return entries.find { it.name == name } ?: NONE
        }
    }
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
    val dialect: Dialect = Dialect.NONE,  // 方言设置
    val clonedVoiceId: String = "",    // 复刻音色ID（用于TTS方言支持）
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
            dialect = Dialect.fromName(prefs.getString(KEY_DIALECT, Dialect.NONE.name) ?: Dialect.NONE.name),
            clonedVoiceId = prefs.getString(KEY_CLONED_VOICE_ID, "") ?: "",
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
     * 设置方言
     */
    fun setDialect(dialect: Dialect) {
        prefs.edit().putString(KEY_DIALECT, dialect.name).apply()
        _userConfig.value = _userConfig.value.copy(dialect = dialect)
    }
    
    /**
     * 设置复刻音色ID
     */
    fun setClonedVoiceId(voiceId: String) {
        prefs.edit().putString(KEY_CLONED_VOICE_ID, voiceId).apply()
        _userConfig.value = _userConfig.value.copy(clonedVoiceId = voiceId)
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
     * 生成二维码内容（包含配对码、长辈称呼、方言设置和复刻音色ID）
     */
    fun generateQRContent(
        code: String, 
        elderName: String, 
        elderProfile: String = "", 
        dialect: Dialect = Dialect.NONE,
        clonedVoiceId: String = ""
    ): String {
        val json = JSONObject().apply {
            put("code", code.replace(" ", ""))
            put("name", elderName)
            if (elderProfile.isNotBlank()) {
                put("profile", elderProfile)
            }
            if (dialect != Dialect.NONE) {
                put("dialect", dialect.name)
            }
            if (clonedVoiceId.isNotBlank()) {
                put("voiceId", clonedVoiceId)
            }
            put("app", "SilverLink")
        }
        return "silverlink://${Base64.encodeToString(json.toString().toByteArray(), Base64.NO_WRAP)}"
    }
    
    /**
     * 解析二维码内容
     * @return Pair of 配对信息 and 方言，解析失败返回 null
     */
    fun parseQRContent(content: String): Pair<PairingInfo, Dialect>? {
        return try {
            if (!content.startsWith("silverlink://")) return null
            val base64Data = content.removePrefix("silverlink://")
            val jsonStr = String(Base64.decode(base64Data, Base64.NO_WRAP))
            val json = JSONObject(jsonStr)
            val pairingInfo = PairingInfo(
                code = json.getString("code"),
                elderName = json.getString("name"),
                timestamp = System.currentTimeMillis()
            )
            val dialect = if (json.has("dialect")) {
                Dialect.fromName(json.getString("dialect"))
            } else {
                Dialect.NONE
            }
            // 保存解析出的 profile、dialect 和 voiceId
            if (json.has("profile")) {
                setElderProfile(json.getString("profile"))
            }
            setDialect(dialect)
            if (json.has("voiceId")) {
                setClonedVoiceId(json.getString("voiceId"))
            }
            Pair(pairingInfo, dialect)
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
     * 
     * 重要：保留之前通过 parseQRContent 设置的 dialect 和 clonedVoiceId
     */
    fun completeElderActivation(elderName: String) {
        prefs.edit()
            .putString(KEY_ROLE, UserRole.ELDER.name)
            .putString(KEY_ELDER_NAME, elderName)
            .putBoolean(KEY_ACTIVATED, true)
            .apply()
        // 保留 dialect 和 clonedVoiceId（在 parseQRContent 中已设置）
        _userConfig.value = _userConfig.value.copy(
            role = UserRole.ELDER,
            isActivated = true,
            elderName = elderName
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
        private const val KEY_DIALECT = "dialect"
        private const val KEY_CLONED_VOICE_ID = "cloned_voice_id"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_PAIRED_DEVICE_ID = "paired_device_id"
        
        // 跌倒检测相关
        private const val KEY_FALL_DETECTION_ENABLED = "fall_detection_enabled"
        private const val KEY_FALL_DETECTION_SENSITIVITY = "fall_detection_sensitivity"

        // 主动关怀相关
        private const val KEY_PROACTIVE_INTERACTION_ENABLED = "proactive_interaction_enabled"
        
        // 位置共享相关
        private const val KEY_LOCATION_SHARING_ENABLED = "location_sharing_enabled"
        
        @Volatile
        private var instance: UserPreferences? = null
        
        fun getInstance(context: Context): UserPreferences {
            return instance ?: synchronized(this) {
                instance ?: UserPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // ==================== 跌倒检测设置 ====================
    
    /**
     * 跌倒检测灵敏度
     */
    enum class FallDetectionSensitivity {
        LOW,    // 低灵敏度（不容易误报）
        MEDIUM, // 中等灵敏度（默认）
        HIGH    // 高灵敏度（更容易触发）
    }
    
    /**
     * 获取跌倒检测开关状态
     */
    fun isFallDetectionEnabled(): Boolean {
        return prefs.getBoolean(KEY_FALL_DETECTION_ENABLED, false)
    }
    
    /**
     * 设置跌倒检测开关
     */
    fun setFallDetectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FALL_DETECTION_ENABLED, enabled).apply()
    }
    
    /**
     * 获取跌倒检测灵敏度
     */
    fun getFallDetectionSensitivity(): FallDetectionSensitivity {
        val name = prefs.getString(KEY_FALL_DETECTION_SENSITIVITY, FallDetectionSensitivity.MEDIUM.name)
        return try {
            FallDetectionSensitivity.valueOf(name ?: FallDetectionSensitivity.MEDIUM.name)
        } catch (e: Exception) {
            FallDetectionSensitivity.MEDIUM
        }
    }
    
    /**
     * 设置跌倒检测灵敏度
     */
    fun setFallDetectionSensitivity(sensitivity: FallDetectionSensitivity) {
        prefs.edit().putString(KEY_FALL_DETECTION_SENSITIVITY, sensitivity.name).apply()
    }

    // ==================== 主动关怀设置 ====================

    /**
     * 获取主动关怀开关状态
     */
    fun isProactiveInteractionEnabled(): Boolean {
        return prefs.getBoolean(KEY_PROACTIVE_INTERACTION_ENABLED, false)
    }

    /**
     * 设置主动关怀开关
     */
    fun setProactiveInteractionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PROACTIVE_INTERACTION_ENABLED, enabled).apply()
    }
    
    // ==================== 位置共享设置 ====================
    
    /**
     * 获取位置共享开关状态
     */
    fun isLocationSharingEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOCATION_SHARING_ENABLED, false)
    }
    
    /**
     * 设置位置共享开关
     */
    fun setLocationSharingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCATION_SHARING_ENABLED, enabled).apply()
    }
}
