package app.n_zik.android.ui.screens.localplaylist

import kotlinx.coroutines.flow.first
import timber.log.Timber
import app.n_zik.android.LOCAL_KEY_PREFIX
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ImportSong

internal suspend fun reconcileLocalPlaylistImportMatches(playlistId: Long): List<ImportSong> {
    val allEntries = Database.importSongTable.getAllEntries()
    val failedEntries = mutableListOf<ImportSong>()
    for (entry in allEntries) {
        if (entry.playlistId != playlistId) continue
        val count = Database.songTable.countById(entry.originalId)
        val isYouTubeId = entry.originalId.length == 11 && !entry.originalId.startsWith(LOCAL_KEY_PREFIX)
        Timber.tag("MatchPlaylist").d("CLEANUP BDD: originalId='${entry.originalId}' count=$count isYouTubeId=$isYouTubeId")
        if (count > 0) {
            if (isYouTubeId) {
                val song = Database.songTable.findById(entry.originalId).first()
                val dur = song?.durationText ?: "?"
                // Riplay placeholders can retain a valid YouTube ID until a match is resolved.
                if (song != null && song.durationText == "00:00") {
                    Timber.tag("MatchPlaylist").d("CLEANUP BDD: FAIL (Riplay) originalId='${entry.originalId}' - duration='00:00'")
                    failedEntries.add(entry)
                } else {
                    Timber.tag("MatchPlaylist").d("CLEANUP BDD: MATCHED originalId='${entry.originalId}' (duration='$dur')")
                    Database.importSongTable.deleteByOriginalId(entry.originalId)
                }
            } else {
                Timber.tag("MatchPlaylist").d("CLEANUP BDD: FAIL originalId='${entry.originalId}' (placeholder)")
                failedEntries.add(entry)
            }
        } else {
            Timber.tag("MatchPlaylist").d("CLEANUP BDD: MATCHED originalId='${entry.originalId}' (deleted)")
            Database.importSongTable.deleteByOriginalId(entry.originalId)
        }
    }
    return failedEntries
}
