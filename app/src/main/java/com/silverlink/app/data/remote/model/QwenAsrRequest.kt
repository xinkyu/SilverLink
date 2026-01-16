package com.silverlink.app.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Qwen-Audio 语音识别请求体 (多模态生成格式)
 */
data class QwenAsrRequest(
    @SerializedName("model")
    val model: String = "qwen2-audio-instruct",
    @SerializedName("input")
    val input: AsrInput
)

data class AsrInput(
    @SerializedName("messages")
    val messages: List<AsrMessage>
)

data class AsrMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: List<AsrContentItem>
)

/**
 * 内容项：可以是音频或文本
 */
data class AsrContentItem(
    @SerializedName("audio")
    val audio: String? = null,  // Base64 格式: "data:audio/m4a;base64,xxxx"
    @SerializedName("text")
    val text: String? = null
) {
    companion object {
        fun audio(base64Data: String, format: String = "m4a") = AsrContentItem(
            audio = "data:audio/$format;base64,$base64Data"
        )
        fun text(content: String) = AsrContentItem(
            text = content
        )
    }
}
