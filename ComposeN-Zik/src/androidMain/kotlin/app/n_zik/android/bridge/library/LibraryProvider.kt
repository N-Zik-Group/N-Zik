package app.n_zik.android.bridge.library

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.R
import app.n_zik.android.bridge.AlbumDto
import app.n_zik.android.bridge.AlbumLike
import app.n_zik.android.bridge.ArtistDto
import app.n_zik.android.bridge.ArtistFollow
import app.n_zik.android.bridge.CacheSpaceDto
import app.n_zik.android.bridge.DislikeModeDto
import app.n_zik.android.bridge.LibraryCacheDto
import app.n_zik.android.bridge.PlaylistDto
import app.n_zik.android.bridge.RewindStateDto
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.TrackDownloadState
import app.n_zik.android.bridge.state.TrackLike
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
import app.it.fast4x.rimusic.enums.DislikeMode
import app.it.fast4x.rimusic.enums.DurationInMinutes
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.it.fast4x.rimusic.enums.MaxTopPlaylistItems
import app.it.fast4x.rimusic.enums.OnDeviceSongSortBy
import app.it.fast4x.rimusic.enums.StatisticsType
import app.it.fast4x.rimusic.enums.PlaylistSongSortBy
import app.it.fast4x.rimusic.enums.PlaylistSortBy
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsCustomValueKey
import app.it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.excludeSongsWithDurationLimitKey
import app.it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.homeSongsAllSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsCachedSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsDislikedSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsDownloadedSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsFavoritesSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsOnDeviceSortMenuOrderKey
import app.it.fast4x.rimusic.utils.homeSongsTopSortMenuOrderKey
import app.it.fast4x.rimusic.utils.includeLocalSongsKey
import org.json.JSONArray
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.putEnum
import app.it.fast4x.rimusic.utils.showMonthlyPlaylistsKey
import app.it.fast4x.rimusic.utils.showPinnedPlaylistsKey
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.services.mediaCacheLocation
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
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
 * Access to the phone's library for the PC bridge (contract §10, since 1.6; writes since 1.7):
 * every list is returned already sorted the way the phone's own screens sort it (the sort needs
 * the phone's database); search and pagination belong to [LibraryQueries]. A `null` song list
 * means the playlist / album / artist is unknown (`404`); the write answers are `null` for an
 * unknown target the same way.
 */
internal interface LibraryProvider {
    suspend fun songs(filter: SongFilter, sort: SongSort, reverse: Boolean, period: TopPeriod? = null): List<TrackDto>

    /**
     * Contract §10.1 (since 1.7.3, feature `library.sortMenu`): the effective content of the
     * phone's sort menu for the songs chip behind [filter] — its visible options, in its menu
     * order, as wire values of the `sort` parameter.
     */
    suspend fun songsSortMenu(filter: SongFilter): List<String>

    /**
     * Since 1.7.2: [rewindFilter] (`month` / `year` / `all`) is applied to the `rewind` listing
     * **and persisted** to the phone's own `rewindPlaylistsFilter` setting (the phone's Rewind
     * tab reflects it); `null` keeps the phone's current setting.
     */
    suspend fun playlists(filter: PlaylistsFilter, sort: PlaylistSort, reverse: Boolean, rewindFilter: RewindPlaylists.Filter? = null): List<PlaylistDto>
    suspend fun playlistSongs(playlistId: Long, sort: PlaylistSongSort, reverse: Boolean): List<TrackDto>?
    suspend fun albums(filter: CollectionFilter, sort: AlbumSort, reverse: Boolean): List<AlbumDto>
    suspend fun albumSongs(albumId: String): List<TrackDto>?
    suspend fun artists(filter: CollectionFilter, sort: ArtistSort, reverse: Boolean): List<ArtistDto>
    suspend fun artistSongs(artistId: String): List<TrackDto>?
    suspend fun trackArtwork(trackId: String, size: Int): ArtworkResult
    suspend fun albumArtwork(albumId: String, size: Int): ArtworkResult
    suspend fun artistArtwork(artistId: String, size: Int): ArtworkResult

    /**
     * Contract §10 (since 1.7.2): the phone's custom playlist cover (its
     * `thumbnail/playlist_<id>` file); `NotFound` when there is none (the client falls back to
     * its mosaic, the phone's own fallback).
     */
    suspend fun playlistArtwork(playlistId: Long): ArtworkResult

    /** Contract §10 (since 1.7.1): the phone's disk caches, used space vs configured cap (its Songs-tab bar). */
    suspend fun cacheSpace(): LibraryCacheDto

    /** Contract §10 (since 1.7.2): the phone's rewind creation toggles and its current Month/Year/All filter. */
    suspend fun rewindState(): RewindStateDto

    /** Contract §10 (since 1.7.2): the phone's `DislikeMode` (`Enabled`?) per collection. */
    suspend fun dislikeMode(): DislikeModeDto

    /** Contract §10.2 (since 1.7): the explicit like state, local Room only; `null` = unknown track (`404`). */
    suspend fun setSongLike(songId: String, state: TrackLike): TrackLike?
    /** Contract §10.2 (since 1.7): the explicit bookmark, local Room only; `null` = unknown album (`404`). */
    suspend fun setAlbumBookmark(albumId: String, bookmarked: Boolean): Boolean?
    /** Contract §10.2 (since 1.7.2): the explicit album tri-state, local Room only; `null` = unknown album (`404`). */
    suspend fun setAlbumLike(albumId: String, state: AlbumLike): AlbumLike?
    /** Contract §10.2 (since 1.7): the explicit follow state, local Room only; `null` = unknown artist (`404`). */
    suspend fun setArtistFollow(artistId: String, state: ArtistFollow): ArtistFollow?
    /** Contract §10.2 (since 1.7): the explicit pin, local Room only; `null` = unknown playlist (`404`). */
    suspend fun setPlaylistPin(playlistId: Long, pinned: Boolean): Boolean?
    /**
     * Contract §10.2 (since 1.7.2): the explicit bookmark (the phone's `isYoutubePlaylist`
     * column), local Room only; `null` = unknown playlist (`404`).
     */
    suspend fun setPlaylistBookmark(playlistId: Long, bookmarked: Boolean): Boolean?

    companion object {
        /** Empty library: the default of a server without a phone behind it (tests). */
        val EMPTY: LibraryProvider = object : LibraryProvider {
            override suspend fun songs(filter: SongFilter, sort: SongSort, reverse: Boolean, period: TopPeriod?): List<TrackDto> = emptyList()
            // Each chip's own native menu (top: its periods, local: its OnDevice options), like the
            // real provider — never the all-chip menu for every filter
            override suspend fun songsSortMenu(filter: SongFilter): List<String> =
                SongsSortMenu.effective(SongsSortMenu.chipKeys(filter), "") { false }
            override suspend fun playlists(filter: PlaylistsFilter, sort: PlaylistSort, reverse: Boolean, rewindFilter: RewindPlaylists.Filter?): List<PlaylistDto> = emptyList()
            override suspend fun playlistSongs(playlistId: Long, sort: PlaylistSongSort, reverse: Boolean): List<TrackDto>? = null
            override suspend fun albums(filter: CollectionFilter, sort: AlbumSort, reverse: Boolean): List<AlbumDto> = emptyList()
            override suspend fun albumSongs(albumId: String): List<TrackDto>? = null
            override suspend fun artists(filter: CollectionFilter, sort: ArtistSort, reverse: Boolean): List<ArtistDto> = emptyList()
            override suspend fun artistSongs(artistId: String): List<TrackDto>? = null
            override suspend fun trackArtwork(trackId: String, size: Int): ArtworkResult = ArtworkResult.NotFound
            override suspend fun albumArtwork(albumId: String, size: Int): ArtworkResult = ArtworkResult.NotFound
            override suspend fun artistArtwork(artistId: String, size: Int): ArtworkResult = ArtworkResult.NotFound
            override suspend fun playlistArtwork(playlistId: Long): ArtworkResult = ArtworkResult.NotFound
            override suspend fun cacheSpace(): LibraryCacheDto =
                LibraryCacheDto(CacheSpaceDto(0L, null), CacheSpaceDto(0L, null))
            override suspend fun rewindState(): RewindStateDto = RewindStateDto(true, true, "month")
            override suspend fun dislikeMode(): DislikeModeDto = DislikeModeDto(true, true, true)
            override suspend fun setSongLike(songId: String, state: TrackLike): TrackLike? = null
            override suspend fun setAlbumBookmark(albumId: String, bookmarked: Boolean): Boolean? = null
            override suspend fun setAlbumLike(albumId: String, state: AlbumLike): AlbumLike? = null
            override suspend fun setArtistFollow(artistId: String, state: ArtistFollow): ArtistFollow? = null
            override suspend fun setPlaylistPin(playlistId: Long, pinned: Boolean): Boolean? = null
            override suspend fun setPlaylistBookmark(playlistId: Long, bookmarked: Boolean): Boolean? = null
        }
    }
}

/**
 * [LibraryProvider] over the local Room database, read on [NzikDispatchers.DATA]. The only writes
 * are the four explicit state setters of contract §10.2 (since 1.7): local Room only, no network
 * call, no YTMusic push, no download, no queue effect — the phone's UI updates through Room
 * invalidation, as with its own actions. The only network access is the relay of an online
 * artwork ([ArtworkRelay]). Visibility and mapping rules live in [LibraryMapping].
 */
internal class DatabaseLibraryProvider(
    context: Context,
    private val httpClient: () -> OkHttpClient = NetworkClientFactory::getClient,
    /**
     * The bound player service's live streaming-cache size (contract §10, since 1.7.1); `null`
     * while the service is not yet bound — the phone's own indicator shows `0` then too.
     */
    private val mediaCacheSpace: () -> Long? = { null },
) : LibraryProvider {
    private val appContext = context.applicationContext
    private val artworkRelay = ArtworkRelay(httpClient, ::openLocal)

    /**
     * The songs of the phone's home tab behind [filter] (contract §10), each sorted the way the
     * phone's own `HomeSongs.kt` sorts it: `sortAll` for the all / local / downloaded tabs,
     * `sortFavorites` for the liked tab, `allDisliked` for the disliked tab (its fixed order), the
     * format table for the cached tab, the most-played events for the top tab. As on the phone, the
     * `Downloaded` sort keeps the tab's base order and puts the downloaded songs first (or last).
     *
     * The phone's own display settings of its home tabs are applied here, as its `HomeSongs.kt`
     * applies them to its rows: the "All" chip keeps its local rows only when its `includeLocalSongs`
     * setting includes them (240, 254), the Top chip hides its rows over the `excludeSongWithDurationLimit`
     * setting (300-302, 319-321), and the parental control hides the explicit `e:` rows of every tab
     * (456; its detail screens apply the same filter to their rows).
     */
    override suspend fun songs(filter: SongFilter, sort: SongSort, reverse: Boolean, period: TopPeriod?): List<TrackDto> = withContext(NzikDispatchers.DATA) {
        val support = trackSupport()
        val downloaded = support.downloaded
        val sortBy = sort.songSortBy()
        val prefs = appContext.preferences
        // The phone's own home-tab display settings (`HomeSongs.kt` 130-134): the "All" chip's
        // local-songs toggle, the Top chip's duration limit, and the parental control (456)
        val includeLocal = prefs.getBoolean(includeLocalSongsKey, true)
        val durationLimit = prefs.getString(excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled.name)
            ?.let { runCatching { DurationInMinutes.valueOf(it) }.getOrNull() } ?: DurationInMinutes.Disabled
        val parentalControl = prefs.getBoolean(parentalControlEnabledKey, false)
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
        // The phone's home-tab display filters on top of its raw lists (`HomeSongs.kt` 240, 456)
        LibraryMapping.librarySongs(
            filter,
            LibraryMapping.homeTabSongs(shown, filter, includeLocal, durationLimit, parentalControl),
            support,
        )
    }

    /**
     * Contract §10.1 (since 1.7.3, feature `library.sortMenu`): the effective content of the
     * phone's sort menu for the songs chip behind [filter] — its visible options, in its menu
     * order. Each chip reads its own prefix and order key, exactly as its home-screen tab does
     * (each filter is a real phone tab — `top` serves its period selector, `local` its OnDevice
     * tab's own menu).
     */
    override suspend fun songsSortMenu(filter: SongFilter): List<String> = withContext(NzikDispatchers.DATA) {
        val chip = SongsSortMenu.chipKeys(filter)
        // The phone's sort menus read the literal `preferences` file (its `Sort` row, its
        // `HomeSongsSortSettingsDialog`), not the profile-aware `preferences` accessor — the
        // served menu must be the one the phone's tabs actually use
        val prefs = appContext.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        SongsSortMenu.effective(chip, prefs.getString(chip.orderKey, "")) { id ->
            !prefs.getBoolean("${chip.prefix}_sort_${id}_visible", true)
        }
    }

    /**
     * The playlists of the phone's home tab behind [filter] (contract §10, since 1.6), each shown the
     * way the phone's own `HomeLibrary.kt` shows it: `All` keeps everything except the rewind and
     * pinned playlists the phone's own toggles hide, `Pinned` the pinned-prefix names, `Rewind` the
     * rewind names the phone's creation toggles and Month/Year/All filter allow, `Youtube` the
     * youtube-synced ones. Since 1.7.2, [rewindFilter] overrides the phone's Month/Year/All filter
     * for the `Rewind` listing and is persisted to the phone's own setting (its Rewind tab
     * reflects the client's choice, like one of its own chip taps).
     */
    override suspend fun playlists(filter: PlaylistsFilter, sort: PlaylistSort, reverse: Boolean, rewindFilter: RewindPlaylists.Filter?): List<PlaylistDto> = withContext(NzikDispatchers.DATA) {
        val previews = Database.playlistTable.sortPreviews(sort.playlistSortBy(), reverse.sortOrder()).first()
        val prefs = appContext.preferences
        val showPinned = prefs.getBoolean(showPinnedPlaylistsKey, true)
        val showRewind = prefs.getBoolean(showMonthlyPlaylistsKey, true)
        val rewindFilterToApply = rewindFilter ?: prefs.getEnum(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, RewindPlaylists.Filter.Month)
        // A present wire filter is persisted exactly like the phone's own chip tap
        if (rewindFilter != null) prefs.edit { putEnum(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, rewindFilter) }
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
                    rewindFilterToApply,
                )
            }

            PlaylistsFilter.YOUTUBE -> previews.filter { it.playlist.isYoutubePlaylist }
        }
        val artworkTracks = LibraryMapping.artworkTracks(Database.songPlaylistMapTable.songsWithThumbnailDirect())
        val listening = Database.eventTable.getPlaylistListeningTotals().first()
            .associate { it.playlistId to CollectionListening(it.playCount, it.totalPlayTimeMs) }
        LibraryMapping.playlists(shown, artworkTracks, listening) { name -> appContext.rewindDisplayName(name) }
    }

    /**
     * The tracks of a local playlist with the phone's own sort (`sortSongs` of the local playlist
     * screen); as there, the `Downloaded` sort orders by title and puts the downloaded songs first
     * (or last). Absent `sort` keeps the position order (the phone's default). As on the phone's
     * own screen, the parental control hides the explicit `e:` rows (`LocalPlaylistSongs.kt` 1058).
     */
    override suspend fun playlistSongs(playlistId: Long, sort: PlaylistSongSort, reverse: Boolean): List<TrackDto>? = withContext(NzikDispatchers.DATA) {
        Database.playlistTable.findById(playlistId).first() ?: return@withContext null
        val support = trackSupport()
        val downloaded = support.downloaded
        val songs = if (sort == PlaylistSongSort.DOWNLOADED) {
            val base = Database.songPlaylistMapTable.sortSongs(playlistId, PlaylistSongSortBy.Title, SortOrder.Ascending).first()
            if (reverse) base.sortedBy { it.id in downloaded } else base.sortedByDescending { it.id in downloaded }
        } else {
            Database.songPlaylistMapTable.sortSongs(playlistId, sort.playlistSongSortBy(), reverse.sortOrder()).first()
        }
        LibraryMapping.detailSongs(songs, parentalControl())
            .map { LibraryMapping.track(it, support) }
    }

    override suspend fun albums(filter: CollectionFilter, sort: AlbumSort, reverse: Boolean): List<AlbumDto> = withContext(NzikDispatchers.DATA) {
        val albums = when (filter) {
            CollectionFilter.LIBRARY -> Database.albumTable.sortInLibrary(sort.albumSortBy(), reverse.sortOrder()).first()
            CollectionFilter.BOOKMARKED -> Database.albumTable.sortBookmarked(sort.albumSortBy(), reverse.sortOrder()).first()
            // The phone's Disliked tab lists its albums as-is (no sort of its own)
            CollectionFilter.DISLIKED -> Database.albumTable.allDisliked().first()
        }
        val listening = Database.eventTable.getAlbumListeningTotals().first()
            .associate { it.albumId to CollectionListening(it.playCount, it.totalPlayTimeMs) }
        LibraryMapping.albums(
            filter,
            albums,
            Database.songAlbumMapTable.songCountsDirect().associate { it.id to it.count },
            listening,
        )
    }

    /** As on the phone's own screen, the parental control hides the explicit `e:` rows (`AlbumScreen.kt` 453-455). */
    override suspend fun albumSongs(albumId: String): List<TrackDto>? = withContext(NzikDispatchers.DATA) {
        Database.albumTable.findByIdDirect(albumId) ?: return@withContext null
        LibraryMapping.detailSongs(
            Database.songAlbumMapTable.allSongsOfDirect(albumId),
            parentalControl(),
        ).map { LibraryMapping.track(it, trackSupport()) }
    }

    override suspend fun artists(filter: CollectionFilter, sort: ArtistSort, reverse: Boolean): List<ArtistDto> = withContext(NzikDispatchers.DATA) {
        val artists = when (filter) {
            CollectionFilter.LIBRARY -> Database.artistTable.sortInLibrary(sort.artistSortBy(), reverse.sortOrder()).first()
            CollectionFilter.BOOKMARKED -> Database.artistTable.sortFollowing(sort.artistSortBy(), reverse.sortOrder()).first()
            // The phone's Disliked tab lists its artists as-is (no sort of its own)
            CollectionFilter.DISLIKED -> Database.artistTable.allDisliked().first()
        }
        val listening = Database.eventTable.getArtistListeningTotals().first()
            .associate { it.artistId to CollectionListening(it.playCount, it.totalPlayTimeMs) }
        LibraryMapping.artists(
            filter,
            artists,
            Database.songArtistMapTable.songCountsDirect().associate { it.id to it.count },
            listening,
        )
    }

    /** As on the phone's own screen, the parental control hides the explicit `e:` rows (`ArtistScreen.kt` 345). */
    override suspend fun artistSongs(artistId: String): List<TrackDto>? = withContext(NzikDispatchers.DATA) {
        Database.artistTable.findByIdDirect(artistId) ?: return@withContext null
        LibraryMapping.detailSongs(
            Database.songArtistMapTable.allSongsByDirect(artistId),
            parentalControl(),
        ).map { LibraryMapping.track(it, trackSupport()) }
    }

    /** The phone's parental-control display switch, off by default (its `parentalControlEnabledKey`). */
    private fun parentalControl(): Boolean = appContext.preferences.getBoolean(parentalControlEnabledKey, false)

    override suspend fun trackArtwork(trackId: String, size: Int): ArtworkResult =
        artwork(size) { Database.songTable.findByIdDirect(trackId)?.thumbnailUrl }

    override suspend fun albumArtwork(albumId: String, size: Int): ArtworkResult =
        artwork(size) { Database.albumTable.findByIdDirect(albumId)?.thumbnailUrl }

    override suspend fun artistArtwork(artistId: String, size: Int): ArtworkResult =
        artwork(size) { Database.artistTable.findByIdDirect(artistId)?.thumbnailUrl }

    /**
     * Contract §10 (since 1.7.2): the phone's custom playlist cover — the `thumbnail/playlist_<id>`
     * file of its files dir (its own save path, its `LocalPlaylistItemMenu.kt` 203), raw bytes
     * (no resizing, like the phone's own display).
     */
    override suspend fun playlistArtwork(playlistId: Long): ArtworkResult = withContext(NzikDispatchers.DATA) {
        val file = File(appContext.filesDir, "thumbnail/playlist_$playlistId")
        if (!file.exists()) return@withContext ArtworkResult.NotFound
        val bytes = runCatching { file.readBytes() }
            .onFailure { Timber.tag(TAG).w(it, "Could not read the playlist cover") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() && it.size <= MAX_ARTWORK_BYTES }
            ?: return@withContext ArtworkResult.NotFound
        // Only jpeg / png / webp are ever served (the phone's own image formats)
        val contentType = ImageTypes.contentTypeOf(bytes, null) ?: return@withContext ArtworkResult.NotFound
        ArtworkResult.Image(bytes, contentType)
    }

    /** Contract §10 (since 1.7.1): the phone's disk caches, used vs configured cap (its Songs-tab bar). */
    override suspend fun cacheSpace(): LibraryCacheDto = withContext(NzikDispatchers.DATA) {
        LibraryCacheDto(
            cached = CacheSpaceDto(
                usedBytes = mediaCacheUsedBytes(),
                maxBytes = mediaCacheMaxBytes(),
                maxText = cacheMaxText(exoPlayerDiskCacheMaxSizeKey),
            ),
            downloaded = CacheSpaceDto(
                usedBytes = downloadCacheUsedBytes(),
                maxBytes = downloadCacheMaxBytes(),
                maxText = cacheMaxText(exoPlayerDiskDownloadCacheMaxSizeKey),
            ),
        )
    }

    /** Contract §10 (since 1.7.2): the phone's rewind creation toggles and its current Month/Year/All filter. */
    override suspend fun rewindState(): RewindStateDto = withContext(NzikDispatchers.DATA) {
        RewindStateDto(
            monthlyEnabled = DataStoreUtils.prefs(appContext).getBoolean(DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true),
            yearlyEnabled = DataStoreUtils.prefs(appContext).getBoolean(DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true),
            filter = appContext.preferences
                .getEnum(RewindPlaylists.REWIND_PLAYLISTS_FILTER_KEY, RewindPlaylists.Filter.Month).wire,
        )
    }

    /** Contract §10 (since 1.7.2): the phone's `DislikeMode` per collection (`Enabled` → `true`, default). */
    override suspend fun dislikeMode(): DislikeModeDto = withContext(NzikDispatchers.DATA) {
        DislikeModeDto(
            songs = appContext.preferences.getEnum(excludeDislikedSongsKey, DislikeMode.Enabled).isEnabled,
            albums = appContext.preferences.getEnum(excludeDislikedAlbumsKey, DislikeMode.Enabled).isEnabled,
            artists = appContext.preferences.getEnum(excludeDislikedArtistsKey, DislikeMode.Enabled).isEnabled,
        )
    }

    // --- Contract §10.2 (since 1.7): the explicit writes, local Room only ---

    override suspend fun setSongLike(songId: String, state: TrackLike): TrackLike? =
        withContext(NzikDispatchers.DATA) {
            if (Database.songTable.findByIdDirect(songId) == null) return@withContext null
            Database.songTable.likeState(songId, TrackLike.toColumnValue(state))
            state
        }

    override suspend fun setAlbumBookmark(albumId: String, bookmarked: Boolean): Boolean? =
        withContext(NzikDispatchers.DATA) {
            if (Database.albumTable.findByIdDirect(albumId) == null) return@withContext null
            Database.albumTable.bookmarkState(albumId, bookmarked)
            bookmarked
        }

    override suspend fun setAlbumLike(albumId: String, state: AlbumLike): AlbumLike? =
        withContext(NzikDispatchers.DATA) {
            if (Database.albumTable.findByIdDirect(albumId) == null) return@withContext null
            Database.albumTable.likeState(
                albumId,
                bookmarked = state == AlbumLike.BOOKMARKED,
                disliked = state == AlbumLike.DISLIKED,
            )
            state
        }

    override suspend fun setArtistFollow(artistId: String, state: ArtistFollow): ArtistFollow? =
        withContext(NzikDispatchers.DATA) {
            if (Database.artistTable.findByIdDirect(artistId) == null) return@withContext null
            Database.artistTable.followState(
                artistId,
                when (state) {
                    ArtistFollow.FOLLOWED -> true
                    ArtistFollow.DISLIKED -> false
                    ArtistFollow.NEUTRAL -> null
                },
            )
            state
        }

    override suspend fun setPlaylistPin(playlistId: Long, pinned: Boolean): Boolean? =
        withContext(NzikDispatchers.DATA) {
            if (Database.playlistTable.findById(playlistId).first() == null) return@withContext null
            Database.playlistTable.pinState(playlistId, pinned)
            pinned
        }

    override suspend fun setPlaylistBookmark(playlistId: Long, bookmarked: Boolean): Boolean? =
        withContext(NzikDispatchers.DATA) {
            val playlist = Database.playlistTable.findById(playlistId).first() ?: return@withContext null
            Database.playlistTable.update(playlist.copy(isYoutubePlaylist = bookmarked))
            bookmarked
        }

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
        openMediaCache { cache ->
            Database.formatTable.allWithSongs().first().mapNotNull { format ->
                val contentLength = format.format.contentLength ?: return@mapNotNull null
                if (cache.isCached(format.song.id, 0, contentLength)) format.song.id else null
            }.toSet()
        }.orEmpty()
    }.onFailure { Timber.tag(TAG).w(it, "Cache read unavailable") }.getOrDefault(emptySet())

    /**
     * The active profile's streaming cache, opened read-only for one read; `null` when the profile
     * has no cache directory of its own (temp cache, or none at all). The `keys` read waits for the
     * cache's asynchronous open before anything else is read off it.
     */
    private suspend fun <T> openMediaCache(block: suspend (SimpleCache) -> T): T? {
        val location = mediaCacheLocation(appContext)
        val dir = location.dir ?: return null
        if (!dir.exists()) return null
        val cache = SimpleCache(dir, NoOpCacheEvictor(), cacheDatabaseProvider(appContext, location.indexDbName))
        try {
            cache.keys
            return block(cache)
        } finally {
            cache.release()
        }
    }

    /**
     * The per-track download and cache states of the phone's rows (contract §1.1, since 1.7.1): the
     * completed downloads, the active download states and their progress (from the download manager),
     * and the streaming-cache membership.
     */
    private suspend fun trackSupport(): TrackSupport = runCatching {
        // The downloads map is only filled once the download manager exists
        MyDownloadHelper.getDownloadManager(appContext)
        val downloads = MyDownloadHelper.downloads.value
        TrackSupport(
            downloaded = downloads.filterValues { it.state == Download.STATE_COMPLETED }.keys,
            cached = cachedSongIds(),
            states = downloads.entries
                .mapNotNull { (id, download) ->
                    when (download.state) {
                        Download.STATE_DOWNLOADING -> id to TrackDownloadState.DOWNLOADING
                        Download.STATE_QUEUED, Download.STATE_RESTARTING -> id to TrackDownloadState.QUEUED
                        else -> null
                    }
                }
                .toMap(),
            progresses = MyDownloadHelper.progresses.value,
        )
    }.onFailure { Timber.tag(TAG).w(it, "Download states unavailable") }.getOrDefault(TrackSupport())

    /**
     * The used space of the player's streaming cache, exactly as the phone's own
     * `CacheSpaceIndicator` reads it: the bound service's live cache, `0` while the service is not
     * bound (the phone shows `0` then too).
     */
    private fun mediaCacheUsedBytes(): Long = runCatching {
        mediaCacheSpace()?.coerceAtLeast(0L) ?: 0L
    }.onFailure { Timber.tag(TAG).w(it, "Media cache size unavailable") }.getOrDefault(0L)

    /** The used space of the phone's download cache (the download manager's live cache). */
    private fun downloadCacheUsedBytes(): Long = runCatching {
        MyDownloadHelper.getDownloadManager(appContext)
        MyDownloadHelper.downloadCache.cacheSpace
    }.onFailure { Timber.tag(TAG).w(it, "Download cache size unavailable") }.getOrDefault(0L)

    /**
     * The configured streaming-cache cap (`null` when unlimited — the phone hides its bar then);
     * `Custom` sends the actual custom value, as the player's own evictor uses it.
     */
    private fun mediaCacheMaxBytes(): Long? {
        val size = appContext.preferences.getEnum(exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)
        return when (size) {
            ExoPlayerDiskCacheMaxSize.Unlimited -> null
            ExoPlayerDiskCacheMaxSize.Custom -> appContext.preferences.getInt(exoPlayerCustomCacheKey, 32) * 1_000_000L
            else -> size.bytes
        }
    }

    /**
     * The configured download-cache cap (`null` when unlimited — the phone hides its bar then);
     * read exactly the way the download cache is built (`MyDownloadHelper.initDownloadCache`).
     */
    private fun downloadCacheMaxBytes(): Long? {
        val size = appContext.preferences.getEnum(exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB`)
        return when (size) {
            ExoPlayerDiskCacheMaxSize.Unlimited -> null
            ExoPlayerDiskCacheMaxSize.Custom -> appContext.preferences.getInt(exoPlayerCustomCacheKey, 32) * 1_000_000L
            else -> size.bytes
        }
    }

    /**
     * The phone's label of its configured cache cap in its own language (contract §10, since
     * 1.7.2) — the non-composable twin of `ExoPlayerDiskCacheMaxSize.text`; `null` when the cap
     * is unlimited (the phone hides its bar then).
     */
    private fun cacheMaxText(key: String): String? {
        val size = appContext.preferences.getEnum(key, ExoPlayerDiskCacheMaxSize.`2GB`)
        return when (size) {
            ExoPlayerDiskCacheMaxSize.Unlimited -> null
            ExoPlayerDiskCacheMaxSize.Disabled -> appContext.getString(R.string.turn_off)
            ExoPlayerDiskCacheMaxSize.Custom -> appContext.getString(R.string.custom)
            else -> size.name
        }
    }


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

/**
 * The effective content of the phone's songs sort menus (contract §10.1, since 1.7.3, feature
 * `library.sortMenu`): the phone's own `Sort.readSortedEnumConstants` /
 * `PeriodSelector.readSortedEntries` semantics — each chip's visible options (its
 * `${prefix}_sort_<option>_visible` flags, visible by default), in the chip's menu order when
 * its user reordered it (the saved JSON array of the chip's order key), the rest in the chip's
 * native option order. Unknown ids in the saved order are dropped, as on the phone. The result
 * is the wire values of the contract's `sort` parameter (its `period` values for the Top chip's
 * periods).
 */
internal object SongsSortMenu {

    /** One chip's sort-menu keys: its visibility prefix and its saved-order key (its `Preferences.kt`). */
    data class ChipKeys(val prefix: String, val orderKey: String)

    /**
     * The phone's songs sort menus, as its home screen and its `HomeSongsSortSettingsDialog`
     * define them — one prefix / order-key pair per chip (the real order-key constants). A new
     * chip is one more entry here: the §10.1 menu read and the §10.3 live listener follow it.
     */
    val all = ChipKeys("all", homeSongsAllSortMenuOrderKey)
    val favs = ChipKeys("favs", homeSongsFavoritesSortMenuOrderKey)
    val off = ChipKeys("off", homeSongsCachedSortMenuOrderKey)
    val dl = ChipKeys("dl", homeSongsDownloadedSortMenuOrderKey)
    val top = ChipKeys("top", homeSongsTopSortMenuOrderKey)
    val onDevice = ChipKeys("dev", homeSongsOnDeviceSortMenuOrderKey)
    val disliked = ChipKeys("disliked", homeSongsDislikedSortMenuOrderKey)

    val chips: List<ChipKeys> = listOf(all, favs, off, dl, top, onDevice, disliked)

    /** The chip behind a wire filter: each filter is the phone's real home tab (`local` is its OnDevice tab). */
    fun chipKeys(filter: SongFilter): ChipKeys = when (filter) {
        SongFilter.ALL -> all
        SongFilter.LIKED -> favs
        SongFilter.OFFLINE -> off
        SongFilter.DOWNLOADED -> dl
        SongFilter.TOP -> top
        SongFilter.LOCAL -> onDevice
        SongFilter.DISLIKED -> disliked
    }

    /**
     * The option names of each menu: `SongSortBy` for the sort chips, `StatisticsType` for the
     * Top chip (its period selector is its sort menu) and `OnDeviceSongSortBy` for OnDevice —
     * exactly the options each tab's menu shows.
     */
    private val optionNames: Map<ChipKeys, List<String>> = mapOf(
        top to StatisticsType.entries.map { it.name },
        onDevice to OnDeviceSongSortBy.entries.map { it.name },
    )

    private fun optionNamesOf(chip: ChipKeys): List<String> =
        optionNames[chip] ?: SongSortBy.entries.map { it.name }

    /**
     * Every preference key one of the phone's songs sort menus reads (contract §10.3, since
     * 1.7.3): each chip's saved order and its per-option visibility flags, with the chip's own
     * option ids (its `SongSortBy` / `StatisticsType` / `OnDeviceSongSortBy` names).
     */
    val sortMenuKeys: Set<String> = buildSet {
        chips.forEach { chip ->
            add(chip.orderKey)
            optionNamesOf(chip).forEach { id ->
                add("${chip.prefix}_sort_${id}_visible")
            }
        }
    }

    /** The phone's sort option names (`SongSortBy`) to the wire values of the contract's `sort`. */
    private val songSortNamesToWire = mapOf(
        "Title" to "title",
        "Artist" to "artist",
        "AlbumName" to "album",
        "Duration" to "duration",
        "PlayCount" to "playCount",
        "PlayTime" to "playTime",
        "RelativePlayTime" to "relativePlayTime",
        "DateAdded" to "dateAdded",
        "DatePlayed" to "datePlayed",
        "DateLiked" to "dateLiked",
        "Downloaded" to "downloaded",
        "Custom" to "custom",
    )

    /** The phone's Top periods (`StatisticsType`) to the wire values of the contract's `period`. */
    private val periodNamesToWire = mapOf(
        "Today" to "today",
        "OneWeek" to "week",
        "OneMonth" to "month",
        "ThreeMonths" to "3months",
        "SixMonths" to "6months",
        "OneYear" to "year",
        "All" to "all",
    )

    /** The phone's OnDevice options (`OnDeviceSongSortBy`) to the wire values of the contract's `sort`. */
    private val onDeviceNamesToWire = mapOf(
        "Title" to "title",
        "DateAdded" to "dateAdded",
        "Artist" to "artist",
        "Duration" to "duration",
        "Album" to "album",
    )

    private val namesToWire: Map<String, String> = songSortNamesToWire + onDeviceNamesToWire + periodNamesToWire

    /**
     * The phone's songs sort options, native (enum) order, as wire values. An option the wire map
     * does not name (a future phone entry) passes through as-is: the client drops what it does
     * not know, no route crashes on it.
     */
    val nativeOrder: List<String> = SongSortBy.entries.map { songSortNamesToWire[it.name] ?: it.name }

    /**
     * A chip's effective menu: [savedOrderJson] is the chip's saved order (its order key, a
     * JSON array of option names, blank when the phone's user never reordered it), [isHidden]
     * the chip's per-option visibility (`${chip.prefix}_sort_<name>_visible`, visible by
     * default). The ids are the chip's own options — a saved id of another chip's menu is
     * dropped, as on the phone.
     */
    fun effective(chip: ChipKeys, savedOrderJson: String?, isHidden: (String) -> Boolean): List<String> {
        val native = optionNamesOf(chip)
        val visible = native.filterNot(isHidden)
        val visibleIds = visible.toSet()
        val saved = runCatching {
            val array = JSONArray(savedOrderJson.orEmpty())
            buildList {
                for (i in 0 until array.length()) {
                    val id = array.getString(i)
                    if (contains(id)) continue
                    add(id)
                }
            }
        }.getOrDefault(emptyList())
        val result = saved.filter { it in visibleIds }.toMutableList()
        for (id in visible) if (id !in result) result.add(id)
        // An id the wire map does not name (a future phone option) passes through: the client
        // drops what it does not know, the route never crashes on it
        return result.map { namesToWire[it] ?: it }
    }
}
