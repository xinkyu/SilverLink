package com.silverlink.app.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Qwen-VL 视觉模型响应体
 */
data class QwenVisionResponse(
    @SerializedName("output")
    val output: VisionOutput,
    @SerializedName("request_id")
    val requestId: String,
    @SerializedName("usage")
    val usage: VisionUsage?
)

data class VisionOutput(
    @SerializedName("choices")
    val choices: List<VisionChoice>?
)

data class VisionChoice(
    @SerializedName("message")
    val message: VisionResponseMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class VisionResponseMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: List<VisionContentItem>?
)

data class VisionContentItem(
    @SerializedName("text")
    val text: String?
)

data class VisionUsage(
    @SerializedName("input_tokens")
    val inputTokens: Int?,
    @SerializedName("output_tokens")
    val outputTokens: Int?
)
