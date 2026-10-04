package app.n_zik.android.bridge.library

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.bridge.AlbumDto
import app.n_zik.android.bridge.ArtistDto
import app.n_zik.android.bridge.PlaylistDto
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.client.NetworkClientFactory
import app.n_zik.android.core.profiles.cacheDatabaseProvider
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.core.rewind.RewindPlaylists.rewindDisplayName
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.utils.DataStoreUtils
import app.it.fast4x.rimusic.PINNED_PREFIX
import app.it.fast4x.rimusic.enums.AlbumSortBy
import app.it.fast4x.rimusic.enums.ArtistSortBy
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.enums.StatisticsType
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsCustomValueKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.mediaCacheLocation
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
 * Read-only access to the phone's library for the PC bridge (contract §10, since 1.6): every list
 * is returned already sorted the way the phone's own screens sort it (the sort needs the phone's
 * database); search and pagination belong to [LibraryQueries]. A `null` song list means the
 * playlist / album / artist is unknown (`404`).
 */
internal interface LibraryProvider {
    suspend fun songs(filter: SongFilter, sort: SongSort, reverse: Boolean, period: TopPeriod? = null): List<LibrarySong>

    suspend fun playlists(filter: PlaylistsFilter, sort: PlaylistSort, reverse: Boolean): List<PlaylistDto>
    suspend fun playlistSongs(playlistId: Long, sort: PlaylistSongSort, reverse: Boolean): List<TrackDto>?
    suspend fun albums(filter: CollectionFilter, sort: AlbumSort, reverse: Boolean): List<AlbumDto>
    suspend fun albumSongs(albumId: String): List<TrackDto>?
    suspend fun artists(filter: CollectionFilter, sort: ArtistSort, reverse: Boolean): List<ArtistDto>
    suspend fun artistSongs(artistId: String): List<TrackDto>?
    suspend fun trackArtwork(trackId: String, size: Int): ArtworkResult
    suspend fun albumArtwork(albumId: String, size: Int): ArtworkResult
    suspend fun artistArtwork(artistId: String, size: Int): ArtworkResult

    companion object {
        /** Empty library: the default of a server without a phone behind it (tests). */
        val EMPTY: LibraryProvider = object : LibraryProvider {
            override suspend fun songs(filter: SongFilter, sort: SongSort, reverse: Boolean, period: TopPeriod?): List<LibrarySong> = emptyList()
            override suspend fun playlists(filter: PlaylistsFilter, sort: PlaylistSort, reverse: Boolean): List<PlaylistDto> = emptyList()
            override suspend fun playlistSongs(playlistId: Long, sort: PlaylistSongSort, reverse: Boolean): List<TrackDto>? = null
            override suspend fun albums(filter: CollectionFilter, sort: AlbumSort, reverse: Boolean): List<AlbumDto> = emptyList()
            override suspend fun albumSongs(albumId: String): List<TrackDto>? = null
            override suspend fun artists(filter: CollectionFilter, sort: ArtistSort, reverse: Boolean): List<ArtistDto> = emptyList()
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

    /**
     * The songs of the phone's home tab behind [filter] (contract §10), each sorted the way the
     * phone's own `HomeSongs.kt` sorts it: `sortAll` for the all / local / downloaded tabs,
     * `sortFavorites` for the liked tab, `allDisliked` for the disliked tab (its fixed order), the
     * format table for the cached tab, the most-played events for the top tab. As on the phone, the
     * `Downloaded` sort keeps the tab's base order and puts the downloaded songs first (or last).
     */
    override suspend fun songs(filter: SongFilter, sort: SongSort, reverse: Boolean, period: TopPeriod?): List<LibrarySong> = withContext(NzikDispatchers.DATA) {
        val downloaded = completedDownloads()
        val sortBy = sort.songSortBy()
        val shown = when (filter) {
            SongFilter.ALL -> when (sortBy) {
                // The phone's tabs use the title order as the base of the "Downloaded" sort
                SongSortBy.Downloaded -> Database.songTable.sortAll(SongSortBy.Title, SortOrder.Ascending, excludeHidden = true).first()
                    .downloadedOrdered(downloaded, reverse)
                else -> Database.songTable.sortAll(sortBy, reverse.sortOrder(), excludeHidden = true).first()
            }

            SongFilter.LIKED -> when (sortBy) {
                SongSortBy.Downloaded -> Database.songTable.sortFavorites(SongSortBy.Title, SortOrder.Ascending).first()
                    .downloadedOrdered(downloaded, reverse)
                else -> Database.songTable.sortFavorites(sortBy, reverse.sortOrder()).first()
            }

            // The phone's Disliked tab lists its songs as-is (no sort of its own)
            SongFilter.DISLIKED -> Database.songTable.allDisliked().first()

            // The bridge's `local`: the phone's local songs, in the All tab's sort
            // (the phone's own "On device" tab is empty)
            SongFilter.LOCAL -> Database.songTable.sortAll(sortBy, reverse.sortOrder(), excludeHidden = true).first()
                .filter { it.id.startsWith(LOCAL_KEY_PREFIX, true) }

            // The phone's Downloaded tab (no hidden exclusion, like there)
            SongFilter.DOWNLOADED -> Database.songTable.sortAll(sortBy, reverse.sortOrder()).first()
                .filter { it.id in downloaded }

            SongFilter.OFFLINE -> {
                val cached = cachedSongIds()
                Database.formatTable.sortAllWithSongs(sortBy, reverse.sortOrder(), excludeHidden = true).first()
                    .map { it.song }.filter { it.id in cached }
            }

            SongFilter.TOP -> {
                val (from, limit) = topPlaylist(period)
                val top = Database.eventTable.findSongsMostPlayedBetween(from = from, limit = limit).first()
                if (sortBy == SongSortBy.Downloaded) top.downloadedOrdered(downloaded, reverse) else top
            }
        }
        LibraryMapping.librarySongs(shown, downloaded)
    }

    /**
     * The playlists of the phone's home tab behind [filter] (contract §10, since 1.6), each shown the
     * way the phone's own `HomeLibrary.kt` shows it: `All` keeps everything except the rewind and
     * pinned playlists the phone's own toggles hide, `Pinned` the pinned-prefix names, `Rewind` the
     * rewind names the phone's creation toggles and Month/Year/All filter allow, `Youtube` the
     * youtube-synced ones.
     */
    override suspend fun playlists(filter: PlaylistsFilter, sort: PlaylistSort, reverse: Boolean): List<PlaylistDto> = withContext(NzikDispatchers.DATA) {
        val previews = Database.playlistTable.sortPreviews(sort.playlistSortBy(), reverse.sortOrder()).first()
        val prefs = appContext.preferences
        val showPinned = prefs.getBoolean(showPinnedPlaylistsKey, true)
        val showRewind = prefs.getBoolean(showMonthlyPlaylistsKey, true)
        val shown = when (filter) {
            PlaylistsFilter.ALL -> previews.filter {
                (!RewindPlaylists.isRewind(it.playlist.name) || showRewind) &&
                    (!it.playlist.name.startsWith(PINNED_PREFIX, true) || showPinned)
            }

            PlaylistsFilter.PINNED -> previews.filter { it.playlist.name.startsWith(PINNED_PREFIX, true) }

            PlaylistsFilter.REWIND -> previews.filter {
                RewindPlaylists.isShown(
                    it.playlist.name,
                    DataStoreUtils.prefs(appContext).getBoolean(DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true),
                    DataStoreUtils.prefs(appContext).getBoolean(DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true),
                    prefs.getEnum(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, RewindPlaylists.Filter.Month),
                )
            }

            PlaylistsFilter.YOUTUBE -> previews.filter { it.playlist.isYoutubePlaylist }
        }
        val artworkTracks = LibraryMapping.artworkTracks(Database.songPlaylistMapTable.songsWithThumbnailDirect())
        LibraryMapping.playlists(shown, artworkTracks) { name -> appContext.rewindDisplayName(name) }
    }

    /**
     * The tracks of a local playlist with the phone's own sort (`sortSongs` of the local playlist
     * screen); as there, the `Downloaded` sort orders by title and puts the downloaded songs first
     * (or last). Absent `sort` keeps the position order (the phone's default).
     */
    override suspend fun playlistSongs(playlistId: Long, sort: PlaylistSongSort, reverse: Boolean): List<TrackDto>? = withContext(NzikDispatchers.DATA) {
        Database.playlistTable.findById(playlistId).first() ?: return@withContext null
        val downloaded = completedDownloads()
        val songs = if (sort == PlaylistSongSort.DOWNLOADED) {
            val base = Database.songPlaylistMapTable.sortSongs(playlistId, PlaylistSongSortBy.Title, SortOrder.Ascending).first()
            if (reverse) base.sortedBy { it.id in downloaded } else base.sortedByDescending { it.id in downloaded }
        } else {
            Database.songPlaylistMapTable.sortSongs(playlistId, sort.playlistSongSortBy(), reverse.sortOrder()).first()
        }
        songs.map { LibraryMapping.track(it, downloaded) }
    }

    override suspend fun albums(filter: CollectionFilter, sort: AlbumSort, reverse: Boolean): List<AlbumDto> = withContext(NzikDispatchers.DATA) {
        val albums = when (filter) {
            CollectionFilter.LIBRARY -> Database.albumTable.sortInLibrary(sort.albumSortBy(), reverse.sortOrder()).first()
            CollectionFilter.BOOKMARKED -> Database.albumTable.sortBookmarked(sort.albumSortBy(), reverse.sortOrder()).first()
            // The phone's Disliked tab lists its albums as-is (no sort of its own)
            CollectionFilter.DISLIKED -> Database.albumTable.allDisliked().first()
        }
        LibraryMapping.albums(albums, Database.songAlbumMapTable.songCountsDirect().associate { it.id to it.count })
    }

    override suspend fun albumSongs(albumId: String): List<TrackDto>? = withContext(NzikDispatchers.DATA) {
        Database.albumTable.findByIdDirect(albumId) ?: return@withContext null
        val downloaded = completedDownloads()
        Database.songAlbumMapTable.allSongsOfDirect(albumId).map { LibraryMapping.track(it, downloaded) }
    }

    override suspend fun artists(filter: CollectionFilter, sort: ArtistSort, reverse: Boolean): List<ArtistDto> = withContext(NzikDispatchers.DATA) {
        val artists = when (filter) {
            CollectionFilter.LIBRARY -> Database.artistTable.sortInLibrary(sort.artistSortBy(), reverse.sortOrder()).first()
            CollectionFilter.BOOKMARKED -> Database.artistTable.sortFollowing(sort.artistSortBy(), reverse.sortOrder()).first()
            // The phone's Disliked tab lists its artists as-is (no sort of its own)
            CollectionFilter.DISLIKED -> Database.artistTable.allDisliked().first()
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

    /**
     * The phone's "Downloaded" sort on top of a tab's base list (`HomeSongs.kt`): the downloaded songs
     * come first when the order is ascending, last when descending; the stable sort keeps the base order.
     */
    private fun List<Song>.downloadedOrdered(downloaded: Set<String>, reverse: Boolean): List<Song> =
        if (reverse) sortedBy { it.id in downloaded } else sortedByDescending { it.id in downloaded }

    /**
     * The window and the cap of the phone's Top tab (its own settings: the period and the item
     * count); a wire [period] overrides the phone's period, exactly like the phone's own
     * period selector.
     */
    private fun topPlaylist(period: TopPeriod?): Pair<Long, Int> {
        val prefs = appContext.preferences
        val periodType = period?.statisticsType() ?: prefs.getEnum(
            Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD.key,
            Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD.default,
        )
        val limit = prefs.getEnum(MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10`)
            .toInt(prefs.getInt(MaxTopPlaylistItemsCustomValueKey, 10))
        return periodType.timeStampInMillis() to limit
    }

    /** The phone's period behind a wire value, so the bridge tops out exactly like the phone's own screens. */
    private fun TopPeriod.statisticsType() = when (this) {
        TopPeriod.TODAY -> StatisticsType.Today
        TopPeriod.WEEK -> StatisticsType.OneWeek
        TopPeriod.MONTH -> StatisticsType.OneMonth
        TopPeriod.THREE_MONTHS -> StatisticsType.ThreeMonths
        TopPeriod.SIX_MONTHS -> StatisticsType.SixMonths
        TopPeriod.YEAR -> StatisticsType.OneYear
        TopPeriod.ALL_TIME -> StatisticsType.All
    }

    /**
     * The ids of the songs in the player's streaming cache, as the phone's "Cached" tab computes them:
     * a read-only [SimpleCache] on the active profile's cache directory and index (the player's own
     * cache — a temp cache, or none at all, leaves nothing to read).
     */
    private suspend fun cachedSongIds(): Set<String> = runCatching {
        val location = mediaCacheLocation(appContext)
        val dir = location.dir ?: return@runCatching emptySet()
        if (!dir.exists()) return@runCatching emptySet()
        val cache = SimpleCache(dir, NoOpCacheEvictor(), cacheDatabaseProvider(appContext, location.indexDbName))
        try {
            Database.formatTable.allWithSongs().first().mapNotNull { format ->
                val contentLength = format.format.contentLength ?: return@mapNotNull null
                if (cache.isCached(format.song.id, 0, contentLength)) format.song.id else null
            }.toSet()
        } finally {
            cache.release()
        }
    }.onFailure { Timber.tag(TAG).w(it, "Cache read unavailable") }.getOrDefault(emptySet())

    /** Ids of the online songs fully downloaded on the phone. */
    private fun completedDownloads(): Set<String> =
        runCatching {
            // The downloads map is only filled once the download manager exists
            MyDownloadHelper.getDownloadManager(appContext)
            MyDownloadHelper.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
        }.onFailure { Timber.tag(TAG).w(it, "Download states unavailable") }.getOrDefault(emptySet())

    /** The phone's sort behind a wire value, so the bridge sorts exactly like the phone's own screens. */
    private fun SongSort.songSortBy() = when (this) {
        SongSort.TITLE -> SongSortBy.Title
        SongSort.ARTIST -> SongSortBy.Artist
        SongSort.ALBUM -> SongSortBy.AlbumName
        SongSort.DURATION -> SongSortBy.Duration
        SongSort.PLAY_COUNT -> SongSortBy.PlayCount
        SongSort.PLAY_TIME -> SongSortBy.PlayTime
        SongSort.RELATIVE_PLAY_TIME -> SongSortBy.RelativePlayTime
        SongSort.DATE_ADDED -> SongSortBy.DateAdded
        SongSort.DATE_PLAYED -> SongSortBy.DatePlayed
        SongSort.DATE_LIKED -> SongSortBy.DateLiked
        SongSort.DOWNLOADED -> SongSortBy.Downloaded
        SongSort.CUSTOM -> SongSortBy.Custom
    }

    private fun PlaylistSongSort.playlistSongSortBy() = when (this) {
        PlaylistSongSort.TITLE -> PlaylistSongSortBy.Title
        PlaylistSongSort.ARTIST -> PlaylistSongSortBy.Artist
        PlaylistSongSort.ALBUM -> PlaylistSongSortBy.Album
        PlaylistSongSort.ARTIST_AND_ALBUM -> PlaylistSongSortBy.ArtistAndAlbum
        PlaylistSongSort.DURATION -> PlaylistSongSortBy.Duration
        PlaylistSongSort.PLAY_COUNT -> PlaylistSongSortBy.PlayCount
        PlaylistSongSort.PLAY_TIME -> PlaylistSongSortBy.PlayTime
        PlaylistSongSort.RELATIVE_PLAY_TIME -> PlaylistSongSortBy.RelativePlayTime
        PlaylistSongSort.DATE_ADDED -> PlaylistSongSortBy.DateAdded
        PlaylistSongSort.DATE_PLAYED -> PlaylistSongSortBy.DatePlayed
        PlaylistSongSort.DATE_LIKED -> PlaylistSongSortBy.DateLiked
        PlaylistSongSort.ALBUM_YEAR -> PlaylistSongSortBy.AlbumYear
        PlaylistSongSort.DOWNLOADED -> PlaylistSongSortBy.Downloaded
        PlaylistSongSort.CUSTOM -> PlaylistSongSortBy.Custom
    }

    private fun AlbumSort.albumSortBy() = when (this) {
        AlbumSort.TITLE -> AlbumSortBy.Title
        AlbumSort.ARTIST -> AlbumSortBy.Artist
        AlbumSort.SONGS -> AlbumSortBy.Songs
        AlbumSort.DURATION -> AlbumSortBy.Duration
        AlbumSort.PLAY_COUNT -> AlbumSortBy.PlayCount
        AlbumSort.LISTENING_TIME -> AlbumSortBy.ListeningTime
        AlbumSort.DATE_ADDED -> AlbumSortBy.DateAdded
        AlbumSort.YEAR -> AlbumSortBy.Year
        AlbumSort.CUSTOM -> AlbumSortBy.Custom
    }

    private fun ArtistSort.artistSortBy() = when (this) {
        ArtistSort.NAME -> ArtistSortBy.Name
        ArtistSort.PLAY_COUNT -> ArtistSortBy.PlayCount
        ArtistSort.LISTENING_TIME -> ArtistSortBy.ListeningTime
        ArtistSort.DATE_ADDED -> ArtistSortBy.DateAdded
        ArtistSort.CUSTOM -> ArtistSortBy.Custom
    }

    private fun PlaylistSort.playlistSortBy() = when (this) {
        PlaylistSort.NAME -> PlaylistSortBy.Name
        PlaylistSort.SONG_COUNT -> PlaylistSortBy.SongCount
        PlaylistSort.LISTENING_TIME -> PlaylistSortBy.ListeningTime
        PlaylistSort.PLAY_COUNT -> PlaylistSortBy.PlayCount
        PlaylistSort.DATE_ADDED -> PlaylistSortBy.DateAdded
        PlaylistSort.CUSTOM -> PlaylistSortBy.Custom
    }

    private fun Boolean.sortOrder() = if (this) SortOrder.Descending else SortOrder.Ascending
}
