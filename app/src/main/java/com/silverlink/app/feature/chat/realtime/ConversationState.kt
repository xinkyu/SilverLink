package com.silverlink.app.feature.chat.realtime

sealed class ConversationState {
    object Idle : ConversationState()
    object Listening : ConversationState()
    object Processing : ConversationState()
    object Speaking : ConversationState()
    object Interrupted : ConversationState()
    data class Error(val message: String) : ConversationState()
}
