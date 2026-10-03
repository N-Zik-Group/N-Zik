package app.n_zik.android.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MuteAudioProcessorTest {

    private var muted = false

    private fun processor(encoding: Int = C.ENCODING_PCM_16BIT) = MuteAudioProcessor { muted }.apply {
        configure(AudioProcessor.AudioFormat(44_100, 2, encoding))
        flush()
    }

    private fun MuteAudioProcessor.process(bytes: ByteArray): ByteArray {
        queueInput(ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() })
        val output = getOutput()
        return ByteArray(output.remaining()).also { output.get(it) }
    }

    private val pcm = byteArrayOf(1, 2, 3, 4, -5, -6, 7, 8)

    @Test
    fun `audio passes through unchanged while not muted`() {
        val p = processor()

        assertTrue(p.isActive)
        assertArrayEquals(pcm, p.process(pcm))
    }

    @Test
    fun `audio is silenced while muted and comes back at once when unmuted`() {
        val p = processor()

        muted = true
        assertArrayEquals(ByteArray(pcm.size), p.process(pcm))
        muted = false
        assertArrayEquals(pcm, p.process(pcm))
    }

    @Test
    fun `unsigned 8-bit silence is centred and float silence is zero`() {
        muted = true

        assertArrayEquals(ByteArray(4) { 0x80.toByte() }, processor(C.ENCODING_PCM_8BIT).process(byteArrayOf(1, 2, 3, 4)))
        assertArrayEquals(ByteArray(8), processor(C.ENCODING_PCM_FLOAT).process(pcm))
    }

    @Test
    fun `non-PCM input is refused`() {
        assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            MuteAudioProcessor { false }.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_AC3))
        }
    }

    @Test
    fun `mute comes last in the chain, after silence skipping and speed`() {
        val mute = MuteAudioProcessor { false }
        val chain = MuteAudioProcessorChain(
            DefaultAudioProcessorChain(arrayOf(), SilenceSkippingAudioProcessor(), SonicAudioProcessor()),
            mute,
        )

        val processors = chain.audioProcessors
        assertSame(mute, processors.last())
        assertTrue(processors[processors.size - 2] is SonicAudioProcessor)
    }
}
