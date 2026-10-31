package app.n_zik.android.playback.services

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink

/** Keeps the service session stable across renderer audio-session messages. */
@UnstableApi
internal class SharedAudioSessionSink(
    private val delegate: AudioSink,
    private val sharedSessionId: Int
) : AudioSink by delegate {
    override fun setAudioSessionId(audioSessionId: Int) {
        // Renderer construction can post an automatic session after the explicit session request.
        delegate.setAudioSessionId(if (sharedSessionId > 0) sharedSessionId else audioSessionId)
    }
}
