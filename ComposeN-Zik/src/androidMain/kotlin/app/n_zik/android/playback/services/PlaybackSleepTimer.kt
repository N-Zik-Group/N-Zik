package app.n_zik.android.playback.services

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

internal class PlaybackSleepTimer(private val scope: CoroutineScope) {
    private val remaining = MutableStateFlow<Long?>(null)
    val millisLeft = remaining.asStateFlow()

    private var countdown: Job? = null

    @Synchronized
    fun start(delayMillis: Long, onExpiration: () -> Unit, onShutdown: () -> Unit) {
        val previous = countdown
        val replacement = scope.launch(start = CoroutineStart.LAZY) {
            val owner = coroutineContext.job
            var millis = delayMillis
            do {
                delay(1_000L)
                millis = if (millis > 1_000L) millis - 1_000L else 0L
                synchronized(this@PlaybackSleepTimer) {
                    if (countdown !== owner) return@launch
                    remaining.value = millis.takeIf { it > 0L }
                    if (millis == 0L && countdown === owner) onExpiration()
                }
            } while (millis > 0L)

            delay(1_000L)
            synchronized(this@PlaybackSleepTimer) {
                if (countdown === owner) onShutdown()
            }
        }
        countdown = replacement
        remaining.value = delayMillis
        previous?.cancel()
        replacement.invokeOnCompletion {
            synchronized(this) {
                if (countdown === replacement) {
                    countdown = null
                    remaining.value = null
                }
            }
        }
        replacement.start()
    }

    @Synchronized
    fun cancel() {
        val previous = countdown
        countdown = null
        remaining.value = null
        previous?.cancel()
    }
}
