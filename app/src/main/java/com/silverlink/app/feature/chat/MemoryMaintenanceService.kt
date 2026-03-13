package com.silverlink.app.feature.chat

import com.silverlink.app.data.local.MemoryDao
import com.silverlink.app.data.local.MemoryRecordEntity
import com.silverlink.app.data.local.UserProfileMemoryEntity
import kotlin.math.max

class MemoryMaintenanceService(
    private val memoryDao: MemoryDao,
    private val pruneIntervalMs: Long = 6 * 60 * 60 * 1000L,
    private val retentionMs: Long = 30L * 24 * 60 * 60 * 1000,
    private val lowImportanceThreshold: Float = 0.35f,
    private val similarityThreshold: Double = 0.82
) {
    private var lastPruneAt: Long = 0L

    suspend fun upsertMemoryRecord(
        conversationId: Long,
        content: String,
        keywordsText: String,
        importance: Float,
        now: Long = System.currentTimeMillis()
    ) {
        val normalized = content.trim()
        if (normalized.isBlank()) return

        val existing = memoryDao.findLatestByExactContent(normalized)
        val similar = findMostSimilarRecentMemory(normalized)
        val target = existing ?: similar
        if (target != null) {
            memoryDao.updateMemoryRecord(
                target.copy(
                    keywordsText = keywordsText,
                    importance = max(target.importance, importance),
                    lastAccessAt = now
                )
            )
        } else {
            memoryDao.insertMemoryRecord(
                MemoryRecordEntity(
                    sourceConversationId = conversationId,
                    content = normalized,
                    keywordsText = keywordsText,
                    importance = importance,
                    createdAt = now,
                    lastAccessAt = now
                )
            )
        }
        pruneIfNeeded(now)
    }

    private suspend fun findMostSimilarRecentMemory(content: String): MemoryRecordEntity? {
        val recent = memoryDao.listRecentMemories(20)
        if (recent.isEmpty()) return null

        val contentTokens = tokenize(content)
        if (contentTokens.isEmpty()) return null

        return recent
            .asSequence()
            .map { it to jaccard(contentTokens, tokenize(it.content)) }
            .filter { it.second >= similarityThreshold }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .replace(Regex("[\\n\\r\\t，。！？、；：,.!?;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.length >= 2 }
            .toSet()
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    suspend fun mergeProfileMemory(
        key: String,
        newValue: String,
        newConfidence: Float,
        now: Long = System.currentTimeMillis()
    ) {
        val value = newValue.trim()
        if (value.isBlank()) return

        val existing = memoryDao.getUserProfileMemory(key)
        if (existing == null) {
            memoryDao.upsertUserProfileMemory(
                UserProfileMemoryEntity(
                    key = key,
                    value = value,
                    confidence = newConfidence.coerceIn(0f, 1f),
                    updatedAt = now
                )
            )
            return
        }

        if (existing.value == value) {
            memoryDao.upsertUserProfileMemory(
                existing.copy(
                    confidence = (max(existing.confidence, newConfidence) + 0.03f).coerceIn(0f, 1f),
                    updatedAt = now
                )
            )
            return
        }

        val mergedValue = mergeProfileValueByKey(key, existing.value, value)
        val replaceThreshold = existing.confidence + 0.15f
        val finalValue = when {
            mergedValue != null -> mergedValue
            newConfidence >= replaceThreshold -> value
            else -> existing.value
        }
        val finalConfidence = when {
            finalValue == value && finalValue != existing.value -> newConfidence
            finalValue == existing.value -> (existing.confidence * 0.98f).coerceIn(0f, 1f)
            else -> max(existing.confidence, newConfidence)
        }

        memoryDao.upsertUserProfileMemory(
            existing.copy(value = finalValue, confidence = finalConfidence, updatedAt = now)
        )
    }

    private fun mergeProfileValueByKey(key: String, oldValue: String, newValue: String): String? {
        return when (key) {
            "health_conditions", "family_relations" -> {
                (oldValue.split('、') + newValue.split('、'))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("、")
            }
            else -> null
        }
    }

    suspend fun pruneIfNeeded(now: Long = System.currentTimeMillis()) {
        if (now - lastPruneAt < pruneIntervalMs) return
        val expireBefore = now - retentionMs
        memoryDao.pruneLowImportanceMemories(expireBefore, lowImportanceThreshold)
        lastPruneAt = now
    }
}
