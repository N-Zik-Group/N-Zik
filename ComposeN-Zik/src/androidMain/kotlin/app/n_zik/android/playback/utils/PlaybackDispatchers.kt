package app.n_zik.android.playback.utils

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Collection of useful threads for playback.
 *
 * Must be closed individually after use to prevent unwanted outcome
 */
object PlaybackDispatchers {

    /**
     * Single thread dispatcher guarantee jobs are
     * executed in the order that were given to it.
     *
     * Should only be used by StreamResolver.kt
     */
    val STREAM_RESOLVER = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}
