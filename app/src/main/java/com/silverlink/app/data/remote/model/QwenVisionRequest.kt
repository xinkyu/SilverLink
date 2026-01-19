package com.silverlink.app.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Qwen-VL 视觉模型请求体
 * 用于发送图片给 AI 进行分析
 */
data class QwenVisionRequest(
    @SerializedName("model")
    val model: String = "qwen-vl-plus",
    @SerializedName("input")
    val input: VisionInput
)

data class VisionInput(
    @SerializedName("messages")
    val messages: List<VisionMessage>
)

data class VisionMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: List<ContentItem>
)

/**
 * 内容项：可以是图片或文本
 * image 和 text 只能填一个
 */
data class ContentItem(
    @SerializedName("image")
    val image: String? = null,  // Base64 格式图片，格式为 "data:image/jpeg;base64,xxxx"
    @SerializedName("text")
    val text: String? = null
) {
    companion object {
        fun text(content: String) = ContentItem(text = content)
        fun image(base64: String) = ContentItem(image = base64)
    }
}
