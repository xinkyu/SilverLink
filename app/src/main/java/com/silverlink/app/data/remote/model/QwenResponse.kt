package com.silverlink.app.data.remote.model

import com.google.gson.annotations.SerializedName

data class QwenResponse(
    @SerializedName("output")
    val output: Output,
    @SerializedName("request_id")
    val requestId: String
)

data class Output(
    @SerializedName("text")
    val text: String?, // Sometimes used in different formats
    @SerializedName("choices")
    val choices: List<Choice>?
)

data class Choice(
    @SerializedName("message")
    val message: Message,
    @SerializedName("finish_reason")
    val finishReason: String
)
