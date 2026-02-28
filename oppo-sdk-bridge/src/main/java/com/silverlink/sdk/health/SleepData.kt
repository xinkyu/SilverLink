package com.silverlink.sdk.health

data class SleepData(
    val totalMinutes: Int,
    val deepSleepMinutes: Int,
    val lightSleepMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val score: Int,
    val date: String
)
