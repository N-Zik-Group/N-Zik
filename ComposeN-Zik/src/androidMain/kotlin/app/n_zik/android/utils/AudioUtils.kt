package app.n_zik.android.utils

import kotlin.math.round

import android.animation.ValueAnimator
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.media.AudioManager
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
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
    audioManager.setMusicStreamVolumeLevel(
        volume.coerceIn(0f, 1f) * audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        AudioManager.FLAG_SHOW_UI,
    )
}

internal fun AudioManager.setMusicStreamVolumeLevel(level: Float, flags: Int) {
    setStreamVolume(AudioManager.STREAM_MUSIC, round(level).toInt(), flags)
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


