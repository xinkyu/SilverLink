package com.silverlink.sdk.nearby

data class DeviceInfo(
    val id: String,
    val name: String,
    val type: DeviceType
)

enum class DeviceType {
    PHONE, WATCH, UNKNOWN
}
