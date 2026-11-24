package app.n_zik.android.widget

import android.os.SystemClock
import android.text.format.DateUtils
import android.widget.RemoteViews

internal data class WidgetPlaybackSnapshot(
    val durationMs: Long,
    val positionMs: Long,
    val sampledAtElapsedRealtimeMs: Long,
    val isPlaying: Boolean,
    val speed: Float = 1f,
) {
    private val maximumPosition = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
    private val sampledPosition = positionMs.coerceIn(0, maximumPosition)

    fun positionAt(elapsedRealtimeMs: Long): Long {
        if (!isPlaying || !speed.isFinite() || speed <= 0f) return sampledPosition
        val elapsed = (elapsedRealtimeMs - sampledAtElapsedRealtimeMs).coerceAtLeast(0)
        val advance = (elapsed.toDouble() * speed).toLong()
        return sampledPosition + advance.coerceAtMost(maximumPosition - sampledPosition)
    }

    fun chronometerBaseAt(elapsedRealtimeMs: Long): Long? =
        if (isPlaying && speed == 1f && durationMs > 0 && positionAt(elapsedRealtimeMs) < durationMs) {
            sampledAtElapsedRealtimeMs - sampledPosition
        } else {
            null
        }

    fun progressLevelAt(elapsedRealtimeMs: Long): Int =
        if (durationMs > 0) ((positionAt(elapsedRealtimeMs).toDouble() / durationMs) * 10_000).toInt()
        else 0
}

internal fun RemoteViews.setWidgetPlaybackTiming(
    playback: WidgetPlaybackSnapshot,
    chronometerId: Int,
    progressImageId: Int? = null,
    updateChronometer: Boolean = true,
) {
    val now = SystemClock.elapsedRealtime()
    if (updateChronometer) {
        val position = playback.positionAt(now)
        val base = playback.chronometerBaseAt(now)
        setChronometer(chronometerId, base ?: now - position, null, base != null)
        // The host chronometer can only advance at 1x.
        if (base == null) setTextViewText(chronometerId, DateUtils.formatElapsedTime(position / 1000))
    }
    if (progressImageId != null) {
        setInt(progressImageId, "setImageLevel", playback.progressLevelAt(now))
    }
}

internal fun formatWidgetDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
