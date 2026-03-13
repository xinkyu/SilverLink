package com.silverlink.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class MedicationInfo(
    val id: Int,
    val name: String,
    val dosage: String,
    val times: List<String>,
    val isTakenToday: Boolean = false
)
