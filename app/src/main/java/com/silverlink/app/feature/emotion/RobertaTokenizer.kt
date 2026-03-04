package com.silverlink.app.feature.emotion

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Byte-level BPE tokenizer for DistilRoBERTa.
 * Loads vocab.json and merges.txt from assets and produces input_ids + attention_mask.
 */
class RobertaTokenizer {

    companion object {
        private const val TAG = "RobertaTokenizer"
        private const val MAX_LENGTH = 512
        private const val BOS_TOKEN_ID = 0L  // <s>
        private const val EOS_TOKEN_ID = 2L  // </s>
        private const val PAD_TOKEN_ID = 1L  // <pad>
        private const val UNK_TOKEN_ID = 3L  // <unk>
    }

    private lateinit var vocab: Map<String, Int>
    private lateinit var mergeRanks: Map<Pair<String, String>, Int>
    private lateinit var byteToUnicode: Map<Int, Char>
    private lateinit var unicodeToByte: Map<Char, Int>

    // GPT-2/RoBERTa pre-tokenization pattern
    private val preTokenizePattern = Regex(
        """'s|'t|'re|'ve|'m|'ll|'d| ?\p{L}+| ?\p{N}+| ?[^\s\p{L}\p{N}]+|\s+"""
    )

    fun initialize(context: Context) {
        // Load vocab
        val vocabStream = context.assets.open("tokenizer/vocab.json")
        val vocabJson = vocabStream.bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, Int>>() {}.type
        vocab = Gson().fromJson(vocabJson, type)
        Log.d(TAG, "Loaded vocab with ${vocab.size} tokens")

        // Load merges
        val mergesStream = context.assets.open("tokenizer/merges.txt")
        val reader = BufferedReader(InputStreamReader(mergesStream))
        val merges = mutableListOf<Pair<String, String>>()
        reader.useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("#") || line.isBlank()) return@forEach
                val parts = line.split(" ")
                if (parts.size == 2) {
                    merges.add(parts[0] to parts[1])
                }
            }
        }
        mergeRanks = merges.withIndex().associate { (index, pair) -> pair to index }
        Log.d(TAG, "Loaded ${mergeRanks.size} merge rules")

        // Build byte-to-unicode mapping (GPT-2/RoBERTa standard)
        byteToUnicode = buildByteToUnicode()
        unicodeToByte = byteToUnicode.entries.associate { (k, v) -> v to k }
    }

    /**
     * Encode text to token IDs with attention mask.
     */
    fun encode(text: String): TokenizerOutput {
        // Pre-tokenize
        val words = preTokenizePattern.findAll(text).map { it.value }.toList()

        // BPE encode each word
        val allTokenIds = mutableListOf<Long>()
        allTokenIds.add(BOS_TOKEN_ID) // <s>

        for (word in words) {
            // Convert bytes to unicode representation
            val unicodeWord = word.toByteArray(Charsets.UTF_8).map { byte ->
                byteToUnicode[byte.toInt() and 0xFF] ?: '?'
            }.joinToString("")

            // Apply BPE
            val bpeTokens = bpe(unicodeWord)

            // Look up vocab IDs
            for (token in bpeTokens) {
                val id = vocab[token]
                if (id != null) {
                    allTokenIds.add(id.toLong())
                } else {
                    allTokenIds.add(UNK_TOKEN_ID)
                }
            }
        }

        allTokenIds.add(EOS_TOKEN_ID) // </s>

        // Truncate if needed
        val truncated = if (allTokenIds.size > MAX_LENGTH) {
            allTokenIds.subList(0, MAX_LENGTH - 1).toMutableList().also {
                it.add(EOS_TOKEN_ID)
            }
        } else {
            allTokenIds
        }

        val inputIds = truncated.toLongArray()
        val attentionMask = LongArray(inputIds.size) { 1L }

        return TokenizerOutput(inputIds, attentionMask)
    }

    /**
     * Apply BPE merges to a word represented in unicode byte encoding.
     */
    private fun bpe(token: String): List<String> {
        if (token.isEmpty()) return emptyList()
        if (token.length == 1) return listOf(token)

        var word = token.map { it.toString() }.toMutableList()

        while (word.size > 1) {
            // Find the pair with the lowest merge rank
            var bestPair: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE

            for (i in 0 until word.size - 1) {
                val pair = word[i] to word[i + 1]
                val rank = mergeRanks[pair]
                if (rank != null && rank < bestRank) {
                    bestRank = rank
                    bestPair = pair
                }
            }

            if (bestPair == null) break

            // Merge all occurrences of the best pair
            val newWord = mutableListOf<String>()
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == bestPair.first && word[i + 1] == bestPair.second) {
                    newWord.add(bestPair.first + bestPair.second)
                    i += 2
                } else {
                    newWord.add(word[i])
                    i++
                }
            }
            word = newWord
        }

        return word
    }

    /**
     * Build the byte-to-unicode mapping used by GPT-2/RoBERTa tokenizers.
     * Maps bytes 0-255 to unicode characters, avoiding control characters.
     */
    private fun buildByteToUnicode(): Map<Int, Char> {
        val bs = mutableListOf<Int>()
        val cs = mutableListOf<Int>()

        // Printable ASCII and Latin-1 Supplement ranges
        for (b in '!'.code..'~'.code) { bs.add(b); cs.add(b) }
        for (b in '¡'.code..'¬'.code) { bs.add(b); cs.add(b) }
        for (b in '®'.code..'ÿ'.code) { bs.add(b); cs.add(b) }

        // Map remaining bytes to higher unicode codepoints
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }

        return bs.zip(cs).associate { (b, c) -> b to c.toChar() }
    }
}

data class TokenizerOutput(
    val inputIds: LongArray,
    val attentionMask: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TokenizerOutput) return false
        return inputIds.contentEquals(other.inputIds) && attentionMask.contentEquals(other.attentionMask)
    }

    override fun hashCode(): Int {
        return 31 * inputIds.contentHashCode() + attentionMask.contentHashCode()
    }
}
