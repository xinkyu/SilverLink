package com.silverlink.sdk.health

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume

class OppoHealthServiceBridge(
    private val appContext: Context
) : HealthServiceBridge {

    private var isMonitoring = false

    override fun initialize(context: Context): Result<Unit> = runCatching {
        val apiClass = findHeytapApiClass()
        val initMethod = apiClass.methods.firstOrNull {
            it.name == "init" && it.parameterTypes.size == 1
        } ?: error("HeytapHealthApi.init(context) not found")
        initMethod.invoke(null, context.applicationContext)
        Unit
    }

    override suspend fun requestAuthorization(activity: Activity): Result<Unit> {
        val authorityApi = runCatching { getAuthorityApi() }.getOrElse { return Result.failure(it) }
        val requestMethod = authorityApi.javaClass.methods.firstOrNull {
            it.name == "request" &&
                it.parameterTypes.size == 2 &&
                Activity::class.java.isAssignableFrom(it.parameterTypes[0])
        } ?: return Result.failure(IllegalStateException("authorityApi.request(activity, callback) not found"))

        return awaitCallbackResult(authorityApi, requestMethod, arrayOf(activity)) { Result.success(Unit) }
    }

    override suspend fun requestAuthorization(activity: Activity, redirectUrl: String): Result<Unit> {
        val authorityApi = runCatching { getAuthorityApi() }.getOrElse { return Result.failure(it) }
        val requestMethod = authorityApi.javaClass.methods.firstOrNull {
            it.name == "request" &&
                it.parameterTypes.size == 3 &&
                Activity::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                it.parameterTypes[1] == String::class.java
        } ?: return Result.failure(IllegalStateException("authorityApi.request(activity, redirectUrl, callback) not found"))

        return awaitCallbackResult(authorityApi, requestMethod, arrayOf(activity, redirectUrl)) { Result.success(Unit) }
    }

    override suspend fun revokeAuthorization(): Result<Unit> {
        val authorityApi = runCatching { getAuthorityApi() }.getOrElse { return Result.failure(it) }
        val revokeMethod = authorityApi.javaClass.methods.firstOrNull {
            it.name == "revoke" && it.parameterTypes.size == 1
        } ?: return Result.failure(IllegalStateException("authorityApi.revoke(callback) not found"))

        return awaitCallbackResult(authorityApi, revokeMethod, emptyArray()) { Result.success(Unit) }
    }

    override suspend fun getAuthorizedScopes(): Result<List<String>> {
        val authorityApi = runCatching { getAuthorityApi() }.getOrElse { return Result.failure(it) }
        val validMethod = authorityApi.javaClass.methods.firstOrNull {
            it.name == "valid" && it.parameterTypes.size == 1
        } ?: return Result.failure(IllegalStateException("authorityApi.valid(callback) not found"))

        return awaitCallbackResult(authorityApi, validMethod, emptyArray()) { payload ->
            when (payload) {
                is List<*> -> Result.success(payload.filterIsInstance<String>())
                else -> Result.success(emptyList())
            }
        }
    }

    override suspend fun getHeartRate(): Result<Int> {
        val now = System.currentTimeMillis()
        val start = now - 24 * 60 * 60 * 1000L
        return readSingleValue(
            dataTypeName = "TYPE_HEART_RATE_COUNT",
            startTime = start,
            endTime = now,
            elementCandidates = listOf("ELEMENT_AVERAGE", "ELEMENT_HEART_RATE")
        )
    }

    override suspend fun getSteps(): Result<Int> {
        val now = System.currentTimeMillis()
        val start = now - 24 * 60 * 60 * 1000L
        return readSingleValue(
            dataTypeName = "TYPE_DAILY_ACTIVITY_COUNT",
            startTime = start,
            endTime = now,
            elementCandidates = listOf("ELEMENT_STEP")
        )
    }

    override suspend fun getSleepData(date: String): Result<SleepData> {
        return runCatching {
            val day = LocalDate.parse(date)
            val zone = ZoneId.systemDefault()
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            val dataPoints = readDataPoints("TYPE_SLEEP_COUNT", start, end).getOrThrow()
            val point = dataPoints.firstOrNull() ?: error("No sleep data")

            val total = readElementAsInt(point, listOf("ELEMENT_TOTAL"))
            val deep = readElementAsInt(point, listOf("ELEMENT_TOTAL_DEEP_SLEEP_TIME"))
            val light = readElementAsInt(point, listOf("ELEMENT_TOTAL_LIGHTLY_SLEEP_TIME"))
            val rem = readElementAsInt(point, listOf("ELEMENT_TOTAL_REM_TIME"))
            val awake = readElementAsInt(point, listOf("ELEMENT_TOTAL_WAKE_UP_TIME"))
            val score = readElementAsInt(point, listOf("ELEMENT_SLEEP_SCORE"))

            SleepData(
                totalMinutes = total,
                deepSleepMinutes = deep,
                lightSleepMinutes = light,
                remMinutes = rem,
                awakeMinutes = awake,
                score = score,
                date = date
            )
        }
    }

    override suspend fun getBloodOxygen(): Result<Int> {
        val now = System.currentTimeMillis()
        val start = now - 24 * 60 * 60 * 1000L
        return readSingleValue(
            dataTypeName = "TYPE_BLOOD_OXYGEN_COUNT",
            startTime = start,
            endTime = now,
            elementCandidates = listOf("ELEMENT_AVERAGE", "ELEMENT_BLOOD_OXYGEN")
        )
    }

    override suspend fun getDailyActivitySummary(startTime: Long, endTime: Long): Result<DailyActivitySummary> {
        return runCatching {
            val points = readDataPoints("TYPE_DAILY_ACTIVITY_COUNT", startTime, endTime).getOrThrow()
            val latest = points.lastOrNull() ?: error("No daily activity data")
            DailyActivitySummary(
                steps = readElementAsInt(latest, listOf("ELEMENT_STEP")),
                calories = readElementAsInt(latest, listOf("ELEMENT_CALORIE")),
                distanceMeters = readElementAsInt(latest, listOf("ELEMENT_DISTANCE")),
                moveMinutes = readElementAsInt(latest, listOf("ELEMENT_WORK_MINUTE", "ELEMENT_MOVE_TIME"))
            )
        }
    }

    override suspend fun getHeartRateTimeline(startTime: Long, endTime: Long): Result<List<HealthValuePoint>> {
        return runCatching {
            val points = readDataPoints("TYPE_HEART_RATE", startTime, endTime).getOrThrow()
            points.mapNotNull { point ->
                val value = runCatching {
                    readElementAsInt(point, listOf("ELEMENT_HEART_RATE"))
                }.getOrNull() ?: return@mapNotNull null
                HealthValuePoint(
                    timestamp = readPointTimestamp(point),
                    value = value
                )
            }.sortedBy { it.timestamp }
        }
    }

    override suspend fun getSleepTimeline(startTime: Long, endTime: Long): Result<List<SleepStagePoint>> {
        return runCatching {
            val points = readDataPoints("TYPE_SLEEP", startTime, endTime).getOrThrow()
            points.mapNotNull { point ->
                val stage = runCatching {
                    readElementAsInt(point, listOf("ELEMENT_SLEEP"))
                }.getOrNull() ?: return@mapNotNull null
                SleepStagePoint(
                    startTimestamp = readPointStartTimestamp(point),
                    endTimestamp = readPointTimestamp(point).takeIf { it > 0 } ?: readPointStartTimestamp(point),
                    stage = stage
                )
            }.sortedBy { it.startTimestamp }
        }
    }

    override suspend fun startHeartRateMonitor(callback: (Int) -> Unit) {
        isMonitoring = true
        while (isMonitoring && currentCoroutineContext().isActive) {
            getHeartRate().getOrNull()?.let(callback)
            delay(15_000)
        }
    }

    override suspend fun stopHeartRateMonitor() {
        isMonitoring = false
    }

    override fun isHealthAppInstalled(): Boolean {
        return try {
            appContext.packageManager.getPackageInfo("com.heytap.health", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    override fun openHealthAppDownload(activity: Activity) {
        runCatching {
            val installUtilsClass = Class.forName("com.heytap.health.sdk.utils.InstallUtils")
            val method = installUtilsClass.methods.firstOrNull {
                (it.name == "DownloadApp" || it.name == "downloadApp") &&
                    it.parameterTypes.size == 1 &&
                    Activity::class.java.isAssignableFrom(it.parameterTypes[0])
            } ?: return
            method.invoke(null, activity)
        }
    }

    override fun isAvailable(): Boolean = runCatching {
        findHeytapApiClass()
        true
    }.getOrElse { false }

    private fun findHeytapApiClass(): Class<*> {
        val candidates = listOf(
            "com.heytap.health.sdk.api.HeytapHealthApi",
            "com.heytap.health.sdk.HeytapHealthApi",
            "com.heytap.health.HeytapHealthApi"
        )
        return candidates.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name) }.getOrNull()
        } ?: error("HeytapHealthApi class not found")
    }

    private fun getApiInstance(): Any {
        val apiClass = findHeytapApiClass()
        val getInstanceMethod = apiClass.methods.firstOrNull {
            it.name == "getInstance" && it.parameterTypes.isEmpty()
        } ?: error("HeytapHealthApi.getInstance() not found")
        return getInstanceMethod.invoke(null) ?: error("HeytapHealthApi.getInstance() returned null")
    }

    private fun getAuthorityApi(): Any {
        val instance = getApiInstance()
        val method = instance.javaClass.methods.firstOrNull {
            it.name == "authorityApi" && it.parameterTypes.isEmpty()
        } ?: error("authorityApi() not found")
        return method.invoke(instance) ?: error("authorityApi() returned null")
    }

    private fun getDataApi(): Any {
        val instance = getApiInstance()
        val method = instance.javaClass.methods.firstOrNull {
            it.name == "dataApi" && it.parameterTypes.isEmpty()
        } ?: error("dataApi() not found")
        return method.invoke(instance) ?: error("dataApi() returned null")
    }

    private suspend fun readSingleValue(
        dataTypeName: String,
        startTime: Long,
        endTime: Long,
        elementCandidates: List<String>
    ): Result<Int> {
        return runCatching {
            val dataPoints = readDataPoints(dataTypeName, startTime, endTime).getOrThrow()
            val latest = dataPoints.lastOrNull() ?: error("No data points")
            readElementAsInt(latest, elementCandidates)
        }
    }

    private suspend fun readDataPoints(
        dataTypeName: String,
        startTime: Long,
        endTime: Long
    ): Result<List<Any>> {
        val dataApi = runCatching { getDataApi() }.getOrElse { return Result.failure(it) }
        val request = runCatching {
            buildDataReadRequest(dataTypeName, startTime, endTime)
        }.getOrElse { return Result.failure(it) }

        val readMethod = dataApi.javaClass.methods.firstOrNull {
            it.name == "read" && it.parameterTypes.size == 2
        } ?: return Result.failure(IllegalStateException("dataApi.read(request, callback) not found"))

        return awaitCallbackResult(dataApi, readMethod, arrayOf(request)) { payload ->
            val points = extractDataPoints(payload)
            Result.success(points)
        }
    }

    private fun buildDataReadRequest(dataTypeName: String, startTime: Long, endTime: Long): Any {
        val dataTypeClass = Class.forName("com.heytap.databaseengine.apiv3.data.DataType")
        val dataType = dataTypeClass.getField(dataTypeName).get(null)

        val builderClass = Class.forName("com.heytap.databaseengine.apiv3.request.DataReadRequest\$Builder")
        val builder = builderClass.getConstructor().newInstance()

        val readMethod = builderClass.methods.firstOrNull {
            it.name == "read" && it.parameterTypes.size == 1
        } ?: error("DataReadRequest.Builder.read(dataType) not found")
        val setTimeRangeMethod = builderClass.methods.firstOrNull {
            it.name == "setTimeRange" &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == java.lang.Long.TYPE &&
                it.parameterTypes[1] == java.lang.Long.TYPE
        } ?: error("DataReadRequest.Builder.setTimeRange(long, long) not found")
        val buildMethod = builderClass.methods.firstOrNull {
            it.name == "build" && it.parameterTypes.isEmpty()
        } ?: error("DataReadRequest.Builder.build() not found")

        readMethod.invoke(builder, dataType)
        setTimeRangeMethod.invoke(builder, startTime, endTime)
        return buildMethod.invoke(builder) ?: error("DataReadRequest build failed")
    }

    private fun extractDataPoints(payload: Any?): List<Any> {
        val dataSets = when (payload) {
            is List<*> -> payload.filterNotNull()
            else -> emptyList()
        }
        if (dataSets.isEmpty()) return emptyList()

        val result = mutableListOf<Any>()
        for (dataSet in dataSets) {
            val method = dataSet.javaClass.methods.firstOrNull {
                it.name == "getDataPoints" && it.parameterTypes.isEmpty()
            } ?: continue
            val points = method.invoke(dataSet)
            if (points is List<*>) {
                result.addAll(points.filterNotNull())
            }
        }
        return result
    }

    private fun readElementAsInt(dataPoint: Any, elementCandidates: List<String>): Int {
        val elementClass = Class.forName("com.heytap.databaseengine.apiv3.data.Element")
        val getValueMethod = dataPoint.javaClass.methods.firstOrNull {
            it.name == "getValue" && it.parameterTypes.size == 1
        } ?: error("DataPoint.getValue(Element) not found")

        for (fieldName in elementCandidates) {
            val element = runCatching { elementClass.getField(fieldName).get(null) }.getOrNull() ?: continue
            val value = runCatching { getValueMethod.invoke(dataPoint, element) }.getOrNull() ?: continue
            val numeric = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
            if (numeric != null) return numeric
        }
        error("Element value not found for ${elementCandidates.joinToString()}")
    }

    private fun readPointTimestamp(dataPoint: Any): Long {
        val method = dataPoint.javaClass.methods.firstOrNull {
            it.name == "getTimeStamp" && it.parameterTypes.isEmpty()
        } ?: return 0L
        val value = method.invoke(dataPoint)
        return (value as? Number)?.toLong() ?: 0L
    }

    private fun readPointStartTimestamp(dataPoint: Any): Long {
        val method = dataPoint.javaClass.methods.firstOrNull {
            it.name == "getStartTimeStamp" && it.parameterTypes.isEmpty()
        } ?: return 0L
        val value = method.invoke(dataPoint)
        return (value as? Number)?.toLong() ?: 0L
    }

    private suspend fun <T> awaitCallbackResult(
        target: Any,
        method: Method,
        baseArgs: Array<Any>,
        successMapper: (Any?) -> Result<T>
    ): Result<T> = suspendCancellableCoroutine { continuation ->
        val callbackType = method.parameterTypes.lastOrNull()
        if (callbackType == null || !callbackType.isInterface) {
            continuation.resume(Result.failure(IllegalStateException("Callback interface missing")))
            return@suspendCancellableCoroutine
        }

        var completed = false
        val handler = InvocationHandler { _, callbackMethod, args ->
            if (completed) return@InvocationHandler null
            when (callbackMethod.name) {
                "onSuccess" -> {
                    completed = true
                    continuation.resume(successMapper(args?.firstOrNull()))
                }
                "onFailure" -> {
                    completed = true
                    val code = (args?.firstOrNull() as? Number)?.toInt() ?: -1
                    continuation.resume(Result.failure(HealthSdkException(code)))
                }
            }
            null
        }

        val proxy = Proxy.newProxyInstance(
            callbackType.classLoader,
            arrayOf(callbackType),
            handler
        )

        val invokeArgs = baseArgs + proxy
        runCatching {
            method.invoke(target, *invokeArgs)
        }.onFailure {
            if (!completed) {
                completed = true
                continuation.resume(Result.failure(it))
            }
        }
    }
}
