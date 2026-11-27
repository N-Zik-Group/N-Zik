package app.n_zik.android.utils.playlist

import android.content.SharedPreferences
import app.n_zik.android.enums.PlaylistSongSortBy
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ImportSong
import app.n_zik.android.core.database.canAddTracksToPlaylist
import app.n_zik.android.core.database.canEditPlaylistMembership
import app.n_zik.android.core.database.canEditStoredPlaylistMembership
import app.n_zik.android.playback.services.isLocal
import kotlinx.coroutines.flow.first

/** Imports metadata and an ordered batch together; call inside Database.asyncTransaction for retries. */
suspend fun importSongsToPlaylist(playlist: Playlist, songs: List<Song>) {
    Database.transaction {
        check(canEditPlaylistMembership(playlist) &&
            (playlist.id <= 0 || canEditStoredPlaylistMembership(playlist.id))) { "Playlist membership is read-only" }
        mergeCsvImportSongs(songs)
        mapIgnore(playlist, *songs.toTypedArray())
    }
}

/** Imports the whole CSV after validating every destination against the songs it would receive. */
internal suspend fun Database.importCsvSongs(
    targetPlaylistId: Long,
    straySongs: List<Song>,
    playlists: Map<Playlist, List<Song>>,
): List<Long> {
    val createdPlaylists = mutableListOf<Long>()
    transaction {
        val destinations = if (targetPlaylistId > 0L) {
            val target = checkNotNull(playlistTable.findById(targetPlaylistId).first()) {
                "Import target playlist no longer exists"
            }
            mapOf(target to (straySongs + playlists.values.flatten()))
        } else playlists
        check(destinations.all { (playlist, songs) -> canAddTracksToPlaylist(playlist, songs.any(Song::isLocal)) }) {
            "Playlist destination cannot accept the CSV tracks"
        }
        if (targetPlaylistId <= 0L) mergeCsvImportSongs(straySongs)
        destinations.forEach { (playlist, songs) ->
            mergeCsvImportSongs(songs)
            val destination = if (playlist.id > 0L) playlist else {
                val id = playlistTable.insert(playlist)
                createdPlaylists.add(id)
                playlist.copy(id = id)
            }
            mapIgnore(destination, *songs.toTypedArray())
        }
    }
    return createdPlaylists
}

/** Stores the source order as the initial track sort for a newly imported playlist. */
internal fun SharedPreferences.saveImportedPlaylistSort(playlistIds: List<Long>) {
    val unsetKeys = playlistIds.map { "PlaylistSongsSortBy_$it" }.filterNot(::contains)
    if (unsetKeys.isEmpty()) return
    val editor = edit()
    unsetKeys.forEach { editor.putString(it, PlaylistSongSortBy.Custom.name) }
    editor.apply()
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
    playlistId: Long?,
    songs: List<Song>,
    onImported: (index: Int, position: Int) -> Unit = { _, _ -> },
) {
    Database.transaction {
        val destination = playlistId?.let { id ->
            checkNotNull(playlistTable.findById(id).first()) { "Import target playlist no longer exists" }
        }
        check(destination == null || canAddTracksToPlaylist(destination, songs.any(Song::isLocal))) {
            "Playlist destination cannot accept the CSV tracks"
        }
        if (destination != null) mapIgnore(destination, *songs.toTypedArray())
        else songs.forEach { songTable.insertIgnore(it) }
        songs.forEachIndexed { index, song ->
            val position = if (destination != null) songPlaylistMapTable.findPositionOf(song.id, destination.id) else index
            importSongTable.insert(ImportSong(originalId = song.id, position = position, playlistId = destination?.id))
            onImported(index, position)
        }
    }
}
