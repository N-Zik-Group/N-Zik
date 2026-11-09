package app.n_zik.android.playback.services.automotive.models

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Monotonic invalidation catches replacement even when file size and mtime stay equal. */
object ArtworkRevisions {
    private val revision = MutableStateFlow(0L)
    internal val changes: StateFlow<Long> = revision.asStateFlow()
    fun invalidate() { revision.update { it + 1L } }
    internal fun current(): Long = revision.value
}
