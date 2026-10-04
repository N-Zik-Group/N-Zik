package app.n_zik.android.bridge.library

import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.hasExplicitPrefix
import app.it.fast4x.rimusic.enums.DurationInMinutes
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.n_zik.android.bridge.AlbumDto
import app.n_zik.android.bridge.ArtistDto
import app.n_zik.android.bridge.PlaylistDto
import app.n_zik.android.bridge.PlaylistOrigin
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.TrackDownloadState
import app.n_zik.android.bridge.state.TrackLike
import app.n_zik.android.bridge.state.TrackMapping
import app.n_zik.android.core.database.PlaylistSongRef
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.it.fast4x.rimusic.MONTHLY_PREFIX
import app.it.fast4x.rimusic.PINNED_PREFIX

/**
 * Pure visibility and mapping rules of the library routes (contract §1.1, §10): what the
 * phone's tabs show, and how database rows become wire objects. No database access here.
 */
internal object LibraryMapping {

    /**
     * The phone's home tabs hide their disliked rows in every chip but the Disliked tab itself
     * (`HomeSongs.kt` 342-344, `HomeAlbum.kt` 333-335, `HomeArtist.kt` 334-336).
     */
    fun isListedSong(song: Song): Boolean = (song.likedAt ?: 0L) >= 0L

    /**
     * The phone's home-tab display filters on top of its raw tab lists (`HomeSongs.kt` 240, 254,
     * 300-302, 319-321, 456): the "All" chip keeps its local rows only when [includeLocal] includes
     * them, the Top chip hides its rows over [durationLimit], and [parentalControl] hides the
     * explicit `e:` rows of every tab. [filter] selects which of the two per-tab filters apply.
     */
    fun homeTabSongs(
        shown: List<Song>,
        filter: SongFilter,
        includeLocal: Boolean,
        durationLimit: DurationInMinutes,
        parentalControl: Boolean,
    ): List<Song> = shown
        .filter { filter != SongFilter.ALL || includeLocal || !it.id.startsWith(LOCAL_KEY_PREFIX, true) }
        .filter { filter != SongFilter.TOP || durationLimit == DurationInMinutes.Disabled
            || it.durationText?.let { text -> durationTextToMillis(text) < durationLimit.asMillis } == true }
        .filter { !parentalControl || !it.title.startsWith(EXPLICIT_PREFIX, true) }

    /**
     * The phone's detail screens' display filter: the parental control hides the explicit `e:`
     * rows of an album / artist / playlist (`AlbumScreen.kt` 453-455, `ArtistScreen.kt` 345,
     * `LocalPlaylistSongs.kt` 1058).
     */
    fun detailSongs(shown: List<Song>, parentalControl: Boolean): List<Song> =
        shown.filter { !parentalControl || !it.title.startsWith(EXPLICIT_PREFIX, true) }

    /**
     * `Track` of a database song: cleaned title and artists, the like tri-state from `likedAt`,
     * and the phone's row states from [support] (contract §1.1, since 1.7.1).
     */
    fun track(song: Song, support: TrackSupport): TrackDto =
        TrackMapping.track(
            mediaId = song.id,
            title = song.cleanTitle(),
            isExplicit = song.title.hasExplicitPrefix(),
            artist = song.artistsText?.let { song.cleanArtistsText() },
            hasArtwork = ArtworkUrls.hasArtwork(song.thumbnailUrl),
            durationText = song.durationText,
            isLiked = (song.likedAt ?: 0L) > 0L,
            isDownloaded = song.id in support.downloaded,
            like = TrackLike.of(song.likedAt),
            totalPlayTimeMs = song.totalPlayTimeMs,
            playCount = song.playCount,
            downloadState = support.states[song.id] ?: TrackDownloadState.NONE,
            downloadProgress = support.progresses[song.id]
                ?.takeIf { support.states[song.id] == TrackDownloadState.DOWNLOADING },
            isCached = song.id in support.cached,
            artworkUrl = song.thumbnailUrl,
        )

    /** [filter] the tab behind the read: the phone lists its disliked rows only in the Disliked tab. */
    fun librarySongs(filter: SongFilter, songs: List<Song>, support: TrackSupport): List<TrackDto> =
        songs
            .filter { filter == SongFilter.DISLIKED || isListedSong(it) }
            .map { track(it, support) }

    /** The phone lists its disliked albums only in the Disliked tab (`HomeAlbum.kt` 333-335). */
    fun albums(
        filter: CollectionFilter,
        albums: List<Album>,
        songCounts: Map<String, Int>,
        listening: Map<String, CollectionListening> = emptyMap(),
    ): List<AlbumDto> =
        albums.filter { filter == CollectionFilter.DISLIKED || it.dislikedAt == null }.map { album ->
            val stats = listening[album.id] ?: CollectionListening()
            AlbumDto(
                id = album.id,
                title = album.cleanTitle(),
                artists = album.authorsText?.let { album.cleanAuthorsText() },
                year = album.year,
                trackCount = songCounts[album.id] ?: 0,
                hasArtwork = ArtworkUrls.hasArtwork(album.thumbnailUrl),
                isBookmarked = album.bookmarkedAt != null,
                isDisliked = album.dislikedAt != null,
                origin = if (album.isYoutubeAlbum) PlaylistOrigin.YTMUSIC else PlaylistOrigin.LOCAL,
                playCount = stats.playCount,
                totalPlayTimeMs = stats.totalPlayTimeMs,
            )
        }

    /** The phone lists its disliked artists only in the Disliked tab (`HomeArtist.kt` 334-336). */
    fun artists(
        filter: CollectionFilter,
        artists: List<Artist>,
        songCounts: Map<String, Int>,
        listening: Map<String, CollectionListening> = emptyMap(),
    ): List<ArtistDto> =
        artists.filter { filter == CollectionFilter.DISLIKED || it.dislikedAt == null }.map { artist ->
            val stats = listening[artist.id] ?: CollectionListening()
            ArtistDto(
                id = artist.id,
                name = artist.cleanName(),
                trackCount = songCounts[artist.id] ?: 0,
                hasArtwork = ArtworkUrls.hasArtwork(artist.thumbnailUrl),
                isBookmarked = artist.bookmarkedAt != null,
                isDisliked = artist.dislikedAt != null,
                origin = if (artist.isYoutubeArtist) PlaylistOrigin.YTMUSIC else PlaylistOrigin.LOCAL,
                playCount = stats.playCount,
                totalPlayTimeMs = stats.totalPlayTimeMs,
            )
        }

    /** First song with a thumbnail of each playlist; [rows] come in playlist order. */
    fun artworkTracks(rows: List<PlaylistSongRef>): Map<Long, String> {
        val first = LinkedHashMap<Long, String>()
        rows.forEach { first.putIfAbsent(it.playlistId, it.songId) }
        return first
    }

    /**
     * Every playlist is listed; [displayName] turns the cleaned name into the shown one (Rewind);
     * [listening] carries the phone's grid overlays (play count / listening time per playlist).
     */
    fun playlists(
        previews: List<PlaylistPreview>,
        artworkTracks: Map<Long, String>,
        listening: Map<Long, CollectionListening> = emptyMap(),
        displayName: (String) -> String,
    ): List<PlaylistDto> =
        previews.map { preview ->
            val playlist = preview.playlist
            val stats = listening[playlist.id] ?: CollectionListening()
            PlaylistDto(
                id = playlist.id.toString(),
                name = displayName(playlist.cleanName()),
                trackCount = preview.songCount,
                artworkTrackId = artworkTracks[playlist.id],
                origin = originOf(playlist.name, playlist.browseId, playlist.isYoutubePlaylist),
                isPinned = playlist.name.startsWith(PINNED_PREFIX, ignoreCase = true),
                isBookmarked = playlist.isYoutubePlaylist,
                isEditable = playlist.isEditable,
                playCount = stats.playCount,
                totalPlayTimeMs = stats.totalPlayTimeMs,
                browseId = playlist.browseId,
            )
        }

    /**
     * The phone's origin icon logic (`PlaylistItem.kt` 301-333) as a wire value: the pin is a
     * separate flag ([PlaylistDto.isPinned]); the generated rewind playlists keep their period
     * kind (the phone's `stat_month` / `stat_year` / `musical_notes` icons, since 1.7.1), the
     * other icons map to their origin.
     */
    fun originOf(name: String, browseId: String?, isYoutubePlaylist: Boolean): PlaylistOrigin = when {
        RewindPlaylists.isMonthly(name) -> PlaylistOrigin.REWIND_MONTHLY
        RewindPlaylists.isYearly(name) -> PlaylistOrigin.REWIND_YEARLY
        RewindPlaylists.isAlltime(name) -> PlaylistOrigin.REWIND_ALLTIME
        // The legacy (pre-kind) `monthly:` prefix and any other rewind name keep the plain kind
        RewindPlaylists.isRewind(name) || name.startsWith(MONTHLY_PREFIX, ignoreCase = true) -> PlaylistOrigin.REWIND
        browseId?.startsWith("SPOTIFY_IMPORT") == true -> PlaylistOrigin.SPOTIFY
        browseId?.startsWith("RIPLAY_IMPORT") == true -> PlaylistOrigin.RIPLAY
        isYoutubePlaylist || browseId?.startsWith("VL") == true -> PlaylistOrigin.YTMUSIC
        else -> PlaylistOrigin.LOCAL
    }
}
