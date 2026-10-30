package app.n_zik.android.playback.services.automotive.browse

import androidx.media3.common.MediaItem
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** One provider batch per exact category/query, with single-flight, retryable continuations. */
class AutoSearchWindowStore(private val maxWindows: Int = 16, private val maxTokens: Int = 128) {
    data class Batch(val items: List<MediaItem>, val continuation: String?)
    data class Window(val parentId: String, val items: List<MediaItem>, val nextToken: String?, val pageNumber: Int)
    private class State(val owner: String, val epoch: Long) {
        val mutex = Mutex()
        var window: Window? = null
        var continuation: String? = null
    }
    private val lock = Any()
    private val windows = LinkedHashMap<String, State>()
    private val tokens = LinkedHashMap<String, String>()
    private var owner: String? = null
    private var epoch = 0L

    init { require(maxWindows > 0 && maxTokens > 0) }

    /** Resolves opaque routes only inside the owning profile/connectivity generation. */
    fun parentFor(token: String, expectedOwner: String): String? = synchronized(lock) {
        if (owner == expectedOwner) tokens[token] else null
    }

    suspend fun load(
        parentId: String,
        expectedOwner: String,
        currentOwner: () -> String?,
        token: String? = null,
        fetch: suspend (String?) -> Batch,
    ): Window? {
        val state = synchronized(lock) {
            if (currentOwner() != expectedOwner) return null
            if (owner != expectedOwner) clearLocked(expectedOwner)
            if (token != null && tokens[token] != parentId) return null
            windows.getOrPut(parentId) { State(expectedOwner, epoch) }.also {
                while (windows.size > maxWindows) {
                    val oldest = windows.keys.first()
                    windows.remove(oldest)
                    tokens.entries.removeAll { entry -> entry.value == oldest }
                }
            }
        }
        return state.mutex.withLock {
            synchronized(lock) {
                if (!owns(parentId, state, currentOwner)) return@withLock null
                val existing = state.window
                // A controller retry of an already consumed token must never advance again.
                if (existing != null && (token == null || token != existing.nextToken)) return@withLock existing
            }
            val continuation = synchronized(lock) { if (token == null) null else state.continuation }
            val batch = try {
                fetch(continuation)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.tag("AutoSearchWindow").w(exception, "Search batch failed; keeping current window")
                return@withLock synchronized(lock) {
                    state.window.takeIf { owns(parentId, state, currentOwner) }
                }
            }
            synchronized(lock) {
                if (!owns(parentId, state, currentOwner)) return@synchronized null
                val next = batch.continuation?.takeIf { it.isNotBlank() }?.let { UUID.randomUUID().toString() }
                val published = Window(parentId, batch.items.toList(), next, (state.window?.pageNumber ?: 0) + 1)
                state.continuation = batch.continuation
                state.window = published
                if (next != null) tokens[next] = parentId
                val active = windows.values.mapNotNull { it.window?.nextToken }.toSet()
                // Only consumed routes expire: another category's current Suite must stay usable.
                while (tokens.size > maxTokens + active.size) {
                    val expired = tokens.keys.firstOrNull { it !in active } ?: break
                    tokens.remove(expired)
                }
                published
            }
        }
    }

    /** New searches and availability changes reject pending publication without cancelling playback. */
    fun clear() = synchronized(lock) { clearLocked(null) }

    private fun owns(parent: String, state: State, currentOwner: () -> String?): Boolean =
        state.epoch == epoch && state.owner == owner && windows[parent] === state && currentOwner() == state.owner

    private fun clearLocked(nextOwner: String?) {
        epoch++
        owner = nextOwner
        windows.clear()
        tokens.clear()
    }
}
