package app.n_zik.android.utils.playlist

import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ImportSong

/** Imports metadata and an ordered batch together; call inside Database.asyncTransaction for retries. */
fun importSongsToPlaylist(playlist: Playlist, songs: List<Song>) {
    Database.syncTransaction {
        songTable.upsert(songs)
        mapIgnore(playlist, *songs.toTypedArray())
    }
}

/** Maps a service import without replacing existing metadata and records actual positions for matching. */
internal fun importServiceSongsToPlaylist(
    playlist: Playlist?,
    songs: List<Song>,
    onImported: (index: Int, position: Int) -> Unit = { _, _ -> },
) {
    Database.syncTransaction {
        if (playlist != null) mapIgnore(playlist, *songs.toTypedArray())
        else songs.forEach { songTable.insertIgnore(it) }
        songs.forEachIndexed { index, song ->
            val position = if (playlist != null) songPlaylistMapTable.findPositionOf(song.id, playlist.id) else index
            importSongTable.insert(ImportSong(originalId = song.id, position = position, playlistId = playlist?.id))
            onImported(index, position)
        }
    }
}
