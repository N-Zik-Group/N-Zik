package app.n_zik.android.playback.services

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import io.mockk.mockk
import io.mockk.verifySequence
import org.junit.jupiter.api.Test

@UnstableApi
class SharedAudioSessionSinkTest {
    @Test
    fun `late constructor automatic session cannot replace the explicit shared session`() {
        val delegate = mockk<AudioSink>(relaxed = true)
        val sink = SharedAudioSessionSink(delegate, 41)

        sink.setAudioSessionId(41)
        sink.setAudioSessionId(C.AUDIO_SESSION_ID_UNSET)
        sink.setAudioSessionId(42)

        verifySequence {
            delegate.setAudioSessionId(41)
            delegate.setAudioSessionId(41)
            delegate.setAudioSessionId(41)
        }
    }

    @Test
    fun `constructor automatic session before explicit request also uses shared session`() {
        val delegate = mockk<AudioSink>(relaxed = true)
        val sink = SharedAudioSessionSink(delegate, 41)

        sink.setAudioSessionId(C.AUDIO_SESSION_ID_UNSET)
        sink.setAudioSessionId(41)

        verifySequence {
            delegate.setAudioSessionId(41)
            delegate.setAudioSessionId(41)
        }
    }

    @Test
    fun `failed shared session allocation preserves every original renderer request`() {
        listOf(0, -1).forEach { sharedSessionId ->
            val delegate = mockk<AudioSink>(relaxed = true)
            val sink = SharedAudioSessionSink(delegate, sharedSessionId)

            sink.setAudioSessionId(C.AUDIO_SESSION_ID_UNSET)
            sink.setAudioSessionId(41)
            sink.setAudioSessionId(-1)
            sink.setAudioSessionId(42)

            verifySequence {
                delegate.setAudioSessionId(C.AUDIO_SESSION_ID_UNSET)
                delegate.setAudioSessionId(41)
                delegate.setAudioSessionId(-1)
                delegate.setAudioSessionId(42)
            }
        }
    }
}
