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
class HybridMemoryRagServiceTest {

    private lateinit var db: AppDatabase
    private lateinit var service: HybridMemoryRagService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        service = HybridMemoryRagService(
            memoryDao = db.memoryDao(),
            keywordExtractor = { text ->
                text.split(" ").map { it.trim() }.filter { it.length >= 2 }
            }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun retrieveRelevantMemories_shouldUseHybridRanking() {
        runBlocking {
            val dao = db.memoryDao()
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "健康状况：高血压，最近头晕",
                    keywordsText = "高血压,头晕",
                    importance = 0.95f,
                    createdAt = 1L,
                    lastAccessAt = 1L
                )
            )
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "喜欢下棋",
                    keywordsText = "下棋",
                    importance = 0.2f,
                    createdAt = 2L,
                    lastAccessAt = 2L
                )
            )

            val result = service.retrieveRelevantMemories("血压有点高", limit = 1)
            assertTrue(result.isNotEmpty())
            assertTrue(result.first().content.contains("高血压"))
        }
    }

    @Test
    fun buildGroundedContext_shouldContainCitationAndScore() {
        runBlocking {
            val dao = db.memoryDao()
            val id = dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "家庭关系提及：儿子、孙女",
                    keywordsText = "儿子,孙女",
                    importance = 0.8f,
                    createdAt = 100L,
                    lastAccessAt = 100L
                )
            )

            val context = service.buildGroundedContext("家里人", limit = 2, maxChars = 300)
            assertTrue(context.contains("记忆#$id"))
            assertTrue(context.contains("score="))
        }
    }

    @Test
    fun ragConfig_shouldControlTopKAndDebugSnapshot() {
        runBlocking {
            val dao = db.memoryDao()
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "记忆一：高血压",
                    keywordsText = "高血压",
                    importance = 0.9f,
                    createdAt = 1L,
                    lastAccessAt = 1L
                )
            )
            dao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = 1,
                    content = "记忆二：散步",
                    keywordsText = "散步",
                    importance = 0.8f,
                    createdAt = 2L,
                    lastAccessAt = 2L
                )
            )

            service.updateConfig(
                RagConfig(
                    topK = 1,
                    enableSemanticSimilarity = false,
                    weightImportance = 0.7f,
                    weightTokenOverlap = 0.2f,
                    weightSemantic = 0.0f,
                    weightRecency = 0.1f
                )
            )
            service.setDebugEnabled(true)

            val result = service.retrieveRelevantMemories("高血压", limit = 5)
            assertEquals(1, result.size)

            val debug = service.getLastDebugSnapshot()
            assertTrue(debug != null)
            assertTrue(debug!!.selected.isNotEmpty())
            assertEquals(1, debug.config.topK)
            assertEquals(false, debug.config.enableSemanticSimilarity)
        }
    }
}
