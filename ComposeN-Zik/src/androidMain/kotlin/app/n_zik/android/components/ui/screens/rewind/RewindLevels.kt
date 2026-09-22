package app.n_zik.android.components.ui.screens.rewind

import app.n_zik.android.R

/**
 * One listening level tier shown on the show slides (top song, album, artist, playlist).
 *
 * Ported from the RiPlay Rewind extension (GPL-3.0, original in docs/RiPlay): tier names,
 * taglines and threshold ranges follow its buildRewindState() mapping. Name and tagline come
 * from string resources (rw_level_*_name / rw_level_*_tagline entries in values/strings.xml)
 * and are resolved at the UI call site.
 */
internal data class RewindLevel(
    val nameId: Int,
    val taglineId: Int
)

/** RiPlay thresholds: 0-200 OBSESSION / 201-500 ANTHEM / 501-1000 SOUNDTRACK / 1001+ ETERNAL FLAME. */
internal fun rewindSongLevel(minutes: Long): RewindLevel = when {
    minutes in 0L..200L ->
        RewindLevel(
            nameId = R.string.rw_level_song_obsession_name,
            taglineId = R.string.rw_level_song_obsession_tagline
        )
    minutes in 201L..500L ->
        RewindLevel(
            nameId = R.string.rw_level_song_anthem_name,
            taglineId = R.string.rw_level_song_anthem_tagline
        )
    minutes in 501L..1000L ->
        RewindLevel(
            nameId = R.string.rw_level_song_soundtrack_name,
            taglineId = R.string.rw_level_song_soundtrack_tagline
        )
    else ->
        RewindLevel(
            nameId = R.string.rw_level_song_eternal_flame_name,
            taglineId = R.string.rw_level_song_eternal_flame_tagline
        )
}

/** RiPlay thresholds: 0-1000 DEEP DIVE / 1001-2500 ON REPEAT / 2501-5000 RESIDENT / 5001+ SANCTUARY. */
internal fun rewindAlbumLevel(minutes: Long): RewindLevel = when {
    minutes in 0L..1000L ->
        RewindLevel(
            nameId = R.string.rw_level_album_deep_dive_name,
            taglineId = R.string.rw_level_album_deep_dive_tagline
        )
    minutes in 1001L..2500L ->
        RewindLevel(
            nameId = R.string.rw_level_album_on_repeat_name,
            taglineId = R.string.rw_level_album_on_repeat_tagline
        )
    minutes in 2501L..5000L ->
        RewindLevel(
            nameId = R.string.rw_level_album_resident_name,
            taglineId = R.string.rw_level_album_resident_tagline
        )
    else ->
        RewindLevel(
            nameId = R.string.rw_level_album_sanctuary_name,
            taglineId = R.string.rw_level_album_sanctuary_tagline
        )
}

/** RiPlay thresholds: 0-500 CURATOR / 501-1500 MASTERMIND / 1501-3000 PHENOMENON / 3001+ OPUS. */
internal fun rewindPlaylistLevel(minutes: Long): RewindLevel = when {
    minutes in 0L..500L ->
        RewindLevel(
            nameId = R.string.rw_level_playlist_curator_name,
            taglineId = R.string.rw_level_playlist_curator_tagline
        )
    minutes in 501L..1500L ->
        RewindLevel(
            nameId = R.string.rw_level_playlist_mastermind_name,
            taglineId = R.string.rw_level_playlist_mastermind_tagline
        )
    minutes in 1501L..3000L ->
        RewindLevel(
            nameId = R.string.rw_level_playlist_phenomenon_name,
            taglineId = R.string.rw_level_playlist_phenomenon_tagline
        )
    else ->
        RewindLevel(
            nameId = R.string.rw_level_playlist_opus_name,
            taglineId = R.string.rw_level_playlist_opus_tagline
        )
}

/** RiPlay thresholds: 0-2000 NEW FAVORITE / 2001-5000 A-LIST FAN / 5001-10000 THE ARCHIVIST / 10001+ THE DEVOTEE. */
internal fun rewindArtistLevel(minutes: Long): RewindLevel = when {
    minutes in 0L..2000L ->
        RewindLevel(
            nameId = R.string.rw_level_artist_new_favorite_name,
            taglineId = R.string.rw_level_artist_new_favorite_tagline
        )
    minutes in 2001L..5000L ->
        RewindLevel(
            nameId = R.string.rw_level_artist_a_list_fan_name,
            taglineId = R.string.rw_level_artist_a_list_fan_tagline
        )
    minutes in 5001L..10000L ->
        RewindLevel(
            nameId = R.string.rw_level_artist_archivist_name,
            taglineId = R.string.rw_level_artist_archivist_tagline
        )
    else ->
        RewindLevel(
            nameId = R.string.rw_level_artist_devotee_name,
            taglineId = R.string.rw_level_artist_devotee_tagline
        )
}
