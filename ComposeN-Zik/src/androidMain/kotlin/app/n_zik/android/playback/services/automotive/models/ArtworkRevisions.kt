package app.n_zik.android.playback.services.automotive.models

import java.util.concurrent.atomic.AtomicLong

/** Monotonic invalidation catches replacement even when file size and mtime stay equal. */
object ArtworkRevisions {
    private val revision = AtomicLong()
    fun invalidate() { revision.incrementAndGet() }
    internal fun current(): Long = revision.get()
}
