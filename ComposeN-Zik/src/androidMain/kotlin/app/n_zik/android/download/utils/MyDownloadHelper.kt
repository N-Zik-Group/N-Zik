package app.n_zik.android.download.utils

import app.n_zik.android.core.database.Database
import app.n_zik.android.core.maintenance.DownloadRevalidationRecord
import app.n_zik.android.core.maintenance.MaintenanceDownloadRevalidationState
import app.n_zik.android.core.maintenance.MaintenanceDownloadSelfHealState
import app.n_zik.android.core.maintenance.MaintenanceStateStore
import app.n_zik.android.core.profiles.ProfileDataDirNames
import app.n_zik.android.core.profiles.LegacyMediaAccess
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.cacheDatabaseProvider
import app.n_zik.android.core.profiles.downloadsDir
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.profiles.profileSharedItems
import app.n_zik.android.core.profiles.resolveProfileDataDirNames
import app.n_zik.android.core.profiles.settingsProfileFor
import java.io.File
import java.util.UUID

import app.n_zik.android.download.services.MyDownloadService
import app.n_zik.android.download.services.ProfileDownloadCommands

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import app.n_zik.android.utils.artistTextOrDb
import app.n_zik.android.playback.services.createDownloadDataSourceFactory

import app.n_zik.android.enums.DownloadQualityFormat
import app.n_zik.android.enums.downloadQualityFormatKey
import app.n_zik.android.enums.ExoPlayerDiskDownloadCacheMaxSize
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.autoDownloadSongKey
import app.n_zik.android.utils.autoDownloadSongWhenAlbumBookmarkedKey
import app.n_zik.android.utils.autoDownloadSongWhenLikedKey
import app.n_zik.android.utils.downloadSyncedLyrics
import app.n_zik.android.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import app.n_zik.android.utils.ExternalUris
import app.n_zik.android.core.coil.ImageCacheFactory

import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.asExecutor
import timber.log.Timber
import kotlin.io.path.createTempDirectory
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.utils.isExplicit
import app.n_zik.android.cleanPrefix
import app.n_zik.android.R
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.innertube.YtMusic
import app.n_zik.android.utils.autoDownloadSongWhenPlaylistBookmarkedKey
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.components.playlist.loadPlaylistSource

@UnstableApi
/**
 * The resolved (cache dir, index DB name) of a profile's downloads
 * (spec-profile-data-separation): [dir] is null when the download cache is Disabled —
 * the caller then uses a process-local temp dir named [tempDirName] that deletes itself
 * after close. [indexDbName] is the base `exoplayer_internal.db` when the downloads are
 * shared, the suffixed `exoplayer_internal_<id>.db` when they are separate.
 */
internal data class DownloadCacheLocation(
    val dir: File?,
    val tempDirName: String,
    val indexDbName: String,
)

object MyDownloadHelper {
    private val coroutineScope = NzikDispatchers.fireAndForget(
        NzikDispatchers.DATA +
                CoroutineName("MyDownloadService-Executor-Scope")
    )

    // Semaphore to limit concurrent download preparation (avoids overwhelming the API)
    private val downloadPreparationSemaphore = Semaphore(3)

    private class DownloadCommandLock {
        val mutex = Mutex()
        var users = 0
    }

    private val downloadCommandLocks = mutableMapOf<Pair<ProfileDownloadCommands, String>, DownloadCommandLock>()
    private val pendingDownloadAdds = mutableMapOf<Pair<ProfileDownloadCommands, String>, String>()
    private data class PendingRemoval(val token: String, val previousAdd: String?)

    private val pendingDownloadRemovals = mutableMapOf<Pair<ProfileDownloadCommands, String>, PendingRemoval>()

    // Scheduling seam lets the failure test exercise the real toast deterministically.
    internal var scheduleDownloadRequest: (ProfileDownloadCommands, Context, DownloadRequest) -> Result<Unit> =
        ProfileDownloadCommands::sendAdd

    // While the class is not a singleton (lifecycle), there should only be one download state at a time

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"

    /** The base (unsuffixed) downloads directory name — see [ProfileDataDirNames.DOWNLOADS_DIR]. */
    const val CACHE_DIRNAME = ProfileDataDirNames.DOWNLOADS_DIR

    private lateinit var databaseProvider: DatabaseProvider
    lateinit var downloadCache: Cache

    private lateinit var downloadManager: DownloadManager
    lateinit var downloadQualityFormat: DownloadQualityFormat

    /** Non-null while the download-quality preferences listener is registered (registration guard). */
    private var downloadQualityPreferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    // URL cache with LRU eviction (max 500 entries): uses StreamUrlCache for headers/client tracking
    internal val songUrlCache = app.n_zik.android.playback.services.StreamUrlCache()

    /**
     * Checks if a download failure was caused by an expired/forbidden stream URL (403/410/416).
     * Used to trigger URL cache invalidation and retry.
     */
    fun isExpiredStreamError(exception: Exception?): Boolean {
        if (exception == null) return false
        // Walk the cause chain looking for HTTP response code errors
        var current: Throwable? = exception
        while (current != null) {
            if (current is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                val code = current.responseCode
                if (code == 403 || code == 410 || code == 416) return true
            }
            current = current.cause
        }
        return false
    }


    var downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    /** Session-scoped view for the maintenance sheet's Downloads row: populated ONLY by the download-manager events ([syncDownloads]), never by the [getDownloads] index seed at init — so the row resets to empty on every process start, while [downloads] keeps the full persistent library for the badges and the download manager UI. This invariant relies on media3 1.10.1 firing no per-download events at manager init (only `onInitialized`, terminal states excluded from the init list) — re-verify on any media3 upgrade. */
    var sessionDownloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    private val mutableProgresses = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progresses = mutableProgresses.asStateFlow()

    /** S1 (Phase 1 download fix, 2026-10-22): the removed count parked by [revalidateCompletedDownloads] — the re-validation runs before the app window is visible (a few seconds after process start, before MainActivity reaches onStart), where the system silently drops toasts; [MainActivity] consumes this once the window has focus (shows the toast + resets it to 0). */
    internal val pendingStaleStartupCount = MutableStateFlow(0)

    // Batch download tracking for "download all" progress notification
    var batchTotal = 0
        private set
    var batchCompleted = 0
        private set
    var isBatchActive = false
        private set

    fun isSongDownloaded(songId: String): Boolean {
        return downloads.value.values.any {
            it.state == Download.STATE_COMPLETED && it.request.id == songId
        }
    }

    @Synchronized
    fun startBatchDownload(total: Int) {
        batchTotal = total
        batchCompleted = 0
        isBatchActive = true
        Timber.tag("MyDownloadHelper").d("startBatchDownload: total=$total")
    }

    @Synchronized
    fun incrementBatchCompleted() {
        if (!isBatchActive) return
        batchCompleted += 1
    }

    @Synchronized
    fun skipBatchCompleted(count: Int) {
        if (!isBatchActive) return
        batchCompleted += count
        Timber.tag("MyDownloadHelper").d("skipBatchCompleted: skipped=$count, completed=$batchCompleted")
    }

    @Synchronized
    fun resetBatch() {
        batchTotal = 0
        batchCompleted = 0
        isBatchActive = false
    }

    fun getDownload(songId: String): Flow<Download?> {
        return downloads.map { it[songId] }

    }

    @SuppressLint("LongLogTag")
    @Synchronized
    fun getDownloads() {
        val result = mutableMapOf<String, Download>()
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.download.request.id] = cursor.download
            }
        }
        downloads.value = result

    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        ensureDownloadManagerInitialized(context)
        return downloadManager
    }

    /**
     * The (cache dir, index DB name) of the ACTIVE profile's downloads
     * (spec-profile-data-separation): the profile's own suffixed folder + suffixed index
     * when the downloads are separate, the base folder + base index when they are shared
     * (governed by the BASE profile's settings — see [downloadsDir]); [DownloadCacheLocation.dir]
     * is null when the (effective) download cache is Disabled — the caller then uses a
     * process-local temp dir named [DownloadCacheLocation.tempDirName] that deletes itself
     * after close. Extracted from [initDownloadCache] so the resolution is unit-testable.
     */
    internal fun downloadCacheLocation( context: Context ): DownloadCacheLocation {
        val profileId = getActiveProfile( context )
        val names = resolveProfileDataDirNames( profileId, context.profileSharedItems( profileId ) )
        return DownloadCacheLocation( downloadsDir( context, profileId ), names.downloadsDir, names.downloadsIndexDb )
    }

    @Synchronized
    private fun initDownloadCache( context: Context ): Cache {
        val active = getActiveProfile(context)
        LegacyMediaAccess.reader(context, active, ProfileDataItem.DOWNLOADS)?.let { return it }
        val location = downloadCacheLocation( context )
        val owner = settingsProfileFor(context, active, ProfileDataItem.DOWNLOADS)
        val settings = profileDataPrefs(context, owner)
        val cacheSize = settings.getEnum(exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskDownloadCacheMaxSize.`2GB`)
        val quota = when (cacheSize) {
            ExoPlayerDiskDownloadCacheMaxSize.Unlimited -> Long.MAX_VALUE
            ExoPlayerDiskDownloadCacheMaxSize.Disabled -> 0L
            else -> cacheSize.bytes
        }
        val cacheDir = location.dir ?:
            // Temporary directory deletes itself after close
            // It means songs remain on device as long as it's open
            createTempDirectory( location.tempDirName ).toFile()

        // Ensure this location exists
        cacheDir.mkdirs()

        val evictor = if (location.dir == null) LeastRecentlyUsedCacheEvictor(cacheSize.bytes) else NoOpCacheEvictor()
        val cache = SimpleCache(cacheDir, evictor, getDatabaseProvider(context, location.indexDbName))
        return if (location.dir == null || quota == Long.MAX_VALUE) cache
        else DownloadQuotaCache(cache, quota, cacheDir.canonicalFile)
    }

    @Synchronized
    fun getDownloadCache( context: Context ): Cache {
        if ( !MyDownloadHelper::downloadCache.isInitialized )
            downloadCache = initDownloadCache( context )

        return downloadCache
    }

    @Synchronized
    internal fun boundDownloadCache(): Cache? =
        if (MyDownloadHelper::downloadCache.isInitialized) downloadCache else null

    @Synchronized
    private fun ensureDownloadManagerInitialized(context: Context) {
        downloadQualityFormat =
            context.preferences.getEnum(downloadQualityFormatKey, DownloadQualityFormat.Auto)

        registerDownloadQualityPreferenceListener(context)

        if (!MyDownloadHelper::downloadManager.isInitialized) {
            val active = getActiveProfile(context)
            val protected = LegacyMediaAccess.isProtected(context, active, ProfileDataItem.DOWNLOADS)
            val commandOwner = ProfileDownloadCommands.capture(context)
            downloadManager = DownloadManager(
                context,
                getDatabaseProvider(context, LegacyMediaAccess.downloadIndex(context, active, downloadIndexDbName(context))),
                getDownloadCache(context),
                createDownloadDataSourceFactory(), // Use dedicated download resolver
                NzikDispatchers.DATA.asExecutor()
            ).apply {
                maxParallelDownloads = 3
                minRetryCount = 2
                requirements = Requirements(Requirements.NETWORK)

                val wasPaused = ProfileDownloadCommands.capture(context)?.isPaused(context) == true
                if (wasPaused || protected) {
                    pauseDownloads()
                }

                addListener(
                    object : DownloadManager.Listener {

                        override fun onInitialized(downloadManager: DownloadManager) {
                            if (!protected) wipeStuckDownloadsOnStart(downloadManager)
                        }

                        override fun onDownloadChanged(
                            downloadManager: DownloadManager,
                            download: Download,
                            finalException: Exception?
                        ) = run {
                            syncDownloads(download, commandOwner)

                            // Handle expired stream errors: invalidate URL cache and retry
                            if (download.state == Download.STATE_FAILED && isExpiredStreamError(finalException)) {
                                Timber.tag("MyDownloadHelper").w("Download failed due to expired stream for ${download.request.id}, invalidating caches")
                                songUrlCache.invalidate(download.request.id)
                                app.n_zik.android.playback.services.streamUrlCache.invalidate(download.request.id)
                            }

                            // Remove player cache after successful download to avoid stale cached streams
                            if (download.state == Download.STATE_COMPLETED) {
                                try {
                                    val playerCache = app.n_zik.android.playback.services.streamUrlCache
                                    playerCache.invalidate(download.request.id)
                                    Timber.tag("MyDownloadHelper").d("Invalidated player URL cache for completed download: ${download.request.id}")
                                } catch (e: Exception) {
                                    Timber.tag("MyDownloadHelper").w(e, "Failed to invalidate player cache for ${download.request.id}")
                                }
                            }
                        }

                        override fun onDownloadRemoved(
                            downloadManager: DownloadManager,
                            download: Download
                        ) = run {
                            syncDownloads(download, commandOwner, removed = true)
                            // Clean up progress when removed
                            mutableProgresses.update { it.toMutableMap().apply { remove(download.request.id) } }
                        }
                    }
                )
            }

            // Centralized polling job
            coroutineScope.launch {
                while (isActive) {
                    val currentDownloads = try {
                        downloadManager.currentDownloads
                    } catch (e: Exception) {
                        emptyList<Download>()
                    }

                    if (currentDownloads.isNotEmpty()) {
                        mutableProgresses.update { progresses ->
                            val newMap = progresses.toMutableMap()
                            var changed = false
                            currentDownloads.forEach { download ->
                                val progress = if (download.contentLength > 0) {
                                    download.bytesDownloaded.toFloat() / download.contentLength
                                } else {
                                    0f
                                }
                                if (newMap[download.request.id] != progress) {
                                    newMap[download.request.id] = progress
                                    changed = true
                                }
                            }
                            if (changed) newMap else progresses
                        }
                    }

                    // Battery optimization: slower polling if nothing is active
                    // Actually, we could potentially stop and restart based on onDownloadChanged,
//
                    // but a 2s delay when idle is much better than 1s constantly.
                    val delayTime = if (currentDownloads.isNotEmpty()) 1000L else 5000L
                    delay(delayTime)
                }
            }
            getDownloads()
            // S1 (Phase 1 download fix, 2026-10-21): the OS/OEM can wipe the span files
            // while the DownloadIndex survives (documented on Samsung) — re-validate the
//
            // completed library against the real files, off the main-Looper callback
            // that `onInitialized` above runs on (one file stat per span).
            if (!protected) coroutineScope.launch { revalidateCompletedDownloads(context, downloadManager) }
        }
    }

    /**
     * The startup download cleanup (run from the DownloadManager `onInitialized`
     * listener): the stuck (non-terminal) records are removed one by one — queued,
     * in progress or canceled downloads can never resume across a restart because
     * the stream URLs they were built from are expired. The completed (offline)
     * library is NEVER touched: [DownloadManager.getCurrentDownloads] excludes the
     * terminal states (completed / failed) by contract, and a full
     * `removeAllDownloads()` would destroy the downloaded data with them.
     */
    internal fun wipeStuckDownloadsOnStart(downloadManager: DownloadManager) {
        val ids = downloadManager.currentDownloads.map { it.request.id }
        if (ids.isEmpty()) return
        Timber.tag("MyDownloadHelper").d("Removing the ${ids.size} stuck download record(s) on app start")
        ids.forEach { id -> downloadManager.removeDownload(id) }
    }

    /**
     * S1 (Phase 1 download fix, 2026-10-21): the startup re-validation of the completed
     * (offline) library — the complement of [wipeStuckDownloadsOnStart], which only
     * clears the stuck (non-terminal) records. The OS / OEM storage management
     * (documented on Samsung) can wipe the span files under the download cache
     * directory while the DownloadIndex survives, leaving `STATE_COMPLETED` rows that
     * [isSongDownloaded] then reports forever. Each completed entry is re-checked
     * against the real files with [DownloadValidator.hasValidDownload]; the stale
     * ones are removed so the download badge is honest again and the track can be
     * re-downloaded.
     *
     * Called (on [NzikDispatchers.DATA]) from [ensureDownloadManagerInitialized] right
     * after [getDownloads] has populated [downloads] — once per process.
     *
     * The run is persisted on EVERY branch (even 0 removed → "Nothing to clean") through
     * [MaintenanceStateStore], guarded so a failed write never breaks the cleanup — and
     * so is the S2 startup clean state ([saveSelfHealCleanRun]), keeping all five
     * maintenance rows on the same "date · status" shape; the stale ids are also purged
     * from [downloads] immediately so the live Downloads row is clean from startup
     * (idempotent with [syncDownloads]' STATE_REMOVING removal).
     */
    internal fun revalidateCompletedDownloads(context: Context, downloadManager: DownloadManager) {
        val stale = downloads.value.values
            .filter { it.state == Download.STATE_COMPLETED }
            .filterNot { DownloadValidator.hasValidDownload(downloadCache, it.request.id) }
        val staleIds = stale.map { it.request.id }
        if (staleIds.isEmpty()) {
            Timber.tag("MyDownloadHelper").d("Download re-validation: completed library intact")
            runCatching {
                MaintenanceStateStore.saveDownloadRevalidation(
                    context,
                    MaintenanceDownloadRevalidationState(System.currentTimeMillis(), 0, emptyList()),
                )
            }.onFailure { Timber.tag("MyDownloadHelper").w(it, "Failed to save the download re-validation state") }
            // The S2 row gets its dated startup state too — all five rows share the
            // "date · status" shape, a real self-heal overwrites the clean state
            saveSelfHealCleanRun(context)
            return
        }
        // Built BEFORE the removal, from the live map (request.data = "artist – title");
        // a blank title falls back to the mediaId
        val removedTracks = stale.map { download ->
            DownloadRevalidationRecord(
                mediaId = download.request.id,
                title = download.request.data.decodeToString().takeIf { it.isNotBlank() } ?: download.request.id,
            )
        }
        Timber.tag("MyDownloadHelper").w(
            "Download re-validation: removing ${staleIds.size} stale completed entr${if (staleIds.size == 1) "y" else "ies"}: $staleIds"
        )
        downloads.update { map -> map.filterKeys { it !in staleIds } }
        staleIds.forEach { id -> downloadManager.removeDownload(id) }
        runCatching {
            MaintenanceStateStore.saveDownloadRevalidation(
                context,
                MaintenanceDownloadRevalidationState(System.currentTimeMillis(), staleIds.size, removedTracks),
            )
        }.onFailure { Timber.tag("MyDownloadHelper").w(it, "Failed to save the download re-validation state") }
        // The dated S2 startup state on a removing run too (nothing to heal at startup)
        saveSelfHealCleanRun(context)
        // The per-track S2 toast is unreachable here — the index entries are already gone, so
        // the startup cleanup is the ONLY user-visible notification of the wipe. This runs a
//
        // few seconds after process start, before the app window is visible — the system
        // silently drops toasts shown by a not-yet-visible app (device-verified 2026-10-22:
//
        // toast.show() called, no toast window ever added) — so the count is parked here and
        // MainActivity shows the toast once the window has focus.
        pendingStaleStartupCount.value = staleIds.size
    }

    /** Records the S2 startup clean run (empty mediaId = "nothing to heal") so the "Download self-heal" row carries a date like the other boot-pass rows — a real pre-playback self-heal overwrites it with the healed track. */
    private fun saveSelfHealCleanRun(context: Context) {
        runCatching {
            MaintenanceStateStore.saveDownloadSelfHeal(
                context,
                MaintenanceDownloadSelfHealState(System.currentTimeMillis(), "", ""),
            )
        }.onFailure { Timber.tag("MyDownloadHelper").w(it, "Failed to save the download self-heal state") }
    }

    /** Download-manager event sink — internal (not private) as the test seam MyDownloadHelperSessionDownloadsTest drives, so the session-row invariant is covered without a live [DownloadManager]. */
    @Synchronized
    internal fun syncDownloads(
        download: Download,
        owner: ProfileDownloadCommands? = null,
        removed: Boolean = false,
    ) {
        downloads.update { map ->
            map.toMutableMap().apply {
                if (removed || download.state == Download.STATE_REMOVING) {
                    remove(download.request.id)
                } else {
                    set(download.request.id, download)
                }
            }
        }
        if (owner != null) {
            val key = owner to download.request.id
            val token = downloadAddToken(download.request.data)
            if (removed) {
                if (pendingDownloadRemovals[key]?.previousAdd == token) pendingDownloadRemovals.remove(key)
                if (token != null) pendingDownloadAdds.remove(key, token)
            } else if (download.state != Download.STATE_REMOVING && token != null &&
                pendingDownloadAdds[key] == token
            ) {
                pendingDownloadAdds.remove(key)
                if (pendingDownloadRemovals[key]?.previousAdd != token) pendingDownloadRemovals.remove(key)
            }
        }
        // Same event-driven update for the session row (never seeded from the index, so
        // the Downloads row resets to empty on every process start)
        sessionDownloads.update { map ->
            map.toMutableMap().apply {
                if (removed || download.state == Download.STATE_REMOVING) {
                    remove(download.request.id)
                } else {
                    set(download.request.id, download)
                }
            }
        }
        if (download.state == Download.STATE_COMPLETED || download.state == Download.STATE_FAILED) {
            mutableProgresses.update { it.toMutableMap().apply { remove(download.request.id) } }
        }
    }

    /** The Media3 index database of the ACTIVE profile's downloads (spec-profile-data-separation): the base `exoplayer_internal.db` when the downloads are shared (or the profile is the base), the suffixed `exoplayer_internal_<id>.db` when they are separate — same resolution as [downloadCacheLocation]. */
    private fun downloadIndexDbName(context: Context): String = downloadCacheLocation(context).indexDbName

    @Synchronized
    private fun getDatabaseProvider(context: Context, dbName: String): DatabaseProvider {
        if (!MyDownloadHelper::databaseProvider.isInitialized) databaseProvider =
            cacheDatabaseProvider(context, dbName)
        return databaseProvider
    }

    fun addDownload(context: Context, mediaItem: MediaItem) {
        addDownload(context, mediaItem, beforeEnqueue = null)
    }

    /** Runs optional preparation after admission checks and before queuing the download. */
    internal fun addDownload(context: Context, mediaItem: MediaItem, beforeEnqueue: (() -> Unit)?) {
        val owner = ProfileDownloadCommands.capture(context) ?: return
        enqueueDownload(context, mediaItem, owner, skipExisting = false, beforeEnqueue = beforeEnqueue)
    }

    private fun enqueueDownload(
        context: Context,
        mediaItem: MediaItem,
        owner: ProfileDownloadCommands,
        skipExisting: Boolean,
        beforeEnqueue: (() -> Unit)? = null,
    ) {
        if (mediaItem.isLocal || owner != ProfileDownloadCommands.capture(context)) return

        if( !isNetworkConnected( context ) ) {
            Toaster.noInternet()
            return
        }

        val parentalControlEnabled = context.preferences.getBoolean(parentalControlEnabledKey, false)
        if (parentalControlEnabled) {
            val title = mediaItem.mediaMetadata.title?.toString().orEmpty()
            if (mediaItem.isExplicit || title.hasExplicitPrefix() || title.startsWith("\uD83C\uDD74 ")) {
                Toaster.w(R.string.parental_control_is_enabled)
                return
            }
        }

        beforeEnqueue?.invoke()
        Database.asyncTransaction {
            insertIgnore( mediaItem, autoFix = false )
        }

        // Join the track's queue before dispatching to DATA so a following removal cannot overtake.
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            addDownloadInternal(context, mediaItem, owner, skipExisting)
        }
    }

    /** Runs commands for the same profile and track in arrival order, with independent tracks using separate locks. */
    private suspend fun <T> withDownloadCommand(
        owner: ProfileDownloadCommands,
        mediaId: String,
        command: suspend () -> T,
    ): T {
        val key = owner to mediaId
        val lock = synchronized(downloadCommandLocks) {
            downloadCommandLocks.getOrPut(key, ::DownloadCommandLock).also { it.users++ }
        }
        try {
            return lock.mutex.withLock {
                withContext(NzikDispatchers.DATA) {
                    // The public entry points enter the queue undispatched from their callers.
                    yield()
                    command()
                }
            }
        } finally {
            synchronized(downloadCommandLocks) {
                if (--lock.users == 0) downloadCommandLocks.remove(key)
            }
        }
    }

    /** Preloads lyrics and artwork after the add command leaves the queue. */
    internal suspend fun addDownloadInternal(
        context: Context,
        mediaItem: MediaItem,
        owner: ProfileDownloadCommands? = ProfileDownloadCommands.capture(context),
        skipExisting: Boolean = false,
    ) {
        if (owner == null) return
        val scheduled = withDownloadCommand(owner, mediaItem.mediaId) {
            prepareAndScheduleDownload(context, mediaItem, owner, skipExisting)
        }
        if (!skipExisting || scheduled) preloadDownloadMetadata(context, mediaItem, owner)
    }

    @Synchronized
    private fun isDownloadAlreadyRequested(owner: ProfileDownloadCommands, mediaId: String): Boolean {
        val key = owner to mediaId
        // Media3 may still show the previous request while a removal is queued.
        val pendingRemoval = pendingDownloadRemovals[key]
        val pendingAdd = pendingDownloadAdds[key]
        return (pendingAdd != null && pendingAdd != pendingRemoval?.previousAdd) ||
            (pendingRemoval == null && !canAutoDownloadInState(downloads.value[mediaId]?.state))
    }

    @Synchronized
    private fun reserveDownloadAdd(
        owner: ProfileDownloadCommands,
        mediaId: String,
        token: String,
        skipExisting: Boolean,
    ): Boolean {
        if (skipExisting && isDownloadAlreadyRequested(owner, mediaId)) return false
        pendingDownloadAdds[owner to mediaId] = token
        return true
    }

    @Synchronized
    internal fun releaseDownloadAdd(request: DownloadRequest) {
        val token = downloadAddToken(request.data) ?: return
        pendingDownloadAdds.entries.removeAll { it.key.second == request.id && it.value == token }
    }

    private suspend fun prepareAndScheduleDownload(
        context: Context,
        mediaItem: MediaItem,
        owner: ProfileDownloadCommands,
        skipExisting: Boolean = false,
    ): Boolean = downloadPreparationSemaphore.withPermit {
        if (owner != ProfileDownloadCommands.capture(context)) return@withPermit false
        if (skipExisting && isDownloadAlreadyRequested(owner, mediaItem.mediaId)) return@withPermit false
        val artistTextRaw = mediaItem.artistTextOrDb()
        if (owner != ProfileDownloadCommands.capture(context)) return@withPermit false
        val artistText = if (artistTextRaw == "null" || artistTextRaw.isBlank()) context.getString(R.string.metadata_unknown_artist) else artistTextRaw
        val titleTextRaw = cleanPrefix(mediaItem.mediaMetadata.title?.toString().orEmpty())
        val titleText = if (titleTextRaw == "null" || titleTextRaw.isBlank()) context.getString(R.string.metadata_unknown_title) else titleTextRaw
        val notificationTitle = "$artistText - $titleText"

        val token = UUID.randomUUID().toString()
        val downloadRequest = DownloadRequest
            .Builder(
                /* id      = */ mediaItem.mediaId,
                /* uri     = */ mediaItem.requestMetadata.mediaUri
                    ?: Uri.parse(ExternalUris.youtubeMusic(mediaItem.mediaId))
            )
            .setCustomCacheKey(mediaItem.mediaId)
            .setData(encodeDownloadDisplayName(notificationTitle, token))
            .build()

        if (!reserveDownloadAdd(owner, mediaItem.mediaId, token, skipExisting)) return@withPermit false
        var accepted = false
        try {
            val error = scheduleDownloadRequest(owner, context, downloadRequest).exceptionOrNull()
            if (error is CancellationException) throw error
            if (error != null) {
                Timber.tag("MyDownloadHelper").e(error, "Unable to schedule download")
                Toaster.e(R.string.downloads_failed)
                return@withPermit false
            }
            accepted = true
            true
        } finally {
            if (!accepted) releaseDownloadAdd(downloadRequest)
        }
    }

    private suspend fun preloadDownloadMetadata(
        context: Context,
        mediaItem: MediaItem,
        owner: ProfileDownloadCommands,
    ) = withContext(NzikDispatchers.DATA) {
        if (owner != ProfileDownloadCommands.capture(context)) return@withContext
        downloadSyncedLyrics(mediaItem.asSong)
        if (owner != ProfileDownloadCommands.capture(context)) return@withContext
        ImageCacheFactory.preloadImage(mediaItem.mediaMetadata.artworkUri.toString())
    }

    fun removeDownload(context: Context, mediaItem: MediaItem) {
        if (mediaItem.isLocal) return
        val owner = ProfileDownloadCommands.capture(context) ?: return
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withDownloadCommand(owner, mediaItem.mediaId) {
                removeDownloadInternal(context, mediaItem.mediaId, owner)
            }
        }
    }

    /**
     * Sends the removal intent to [MyDownloadService].
     */
    @Synchronized
    private fun removeDownloadInternal(context: Context, mediaId: String, owner: ProfileDownloadCommands) {
        val key = owner to mediaId
        val pendingAdd = pendingDownloadAdds[key]
        val indexedDownload = downloads.value[mediaId]
        val removal = if (pendingAdd != null || indexedDownload != null) {
            PendingRemoval(UUID.randomUUID().toString(), pendingAdd ?: indexedDownload?.request?.data?.let(::downloadAddToken))
        } else null
        val error = owner.sendRemove(context, mediaId, removal?.token).exceptionOrNull()
        if (error is CancellationException) throw error
        if (error != null) {
            Timber.tag("MyDownloadHelper").e(error, "Unable to schedule download removal")
            return
        }

        if (removal != null) pendingDownloadRemovals[key] = removal
    }

    @Synchronized
    internal fun releaseDownloadRemoval(mediaId: String, token: String) {
        pendingDownloadRemovals.entries.removeAll { it.key.second == mediaId && it.value.token == token }
    }

    /** Refreshes [downloadQualityFormat] whenever the download quality setting changes so downloads scheduled after the change use the new quality immediately. */
    private fun registerDownloadQualityPreferenceListener(context: Context) {
        if (downloadQualityPreferenceListener != null) return
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == downloadQualityFormatKey) {
                downloadQualityFormat =
                    context.preferences.getEnum(downloadQualityFormatKey, DownloadQualityFormat.Auto)
                // URLs cached at the previous download quality are not reusable at the new one
                songUrlCache.clear()
                Timber.tag("MyDownloadHelper").d("Download quality changed to $downloadQualityFormat")
            }
        }
        context.preferences.registerOnSharedPreferenceChangeListener(listener)
        downloadQualityPreferenceListener = listener
    }

    /**
     * @return the ids of completed downloads that already have a [Format] row, i.e. the ones
     * whose download quality is tracked.
     */
    private suspend fun trackedCompletedSongIds(): List<String> =
        withContext( NzikDispatchers.DATA ) {
            val completedIds = downloads.value.values
                .filter { it.state == Download.STATE_COMPLETED }
                .map { it.request.id }
            if ( completedIds.isEmpty() ) return@withContext emptyList()

            Database.formatTable.findSongIdsWithFormat( completedIds )
        }

    /**
     * @return the ids of completed downloads whose recorded download quality (see
     * [app.n_zik.android.models.Format.downloadQuality]) does not match the current
     * [downloadQualityFormat]. A NULL tracking value (download made before quality tracking
     * existed) counts as non-compliant.
     */
    private suspend fun nonCompliantDownloadedSongIds(): List<String> =
        withContext( NzikDispatchers.DATA ) {
            val trackedIds = trackedCompletedSongIds()
            if ( trackedIds.isEmpty() ) return@withContext emptyList()

            Database.formatTable.findNonCompliantSongIds( trackedIds, downloadQualityFormat.name )
        }

    /**
     * @return the number of completed downloads that [updateDownloads] would re-download.
     */
    suspend fun countNonCompliantDownloads(): Int =
        withContext( NzikDispatchers.DATA ) {
            val trackedIds = trackedCompletedSongIds()
            if ( trackedIds.isEmpty() ) return@withContext 0

            Database.formatTable.countNonCompliantDownloaded( trackedIds, downloadQualityFormat.name )
        }

    /**
     * Re-downloads every completed download that does not match [downloadQualityFormat].
     *
     * Each target is removed then re-added sequentially in the same coroutine: the remove
     * intent must reach [MyDownloadService] before the add intent for the same id, because
     * media3 rejects an add targeting a download that is not in
     * [Download.STATE_REMOVING] or [Download.STATE_FAILED]. Batch counters drive the
     * existing progress notification, and the download path records the new quality on
     * each re-downloaded song.
     */
    suspend fun updateDownloads(context: Context) {
        val owner = ProfileDownloadCommands.capture(context) ?: return
        val targets = nonCompliantDownloadedSongIds()
        if ( targets.isEmpty() ) return

        Timber.tag("MyDownloadHelper").i("updateDownloads: re-downloading ${targets.size} downloads with $downloadQualityFormat")
        startBatchDownload( targets.size )

        targets.forEach { songId ->
            val mediaItem = withDownloadCommand(owner, songId) {
                if (owner != ProfileDownloadCommands.capture(context)) return@withDownloadCommand null
                // A stream URL cached under the previous quality must not be reused by the re-download.
                songUrlCache.invalidate(songId)
                val item = Database.songTable.findByIdDirect(songId)?.asMediaItem
                    ?: MediaItem.Builder()
                        .setMediaId(songId)
                        .setUri(Uri.parse(ExternalUris.youtubeMusic(songId)))
                        .build()

                removeDownloadInternal(context, songId, owner)
                prepareAndScheduleDownload(context, item, owner)
                item
            } ?: return
            preloadDownloadMetadata(context, mediaItem, owner)
        }
    }

    fun resumeDownloads(context: Context) {
        val target = ProfileDownloadCommands.capture(context) ?: return
        context.startService(target.intent(context, DownloadService.ACTION_RESUME_DOWNLOADS))
    }

    /** Enqueues a newly liked title using the current profile's download settings. */
    internal fun downloadNewlyLiked(context: Context, mediaItem: MediaItem) {
        val owner = favoriteDownloadOwner(context, autoDownloadSongWhenLikedKey) ?: return
        enqueueDownload(context, mediaItem, owner, skipExisting = true)
    }

    /** Removes a title that leaves favorites when like-based downloads are enabled. */
    internal fun removeNewlyUnliked(context: Context, mediaItem: MediaItem) {
        if (favoriteDownloadOwner(context, autoDownloadSongWhenLikedKey) != null) {
            removeDownload(context, mediaItem)
        }
    }

    /** Uses cached tracks for a known album and fetches uncached online albums on demand. */
    internal fun downloadBookmarkedAlbum(context: Context, albumId: String, previousAlbumFetch: Long?) {
        if (isDeviceAlbumId(albumId)) return
        val owner = favoriteDownloadOwner(context, autoDownloadSongWhenAlbumBookmarkedKey) ?: return
        coroutineScope.launch {
            if (owner != ProfileDownloadCommands.capture(context)) return@launch
            val cached = Database.songAlbumMapTable.allSongsOfDirect(albumId).map { it.asMediaItem }
            val hasFullCache = cached.isNotEmpty() && previousAlbumFetch != null
            val mediaItems = if (hasFullCache) cached else {
                YtMusic.getAlbum(albumId.removePrefix(MODIFIED_PREFIX)).onFailure { error ->
                    if (error is CancellationException) throw error
                    Timber.tag("MyDownloadHelper").w(error, "Unable to load tracks for bookmarked album")
                }.getOrNull()?.songs?.map { it.asMediaItem } ?: cached
            }
            mediaItems.distinctBy(MediaItem::mediaId).forEach {
                enqueueDownload(context, it, owner, skipExisting = true)
            }
        }
    }

    /** Downloads the saved membership or loads the full remote source for a new music playlist. */
    internal suspend fun downloadBookmarkedPlaylist(
        context: Context,
        browseId: String?,
        playlist: Playlist? = null,
        completeTracks: List<MediaItem>? = null,
    ) {
        if (browseId == null || playlistLinkKind(browseId) != PlaylistLinkKind.YouTubePlaylist) return
        if (browseId.removePrefix("VL") in listOf("LM", "SE")) return
        val owner = favoriteDownloadOwner(context, autoDownloadSongWhenPlaylistBookmarkedKey) ?: return
        if (owner != ProfileDownloadCommands.capture(context)) return
        val saved = playlist?.let { Database.songPlaylistMapTable.songsByPositionDirect(it.id) }
            .orEmpty().map { it.asMediaItem }
        val mediaItems = completeTracks ?: if (playlist?.isEditable == true) saved else {
            val remote = loadPlaylistSource(browseId)
            if (owner != ProfileDownloadCommands.capture(context)) return
            chooseBookmarkedPlaylistTracks(remote, saved) {
                Timber.tag("MyDownloadHelper").w("Unable to load full tracks for bookmarked playlist")
                Toaster.e(R.string.downloads_failed)
            }
        }
        mediaItems.distinctBy(MediaItem::mediaId).forEach {
            enqueueDownload(context, it, owner, skipExisting = true)
        }
    }

    private fun favoriteDownloadOwner(context: Context, categoryKey: String): ProfileDownloadCommands? {
        if (!isFavoriteAutoDownloadEnabled(context.preferences, categoryKey) || !isNetworkConnected(context)) {
            return null
        }
        return ProfileDownloadCommands.capture(context)
    }

    fun handleDownload(context: Context, song: Song, removeIfDownloaded: Boolean = false ) {
        if( song.isLocal ) return

        val download = downloads.value.values.firstOrNull { it.request.id == song.id }
        val isDownloaded = download?.state == Download.STATE_COMPLETED
        val isDownloading = download?.state == Download.STATE_DOWNLOADING || 
                           download?.state == Download.STATE_QUEUED ||
                           download?.state == Download.STATE_RESTARTING

        when {
            // If currently downloading/queued → stop and remove
            isDownloading -> {
                removeDownload( context, song.asMediaItem )
            }
            // If completed and removeIfDownloaded → remove
            isDownloaded && removeIfDownloaded -> {
                removeDownload( context, song.asMediaItem )
            }
            // If not downloaded and not downloading → start download
            !isDownloaded && !isDownloading -> {
                addDownload( context, song.asMediaItem )
            }
        }
    }

}

/**
 * Off-Main initialization of the shared download manager (issue #606 N1).
 *
 * Callers must dispatch this on [NzikDispatchers.DATA] — it performs disk I/O (temp dir, cache,
 * database). Initialization failures are logged, never propagated: the composition must not
 * crash over a failed one-time init, and the `downloads` map simply stays unpopulated until a
 * later init attempt succeeds.
 */
internal suspend fun initDownloadManagerOffMain(context: Context) {
    runCatching {
        MyDownloadHelper.getDownloadManager(context)
    }.onFailure { e ->
        Timber.tag("MyDownloadHelper").e(e, "Download manager init failed")
    }
}




internal fun isFavoriteAutoDownloadEnabled(preferences: SharedPreferences, categoryKey: String): Boolean =
    preferences.getBoolean(autoDownloadSongKey, false) && preferences.getBoolean(categoryKey, false)

internal fun canAutoDownloadInState(state: Int?): Boolean =
    state == null || state == Download.STATE_FAILED

@UnstableApi
internal fun Map<String, Download>.completedDownloadRequestIds(): Set<String> = values.asSequence()
    .filter { it.state == Download.STATE_COMPLETED }
    .mapTo(HashSet()) { it.request.id }

internal fun chooseBookmarkedPlaylistTracks(
    remote: List<MediaItem>?,
    saved: List<MediaItem>,
    onIncomplete: () -> Unit,
): List<MediaItem> {
    if (remote != null) return remote
    onIncomplete()
    return saved.filterNot { it.isLocal }
}
