package app.n_zik.android.components.player

import android.content.Context
import android.graphics.Bitmap
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

data class ArtworkColorKey(
    val profile: String,
    val uri: String,
    val revision: String,
    val generation: Long,
)

/** Shared tasks belong to this repository, never to any one waiting screen. */
internal class ArtworkColorRepository(
    scope: CoroutineScope,
    private val capacity: Int = 24,
    maxInFlight: Int = 8,
) {
    private class Flight {
        lateinit var task: Deferred<ArtworkColorSnapshot?>
        var waiters = 1
    }
    private val workScope = NzikDispatchers.supervisedChildScope(scope)
    private val admission = Semaphore(maxInFlight)
    private val lock = Any()
    private val completed = LinkedHashMap<ArtworkColorKey, ArtworkColorSnapshot>(16, .75f, true)
    private val pending = mutableMapOf<ArtworkColorKey, Flight>()
    private val mutableGeneration = MutableStateFlow(0L)
    val generation: StateFlow<Long> = mutableGeneration.asStateFlow()

    init { require(capacity > 0); require(maxInFlight > 0) }

    suspend fun load(key: ArtworkColorKey, produce: suspend () -> ArtworkColorSnapshot?): ArtworkColorSnapshot? {
        var flight = synchronized(lock) {
            requireCurrentGeneration(key)
            completed[key]?.let {
                Timber.tag("ArtworkColors").d("Color cache hit")
                return it
            }
            pending[key]?.also { it.waiters++ }
        }
        if (flight == null) {
            // Admission waits in the consumer, so abandoned requests do not create queued workers.
            admission.acquire()
            flight = synchronized(lock) {
                if (key.generation != generation.value) {
                    admission.release()
                    requireCurrentGeneration(key)
                }
                completed[key]?.let { admission.release(); return it }
                pending[key]?.also { admission.release(); it.waiters++ } ?: createFlight(key, produce)
            }
        }
        val shared = requireNotNull(flight)
        shared.task.start()
        try {
            return shared.task.await()
        } finally {
            val abandoned = synchronized(lock) {
                shared.waiters--
                (shared.waiters == 0 && !shared.task.isCompleted).also {
                    if (it && pending[key] === shared) pending.remove(key)
                }
            }
            if (abandoned) shared.task.cancel()
        }
    }

    private fun requireCurrentGeneration(key: ArtworkColorKey) {
        if (key.generation != generation.value) throw CancellationException("Artwork cache changed")
    }

    /** Called with lock held and one admission permit owned. */
    private fun createFlight(key: ArtworkColorKey, produce: suspend () -> ArtworkColorSnapshot?): Flight {
        val flight = Flight()
        flight.task = workScope.async(start = CoroutineStart.LAZY) {
            Timber.tag("ArtworkColors").d("Color cache miss")
            val result = produce()
            coroutineContext.ensureActive()
            synchronized(lock) {
                if (result != null && key.generation == generation.value && pending[key] === flight) {
                    completed[key] = result
                    while (completed.size > capacity) completed.remove(completed.keys.first())
                }
            }
            result
        }
        pending[key] = flight
        flight.task.invokeOnCompletion {
            synchronized(lock) { if (pending[key] === flight) pending.remove(key) }
            admission.release()
        }
        return flight
    }

    fun invalidate() = synchronized(lock) {
        completed.clear()
        // Existing waiters may finish; future requests must not join old work.
        pending.clear()
        mutableGeneration.value++
    }
}

/** Process-shared extraction keyed by the effective source and its freshness. */
object SharedArtworkColors {
    private val repository = ArtworkColorRepository(NzikDispatchers.fireAndForget(NzikDispatchers.DATA))
    internal val displayed = DisplayedArtworkRegistry()
    val generation: StateFlow<Long> = repository.generation
    private val bitmapIds = WeakHashMap<Bitmap, Long>()
    private var nextBitmapId = 0L

    @Suppress("UNUSED_PARAMETER") // Alias URLs share pixels, so explicit invalidation clears this small cache.
    fun invalidate(uri: String? = null) {
        // A bounded global invalidation also covers learned URL aliases and replaced local files.
        repository.invalidate()
    }

    suspend fun load(
        context: Context,
        uri: String?,
        revision: String? = null,
        bitmap: Bitmap? = null,
    ): ArtworkColorSnapshot? = withContext(NzikDispatchers.DATA) {
        if (uri.isNullOrBlank() || uri == "null") return@withContext bitmap?.let { fromBitmap(it, getActiveProfile(context)) }
        val cacheGeneration = generation.value
        val profile = getActiveProfile(context)
        val source = ImageCacheFactory.artworkColorSource(uri, profile) ?: return@withContext null
        val fileRevision = ImageCacheFactory.artworkFileRevision(source.uri)
        val key = ArtworkColorKey(profile, source.uri,
            "${source.localRevision}:${source.remoteRevision}:$fileRevision:${revision.orEmpty()}", cacheGeneration)
        val quality = ImageCacheFactory.NetworkQuality.HIGH
        fun requireCurrentArtwork() {
            if (cacheGeneration != generation.value || !ImageCacheFactory.isCurrentArtworkSource(source) ||
                ImageCacheFactory.artworkFileRevision(source.uri) != fileRevision || getActiveProfile(context) != profile
            ) throw CancellationException("Artwork changed during extraction")
        }
        val colors = repository.load(key) {
            val current = displayed.await(source, fileRevision)?.takeUnless { it.isRecycled }
            requireCurrentArtwork()
            val image = if (current != null) {
                Timber.tag("ArtworkColors").d("Analyze displayed artwork")
                current
            } else {
                Timber.tag("ArtworkColors").d("Decode canonical artwork")
                ImageCacheFactory.loadBitmap(uri, allowHardware = false,
                    qualityOverride = quality, requestSize = quality.size)
            }
            image?.takeUnless { it.isRecycled }?.let { analyzeArtworkColors(it) }
                .also { requireCurrentArtwork() }
        }
        requireCurrentArtwork()
        colors
    }

    /** Compatibility for genuinely URI-less bitmaps; distinct decodes cannot share this identity. */
    suspend fun fromBitmap(bitmap: Bitmap, profile: String = "bitmap"): ArtworkColorSnapshot? {
        if (bitmap.isRecycled) return null
        val identity = synchronized(bitmapIds) { bitmapIds.getOrPut(bitmap) { ++nextBitmapId } }
        val key = ArtworkColorKey(profile, "bitmap:$identity",
            "${bitmap.generationId}:${bitmap.width}:${bitmap.height}", generation.value)
        return repository.load(key) { analyzeArtworkColors(bitmap) }
    }
}
