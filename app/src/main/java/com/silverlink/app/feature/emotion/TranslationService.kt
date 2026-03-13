package com.silverlink.app.feature.emotion

import android.util.Log
import com.silverlink.app.data.remote.RetrofitClient
import com.silverlink.app.data.remote.model.Input
import com.silverlink.app.data.remote.model.Message
import com.silverlink.app.data.remote.model.QwenRequest

/**
 * Translates Chinese text to English using the existing Qwen API.
 * Used as a preprocessing step before text emotion classification,
 * since the DistilRoBERTa model is trained on English text only.
 */
class TranslationService {

    companion object {
        private const val TAG = "TranslationService"
    }

    /**
     * Translate Chinese text to English.
     * @return English translation, or the original text if translation fails
     */
    suspend fun translateToEnglish(chineseText: String): String {
        return try {
            val request = QwenRequest(
                input = Input(
                    messages = listOf(
                        Message(
                            "system",
                            "Translate the following Chinese text to English. " +
                                "Output ONLY the English translation, nothing else. " +
                                "Preserve the emotional tone and intent."
                        ),
                        Message("user", chineseText)
                    )
                )
            )

            val response = RetrofitClient.api.chat(request)
            val translation = response.output.choices?.firstOrNull()?.message?.content
                ?: response.output.text

            if (translation.isNullOrBlank()) {
                Log.w(TAG, "Empty translation, returning original text")
                chineseText
            } else {
                Log.d(TAG, "Translated: '$chineseText' -> '$translation'")
                translation.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed, returning original text", e)
            chineseText
        }
    }
}
