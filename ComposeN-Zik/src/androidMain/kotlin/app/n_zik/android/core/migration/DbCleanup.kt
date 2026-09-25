package app.n_zik.android.core.migration

import android.content.Context
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.SongArtistMap
import app.it.fast4x.rimusic.utils.splitArtistNames
import app.n_zik.android.R
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.rewind.RewindPostImportRegenerationWorker
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.kreate.android.me.knighthat.utils.Toaster
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Cleanup of polluted artist↔song links, re-run on every app launch.
 *
 * The old mapping was add-only: YTM author lists vary by playback context (a
 * channel playlist or radio credits the channel as an "author"), so
 * `SongArtistMap` accumulated the union of every context ever seen — a
 * channel-derived artist row could collect plays of hundreds of unrelated
 * songs and rank as a top artist in the Rewind deck. Playback-time
 * reconciliation (see `Database.insertIgnore` / `Database.upsert`) now stops
 * new pollution and self-heals songs that get replayed; this cleanup removes
 * the remaining stale links, including ones that reappear through database
 * imports, without having to replay anything.
 *
 * Rule: a (song, artist) link is stale when the artist's name does not appear
 * in the song's stored artist list. Custom values are untouchable — a
 * `modified:` artist name or a `modified:` song artist text is never judged,
 * and a song without any artist text keeps all its links.
 *
 * Idempotent and cheap: a single read pass over `SongArtistMap` plus one
 * cached name lookup per id, on the background DATA dispatcher. A clean
 * database yields zero deletions, so re-running it on every launch is safe
 * and also means a failed run heals itself on the next launch.
 *
 * Wired in [MainApplication] onCreate, right after [MonthlyPlaylistCleanup.run].
 */
object DbCleanup {
    private const val TAG = "DbCleanup"

    /**
     * Schedules the cleanup on [NzikDispatchers.DATA] and logs failures.
     * Runs on every launch, so a failed attempt retries on the next one.
     */
    fun run(context: Context) {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runCatching {
                val removed = runClean(Database.songTable, Database.artistTable, Database.songArtistMapTable)
                if (removed > 0) {
                    // The mapping feeds the Rewind deck: recompute its playlists from the healed data
                    RewindPostImportRegenerationWorker.schedule(context)
                    withContext(NzikDispatchers.UI) {
                        // Format the message here: `Toaster.s(Int, Int)` would bind an Int
                        // count to the `duration` parameter of the no-vararg overload,
                        // leaving the `%d` format specifier unfilled.
                        runCatching {
                            Toaster.s(context.getString(R.string.db_artist_link_cleanup_toast, removed))
                        }.onFailure { e ->
                            Timber.tag(TAG).w(e, "Cleanup succeeded but toast failed")
                        }
                    }
                }
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Artist link cleanup failed (will retry on next launch)")
            }
        }
    }

    /**
     * The cleanup core, parameterized by the DAOs so it can be exercised in
     * unit tests with mocks.
     *
     * @return the number of stale links removed (0 when nothing matched)
     */
    internal suspend fun runClean(
        songTable: SongTable,
        artistTable: ArtistTable,
        mapTable: SongArtistMapTable,
    ): Int {
        val pairs = mapTable.allPairsDirect()
        if (pairs.isEmpty()) return 0

        val songs = songTable.all().first().associateBy { it.id }
        val artistNameCache = HashMap<String, String?>()
        fun artistName(artistId: String): String? =
            artistNameCache.getOrPut(artistId) { artistTable.findByIdDirect(artistId)?.name }

        var removed = 0
        for (pair in pairs) {
            val song = songs[pair.songId] ?: continue // no stored artist list: nothing to judge against
            if (isSuspiciousArtistLink(artistName(pair.artistId), song.artistsText)) {
                mapTable.deletePairDirect(pair.songId, pair.artistId)
                removed++
            }
        }

        Timber.tag(TAG).i("Artist link cleanup: removed %d stale link(s)", removed)
        return removed
    }

    /**
     * Pure decision rule, testable without any database.
     *
     * @param artistName name of the mapped [Artist] row (may carry the `modified:` custom prefix)
     * @param songArtistsText stored artist list of the [app.it.fast4x.rimusic.models.Song]
     * (may carry the `modified:` custom prefix)
     * @return true when the link should be removed
     */
    internal fun isSuspiciousArtistLink(artistName: String?, songArtistsText: String?): Boolean {
        // Custom values are untouchable: a user-modified artist name or a user-modified
        // song artist text is not a YTM author list, so it cannot be used to judge links.
        if (artistName?.startsWith(MODIFIED_PREFIX, true) == true) return false
        if (songArtistsText?.startsWith(MODIFIED_PREFIX, true) == true) return false
        // No source of truth for this song (no artist text) -> keep every link.
        if (songArtistsText.isNullOrBlank()) return false
        if (artistName.isNullOrBlank()) return false
        val songArtists = cleanPrefix(songArtistsText).splitArtistNames()
        if (songArtists.isEmpty()) return false
        return songArtists.none { it.equals(artistName, ignoreCase = true) }
    }
}
