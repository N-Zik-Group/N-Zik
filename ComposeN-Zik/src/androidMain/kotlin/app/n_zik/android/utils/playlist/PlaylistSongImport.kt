package app.n_zik.android.utils.playlist

import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ImportSong
import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.core.database.canEditStoredPlaylistMembership

/** Imports metadata and an ordered batch together; call inside Database.asyncTransaction for retries. */
suspend fun importSongsToPlaylist(playlist: Playlist, songs: List<Song>) {
    Database.transaction {
        check(canEditPlaylistMembership(playlist) &&
            (playlist.id <= 0 || canEditStoredPlaylistMembership(playlist.id))) { "Playlist membership is read-only" }
        mergeCsvImportSongs(songs)
        mapIgnore(playlist, *songs.toTypedArray())
    }
}

/** Sparse imports fill missing metadata and preserve existing library state. */
internal suspend fun Database.mergeCsvImportSongs(songs: List<Song>) {
    songs.distinctBy(Song::id).chunked(500).forEach { batch ->
        val existing = songTable.findByIdsDirect(batch.map(Song::id)).associateBy(Song::id)
        songTable.upsert(batch.map { imported ->
            existing[imported.id]?.let { stored ->
                stored.copy(
                    title = stored.title.ifBlank { imported.title },
                    artistsText = stored.artistsText.takeUnless { it.isNullOrBlank() } ?: imported.artistsText,
                    durationText = stored.durationText.takeUnless { it.isNullOrBlank() } ?: imported.durationText,
                    thumbnailUrl = stored.thumbnailUrl.takeUnless { it.isNullOrBlank() } ?: imported.thumbnailUrl,
                    likedAt = stored.likedAt ?: imported.likedAt,
                    totalPlayTimeMs = maxOf(stored.totalPlayTimeMs, imported.totalPlayTimeMs),
                ).apply { playCount = stored.playCount }
            } ?: imported
        })
    }
}

/** Maps a service import without replacing existing metadata and records actual positions for matching. */
internal suspend fun importServiceSongsToPlaylist(
    playlist: Playlist?,
    songs: List<Song>,
    onImported: (index: Int, position: Int) -> Unit = { _, _ -> },
) {
    Database.transaction {
        check(playlist == null || (canEditPlaylistMembership(playlist) &&
            (playlist.id <= 0 || canEditStoredPlaylistMembership(playlist.id)))) { "Playlist membership is read-only" }
        if (playlist != null) mapIgnore(playlist, *songs.toTypedArray())
        else songs.forEach { songTable.insertIgnore(it) }
        songs.forEachIndexed { index, song ->
            val position = if (playlist != null) songPlaylistMapTable.findPositionOf(song.id, playlist.id) else index
            importSongTable.insert(ImportSong(originalId = song.id, position = position, playlistId = playlist?.id))
            onImported(index, position)
        }
    }
}
