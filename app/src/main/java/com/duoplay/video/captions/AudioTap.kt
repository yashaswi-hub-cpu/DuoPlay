package com.duoplay.video.captions

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * No-op audio tap. Captions are disabled in Duo Play, so this processor
 * passes audio through untouched. Kept so PlayerScreen's ExoPlayer
 * setup can still reference an AudioTap instance.
 */
class AudioTap(
    @Suppress("UNUSED_PARAMETER") onChunk: (ByteArray, Long, Long) -> Unit,
    @Suppress("UNUSED_PARAMETER") onDiscontinuity: () -> Unit
) : BaseAudioProcessor() {

    var enabled: Boolean = false
    var pendingBaseMs: Long = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }
}
