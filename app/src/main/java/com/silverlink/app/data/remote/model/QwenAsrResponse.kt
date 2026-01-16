package com.silverlink.app.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * Qwen-Audio 语音识别响应体 (多模态生成格式)
 */
data class QwenAsrResponse(
    @SerializedName("output")
    val output: AsrOutput?,
    @SerializedName("usage")
    val usage: AsrUsage?,
    @SerializedName("request_id")
    val requestId: String?
)

data class AsrOutput(
    @SerializedName("choices")
    val choices: List<AsrChoice>?
)

data class AsrChoice(
    @SerializedName("message")
    val message: AsrResponseMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class AsrResponseMessage(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: List<AsrContentResponse>?
)

data class AsrContentResponse(
    @SerializedName("text")
    val text: String?
)

data class AsrUsage(
    @SerializedName("input_tokens")
    val inputTokens: Int?,
    @SerializedName("output_tokens")
    val outputTokens: Int?
)
