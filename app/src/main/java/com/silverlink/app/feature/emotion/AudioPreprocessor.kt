package com.silverlink.app.feature.emotion

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Audio preprocessor for the DistilHuBERT speech emotion model.
 * Decodes M4A/AAC to PCM and applies the required preprocessing pipeline:
 * 1. Decode to 16kHz float PCM
 * 2. Top-threshold silence trim at 20dB
 * 3. Pre-emphasis filter: y[n] = x[n] - 0.97 * x[n-1]
 * 4. Peak normalize to 0.95
 * 5. Pad/truncate to 8 seconds (128,000 samples)
 */
class AudioPreprocessor {

    companion object {
        private const val TAG = "AudioPreprocessor"
        const val SAMPLE_RATE = 16000
        const val MAX_DURATION_SECONDS = 8.0f
        val MAX_SAMPLES = (SAMPLE_RATE * MAX_DURATION_SECONDS).toInt() // 128000
        const val PRE_EMPHASIS_COEFF = 0.97f
        const val PEAK_NORMALIZE_TARGET = 0.95f
        const val TRIM_THRESHOLD_DB = 20.0f
    }

    /**
     * Process an audio file and return a preprocessed float waveform.
     */
    fun processAudioFile(filePath: String): FloatArray {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Audio file does not exist: $filePath")
        }

        // 1. Decode M4A/AAC to raw PCM float samples
        val rawPcm = decodeToFloat(filePath)
        Log.d(TAG, "Decoded ${rawPcm.size} samples from $filePath")

        if (rawPcm.isEmpty()) {
            Log.w(TAG, "Empty audio, returning zero-padded waveform")
            return FloatArray(MAX_SAMPLES)
        }

        // 2. Top-threshold trim at 20dB below peak
        val trimmed = trimSilence(rawPcm, TRIM_THRESHOLD_DB)
        Log.d(TAG, "Trimmed to ${trimmed.size} samples")

        // 3. Pre-emphasis filter
        val emphasized = applyPreEmphasis(trimmed, PRE_EMPHASIS_COEFF)

        // 4. Peak normalize to 0.95
        val normalized = peakNormalize(emphasized, PEAK_NORMALIZE_TARGET)

        // 5. Pad or truncate to exactly MAX_SAMPLES
        val result = padOrTruncate(normalized, MAX_SAMPLES)
        Log.d(TAG, "Final waveform: ${result.size} samples")

        return result
    }

    /**
     * Decode M4A/AAC to float PCM samples using MediaExtractor + MediaCodec.
     */
    private fun decodeToFloat(filePath: String): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(filePath)

        // Find audio track
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || audioFormat == null) {
            extractor.release()
            throw IllegalArgumentException("No audio track found in file")
        }

        extractor.selectTrack(audioTrackIndex)

        val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        Log.d(TAG, "Audio: mime=$mime, sampleRate=$sampleRate, channels=$channelCount")

        // Create decoder
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(audioFormat, null, null, 0)
        codec.start()

        val pcmSamples = mutableListOf<Float>()
        val bufferInfo = MediaCodec.BufferInfo()
        var isEndOfStream = false

        while (true) {
            // Feed input
            if (!isEndOfStream) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputBufferIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        isEndOfStream = true
                    } else {
                        codec.queueInputBuffer(
                            inputBufferIndex, 0, sampleSize,
                            extractor.sampleTime, 0
                        )
                        extractor.advance()
                    }
                }
            }

            // Read output
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                // Convert Int16 PCM to Float32 [-1.0, 1.0]
                val shortBuffer = outputBuffer.asShortBuffer()
                val numSamples = shortBuffer.remaining()
                for (i in 0 until numSamples) {
                    val sample = shortBuffer.get()
                    // If stereo, take only the first channel (left)
                    if (channelCount == 1 || i % channelCount == 0) {
                        pcmSamples.add(sample.toFloat() / 32768.0f)
                    }
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "Output format changed: ${codec.outputFormat}")
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        // Resample to 16kHz if necessary
        val result = pcmSamples.toFloatArray()
        return if (sampleRate != SAMPLE_RATE) {
            resample(result, sampleRate, SAMPLE_RATE)
        } else {
            result
        }
    }

    /**
     * Simple linear interpolation resampling.
     */
    private fun resample(input: FloatArray, inputRate: Int, outputRate: Int): FloatArray {
        if (inputRate == outputRate) return input
        val ratio = inputRate.toDouble() / outputRate.toDouble()
        val outputLength = (input.size / ratio).toInt()
        val output = FloatArray(outputLength)
        for (i in 0 until outputLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val frac = (srcPos - srcIndex).toFloat()
            output[i] = if (srcIndex + 1 < input.size) {
                input[srcIndex] * (1 - frac) + input[srcIndex + 1] * frac
            } else {
                input[srcIndex.coerceAtMost(input.size - 1)]
            }
        }
        return output
    }

    /**
     * Trim silence from the beginning and end of the signal.
     * Removes samples below thresholdDb below the peak amplitude.
     */
    private fun trimSilence(samples: FloatArray, thresholdDb: Float): FloatArray {
        if (samples.isEmpty()) return samples

        val peak = samples.maxOf { abs(it) }
        if (peak == 0f) return samples

        val threshold = peak * 10f.pow(-thresholdDb / 20f)

        var start = 0
        while (start < samples.size && abs(samples[start]) < threshold) {
            start++
        }

        var end = samples.size - 1
        while (end > start && abs(samples[end]) < threshold) {
            end--
        }

        return if (start >= end) {
            samples // Don't trim to empty
        } else {
            samples.copyOfRange(start, end + 1)
        }
    }

    /**
     * Apply pre-emphasis filter: y[n] = x[n] - coeff * x[n-1]
     */
    private fun applyPreEmphasis(samples: FloatArray, coeff: Float): FloatArray {
        if (samples.isEmpty()) return samples
        val result = FloatArray(samples.size)
        result[0] = samples[0]
        for (i in 1 until samples.size) {
            result[i] = samples[i] - coeff * samples[i - 1]
        }
        return result
    }

    /**
     * Peak normalize to target max amplitude.
     */
    private fun peakNormalize(samples: FloatArray, target: Float): FloatArray {
        if (samples.isEmpty()) return samples
        val maxAbs = samples.maxOf { abs(it) }
        if (maxAbs == 0f) return samples
        val scale = target / maxAbs
        return FloatArray(samples.size) { samples[it] * scale }
    }

    /**
     * Pad with zeros or truncate to exactly targetLength samples.
     */
    private fun padOrTruncate(samples: FloatArray, targetLength: Int): FloatArray {
        return when {
            samples.size == targetLength -> samples
            samples.size > targetLength -> samples.copyOfRange(0, targetLength)
            else -> {
                val padded = FloatArray(targetLength)
                samples.copyInto(padded)
                padded
            }
        }
    }
}
