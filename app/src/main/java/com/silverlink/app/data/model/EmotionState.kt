package com.silverlink.app.data.model

/**
 * 用户情绪类型
 */
enum class Emotion(val displayName: String, val promptHint: String) {
    HAPPY(
        displayName = "开心",
        promptHint = "用户心情不错，可以更活泼、欢快地回应，分享他的喜悦。"
    ),
    SAD(
        displayName = "难过",
        promptHint = "用户听起来有些难过或沮丧，请用更温柔、更慢的语气安慰他，表达关心和理解。"
    ),
    ANGRY(
        displayName = "烦躁",
        promptHint = "用户可能有些烦躁或不满，请保持耐心，先安抚情绪，不要反驳，表示理解。"
    ),
    ANXIOUS(
        displayName = "焦虑",
        promptHint = "用户听起来有些焦虑或担忧，请用平静、稳定的语气帮助他放松，给予安心的回应。"
    ),
    NEUTRAL(
        displayName = "平静",
        promptHint = "" // 默认风格，无需额外提示
    );

    companion object {
        /**
         * 从情感标签解析情绪
         */
        fun fromLabel(label: String): Emotion {
            return when (label.uppercase().trim()) {
                "HAPPY", "JOY", "EXCITED", "开心", "高兴", "喜悦" -> HAPPY
                "SAD", "UNHAPPY", "DEPRESSED", "难过", "伤心", "沮丧" -> SAD
                "ANGRY", "FRUSTRATED", "ANNOYED", "生气", "愤怒", "烦躁" -> ANGRY
                "ANXIOUS", "WORRIED", "NERVOUS", "FEAR", "焦虑", "担心", "紧张", "害怕" -> ANXIOUS
                else -> NEUTRAL
            }
        }
    }
}

/**
 * 语音识别结果，包含文本和情绪
 */
data class SpeechResult(
    val text: String,
    val emotion: Emotion,
    val confidence: Float = 1.0f
)
