package com.silverlink.app.feature.emotion

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.PI

/**
 * Audio preprocessor for the BERT+VGGish emotion model.
 * Decodes M4A/AAC to PCM and computes a VGGish-style mel spectrogram:
 * 1. Decode to 16kHz float PCM mono
 * 2. Peak-normalize
 * 3. Compute STFT (25ms window / 10ms hop / 512-point FFT)
 * 4. Apply 64 triangular mel filterbanks (125–7500 Hz, HTK scale)
 * 5. Log10-compress and pick 96 frames → output [96 × 64] = 6144 floats
 */
class AudioPreprocessor {

    companion object {
        private const val TAG = "AudioPreprocessor"
        const val SAMPLE_RATE = 16000

        // VGGish STFT parameters
        private const val WINDOW_SAMPLES = 400   // 25 ms at 16 kHz
        private const val HOP_SAMPLES = 160       // 10 ms at 16 kHz
        private const val FFT_SIZE = 512
        private const val MEL_BINS = 64
        private const val MEL_FRAMES = 96
        private const val MEL_FMIN = 125.0        // Hz
        private const val MEL_FMAX = 7500.0       // Hz
        private const val LOG_OFFSET = 0.01   // VGGish standard: np.log(mel + 0.01)
    }

    /**
     * Process an audio file and return a VGGish mel spectrogram for model input.
     * Output is [MEL_FRAMES × MEL_BINS] = 6144 floats, row-major (frames first).
     */
    fun processAudioToMelSpectrogram(filePath: String): FloatArray {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Audio file does not exist: $filePath")
        }

        val rawPcm = decodeToFloat(filePath)
        Log.d(TAG, "Decoded ${rawPcm.size} samples from $filePath")

        if (rawPcm.isEmpty()) {
            Log.w(TAG, "Empty audio, returning zero mel spectrogram")
            return FloatArray(MEL_FRAMES * MEL_BINS)
        }

        // Peak normalization
        val normalized = normalizeByPeak(rawPcm)

        // Compute mel spectrogram and select MEL_FRAMES frames from center
        val mel = computeMelSpectrogram(normalized)
        Log.d(TAG, "Mel spectrogram: ${mel.size / MEL_BINS} frames × $MEL_BINS bins")

        return mel
    }

    /**
     * Compute VGGish-style mel spectrogram.
     * Returns [MEL_FRAMES × MEL_BINS] floats (row-major), selecting MEL_FRAMES frames
     * from the center of the audio to represent the most emotionally relevant segment.
     */
    private fun computeMelSpectrogram(pcm: FloatArray): FloatArray {
        val hannWindow = FloatArray(WINDOW_SAMPLES) { n ->
            (0.5 * (1.0 - cos(2.0 * PI * n / (WINDOW_SAMPLES - 1)))).toFloat()
        }
        val melFilterbank = buildMelFilterbank()

        // Number of STFT frames from this audio
        val totalFrames = if (pcm.size < WINDOW_SAMPLES) 0 else
            (pcm.size - WINDOW_SAMPLES) / HOP_SAMPLES + 1

        // Compute how many frames we actually need to fill MEL_FRAMES output frames
        val needFrames = MEL_FRAMES
        val startFrame = if (totalFrames <= needFrames) 0 else (totalFrames - needFrames) / 2

        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        val output = FloatArray(MEL_FRAMES * MEL_BINS)

        for (outFrame in 0 until MEL_FRAMES) {
            val srcFrame = startFrame + outFrame
            val sampleStart = srcFrame * HOP_SAMPLES

            // Fill FFT buffer with windowed frame (zero-pad if out of range)
            re.fill(0.0)
            im.fill(0.0)
            for (k in 0 until WINDOW_SAMPLES) {
                val idx = sampleStart + k
                re[k] = if (idx < pcm.size) (pcm[idx] * hannWindow[k]).toDouble() else 0.0
            }

            fft(re, im)

            // Power spectrum (one-sided, bins 0..FFT_SIZE/2)
            val power = DoubleArray(FFT_SIZE / 2 + 1) { k -> re[k] * re[k] + im[k] * im[k] }

            // Apply mel filterbank and log-compress
            for (m in 0 until MEL_BINS) {
                var energy = 0.0
                for (k in power.indices) {
                    energy += melFilterbank[m][k] * power[k]
                }
                output[outFrame * MEL_BINS + m] = ln(energy.coerceAtLeast(LOG_OFFSET.toDouble())).toFloat()
            }
        }
        return output
    }

    /** Build 64 triangular mel filterbank weights, shape [MEL_BINS][FFT_SIZE/2+1]. */
    private fun buildMelFilterbank(): Array<DoubleArray> {
        val numBins = FFT_SIZE / 2 + 1
        val melMin = hzToMel(MEL_FMIN)
        val melMax = hzToMel(MEL_FMAX)

        // MEL_BINS + 2 linearly spaced points in mel scale
        val melPoints = DoubleArray(MEL_BINS + 2) { i ->
            melMin + i * (melMax - melMin) / (MEL_BINS + 1)
        }
        // Convert mel points to FFT bin indices
        val binIdx = IntArray(MEL_BINS + 2) { i ->
            ((melToHz(melPoints[i]) / (SAMPLE_RATE / 2.0)) * (numBins - 1)).toInt().coerceIn(0, numBins - 1)
        }

        return Array(MEL_BINS) { m ->
            DoubleArray(numBins) { k ->
                when {
                    k < binIdx[m] || k > binIdx[m + 2] -> 0.0
                    k <= binIdx[m + 1] -> {
                        val denom = (binIdx[m + 1] - binIdx[m]).toDouble()
                        if (denom <= 0) 0.0 else (k - binIdx[m]) / denom
                    }
                    else -> {
                        val denom = (binIdx[m + 2] - binIdx[m + 1]).toDouble()
                        if (denom <= 0) 0.0 else (binIdx[m + 2] - k) / denom
                    }
                }
            }
        }
    }

    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)

    /** In-place iterative Cooley-Tukey FFT (power-of-2 size). */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        // Butterfly iterations
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wr = cos(angle); val wi = kotlin.math.sin(angle)
            var step = 0
            while (step < n) {
                var wre = 1.0; var wim = 0.0
                for (k in 0 until len / 2) {
                    val i1 = step + k; val i2 = i1 + len / 2
                    val vr = wre * re[i2] - wim * im[i2]
                    val vi = wre * im[i2] + wim * re[i2]
                    re[i2] = re[i1] - vr; im[i2] = im[i1] - vi
                    re[i1] += vr;          im[i1] += vi
                    val tmp = wre * wr - wim * wi; wim = wre * wi + wim * wr; wre = tmp
                }
                step += len
            }
            len = len shl 1
        }
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

    /** Peak normalization to [-1, 1] while preserving waveform shape. */
    private fun normalizeByPeak(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val peak = samples.maxOf { abs(it) }
        if (peak <= 1e-8f) return samples
        return FloatArray(samples.size) { i -> samples[i] / peak }
    }
}
