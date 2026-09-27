package app.n_zik.android.core.migration

import android.content.Context
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.R
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.maintenance.DbCleanupLinkRecord
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
 * Wired in [MainApplication] onCreate as the FIRST pass of the sequential
 * artist-data boot chain (`DbCleanup` -> same-name dedup -> name convergence).
 */
object DbCleanup {
    private const val TAG = "DbCleanup"

    /**
     * The outcome of one successful artist link cleanup pass: the number of stale links
     * removed plus the per-link records the boot chain persists (the Maintenance sheet
     * lists them in the expanded "Artist link cleanup" row).
     */
    internal data class DbCleanupResult(
        val removed: Int,
        val removedLinks: List<DbCleanupLinkRecord>,
    )

    /**
     * Schedules the cleanup on [NzikDispatchers.DATA] and logs failures.
     * Runs on every launch, so a failed attempt retries on the next one.
     */
    fun run(context: Context) {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runCatching { runPass(context) }
                .onFailure { e ->
                    Timber.tag(TAG).e(e, "Artist link cleanup failed (will retry on next launch)")
                }
        }
    }

    /**
     * The cleanup pass, called from the sequential boot chain in [MainApplication]
     * (`DbCleanup` -> same-name dedup -> name convergence) or from [run].
     *
     * @return the [DbCleanupResult] of the pass (removed counter + per-link records)
     * so the boot chain can persist the last successful run
     */
    internal suspend fun runPass(context: Context): DbCleanupResult {
        val result = runClean(Database.songTable, Database.artistTable, Database.songArtistMapTable)
        if (result.removed > 0) {
            // The mapping feeds the Rewind deck: recompute its playlists from the healed data
            RewindPostImportRegenerationWorker.schedule(context)
            withContext(NzikDispatchers.UI) {
                // Format the message here: `Toaster.s(Int, Int)` would bind an Int
                // count to the `duration` parameter of the no-vararg overload,
                // leaving the `%d` format specifier unfilled.
                runCatching {
                    Toaster.s(context.getString(R.string.db_artist_link_cleanup_toast, result.removed))
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "Cleanup succeeded but toast failed")
                }
            }
        }
        return result
    }

    /**
     * The cleanup core, parameterized by the DAOs so it can be exercised in
     * unit tests with mocks.
     *
     * @return the [DbCleanupResult] of the sweep (removed counter + per-link records,
     * empty when nothing matched)
     */
    internal suspend fun runClean(
        songTable: SongTable,
        artistTable: ArtistTable,
        mapTable: SongArtistMapTable,
    ): DbCleanupResult {
        val pairs = mapTable.allPairsDirect()
        if (pairs.isEmpty()) return DbCleanupResult(0, emptyList())

        val songs = songTable.all().first().associateBy { it.id }
        val artistNameCache = HashMap<String, String?>()
        fun artistName(artistId: String): String? =
            artistNameCache.getOrPut(artistId) { artistTable.findByIdDirect(artistId)?.name }

        var removed = 0
        val removedLinks = mutableListOf<DbCleanupLinkRecord>()
        for (pair in pairs) {
            val song = songs[pair.songId] ?: continue // no stored artist list: nothing to judge against
            val name = artistName(pair.artistId)
            if (isSuspiciousArtistLink(name, song.artistsText)) {
                Timber.tag(TAG).d(
                    "stale link removed: song=%s \"%s\" artist=\"%s\" artistsText=\"%s\"",
                    pair.songId, song.title, name, song.artistsText
                )
                mapTable.deletePairDirect(pair.songId, pair.artistId)
                removed++
                removedLinks += DbCleanupLinkRecord(song.title, name)
            }
        }

        Timber.tag(TAG).i("Artist link cleanup: removed %d stale link(s)", removed)
        return DbCleanupResult(removed, removedLinks)
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
        val cleaned = cleanPrefix(songArtistsText)
        // The artist name must appear in the stored list as a whole phrase delimited
        // by non-letter characters (list separators, punctuation, start/end of the
        // string). The previous split-based matching (splitArtistNames) destroyed
        // legitimate names carrying a delimiter or a localized conjunction substring
        // - "COOL&CREATE" broke into ["COOL", "CREATE"], and "Grand Corps Malade" was
        // cut at the "and" inside "Grand" (the localized conjunction word on the
        // device) - so the sweep removed those links on every launch (device captures
        // 2026-09-26: 11:40 / 11:59 / 12:02). A raw substring match would be too
        // permissive (junk fragments), so both ends of the name are pinned against
        // Unicode letters (\p{L}). Java's \b word boundary does not exist around CJK
        // characters, so letter-category anchors are used instead: CJK names still
        // match, while accented-name fragments ("gr" inside "Grégoire") do not. The
        // rule is locale-independent: it no longer depends on the localized
        // conjunction list at all.
        val phrase = Regex("(?<!\\p{L})" + Regex.escape(artistName) + "(?!\\p{L})", RegexOption.IGNORE_CASE)
        return !phrase.containsMatchIn(cleaned)
    }
}
