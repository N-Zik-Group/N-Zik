package app.it.fast4x.rimusic.utils

import android.animation.ValueAnimator
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber

var volume = 0f

fun getDeviceVolume(context: Context): Float {
    val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
    val volumeLevel: Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val maxVolume: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    return volumeLevel.toFloat() / maxVolume
}

fun setDeviceVolume(context: Context, volume: Float) {
    val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).toInt(), AudioManager.FLAG_SHOW_UI)
}

@Composable
@OptIn(UnstableApi::class)
fun MedleyMode(binder: PlayerServiceModern.Binder?, seconds: Int) {
    if (seconds == 0) return
    if (binder != null) {
        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            coroutineScope.launch {
                while (isActive) {
                    delay(1.seconds * seconds)
                    withContext(NzikDispatchers.UI) {
                        if (binder.player.isPlaying)
                            binder.player.playNext()
                    }
                }
            }
        }
    }
}

@MainThread
fun Player.fadeInEffect( duration: Long ) {
    if( isPlaying ) return
    if( duration == 0L ) {
        if( playbackState == Player.STATE_IDLE )
            prepare()
        play()
        return
    }

    val animator = ValueAnimator.ofFloat( 0f, getGlobalVolume() )
    animator.duration = duration
    animator.addUpdateListener {
        volume = it.animatedValue as Float
    }
    animator.doOnStart {
        if (playbackState == Player.STATE_IDLE)
            prepare()
        play()
    }
    animator.start()
}

@MainThread
fun Player.fadeOutEffect( duration: Long ) {
    if( !isPlaying && !playWhenReady && playbackState != Player.STATE_BUFFERING ) return
    if( duration == 0L || !isPlaying ) {
        pause()
        return
    }

    val animator = ValueAnimator.ofFloat( getGlobalVolume(), 0f )
    animator.duration = duration
    animator.addUpdateListener {
        volume = it.animatedValue as Float
    }
    animator.doOnEnd {
        pause()
        restoreGlobalVolume()
    }
    animator.start()
}


