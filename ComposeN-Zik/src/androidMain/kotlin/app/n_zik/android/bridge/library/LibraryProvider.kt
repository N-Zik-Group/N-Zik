package app.n_zik.android.bridge.library

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.bridge.AlbumDto
import app.n_zik.android.bridge.ArtistDto
import app.n_zik.android.bridge.PlaylistDto
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.core.rewind.RewindPlaylists.rewindDisplayName
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber

private const val TAG = "BridgeLibrary"

/** Outcome of an artwork lookup (contract §10). */
internal sealed interface ArtworkResult {
    class Image(val bytes: ByteArray, val contentType: String) : ArtworkResult

    /** No artwork, or unknown track / album / artist: `404`. */
    data object NotFound : ArtworkResult

    /** The online image could not be fetched: `502 AUDIO_UPSTREAM_FAILED`. */
    data object UpstreamFailed : ArtworkResult
}

/**
 * Read-only access to the phone's library for the PC bridge (contract §10). Lists are
 * returned unsorted: sorting and pagination belong to [LibraryQueries]. A `null` song list
 * means the playlist / album / artist is unknown (`404`).
 */
internal interface LibraryProvider {
    suspend fun songs(): List<LibrarySong>
    suspend fun playlists(): List<PlaylistDto>
    suspend fun playlistSongs(playlistId: Long): List<TrackDto>?
    suspend fun albums(filter: CollectionFilter): List<AlbumDto>
    suspend fun albumSongs(albumId: String): List<TrackDto>?
    suspend fun artists(filter: CollectionFilter): List<ArtistDto>
    suspend fun artistSongs(artistId: String): List<TrackDto>?
    suspend fun trackArtwork(trackId: String, size: Int): ArtworkResult
    suspend fun albumArtwork(albumId: String, size: Int): ArtworkResult
    suspend fun artistArtwork(artistId: String, size: Int): ArtworkResult

    companion object {
        /** Empty library: the default of a server without a phone behind it (tests). */
        val EMPTY: LibraryProvider = object : LibraryProvider {
            override suspend fun songs(): List<LibrarySong> = emptyList()
            override suspend fun playlists(): List<PlaylistDto> = emptyList()
            override suspend fun playlistSongs(playlistId: Long): List<TrackDto>? = null
            override suspend fun albums(filter: CollectionFilter): List<AlbumDto> = emptyList()
            override suspend fun albumSongs(albumId: String): List<TrackDto>? = null
            override suspend fun artists(filter: CollectionFilter): List<ArtistDto> = emptyList()
            override suspend fun artistSongs(artistId: String): List<TrackDto>? = null
            override suspend fun trackArtwork(trackId: String, size: Int): ArtworkResult = ArtworkResult.NotFound
            override suspend fun albumArtwork(albumId: String, size: Int): ArtworkResult = ArtworkResult.NotFound
            override suspend fun artistArtwork(artistId: String, size: Int): ArtworkResult = ArtworkResult.NotFound
        }
    }
}

/**
 * [LibraryProvider] over the local Room database, read on [NzikDispatchers.DATA]. Nothing
 * is written. The only network access is the relay of an online artwork ([ArtworkRelay]).
 * Visibility and mapping rules live in [LibraryMapping].
 */
internal class DatabaseLibraryProvider(
    context: Context,
    private val httpClient: () -> OkHttpClient = NetworkClientFactory::getClient,
) : LibraryProvider {
    private val appContext = context.applicationContext
    private val artworkRelay = ArtworkRelay(httpClient, ::openLocal)

    /** Same list as the phone's Songs tab: never-played and disliked songs are left out. */
    override suspend fun songs(): List<LibrarySong> = withContext(NzikDispatchers.DATA) {
        LibraryMapping.librarySongs(Database.songTable.all(excludeHidden = true).first(), completedDownloads())
    }

    override suspend fun playlists(): List<PlaylistDto> = withContext(NzikDispatchers.DATA) {
        val artworkTracks = LibraryMapping.artworkTracks(Database.songPlaylistMapTable.songsWithThumbnailDirect())
        LibraryMapping.playlists(Database.playlistTable.allAsPreview().first(), artworkTracks) { name ->
            appContext.rewindDisplayName(name)
        }
    }

    override suspend fun playlistSongs(playlistId: Long): List<TrackDto>? = withContext(NzikDispatchers.DATA) {
        Database.playlistTable.findById(playlistId).first() ?: return@withContext null
        val downloaded = completedDownloads()
        Database.songPlaylistMapTable.songsByPositionDirect(playlistId).map { LibraryMapping.track(it, downloaded) }
    }

    override suspend fun albums(filter: CollectionFilter): List<AlbumDto> = withContext(NzikDispatchers.DATA) {
        val albums = when (filter) {
            CollectionFilter.LIBRARY -> Database.albumTable.allInLibrary().first()
            CollectionFilter.BOOKMARKED -> Database.albumTable.allBookmarked().first()
        }
        LibraryMapping.albums(albums, Database.songAlbumMapTable.songCountsDirect().associate { it.id to it.count })
    }

    override suspend fun albumSongs(albumId: String): List<TrackDto>? = withContext(NzikDispatchers.DATA) {
        Database.albumTable.findByIdDirect(albumId) ?: return@withContext null
        val downloaded = completedDownloads()
        Database.songAlbumMapTable.allSongsOfDirect(albumId).map { LibraryMapping.track(it, downloaded) }
    }

    override suspend fun artists(filter: CollectionFilter): List<ArtistDto> = withContext(NzikDispatchers.DATA) {
        val artists = when (filter) {
            CollectionFilter.LIBRARY -> Database.artistTable.allInLibrary().first()
            CollectionFilter.BOOKMARKED -> Database.artistTable.allFollowing().first()
        }
        LibraryMapping.artists(artists, Database.songArtistMapTable.songCountsDirect().associate { it.id to it.count })
    }

    override suspend fun artistSongs(artistId: String): List<TrackDto>? = withContext(NzikDispatchers.DATA) {
        Database.artistTable.findByIdDirect(artistId) ?: return@withContext null
        val downloaded = completedDownloads()
        Database.songArtistMapTable.allSongsByDirect(artistId).map { LibraryMapping.track(it, downloaded) }
    }

    override suspend fun trackArtwork(trackId: String, size: Int): ArtworkResult =
        artwork(size) { Database.songTable.findByIdDirect(trackId)?.thumbnailUrl }

    override suspend fun albumArtwork(albumId: String, size: Int): ArtworkResult =
        artwork(size) { Database.albumTable.findByIdDirect(albumId)?.thumbnailUrl }

    override suspend fun artistArtwork(artistId: String, size: Int): ArtworkResult =
        artwork(size) { Database.artistTable.findByIdDirect(artistId)?.thumbnailUrl }

    private suspend fun artwork(size: Int, thumbnailUrl: () -> String?): ArtworkResult =
        withContext(NzikDispatchers.DATA) { artworkRelay.relay(thumbnailUrl(), size) }

    /** Local file artwork (`content://…/albumart/…`), opened through the ContentResolver; the relay bounds the read. */
    private fun openLocal(url: String): LocalSource? {
        val uri = Uri.parse(url)
        val resolver = appContext.contentResolver
        return resolver.openInputStream(uri)?.let { LocalSource(it, resolver.getType(uri)) }
    }

    /** Ids of the online songs fully downloaded on the phone. */
    private fun completedDownloads(): Set<String> =
        runCatching {
            // The downloads map is only filled once the download manager exists
            MyDownloadHelper.getDownloadManager(appContext)
            MyDownloadHelper.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
        }.onFailure { Timber.tag(TAG).w(it, "Download states unavailable") }.getOrDefault(emptySet())
}
