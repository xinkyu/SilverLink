package com.silverlink.app.feature.chat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.silverlink.app.data.local.AppDatabase
import com.silverlink.app.data.local.MemoryRecordEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryMaintenanceServiceTest {

    private lateinit var db: AppDatabase
    private lateinit var service: MemoryMaintenanceService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = MemoryMaintenanceService(
            memoryDao = db.memoryDao(),
            pruneIntervalMs = 0L,
            retentionMs = 1000L,
            lowImportanceThreshold = 0.35f
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun upsertMemoryRecord_shouldDeduplicateByContent() {
        runBlocking {
            service.upsertMemoryRecord(
                conversationId = 1L,
                content = "我每天都去公园散步",
                keywordsText = "公园,散步",
                importance = 0.45f,
                now = 1000L
            )

            service.upsertMemoryRecord(
                conversationId = 1L,
                content = "我每天都去公园散步",
                keywordsText = "公园,散步,每天",
                importance = 0.8f,
                now = 2000L
            )

            val all = db.memoryDao().listAllMemories()
            assertEquals(1, all.size)
            assertEquals("公园,散步,每天", all.first().keywordsText)
            assertTrue(all.first().importance >= 0.8f)
            assertEquals(2000L, all.first().lastAccessAt)
        }
    }

    @Test
    fun pruneIfNeeded_shouldDeleteOnlyExpiredLowImportanceRecords() {
        runBlocking {
            val dao = db.memoryDao()
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "旧且低重要性",
                    keywordsText = "旧",
                    importance = 0.2f,
                    createdAt = 100L,
                    lastAccessAt = 100L
                )
            )
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "旧但高重要性",
                    keywordsText = "旧",
                    importance = 0.9f,
                    createdAt = 100L,
                    lastAccessAt = 100L
                )
            )
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "新但低重要性",
                    keywordsText = "新",
                    importance = 0.2f,
                    createdAt = 4500L,
                    lastAccessAt = 4500L
                )
            )

            service.pruneIfNeeded(now = 5000L)

            val remain = dao.listAllMemories().map { it.content }
            assertEquals(2, remain.size)
            assertTrue(remain.contains("旧但高重要性"))
            assertTrue(remain.contains("新但低重要性"))
        }
    }

    @Test
    fun upsertMemoryRecord_shouldMergeSimilarSentence() {
        runBlocking {
            service.upsertMemoryRecord(
                conversationId = 1L,
                content = "健康状况：高血压、糖尿病",
                keywordsText = "高血压,糖尿病",
                importance = 0.7f,
                now = 1000L
            )
            service.upsertMemoryRecord(
                conversationId = 1L,
                content = "健康状况：糖尿病、高血压",
                keywordsText = "糖尿病,高血压",
                importance = 0.9f,
                now = 2000L
            )

            val all = db.memoryDao().listAllMemories()
            assertEquals(1, all.size)
            assertTrue(all.first().importance >= 0.9f)
        }
    }
}
