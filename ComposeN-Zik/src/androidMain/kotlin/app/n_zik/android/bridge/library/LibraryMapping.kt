package app.n_zik.android.bridge.library

import app.it.fast4x.rimusic.hasExplicitPrefix
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.bridge.AlbumDto
import app.n_zik.android.bridge.ArtistDto
import app.n_zik.android.bridge.PlaylistDto
import app.n_zik.android.bridge.state.TrackDto
import app.n_zik.android.bridge.state.TrackMapping
import app.n_zik.android.core.database.PlaylistSongRef

/**
 * Pure visibility and mapping rules of the library routes (contract §1.1, §10): what the
 * phone's tabs show, and how database rows become wire objects. No database access here.
 */
internal object LibraryMapping {

    /** Phone's Songs tab: a disliked song (`likedAt < 0`) is hidden. */
    fun isListedSong(song: Song): Boolean = (song.likedAt ?: 0L) >= 0L

    /** `Track` of a database song: cleaned title and artists, liked only for `likedAt > 0`. */
    fun track(song: Song, downloaded: Set<String>): TrackDto =
        TrackMapping.track(
            mediaId = song.id,
            title = song.cleanTitle(),
            isExplicit = song.title.hasExplicitPrefix(),
            artist = song.artistsText?.let { song.cleanArtistsText() },
            hasArtwork = ArtworkUrls.hasArtwork(song.thumbnailUrl),
            durationText = song.durationText,
            isLiked = (song.likedAt ?: 0L) > 0L,
            isDownloaded = song.id in downloaded,
        )

    fun librarySongs(songs: List<Song>, downloaded: Set<String>): List<LibrarySong> =
        songs.filter(::isListedSong).map { LibrarySong(track(it, downloaded), it.totalPlayTimeMs) }

    /** Phone's Albums tab: disliked albums are hidden. */
    fun albums(albums: List<Album>, songCounts: Map<String, Int>): List<AlbumDto> =
        albums.filter { it.dislikedAt == null }.map { album ->
            AlbumDto(
                id = album.id,
                title = album.cleanTitle(),
                artists = album.authorsText?.let { album.cleanAuthorsText() },
                year = album.year,
                trackCount = songCounts[album.id] ?: 0,
                hasArtwork = ArtworkUrls.hasArtwork(album.thumbnailUrl),
                isBookmarked = album.bookmarkedAt != null,
            )
        }

    /** Phone's Artists tab: disliked artists are hidden. */
    fun artists(artists: List<Artist>, songCounts: Map<String, Int>): List<ArtistDto> =
        artists.filter { it.dislikedAt == null }.map { artist ->
            ArtistDto(
                id = artist.id,
                name = artist.cleanName(),
                trackCount = songCounts[artist.id] ?: 0,
                hasArtwork = ArtworkUrls.hasArtwork(artist.thumbnailUrl),
                isBookmarked = artist.bookmarkedAt != null,
            )
        }

    /** First song with a thumbnail of each playlist; [rows] come in playlist order. */
    fun artworkTracks(rows: List<PlaylistSongRef>): Map<Long, String> {
        val first = LinkedHashMap<Long, String>()
        rows.forEach { first.putIfAbsent(it.playlistId, it.songId) }
        return first
    }

    /** Every playlist is listed; [displayName] turns the cleaned name into the shown one (Rewind). */
    fun playlists(
        previews: List<PlaylistPreview>,
        artworkTracks: Map<Long, String>,
        displayName: (String) -> String,
    ): List<PlaylistDto> =
        previews.map { preview ->
            val playlist = preview.playlist
            PlaylistDto(
                id = playlist.id.toString(),
                name = displayName(playlist.cleanName()),
                trackCount = preview.songCount,
                artworkTrackId = artworkTracks[playlist.id],
            )
        }
}
