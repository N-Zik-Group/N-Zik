package app.n_zik.android.playback.services

import java.util.concurrent.atomic.AtomicReference

import androidx.media3.common.Player
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Publishes durations already available from stream responses or the active song's local record. */
internal object ResolvedSessionDuration {
    private val revision = MutableStateFlow(0L)
    private val storedBinding = AtomicReference<StoredSessionDurationBinding?>()
    val updates: StateFlow<Long> = revision.asStateFlow()

    /** Resolves the active facade at delivery, including after a crossfade player swap. */
    fun bind(
        scope: CoroutineScope,
        playerChanges: StateFlow<Int>? = null,
        currentPlayer: () -> Player,
    ): Job = scope.launch(NzikDispatchers.UI) {
        coroutineScope {
            val binding = StoredSessionDurationBinding(
                this,
                currentPlayer,
                readDuration = { id ->
                    Database.songTable.findByIdDirect(id)?.takeIf { it.id == id }
                        ?.durationText?.let(SessionDurationMetadata::fromDurationText)
                },
                onResolved = { (currentPlayer() as? SessionArtworkPlayer)?.refreshDuration() },
            )
            storedBinding.set(binding)
            try {
                binding.refreshPlayer()
                launch {
                    updates.collect {
                        binding.refreshPlayer()
                        (currentPlayer() as? SessionArtworkPlayer)?.refreshDuration()
                    }
                }
                playerChanges?.collect { binding.refreshPlayer() } ?: awaitCancellation()
            } finally {
                binding.close()
                storedBinding.compareAndSet(binding, null)
            }
        }
    }

    fun notifyResolved() {
        revision.update { it + 1 }
    }

    fun forMediaId(mediaId: String): Long? {
        val details = playbackDataCache[mediaId]?.videoDetails
        return details?.takeIf { it.videoId == mediaId }?.lengthSeconds?.let(::fromLengthSeconds)
            ?: storedBinding.get()?.forMediaId(mediaId)
    }

    internal fun fromLengthSeconds(value: String?): Long? {
        val seconds = value?.toLongOrNull()?.takeIf { it > 0 && it <= Long.MAX_VALUE / 1000 } ?: return null
        return seconds * 1000
    }
}
