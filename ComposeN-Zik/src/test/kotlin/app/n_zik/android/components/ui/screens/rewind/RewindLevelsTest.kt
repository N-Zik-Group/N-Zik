package app.n_zik.android.components.ui.screens.rewind

import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the RiPlay listening levels ([rewindSongLevel] / [rewindAlbumLevel] /
 * [rewindPlaylistLevel] / [rewindArtistLevel]): the threshold ranges are inclusive on BOTH
 * sides (0-200 / 201-500 / ...), so every boundary minute lands in the tier its KDoc range
 * declares, and anything above the top range keeps its top tier.
 */
class RewindLevelsTest {

    @Test
    fun songLevelBoundariesAreInclusiveOnBothSides() {
        assertEquals(R.string.rw_level_song_obsession_name, rewindSongLevel(0L).nameId)
        assertEquals(R.string.rw_level_song_obsession_name, rewindSongLevel(200L).nameId)
        assertEquals(R.string.rw_level_song_anthem_name, rewindSongLevel(201L).nameId)
        assertEquals(R.string.rw_level_song_anthem_name, rewindSongLevel(500L).nameId)
        assertEquals(R.string.rw_level_song_soundtrack_name, rewindSongLevel(501L).nameId)
        assertEquals(R.string.rw_level_song_soundtrack_name, rewindSongLevel(1000L).nameId)
        assertEquals(R.string.rw_level_song_eternal_flame_name, rewindSongLevel(1001L).nameId)
        assertEquals(R.string.rw_level_song_eternal_flame_name, rewindSongLevel(1_000_000L).nameId)
    }

    @Test
    fun albumLevelBoundariesAreInclusiveOnBothSides() {
        assertEquals(R.string.rw_level_album_deep_dive_name, rewindAlbumLevel(0L).nameId)
        assertEquals(R.string.rw_level_album_deep_dive_name, rewindAlbumLevel(1000L).nameId)
        assertEquals(R.string.rw_level_album_on_repeat_name, rewindAlbumLevel(1001L).nameId)
        assertEquals(R.string.rw_level_album_on_repeat_name, rewindAlbumLevel(2500L).nameId)
        assertEquals(R.string.rw_level_album_resident_name, rewindAlbumLevel(2501L).nameId)
        assertEquals(R.string.rw_level_album_resident_name, rewindAlbumLevel(5000L).nameId)
        assertEquals(R.string.rw_level_album_sanctuary_name, rewindAlbumLevel(5001L).nameId)
        assertEquals(R.string.rw_level_album_sanctuary_name, rewindAlbumLevel(1_000_000L).nameId)
    }

    @Test
    fun playlistLevelBoundariesAreInclusiveOnBothSides() {
        assertEquals(R.string.rw_level_playlist_curator_name, rewindPlaylistLevel(0L).nameId)
        assertEquals(R.string.rw_level_playlist_curator_name, rewindPlaylistLevel(500L).nameId)
        assertEquals(R.string.rw_level_playlist_mastermind_name, rewindPlaylistLevel(501L).nameId)
        assertEquals(R.string.rw_level_playlist_mastermind_name, rewindPlaylistLevel(1500L).nameId)
        assertEquals(R.string.rw_level_playlist_phenomenon_name, rewindPlaylistLevel(1501L).nameId)
        assertEquals(R.string.rw_level_playlist_phenomenon_name, rewindPlaylistLevel(3000L).nameId)
        assertEquals(R.string.rw_level_playlist_opus_name, rewindPlaylistLevel(3001L).nameId)
        assertEquals(R.string.rw_level_playlist_opus_name, rewindPlaylistLevel(1_000_000L).nameId)
    }

    @Test
    fun artistLevelBoundariesAreInclusiveOnBothSides() {
        assertEquals(R.string.rw_level_artist_new_favorite_name, rewindArtistLevel(0L).nameId)
        assertEquals(R.string.rw_level_artist_new_favorite_name, rewindArtistLevel(2000L).nameId)
        assertEquals(R.string.rw_level_artist_a_list_fan_name, rewindArtistLevel(2001L).nameId)
        assertEquals(R.string.rw_level_artist_a_list_fan_name, rewindArtistLevel(5000L).nameId)
        assertEquals(R.string.rw_level_artist_archivist_name, rewindArtistLevel(5001L).nameId)
        assertEquals(R.string.rw_level_artist_archivist_name, rewindArtistLevel(10000L).nameId)
        assertEquals(R.string.rw_level_artist_devotee_name, rewindArtistLevel(10001L).nameId)
        assertEquals(R.string.rw_level_artist_devotee_name, rewindArtistLevel(1_000_000L).nameId)
    }
}
