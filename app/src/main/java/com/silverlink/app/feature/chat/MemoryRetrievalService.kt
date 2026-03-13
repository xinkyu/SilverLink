package com.silverlink.app.feature.chat

import com.silverlink.app.data.local.MemoryDao
import com.silverlink.app.data.local.MemoryRecordEntity
import kotlin.math.exp
import kotlin.math.max

interface MemoryRetrievalService {
    suspend fun retrieveRelevantMemories(query: String, limit: Int): List<MemoryRecordEntity>

    fun updateConfig(config: RagConfig) {}

    fun getConfig(): RagConfig = RagConfig()

    fun setDebugEnabled(enabled: Boolean) {}

    fun getLastDebugSnapshot(): RagDebugSnapshot? = null

    suspend fun buildGroundedContext(query: String, limit: Int, maxChars: Int = 520): String {
        val memories = retrieveRelevantMemories(query, limit)
        if (memories.isEmpty()) return ""
        val lines = memories.map { "- [记忆#${it.id}] ${it.content}" }
        val content = lines.joinToString("\n")
        return if (content.length <= maxChars) content else content.take(maxChars) + "…"
    }
}

class KeywordMemoryRetrievalService(
    private val memoryDao: MemoryDao,
    private val keywordExtractor: (String) -> List<String>
) : MemoryRetrievalService {

    override suspend fun retrieveRelevantMemories(query: String, limit: Int): List<MemoryRecordEntity> {
        val keywords = keywordExtractor(query)
        if (keywords.isEmpty()) {
            return memoryDao.getTopMemories(limit)
        }

        val merged = linkedMapOf<Long, MemoryRecordEntity>()
        keywords.forEach { keyword ->
            memoryDao.searchMemoryByKeyword(keyword, limit).forEach { record ->
                if (!merged.containsKey(record.id)) {
                    merged[record.id] = record
                }
            }
        }

        val result = merged.values
            .sortedWith(compareByDescending<MemoryRecordEntity> { it.importance }.thenByDescending { it.lastAccessAt })
            .take(limit)

        if (result.isNotEmpty()) {
            memoryDao.touchMemories(result.map { it.id })
        }
        return result
    }
}

data class RagConfig(
    val topK: Int = 3,
    val enableSemanticSimilarity: Boolean = true,
    val weightImportance: Float = 0.45f,
    val weightTokenOverlap: Float = 0.35f,
    val weightSemantic: Float = 0.15f,
    val weightRecency: Float = 0.05f
) {
    fun normalized(): RagConfig {
        val sum = max(0.0001f, weightImportance + weightTokenOverlap + weightSemantic + weightRecency)
        return copy(
            topK = topK.coerceIn(1, 8),
            weightImportance = weightImportance / sum,
            weightTokenOverlap = weightTokenOverlap / sum,
            weightSemantic = if (enableSemanticSimilarity) weightSemantic / sum else 0f,
            weightRecency = weightRecency / sum
        )
    }
}

data class RagDebugItem(
    val memoryId: Long,
    val content: String,
    val finalScore: Double,
    val matchedTerms: List<String>,
    val partImportance: Float,
    val partOverlap: Float,
    val partSemantic: Float,
    val partRecency: Float
)

data class RagDebugSnapshot(
    val query: String,
    val config: RagConfig,
    val selected: List<RagDebugItem>
)

class HybridMemoryRagService(
    private val memoryDao: MemoryDao,
    private val keywordExtractor: (String) -> List<String>
) : MemoryRetrievalService {

    private var config: RagConfig = RagConfig()
    private var debugEnabled: Boolean = false
    private var lastDebugSnapshot: RagDebugSnapshot? = null

    override fun updateConfig(config: RagConfig) {
        this.config = config.normalized()
    }

    override fun getConfig(): RagConfig = config

    override fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
    }

    override fun getLastDebugSnapshot(): RagDebugSnapshot? = lastDebugSnapshot

    override suspend fun retrieveRelevantMemories(query: String, limit: Int): List<MemoryRecordEntity> {
        return retrieveWithScores(query, limit).map { it.record }
    }

    override suspend fun buildGroundedContext(query: String, limit: Int, maxChars: Int): String {
        val ranked = retrieveWithScores(query, limit)
        if (ranked.isEmpty()) return ""

        val lines = ranked.map {
            val scoreText = "%.2f".format(it.score)
            val tags = if (it.matchedTerms.isEmpty()) "" else " 关键词:${it.matchedTerms.joinToString("/")}" 
            "- [记忆#${it.record.id}|score=$scoreText] ${it.record.content}$tags"
        }
        val content = lines.joinToString("\n")
        return if (content.length <= maxChars) content else content.take(maxChars) + "…"
    }

    private suspend fun retrieveWithScores(query: String, limit: Int): List<ScoredMemory> {
        val normalizedConfig = config.normalized()
        val effectiveLimit = normalizedConfig.topK.coerceAtMost(limit.coerceAtLeast(1))
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            val top = memoryDao.getTopMemories(effectiveLimit)
            val result = top.map {
                ScoredMemory(
                    record = it,
                    score = it.importance.toDouble(),
                    matchedTerms = emptyList(),
                    partImportance = it.importance,
                    partOverlap = 0f,
                    partSemantic = 0f,
                    partRecency = 0f
                )
            }
            maybeStoreDebugSnapshot(normalizedQuery, normalizedConfig, result)
            return result
        }

        val queryTerms = keywordExtractor(normalizedQuery)
        val queryTokens = tokenize(normalizedQuery)

        val merged = linkedMapOf<Long, MemoryRecordEntity>()

        queryTerms.forEach { keyword ->
            memoryDao.searchMemoryByKeyword(keyword, effectiveLimit * 2).forEach { record ->
                merged.putIfAbsent(record.id, record)
            }
        }
        memoryDao.listRecentMemories(effectiveLimit * 4).forEach { merged.putIfAbsent(it.id, it) }
        memoryDao.getTopMemories(effectiveLimit * 3).forEach { merged.putIfAbsent(it.id, it) }

        if (merged.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val ranked = merged.values
            .map { record ->
                val recordTokens = tokenize(record.content + " " + record.keywordsText)
                val termHits = queryTerms.filter { term ->
                    record.content.contains(term, ignoreCase = true) || record.keywordsText.contains(term, ignoreCase = true)
                }

                val overlap = tokenOverlapRatio(queryTokens, recordTokens)
                val semantic = if (normalizedConfig.enableSemanticSimilarity) {
                    ngramJaccard(normalizedQuery, record.content)
                } else {
                    0f
                }
                val recency = recencyScore(now, record.lastAccessAt)
                val directHitBonus = if (termHits.isNotEmpty()) 0.08f else 0f

                val partImportance = record.importance * normalizedConfig.weightImportance
                val partOverlap = overlap * normalizedConfig.weightTokenOverlap
                val partSemantic = semantic * normalizedConfig.weightSemantic
                val partRecency = recency * normalizedConfig.weightRecency

                val score = partImportance + partOverlap + partSemantic + partRecency + directHitBonus
                ScoredMemory(record, score.toDouble(), termHits, partImportance, partOverlap, partSemantic, partRecency)
            }
            .sortedByDescending { it.score }
            .take(effectiveLimit)

        if (ranked.isNotEmpty()) {
            memoryDao.touchMemories(ranked.map { it.record.id }, now)
        }

        maybeStoreDebugSnapshot(normalizedQuery, normalizedConfig, ranked)
        return ranked
    }

    private fun maybeStoreDebugSnapshot(query: String, config: RagConfig, ranked: List<ScoredMemory>) {
        if (!debugEnabled) return
        lastDebugSnapshot = RagDebugSnapshot(
            query = query,
            config = config,
            selected = ranked.map {
                RagDebugItem(
                    memoryId = it.record.id,
                    content = it.record.content,
                    finalScore = it.score,
                    matchedTerms = it.matchedTerms,
                    partImportance = it.partImportance,
                    partOverlap = it.partOverlap,
                    partSemantic = it.partSemantic,
                    partRecency = it.partRecency
                )
            }
        )
    }

    private fun tokenize(text: String): Set<String> {
        val normalized = text.lowercase()
            .replace(Regex("[\\n\\r\\t，。！？、；：,.!?;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val splitTerms = normalized.split(" ").filter { it.length >= 2 }
        val compact = normalized.replace(" ", "")
        val ngrams = mutableSetOf<String>()
        for (n in 2..3) {
            if (compact.length >= n) {
                for (i in 0..compact.length - n) {
                    ngrams.add(compact.substring(i, i + n))
                }
            }
        }
        return (splitTerms + ngrams).toSet()
    }

    private fun tokenOverlapRatio(queryTokens: Set<String>, recordTokens: Set<String>): Float {
        if (queryTokens.isEmpty() || recordTokens.isEmpty()) return 0f
        val hit = queryTokens.intersect(recordTokens).size.toFloat()
        return (hit / queryTokens.size).coerceIn(0f, 1f)
    }

    private fun ngramJaccard(a: String, b: String): Float {
        val ag = ngrams(a, 2)
        val bg = ngrams(b, 2)
        if (ag.isEmpty() || bg.isEmpty()) return 0f
        val intersection = ag.intersect(bg).size.toFloat()
        val union = ag.union(bg).size.toFloat()
        return if (union <= 0f) 0f else (intersection / union).coerceIn(0f, 1f)
    }

    private fun ngrams(text: String, n: Int): Set<String> {
        val clean = text.lowercase().replace(Regex("\\s+"), "").trim()
        if (clean.length < n) return emptySet()
        val result = mutableSetOf<String>()
        for (i in 0..clean.length - n) {
            result.add(clean.substring(i, i + n))
        }
        return result
    }

    private fun recencyScore(now: Long, lastAccessAt: Long): Float {
        val day = 24 * 60 * 60 * 1000.0
        val days = ((now - lastAccessAt).coerceAtLeast(0L)) / day
        return exp((-days / 7.0)).toFloat().coerceIn(0f, 1f)
    }

    private data class ScoredMemory(
        val record: MemoryRecordEntity,
        val score: Double,
        val matchedTerms: List<String>,
        val partImportance: Float,
        val partOverlap: Float,
        val partSemantic: Float,
        val partRecency: Float
    )
}
