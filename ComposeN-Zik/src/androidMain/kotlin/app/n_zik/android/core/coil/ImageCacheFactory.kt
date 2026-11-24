package app.n_zik.android.core.coil

import app.n_zik.android.appTextResources
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.collection.LruCache
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.n_zik.android.R
import app.n_zik.android.components.image.keepsOriginalArtwork
import app.n_zik.android.components.glass.glassImageRim
import app.n_zik.android.components.player.ArtworkSource
import app.n_zik.android.components.player.DisplayedArtwork
import app.n_zik.android.components.player.DisplayedArtworkResult
import app.n_zik.android.components.player.SharedArtworkColors
import app.n_zik.android.utils.profilePreferences
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePainter.State
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.asPainter
import coil3.compose.rememberAsyncImagePainter
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.CachePolicy
import coil3.size.Precision
import coil3.size.Scale
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.SuccessResult
import coil3.toBitmap
import app.n_zik.android.appContext
import app.n_zik.android.cleanPrefix
import app.n_zik.android.thumbnailShape
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.coilImageCacheDir
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import app.n_zik.android.core.network.client.GlobalNetworkLogger
import app.n_zik.android.core.network.client.NetworkClientFactory
import androidx.compose.foundation.Image
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.buffer
import timber.log.Timber

private class PlayerArtworkContinuity(var painter: Painter? = null)

@OptIn(ExperimentalCoilApi::class)
object ImageCacheFactory {

    private val launcherArtwork = lazy {
        val options = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(appContext().resources, R.drawable.ic_launcher_box, options).asImageBitmap()
    }

    /** The bundled artwork is profile-independent. Each caller owns its painter and shares pixels. */
    @Composable
    internal fun rememberArtworkResourcePainter(@DrawableRes id: Int = R.drawable.ic_launcher_box): Painter =
        if (id == R.drawable.ic_launcher_box) remember { BitmapPainter(launcherArtwork.value) }
        else painterResource(id)

    /** Builds the Coil loader used before application dependencies are initialized. */
    internal fun bootstrapLoader(context: Context): ImageLoader = ImageLoader.Builder(context).build()

    /**
     * The Coil disk-cache dir of the ACTIVE profile (spec-profile-data-separation):
     * `coil` (base) or `coil_<id>` (separate), under that profile's location setting
     * (a shared item follows the BASE profile's setting — see [coilImageCacheDir]).
     * Extracted from the [DISK_CACHE] lazy so the selection is unit-testable.
     */
    internal fun coilDiskCacheDir(context: Context): File =
        coilImageCacheDir(context, getActiveProfile(context))

    val DISK_CACHE: DiskCache get() = ProfileArtworkCache.DISK_CACHE

    internal fun <T> captureCleanupState(capture: (Long) -> T): T =
        ProfileArtworkCache.captureCleanupState(capture)

    internal fun profileMetadataFiles(identity: ProfileStorageIdentity): Set<String> =
        ProfileArtworkCache.profileMetadataFiles(identity)

    internal fun profileMetadataStoreName(context: Context, base: String): String =
        ProfileArtworkCache.profileMetadataStoreName(context, base)

    enum class NetworkQuality(val size: Int, val ttl: Long) {
        LOW(300, TimeUnit.HOURS.toMillis(1)),
        MEDIUM(700, TimeUnit.HOURS.toMillis(24)),
        HIGH(1000, TimeUnit.DAYS.toMillis(14))
    }

    data class DownloadDecision(
        val useNetwork: Boolean,
        val quality: NetworkQuality,
        val original: Boolean = false,
    ) {
        val sourceSize: Int get() = if (original) ORIGINAL_SOURCE_SIZE else quality.size
    }

    private const val COOLDOWN_MS = 10 * 1000L
    private val cooldownMap = ArtworkQualityCooldown(COOLDOWN_MS)
    private val cacheKeyMap = LruCache<String, String>(4096)
    private val playerArtworkCacheEpoch = MutableStateFlow(0)
    private val storeVersion get() = ProfileArtworkCache.storeVersion
    private val diskRevisions get() = ProfileArtworkCache.diskRevisions
    internal val PlaylistThumbnailStore get() = ProfileArtworkCache.PlaylistThumbnailStore
    private val cacheMetadataStore get() = ProfileArtworkCache.CacheMetadataStore

    val LOADER: ImageLoader by lazy {
        ImageLoader.Builder(appContext())
            .crossfade(true)
            // Decode every image as a software bitmap: Coil defaults to HARDWARE on API 26+,
            // but the Rewind share screenshot replays the view tree on a software canvas,
//
            // where hardware bitmaps cannot be drawn at all (IllegalArgumentException:
            // "Software rendering doesn't support hardware bitmaps") — the legacy mini
//
            // player's cover goes through this factory too. loadBitmap() keeps its
            // per-request allowHardware override.
            .allowHardware(false)
            .memoryCache { MemoryCache.Builder().maxSizePercent(appContext(), 0.15).strongReferencesEnabled(true).build() }
            .diskCache(DISK_CACHE)
            .components {
                add(PlayerArtworkDecodeInterceptor())
                add(OkHttpNetworkFetcherFactory(
                    callFactory = { request: Request ->
                        NetworkClientFactory.getClient().newCall(request)
                    }
                ))
            }
            .build()
    }

    fun generateCacheKeySync(url: String?, quality: NetworkQuality): String {
        if (url.isNullOrBlank()) return "empty"
        val baseKey = artworkBaseKey(url, quality)
        return when {
            isLocalArtworkSource(url) -> {
                val clean = cleanPrefix(url)
                val profile = if (clean.startsWith("content:", ignoreCase = true)) getActiveProfile(appContext()) else null
                val source = profile?.let { currentArtworkSource(clean, it) }
                if (source?.signedRemote == true) "$baseKey-provider-${source.remoteRevision}"
                else "$baseKey-local-${ArtworkRevisions.current(clean, profile)}"
            }
            isRemoteArtworkSource(url) -> diskRevisions.key(baseKey)
            else -> baseKey
        }
    }

    private fun artworkBaseKey(url: String, quality: NetworkQuality): String {
        val id = url.learnablePlaylistArtworkId()
        if (id != null) {
            val resSuffix = if (url.isYouTubeHighRes()) "HIGH" else "LOW"
            return "playlist_${id}_$resSuffix"
        }

        val cacheKey = "${url}_${quality.size}"
        cacheKeyMap.get(cacheKey)?.let { return it }
        return run {
            try {
                val md = MessageDigest.getInstance("MD5")
                val digest = md.digest(url.toByteArray())
                digest.fold("") { str, it -> str + "%02x".format(it) }
            } catch (e: Exception) { "${url.hashCode()}_${quality.size}" }
        }.also { cacheKeyMap.put(cacheKey, it) }
    }

    private fun isRemoteArtworkSource(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    internal fun isLocalArtworkSource(source: String?): Boolean {
        if (source.isNullOrBlank()) return false
        val clean = cleanPrefix(source)
        return clean.startsWith("file:", ignoreCase = true) ||
            clean.startsWith("content:", ignoreCase = true) || File(clean).isAbsolute
    }

    @Composable
    internal fun rememberLocalArtworkRevision(source: String?, profile: String? = null): Long {
        if (source == null || !isLocalArtworkSource(source)) return 0L
        return rememberArtworkRevision(source, profile)
    }

    @Composable
    internal fun rememberArtworkRevision(source: String?, profile: String? = null): Long {
        if (source.isNullOrBlank() || source == "null") return 0L
        val observedProfile = profile ?: rememberArtworkProfile()
        val changes = remember(source, observedProfile) {
            ArtworkRevisions.changes.map { artworkSourceRevision(source, observedProfile) }.distinctUntilChanged()
        }
        return key(source, observedProfile) {
            changes.collectAsStateWithLifecycle(
                initialValue = artworkSourceRevision(source, observedProfile),
                context = NzikDispatchers.DATA,
            ).value
        }
    }

    internal fun artworkSourceRevision(source: String?, profile: String): Long {
        val uri = source?.let(::cleanPrefix) ?: return 0L
        val sourceRevision = currentArtworkSource(uri, profile)
        return sourceRevision.localRevision + sourceRevision.remoteRevision
    }

    internal fun artworkColorSource(url: String?, profile: String): ArtworkSource? =
        displayArtworkSource(url, profile)?.let(::cleanPrefix)?.takeUnless { it.isBlank() || it == "null" }
            ?.let { currentArtworkSource(it, profile) }

    /** Aliases share invalidation, while extraction still distinguishes crops and exact source URIs. */
    internal fun artworkColorIdentity(url: String?, profile: String): String? {
        val cleaned = url?.let(::cleanPrefix)?.takeUnless { it.isBlank() || it == "null" } ?: return null
        val source = signedRemoteArtworkSource(cleaned, profile) ?: cleaned
        if (source.startsWith("file:", ignoreCase = true)) return Uri.parse(source).path ?: source
        val remote = source.toHttpUrlOrNull() ?: return source
        val path = remote.pathSegments
        if (remote.host == "i.ytimg.com" && path.size >= 3 &&
            path.first() in setOf("vi", "pl_c", "podcasts_artwork")
        ) return "https://i.ytimg.com/${path[0]}/${path[1]}"
        if (remote.host == "yt3.ggpht.com" || remote.host == "googleusercontent.com" ||
            remote.host.endsWith(".googleusercontent.com")
        ) return source.thumbnail(ORIGINAL_SOURCE_SIZE)
        return source
    }

    internal fun currentArtworkSource(uri: String, profile: String): ArtworkSource {
        val signedRemote = signedRemoteArtworkSource(uri, profile)
        val source = signedRemote ?: uri
        return ArtworkSource(
            profile = profile,
            uri = uri,
            localRevision = if (isLocalArtworkSource(source)) ArtworkRevisions.current(source, profile) else 0L,
            remoteRevision = if (isRemoteArtworkSource(source)) ArtworkRevisions.global() + remoteArtworkRevision(source) else 0L,
            signedRemote = signedRemote != null,
        )
    }

    private fun remoteArtworkRevision(url: String): Long =
        if (isRemoteArtworkSource(url)) diskRevisions.current(artworkBaseKey(url, NetworkQuality.HIGH)) else 0L

    internal fun isCurrentArtworkSource(source: ArtworkSource): Boolean =
        source == currentArtworkSource(source.uri, source.profile)

    internal fun artworkFileRevision(uri: String): String {
        val file = when {
            uri.startsWith("file:", ignoreCase = true) -> Uri.parse(uri).path?.let(::File)
            File(uri).isAbsolute -> File(uri)
            else -> null
        }
        return file?.let { "${it.lastModified()}:${it.length()}" }.orEmpty()
    }

    @Composable
    internal fun rememberArtworkProfile(): String {
        val context = LocalContext.current.applicationContext
        val preferences = context.profilePreferences
        var profile by remember(preferences) { mutableStateOf(getActiveProfile(context)) }
        DisposableEffect(preferences) {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key == activeProfileKey) profile = getActiveProfile(context)
            }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            profile = getActiveProfile(context)
            onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return profile
    }

    @Composable
    internal fun ObserveDisplayedArtwork() {
        val profile = rememberArtworkProfile()
        DisposableEffect(profile) {
            val remove = SharedArtworkColors.displayed.showPlayer(profile)
            onDispose(remove)
        }
    }

    fun clearCacheForKey(url: String?, quality: NetworkQuality, invalidateColors: Boolean = true) {
        if (url == null) return
        val profile = if (invalidateColors) getActiveProfile(appContext()) else null
        val key = generateCacheKeySync(url, quality)
        try {
            evictCacheKey(key)
        } catch (error: Exception) {
            Timber.tag("ImageCacheFactory").w("Unable to evict image cache key (%s)", error.javaClass.simpleName)
        }
        // Publish only after pixel eviction so palette readers cannot reuse the evicted pixels.
        if (profile != null) SharedArtworkColors.invalidateSources(setOf(url), profile)
    }

    /** Explicit update actions must report failed eviction instead of claiming a complete cover reset. */
    internal fun refreshArtworkSourcesOrThrow(urls: Set<String>, profile: String) = synchronized(ProfileArtworkCache.diskMutationLock) {
        val sources = urls.flatMapTo(mutableSetOf()) { url ->
            val cleaned = cleanPrefix(url)
            val effective = signedRemoteArtworkSource(cleaned, profile) ?: cleaned
            buildSet {
                add(url)
                add(cleaned)
                add(effective)
                if (effective.toHttpUrlOrNull() != null) addAll(coverSourceCandidates(effective))
                effective.thumbnail(ORIGINAL_SOURCE_SIZE)?.let(::add)
                effective.thumbnail(1200)?.let(::add)
            }
        }
        val keys = sources.flatMapTo(mutableSetOf()) { url ->
            NetworkQuality.entries.map { generateCacheKeySync(url, it) }
        }
        keys.forEach(::evictCacheKey)
        diskRevisions.refresh(sources.filter(::isRemoteArtworkSource).flatMapTo(mutableSetOf()) { url ->
            NetworkQuality.entries.map { artworkBaseKey(url, it) }
        })
        SharedArtworkColors.invalidateSources(sources, profile)
        val localSources = sources.filter(::isLocalArtworkSource)
        if (localSources.isNotEmpty()) ArtworkRevisions.invalidateSources(localSources, profile)
        else if (sources.any(::isRemoteArtworkSource)) ArtworkRevisions.notifyRemoteRefresh()
        storeVersion.update { it + 1 }
    }

    private fun evictCacheKey(key: String) {
        LOADER.memoryCache?.remove(MemoryCache.Key(key))
        DISK_CACHE.remove(key)
    }

    fun getNetworkQuality(): NetworkQuality = ArtworkNetworkQuality.getNetworkQuality()

    fun getActualNetworkQuality(): NetworkQuality =
        ArtworkNetworkQuality.getActualNetworkQuality(appContext())

    fun getCurrentNetworkQuality(): NetworkQuality = getActualNetworkQuality()

    internal fun refreshArtworkQuality() {
        cooldownMap.clear()
        storeVersion.update { it + 1 }
    }

    fun getDownloadDecision(thumbnailUrl: String?): DownloadDecision {
        if (thumbnailUrl.isNullOrBlank() || thumbnailUrl == "null") {
            return DownloadDecision(false, NetworkQuality.LOW)
        }

        if (keepsOriginalArtwork(appContext())) return DownloadDecision(true, NetworkQuality.HIGH, original = true)

        return cooldownMap.decide(thumbnailUrl, SystemClock.elapsedRealtime(), cacheMetadataStore.get(thumbnailUrl),
            ::getNetworkQuality)
    }

    /** The displayed cover and prefetch share the pending request size until it resolves. */
    internal class ArtworkQualityCooldown(private val durationMillis: Long) {
        private data class Attempt(val startedAt: Long, val quality: NetworkQuality)
        private val attempts = LruCache<String, Attempt>(512)
        private var generation = 0L

        fun decide(source: String, now: Long, cached: NetworkQuality?, available: () -> NetworkQuality): DownloadDecision {
            if (cached == NetworkQuality.HIGH) return DownloadDecision(false, cached)
            fun recentAttempt() = attempts.get(source)?.takeIf { now - it.startedAt < durationMillis }
            val probeGeneration = synchronized(attempts) { generation }
            val desired = recentAttempt()?.quality ?: available()
            // The network detector can make Binder calls. Only serialize the cache lookup and write.
            val quality = synchronized(attempts) {
                val recent = recentAttempt()
                val chosen = recent?.quality ?: desired
                if (probeGeneration == generation && recent == null && (cached == null || chosen > cached)) {
                    attempts.put(source, Attempt(now, chosen))
                }
                maxOf(cached ?: NetworkQuality.LOW, chosen)
            }
            return DownloadDecision(cached == null || quality > cached, quality)
        }

        fun clear() = synchronized(attempts) {
            generation++
            attempts.evictAll()
        }
    }

    private fun thumbnailRequest(
        originalUrl: String?,
        decision: DownloadDecision,
        currentUrl: () -> String?,
        onUrlChanged: (String?) -> Unit,
        retryMissingVideo: Boolean,
    ): ImageRequest {
        val requestedUrl = currentUrl()
        val cacheKey = generateCacheKeySync(requestedUrl, decision.quality)
        return ImageRequest.Builder(appContext())
            .data(requestedUrl)
            .diskCacheKey(cacheKey)
            .memoryCacheKey(cacheKey)
            .listener(
                onSuccess = { _, result ->
                    if (result.dataSource != DataSource.MEMORY_CACHE) {
                        requestedUrl?.let(PlaylistThumbnailStore::save)
                    }
                    if (originalUrl != null && decision.useNetwork) {
                        cacheMetadataStore.save(originalUrl, decision.quality)
                    }
                },
                onError = { _, result ->
                    val url = currentUrl()
                    val id = url?.learnablePlaylistArtworkId()
                    if (url != null && url != originalUrl && id != null) {
                        clearCacheForKey(url, decision.quality, invalidateColors = false)
                        PlaylistThumbnailStore.clear(id)
                        onUrlChanged(originalUrl)
                        return@listener
                    }
                    if (retryMissingVideo && result.throwable.message?.contains("404") == true &&
                        url?.contains("i.ytimg.com/vi/") == true) {
                        url.getNextYouTubeFallback()?.let(onUrlChanged)
                    }
                },
            )
            .build()
    }

    @Composable
    fun Thumbnail(
        thumbnailUrl: String?,
        contentDescription: String? = null,
        contentScale: ContentScale = ContentScale.Crop,
        modifier: Modifier = Modifier.clip(thumbnailShape()).fillMaxSize(),
        shape: Shape? = thumbnailShape(),
    ) {
        val sourceUrl = rememberDisplayArtworkSource(thumbnailUrl)
        val resolvedScale = rememberVideoArtworkContentScale(sourceUrl, contentScale)
        val imageModifier = if (shape == null) modifier else modifier.glassImageRim(shape)
        val cleanedUrl = sourceUrl?.let { cleanPrefix(it) }
        val validUrl = if (cleanedUrl.isNullOrBlank() || cleanedUrl == "null") null else cleanedUrl
        val decision = getDownloadDecision(validUrl)
        val version by storeVersion.collectAsStateWithLifecycle()
        val localRevision = rememberLocalArtworkRevision(validUrl)
        var currentUrl by remember(validUrl, version, localRevision, decision.sourceSize) {
            mutableStateOf(validUrl?.thumbnail(decision.sourceSize).also { modUrl ->
                // if (validUrl != null) Timber.tag("ImageCache").d("URL: original=%s, modified=%s", validUrl, modUrl)
            })
        }

        val loadingPainter = rememberFramedImageLoadingPainter(
            thumbnailUrl,
        )

        if (currentUrl == null) {
            MissingArtworkThumbnail(imageModifier, resolvedScale, loadingPainter)
            return
        }

        val loading = remember(currentUrl) { mutableStateOf(true) }

        val request = thumbnailRequest(
            validUrl,
            decision,
            currentUrl = { currentUrl },
            onUrlChanged = { currentUrl = it },
            retryMissingVideo = true,
        )

        AsyncImage(
            model = request,
            imageLoader = LOADER,
            contentDescription = contentDescription,
            contentScale = resolvedScale,
            modifier = imageModifier.imageLoadingBackground(loadingPainter) { loading.value },
            placeholder = loadingPainter,
            error = rememberArtworkResourcePainter(),
            fallback = rememberArtworkResourcePainter(),
            onLoading = { loading.value = true },
            onSuccess = { loading.value = false },
            onError = { loading.value = false },
        )
    }

    @Composable
    fun Painter(
        thumbnailUrl: String?,
        contentScale: ContentScale = ContentScale.Crop,
        @DrawableRes placeholder: Int? = null,
        @DrawableRes error: Int = R.drawable.ic_launcher_box,
        @DrawableRes fallback: Int = R.drawable.ic_launcher_box,
        onLoading: ((State.Loading) -> Unit)? = null,
        onSuccess: ((State.Success) -> Unit)? = null,
        onError: ((State.Error) -> Unit)? = null,
        publishArtworkColors: Boolean = false,
        onArtworkDrawn: (() -> Unit)? = null,
        playerArtworkMediaId: String? = null,
        sharePlayerArtwork: Boolean = false,
    ): AsyncImagePainter {
        val requestProfile = rememberArtworkProfile()
        val sourceUrl = rememberDisplayArtworkSource(thumbnailUrl, requestProfile)
        val transformArtwork = rememberVideoArtworkPainterTransform(sourceUrl, contentScale)
        val cleanedUrl = sourceUrl?.let { cleanPrefix(it) }
        val validUrl = if (cleanedUrl.isNullOrBlank() || cleanedUrl == "null") null else cleanedUrl
        val decision = getDownloadDecision(validUrl)
        val version by storeVersion.collectAsStateWithLifecycle()
        val sourceRevision = rememberArtworkRevision(validUrl, requestProfile)
        val colorSource = validUrl?.let { currentArtworkSource(it, requestProfile) }
        var currentUrl by remember(validUrl, requestProfile, version, sourceRevision, decision.sourceSize) {
            mutableStateOf(validUrl?.thumbnail(decision.sourceSize).also { modUrl ->
                // if (validUrl != null) Timber.tag("ImageCache").d("URL: original=%s, modified=%s", validUrl, modUrl)
            })
        }

        val loadingPainter = rememberThemedImageLoadingPainter(
            thumbnailUrl,
            placeholder,
        )

        // A null model never resolves in Coil (the placeholder would show forever). Load the
        // fallback box as a resource so the painter settles on a drawable instead of hanging.
        if (currentUrl == null) {
            return rememberAsyncImagePainter(
                model = ImageRequest.Builder(appContext())
                    .data(R.drawable.ic_launcher_box)
                    .build(),
                imageLoader = LOADER,
                contentScale = contentScale,
                placeholder = loadingPainter,
                error = rememberArtworkResourcePainter(error),
                fallback = rememberArtworkResourcePainter(fallback)
            )
        }

        val fileUrl = validUrl?.takeIf(::isFileArtwork)
        var fileRevision by remember(fileUrl, sourceRevision) { mutableStateOf<String?>(null) }
        if (fileUrl != null) LaunchedEffect(fileUrl, sourceRevision) {
            fileRevision = withContext(NzikDispatchers.DATA) { artworkFileRevision(fileUrl) }
        }
        val attemptedUrl = currentUrl
        val stamp = fileRevision.orEmpty()
        val cacheKey = generateCacheKeySync(attemptedUrl, decision.quality).withFileRevision(stamp)
        val requestedData = attemptedUrl.takeIf { fileUrl == null || fileRevision != null }
        val usesPlayerArtwork = sharePlayerArtwork || playerArtworkMediaId != null
        val cacheEpoch = if (usesPlayerArtwork) playerArtworkCacheEpoch.collectAsStateWithLifecycle().value else 0
        val retainedArtwork = if (playerArtworkMediaId != null) {
            remember(requestProfile, playerArtworkMediaId, cacheEpoch) { PlayerArtworkContinuity() }
        } else null
        fun isCurrentRequest(): Boolean = requestedData != null && attemptedUrl == currentUrl &&
            getActiveProfile(appContext()) == requestProfile &&
            (colorSource == null || isCurrentArtworkSource(colorSource)) &&
            generateCacheKeySync(attemptedUrl, decision.quality).withFileRevision(stamp) == cacheKey

        val request = ImageRequest.Builder(appContext())
            .data(requestedData)
            .diskCacheKey(cacheKey)
            .memoryCacheKey(cacheKey)
            .apply {
                if (usesPlayerArtwork) {
                    extras[PlayerArtworkDecodeScope.extra] = PlayerArtworkDecodeScope(requestProfile, cacheEpoch)
                }
            }
            .listener(
                onSuccess = { _, result ->
                    if (!isCurrentRequest()) return@listener
                    if (result.dataSource != DataSource.MEMORY_CACHE) {
                        attemptedUrl?.let(PlaylistThumbnailStore::save)
                    }
                    if (validUrl != null && decision.useNetwork) {
                        cacheMetadataStore.save(validUrl, decision.quality)
                    }
                },
                onError = { _, result ->
                    if (!isCurrentRequest()) return@listener
                    val next = nextPainterArtwork(attemptedUrl, validUrl, result)
                    if (next != null) {
                        if (next == validUrl) {
                            clearCacheForKey(attemptedUrl, decision.quality, invalidateColors = false)
                            attemptedUrl?.learnablePlaylistArtworkId()?.let(PlaylistThumbnailStore::clear)
                        }
                        currentUrl = next
                    }
                }
            )
            .build()

        // Some player backgrounds use the success callback without drawing the painter.
        val window = LocalWindowInfo.current.containerSize
        val displayEdge = maxOf(window.width, window.height).takeIf { it > 0 } ?: 1000
        val sizedRequest = if (isFileArtwork(currentUrl) || decision.original) {
            request.newBuilder().size(displayEdge, displayEdge).build()
        } else request
        val displayRequest = if (playerArtworkMediaId != null) {
            sizedRequest.newBuilder().precision(Precision.INEXACT).crossfade(false).build()
        } else sizedRequest

        // Coil's CrossfadePainter freezes intrinsicSize and keeps applying its original Crop
        // after the fade ends, preventing this painter's live Crop/Fit geometry from taking over.
        val painterRequest = if (isVideoArtwork(sourceUrl) && contentScale == ContentScale.Crop) {
            displayRequest.newBuilder().crossfade(false).build()
        } else displayRequest
        val errorPainter = rememberArtworkResourcePainter(error)
        val reportScope = if (onArtworkDrawn != null) rememberCoroutineScope() else null
        fun matches(result: ImageRequest): Boolean =
            result.data == requestedData && result.memoryCacheKey == cacheKey && isCurrentRequest()

        fun drawnPainter(source: androidx.compose.ui.graphics.painter.Painter, request: ImageRequest) =
            if (reportScope != null && onArtworkDrawn != null && matches(request)) {
                ArtworkPresentedPainter(source) {
                    reportScope.launch { if (matches(request)) onArtworkDrawn() }
                }
            } else source

        fun readyArtwork(): Painter? = retainedArtwork?.painter ?: if (playerArtworkMediaId != null && requestedData != null) {
            LOADER.memoryCache?.get(MemoryCache.Key(cacheKey))?.image?.let {
                transformArtwork(it.asPainter(appContext()))
            }
        } else null

        val painter = rememberAsyncImagePainter(
            model = painterRequest,
            imageLoader = LOADER,
            contentScale = contentScale,
            transform = { state ->
                when (state) {
                    is State.Loading -> state.copy(painter = readyArtwork() ?: loadingPainter)
                    is State.Success -> {
                        val artwork = transformArtwork(state.painter)
                        if (matches(state.result.request)) retainedArtwork?.painter = artwork
                        state.copy(painter = drawnPainter(artwork, state.result.request))
                    }
                    is State.Error -> {
                        val terminal = nextPainterArtwork(attemptedUrl, validUrl, state.result) == null
                        state.copy(painter = if (terminal && onArtworkDrawn != null && matches(state.result.request)) {
                            // Settle on the fallback frame without acknowledging the retained pixels.
                            drawnPainter(errorPainter, state.result.request)
                        } else readyArtwork() ?: errorPainter)
                    }
                    State.Empty -> readyArtwork()?.let { State.Loading(it) } ?: state
                }
            },
            onState = { state ->
                when (state) {
                    is State.Loading -> if (state.painter === loadingPainter) onLoading?.invoke(state)
                    is State.Success -> if (matches(state.result.request)) onSuccess?.invoke(state)
                    is State.Error -> if (matches(state.result.request) &&
                        nextPainterArtwork(attemptedUrl, validUrl, state.result) == null
                    ) onError?.invoke(state)
                    State.Empty -> Unit
                }
            },
        )
        if (publishArtworkColors && colorSource != null && requestedData != null) {
            DisposableEffect(colorSource, painter, requestedData, cacheKey) {
                val remove = SharedArtworkColors.displayed.register(colorSource, painter.state.mapNotNull { frame ->
                    when (frame) {
                        is State.Success -> if (matches(frame.result.request)) {
                            (frame.result.image as? BitmapImage)?.let {
                                DisplayedArtworkResult.Ready(DisplayedArtwork(it.bitmap, stamp))
                            } ?: DisplayedArtworkResult.Unavailable
                        } else null
                        is State.Error -> DisplayedArtworkResult.Unavailable.takeIf {
                            matches(frame.result.request) && nextPainterArtwork(attemptedUrl, validUrl, frame.result) == null
                        }
                        is State.Loading, State.Empty -> null
                    }
                })
                onDispose(remove)
            }
        }
        return painter
    }

    private fun nextPainterArtwork(url: String?, source: String?, result: ErrorResult): String? {
        if (url == null) return null
        if (url != source && url.learnablePlaylistArtworkId() != null) return source
        return if (result.throwable.message.orEmpty().contains("404")) url.getNextYouTubeFallback() else null
    }

    @Composable
    fun SubcomposeImage(
        thumbnailUrl: String?,
        contentDescription: String? = null,
        contentScale: ContentScale = ContentScale.Crop,
        modifier: Modifier = Modifier,
        @DrawableRes error: Int = R.drawable.ic_launcher_box,
        shape: Shape = RectangleShape,
    ) {
        val sourceUrl = rememberDisplayArtworkSource(thumbnailUrl)
        val resolvedScale = rememberVideoArtworkContentScale(sourceUrl, contentScale)
        val imageModifier = modifier.glassImageRim(shape)
        val cleanedUrl = sourceUrl?.let { cleanPrefix(it) }
        val validUrl = if (cleanedUrl.isNullOrBlank() || cleanedUrl == "null") null else cleanedUrl
        val decision = getDownloadDecision(validUrl)
        val version by storeVersion.collectAsStateWithLifecycle()
        val localRevision = rememberLocalArtworkRevision(validUrl)
        var currentUrl by remember(validUrl, version, localRevision, decision.sourceSize) {
            mutableStateOf(validUrl?.thumbnail(decision.sourceSize))
        }

        val loadingPainter = rememberFramedImageLoadingPainter(
            thumbnailUrl,
        )

        if (currentUrl == null) {
            MissingArtworkThumbnail(imageModifier, resolvedScale, loadingPainter)
            return
        }

        val request = thumbnailRequest(
            validUrl,
            decision,
            currentUrl = { currentUrl },
            onUrlChanged = { currentUrl = it },
            retryMissingVideo = false,
        )

        SubcomposeAsyncImage(
            model = request,
            imageLoader = LOADER,
            contentDescription = contentDescription,
            contentScale = resolvedScale,
            modifier = imageModifier,
            loading = {
                Image(
                    painter = loadingPainter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().imageLoadingBackground(loadingPainter),
                    contentScale = resolvedScale
                )
            },
            error = {
                Image(
                    painter = rememberArtworkResourcePainter(error),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = resolvedScale
                )
            }
        )
    }

    @Composable
    fun AsyncImage(
        thumbnailUrl: String?,
        contentDescription: String? = null,
        contentScale: ContentScale = ContentScale.Crop,
        modifier: Modifier = Modifier,
        onLoading: ((State.Loading) -> Unit)? = null,
        onSuccess: ((State.Success) -> Unit)? = null,
        onError: ((State.Error) -> Unit)? = null,
        shape: Shape? = RectangleShape,
    ) {
        val sourceUrl = rememberDisplayArtworkSource(thumbnailUrl)
        val resolvedScale = rememberVideoArtworkContentScale(sourceUrl, contentScale)
        val imageModifier = if (shape == null) modifier else modifier.glassImageRim(shape)
        val cleanedUrl = sourceUrl?.let { cleanPrefix(it) }
        val validUrl = if (cleanedUrl.isNullOrBlank() || cleanedUrl == "null") null else cleanedUrl
        val decision = getDownloadDecision(validUrl)
        val version by storeVersion.collectAsStateWithLifecycle()
        val localRevision = rememberLocalArtworkRevision(validUrl)
        var currentUrl by remember(validUrl, version, localRevision, decision.sourceSize) {
            mutableStateOf(validUrl?.thumbnail(decision.sourceSize).also { modUrl ->
                //if (validUrl != null) Timber.tag("ImageCache").d("URL: original=%s, modified=%s", validUrl, modUrl)
            })
        }

        val loadingPainter = rememberFramedImageLoadingPainter(
            thumbnailUrl,
        )

        if (currentUrl == null) {
            MissingArtworkThumbnail(imageModifier, resolvedScale, loadingPainter)
            return
        }

        val loading = remember(currentUrl) { mutableStateOf(true) }

        val request = thumbnailRequest(
            validUrl,
            decision,
            currentUrl = { currentUrl },
            onUrlChanged = { currentUrl = it },
            retryMissingVideo = true,
        )

        AsyncImage(
            model = request,
            imageLoader = LOADER,
            contentDescription = contentDescription,
            contentScale = resolvedScale,
            modifier = imageModifier.imageLoadingBackground(loadingPainter) { loading.value },
            placeholder = loadingPainter,
            error = rememberArtworkResourcePainter(),
            fallback = rememberArtworkResourcePainter(),
            onLoading = { state -> loading.value = true; onLoading?.invoke(state) },
            onSuccess = { state -> loading.value = false; onSuccess?.invoke(state) },
            onError = { state ->
                loading.value = false
                val errorMsg = state.result.throwable.message ?: ""
                if (errorMsg.contains("404") && currentUrl?.contains("i.ytimg.com/vi/") == true) {
                    val fallback = currentUrl.getNextYouTubeFallback()
                    if (fallback != null) {
                        // Re-trigger via currentUrl in listener above
                        return@AsyncImage
                    }
                }
                onError?.invoke(state)
            }
        )
    }

    /**
     * Renders an image from a possibly-null model (typically a local file) with the
     * app's standard null handling: while [model] is null, the [placeholder] shows for
     * [fallbackDelayMs], after which the [fallback] drawable replaces it instead of
     * spinning forever. Mirrors the null branch of [AsyncImage] and [Thumbnail].
     *
     * [cacheKey] (when provided) is used as both the memory and disk cache key, so a
     * content replacement behind a stable path (a re-downloaded file at the same URI)
     * forces Coil to re-read it — a bare file:// URI is string-equal across rewrites,
     * which would otherwise keep the previously decoded bitmap.
     * [isResolvingModel] keeps the loading frame while a caller resolves a model asynchronously.
     * [loadIdentity] can hold that frame stable across null-to-model resolution. Cache-key
     * changes still refresh the image request without choosing another loading glyph.
     */
    @Composable
    fun LocalAsyncImage(
        model: Any?,
        contentDescription: String?,
        contentScale: ContentScale = ContentScale.Crop,
        modifier: Modifier = Modifier,
        cacheKey: String? = null,
        @DrawableRes placeholder: Int? = null,
        @DrawableRes fallback: Int? = R.drawable.ic_launcher_box,
        fallbackDelayMs: Long = 4_000L,
        shape: Shape? = RectangleShape,
        isResolvingModel: Boolean = false,
        loadIdentity: Any? = model,
        crossfade: Boolean? = null,
        onSuccess: ((State.Success) -> Unit)? = null,
    ) {
        val imageModifier = if (shape == null) modifier else modifier.glassImageRim(shape)
        // Visual identity is independent of request/cache invalidations and model resolution.
        val loadingPainter = rememberFramedImageLoadingPainter(loadIdentity, placeholder)
        if (model == null) {
            var showFallback by remember(loadIdentity) { mutableStateOf(false) }
            LaunchedEffect(loadIdentity, isResolvingModel, fallbackDelayMs) {
                showFallback = false
                if (!isResolvingModel) {
                    kotlinx.coroutines.delay(fallbackDelayMs)
                    showFallback = true
                }
            }
            val painter = if (showFallback) fallback?.let { rememberArtworkResourcePainter(it) } else loadingPainter
            if (painter != null) Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = imageModifier.fillMaxSize().imageLoadingBackground(loadingPainter) { !showFallback },
                contentScale = contentScale
            )
            return
        }

        val loading = remember(listOf(model, cacheKey)) { mutableStateOf(true) }

        val request = ImageRequest.Builder(appContext())
            .data(model)
            .apply { if (cacheKey != null) { diskCacheKey(cacheKey); memoryCacheKey(cacheKey) } }
            .apply { if (crossfade != null) crossfade(crossfade) }
            .build()

        AsyncImage(
            model = request,
            imageLoader = LOADER,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = imageModifier.imageLoadingBackground(loadingPainter) { loading.value },
            placeholder = loadingPainter,
            error = fallback?.let { rememberArtworkResourcePainter(it) },
            onLoading = { loading.value = true },
            onSuccess = { state -> loading.value = false; onSuccess?.invoke(state) },
            onError = { loading.value = false },
        )
    }

    /** Loads a borrowed Coil bitmap with the shared cache and URL fallback policy.
     * [requestSize] bounds decoding for consumers that serialize small artwork.
     * [qualityOverride] selects source quality for this request without changing phone preferences.
     * A consumer that caches a derived result can set [cacheDecodedBitmap] to false to avoid
     * replacing the displayed cover in memory with a smaller decode of the same source.
     */
    suspend fun loadBitmap(
        url: String?,
        allowHardware: Boolean = false,
        requestSize: Int? = null,
        qualityOverride: NetworkQuality? = null,
        cacheOnly: Boolean = false,
        cropToSquare: Boolean = false,
        cacheDecodedBitmap: Boolean = true,
    ): Bitmap? {
        require(requestSize == null || requestSize > 0)
        if (url.isNullOrBlank() || url == "null") {
            return null
        }

        val defaultDecision = getDownloadDecision(url)
        val decision = qualityOverride?.let { quality ->
            defaultDecision.copy(
                quality = quality,
                original = false,
                useNetwork = defaultDecision.useNetwork || quality.ordinal > defaultDecision.quality.ordinal,
            )
        } ?: defaultDecision
        var currentUrl = url.thumbnail(decision.sourceSize)
        val fileRevision = if (isFileArtwork(url)) withContext(NzikDispatchers.DATA) {
            artworkFileRevision(url)
        } else ""
        // Timber.tag("ImageCache").d("URL (loadBitmap): original=%s, modified=%s", url, currentUrl)
        var lastError: String? = null

        while (currentUrl != null) {
            val isLocalFile = isFileArtwork(currentUrl)

            val cacheKey = generateCacheKeySync(currentUrl, decision.quality).withFileRevision(fileRevision)
            val requestBuilder = ImageRequest.Builder(appContext())
                .data(currentUrl)
                .diskCacheKey(cacheKey)
                .memoryCacheKey(cacheKey)
                .allowHardware(allowHardware)
                .networkCachePolicy(if (cacheOnly) CachePolicy.DISABLED else CachePolicy.ENABLED)
                .scale(if (cropToSquare) Scale.FILL else Scale.FIT)

            if (!cacheDecodedBitmap) {
                requestBuilder.memoryCachePolicy(CachePolicy.READ_ONLY).precision(Precision.INEXACT)
            }

            val boundedSize = requestSize ?: 1000.takeIf { isLocalFile || decision.original }
            if (boundedSize != null) {
                requestBuilder.size(boundedSize, boundedSize)
            }

            val request = requestBuilder.build()

            val result = LOADER.execute(request)
            if (result.image != null) {
                val dataSource = (result as? SuccessResult)?.dataSource

                if (!cacheOnly && dataSource != null && dataSource != DataSource.MEMORY_CACHE) {
                    PlaylistThumbnailStore.save(currentUrl)
                }
                if (!cacheOnly && decision.useNetwork) {
                    cacheMetadataStore.save(url, decision.quality)
                }
                return result.image?.toBitmap() ?: run {
                    Timber.tag("ImageCacheFactory").w("Null bitmap for image result")
                    return null
                }
            }

            lastError = (result as? ErrorResult)?.throwable?.message ?: appTextResources().getString(R.string.image_error_unknown)
            //Timber.tag("ImageCache").e("Error (loadBitmap): original=%s, modified=%s, error=%s", url, currentUrl, lastError)

            // Un-swap fallback for loadBitmap
            val id = currentUrl.learnablePlaylistArtworkId()
            if (currentUrl != url && id != null) {

                if (!cacheOnly) {
                    clearCacheForKey(currentUrl, decision.quality, invalidateColors = false)
                    PlaylistThumbnailStore.clear(id)
                }
                currentUrl = url
                continue
            }

            if (lastError.contains("404") && currentUrl.contains("i.ytimg.com/vi/")) {
                val fallback = currentUrl.getNextYouTubeFallback()
                if (fallback != null) {

                    currentUrl = fallback
                    continue
                }
            }
            break
        }

        return null
    }

    suspend fun preloadImage(thumbnailUrl: String?, playerDisplayEdge: Int? = null) {
        val source = artworkColorSource(thumbnailUrl, getActiveProfile(appContext())) ?: return
        val fileRevision = if (isFileArtwork(source.uri)) withContext(NzikDispatchers.DATA) {
            artworkFileRevision(source.uri)
        } else ""
        val decision = getDownloadDecision(source.uri)
        val finalUrl = source.uri.thumbnail(decision.sourceSize)
        val decodeScope = playerDisplayEdge?.let { PlayerArtworkDecodeScope(source.profile, playerArtworkCacheEpoch.value) }
        // Timber.tag("ImageCache").d("URL (preload): original=%s, modified=%s", thumbnailUrl, finalUrl)

        suspend fun executeWithFallback(url: String) {
            if (!isCurrentArtworkSource(source)) return
            val cacheKey = generateCacheKeySync(url, decision.quality).withFileRevision(fileRevision)
            val request = ImageRequest.Builder(appContext())
                .data(url)
                .apply {
                    if (isFileArtwork(url) || decision.original) {
                        val edge = playerDisplayEdge ?: 1000
                        size(edge, edge)
                    }
                    if (playerDisplayEdge != null) {
                        scale(Scale.FILL)
                        precision(Precision.INEXACT)
                    }
                    if (decodeScope != null) extras[PlayerArtworkDecodeScope.extra] = decodeScope
                }
                .diskCacheKey(cacheKey)
                .memoryCacheKey(cacheKey)
                .build()

            val result = LOADER.execute(request)
            if (!isCurrentArtworkSource(source) || getActiveProfile(appContext()) != source.profile) return
            if (result is SuccessResult) {
                if (decision.useNetwork) {
                    cacheMetadataStore.save(source.uri, decision.quality)
                }
            } else if (result is ErrorResult) {
                val errorMsg = result.throwable.message ?: ""
                // Timber.tag("ImageCache").e(result.throwable, "Error (preload): original=%s, modified=%s, error=%s", thumbnailUrl, url, errorMsg)
                if (errorMsg.contains("404") && url.contains("i.ytimg.com/vi/")) {
                    val fallback = url.getNextYouTubeFallback()
                    if (fallback != null) {
                        executeWithFallback(fallback)
                    }
                }
            }
        }

        if (finalUrl != null) executeWithFallback(finalUrl)
    }

    private fun isFileArtwork(url: String?): Boolean =
        url != null && (url.startsWith("file:", ignoreCase = true) || File(url).isAbsolute)

    private fun String.withFileRevision(revision: String): String =
        if (revision.isEmpty()) this else "$this-$revision"

    fun isImageCached(thumbnailUrl: String?): Boolean = cacheMetadataStore.get(thumbnailUrl ?: "") != null

    fun getDiskCache(): DiskCache = DISK_CACHE

    /** Reads encoded cover bytes while the cache snapshot prevents eviction. */
    internal suspend fun <T : Any> readCachedCover(url: String, read: suspend (InputStream) -> T?): T? {
        val cache = getDiskCache()
        val sources = coverSourceCandidates(url)
        val tried = mutableSetOf<String>()
        for (quality in NetworkQuality.entries.asReversed()) {
            for (source in sources) {
                val key = generateCacheKeySync(source, quality)
                if (!tried.add(key)) continue
                val snapshot = cache.openSnapshot(key) ?: continue
                snapshot.use {
                    val result = cache.fileSystem.source(snapshot.data).buffer().inputStream().use { read(it) }
                    if (result != null) return result
                }
            }
        }
        return null
    }

    /** Includes the original URL and the variants the artwork renderer can display. */
    internal fun coverSourceCandidates(url: String): List<String> = buildList {
        add(url)
        val host = url.toHttpUrl().host
        if (host != "i.ytimg.com" && host != "yt3.ggpht.com" &&
            host != "googleusercontent.com" && !host.endsWith(".googleusercontent.com")
        ) return@buildList

        for (quality in NetworkQuality.entries.asReversed()) {
            generateSequence(url.thumbnail(quality.size)) { it.getNextYouTubeFallback() }.forEach {
                if (it !in this) add(it)
            }
        }
    }

    fun clearImageCache() {
        try {
            clearImageCacheOrThrow()
        } catch (error: Exception) {
            Timber.tag("ImageCacheFactory").w("Unable to clear image cache (%s)", error.javaClass.simpleName)
        }
    }

    internal fun clearImageCacheOrThrow() = synchronized(ProfileArtworkCache.diskMutationLock) {
        var diskCleared = false
        try {
            LOADER.memoryCache?.clear()
            DISK_CACHE.clear()
            diskCleared = true
            PlaylistThumbnailStore.clearAll()
            cacheMetadataStore.clearAll()
            cacheKeyMap.evictAll()
            cooldownMap.clear()
        } finally {
            // Refresh observers even when only part of the cache could be cleared.
            playerArtworkCacheEpoch.update { it + 1 }
            if (diskCleared) invalidateClearedArtwork() else SharedArtworkColors.invalidate()
        }
    }

    internal fun clearBoundProfileCache(
        context: Context,
        owner: ProfileStorageIdentity,
        expected: File,
        revision: Long,
        clearDisk: Boolean,
        metadataFiles: Set<String>,
        verify: () -> Boolean,
    ): Boolean = ProfileArtworkCache.clearBoundProfileCache(
        context, owner, expected, revision, clearDisk, metadataFiles, verify,
        clearMemory = { LOADER.memoryCache?.clear() },
        resetPresentationCaches = {
            cacheKeyMap.evictAll()
            cooldownMap.clear()
        },
        notifyArtworkInvalidated = {
            playerArtworkCacheEpoch.update { it + 1 }
            if (it) invalidateClearedArtwork() else SharedArtworkColors.invalidate()
        },
    )

    private fun invalidateClearedArtwork() {
        diskRevisions.clear()
        ArtworkRevisions.invalidate()
        storeVersion.update { it + 1 }
        SharedArtworkColors.invalidate()
    }

    fun getCacheSize(): Long = try { DISK_CACHE.size } catch (e: Exception) { 0L }
}
