package app.n_zik.android.playback.services

import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.n_zik.android.utils.coroutines.NzikDispatchers

internal fun crossfadeDelayMillis(remainingMediaMs: Long, speed: Float): Long =
    ceil(remainingMediaMs.coerceAtLeast(0L) / speed.toDouble()).toLong()

/** Schedules against live media time and keeps optional artwork work off the trigger path. */
internal class CrossfadePositionScheduler {
    private var scheduled: Job? = null
    private var ownsSchedule: (() -> Boolean)? = null

    val isArmed: Boolean get() = scheduled?.isActive == true

    fun stillOwnsSchedule(): Boolean = isArmed && ownsSchedule?.invoke() == true

    fun cancel() {
        scheduled?.cancel()
        scheduled = null
        ownsSchedule = null
    }

    fun arm(
        scope: CoroutineScope,
        triggerPosition: Long,
        position: () -> Long,
        speed: () -> Float,
        isStillOwner: () -> Boolean,
        prepareAudio: () -> Unit,
        prefetchArtwork: suspend () -> Unit,
        beginFade: () -> Unit,
    ) {
        cancel()
        ownsSchedule = isStillOwner
        scheduled = scope.launch(NzikDispatchers.UI) {
            runCrossfadeSchedule(
                triggerPosition,
                position,
                speed,
                isStillOwner,
                prepareAudio,
                prefetchArtwork,
                beginFade,
            )
        }
    }
}

internal suspend fun runCrossfadeSchedule(
    triggerPosition: Long,
    position: () -> Long,
    speed: () -> Float,
    isStillOwner: () -> Boolean,
    prepareAudio: () -> Unit,
    prefetchArtwork: suspend () -> Unit,
    beginFade: () -> Unit,
) = coroutineScope {
    suspend fun waitForPosition(target: Long): Boolean {
        while (isStillOwner()) {
            val remaining = target - position()
            if (remaining <= 0L) return true
            delay(crossfadeDelayMillis(remaining, speed()))
        }
        return false
    }

    if (!waitForPosition(triggerPosition - 15_000L)) return@coroutineScope
    prepareAudio()
    val artwork = launch { prefetchArtwork() }
    try {
        if (waitForPosition(triggerPosition)) beginFade()
    } finally {
        artwork.cancel()
    }
}
