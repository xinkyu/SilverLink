package com.silverlink.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.Input
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.data.remote.model.QwenRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val systemPrompt = Message(
        role = "system",
        content = "你叫'小银'，是一个温柔、耐心的年轻人，专门陪伴老人。你的回答要简短、温暖，不要使用复杂的网络用语。如果老人提到身体不适，请建议他们联系子女或就医。如果老人发来疑似诈骗的信息，请帮他们分析并预警。"
    )

    init {
        // Initial welcome message
        _messages.value = listOf(
            Message("assistant", "爷爷奶奶好，我是小银。今天身体怎么样？有什么想跟我聊聊的吗？")
        )
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val userMessage = Message("user", content)
        val currentHistory = _messages.value
        _messages.value = currentHistory + userMessage

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Construct the full context including system prompt
                val apiMessages = mutableListOf<Message>()
                apiMessages.add(systemPrompt)
                // Add last few messages for context (simple context window management)
                apiMessages.addAll(currentHistory.takeLast(10)) 
                apiMessages.add(userMessage)

                val request = QwenRequest(
                    input = Input(messages = apiMessages)
                )

                val response = RetrofitClient.api.chat(request)
                
                val assistantMessageContent = response.output.choices?.firstOrNull()?.message?.content 
                    ?: response.output.text 
                    ?: "哎呀，我刚才走神了，没听清您说什么，能再说一遍吗？"

                val assistantMessage = Message("assistant", assistantMessageContent)
                _messages.value = _messages.value + assistantMessage

            } catch (e: Exception) {
                e.printStackTrace()
                _messages.value = _messages.value + Message("assistant", "网络好像有点卡，请检查一下网络连接哦。")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
