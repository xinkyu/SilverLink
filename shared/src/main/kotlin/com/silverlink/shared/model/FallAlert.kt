package com.silverlink.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class FallAlert(
    val timestamp: Long,
    val severity: FallSeverity,
    val confidence: Float,
    val patterns: List<String>,
    val location: String? = null
)

@Serializable
enum class FallSeverity {
    LOW, MEDIUM, HIGH
}
