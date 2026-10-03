package app.n_zik.android.playback.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import java.nio.ByteBuffer

/**
 * Silences the PCM it receives while [isMuted] is `true`, without touching the player's state
 * or `player.volume` (which fades, crossfade and the global volume rewrite). Used for the PC
 * audio output (bridge contract §8.5): the phone keeps playing — position, queue, counters —
 * but nothing comes out. While [isMuted] is `false` the audio passes through unchanged.
 *
 * [isMuted] is read for every buffer, from the playback thread: it must be cheap and
 * thread-safe (e.g. a `StateFlow.value`).
 */
@OptIn(UnstableApi::class)
class MuteAudioProcessor(private val isMuted: () -> Boolean) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!Util.isEncodingLinearPcm(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // Always active: muting can start or stop at any buffer
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        if (isMuted()) {
            val silence = silenceByte(inputAudioFormat.encoding)
            repeat(remaining) { output.put(silence) }
            inputBuffer.position(inputBuffer.limit())
        } else {
            output.put(inputBuffer)
        }
        output.flip()
    }

    internal companion object {
        /** Unsigned 8-bit PCM is centred on 0x80; every signed or float encoding on zero. */
        fun silenceByte(encoding: Int): Byte = if (encoding == C.ENCODING_PCM_8BIT) 0x80.toByte() else 0
    }
}

/**
 * [delegate] followed by [mute], last of the chain: after silence skipping and speed, so the
 * silence it writes is never skipped (the position keeps its pace) and the durations computed
 * by [delegate] stay exact.
 */
@OptIn(UnstableApi::class)
class MuteAudioProcessorChain(
    private val delegate: AudioProcessorChain,
    private val mute: MuteAudioProcessor,
) : AudioProcessorChain {
    // Built once: ExoPlayer reads the chain repeatedly (sink build, audio-config changes)
    private val processors: Array<AudioProcessor> = delegate.audioProcessors + mute

    override fun getAudioProcessors(): Array<AudioProcessor> = processors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
        delegate.applyPlaybackParameters(playbackParameters)

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean =
        delegate.applySkipSilenceEnabled(skipSilenceEnabled)

    override fun getMediaDuration(playoutDuration: Long): Long = delegate.getMediaDuration(playoutDuration)

    override fun getSkippedOutputFrameCount(): Long = delegate.skippedOutputFrameCount
}
