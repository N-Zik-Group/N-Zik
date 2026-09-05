package app.n_zik.android.components.ui.screens.home

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import timber.log.Timber
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.utils.from
import app.kreate.android.me.knighthat.utils.PropUtils
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.SongAlbumMap
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.asMediaItem
import kotlinx.coroutines.flow.first
import app.kreate.android.me.knighthat.utils.Toaster
import androidx.core.app.NotificationCompat
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import java.net.UnknownHostException
import java.net.ConnectException

class HomeSyncService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeSyncs = 0

    companion object {
        const val ACTION_SYNC_ARTISTS = "app.n_zik.android.action.SYNC_ARTISTS"
        const val ACTION_SYNC_ALBUMS = "app.n_zik.android.action.SYNC_ALBUMS"
        const val ACTION_SYNC_PLAYLISTS = "app.n_zik.android.action.SYNC_PLAYLISTS"
        const val EXTRA_IDS = "EXTRA_IDS"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        
        val notificationId = when (action) {
            ACTION_SYNC_ARTISTS -> 1001
            ACTION_SYNC_ALBUMS -> 1002
            ACTION_SYNC_PLAYLISTS -> 1003
            else -> 1001
        }
        
        HomeSyncState.showSyncNotification(
            title = appContext().getString(R.string.sync_notifications),
            message = "Starting sync...",
            notificationId = notificationId,
            isOngoing = true
        )
        
        val builder = NotificationCompat.Builder(appContext(), "sync_channel_id")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(appContext().getString(R.string.sync_notifications))
            .setContentText("Syncing in background...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
        
        try {
            ServiceCompat.startForeground(
                this,
                notificationId,
                builder.build(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to start foreground service")
        }

        val ids = intent.getStringArrayListExtra(EXTRA_IDS)

        activeSyncs++
        Timber.tag("HomeSyncService").d("Active syncs: $activeSyncs (started $action)")

        serviceScope.launch {
            val resultNotificationId = notificationId + 100
            try {
                when (action) {
                    ACTION_SYNC_ARTISTS -> syncArtists(ids, notificationId, resultNotificationId)
                    ACTION_SYNC_ALBUMS -> syncAlbums(ids, notificationId, resultNotificationId)
                    ACTION_SYNC_PLAYLISTS -> syncPlaylists(ids, notificationId, resultNotificationId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Sync failed with exception")
            } finally {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            activeSyncs--
            Timber.tag("HomeSyncService").d("Active syncs remaining: $activeSyncs")
            if (activeSyncs <= 0) {
                activeSyncs = 0
                stopSelf()
                Timber.tag("HomeSyncService").d("All syncs finished, stopping service")
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ARTISTS SYNC
    // ═══════════════════════════════════════════════════════════════════
    private suspend fun syncArtists(ids: List<String>?, notificationId: Int, resultNotificationId: Int) {
        if (HomeSyncState.isSyncingArtists) return
        HomeSyncState.isSyncingArtists = true
        HomeSyncState.artistSyncProgress = 0f

        val allArtists = (Database.artistTable.allFollowing().first() + Database.artistTable.allInLibrary().first()).distinctBy { it.id }
        val targetItems = if (ids.isNullOrEmpty()) allArtists else allArtists.filter { it.id in ids }

        val ytArtists = targetItems.filter { it.isYoutubeArtist || it.id.startsWith("UC") }
        val localArtists = targetItems.filterNot { it.isYoutubeArtist || it.id.startsWith("UC") }
        val totalArtists = ytArtists.size + localArtists.size

        Timber.tag("HomeSyncService").d("══════ ARTIST SYNC START ══════ Total: $totalArtists (YT: ${ytArtists.size}, Local/Fallback: ${localArtists.size})")

        withContext(Dispatchers.Main) {
            if (totalArtists > 0) Toaster.i(appContext().getString(R.string.refreshing_artists, totalArtists))
            if (ids == null && totalArtists == 0) Toaster.w(appContext().getString(R.string.sync_no_items))
        }
        
        if (totalArtists == 0) {
            HomeSyncState.isSyncingArtists = false
            return
        }

        var failedCount = 0
        val failedList = mutableListOf<Artist>()
        HomeSyncState.artistSyncFailed = 0
        HomeSyncState.artistSyncTotal = totalArtists
        var successCount = 0

        var abortSync = false

        // ── YT Artists: direct fetch by ID ──
        for ((index, artist) in ytArtists.withIndex()) {
            if (abortSync) break
            HomeSyncState.artistSyncCurrentIndex = index + 1
            HomeSyncState.artistSyncCurrentName = artist.name ?: ""
            HomeSyncState.artistSyncProgress = index.toFloat() / ytArtists.size
            HomeSyncState.showSyncNotification(
                title = appContext().getString(R.string.sync_notifications),
                message = appContext().getString(R.string.sync_progress_artists, index + 1, totalArtists),
                notificationId = notificationId,
                isOngoing = true,
                maxProgress = totalArtists,
                currentProgress = index + 1
            )
            kotlinx.coroutines.delay((2000L..5000L).random())

            Timber.tag("HomeSyncService").d("[ARTIST|YT_DIRECT] #${index+1}/${ytArtists.size} id='${artist.id}' name='${artist.name}'")

            var status = 0
            for (attempt in 1..3) {
                YtMusic.getArtistPage(artist.id.removePrefix(MODIFIED_PREFIX)).onSuccess { online ->
                    val a = online.artist
                    Timber.tag("HomeSyncService").d("[ARTIST|YT_DIRECT] ✓ name='${a.title}' thumbnail='${a.thumbnail?.url}'")
                    Database.asyncTransaction {
                        Database.artistTable.upsert(Artist(
                            id = artist.id,
                            name = PropUtils.retainIfModified(artist.name, a.title),
                            thumbnailUrl = PropUtils.retainIfModified(artist.thumbnailUrl, a.thumbnail?.url) ?: artist.thumbnailUrl,
                            timestamp = artist.timestamp,
                            bookmarkedAt = artist.bookmarkedAt,
                            isYoutubeArtist = artist.isYoutubeArtist,
                            position = artist.position
                        ))
                    }
                    successCount++
                    status = 1
                }.onFailure {
                    Timber.tag("HomeSyncService").e(it, "[ARTIST|YT_DIRECT] ✗ Failed attempt $attempt: ${artist.name}")
                    if (it is UnknownHostException || it is ConnectException) status = 3
                }
                if (status != 0) break
            }
            if (status == 3) { abortSync = true; break }
            if (status != 1) { failedCount++; HomeSyncState.artistSyncFailed = failedCount; failedList.add(artist) }
        }

        // ── Local Artists: direct fetch or fallback search ──
        for ((index, artist) in localArtists.withIndex()) {
            if (abortSync) break
            HomeSyncState.artistSyncCurrentIndex = ytArtists.size + index + 1
            HomeSyncState.artistSyncCurrentName = artist.name ?: ""
            HomeSyncState.artistSyncProgress = (ytArtists.size + index).toFloat() / totalArtists
            HomeSyncState.showSyncNotification(
                title = appContext().getString(R.string.sync_notifications),
                message = appContext().getString(R.string.sync_progress_artists, ytArtists.size + index + 1, totalArtists),
                notificationId = notificationId,
                isOngoing = true,
                maxProgress = totalArtists,
                currentProgress = ytArtists.size + index + 1
            )
            kotlinx.coroutines.delay((2000L..5000L).random())

            val hasValidId = artist.id.startsWith("UC")
            val method = if (hasValidId) "LOCAL_DIRECT" else "FALLBACK_SEARCH"
            Timber.tag("HomeSyncService").d("[ARTIST|$method] #${ytArtists.size+index+1}/${totalArtists} id='${artist.id}' name='${artist.name}' hasValidId=$hasValidId")

            var status = 0
            for (attempt in 1..3) {
                if (hasValidId) {
                    YtMusic.getArtistPage(artist.id.removePrefix(MODIFIED_PREFIX)).onSuccess { online ->
                        val a = online.artist
                        Timber.tag("HomeSyncService").d("[ARTIST|$method] ✓ name='${a.title}' thumbnail='${a.thumbnail?.url}'")
                        Database.asyncTransaction {
                            Database.artistTable.upsert(Artist(
                                id = artist.id,
                                name = PropUtils.retainIfModified(artist.name, a.title),
                                thumbnailUrl = PropUtils.retainIfModified(artist.thumbnailUrl, a.thumbnail?.url) ?: artist.thumbnailUrl,
                                timestamp = artist.timestamp,
                                bookmarkedAt = artist.bookmarkedAt,
                                isYoutubeArtist = artist.isYoutubeArtist,
                                position = artist.position
                            ))
                        }
                        successCount++
                        status = 1
                    }.onFailure {
                        Timber.tag("HomeSyncService").e(it, "[ARTIST|$method] ✗ Failed attempt $attempt: ${artist.name}")
                        if (it is UnknownHostException || it is ConnectException) status = 3
                    }
                } else {
                    val query = artist.name?.trim()
                    if (query.isNullOrBlank()) { status = 2; break }
                    try {
                        Timber.tag("HomeSyncService").d("[ARTIST|FALLBACK_SEARCH] Searching YouTube: query='$query'")
                        val searchResult = Innertube.searchPage<Innertube.ArtistItem>(
                            query = query, params = Innertube.SearchFilter.Artist.value,
                            fromMusicShelfRendererContent = { Innertube.ArtistItem.from(it) }
                        )?.getOrNull()
                        val remoteId = searchResult?.items?.firstOrNull()?.info?.endpoint?.browseId
                        val onlineName = searchResult?.items?.firstOrNull()?.title
                        if (remoteId != null) {
                            Timber.tag("HomeSyncService").d("[ARTIST|FALLBACK_SEARCH] ✓ Found: remoteId='$remoteId' onlineName='$onlineName'")
                            YtMusic.getArtistPage(remoteId.removePrefix(MODIFIED_PREFIX)).onSuccess { online ->
                                val a = online.artist
                                Timber.tag("HomeSyncService").d("[ARTIST|FALLBACK_SEARCH] ✓ Fetched: name='${a.title}' thumbnail='${a.thumbnail?.url}'")
                                Database.asyncTransaction {
                                    Database.artistTable.upsert(Artist(
                                        id = artist.id,
                                        name = PropUtils.retainIfModified(artist.name, a.title),
                                        thumbnailUrl = PropUtils.retainIfModified(artist.thumbnailUrl, a.thumbnail?.url) ?: artist.thumbnailUrl,
                                        timestamp = artist.timestamp,
                                        bookmarkedAt = artist.bookmarkedAt,
                                        isYoutubeArtist = artist.isYoutubeArtist,
                                        position = artist.position
                                    ))
                                }
                                successCount++
                                status = 1
                            }.onFailure {
                                if (it is UnknownHostException || it is ConnectException) status = 3
                            }
                        } else {
                            Timber.tag("HomeSyncService").d("[ARTIST|FALLBACK_SEARCH] ✗ No match found for '$query'")
                            status = 2
                        }
                    } catch (it: Exception) {
                        Timber.tag("HomeSyncService").e(it, "[ARTIST|FALLBACK_SEARCH] ✗ Search failed attempt $attempt: $query")
                        if (it is UnknownHostException || it is ConnectException) status = 3
                    }
                }
                if (status != 0) break
            }
            if (status == 3) { abortSync = true; break }
            if (status != 1) { failedCount++; HomeSyncState.artistSyncFailed = failedCount; failedList.add(artist) }
        }

        Timber.tag("HomeSyncService").d("══════ ARTIST SYNC END ══════ success=$successCount failed=$failedCount total=$totalArtists")

        withContext(Dispatchers.Main) {
            if (abortSync) {
                Toaster.e(appContext().getString(R.string.sync_failed))
                HomeSyncState.showSyncNotification(appContext().getString(R.string.sync_failed), "Sync aborted due to network error.", resultNotificationId)
            } else if (failedCount > 0) {
                HomeSyncState.failedArtistsList = failedList
                val errorMessage = appContext().getString(R.string.failed_artists, failedCount)
                val notificationMessage = appContext().getString(R.string.sync_failed_notification_artists, failedCount)
                Toaster.e(errorMessage)
                HomeSyncState.showSyncNotification(
                    title = appContext().getString(R.string.sync_completed_with_errors),
                    message = notificationMessage,
                    notificationId = resultNotificationId
                )
            } else {
                Toaster.s(appContext().getString(R.string.found_all_artists))
                HomeSyncState.showSyncNotification(
                    title = appContext().getString(R.string.sync_successful),
                    message = appContext().getString(R.string.sync_success_notification_artists),
                    notificationId = resultNotificationId
                )
            }
            HomeSyncState.isSyncingArtists = false
            HomeSyncState.artistSyncProgress = 1f
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ALBUMS SYNC
    // ═══════════════════════════════════════════════════════════════════
    private suspend fun syncAlbums(ids: List<String>?, notificationId: Int, resultNotificationId: Int) {
        if (HomeSyncState.isSyncingAlbums) return
        HomeSyncState.isSyncingAlbums = true
        HomeSyncState.albumSyncProgress = 0f
        
        val allAlbums = Database.albumTable.all().first()
        val targetItems = if (ids.isNullOrEmpty()) allAlbums else allAlbums.filter { it.id in ids }

        val ytAlbums = targetItems.filter { it.isYoutubeAlbum || (it.id.startsWith("MPRE") || it.id.startsWith("OLAK")) }
        val localAlbums = targetItems.filterNot { it.isYoutubeAlbum || (it.id.startsWith("MPRE") || it.id.startsWith("OLAK")) }
        val totalAlbums = ytAlbums.size + localAlbums.size

        Timber.tag("HomeSyncService").d("══════ ALBUM SYNC START ══════ Total: $totalAlbums (YT: ${ytAlbums.size}, Local/Fallback: ${localAlbums.size})")

        withContext(Dispatchers.Main) {
            if (totalAlbums > 0) Toaster.i(appContext().getString(R.string.refreshing_albums, totalAlbums))
            if (ids == null && totalAlbums == 0) Toaster.w(appContext().getString(R.string.sync_no_items))
        }

        if (totalAlbums == 0) {
            HomeSyncState.isSyncingAlbums = false
            return
        }

        var failedCount = 0
        val failedList = mutableListOf<Album>()
        HomeSyncState.albumSyncFailed = 0
        HomeSyncState.albumSyncTotal = totalAlbums
        var successCount = 0

        var abortSync = false

        // ── YT Albums: direct fetch by ID ──
        for ((index, album) in ytAlbums.withIndex()) {
            if (abortSync) break
            HomeSyncState.albumSyncCurrentIndex = index + 1
            HomeSyncState.albumSyncCurrentName = album.title ?: ""
            HomeSyncState.albumSyncProgress = index.toFloat() / ytAlbums.size
            HomeSyncState.showSyncNotification(
                title = appContext().getString(R.string.sync_notifications),
                message = appContext().getString(R.string.sync_progress_albums, index + 1, totalAlbums),
                notificationId = notificationId,
                isOngoing = true,
                maxProgress = totalAlbums,
                currentProgress = index + 1
            )
            kotlinx.coroutines.delay((2000L..5000L).random())

            Timber.tag("HomeSyncService").d("[ALBUM|YT_DIRECT] #${index+1}/${ytAlbums.size} id='${album.id}' title='${album.title}'")

            var status = 0
            for (attempt in 1..3) {
                YtMusic.getAlbum(album.id.removePrefix(MODIFIED_PREFIX), true).onSuccess { online ->
                    val a = online.album
                    val songCount = online.songs.size
                    Timber.tag("HomeSyncService").d("[ALBUM|YT_DIRECT] ✓ title='${a.title}' year='${a.year}' authors='${a.authors?.joinToString { it.name.orEmpty() }}' thumbnail='${a.thumbnail?.url}' songs=$songCount url='${online.url}'")
                    Database.asyncTransaction {
                        Database.albumTable.upsert(Album(
                            id = album.id,
                            title = PropUtils.retainIfModified(album.title, a.title),
                            year = a.year ?: album.year,
                            authorsText = PropUtils.retainIfModified(album.authorsText, a.authors?.joinToString(", ") { it.name.orEmpty() }.takeIf { !it.isNullOrBlank() }) ?: album.authorsText,
                            shareUrl = online.url ?: album.shareUrl,
                            thumbnailUrl = PropUtils.retainIfModified(album.thumbnailUrl, a.thumbnail?.url) ?: album.thumbnailUrl,
                            timestamp = album.timestamp,
                            bookmarkedAt = album.bookmarkedAt,
                            isYoutubeAlbum = album.isYoutubeAlbum,
                            position = album.position
                        ))
                        songAlbumMapTable.clear(album.id)
                        online.songs
                            .map { it.asMediaItem }
                            .onEach { Database.insertIgnore(it) }
                            .mapIndexed { pos, mediaItem ->
                                SongAlbumMap(songId = mediaItem.mediaId, albumId = album.id, position = pos)
                            }.also { songAlbumMapTable.upsert(it) }
                    }
                    successCount++
                    status = 1
                }.onFailure {
                    Timber.tag("HomeSyncService").e(it, "[ALBUM|YT_DIRECT] ✗ Failed attempt $attempt: ${album.title}")
                    if (it is UnknownHostException || it is ConnectException) status = 3
                }
                if (status != 0) break
            }
            if (status == 3) { abortSync = true; break }
            if (status != 1) { failedCount++; HomeSyncState.albumSyncFailed = failedCount; failedList.add(album) }
        }

        // ── Local Albums: direct fetch or fallback search ──
        for ((index, album) in localAlbums.withIndex()) {
            if (abortSync) break
            HomeSyncState.albumSyncCurrentIndex = ytAlbums.size + index + 1
            HomeSyncState.albumSyncCurrentName = album.title ?: ""
            HomeSyncState.albumSyncProgress = (ytAlbums.size + index).toFloat() / totalAlbums
            HomeSyncState.showSyncNotification(
                title = appContext().getString(R.string.sync_notifications),
                message = appContext().getString(R.string.sync_progress_albums, ytAlbums.size + index + 1, totalAlbums),
                notificationId = notificationId,
                isOngoing = true,
                maxProgress = totalAlbums,
                currentProgress = ytAlbums.size + index + 1
            )
            kotlinx.coroutines.delay((2000L..5000L).random())

            val hasValidId = album.id.startsWith("OLAK") || album.id.startsWith("MPRE")
            val method = if (hasValidId) "LOCAL_DIRECT" else "FALLBACK_SEARCH"
            Timber.tag("HomeSyncService").d("[ALBUM|$method] #${ytAlbums.size+index+1}/${totalAlbums} id='${album.id}' title='${album.title}' hasValidId=$hasValidId")

            var status = 0
            for (attempt in 1..3) {
                if (hasValidId) {
                    YtMusic.getAlbum(album.id.removePrefix(MODIFIED_PREFIX), true).onSuccess { online ->
                        val a = online.album
                        val songCount = online.songs.size
                        Timber.tag("HomeSyncService").d("[ALBUM|$method] ✓ title='${a.title}' year='${a.year}' authors='${a.authors?.joinToString { it.name.orEmpty() }}' thumbnail='${a.thumbnail?.url}' songs=$songCount url='${online.url}'")
                        Database.asyncTransaction {
                            Database.albumTable.upsert(Album(
                                id = album.id,
                                title = PropUtils.retainIfModified(album.title, a.title),
                                year = a.year ?: album.year,
                                authorsText = PropUtils.retainIfModified(album.authorsText, a.authors?.joinToString(", ") { it.name.orEmpty() }.takeIf { !it.isNullOrBlank() }) ?: album.authorsText,
                                shareUrl = online.url ?: album.shareUrl,
                                thumbnailUrl = PropUtils.retainIfModified(album.thumbnailUrl, a.thumbnail?.url) ?: album.thumbnailUrl,
                                timestamp = album.timestamp,
                                bookmarkedAt = album.bookmarkedAt,
                                isYoutubeAlbum = album.isYoutubeAlbum,
                                position = album.position
                            ))
                            songAlbumMapTable.clear(album.id)
                            online.songs
                                .map { it.asMediaItem }
                                .onEach { Database.insertIgnore(it) }
                                .mapIndexed { pos, mediaItem ->
                                    SongAlbumMap(songId = mediaItem.mediaId, albumId = album.id, position = pos)
                                }.also { songAlbumMapTable.upsert(it) }
                        }
                        successCount++
                        status = 1
                    }.onFailure {
                        Timber.tag("HomeSyncService").e(it, "[ALBUM|$method] ✗ Failed attempt $attempt: ${album.title}")
                        if (it is UnknownHostException || it is ConnectException) status = 3
                    }
                } else {
                    val query = album.title?.trim()
                    if (query.isNullOrBlank()) { status = 2; break }
                    try {
                        Timber.tag("HomeSyncService").d("[ALBUM|FALLBACK_SEARCH] Searching YouTube: query='$query'")
                        val searchResult = Innertube.searchPage<Innertube.AlbumItem>(
                            query = query, params = Innertube.SearchFilter.Album.value,
                            fromMusicShelfRendererContent = { Innertube.AlbumItem.from(it) }
                        )?.getOrNull()
                        val remoteId = searchResult?.items?.firstOrNull()?.info?.endpoint?.browseId
                        val onlineTitle = searchResult?.items?.firstOrNull()?.title
                        val onlineYear = searchResult?.items?.firstOrNull()?.year
                        val onlineThumbnail = searchResult?.items?.firstOrNull()?.thumbnail?.url
                        if (remoteId != null) {
                            Timber.tag("HomeSyncService").d("[ALBUM|FALLBACK_SEARCH] ✓ Found: remoteId='$remoteId' onlineTitle='$onlineTitle' year='$onlineYear' thumbnail='$onlineThumbnail'")
                            YtMusic.getAlbum(remoteId.removePrefix(MODIFIED_PREFIX), true).onSuccess { online ->
                                val a = online.album
                                val songCount = online.songs.size
                                Timber.tag("HomeSyncService").d("[ALBUM|FALLBACK_SEARCH] ✓ Fetched: title='${a.title}' year='${a.year}' authors='${a.authors?.joinToString { it.name.orEmpty() }}' songs=$songCount url='${online.url}'")
                                Database.asyncTransaction {
                                    Database.albumTable.upsert(Album(
                                        id = album.id,
                                        title = PropUtils.retainIfModified(album.title, a.title),
                                        year = a.year ?: album.year,
                                        authorsText = PropUtils.retainIfModified(album.authorsText, a.authors?.joinToString(", ") { it.name.orEmpty() }.takeIf { !it.isNullOrBlank() }) ?: album.authorsText,
                                        shareUrl = online.url ?: album.shareUrl,
                                        thumbnailUrl = PropUtils.retainIfModified(album.thumbnailUrl, a.thumbnail?.url) ?: album.thumbnailUrl,
                                        timestamp = album.timestamp,
                                        bookmarkedAt = album.bookmarkedAt,
                                        isYoutubeAlbum = album.isYoutubeAlbum,
                                        position = album.position
                                    ))
                                    songAlbumMapTable.clear(album.id)
                                    online.songs
                                        .map { it.asMediaItem }
                                        .onEach { Database.insertIgnore(it) }
                                        .mapIndexed { pos, mediaItem ->
                                            SongAlbumMap(songId = mediaItem.mediaId, albumId = album.id, position = pos)
                                        }.also { songAlbumMapTable.upsert(it) }
                                }
                                successCount++
                                status = 1
                            }.onFailure {
                                if (it is UnknownHostException || it is ConnectException) status = 3
                            }
                        } else {
                            Timber.tag("HomeSyncService").d("[ALBUM|FALLBACK_SEARCH] ✗ No match found for '$query'")
                            status = 2
                        }
                    } catch (it: Exception) {
                        Timber.tag("HomeSyncService").e(it, "[ALBUM|FALLBACK_SEARCH] ✗ Search failed attempt $attempt: $query")
                        if (it is UnknownHostException || it is ConnectException) status = 3
                    }
                }
                if (status != 0) break
            }
            if (status == 3) { abortSync = true; break }
            if (status != 1) { failedCount++; HomeSyncState.albumSyncFailed = failedCount; failedList.add(album) }
        }

        Timber.tag("HomeSyncService").d("══════ ALBUM SYNC END ══════ success=$successCount failed=$failedCount total=$totalAlbums")

        withContext(Dispatchers.Main) {
            if (abortSync) {
                Toaster.e(appContext().getString(R.string.sync_failed))
                HomeSyncState.showSyncNotification(appContext().getString(R.string.sync_failed), "Sync aborted due to network error.", resultNotificationId)
            } else if (failedCount > 0) {
                HomeSyncState.failedAlbumsList = failedList
                val errorMessage = appContext().getString(R.string.failed_albums, failedCount)
                val notificationMessage = appContext().getString(R.string.sync_failed_notification_albums, failedCount)
                Toaster.e(errorMessage)
                HomeSyncState.showSyncNotification(
                    title = appContext().getString(R.string.sync_completed_with_errors),
                    message = notificationMessage,
                    notificationId = resultNotificationId
                )
            } else {
                Toaster.s(appContext().getString(R.string.found_all_albums))
                HomeSyncState.showSyncNotification(
                    title = appContext().getString(R.string.sync_successful),
                    message = appContext().getString(R.string.sync_success_notification_albums),
                    notificationId = resultNotificationId
                )
            }
            HomeSyncState.isSyncingAlbums = false
            HomeSyncState.albumSyncProgress = 1f
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    //  PLAYLISTS SYNC
    // ═══════════════════════════════════════════════════════════════════
    private suspend fun syncPlaylists(ids: List<String>?, notificationId: Int, resultNotificationId: Int) {
        if (HomeSyncState.isSyncingPlaylists) return
        HomeSyncState.isSyncingPlaylists = true
        HomeSyncState.playlistSyncProgress = 0f

        val allPlaylistsPreviews = Database.playlistTable.allAsPreview().first()
        val targetPlaylists = if (ids.isNullOrEmpty()) allPlaylistsPreviews else allPlaylistsPreviews.filter { it.playlist.id.toString() in ids }

        val ytPlaylists = targetPlaylists.filter { 
            it.playlist.isYoutubePlaylist && it.playlist.browseId?.startsWith("VL") == true
        }

        Timber.tag("HomeSyncService").d("══════ PLAYLIST SYNC START ══════ Total filtered: ${ytPlaylists.size}")

        withContext(Dispatchers.Main) {
            if (ytPlaylists.isNotEmpty()) Toaster.i(appContext().getString(R.string.refreshing_playlists, ytPlaylists.size))
        }

        if (ytPlaylists.isEmpty()) {
            HomeSyncState.isSyncingPlaylists = false
            return
        }

        var failedCount = 0
        val failedList = mutableListOf<PlaylistPreview>()
        HomeSyncState.playlistSyncFailed = 0
        HomeSyncState.playlistSyncTotal = ytPlaylists.size
        var successCount = 0
        var skippedCount = 0
        
        var abortSync = false
        for ((index, preview) in ytPlaylists.withIndex()) {
            if (abortSync) break
            HomeSyncState.playlistSyncCurrentIndex = index + 1
            HomeSyncState.playlistSyncCurrentName = preview.playlist.name
            HomeSyncState.playlistSyncProgress = index.toFloat() / ytPlaylists.size
            HomeSyncState.showSyncNotification(
                title = appContext().getString(R.string.sync_notifications),
                message = appContext().getString(R.string.sync_progress_playlists, index + 1, ytPlaylists.size),
                notificationId = notificationId,
                isOngoing = true,
                maxProgress = ytPlaylists.size,
                currentProgress = index + 1
            )
            val p = preview.playlist
            val browseId = p.browseId

            if (browseId == null || !browseId.startsWith("VL")) {
                Timber.tag("HomeSyncService").d("[PLAYLIST|SKIP] '${p.name}' browseId='$browseId' — not a VL playlist, skipping")
                skippedCount++
                continue
            }

            kotlinx.coroutines.delay((2000L..5000L).random())
            Timber.tag("HomeSyncService").d("[PLAYLIST|YT_DIRECT] #${index+1}/${ytPlaylists.size} id=${p.id} browseId='$browseId' name='${p.name}'")

            var status = 0
            for (attempt in 1..3) {
                val request = Innertube.playlistPage(browseId = browseId)
                if (request == null) {
                    Timber.tag("HomeSyncService").d("[PLAYLIST|YT_DIRECT] ✗ Request null for '${p.name}'")
                    status = 2
                    break
                }
                request.getOrNull()?.let { playlistPage ->
                    val songCount = playlistPage.songsPage?.items?.size ?: 0
                    Timber.tag("HomeSyncService").d("[PLAYLIST|YT_DIRECT] ✓ title='${playlistPage.title}' songs=$songCount url='${playlistPage.url}'")
                    Database.asyncTransaction {
                        playlistTable.update(p.copy(
                            name = PropUtils.retainIfModified(p.name, playlistPage.title) ?: p.name,
                            isYoutubePlaylist = true
                        ))
                        songPlaylistMapTable.clear(p.id)
                        val songs = playlistPage.songsPage?.items?.mapNotNull { it.asSong?.copy(totalPlayTimeMs = 1L) }
                        if (songs != null) {
                            songTable.upsert(songs)
                            songs.forEach { song ->
                                songPlaylistMapTable.map(song.id, p.id)
                            }
                        }
                    }
                    successCount++
                    status = 1
                } ?: run {
                    Timber.tag("HomeSyncService").e("[PLAYLIST|YT_DIRECT] ✗ Failed attempt $attempt: ${p.name}")
                    status = 3
                }
                if (status != 0) break
            }
            if (status == 3) { abortSync = true; break }
            if (status != 1) { failedCount++; HomeSyncState.playlistSyncFailed = failedCount; failedList.add(preview) }
        }

        Timber.tag("HomeSyncService").d("══════ PLAYLIST SYNC END ══════ success=$successCount failed=$failedCount skipped=$skippedCount total=${ytPlaylists.size}")

        withContext(Dispatchers.Main) {
            if (abortSync) {
                Toaster.e(appContext().getString(R.string.sync_failed))
                HomeSyncState.showSyncNotification(appContext().getString(R.string.sync_failed), "Sync aborted due to network error.", resultNotificationId)
            } else if (failedCount > 0) {
                HomeSyncState.failedPlaylistsList = failedList
                val errorMessage = appContext().getString(R.string.failed_playlists, failedCount)
                val notificationMessage = appContext().getString(R.string.sync_failed_notification_playlists, failedCount)
                Toaster.e(errorMessage)
                HomeSyncState.showSyncNotification(
                    title = appContext().getString(R.string.sync_completed_with_errors),
                    message = notificationMessage,
                    notificationId = resultNotificationId
                )
            } else {
                Toaster.s(appContext().getString(R.string.found_all_playlists))
                HomeSyncState.showSyncNotification(
                    title = appContext().getString(R.string.sync_successful),
                    message = appContext().getString(R.string.sync_success_notification_playlists),
                    notificationId = resultNotificationId
                )
            }
        }

        HomeSyncState.playlistSyncProgress = 1f
        HomeSyncState.isSyncingPlaylists = false
    }
}
