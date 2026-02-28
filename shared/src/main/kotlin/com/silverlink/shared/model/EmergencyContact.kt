package com.silverlink.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyContact(
    val id: Int,
    val name: String,
    val phone: String,
    val relationship: String = "",
    val isPrimary: Boolean = false
)
