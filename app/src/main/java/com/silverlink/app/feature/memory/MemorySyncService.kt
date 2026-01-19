package com.silverlink.app.feature.memory

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.silverlink.app.SilverLinkApp
import com.silverlink.app.data.local.UserPreferences
import com.silverlink.app.data.local.UserRole
import com.silverlink.app.data.local.entity.MemoryPhotoEntity
import com.silverlink.app.data.remote.CloudBaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "MemorySyncService"

/**
 * 记忆照片后台同步服务
 * - WorkManager 定时任务
 * - WiFi + 充电时同步
 * - 下载新照片到本地缓存
 */
object MemorySyncService {
    const val WORK_NAME = "memory_photo_sync"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()

        val request = PeriodicWorkRequestBuilder<MemoryPhotoSyncWorker>(
            12, TimeUnit.HOURS
        ).setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class MemoryPhotoSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val memoryPhotoDao = SilverLinkApp.database.memoryPhotoDao()
    private val userPrefs = UserPreferences.getInstance(appContext)
    private val httpClient = OkHttpClient()
    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (userPrefs.userConfig.value.role != UserRole.ELDER) {
            return@withContext Result.success()
        }

        val elderDeviceId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (elderDeviceId.isNullOrBlank()) {
            Log.w(TAG, "Device ID missing, skip sync")
            return@withContext Result.success()
        }

        val latestCreatedAt = memoryPhotoDao.getLatestCreatedAt(elderDeviceId) ?: 0L

        val remoteResult = CloudBaseService.getMemoryPhotos(
            elderDeviceId = elderDeviceId,
            pageSize = 50,
            sinceTimestamp = if (latestCreatedAt > 0) latestCreatedAt else null
        )

        val remotePhotos = remoteResult.getOrElse {
            Log.e(TAG, "Sync failed: ${it.message}")
            return@withContext Result.retry()
        }

        if (remotePhotos.isEmpty()) {
            return@withContext Result.success()
        }

        for (photo in remotePhotos) {
            val existing = memoryPhotoDao.getByCloudId(photo.id)
            val createdAt = parseCreatedAt(photo.createdAt)
            val entity = MemoryPhotoEntity(
                cloudId = photo.id,
                elderDeviceId = photo.elderDeviceId,
                familyDeviceId = photo.familyDeviceId,
                imageUrl = photo.imageUrl,
                thumbnailUrl = photo.thumbnailUrl,
                localPath = existing?.localPath,
                thumbnailPath = existing?.thumbnailPath,
                description = photo.description,
                aiDescription = photo.aiDescription,
                takenDate = photo.takenDate,
                location = photo.location,
                people = photo.people,
                tags = photo.tags,
                isDownloaded = existing?.isDownloaded ?: false,
                createdAt = createdAt,
                lastSyncAt = System.currentTimeMillis()
            )

            memoryPhotoDao.insert(entity)

            if (!entity.isDownloaded) {
                val cacheDir = File(applicationContext.cacheDir, "memory_photos").apply {
                    if (!exists()) mkdirs()
                }
                val imageFile = File(cacheDir, "${photo.id}.jpg")
                val thumbFile = photo.thumbnailUrl?.let { File(cacheDir, "${photo.id}_thumb.jpg") }

                val imageSaved = downloadToFile(photo.imageUrl, imageFile)
                val thumbSaved = photo.thumbnailUrl?.let { url ->
                    thumbFile?.let { downloadToFile(url, it) }
                } ?: false

                if (imageSaved) {
                    memoryPhotoDao.markAsDownloaded(
                        photo.id,
                        imageFile.absolutePath,
                        if (thumbSaved) thumbFile?.absolutePath else null
                    )
                }
            }
        }

        Result.success()
    }

    private fun parseCreatedAt(raw: String): Long {
        for (format in dateFormats) {
            try {
                val date = format.parse(raw)
                if (date != null) return date.time
            } catch (_: ParseException) {
                // try next
            }
        }
        return System.currentTimeMillis()
    }

    private fun downloadToFile(url: String, file: File): Boolean {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                FileOutputStream(file).use { out ->
                    out.write(body.bytes())
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            false
        }
    }
}
