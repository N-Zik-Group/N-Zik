package app.n_zik.android.core.coil

import coil3.BitmapImage
import coil3.Extras
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

internal data class PlayerArtworkDecodeScope(val profile: String, val cacheEpoch: Int) {
    companion object {
        val extra = Extras.Key<PlayerArtworkDecodeScope?>(null)
    }
}

/** Shares in-flight pixels while keeping each player's painter and request lifecycle independent. */
internal class PlayerArtworkDecodeInterceptor : Interceptor {
    private data class DecodeKey(
        val scope: PlayerArtworkDecodeScope,
        val data: String,
        val memoryKey: String,
        val diskKey: String?,
        val size: Size,
        val scale: Scale,
        val precision: Precision,
    )

    private val inFlight = ConcurrentHashMap<DecodeKey, CompletableDeferred<ImageResult?>>()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val scope = request.extras[PlayerArtworkDecodeScope.extra] ?: return chain.proceed()
        val data = request.data as? String ?: return chain.proceed()
        val memoryKey = request.memoryCacheKey ?: return chain.proceed()
        val key = DecodeKey(scope, data, memoryKey, request.diskCacheKey, chain.size, request.scale, request.precision)

        while (true) {
            val completion = CompletableDeferred<ImageResult?>()
            val existing = inFlight.putIfAbsent(key, completion)
            if (existing != null) {
                val shared = existing.await() ?: continue // The owner left before publishing a result.
                if (shared is SuccessResult && (shared.image as? BitmapImage)?.shareable == true) {
                    return shared.copy(request = request, dataSource = DataSource.MEMORY_CACHE)
                }
                return chain.proceed()
            }

            try {
                return chain.proceed().also { completion.complete(it) }
            } finally {
                inFlight.remove(key, completion)
                completion.complete(null)
            }
        }
    }
}
