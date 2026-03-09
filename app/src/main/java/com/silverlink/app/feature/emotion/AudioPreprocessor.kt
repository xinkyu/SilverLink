package com.silverlink.app.feature.emotion

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Audio preprocessor for the MemoCMT cross-modal emotion model.
 * Decodes M4A/AAC to PCM and prepares raw waveform input:
 * 1. Decode to 16kHz float PCM mono
 * 2. Normalize to [-1.0, 1.0]
 * 3. Pad/truncate to 160,220 samples (~10 seconds)
 */
class AudioPreprocessor {

    companion object {
        private const val TAG = "AudioPreprocessor"
        const val SAMPLE_RATE = 16000
        const val MAX_SAMPLES = 160220 // ~10.01 seconds at 16kHz
        private const val TRIM_TOP_DB = 25.0
    }

    /**
     * Process an audio file and return a float waveform for MemoCMT model input.
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

        val decodedRms = computeRms(rawPcm)
        val decodedPeak = rawPcm.maxOf { abs(it) }

        // 2) VAD-like trim (remove leading/trailing low-energy region)
        val trimmed = trimSilence(rawPcm, TRIM_TOP_DB)

        // 3) Peak normalization to align with notebook preprocessing
        val normalized = normalizeByPeak(trimmed)

        // 4) Pad or truncate to exactly MAX_SAMPLES
        val result = padOrTruncate(normalized, MAX_SAMPLES)
        val finalRms = computeRms(result)
        val finalPeak = result.maxOf { abs(it) }
        Log.d(
            TAG,
            "Waveform stats: decodedLen=${rawPcm.size}, trimmedLen=${trimmed.size}, " +
                "decodedRms=${"%.6f".format(decodedRms)}, decodedPeak=${"%.6f".format(decodedPeak)}, " +
                "finalLen=${result.size}, finalRms=${"%.6f".format(finalRms)}, finalPeak=${"%.6f".format(finalPeak)}"
        )

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
                if (bufferInfo.size > 0) {
                    // Respect codec buffer boundaries; otherwise stale bytes can corrupt waveform.
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    val pcmBytes = ByteArray(bufferInfo.size)
                    outputBuffer.get(pcmBytes)

                    val shortBuffer = ByteBuffer.wrap(pcmBytes)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()

                    // Convert Int16 PCM to Float32 [-1.0, 1.0]
                    val numSamples = shortBuffer.remaining()
                    for (i in 0 until numSamples) {
                        val sample = shortBuffer.get()
                        // If stereo, take only the first channel (left)
                        if (channelCount == 1 || i % channelCount == 0) {
                            pcmSamples.add(sample.toFloat() / 32768.0f)
                        }
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

    /**
     * Trim leading/trailing low-energy samples, similar to librosa.effects.trim(top_db=25).
     */
    private fun trimSilence(samples: FloatArray, topDb: Double): FloatArray {
        if (samples.isEmpty()) return samples
        val peak = samples.maxOf { abs(it) }
        if (peak <= 1e-8f) return samples

        // Relative amplitude threshold from dB: amp_ratio = 10^(-db/20)
        val threshold = peak * Math.pow(10.0, -topDb / 20.0).toFloat()

        var start = 0
        while (start < samples.size && abs(samples[start]) < threshold) {
            start++
        }

        var end = samples.size - 1
        while (end >= start && abs(samples[end]) < threshold) {
            end--
        }

        return if (start > end) {
            // Keep minimal content if everything is below threshold
            samples
        } else {
            samples.copyOfRange(start, end + 1)
        }
    }

    /** Peak normalization to [-1, 1] while preserving waveform shape. */
    private fun normalizeByPeak(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val peak = samples.maxOf { abs(it) }
        if (peak <= 1e-8f) return samples
        return FloatArray(samples.size) { i -> samples[i] / peak }
    }

    private fun computeRms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        val meanSquare = samples.fold(0.0) { acc, v -> acc + (v * v) } / samples.size
        return sqrt(meanSquare)
    }
}
