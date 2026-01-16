package com.silverlink.app.data.remote.model

import com.google.gson.annotations.SerializedName

data class QwenRequest(
    @SerializedName("model")
    val model: String = "qwen-turbo",
    @SerializedName("input")
    val input: Input,
    @SerializedName("parameters")
    val parameters: Parameters = Parameters()
)

data class Input(
    @SerializedName("messages")
    val messages: List<Message>
)

data class Message(
    @SerializedName("role")
    val role: String, // "system", "user", "assistant"
    @SerializedName("content")
    val content: String
)

data class Parameters(
    @SerializedName("result_format")
    val resultFormat: String = "message"
)
