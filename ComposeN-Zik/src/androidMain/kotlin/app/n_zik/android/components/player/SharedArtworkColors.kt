package app.n_zik.android.components.player

import android.content.Context
import android.graphics.Bitmap
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ArtworkColorKey(
    val profile: String,
    val uri: String,
    val revision: String,
    val quality: String,
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
    private val workScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
    private val admission = Semaphore(maxInFlight)
    private val lock = Any()
    private val completed = LinkedHashMap<ArtworkColorKey, ArtworkColorSnapshot>(16, .75f, true)
    private val pending = mutableMapOf<ArtworkColorKey, Flight>()
    private var generation = 0L

    init { require(capacity > 0); require(maxInFlight > 0) }

    suspend fun load(key: ArtworkColorKey, produce: suspend () -> ArtworkColorSnapshot?): ArtworkColorSnapshot? {
        var flight = synchronized(lock) {
            completed[key]?.let { return it }
            pending[key]?.also { it.waiters++ }
        }
        if (flight == null) {
            // Admission waits in the consumer, so abandoned requests do not create queued workers.
            admission.acquire()
            flight = synchronized(lock) {
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

    /** Called with lock held and one admission permit owned. */
    private fun createFlight(key: ArtworkColorKey, produce: suspend () -> ArtworkColorSnapshot?): Flight {
        val flight = Flight()
        val startedGeneration = generation
        flight.task = workScope.async(start = CoroutineStart.LAZY) {
            val result = produce()
            coroutineContext.ensureActive()
            synchronized(lock) {
                if (result != null && generation == startedGeneration) {
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
        generation++
        completed.clear()
        // Existing waiters may finish; future requests must not join old work.
        pending.clear()
    }
}

/** Process-shared extraction. URI callers use one source contract before UI-specific resizing. */
object SharedArtworkColors {
    private val repository = ArtworkColorRepository(NzikDispatchers.fireAndForget(NzikDispatchers.DATA))
    private val mutableGeneration = MutableStateFlow(0L)
    val generation: StateFlow<Long> = mutableGeneration
    private val bitmapIds = WeakHashMap<Bitmap, Long>()
    private var nextBitmapId = 0L

    @Suppress("UNUSED_PARAMETER") // Alias URLs share pixels, so explicit invalidation clears this small cache.
    fun invalidate(uri: String? = null) {
        // A bounded global invalidation also covers learned URL aliases and replaced local files.
        synchronized(this) {
            repository.invalidate()
            mutableGeneration.value++
        }
    }

    suspend fun load(
        context: Context,
        uri: String?,
        revision: String? = null,
        bitmap: Bitmap? = null,
    ): ArtworkColorSnapshot? = withContext(NzikDispatchers.DATA) {
        if (uri.isNullOrBlank() || uri == "null") return@withContext bitmap?.let { fromBitmap(it, getActiveProfile(context)) }
        // Color analysis uses the existing highest app source contract, independent of UI thumbnails.
        val quality = ImageCacheFactory.NetworkQuality.HIGH
        val profile = getActiveProfile(context)
        val file = when {
            uri.startsWith("file://") -> File(android.net.Uri.parse(uri).path.orEmpty())
            uri.startsWith("/") -> File(uri)
            else -> null
        }
        val sourceRevision = revision ?: file?.let { "${it.lastModified()}:${it.length()}" }.orEmpty()
        val key = ArtworkColorKey(profile, uri, sourceRevision, quality.name, mutableGeneration.value)
        repository.load(key) {
            // URI-backed analysis never accepts a caller's potentially downscaled display bitmap.
            val image = ImageCacheFactory.loadBitmap(uri, allowHardware = false, qualityOverride = quality, requestSize = quality.size)
            image?.takeUnless { it.isRecycled }?.let { analyzeArtworkColors(it) }
        }
    }

    /** Compatibility for genuinely URI-less bitmaps; distinct decodes cannot share this identity. */
    suspend fun fromBitmap(bitmap: Bitmap, profile: String = "bitmap"): ArtworkColorSnapshot? {
        if (bitmap.isRecycled) return null
        val identity = synchronized(bitmapIds) { bitmapIds.getOrPut(bitmap) { ++nextBitmapId } }
        val key = ArtworkColorKey(profile, "bitmap:$identity",
            "${bitmap.generationId}:${bitmap.width}:${bitmap.height}", "pixels", mutableGeneration.value)
        return repository.load(key) { analyzeArtworkColors(bitmap) }
    }
}
