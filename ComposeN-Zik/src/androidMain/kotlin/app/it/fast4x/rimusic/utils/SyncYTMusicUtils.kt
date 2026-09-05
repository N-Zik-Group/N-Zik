package app.it.fast4x.rimusic.utils

import app.n_zik.android.core.database.*

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.utils.completed
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.syncImportLibrarySongsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedSongsKey
import app.it.fast4x.rimusic.utils.syncImportUploadedAlbumsKey
import app.it.fast4x.rimusic.utils.syncImportEpisodesKey
import app.it.fast4x.rimusic.utils.syncPushEpisodeKey
import app.it.fast4x.rimusic.utils.syncCooldownKey
import app.it.fast4x.rimusic.utils.syncDirectionKey
import app.it.fast4x.rimusic.utils.autosyncLikesKey
import app.it.fast4x.rimusic.utils.autosyncPlaylistsKey
import app.it.fast4x.rimusic.utils.syncPushSongLikeKey
import app.it.fast4x.rimusic.utils.syncPushAlbumBookmarkKey
import app.it.fast4x.rimusic.utils.syncPushArtistFollowKey
import app.it.fast4x.rimusic.utils.syncPushPlaylistKey
import app.it.fast4x.rimusic.utils.isNetworkConnected
import app.it.fast4x.rimusic.utils.syncBackgroundGuardKey
import app.n_zik.android.appRunningInBackground
import app.it.fast4x.rimusic.enums.SyncDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.kreate.android.me.knighthat.utils.PropUtils

fun getSyncDirection(): SyncDirection {
    return appContext().preferences.getString(syncDirectionKey, SyncDirection.TWO_WAY.name)?.let {
        runCatching { SyncDirection.valueOf(it) }.getOrNull()
    } ?: SyncDirection.TWO_WAY
}

fun canImportFromYTM(): Boolean = getSyncDirection() != SyncDirection.APP_TO_YT
fun canPushToYTM(): Boolean = getSyncDirection() != SyncDirection.YT_TO_APP

fun isBackgroundGuardActive(): Boolean =
    appContext().preferences.getBoolean(syncBackgroundGuardKey, true) && appRunningInBackground

// ============ PLAYLIST EDIT THROTTLE ============

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
suspend fun ytmPrivatePlaylistSync(playlist: Playlist, playlistId: Long) = withContext(Dispatchers.IO) {
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

suspend fun importYTMSubscribedChannels(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("importYTMSubscribedChannels isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsed()) return@withContext false
    val autosyncArtists = appContext().preferences.getBoolean(autosyncArtistsKey, false)
    if (!autosyncArtists && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

        if (showDetails) Toaster.i( R.string.syncing, Toast.LENGTH_LONG )

        val result = Innertube.library("FEmusic_library_corpus_artists").completed().onSuccess { page ->

            val ytmArtists = page.items.filterIsInstance<Innertube.ArtistItem>()
            val remoteKeys = ytmArtists.map { it.key }.toSet()

            Timber.tag("SyncYTMusicUtils").d("YTM artists: $ytmArtists")

            ytmArtists.forEach { remoteArtist ->
                var localArtist = Database.artistTable.findById( remoteArtist.key ).first()
                Timber.tag("SyncYTMusicUtils").d("Local artist: $localArtist")
                Timber.tag("SyncYTMusicUtils").d("Remote artist: $remoteArtist")

                if (localArtist == null) {
                    localArtist = Artist(
                        id = remoteArtist.key,
                        name = remoteArtist.title,
                        thumbnailUrl = remoteArtist.thumbnail?.url,
                        bookmarkedAt = System.currentTimeMillis(),
                        isYoutubeArtist = true
                    )
                    Database.artistTable.upsert( localArtist )
                } else {
                    localArtist.copy(
                        bookmarkedAt = localArtist.bookmarkedAt ?: System.currentTimeMillis(),
                        thumbnailUrl = PropUtils.retainIfModified(localArtist.thumbnailUrl, remoteArtist.thumbnail?.url) ?: localArtist.thumbnailUrl,
                        isYoutubeArtist = true
                    ).let( Database.artistTable::update )
                }
            }

            Database.artistTable
                    .allFollowing()
                    .first()
                    .filter { artist ->
                        artist.isYoutubeArtist && artist.id !in remoteKeys
                    }
                    .map { it.copy( isYoutubeArtist = false, bookmarkedAt = null ) }
                    .forEach( Database.artistTable::update )
        }
        result.onFailure {
            Timber.tag("SyncYTMusicUtils").e("Error importing YTM subscribed artists channels: ${it.stackTraceToString()}")
            if (showDetails) Toaster.e(R.string.syncing_failed)
        }
        if (result.isSuccess) {
            if (showDetails) Toaster.done()
            setLastSyncTime()
        }
        result.isSuccess
}

suspend fun importYTMLikedAlbums(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("importYTMLikedAlbums isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsed()) return@withContext false
    val autosyncAlbums = appContext().preferences.getBoolean(autosyncAlbumsKey, false)
    if (!autosyncAlbums && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

        if (showDetails) Toaster.i( R.string.syncing, Toast.LENGTH_LONG )

        val result = Innertube.library("FEmusic_liked_albums").completed().onSuccess { page ->

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
                        .let( Database.albumTable::updateReplace )
                }
            }

            Database.albumTable
                    .all()
                    .first()
                    .filter { album ->
                        album.isYoutubeAlbum && album.id !in remoteKeys
                    }
                    .map { it.copy( isYoutubeAlbum = false, bookmarkedAt = null ) }
                    .also( Database.albumTable::updateReplace )
        }
        result.onFailure {
            Timber.tag("SyncYTMusicUtils").e("Error importing YTM liked albums: ${it.stackTraceToString()}")
            if (showDetails) Toaster.e(R.string.syncing_failed)
        }
        if (result.isSuccess) {
            if (showDetails) Toaster.done()
            setLastSyncTime()
        }
        result.isSuccess
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
    YtMusic.removeFromPlaylist( playlistBrowseId, songId, setVideoId )
    return true
}

suspend fun importYTMLikedSongs(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("importYTMLikedSongs isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsed()) return@withContext false
    val autosyncLikes = appContext().preferences.getBoolean(autosyncLikesKey, false)
    if (!autosyncLikes && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.syncing, Toast.LENGTH_LONG )

    val result = YtMusic.getPlaylist(playlistId = "LM").completed().onSuccess { page ->
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

        Timber.tag("SyncYTMusicUtils").d("importYTMLikedSongs completed: ${ytmLikedSongs.size} songs liked")
        if (showDetails) Toaster.done()
        setLastSyncTime()
    }
    result.onFailure {
        Timber.tag("SyncYTMusicUtils").e(it, "importYTMLikedSongs failed")
        if (showDetails) Toaster.e(R.string.syncing_failed)
    }
    result.isSuccess
}

suspend fun removeYTMLikedSongs(): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("removeYTMLikedSongs called")
    if (!isYouTubeSyncEnabled()) return@withContext false

    Toaster.i( R.string.syncing, Toast.LENGTH_LONG )

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
            Timber.tag("SyncYTMusicUtils").e(it, "removeYTMLikedSongs failed")
            Toaster.e(R.string.syncing_failed)
        }
        result.isSuccess
    }.getOrDefault(false)
}

suspend fun importYTMPlaylists(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("importYTMPlaylists isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsed()) return@withContext false
    val autosyncPlaylists = appContext().preferences.getBoolean(autosyncPlaylistsKey, false)
    if (!autosyncPlaylists && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.syncing, Toast.LENGTH_LONG )

    val result = Innertube.library("FEmusic_liked_playlists").completed().onSuccess { page ->
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

            val playlistId: Long
            if (existingPlaylist == null) {
                val newPlaylist = Playlist(
                    name = remotePlaylist.title ?: "Unknown Playlist",
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
                        name = remotePlaylist.title ?: existingPlaylist.name,
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

        Timber.tag("SyncYTMusicUtils").d("importYTMPlaylists completed: ${ytmPlaylists.size} playlists synced")
        if (showDetails) Toaster.done()
        setLastSyncTime()
    }
    result.onFailure {
        Timber.tag("SyncYTMusicUtils").e(it, "importYTMPlaylists failed")
        if (showDetails) Toaster.e(R.string.syncing_failed)
    }
    result.isSuccess
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

suspend fun importYTMLibrarySongs(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("importYTMLibrarySongs isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsed()) return@withContext false
    val syncLibrarySongs = appContext().preferences.getBoolean(syncImportLibrarySongsKey, false)
    if (!syncLibrarySongs && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.syncing, Toast.LENGTH_LONG )

    val result = Innertube.library("FEmusic_liked_videos").completed().onSuccess { page ->
        val ytmSongs = page.items.filterIsInstance<Innertube.SongItem>()
        Timber.tag("SyncYTMusicUtils").d("YTM library songs: ${ytmSongs.size}")

        ytmSongs.forEach { song ->
            Database.insertIgnore( song.asMediaItem )
            Timber.tag("SyncYTMusicUtils").d("Library song: ${song.title}")
        }

        Timber.tag("SyncYTMusicUtils").d("importYTMLibrarySongs completed: ${ytmSongs.size} songs imported")
        if (showDetails) Toaster.done()
        setLastSyncTime()
    }
    result.onFailure {
        Timber.tag("SyncYTMusicUtils").e(it, "importYTMLibrarySongs failed")
        if (showDetails) Toaster.e(R.string.syncing_failed)
    }
    result.isSuccess
}

suspend fun importYTMUploadedSongs(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("importYTMUploadedSongs isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsed()) return@withContext false
    val syncUploadedSongs = appContext().preferences.getBoolean(syncImportUploadedSongsKey, false)
    if (!syncUploadedSongs && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.syncing, Toast.LENGTH_LONG )

    val result = Innertube.library("FEmusic_library_privately_owned_tracks").completed().onSuccess { page ->
        val ytmSongs = page.items.filterIsInstance<Innertube.SongItem>()
        Timber.tag("SyncYTMusicUtils").d("YTM uploaded songs: ${ytmSongs.size}")

        ytmSongs.forEach { song ->
            Database.insertIgnore( song.asMediaItem )
            Timber.tag("SyncYTMusicUtils").d("Uploaded song: ${song.title}")
        }

        Timber.tag("SyncYTMusicUtils").d("importYTMUploadedSongs completed: ${ytmSongs.size} songs imported")
        if (showDetails) Toaster.done()
        setLastSyncTime()
    }
    result.onFailure {
        Timber.tag("SyncYTMusicUtils").e(it, "importYTMUploadedSongs failed")
        if (showDetails) Toaster.e(R.string.syncing_failed)
    }
    result.isSuccess
}

suspend fun importYTMUploadedAlbums(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("importYTMUploadedAlbums isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsed()) return@withContext false
    val syncUploadedAlbums = appContext().preferences.getBoolean(syncImportUploadedAlbumsKey, false)
    if (!syncUploadedAlbums && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.syncing, Toast.LENGTH_LONG )

    val result = Innertube.library("FEmusic_library_privately_owned_releases").completed().onSuccess { page ->
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
                ).let( Database.albumTable::updateReplace )
            }
        }

        Database.albumTable
                .all()
                .first()
                .filter { album ->
                    album.isYoutubeAlbum && album.id !in remoteKeys
                }
                .map { it.copy( isYoutubeAlbum = false ) }
                .also( Database.albumTable::updateReplace )

        Timber.tag("SyncYTMusicUtils").d("importYTMUploadedAlbums completed: ${ytmAlbums.size} albums imported")
        if (showDetails) Toaster.done()
        setLastSyncTime()
    }
    result.onFailure {
        Timber.tag("SyncYTMusicUtils").e(it, "importYTMUploadedAlbums failed")
        if (showDetails) Toaster.e(R.string.syncing_failed)
    }
    result.isSuccess
}

// ============ EPISODES FOR LATER (VLSE) ============

suspend fun importYTMEpisodesForLater(force: Boolean = false, showDetails: Boolean = true): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("importYTMEpisodesForLater isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canImportFromYTM()) return@withContext false
    if (!force && !isSyncCooldownElapsed()) return@withContext false
    val syncEpisodes = appContext().preferences.getBoolean(syncImportEpisodesKey, false)
    if (!syncEpisodes && !force) return@withContext false
    if (!force && isBackgroundGuardActive()) return@withContext false

    if (showDetails) Toaster.i( R.string.syncing, Toast.LENGTH_LONG )

    val result = YtMusic.getPlaylist(playlistId = "SE").completed().onSuccess { page ->
        val episodes = page.songs
        Timber.tag("SyncYTMusicUtils").d("YTM episodes for later: ${episodes.size}")

        episodes.forEach { episode ->
            Database.insertIgnore( episode.asMediaItem )
            Timber.tag("SyncYTMusicUtils").d("Episode: ${episode.title}")
        }

        Timber.tag("SyncYTMusicUtils").d("importYTMEpisodesForLater completed: ${episodes.size} episodes")
        if (showDetails) Toaster.done()
        setLastSyncTime()
    }
    result.onFailure {
        Timber.tag("SyncYTMusicUtils").e(it, "importYTMEpisodesForLater failed")
        if (showDetails) Toaster.e(R.string.syncing_failed)
    }
    result.isSuccess
}

fun getLastSyncTime(): Long {
    return appContext().preferences.getLong("lastSyncTime", 0)
}

fun setLastSyncTime() {
    appContext().preferences.edit().putLong("lastSyncTime", System.currentTimeMillis()).apply()
}

fun isSyncCooldownElapsed(): Boolean {
    val cooldownMinutes = appContext().preferences.getInt(syncCooldownKey, 30)
    val lastSync = getLastSyncTime()
    val cooldownMs = cooldownMinutes * 60 * 1000L
    return System.currentTimeMillis() - lastSync > cooldownMs
}

// ============ SYNC QUEUE ============

sealed class SyncOperation {
    // Imports (YTM → App)
    data object FullSync : SyncOperation()
    data object LikedSongs : SyncOperation()
    data object LibrarySongs : SyncOperation()
    data object UploadedSongs : SyncOperation()
    data object LikedAlbums : SyncOperation()
    data object UploadedAlbums : SyncOperation()
    data object ArtistSubscriptions : SyncOperation()
    data object SavedPlaylists : SyncOperation()
    data object EpisodesForLater : SyncOperation()
    data class SinglePlaylist(val browseId: String, val playlistId: Long) : SyncOperation()
    data object CleanupDuplicates : SyncOperation()

    // Pushes (App → YTM)
    data class PushLikedSongs(val force: Boolean = false) : SyncOperation()
    data class PushAlbumBookmarks(val force: Boolean = false) : SyncOperation()
    data class PushArtistFollows(val force: Boolean = false) : SyncOperation()
    data class PushPlaylists(val force: Boolean = false) : SyncOperation()
    data class PushEpisodes(val force: Boolean = false) : SyncOperation()
}

data class SyncStatus(
    val isRunning: Boolean = false,
    val currentOperation: String = "",
    val likedSongs: String = "idle",
    val librarySongs: String = "idle",
    val uploadedSongs: String = "idle",
    val likedAlbums: String = "idle",
    val uploadedAlbums: String = "idle",
    val artists: String = "idle",
    val playlists: String = "idle",
    val episodes: String = "idle",
    val pushLikedSongs: String = "idle",
    val pushAlbumBookmarks: String = "idle",
    val pushArtistFollows: String = "idle",
    val pushPlaylists: String = "idle",
    val pushEpisodes: String = "idle"
)

private val _syncStatus = MutableStateFlow(SyncStatus())
val syncStatus: StateFlow<SyncStatus> = _syncStatus

private var syncChannel = Channel<SyncOperation>(Channel.BUFFERED)
private val syncJob = kotlinx.coroutines.SupervisorJob()
private val syncScope = CoroutineScope(Dispatchers.IO + syncJob)
private var processingJob: kotlinx.coroutines.Job? = null
private val syncMutex = Mutex()

// Coalescing: track queued operation keys to prevent duplicates
private val queuedOperationKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

private fun SyncOperation.coalescingKey(): String? = when (this) {
    is SyncOperation.FullSync -> "full"
    is SyncOperation.LikedSongs -> "import:likedSongs"
    is SyncOperation.LibrarySongs -> "import:librarySongs"
    is SyncOperation.UploadedSongs -> "import:uploadedSongs"
    is SyncOperation.LikedAlbums -> "import:likedAlbums"
    is SyncOperation.UploadedAlbums -> "import:uploadedAlbums"
    is SyncOperation.ArtistSubscriptions -> "import:artists"
    is SyncOperation.SavedPlaylists -> "import:playlists"
    is SyncOperation.EpisodesForLater -> "import:episodes"
    is SyncOperation.SinglePlaylist -> "playlist:${browseId}"
    is SyncOperation.CleanupDuplicates -> "cleanup"
    is SyncOperation.PushLikedSongs -> "push:likedSongs"
    is SyncOperation.PushAlbumBookmarks -> "push:albumBookmarks"
    is SyncOperation.PushArtistFollows -> "push:artistFollows"
    is SyncOperation.PushPlaylists -> "push:playlists"
    is SyncOperation.PushEpisodes -> "push:episodes"
}

private suspend fun <T> withRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000L,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(maxRetries - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.tag("SyncYTMusicUtils").w("Retry ${attempt + 1}/$maxRetries after ${currentDelay}ms: ${e.message}")
            kotlinx.coroutines.delay(currentDelay)
            currentDelay *= 2
        }
    }
    return block()
}

private fun isCoveredByFullSync(operation: SyncOperation): Boolean = when (operation) {
    is SyncOperation.FullSync, is SyncOperation.CleanupDuplicates -> false
    is SyncOperation.LikedSongs, is SyncOperation.LibrarySongs, is SyncOperation.UploadedSongs,
    is SyncOperation.LikedAlbums, is SyncOperation.UploadedAlbums,
    is SyncOperation.ArtistSubscriptions, is SyncOperation.SavedPlaylists,
    is SyncOperation.EpisodesForLater, is SyncOperation.SinglePlaylist -> "full" in queuedOperationKeys
    else -> false
}

private fun startSyncQueue() {
    if (processingJob?.isActive == true) return
    processingJob = syncScope.launch {
        for (operation in syncChannel) {
            // Remove from coalescing set
            operation.coalescingKey()?.let(queuedOperationKeys::remove)

            // Skip individual imports if FullSync is already queued/running
            if (isCoveredByFullSync(operation)) {
                Timber.tag("SyncYTMusicUtils").d("Skipping $operation — covered by FullSync")
                continue
            }

            syncMutex.lock()
            try {
                _syncStatus.value = _syncStatus.value.copy(isRunning = true, currentOperation = operation::class.simpleName ?: "")
                executeOperation(operation)
            } catch (e: Exception) {
                Timber.tag("SyncYTMusicUtils").e(e, "Sync operation failed: $operation")
            } finally {
                _syncStatus.value = _syncStatus.value.copy(isRunning = false, currentOperation = "")
                syncMutex.unlock()
            }
        }
    }
}

fun queueSync(operation: SyncOperation) {
    val key = operation.coalescingKey()
    if (key != null && !queuedOperationKeys.add(key)) {
        Timber.tag("SyncYTMusicUtils").d("Skipping duplicate sync operation: $operation")
        return // Already queued
    }
    startSyncQueue()
    syncChannel.trySend(operation)
}

fun cancelAllSyncs() {
    processingJob?.cancel()
    processingJob = null
    queuedOperationKeys.clear()
    syncChannel.cancel()
    syncChannel = Channel(Channel.BUFFERED)
    _syncStatus.value = SyncStatus()
    Timber.tag("SyncYTMusicUtils").d("All sync operations cancelled")
}

private suspend fun executeOperation(operation: SyncOperation) {
    when (operation) {
        is SyncOperation.FullSync -> {
            _syncStatus.value = _syncStatus.value.copy(likedSongs = "syncing")
            withRetry { importYTMLikedSongs(force = true) }
            _syncStatus.value = _syncStatus.value.copy(likedSongs = "idle", librarySongs = "syncing")
            withRetry { importYTMLibrarySongs(force = true) }
            _syncStatus.value = _syncStatus.value.copy(librarySongs = "idle", uploadedSongs = "syncing")
            withRetry { importYTMUploadedSongs(force = true) }
            _syncStatus.value = _syncStatus.value.copy(uploadedSongs = "idle", likedAlbums = "syncing")
            withRetry { importYTMLikedAlbums(force = true) }
            _syncStatus.value = _syncStatus.value.copy(likedAlbums = "idle", uploadedAlbums = "syncing")
            withRetry { importYTMUploadedAlbums(force = true) }
            _syncStatus.value = _syncStatus.value.copy(uploadedAlbums = "idle", artists = "syncing")
            withRetry { importYTMSubscribedChannels(force = true) }
            _syncStatus.value = _syncStatus.value.copy(artists = "idle", playlists = "syncing")
            withRetry { importYTMPlaylists(force = true) }
            _syncStatus.value = _syncStatus.value.copy(playlists = "idle", episodes = "syncing")
            withRetry { importYTMEpisodesForLater(force = true) }
            _syncStatus.value = _syncStatus.value.copy(episodes = "idle")
            withRetry { cleanupDuplicatePlaylists() }
            setLastSyncTime()
        }
        is SyncOperation.LikedSongs -> {
            _syncStatus.value = _syncStatus.value.copy(likedSongs = "syncing")
            withRetry { importYTMLikedSongs() }
            _syncStatus.value = _syncStatus.value.copy(likedSongs = "idle")
        }
        is SyncOperation.LibrarySongs -> {
            _syncStatus.value = _syncStatus.value.copy(librarySongs = "syncing")
            withRetry { importYTMLibrarySongs() }
            _syncStatus.value = _syncStatus.value.copy(librarySongs = "idle")
        }
        is SyncOperation.UploadedSongs -> {
            _syncStatus.value = _syncStatus.value.copy(uploadedSongs = "syncing")
            withRetry { importYTMUploadedSongs() }
            _syncStatus.value = _syncStatus.value.copy(uploadedSongs = "idle")
        }
        is SyncOperation.LikedAlbums -> {
            _syncStatus.value = _syncStatus.value.copy(likedAlbums = "syncing")
            withRetry { importYTMLikedAlbums() }
            _syncStatus.value = _syncStatus.value.copy(likedAlbums = "idle")
        }
        is SyncOperation.UploadedAlbums -> {
            _syncStatus.value = _syncStatus.value.copy(uploadedAlbums = "syncing")
            withRetry { importYTMUploadedAlbums() }
            _syncStatus.value = _syncStatus.value.copy(uploadedAlbums = "idle")
        }
        is SyncOperation.ArtistSubscriptions -> {
            _syncStatus.value = _syncStatus.value.copy(artists = "syncing")
            withRetry { importYTMSubscribedChannels() }
            _syncStatus.value = _syncStatus.value.copy(artists = "idle")
        }
        is SyncOperation.SavedPlaylists -> {
            _syncStatus.value = _syncStatus.value.copy(playlists = "syncing")
            withRetry { importYTMPlaylists() }
            _syncStatus.value = _syncStatus.value.copy(playlists = "idle")
        }
        is SyncOperation.EpisodesForLater -> {
            _syncStatus.value = _syncStatus.value.copy(episodes = "syncing")
            withRetry { importYTMEpisodesForLater() }
            _syncStatus.value = _syncStatus.value.copy(episodes = "idle")
        }
        is SyncOperation.SinglePlaylist -> {
            val playlist = Database.playlistTable.findById(operation.playlistId).first()
            if (playlist != null) {
                withRetry { ytmPrivatePlaylistSync(playlist, operation.playlistId) }
            }
        }
        is SyncOperation.CleanupDuplicates -> {
            cleanupDuplicatePlaylists()
        }

        // Push operations (App → YTM) — respect direction + individual toggles
        is SyncOperation.PushLikedSongs -> {
            _syncStatus.value = _syncStatus.value.copy(pushLikedSongs = "syncing")
            withRetry { pushYTMLikedSongs(force = operation.force) }
            _syncStatus.value = _syncStatus.value.copy(pushLikedSongs = "idle")
        }
        is SyncOperation.PushAlbumBookmarks -> {
            _syncStatus.value = _syncStatus.value.copy(pushAlbumBookmarks = "syncing")
            withRetry { pushYTMAlbumBookmarks(force = operation.force) }
            _syncStatus.value = _syncStatus.value.copy(pushAlbumBookmarks = "idle")
        }
        is SyncOperation.PushArtistFollows -> {
            _syncStatus.value = _syncStatus.value.copy(pushArtistFollows = "syncing")
            withRetry { pushYTMArtistFollows(force = operation.force) }
            _syncStatus.value = _syncStatus.value.copy(pushArtistFollows = "idle")
        }
        is SyncOperation.PushPlaylists -> {
            _syncStatus.value = _syncStatus.value.copy(pushPlaylists = "syncing")
            withRetry { pushYTMPlaylists(force = operation.force) }
            _syncStatus.value = _syncStatus.value.copy(pushPlaylists = "idle")
        }
        is SyncOperation.PushEpisodes -> {
            _syncStatus.value = _syncStatus.value.copy(pushEpisodes = "syncing")
            withRetry { pushYTMSavedEpisodes(force = operation.force) }
            _syncStatus.value = _syncStatus.value.copy(pushEpisodes = "idle")
        }
    }
}

// ============ DUPLICATE PLAYLIST CLEANUP ============

suspend fun cleanupDuplicatePlaylists() = withContext(Dispatchers.IO) {
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

// ============ PODCAST PUSH (SAVE/UNSAVE) ============

suspend fun savePodcast(podcastId: String): Boolean = withContext(Dispatchers.IO) {
    if (!isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext())) return@withContext false
    val result = runCatching {
        YtMusic.likePlaylistOrAlbum(podcastId)
    }
    if (result.isSuccess) Toaster.s( R.string.added_to_favorites )
    else Toaster.e( R.string.syncing_failed )
    result.isSuccess
}

suspend fun unsavePodcast(podcastId: String): Boolean = withContext(Dispatchers.IO) {
    if (!isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext())) return@withContext false
    val result = runCatching {
        YtMusic.removelikePlaylistOrAlbum(podcastId)
    }
    if (result.isSuccess) Toaster.s( R.string.removed_from_favorites )
    else Toaster.e( R.string.syncing_failed )
    result.isSuccess
}

// ============ EPISODE PUSH (ADD/REMOVE FROM SE) ============

suspend fun addEpisodeToSavedEpisodes(videoId: String): Boolean = withContext(Dispatchers.IO) {
    if (!isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext())) return@withContext false
    val result = runCatching {
        PlaylistEditThrottle.throttle("SE")
        YtMusic.addToPlaylist("SE", videoId)
    }
    if (result.isSuccess) Toaster.s( R.string.added_to_favorites )
    else Toaster.e( R.string.syncing_failed )
    result.isSuccess
}

suspend fun removeEpisodeFromSavedEpisodes(videoId: String, setVideoId: String? = null): Boolean = withContext(Dispatchers.IO) {
    if (!isYouTubeSyncEnabled() || !canPushToYTM() || !isNetworkConnected(appContext())) return@withContext false
    val result = runCatching {
        PlaylistEditThrottle.throttle("SE")
        YtMusic.removeFromPlaylist("SE", videoId, setVideoId)
    }
    if (result.isSuccess) Toaster.s( R.string.removed_from_favorites )
    else Toaster.e( R.string.syncing_failed )
    result.isSuccess
}

// ============ BULK PUSH TO YTM ============

suspend fun pushYTMLikedSongs(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
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
    Timber.tag("SyncYTMusicUtils").d("pushYTMLikedSongs completed: $success success, $failed failed")
    failed == 0
}

suspend fun pushYTMAlbumBookmarks(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
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
    Timber.tag("SyncYTMusicUtils").d("pushYTMAlbumBookmarks completed: $success success, $failed failed")
    failed == 0
}

suspend fun pushYTMArtistFollows(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("pushYTMArtistFollows isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()}")
    if (!isYouTubeSyncEnabled()) return@withContext false
    if (!canPushToYTM()) return@withContext false
    val pushArtistFollow = appContext().preferences.getBoolean(syncPushArtistFollowKey, false)
    if (!pushArtistFollow && !force) return@withContext false
    if (!isNetworkConnected(appContext())) return@withContext false

    val followedArtists = Database.artistTable.allFollowing().first()
    Timber.tag("SyncYTMusicUtils").d("pushYTMArtistFollows: ${followedArtists.size} followed artists to push")

    var success = 0
    var failed = 0
    followedArtists.forEach { artist ->
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
    Timber.tag("SyncYTMusicUtils").d("pushYTMArtistFollows completed: $success success, $failed failed")
    failed == 0
}

suspend fun pushYTMPlaylists(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
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
    Timber.tag("SyncYTMusicUtils").d("pushYTMPlaylists completed: $success success, $failed failed")
    failed == 0
}

// ============ PUSH SAVED EPISODES TO YTM ============

suspend fun pushYTMSavedEpisodes(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
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
    Timber.tag("SyncYTMusicUtils").d("pushYTMSavedEpisodes completed: $success success, $failed failed")
    failed == 0
}

// ============ CLEAR ALL SYNCED DATA ON LOGOUT ============

suspend fun clearAllSyncedData(): Boolean = withContext(Dispatchers.IO) {
    Timber.tag("SyncYTMusicUtils").d("clearAllSyncedData started")

    // Cancel any running sync operations
    cancelAllSyncs()

    var hadErrors = false

    // Clear all artist YouTube flags
    runCatching {
        Database.artistTable.allFollowing().first().forEach { artist ->
            if (artist.isYoutubeArtist) {
                Database.artistTable.update(artist.copy(isYoutubeArtist = false, bookmarkedAt = null))
            }
        }
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

