package com.silverlink.app.feature.emotion

import com.silverlink.app.data.model.Emotion

/**
 * Maps model output probabilities to the app's Emotion enum.
 *
 * Text model (7 classes → 5 app classes):
 *   anger    → ANGRY
 *   disgust  → ANGRY   (merged: closest negative-arousal match)
 *   fear     → ANXIOUS
 *   joy      → HAPPY
 *   neutral  → NEUTRAL
 *   sadness  → SAD
 *   surprise → HAPPY   (merged: typically positive in elderly context)
 *
 * Speech model (4 classes → 5 app classes):
 *   neutral   → NEUTRAL
 *   happiness → HAPPY
 *   anger     → ANGRY
 *   sadness   → SAD
 *   (ANXIOUS is not produced by the speech model)
 */
object EmotionMapper {

    /**
     * Map text model (DistilRoBERTa) 7-class probabilities to app Emotion.
     * @param probabilities Ordered: anger, disgust, fear, joy, neutral, sadness, surprise
     */
    fun mapTextEmotion(probabilities: List<Float>): Emotion {
        require(probabilities.size == 7) { "Expected 7 probabilities, got ${probabilities.size}" }

        // Merge probabilities for combined classes
        val mergedProbs = mapOf(
            Emotion.ANGRY   to (probabilities[0] + probabilities[1]),  // anger + disgust
            Emotion.ANXIOUS to probabilities[2],                        // fear
            Emotion.HAPPY   to (probabilities[3] + probabilities[6]),  // joy + surprise
            Emotion.NEUTRAL to probabilities[4],                        // neutral
            Emotion.SAD     to probabilities[5]                         // sadness
        )

        return mergedProbs.maxByOrNull { it.value }?.key ?: Emotion.NEUTRAL
    }

    /**
     * Map speech model (DistilHuBERT) 4-class probabilities to app Emotion.
     * @param probabilities Ordered: anger, happiness, neutral, sadness
     */
    fun mapSpeechEmotion(probabilities: List<Float>): Emotion {
        require(probabilities.size == 4) { "Expected 4 probabilities, got ${probabilities.size}" }

        val mapping = arrayOf(
            Emotion.NEUTRAL, // index 0: neutral
            Emotion.HAPPY,   // index 1: happiness
            Emotion.ANGRY,   // index 2: anger
            Emotion.SAD      // index 3: sadness
        )

        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 2
        return mapping[maxIndex]
    }
}
