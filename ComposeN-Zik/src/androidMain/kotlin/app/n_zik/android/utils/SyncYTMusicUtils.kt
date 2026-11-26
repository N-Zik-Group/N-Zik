package app.n_zik.android.utils

import app.n_zik.android.appTextResources

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.YtMusic
import app.n_zik.innertube.utils.completed
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.n_zik.android.models.Album
import app.n_zik.android.models.Playlist
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.syncImportLibrarySongsKey
import app.n_zik.android.utils.syncImportUploadedSongsKey
import app.n_zik.android.utils.syncImportUploadedAlbumsKey
import app.n_zik.android.utils.syncImportEpisodesKey
import app.n_zik.android.utils.syncPushEpisodeKey
import app.n_zik.android.utils.syncCooldownKey
import app.n_zik.android.utils.syncShowDetailsKey
import app.n_zik.android.utils.syncDirectionKey
import app.n_zik.android.utils.autosyncLikesKey
import app.n_zik.android.utils.autosyncPlaylistsKey
import app.n_zik.android.utils.syncPushSongLikeKey
import app.n_zik.android.utils.syncPushAlbumBookmarkKey
import app.n_zik.android.utils.syncPushArtistFollowKey
import app.n_zik.android.utils.syncPushPlaylistKey
import app.n_zik.android.utils.isNetworkConnected
import app.n_zik.android.utils.syncBackgroundGuardKey
import app.n_zik.android.appRunningInBackground
import app.n_zik.android.enums.SyncDirection
import app.n_zik.android.utils.SyncStatusDetail
import kotlinx.coroutines.CancellationException
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import app.n_zik.android.core.utils.Toaster
import timber.log.Timber
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.core.utils.PropUtils

fun getSyncDirection(): SyncDirection {
    return appContext().preferences.getString(syncDirectionKey, SyncDirection.TWO_WAY.name)?.let {
        runCatching { SyncDirection.valueOf(it) }.getOrNull()
    } ?: SyncDirection.TWO_WAY
}

fun canImportFromYTM(): Boolean = getSyncDirection() != SyncDirection.APP_TO_YT
fun canPushToYTM(): Boolean = getSyncDirection() != SyncDirection.YT_TO_APP

fun isBackgroundGuardActive(): Boolean =
    appContext().preferences.getBoolean(syncBackgroundGuardKey, true) && appRunningInBackground

/**
 * Prevents rapid successive edits to the same playlist on YouTube.
 * Tracks last edit time per playlist and enforces a minimum delay.
 */
object PlaylistEditThrottle {
    private const val THROTTLE_MS = 500L
    private val lastEditTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

    suspend fun throttle(playlistBrowseId: String) {
        val now = System.currentTimeMillis()
        val last = lastEditTime[playlistBrowseId] ?: 0L
        val elapsed = now - last
        if (elapsed < THROTTLE_MS) {
            kotlinx.coroutines.delay(THROTTLE_MS - elapsed)
        }
        lastEditTime[playlistBrowseId] = System.currentTimeMillis()
    }

    fun clear() {
        lastEditTime.clear()
    }
}

@OptIn(UnstableApi::class)
suspend fun ytmPrivatePlaylistSync(playlist: Playlist, playlistId: Long) = withContext(NzikDispatchers.DATA) {
    val plist = playlist
    // Network call on IO thread
    val remotePlaylist = plist.browseId?.let {
        YtMusic.getPlaylist(playlistId = it.removePrefix(MODIFIED_PREFIX)).completed().getOrNull()
    }
    remotePlaylist?.let { rp ->
        Timber.tag("SyncYTMusicUtils").d("ytmPrivatePlaylistSync Remote playlist editable: ${rp.isEditable}")

        val allSongs = rp.songs.toMutableList()
        var continuation = rp.songsContinuation
        while (continuation != null) {
            val contPage = YtMusic.getPlaylistContinuation(continuation).getOrNull()
            if (contPage != null) {
                allSongs.addAll(contPage.songs)
                continuation = contPage.continuation
            } else {
                break
            }
        }
        val mediaItems = allSongs.map( Innertube.SongItem::asMediaItem )

        Database.asyncTransaction {
            if (rp.isEditable == true)
                Database.playlistTable
                        .update( playlist.copy(isEditable = true) )
            mediaItems.forEach { mediaItem ->
                Database.insertIgnore( mediaItem )
                Database.songPlaylistMapTable.map( mediaItem.mediaId, playlistId )
            }
        }
    }
}

suspend fun importYTMSubscribedChannels(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("importYTMSubscribedChannels isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsedFor("artists")) return@withContext false
    val autosyncArtists = appContext().preferences.getBoolean(autosyncArtistsKey, false)
    if (!autosyncArtists && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

        if (showDetails) Toaster.i( R.string.sync_running_status, Toast.LENGTH_LONG )

        val imported = YouTubeArtistProvenance.importSnapshot {
            val first = Innertube.library("FEmusic_library_corpus_artists", requireLibraryRenderer = true).getOrThrowRetriable()
                ?: return@importSnapshot null
            completeYouTubeArtistLibrary(first) { continuation ->
                Innertube.libraryContinuation(continuation).getOrThrowRetriable()
            }
        }
        if (!imported) {
            coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Text(R.string.sync_fetch_subscribed_channels_failed)
            if (showDetails) Toaster.e(R.string.sync_failed)
            return@withContext false
        }

        if (showDetails) Toaster.done()
        setLastSyncTimeFor("artists")
        true
}

suspend fun importYTMLikedAlbums(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("importYTMLikedAlbums isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsedFor("albums")) return@withContext false
    val autosyncAlbums = appContext().preferences.getBoolean(autosyncAlbumsKey, false)
    if (!autosyncAlbums && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

        if (showDetails) Toaster.i( R.string.sync_running_status, Toast.LENGTH_LONG )

        val page = Innertube.library("FEmusic_liked_albums").completed().getOrThrowRetriable()
        if (page == null) {
            coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Text(R.string.sync_fetch_liked_albums_failed)
            if (showDetails) Toaster.e(R.string.sync_failed)
            return@withContext false
        }

        val ytmAlbums = page.items.filterIsInstance<Innertube.AlbumItem>()
        val remoteKeys = ytmAlbums.map { it.key }.toSet()

        Timber.tag("SyncYTMusicUtils").d("YTM albums: $ytmAlbums")

        ytmAlbums.forEach { remoteAlbum ->
            var localAlbum = Database.albumTable.findById( remoteAlbum.key ).first()
            Timber.tag("SyncYTMusicUtils").d("Local album: $localAlbum")
            Timber.tag("SyncYTMusicUtils").d("Remote album: $remoteAlbum")

            if (localAlbum == null) {
                localAlbum = Album(
                    id = remoteAlbum.key,
                    title = remoteAlbum.title,
                    thumbnailUrl = remoteAlbum.thumbnail?.url,
                    bookmarkedAt = System.currentTimeMillis(),
                    year = remoteAlbum.year,
                    authorsText = remoteAlbum.authors?.joinToString(", ") { it.name.orEmpty() }.takeIf { !it.isNullOrBlank() },
                    isYoutubeAlbum = true
                )
                Database.albumTable.upsert( localAlbum )
            } else {
                localAlbum.copy(
                    isYoutubeAlbum = true,
                    bookmarkedAt = localAlbum.bookmarkedAt ?: System.currentTimeMillis(),
                    thumbnailUrl = PropUtils.retainIfModified(localAlbum.thumbnailUrl, remoteAlbum.thumbnail?.url) ?: localAlbum.thumbnailUrl)
                    .let { Database.albumTable.updateReplace(it) }
            }
        }

        Database.albumTable
                .all()
                .first()
                .filter { album ->
                    album.isYoutubeAlbum && album.id !in remoteKeys
                }
                .map { it.copy( isYoutubeAlbum = false, bookmarkedAt = null ) }
                .also { Database.albumTable.updateReplace(it) }

        if (showDetails) Toaster.done()
        setLastSyncTimeFor("albums")
        true
}

suspend fun removeYTSongFromPlaylist(
    songId: String,
    playlistBrowseId: String,
    playlistId: Long,
): Boolean {
    Timber.tag("SyncYTMusicUtils").d("removeYTSongFromPlaylist removeSongFromPlaylist params songId = $songId, playlistBrowseId = $playlistBrowseId, playlistId = $playlistId")

    if ( !isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext()) ) return false

    val setVideoId: String = Database.songPlaylistMapTable
                                     .findById( songId, playlistId )
                                     .first()
                                     ?.setVideoId ?: return false

    Timber.tag("SyncYTMusicUtils").d("removeYTSongFromPlaylist removeSongFromPlaylist songSetVideoId = $setVideoId")

    PlaylistEditThrottle.throttle(playlistBrowseId)
    return YtMusic.removeFromPlaylist( playlistBrowseId, songId, setVideoId )
        .onFailure { e ->
            Timber.tag("SyncYTMusicUtils").e(e, "removeYTSongFromPlaylist failed for $songId in $playlistBrowseId")
        }
        .isSuccess
}

suspend fun importYTMLikedSongs(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("importYTMLikedSongs isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsedFor("likedSongs")) return@withContext false
    val autosyncLikes = appContext().preferences.getBoolean(autosyncLikesKey, false)
    if (!autosyncLikes && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.sync_running_status, Toast.LENGTH_LONG )

    val page = YtMusic.getPlaylist(playlistId = "LM").completed().getOrThrowRetriable()
    if (page == null) {
        coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Text(R.string.sync_fetch_liked_songs_failed)
        if (showDetails) Toaster.e(R.string.sync_failed)
        return@withContext false
    }

    val ytmLikedSongs = page.songs
    Timber.tag("SyncYTMusicUtils").d("YTM liked songs: ${ytmLikedSongs.size}")

    // Use a base timestamp and decrement for each song to preserve order
    // YouTube returns most recent first, so index0 = newest = largest timestamp
    val baseTimestamp = System.currentTimeMillis()
    ytmLikedSongs.forEachIndexed { index, song ->
        Database.insertIgnore( song.asMediaItem )
        // First song (most recent) gets largest timestamp
        val likedAt = baseTimestamp - index * 1000L
        Database.songTable.likeState(song.key, likedAt)
        Timber.tag("SyncYTMusicUtils").d("Liked song: ${song.title}")
    }

    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Count(R.plurals.sync_detail_songs_liked, ytmLikedSongs.size)

    Timber.tag("SyncYTMusicUtils").d("importYTMLikedSongs completed: ${ytmLikedSongs.size} songs liked")
    if (showDetails) Toaster.done()
    setLastSyncTimeFor("likedSongs")
    true
}

suspend fun removeYTMLikedSongs(): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("removeYTMLikedSongs called")
    if (!isYouTubeSyncEnabled()) return@withContext false

    Toaster.i( R.string.sync_running_status, Toast.LENGTH_LONG )

    runCatching {
        // Fetch current YTM liked songs to get their IDs
        val result = YtMusic.getPlaylist(playlistId = "LM").completed().onSuccess { page ->
            val ytmLikedSongIds = page.songs.map { it.key }.toSet()
            Timber.tag("SyncYTMusicUtils").d("YTM liked songs to remove: ${ytmLikedSongIds.size}")

            // Unlike all YTM-liked songs in local database
            ytmLikedSongIds.forEach { songId ->
                Database.songTable.likeState(songId, null)
                Timber.tag("SyncYTMusicUtils").d("Unliked song: $songId")
            }

            Timber.tag("SyncYTMusicUtils").d("removeYTMLikedSongs completed: ${ytmLikedSongIds.size} songs unliked")
            Toaster.done()
        }
        result.onFailure {
            coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Text(R.string.common_error_unknown)
            Timber.tag("SyncYTMusicUtils").e(it, "removeYTMLikedSongs failed")
            Toaster.e(R.string.sync_failed)
        }
        result.isSuccess
    }.getOrDefault(false)
}

suspend fun importYTMPlaylists(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("importYTMPlaylists isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsedFor("playlists")) return@withContext false
    val autosyncPlaylists = appContext().preferences.getBoolean(autosyncPlaylistsKey, false)
    if (!autosyncPlaylists && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.sync_running_status, Toast.LENGTH_LONG )

    val page = Innertube.library("FEmusic_liked_playlists").completed().getOrThrowRetriable()
    if (page == null) {
        coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Text(R.string.sync_fetch_playlists_failed)
        if (showDetails) Toaster.e(R.string.sync_failed)
        return@withContext false
    }

    val ytmPlaylists = page.items.filterIsInstance<Innertube.PlaylistItem>()
        .filterNot { it.key == "LM" || it.key == "SE" }
        .distinctBy { it.key }

    Timber.tag("SyncYTMusicUtils").d("YTM playlists: ${ytmPlaylists.size}")

    ytmPlaylists.forEach { remotePlaylist ->
        val browseIdWithVL = if (remotePlaylist.key.startsWith("VL")) remotePlaylist.key else "VL${remotePlaylist.key}"

        val existingPlaylist = Database.playlistTable
            .findByBrowseId(browseIdWithVL)
            .first()
            ?: Database.playlistTable
                .findByBrowseId(remotePlaylist.key)
                .first()

        val remoteTitle = remotePlaylist.title?.takeIf(String::isNotBlank)
        val playlistId: Long
        if (existingPlaylist == null) {
            val newPlaylist = Playlist(
                name = remoteTitle ?: appTextResources().getString(R.string.playlist_youtube_title),
                browseId = browseIdWithVL,
                isYoutubePlaylist = true,
                isEditable = remotePlaylist.isEditable ?: false
            )
            playlistId = Database.playlistTable.insert(newPlaylist)
            Timber.tag("SyncYTMusicUtils").d("Created playlist: ${remotePlaylist.title}")
        } else {
            playlistId = existingPlaylist.id
            Database.playlistTable.update(
                existingPlaylist.copy(
                    name = remoteTitle ?: existingPlaylist.name,
                    browseId = browseIdWithVL,
                    isEditable = remotePlaylist.isEditable ?: existingPlaylist.isEditable
                )
            )
            Timber.tag("SyncYTMusicUtils").d("Updated playlist: ${remotePlaylist.title}")
        }

        val playlist = Database.playlistTable.findById(playlistId).first()
        if (playlist != null) {
            ytmPrivatePlaylistSync(playlist, playlistId)
        }
    }

    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Count(R.plurals.sync_detail_playlists_synced, ytmPlaylists.size)

    Timber.tag("SyncYTMusicUtils").d("importYTMPlaylists completed: ${ytmPlaylists.size} playlists synced")
    if (showDetails) Toaster.done()
    setLastSyncTimeFor("playlists")
    true
}


@Composable
fun autoSyncToolbutton(messageId: Int, preferenceKey: String = autosyncKey, syncAction: () -> Unit = {}): MenuIcon = object : MenuIcon, Descriptive {

    override val iconId: Int = R.drawable.sync
    override val messageId: Int = messageId
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override fun onShortClick() {
        syncAction()
    }

    override fun onLongClick() {}
}

suspend fun importYTMLibrarySongs(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("importYTMLibrarySongs isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsedFor("librarySongs")) return@withContext false
    val syncLibrarySongs = appContext().preferences.getBoolean(syncImportLibrarySongsKey, false)
    if (!syncLibrarySongs && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.sync_running_status, Toast.LENGTH_LONG )

    val page = Innertube.library("FEmusic_liked_videos").completed().getOrThrowRetriable()
    if (page == null) {
        coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Text(R.string.sync_fetch_library_songs_failed)
        if (showDetails) Toaster.e(R.string.sync_failed)
        return@withContext false
    }

    val ytmSongs = page.items.filterIsInstance<Innertube.SongItem>()
    Timber.tag("SyncYTMusicUtils").d("YTM library songs: ${ytmSongs.size}")

    ytmSongs.forEach { song ->
        Database.insertIgnore( song.asMediaItem )
        Timber.tag("SyncYTMusicUtils").d("Library song: ${song.title}")
    }

    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Count(R.plurals.sync_detail_songs_imported, ytmSongs.size)

    Timber.tag("SyncYTMusicUtils").d("importYTMLibrarySongs completed: ${ytmSongs.size} songs imported")
    if (showDetails) Toaster.done()
    setLastSyncTimeFor("librarySongs")
    true
}

suspend fun importYTMUploadedSongs(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("importYTMUploadedSongs isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsedFor("uploadedSongs")) return@withContext false
    val syncUploadedSongs = appContext().preferences.getBoolean(syncImportUploadedSongsKey, false)
    if (!syncUploadedSongs && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.sync_running_status, Toast.LENGTH_LONG )

    val page = Innertube.library("FEmusic_library_privately_owned_tracks").completed().getOrThrowRetriable()
    if (page == null) {
        coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Text(R.string.sync_fetch_uploaded_songs_failed)
        if (showDetails) Toaster.e(R.string.sync_failed)
        return@withContext false
    }

    val ytmSongs = page.items.filterIsInstance<Innertube.SongItem>()
    Timber.tag("SyncYTMusicUtils").d("YTM uploaded songs: ${ytmSongs.size}")

    ytmSongs.forEach { song ->
        Database.insertIgnore( song.asMediaItem )
        Timber.tag("SyncYTMusicUtils").d("Uploaded song: ${song.title}")
    }

    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Count(R.plurals.sync_detail_songs_imported, ytmSongs.size)

    Timber.tag("SyncYTMusicUtils").d("importYTMUploadedSongs completed: ${ytmSongs.size} songs imported")
    if (showDetails) Toaster.done()
    setLastSyncTimeFor("uploadedSongs")
    true
}

suspend fun importYTMUploadedAlbums(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("importYTMUploadedAlbums isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsedFor("uploadedAlbums")) return@withContext false
    val syncUploadedAlbums = appContext().preferences.getBoolean(syncImportUploadedAlbumsKey, false)
    if (!syncUploadedAlbums && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.sync_running_status, Toast.LENGTH_LONG )

    val page = Innertube.library("FEmusic_library_privately_owned_releases").completed().getOrThrowRetriable()
    if (page == null) {
        coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Text(R.string.sync_fetch_uploaded_albums_failed)
        if (showDetails) Toaster.e(R.string.sync_failed)
        return@withContext false
    }

    val ytmAlbums = page.items.filterIsInstance<Innertube.AlbumItem>()
    val remoteKeys = ytmAlbums.map { it.key }.toSet()
    Timber.tag("SyncYTMusicUtils").d("YTM uploaded albums: ${ytmAlbums.size}")

    ytmAlbums.forEach { remoteAlbum ->
        var localAlbum = Database.albumTable.findById( remoteAlbum.key ).first()

        if (localAlbum == null) {
            localAlbum = Album(
                id = remoteAlbum.key,
                title = remoteAlbum.title,
                thumbnailUrl = remoteAlbum.thumbnail?.url,
                year = remoteAlbum.year,
                authorsText = remoteAlbum.authors?.joinToString(", ") { it.name.orEmpty() }.takeIf { !it.isNullOrBlank() },
                isYoutubeAlbum = true
            )
            Database.albumTable.upsert( localAlbum )
        } else {
            localAlbum.copy(
                isYoutubeAlbum = true,
                thumbnailUrl = PropUtils.retainIfModified(localAlbum.thumbnailUrl, remoteAlbum.thumbnail?.url) ?: localAlbum.thumbnailUrl
            ).let { Database.albumTable.updateReplace(it) }
        }
    }

    Database.albumTable
            .all()
            .first()
            .filter { album ->
                album.isYoutubeAlbum && album.id !in remoteKeys
            }
            .map { it.copy( isYoutubeAlbum = false ) }
            .also { Database.albumTable.updateReplace(it) }

    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Count(R.plurals.sync_detail_albums_imported, ytmAlbums.size)

    Timber.tag("SyncYTMusicUtils").d("importYTMUploadedAlbums completed: ${ytmAlbums.size} albums imported")
    if (showDetails) Toaster.done()
    setLastSyncTimeFor("uploadedAlbums")
    true
}

suspend fun importYTMEpisodesForLater(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("importYTMEpisodesForLater isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsedFor("episodes")) return@withContext false
    val syncEpisodes = appContext().preferences.getBoolean(syncImportEpisodesKey, false)
    if (!syncEpisodes && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.sync_running_status, Toast.LENGTH_LONG )

    val page = YtMusic.getPlaylist(playlistId = "SE").completed().getOrThrowRetriable()
    if (page == null) {
        coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Text(R.string.sync_fetch_episodes_failed)
        if (showDetails) Toaster.e(R.string.sync_failed)
        return@withContext false
    }

    val episodes = page.songs
    Timber.tag("SyncYTMusicUtils").d("YTM episodes for later: ${episodes.size}")

    episodes.forEach { episode ->
        Database.insertIgnore( episode.asMediaItem )
        Timber.tag("SyncYTMusicUtils").d("Episode: ${episode.title}")
    }

    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.Count(R.plurals.sync_detail_episodes, episodes.size)

    Timber.tag("SyncYTMusicUtils").d("importYTMEpisodesForLater completed: ${episodes.size} episodes")
    if (showDetails) Toaster.done()
    setLastSyncTimeFor("episodes")
    true
}

private val lastSyncTimePerFeature = java.util.concurrent.ConcurrentHashMap<String, Long>()

internal fun clearArtistSyncCooldown() {
    lastSyncTimePerFeature.remove("artists")
}

fun getLastSyncTime(): Long {
    return appContext().preferences.getLong("lastSyncTime", 0)
}

fun setLastSyncTime() {
    appContext().preferences.edit().putLong("lastSyncTime", System.currentTimeMillis()).apply()
}

fun setLastSyncTimeFor(featureKey: String) {
    lastSyncTimePerFeature[featureKey] = System.currentTimeMillis()
    setLastSyncTime() // also update the global timestamp for UI display
}

fun isSyncCooldownElapsedFor(featureKey: String): Boolean {
    val cooldownMinutes = appContext().preferences.getInt(syncCooldownKey, 30)
    val cooldownMs = cooldownMinutes * 60 * 1000L
    val lastSync = lastSyncTimePerFeature[featureKey] ?: 0L
    return System.currentTimeMillis() - lastSync > cooldownMs
}

fun isSyncCooldownElapsed(): Boolean {
    val cooldownMinutes = appContext().preferences.getInt(syncCooldownKey, 30)
    val lastSync = getLastSyncTime()
    val cooldownMs = cooldownMinutes * 60 * 1000L
    return System.currentTimeMillis() - lastSync > cooldownMs
}

/**
 * Extracts the value from a Result, throwing if the failure is a retriable error
 * (network timeout, DNS failure, connection refused, etc.) for the sync queue to retry.
 * Non-retriable errors return null.
 */
private fun <T> Result<T>.getOrThrowRetriable(): T? {
    return fold(
        onSuccess = { it },
        onFailure = { e ->
            when (e) {
                is CancellationException,
                is java.net.UnknownHostException,
                is java.net.SocketTimeoutException,
                is java.net.ConnectException,
                is java.io.IOException -> throw e
                else -> null
            }
        }
    )
}

suspend fun cleanupDuplicatePlaylists() = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("cleanupDuplicatePlaylists started")
    val playlists = Database.playlistTable.getAll()
    val byBrowseId = playlists.filter { !it.browseId.isNullOrBlank() }.groupBy { it.browseId }
    var cleaned = 0
    byBrowseId.forEach { (browseId, duplicates) ->
        if (duplicates.size > 1) {
            val keep = duplicates.maxByOrNull { it.id } ?: return@forEach
            duplicates.filter { it.id != keep.id }.forEach { dup ->
                Database.songPlaylistMapTable.clear(dup.id)
                Database.playlistTable.delete(dup)
                cleaned++
                Timber.tag("SyncYTMusicUtils").d("Removed duplicate playlist: ${dup.name} (browseId=$browseId)")
            }
        }
    }
    Timber.tag("SyncYTMusicUtils").d("cleanupDuplicatePlaylists completed: $cleaned duplicates removed")
}

suspend fun savePodcast(podcastId: String): Boolean = withContext(NzikDispatchers.DATA) {
    if (!isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext())) return@withContext false
    val result = runCatching {
        YtMusic.likePlaylistOrAlbum(podcastId)
    }
    if (result.isSuccess) Toaster.s( R.string.library_favorites_added )
    else Toaster.e( R.string.sync_failed )
    result.isSuccess
}

suspend fun unsavePodcast(podcastId: String): Boolean = withContext(NzikDispatchers.DATA) {
    if (!isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext())) return@withContext false
    val result = runCatching {
        YtMusic.removelikePlaylistOrAlbum(podcastId)
    }
    if (result.isSuccess) Toaster.s( R.string.library_favorites_removed )
    else Toaster.e( R.string.sync_failed )
    result.isSuccess
}

suspend fun addEpisodeToSavedEpisodes(videoId: String): Boolean = withContext(NzikDispatchers.DATA) {
    if (!isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext())) return@withContext false
    val result = runCatching {
        PlaylistEditThrottle.throttle("SE")
        YtMusic.addToPlaylist("SE", videoId)
    }
    if (result.isSuccess) Toaster.s( R.string.library_favorites_added )
    else Toaster.e( R.string.sync_failed )
    result.isSuccess
}

suspend fun removeEpisodeFromSavedEpisodes(videoId: String, setVideoId: String? = null): Boolean = withContext(NzikDispatchers.DATA) {
    if (!isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext())) return@withContext false
    val result = runCatching {
        PlaylistEditThrottle.throttle("SE")
        YtMusic.removeFromPlaylist("SE", videoId, setVideoId)
    }
    if (result.isSuccess) Toaster.s( R.string.library_favorites_removed )
    else Toaster.e( R.string.sync_failed )
    result.isSuccess
}

suspend fun pushYTMLikedSongs(force: Boolean = false): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("pushYTMLikedSongs isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canPushToYTM()) return@withContext false
    val pushSongLike = appContext().preferences.getBoolean(syncPushSongLikeKey, false)
    if (!pushSongLike && !force) return@withContext false
    if (!isNetworkConnected(appContext())) return@withContext false

    val likedSongs = Database.songTable.all().first().filter { it.likedAt != null }
    Timber.tag("SyncYTMusicUtils").d("pushYTMLikedSongs: ${likedSongs.size} liked songs to push")

    var success = 0
    var failed = 0
    likedSongs.forEach { song ->
        runCatching {
            YtMusic.likeVideoOrSong(song.id)
        }.onSuccess {
            success++
        }.onFailure { e ->
            failed++
            Timber.tag("SyncYTMusicUtils").e(e, "pushYTMLikedSongs failed for song: ${song.id}")
        }
        kotlinx.coroutines.delay(50L)
    }
    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.PushResult(success, failed)

    Timber.tag("SyncYTMusicUtils").d("pushYTMLikedSongs completed: $success success, $failed failed")
    failed == 0
}

suspend fun pushYTMAlbumBookmarks(force: Boolean = false): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("pushYTMAlbumBookmarks isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canPushToYTM()) return@withContext false
    val pushAlbumBookmark = appContext().preferences.getBoolean(syncPushAlbumBookmarkKey, false)
    if (!pushAlbumBookmark && !force) return@withContext false
    if (!isNetworkConnected(appContext())) return@withContext false

    val bookmarkedAlbums = Database.albumTable.all().first().filter { it.bookmarkedAt != null }
    Timber.tag("SyncYTMusicUtils").d("pushYTMAlbumBookmarks: ${bookmarkedAlbums.size} bookmarked albums to push")

    var success = 0
    var failed = 0
    bookmarkedAlbums.forEach { album ->
        val playlistId = album.shareUrl
            ?.substringAfter("list=")
            ?.takeIf { it.isNotBlank() }
            ?: album.id
        runCatching {
            YtMusic.likePlaylistOrAlbum(playlistId)
        }.onSuccess {
            success++
        }.onFailure { e ->
            failed++
            Timber.tag("SyncYTMusicUtils").e(e, "pushYTMAlbumBookmarks failed for album: ${album.id}")
        }
        kotlinx.coroutines.delay(50L)
    }
    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.PushResult(success, failed)

    Timber.tag("SyncYTMusicUtils").d("pushYTMAlbumBookmarks completed: $success success, $failed failed")
    failed == 0
}

suspend fun pushYTMArtistFollows(force: Boolean = false): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("pushYTMArtistFollows isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canPushToYTM()) return@withContext false
    val pushArtistFollow = appContext().preferences.getBoolean(syncPushArtistFollowKey, false)
    if (!pushArtistFollow && !force) return@withContext false
    if (!isNetworkConnected(appContext())) return@withContext false

    if (!YouTubeArtistProvenance.canShowOrigin()) return@withContext false
    val followedArtists = Database.artistTable.allFollowing().first().filter { it.isYoutubeArtist }
    Timber.tag("SyncYTMusicUtils").d("pushYTMArtistFollows: ${followedArtists.size} YouTube followed artists to push")

    var success = 0
    var failed = 0
    followedArtists.forEach { artist ->
        if (!YouTubeArtistProvenance.canShowOrigin()) return@withContext false
        runCatching {
            YtMusic.subscribeChannel(artist.id)
        }.onSuccess {
            success++
        }.onFailure { e ->
            failed++
            Timber.tag("SyncYTMusicUtils").e(e, "pushYTMArtistFollows failed for artist: ${artist.id}")
        }
        kotlinx.coroutines.delay(50L)
    }
    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.PushResult(success, failed)

    Timber.tag("SyncYTMusicUtils").d("pushYTMArtistFollows completed: $success success, $failed failed")
    failed == 0
}

suspend fun pushYTMPlaylists(force: Boolean = false): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("pushYTMPlaylists isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canPushToYTM()) return@withContext false
    val pushPlaylist = appContext().preferences.getBoolean(syncPushPlaylistKey, false)
    if (!pushPlaylist && !force) return@withContext false
    if (!isNetworkConnected(appContext())) return@withContext false

    val localPlaylists = Database.playlistTable.getAll().filter { !it.isYoutubePlaylist && it.browseId.isNullOrBlank() }
    Timber.tag("SyncYTMusicUtils").d("pushYTMPlaylists: ${localPlaylists.size} local playlists to push")

    var success = 0
    var failed = 0
    localPlaylists.forEach { playlist ->
        runCatching {
            val createResult = YtMusic.createPlaylist(playlist.name)
            createResult.getOrNull()?.let { ytPlaylistId ->
                val songs = Database.songPlaylistMapTable.allSongsOf(playlist.id).first()
                songs.forEach { song ->
                    PlaylistEditThrottle.throttle(ytPlaylistId)
                    YtMusic.addToPlaylist(ytPlaylistId, song.id)
                }
                Database.playlistTable.update(
                    playlist.copy(
                        browseId = "VL$ytPlaylistId",
                        isYoutubePlaylist = true
                    )
                )
            }
        }.onSuccess {
            success++
        }.onFailure { e ->
            failed++
            Timber.tag("SyncYTMusicUtils").e(e, "pushYTMPlaylists failed for playlist: ${playlist.name}")
        }
    }
    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.PushResult(success, failed)

    Timber.tag("SyncYTMusicUtils").d("pushYTMPlaylists completed: $success success, $failed failed")
    failed == 0
}

suspend fun pushYTMSavedEpisodes(force: Boolean = false): Boolean = withContext(NzikDispatchers.DATA) {
    if (!isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext())) return@withContext false
    val pushEpisodes = appContext().preferences.getBoolean(syncPushEpisodeKey, false)
    if (!pushEpisodes && !force) return@withContext false

    Timber.tag("SyncYTMusicUtils").d("pushYTMSavedEpisodes started")
    var success = 0
    var failed = 0

    // Get songs that are in the "SE" playlist (saved episodes)
    val sePlaylist = Database.playlistTable.findByBrowseId("SE").first()
    if (sePlaylist == null) {
        Timber.tag("SyncYTMusicUtils").d("pushYTMSavedEpisodes: no SE playlist found locally")
        return@withContext true
    }

    val episodes = Database.songPlaylistMapTable.allSongsOf(sePlaylist.id).first()
    episodes.forEach { episode ->
        runCatching {
            PlaylistEditThrottle.throttle("SE")
            YtMusic.addToPlaylist("SE", episode.id)
        }.onSuccess {
            success++
        }.onFailure { e ->
            failed++
            Timber.tag("SyncYTMusicUtils").e(e, "pushYTMSavedEpisodes failed for episode: ${episode.title}")
        }
    }
    coroutineContext[SyncDetailCapture]?.detail = SyncStatusDetail.PushResult(success, failed)

    Timber.tag("SyncYTMusicUtils").d("pushYTMSavedEpisodes completed: $success success, $failed failed")
    failed == 0
}

suspend fun clearAllSyncedData(): Boolean = withContext(NzikDispatchers.DATA) {
    Timber.tag("SyncYTMusicUtils").d("clearAllSyncedData started")

    // Cancel any running sync operations
    cancelAllSyncs()

    var hadErrors = false

    // Clear all artist YouTube flags
    runCatching {
        YouTubeArtistProvenance.reset()
    }.onFailure { e ->
        Timber.tag("SyncYTMusicUtils").e(e, "Failed to clear artist YouTube flags")
        hadErrors = true
    }

    // Clear all album YouTube flags
    runCatching {
        Database.albumTable.all().first().forEach { album ->
            if (album.isYoutubeAlbum) {
                Database.albumTable.updateReplace(album.copy(isYoutubeAlbum = false, bookmarkedAt = null))
            }
        }
    }.onFailure { e ->
        Timber.tag("SyncYTMusicUtils").e(e, "Failed to clear album YouTube flags")
        hadErrors = true
    }

    // Clear all playlist YouTube flags
    runCatching {
        Database.playlistTable.getAll().forEach { playlist ->
            if (playlist.isYoutubePlaylist) {
                Database.playlistTable.update(playlist.copy(isYoutubePlaylist = false))
            }
        }
    }.onFailure { e ->
        Timber.tag("SyncYTMusicUtils").e(e, "Failed to clear playlist YouTube flags")
        hadErrors = true
    }

    // Clear all song like states
    runCatching {
        Database.songTable.all().first().forEach { song ->
            if (song.likedAt != null) {
                Database.songTable.likeState(song.id, null)
            }
        }
    }.onFailure { e ->
        Timber.tag("SyncYTMusicUtils").e(e, "Failed to clear song like states")
        hadErrors = true
    }

    Timber.tag("SyncYTMusicUtils").d("clearAllSyncedData completed, hadErrors=$hadErrors")
    !hadErrors
}

