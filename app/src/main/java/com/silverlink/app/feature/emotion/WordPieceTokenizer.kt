package com.silverlink.app.feature.emotion

import android.content.Context
import android.util.Log

/**
 * WordPiece tokenizer for DistilBERT (distilbert-base-uncased).
 * Loads vocab.txt from assets and produces token IDs for the MemoCMT model.
 *
 * Special tokens: [CLS]=101, [SEP]=102, [PAD]=0, [UNK]=100
 */
class WordPieceTokenizer {

    companion object {
        private const val TAG = "WordPieceTokenizer"
        private const val MAX_LENGTH = 297
        private const val DEFAULT_CLS_TOKEN_ID = 101L
        private const val DEFAULT_SEP_TOKEN_ID = 102L
        private const val DEFAULT_PAD_TOKEN_ID = 0L
        private const val DEFAULT_UNK_TOKEN_ID = 100L
        private const val MAX_WORD_CHARS = 200
    }

    private lateinit var vocab: Map<String, Int>
    private var clsTokenId: Long = DEFAULT_CLS_TOKEN_ID
    private var sepTokenId: Long = DEFAULT_SEP_TOKEN_ID
    private var padTokenId: Long = DEFAULT_PAD_TOKEN_ID
    private var unkTokenId: Long = DEFAULT_UNK_TOKEN_ID

    // Pattern to split on whitespace and punctuation, keeping punctuation as separate tokens
    private val splitPattern = Regex("""[^\s\p{P}]+|\p{P}""")

    fun initialize(context: Context) {
        vocab = context.assets.open("tokenizer/vocab.txt").bufferedReader()
            .readLines()
            .mapIndexed { index, word -> word to index }
            .toMap()

        // Prefer special token IDs from the actual vocab to avoid hardcoded-ID mismatch.
        clsTokenId = vocab["[CLS]"]?.toLong() ?: DEFAULT_CLS_TOKEN_ID
        sepTokenId = vocab["[SEP]"]?.toLong() ?: DEFAULT_SEP_TOKEN_ID
        padTokenId = vocab["[PAD]"]?.toLong() ?: DEFAULT_PAD_TOKEN_ID
        unkTokenId = vocab["[UNK]"]?.toLong() ?: DEFAULT_UNK_TOKEN_ID

        Log.d(TAG, "Loaded WordPiece vocab with ${vocab.size} tokens")
        Log.d(
            TAG,
            "Special token IDs from vocab: PAD=$padTokenId, UNK=$unkTokenId, CLS=$clsTokenId, SEP=$sepTokenId"
        )
    }

    /**
     * Encode text to DistilBERT token IDs.
     * Text is lowercased (uncased model), split into words, then WordPiece tokenized.
     * Output is padded to MAX_LENGTH (297) with [PAD]=0.
     */
    fun encode(text: String): LongArray {
        val tokens = mutableListOf(clsTokenId)
        val words = splitPattern.findAll(text.lowercase()).map { it.value }.toList()

        for (word in words) {
            val subTokens = wordPieceTokenize(word)
            // Leave room for [SEP]
            if (tokens.size + subTokens.size >= MAX_LENGTH) break
            tokens.addAll(subTokens)
        }
        tokens.add(sepTokenId)

        val result = LongArray(MAX_LENGTH) { padTokenId }
        for (i in tokens.indices.take(MAX_LENGTH)) {
            result[i] = tokens[i]
        }
        return result
    }

    /**
     * WordPiece tokenization for a single word.
     * Tries to match the longest sub-word from left to right.
     * Sub-words after the first are prefixed with "##".
     */
    private fun wordPieceTokenize(word: String): List<Long> {
        if (word.length > MAX_WORD_CHARS) {
            return listOf(unkTokenId)
        }

        val tokens = mutableListOf<Long>()
        var start = 0
        while (start < word.length) {
            var found = false
            var end = word.length
            while (end > start) {
                val sub = if (start == 0) {
                    word.substring(0, end)
                } else {
                    "##${word.substring(start, end)}"
                }
                val id = vocab[sub]
                if (id != null) {
                    tokens.add(id.toLong())
                    start = end
                    found = true
                    break
                }
                end--
            }
            if (!found) {
                tokens.add(unkTokenId)
                break
            }
        }
        return tokens
    }
}
