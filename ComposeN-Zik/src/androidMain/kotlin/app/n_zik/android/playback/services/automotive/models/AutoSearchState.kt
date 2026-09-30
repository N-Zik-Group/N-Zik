package app.n_zik.android.playback.services.automotive.models

import app.it.fast4x.rimusic.models.Song
import it.fast4x.innertube.Innertube

object AutoSearchState {
    var searchedSongs: List<Song> = emptyList()
    // Songs of opened online playlists, scoped per playlist id so a queue can
    // never contain songs from a previously opened playlist (issue #777).
    var playlistSongsById: Map<String, List<Song>> = emptyMap()
    var searchedArtists: List<Innertube.ArtistItem> = emptyList()
    var searchedVideos: List<Innertube.VideoItem> = emptyList()
    var searchedAlbums: List<Innertube.AlbumItem> = emptyList()

    fun clear() {
        searchedSongs = emptyList()
        playlistSongsById = emptyMap()
        searchedArtists = emptyList()
        searchedVideos = emptyList()
        searchedAlbums = emptyList()
    }
}
