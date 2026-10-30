package app.n_zik.android.playback.services.automotive.models

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** LRU of encoded bytes, bounded independently by count and memory. */
class ArtworkBytesCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Int = DEFAULT_MAX_BYTES
) {
    init { require(maxEntries > 0 && maxBytes > 0) }
    private val entries = LinkedHashMap<String, ByteArray>(maxEntries, 0.75f, true)
    private var bytes = 0L

    @Synchronized fun get(key: String): ByteArray? = entries[key]

    @Synchronized fun put(key: String, value: ByteArray) {
        entries.remove(key)?.let { bytes -= it.size }
        if (value.size > maxBytes) return
        entries[key] = value
        bytes += value.size
        val iterator = entries.entries.iterator()
        while ((entries.size > maxEntries || bytes > maxBytes) && iterator.hasNext()) {
            bytes -= iterator.next().value.size
            iterator.remove()
        }
    }

    private val inFlight = mutableMapOf<String, CompletableDeferred<ByteArray?>>()

    /** Only bookkeeping is synchronized. Duplicate callers suspend without holding a monitor. */
    internal suspend fun getOrLoad(key: String, load: suspend () -> ByteArray?): ByteArray? {
        while (true) {
            currentCoroutineContext().ensureActive()
            var owner = false
            val flight = synchronized(this) {
                entries[key]?.let { return it }
                inFlight[key] ?: if (inFlight.size < DEFAULT_MAX_ENTRIES) {
                    owner = true
                    CompletableDeferred<ByteArray?>().also { inFlight[key] = it }
                } else null // Bound bookkeeping even for a burst of distinct sources.
            }
            if (flight == null) return load()?.also { put(key, it) }
            if (!owner) {
                try {
                    return flight.await()
                } catch (cancelled: CancellationException) {
                    // The owner may have been cancelled by a different callback. An active
                    // duplicate can take ownership, while its own cancellation still wins.
                    currentCoroutineContext().ensureActive()
                    synchronized(this) {
                        if (inFlight[key] === flight) inFlight.remove(key)
                    }
                    continue
                }
            }
            try {
                return load().also { value ->
                    if (value != null) put(key, value)
                    flight.complete(value)
                }
            } catch (failure: Throwable) {
                flight.completeExceptionally(failure)
                throw failure
            } finally {
                synchronized(this) {
                    if (inFlight[key] === flight) inFlight.remove(key)
                }
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 64
        const val DEFAULT_MAX_BYTES = 4 * 1024 * 1024
    }
}
