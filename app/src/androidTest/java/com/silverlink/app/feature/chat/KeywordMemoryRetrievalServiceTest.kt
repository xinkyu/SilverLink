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
class KeywordMemoryRetrievalServiceTest {

    private lateinit var db: AppDatabase
    private lateinit var service: KeywordMemoryRetrievalService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        service = KeywordMemoryRetrievalService(
            memoryDao = db.memoryDao(),
            keywordExtractor = { query ->
                query.split(" ").map { it.trim() }.filter { it.length >= 2 }
            }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun retrieveRelevantMemories_shouldReturnKeywordMatchesByImportance() {
        runBlocking {
            val dao = db.memoryDao()
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "有高血压，最近头晕",
                    keywordsText = "高血压,头晕",
                    importance = 0.95f,
                    createdAt = 1,
                    lastAccessAt = 1
                )
            )
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "喜欢下棋",
                    keywordsText = "下棋",
                    importance = 0.3f,
                    createdAt = 2,
                    lastAccessAt = 2
                )
            )

            val result = service.retrieveRelevantMemories("高血压 头晕", limit = 3)
            assertEquals(1, result.size)
            assertTrue(result.first().content.contains("高血压"))
        }
    }

    @Test
    fun retrieveRelevantMemories_whenNoKeywords_shouldFallbackTopMemories() {
        runBlocking {
            val dao = db.memoryDao()
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "记忆A",
                    keywordsText = "",
                    importance = 0.8f,
                    createdAt = 1,
                    lastAccessAt = 1
                )
            )
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "记忆B",
                    keywordsText = "",
                    importance = 0.4f,
                    createdAt = 2,
                    lastAccessAt = 2
                )
            )

            val result = service.retrieveRelevantMemories("a", limit = 1)
            assertEquals(1, result.size)
            assertEquals("记忆A", result.first().content)
        }
    }
}
