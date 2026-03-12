package com.silverlink.app.feature.emotion

import com.silverlink.app.data.model.Emotion

/**
 * Maps MemoCMT cross-modal model output to the app's Emotion enum.
 *
 * MemoCMT model (4 classes → 5 app classes):
 *   Angry   (index 0) → ANGRY
 *   Happy   (index 1) → HAPPY
 *   Sad     (index 2) → SAD
 *   Neutral (index 3) → NEUTRAL
 *   (ANXIOUS is not produced by the model)
 */
object EmotionMapper {

    /**
     * Map MemoCMT 4-class label to app Emotion.
     * @param label One of "Angry", "Happy", "Sad", "Neutral"
     */
    fun mapLabel(label: String): Emotion {
        return when (label) {
            "Angry" -> Emotion.ANGRY
            "Happy" -> Emotion.HAPPY
            "Sad" -> Emotion.SAD
            "Neutral" -> Emotion.NEUTRAL
            else -> Emotion.NEUTRAL
        }
    }

    /**
     * Map MemoCMT 4-class result using EmotionResult.
     */
    fun mapResult(result: EmotionResult): Emotion {
        return mapLabel(result.label)
    }
}
