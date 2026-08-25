package app.n_zik.android.download.utils

import app.n_zik.android.core.database.*

import app.n_zik.android.download.services.*
import app.n_zik.android.download.utils.*

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import app.n_zik.android.utils.artistTextOrDb
import app.n_zik.android.playback.services.createDataSourceFactory
import app.n_zik.android.core.database.Database

import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.isLocal
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import app.it.fast4x.rimusic.utils.autoDownloadSongKey
import app.it.fast4x.rimusic.utils.autoDownloadSongWhenAlbumBookmarkedKey
import app.it.fast4x.rimusic.utils.autoDownloadSongWhenLikedKey
import app.it.fast4x.rimusic.utils.download
import app.it.fast4x.rimusic.utils.downloadSyncedLyrics
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.removeDownload
import app.n_zik.android.core.coil.thumbnail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import app.it.fast4x.rimusic.utils.ExternalUris
import app.n_zik.android.core.coil.ImageCacheFactory

import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.n_zik.android.R
import kotlinx.coroutines.cancel
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import java.util.Collections

@UnstableApi
object MyDownloadHelper {
    private val executor = Executors.newCachedThreadPool()
    private val coroutineScope = CoroutineScope(
        executor.asCoroutineDispatcher() +
                SupervisorJob() +
                CoroutineName("MyDownloadService-Executor-Scope")
    )

    // While the class is not a singleton (lifecycle), there should only be one download state at a time
//    private val mutableDownloadState = MutableStateFlow(false)
//    val downloadState = mutableDownloadState.asStateFlow()
//    private val downloadQueue =
//        Channel<DownloadManager>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    const val CACHE_DIRNAME = "exo_downloads"

    private lateinit var databaseProvider: DatabaseProvider
    lateinit var downloadCache: Cache

    private lateinit var downloadNotificationHelper: DownloadNotificationHelper
    private lateinit var downloadManager: DownloadManager
    lateinit var audioQualityFormat: AudioQualityFormat

    // URL cache with LRU eviction (max 500 entries): videoId -> (url, expiryTimestamp)
    internal val songUrlCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, Pair<String, Long>>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, Long>>): Boolean {
                return size > 500
            }
        }
    )


    var downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    private val mutableProgresses = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progresses = mutableProgresses.asStateFlow()

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
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            result[cursor.download.request.id] = cursor.download
        }
        downloads.value = result

    }


    @Synchronized
    fun getDownloadNotificationHelper(context: Context?): DownloadNotificationHelper {
        if (!MyDownloadHelper::downloadNotificationHelper.isInitialized) {
            downloadNotificationHelper =
                DownloadNotificationHelper(context!!, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        }
        return downloadNotificationHelper
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        ensureDownloadManagerInitialized(context)
        return downloadManager
    }

    @Synchronized
    private fun initDownloadCache( context: Context ): SimpleCache {
        val cacheSize = context.preferences.getEnum( exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB` )

        val cacheEvictor = when( cacheSize ) {
            ExoPlayerDiskCacheMaxSize.Unlimited -> NoOpCacheEvictor()

            ExoPlayerDiskCacheMaxSize.Custom    -> {
                val customCacheSize = context.preferences.getInt( exoPlayerCustomCacheKey, 32 ) * 1000 * 1000L
                LeastRecentlyUsedCacheEvictor( customCacheSize )
            }

            else                                -> LeastRecentlyUsedCacheEvictor( cacheSize.bytes )
        }

        val cacheDir = when( cacheSize ) {
            // Temporary directory deletes itself after close
            // It means songs remain on device as long as it's open
            ExoPlayerDiskCacheMaxSize.Disabled -> createTempDirectory( CACHE_DIRNAME ).toFile()

            else                               ->
                // Looks a bit ugly but what it does is
                // check location set by user and return
                // appropriate path with [CACHE_DIRNAME] appended.
                when( context.preferences.getEnum( exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System ) ) {
                    ExoPlayerCacheLocation.System -> context.cacheDir
                    ExoPlayerCacheLocation.Private -> context.filesDir
                }.resolve( CACHE_DIRNAME )
        }

        // Ensure this location exists
        cacheDir.mkdirs()

        return SimpleCache( cacheDir, cacheEvictor, getDatabaseProvider(context) )
    }

    @Synchronized
    fun getDownloadCache( context: Context ): Cache {
        if ( !MyDownloadHelper::downloadCache.isInitialized )
            downloadCache = initDownloadCache( context )

        return downloadCache
    }

    @Synchronized
    private fun ensureDownloadManagerInitialized(context: Context) {
        audioQualityFormat =
            context.preferences.getEnum(audioQualityFormatKey, AudioQualityFormat.Auto)

        if (!MyDownloadHelper::downloadManager.isInitialized) {
            downloadManager = DownloadManager(
                context,
                getDatabaseProvider(context),
                getDownloadCache(context),
                createDataSourceFactory(),
                executor
            ).apply {
                maxParallelDownloads = 3
                minRetryCount = 2
                requirements = Requirements(Requirements.NETWORK)

                addListener(
                    object : DownloadManager.Listener {

                        override fun onDownloadChanged(
                            downloadManager: DownloadManager,
                            download: Download,
                            finalException: Exception?
                        ) = run {
                            syncDownloads(download)
                        }

                        override fun onDownloadRemoved(
                            downloadManager: DownloadManager,
                            download: Download
                        ) = run {
                            syncDownloads(download)
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
                    // but a 2s delay when idle is much better than 1s constantly.
                    val delayTime = if (currentDownloads.isNotEmpty()) 1000L else 5000L
                    delay(delayTime)
                }
            }
            getDownloads()
        }
    }

    @Synchronized
    private fun syncDownloads(download: Download) {
        downloads.update { map ->
            map.toMutableMap().apply {
                if (download.state == Download.STATE_REMOVING) {
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

    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        if (!MyDownloadHelper::databaseProvider.isInitialized) databaseProvider =
            StandaloneDatabaseProvider(context)
        return databaseProvider
    }

    fun addDownload(context: Context, mediaItem: MediaItem) {
        if (mediaItem.isLocal) return

        if( !isNetworkConnected( context ) ) {
            Toaster.noInternet()
            return
        }

        val parentalControlEnabled = context.preferences.getBoolean(parentalControlEnabledKey, false)
        if (parentalControlEnabled) {
            val isExplicit = mediaItem.mediaMetadata.title?.startsWith(EXPLICIT_PREFIX, true) == true
            if (isExplicit) {
                Toaster.w(R.string.parental_control_is_enabled)
                return
            }
        }

        Database.asyncTransaction {
            insertIgnore( mediaItem, autoFix = false )
        }

        coroutineScope.launch {
            val artistTextRaw = mediaItem.artistTextOrDb()
            val artistText = if (artistTextRaw == "null" || artistTextRaw.isBlank()) context.getString(R.string.unknown_artist) else artistTextRaw
            val titleTextRaw = mediaItem.mediaMetadata.title?.toString() ?: ""
            val titleText = if (titleTextRaw == "null" || titleTextRaw.isBlank()) context.getString(R.string.unknown_title) else titleTextRaw
            val notificationTitle = "$artistText - $titleText"

            val downloadRequest = DownloadRequest
                .Builder(
                    /* id      = */ mediaItem.mediaId,
                    /* uri     = */ mediaItem.requestMetadata.mediaUri
                        ?: Uri.parse(ExternalUris.youtubeMusic(mediaItem.mediaId))
                )
                .setCustomCacheKey(mediaItem.mediaId)
                .setData(notificationTitle.encodeToByteArray()) // Title in notification
                .build()

            val imageUrl = mediaItem.mediaMetadata.artworkUri.thumbnail(1000)

            context.download<MyDownloadService>(downloadRequest).exceptionOrNull()?.let {
                if (it is CancellationException) throw it

                Timber.tag("MyDownloadHelper").e("scheduleDownload exception ${it.stackTraceToString()}")
                Timber.tag("MyDownloadHelper").e("scheduleDownload exception ${it.stackTraceToString()}")
                Toaster.e(R.string.error_playback_failed)
            }
            downloadSyncedLyrics( mediaItem.asSong )
            ImageCacheFactory.preloadImage(mediaItem.mediaMetadata.artworkUri.toString())
        }


    }

    fun removeDownload(context: Context, mediaItem: MediaItem) {
        if (mediaItem.isLocal) return
        coroutineScope.launch {
            context.removeDownload<MyDownloadService>(mediaItem.mediaId).exceptionOrNull()?.let {
                if (it is CancellationException) throw it

                Timber.tag("MyDownloadHelper").e(it.stackTraceToString())
                Timber.tag("MyDownloadHelper").e("removeDownload exception ${it.stackTraceToString()}")
            }
        }
    }

    fun resumeDownloads(context: Context) {
        DownloadService.sendResumeDownloads(
            context,
            MyDownloadService::class.java,
            false
        )
    }

    fun autoDownload(context: Context, mediaItem: MediaItem) {
        if (context.preferences.getBoolean(autoDownloadSongKey, false)) {
            if (downloads.value[mediaItem.mediaId]?.state != Download.STATE_COMPLETED)
                addDownload(context, mediaItem)
        }
    }

    fun autoDownloadWhenLiked(context: Context, mediaItem: MediaItem) {
        if (context.preferences.getBoolean(autoDownloadSongWhenLikedKey, false)) {
            Database.asyncQuery {
                if( songTable.isLikedDirect( mediaItem.mediaId ) )
                    autoDownload(context, mediaItem)
                else
                    removeDownload(context, mediaItem)
            }
        }
    }

    fun downloadOnLike( mediaItem: MediaItem, likeState: Boolean?, context: Context ) {
        // Only continues when this setting is enabled
        val isSettingEnabled = context.preferences.getBoolean( autoDownloadSongWhenLikedKey, false )
        if( !isSettingEnabled || !isNetworkConnected( context ) )
            return

        // [likeState] is a tri-state value,
        // only `true` represents like, so
        // `true` must be value set to download
        if( likeState == true )
            autoDownload( context, mediaItem )
        else
            removeDownload( context, mediaItem )
    }

    fun autoDownloadWhenAlbumBookmarked(context: Context, mediaItems: List<MediaItem>) {
        if (context.preferences.getBoolean(autoDownloadSongWhenAlbumBookmarkedKey, false)) {
            mediaItems.forEach { mediaItem ->
                autoDownload(context, mediaItem)
            }
        }
    }

    fun handleDownload(context: Context, song: Song, removeIfDownloaded: Boolean = false ) {
        if( song.isLocal ) return

        val isDownloaded =
            downloads.value.values.any{ it.state == Download.STATE_COMPLETED && it.request.id == song.id }

        if( isDownloaded && removeIfDownloaded )
            removeDownload( context, song.asMediaItem )
        else if( !isDownloaded )
            addDownload( context, song.asMediaItem )
    }

    /**
     * Cancel the coroutine scope and shut down the executor.
     * Call this when the download system is being torn down.
     */
    fun release() {
        coroutineScope.cancel()
        executor.shutdown()
    }
}



