package com.silverlink.app.feature.chat.realtime

class RealtimeAudioSegmentBuffer(private val maxFrames: Int = 200) {
    private val frames = ArrayDeque<ShortArray>()

    fun append(frame: ShortArray) {
        if (frames.size >= maxFrames) {
            frames.removeFirst()
        }
        frames.addLast(frame)
    }

    fun reset() {
        frames.clear()
    }

    fun toPcmBytes(): ByteArray {
        val totalSamples = frames.sumOf { it.size }
        val output = ByteArray(totalSamples * 2)
        var offset = 0
        frames.forEach { frame ->
            frame.forEach { sample ->
                output[offset++] = (sample.toInt() and 0xFF).toByte()
                output[offset++] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
        }
        return output
    }
}
