package app.n_zik.android.core.coil

import java.io.File
import android.content.Context
import coil3.disk.DiskCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory.NetworkQuality
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.coilImageCacheDir
import app.n_zik.android.core.profiles.isLegacyStorageShared
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.profileStorageGeneration
import app.n_zik.android.core.profiles.settingsProfileFor
import app.n_zik.android.utils.getActiveProfile

/** Owns physical Coil cache storage and profile-bound thumbnail metadata. */
internal object ProfileArtworkCache {
    val diskMutationLock = Any()
    private var metadataRevision = 0L

    internal fun <T> captureCleanupState(capture: (Long) -> T): T = synchronized(diskMutationLock) {
        capture(metadataRevision)
    }

    private fun updateCleanupMetadata(write: () -> Unit) = synchronized(diskMutationLock) {
        metadataRevision++
        write()
    }

    val DISK_CACHE: DiskCache by lazy {
        val app = appContext()
        val profile = getActiveProfile(app)
        val owner = settingsProfileFor(app, profile, ProfileDataItem.IMAGES)
        val preferences = profileDataPrefs(app, owner)
        // The Coil image cache of the ACTIVE profile (spec-profile-data-separation).
        val cacheDir = coilImageCacheDir(app, profile)
        val maxSizeBytes = configuredImageCacheBytes(preferences)
        if (isLegacyStorageShared(app, profile, ProfileDataItem.IMAGES)) {
            ReadOnlyCoilCache(checkNotNull(cacheDir.parentFile).canonicalFile.resolve(cacheDir.name),
                maxSizeBytes, diskRevisions::resolve)
        } else {
            CleanupCoilCache(
                DiskCache.Builder().directory(cacheDir.toPath().toOkioPath()).maxSizeBytes(maxSizeBytes).build(),
                diskMutationLock,
                diskRevisions::resolve,
            )
        }
    }

    private const val PLAYLIST_THUMBNAIL_STORE = "playlist_thumbnail_store"
    private const val IMAGE_METADATA_STORE = "image_cache_metadata"

    internal fun profileMetadataFiles(identity: ProfileStorageIdentity): Set<String> =
        setOf(PLAYLIST_THUMBNAIL_STORE, IMAGE_METADATA_STORE).flatMap { base ->
            val name = "${base}_${identity.id}_${identity.generation}.xml"
            listOf(name, "$name.bak")
        }.toSet()

    internal fun profileMetadataStoreName(context: Context, base: String): String {
        val id = getActiveProfile(context)
        return "${base}_${id}_${profileStorageGeneration(context, id)}"
    }

    val storeVersion = MutableStateFlow(0)
    val diskRevisions = ArtworkDiskRevisions()

    internal object PlaylistThumbnailStore {
        private const val PREFS_NAME = PLAYLIST_THUMBNAIL_STORE
        private fun getPrefs() = appContext().let { app ->
            app.getSharedPreferences(profileMetadataStoreName(app, PREFS_NAME), Context.MODE_PRIVATE)
        }

        // Store format: "lowResUrl|highResUrl"
        @Synchronized
        fun save(id: String, url: String, isHigh: Boolean) {
            try {
                val preferences = getPrefs()
                val current = preferences.getString(id, null)
                val parts = current?.split("|")
                var low = parts?.getOrNull(0).orEmpty()
                var high = parts?.getOrNull(1).orEmpty()

                val newScore = url.getYouTubeQualityScore()

                // Only overwrite if the quality is better for the "high" slot
                // or worse for the "low" slot (to keep an ultra-light fallback thumbnail)
                if (isHigh || newScore >= 5) {
                    if (newScore >= high.getYouTubeQualityScore()) {
                        high = url
                    }
                } else {
                    if (low.isEmpty() || newScore < low.getYouTubeQualityScore()) {
                        low = url
                    }
                }

                val updated = "$low|$high"
                if ((low.isNotEmpty() || high.isNotEmpty()) && updated != current) {
                    updateCleanupMetadata { preferences.edit().putString(id, updated).apply() }
                    storeVersion.update { it + 1 }
                }
            } catch (error: Exception) {
                Timber.tag("ImageCacheFactory").w("Unable to store playlist thumbnail metadata (%s)", error.javaClass.simpleName)
            }
        }

        fun getHighUrl(id: String): String? {
            return try {
                val current = getPrefs().getString(id, null) ?: return null
                current.split("|").getOrNull(1)?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) { null }
        }

        fun getLowUrl(id: String): String? {
            return try {
                val current = getPrefs().getString(id, null) ?: return null
                current.split("|").getOrNull(0)?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) { null }
        }

        fun clear(id: String) {
            try {
                updateCleanupMetadata { getPrefs().edit().remove(id).apply() }
            } catch (error: Exception) {
                Timber.tag("ImageCacheFactory").w("Unable to remove playlist thumbnail metadata (%s)", error.javaClass.simpleName)
            }
        }

        fun clearAll() {
            try { updateCleanupMetadata { getPrefs().edit().clear().apply() } } catch (error: Exception) {
                Timber.tag("ImageCacheFactory").w("Unable to clear playlist thumbnail metadata (%s)", error.javaClass.simpleName)
            }
        }
    }

    object CacheMetadataStore {
        private const val PREFS_NAME = IMAGE_METADATA_STORE
        private fun getPrefs() = appContext().let { app ->
            app.getSharedPreferences(profileMetadataStoreName(app, PREFS_NAME), Context.MODE_PRIVATE)
        }

        fun save(url: String, quality: NetworkQuality) {
            try {
                val key = url.hashCode().toString()
                val preferences = getPrefs()
                if (preferences.getString(key, null)?.substringBefore(":") == quality.name) return
                updateCleanupMetadata { preferences.edit().putString(key, "${quality.name}:${System.currentTimeMillis()}").apply() }
            } catch (error: Exception) {
                Timber.tag("ImageCacheFactory").w("Unable to store image cache quality (%s)", error.javaClass.simpleName)
            }
        }

        fun get(url: String): NetworkQuality? {
            return try {
                val key = url.hashCode().toString()
                val value = getPrefs().getString(key, null) ?: return null
                NetworkQuality.valueOf(value.substringBefore(":"))
            } catch (e: Exception) { null }
        }

        fun clearAll() {
            try { updateCleanupMetadata { getPrefs().edit().clear().apply() } } catch (error: Exception) {
                Timber.tag("ImageCacheFactory").w("Unable to clear image cache quality (%s)", error.javaClass.simpleName)
            }
        }
    }

    /** Uses the frozen owner for both metadata stores even if profile selection changes concurrently. */
    fun clearBoundProfileCache(
        context: Context,
        owner: ProfileStorageIdentity,
        expected: File,
        revision: Long,
        clearDisk: Boolean,
        metadataFiles: Set<String>,
        verify: () -> Boolean,
        clearMemory: () -> Unit,
        resetPresentationCaches: () -> Unit,
        notifyArtworkInvalidated: (Boolean) -> Unit,
    ): Boolean {
        val cache = DISK_CACHE as? CleanupCoilCache ?: return false
        if (cache.directory.toFile().canonicalFile != expected) return false
        return cache.whileIdle {
            if (revision != metadataRevision || !verify()) return@whileIdle false
            try {
                clearMemory()
                if (clearDisk) cache.clear()
                val metadataCleared = listOf(PLAYLIST_THUMBNAIL_STORE, IMAGE_METADATA_STORE).map { base ->
                    val name = "${base}_${owner.id}_${owner.generation}"
                    if ("$name.xml" !in metadataFiles && "$name.xml.bak" !in metadataFiles) true
                    else {
                        metadataRevision++
                        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                    }
                }.all { it }
                resetPresentationCaches()
                metadataCleared && (!clearDisk || cache.size == 0L)
            } finally {
                notifyArtworkInvalidated(clearDisk)
            }
        }
    }

}
