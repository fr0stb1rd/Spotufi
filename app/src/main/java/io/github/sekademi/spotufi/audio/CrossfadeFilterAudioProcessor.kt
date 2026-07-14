package io.github.sekademi.spotufi.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 [AudioProcessor] applying a real-time Biquad low-pass / high-pass filter to the
 * audio stream, for DJ-style crossfade transitions. Ported from SimpMusic.
 *
 * - [enabled], [cutoffFrequencyHz] and [filterType] are runtime-mutable (thread-safe).
 * - When [enabled] is false the audio passes through unmodified (near-zero overhead).
 * - Coefficient recalculation is lazy: only when cutoff or filter type changes.
 * - Supports PCM 16-bit (mono and stereo).
 */
@UnstableApi
class CrossfadeFilterAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) filter.reset()
            }
        }

    @Volatile
    var cutoffFrequencyHz: Float = 20000f
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    @Volatile
    var filterType: BiquadFilter.FilterType = BiquadFilter.FilterType.LOW_PASS
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    private val filter = BiquadFilter()

    @Volatile
    private var coefficientsDirty = true

    private var sampleRate = 0
    private var channelCount = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        coefficientsDirty = true
        return inputAudioFormat
    }

    // NOTE: do NOT override isActive() to true — onConfigure returns NOT_SET for
    // non-16-bit input (e.g. 24-bit hi-res FLAC), and claiming to be active with an
    // unset format broke the audio pipeline: lossless downloads played silently.
    // BaseAudioProcessor's isActive() correctly deactivates us so such streams
    // bypass the filter and reach the sink untouched.

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || sampleRate == 0) {
            val output = replaceOutputBuffer(remaining)
            copyBuffer(inputBuffer, output, remaining)
            output.flip()
            return
        }

        if (coefficientsDirty) {
            filter.updateCoefficients(
                cutoffHz = cutoffFrequencyHz,
                sampleRate = sampleRate,
                type = filterType,
            )
            coefficientsDirty = false
        }

        val output = replaceOutputBuffer(remaining)
        inputBuffer.order(ByteOrder.nativeOrder())

        when (channelCount) {
            1 -> processMonoBlock(inputBuffer, output)
            2 -> processStereoBlock(inputBuffer, output)
            else -> copyBuffer(inputBuffer, output, remaining)
        }

        output.flip()
    }

    private fun copyBuffer(src: ByteBuffer, dst: ByteBuffer, size: Int) {
        if (src === dst) {
            dst.position(0)
            dst.limit(size)
            return
        }
        val pos = src.position()
        for (i in 0 until size) {
            dst.put(src.get(pos + i))
        }
        src.position(pos + size)
    }

    private fun processMonoBlock(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 2) {
            val sample = input.short.toDouble() / Short.MAX_VALUE
            val filtered = filter.processSampleMono(sample)
            output.putShort((filtered.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
        }
    }

    private fun processStereoBlock(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            val left = input.short.toDouble() / Short.MAX_VALUE
            val right = input.short.toDouble() / Short.MAX_VALUE
            val (filteredL, filteredR) = filter.processStereo(left, right)
            output.putShort((filteredL.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
            output.putShort((filteredR.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        super.onFlush(streamMetadata)
        filter.reset()
    }

    override fun onReset() {
        super.onReset()
        enabled = false
        cutoffFrequencyHz = 20000f
        filterType = BiquadFilter.FilterType.LOW_PASS
        filter.reset()
    }
}
