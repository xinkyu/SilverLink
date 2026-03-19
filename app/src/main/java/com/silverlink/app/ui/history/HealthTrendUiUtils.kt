package com.silverlink.app.ui.history

import com.silverlink.app.feature.health.HealthTrendPoint
import com.silverlink.app.ui.components.TimeRange
import com.silverlink.sdk.health.SleepSummaryPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class TimeWindow(
    val start: Long,
    val end: Long
)

fun currentTimeWindow(range: TimeRange, zone: ZoneId = ZoneId.systemDefault()): TimeWindow {
    val today = LocalDate.now(zone)
    val start = when (range) {
        TimeRange.DAY -> today.atStartOfDay(zone).toInstant().toEpochMilli()
        TimeRange.WEEK -> today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        TimeRange.MONTH -> today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
        TimeRange.YEAR -> today.minusDays(364).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return TimeWindow(start = start, end = end)
}

fun averageTrend(points: List<HealthTrendPoint>): Int {
    return points.map { it.value }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
}

fun minTrend(points: List<HealthTrendPoint>): Int = points.minOfOrNull { it.value } ?: 0

fun maxTrend(points: List<HealthTrendPoint>): Int = points.maxOfOrNull { it.value } ?: 0

fun totalTrend(points: List<HealthTrendPoint>): Int = points.sumOf { it.value }

fun lastDays(points: List<HealthTrendPoint>, days: Int): List<HealthTrendPoint> {
    return points.sortedBy { it.timestamp }.takeLast(days)
}

fun monthlyAverageValues(points: List<HealthTrendPoint>): List<Int> {
    val grouped = points.groupBy {
        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).monthValue
    }
    return (1..12).map { month -> averageTrend(grouped[month].orEmpty()) }
}

fun averageSleepMinutes(points: List<SleepSummaryPoint>): Int {
    return points.map { it.totalMinutes }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
}

fun averageSleepScore(points: List<SleepSummaryPoint>): Int {
    return points.map { it.score }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
}

fun sleepMonthlyAverageHours(points: List<SleepSummaryPoint>): List<Float> {
    val grouped = points.groupBy {
        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).monthValue
    }
    return (1..12).map { month ->
        val values = grouped[month].orEmpty().map { it.totalMinutes / 60f }
        values.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
    }
}
