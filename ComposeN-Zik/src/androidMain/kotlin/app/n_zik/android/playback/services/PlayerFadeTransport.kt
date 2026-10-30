package app.n_zik.android.playback.services

import androidx.annotation.MainThread
import androidx.media3.common.Player
import app.it.fast4x.rimusic.utils.fadeOutEffect

/** Delivers an external Pause even when the fade helper has no active audio to fade. */
@MainThread
internal fun Player.pauseWithFade(duration: Long) {
    if (!isPlaying && !playWhenReady && playbackState != Player.STATE_BUFFERING) {
        // A saved queue may still be loading. The shared facade must observe this intent
        // even though the upstream player is already paused (or has no items yet).
        pause()
    } else {
        fadeOutEffect(duration)
    }
}
