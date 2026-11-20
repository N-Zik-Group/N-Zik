package app.n_zik.android.components.player

import android.content.Context
import android.graphics.Bitmap
import app.n_zik.android.appContext
import app.n_zik.android.components.glass.invalidatePlayerForegroundMemo
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.lang.ref.WeakReference
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
    val sourceIdentity: String = uri,
)

internal data class ArtworkColorIdentity(val profile: String, val source: String)

internal class ArtworkColorObservation(val identity: ArtworkColorIdentity) {
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun invalidate() { mutableRevision.value++ }
}

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
    // A source revision is needed only while a consumer or an in-flight request holds it.
    private val observations = WeakHashMap<ArtworkColorIdentity, WeakReference<ArtworkColorObservation>>()
    private val mutableGeneration = MutableStateFlow(0L)
    val generation: StateFlow<Long> = mutableGeneration.asStateFlow()

    init { require(capacity > 0); require(maxInFlight > 0) }

    fun observe(identity: ArtworkColorIdentity): ArtworkColorObservation = synchronized(lock) {
        observations[identity]?.get() ?: ArtworkColorObservation(identity).also {
            // Use the same weak key instance that the observation retains.
            observations.remove(identity)
            observations[identity] = WeakReference(it)
        }
    }

    suspend fun load(key: ArtworkColorKey, produce: suspend () -> ArtworkColorSnapshot?): ArtworkColorSnapshot? {
        val observation = observe(ArtworkColorIdentity(key.profile, key.sourceIdentity))
        val revision = observation.revision.value
        var flight = synchronized(lock) {
            requireCurrentGeneration(key)
            requireCurrentSource(observation, revision)
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
                if (key.generation != generation.value || observation.revision.value != revision) {
                    admission.release()
                    requireCurrentGeneration(key)
                    requireCurrentSource(observation, revision)
                }
                completed[key]?.let { admission.release(); return it }
                pending[key]?.also { admission.release(); it.waiters++ } ?: createFlight(key, produce)
            }
        }
        val shared = requireNotNull(flight)
        shared.task.start()
        try {
            val result = shared.task.await()
            requireCurrentSource(observation, revision)
            return result
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

    private fun requireCurrentSource(observation: ArtworkColorObservation, revision: Long) {
        if (observation.revision.value != revision) throw CancellationException("Artwork source changed")
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

    fun invalidateSources(profile: String, sources: Set<String>) {
        fun ArtworkColorKey.matches() = this.profile == profile && sourceIdentity in sources
        val invalidated = synchronized(lock) {
            completed.keys.removeAll { it.matches() }
            val flights = pending.filterKeys { it.matches() }.values.toList()
            pending.keys.removeAll { it.matches() }
            for (source in sources) {
                observations[ArtworkColorIdentity(profile, source)]?.get()?.invalidate()
            }
            flights
        }
        invalidated.forEach { it.task.cancel() }
    }
}

/** Process-shared extraction keyed by the effective source and its freshness. */
object SharedArtworkColors {
    private val repository = ArtworkColorRepository(NzikDispatchers.fireAndForget(NzikDispatchers.DATA))
    internal val displayed = DisplayedArtworkRegistry()
    val generation: StateFlow<Long> = repository.generation
    private val bitmapIds = WeakHashMap<Bitmap, Long>()
    private var nextBitmapId = 0L

    fun invalidate(uri: String? = null) {
        if (uri != null) {
            invalidateSources(setOf(uri), getActiveProfile(appContext()))
            return
        }
        repository.invalidate()
        invalidatePlayerForegroundMemo()
    }

    internal fun invalidateSources(uris: Set<String>, profile: String) {
        val sources = uris.mapNotNullTo(mutableSetOf()) { ImageCacheFactory.artworkColorIdentity(it, profile) }
        if (sources.isEmpty()) return
        displayed.invalidateSources(profile) { ImageCacheFactory.artworkColorIdentity(it, profile) in sources }
        repository.invalidateSources(profile, sources)
    }

    internal fun observe(uri: String?, profile: String): ArtworkColorObservation? =
        ImageCacheFactory.artworkColorIdentity(uri, profile)?.let { repository.observe(ArtworkColorIdentity(profile, it)) }

    suspend fun load(
        context: Context,
        uri: String?,
        revision: String? = null,
        bitmap: Bitmap? = null,
    ): ArtworkColorSnapshot? = withContext(NzikDispatchers.DATA) {
        if (uri.isNullOrBlank() || uri == "null") return@withContext bitmap?.let { fromBitmap(it, getActiveProfile(context)) }
        val cacheGeneration = generation.value
        val profile = getActiveProfile(context)
        val observation = observe(uri, profile) ?: return@withContext null
        val sourceRevision = observation.revision.value
        val source = ImageCacheFactory.artworkColorSource(uri, profile) ?: return@withContext null
        val fileRevision = ImageCacheFactory.artworkFileRevision(source.uri)
        val key = ArtworkColorKey(profile, source.uri,
            "${source.localRevision}:${source.remoteRevision}:$fileRevision:${revision.orEmpty()}", cacheGeneration,
            observation.identity.source)
        val quality = ImageCacheFactory.NetworkQuality.HIGH
        fun requireCurrentArtwork() {
            if (cacheGeneration != generation.value || observation.revision.value != sourceRevision ||
                !ImageCacheFactory.isCurrentArtworkSource(source) ||
                ImageCacheFactory.artworkFileRevision(source.uri) != fileRevision || getActiveProfile(context) != profile
            ) throw CancellationException("Artwork changed during extraction")
        }
        requireCurrentArtwork()
        val colors = repository.load(key) {
            val current = displayed.await(source, fileRevision)?.takeUnless { it.isRecycled }
            requireCurrentArtwork()
            val image = if (current != null) {
                Timber.tag("ArtworkColors").d("Analyze displayed artwork")
                current
            } else {
                Timber.tag("ArtworkColors").d("Decode canonical artwork")
                ImageCacheFactory.loadBitmap(source.uri, allowHardware = false,
                    qualityOverride = quality, requestSize = quality.size, cacheDecodedBitmap = false)
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
