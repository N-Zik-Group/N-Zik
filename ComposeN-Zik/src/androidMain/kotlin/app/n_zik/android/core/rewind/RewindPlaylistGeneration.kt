package app.n_zik.android.core.rewind

import app.it.fast4x.rimusic.models.Playlist
import app.n_zik.android.core.database.Database
import kotlinx.coroutines.flow.first
import timber.log.Timber

private const val TAG = "RewindPlaylistGeneration"

/**
 * Write mode of [generateRewindPlaylist] (spec 2 — the single write path of every
 * `rewind-*` playlist):
 */
internal enum class GenerateMode {

    /**
     * Worker path: create the playlist only if it does not exist yet. An existing
     * playlist is left intact (idempotent WorkManager re-run, and a user deletion is
     * never resurrected by the cycle — only the deck pill can bring it back).
     */
    CreateIfMissing,

    /**
     * Button / post-import job path: delete the existing playlist first (its
     * `SongPlaylistMap` mappings cascade away) and recreate it. The click or the
     * import is an explicit "recompute now" act.
     */
    Regenerate
}

/**
 * The shared generation function of every `rewind-*` playlist (spec 2): the monthly/yearly
 * workers ([GenerateMode.CreateIfMissing]), the deck's regenerate pills and the
 * post-import job ([GenerateMode.Regenerate]) all funnel through this function — no other
 * code writes `rewind-*` names.
 *
 * Window -> [Database.eventTable.findSongsMostPlayedBetween] (the `BETWEEN`-inclusive
 * window in epoch millis, results in `ORDER BY SUM(playtime) DESC`) -> (re)create the
 * playlist and map each song in top order, so `SongPlaylistMap.position` becomes 0,1,2…
 * in top order — the "Rewind Top" snapshot read back by `sortSongsByPosition`.
 *
 * An empty window is a no-op: nothing is created or modified, the playlist (if it
 * exists) is left intact, and 0 is returned so the caller shows no success feedback.
 *
 * @param name the language-neutral database name (`rewind-monthly:YYYYMM`,
 *        `rewind-yearly:YYYY`, `rewind-alltime`)
 * @param from window start, epoch millis
 * @param to window end, epoch millis
 * @param mode see [GenerateMode]
 * @return the number of songs written into the playlist (0 = no-op / skipped)
 */
internal suspend fun generateRewindPlaylist(
    name: String,
    from: Long,
    to: Long,
    mode: GenerateMode
): Int {
    val songs = Database.eventTable.findSongsMostPlayedBetween(from, to).first()
    if (songs.isEmpty()) {
        Timber.tag(TAG).i("Empty rewind window for \"$name\": nothing created or modified")
        return 0
    }
    if (mode == GenerateMode.CreateIfMissing &&
        Database.playlistTable.findByName(name).first() != null
    ) {
        Timber.tag(TAG).i("Rewind playlist \"$name\" already exists: skipping creation")
        return 0
    }
    Database.asyncTransaction {
        // Re-check inside the transaction (delete-then-recreate must hold even if a
        // concurrent write raced between the check above and this block).
        val existing = playlistTable.getAll().firstOrNull {
            it.name.trim().equals(name.trim(), ignoreCase = true)
        }
        if (existing != null) {
            playlistTable.delete(existing)
        }
        val playlistId = playlistTable.insert(Playlist(name = name))
        songs.forEach { song ->
            songTable.insertIgnore(song)
            // `map` appends at MAX(position)+1 -> positions 0..n-1 in top order
            songPlaylistMapTable.map(song.id, playlistId)
        }
    }
    Timber.tag(TAG).i("Wrote ${songs.size} songs into rewind playlist \"$name\" (mode=$mode)")
    return songs.size
}
