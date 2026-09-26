package app.n_zik.android.core.migration

import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongAlbumMapTable
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.database.SongTable
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Convergence of the denormalized artist-name copies, re-run on every app launch (offline).
 *
 * The artist's name lives in the [Artist] row (source of truth, id = browse id) AND in
 * denormalized copies that are never joined: [app.it.fast4x.rimusic.models.Song.artistsText]
 * (Home Songs list) and [app.it.fast4x.rimusic.models.Album.authorsText] (album page).
 * After a rename (artist page, song/album author edit, dedup pin/merge) the copies stay
 * stale until the next playback reconcile. This converges them in both directions,
 * always by id/links — never by guessing:
 *
 * 1. Rename on the artist page -> [propagateRename] rewrites every linked copy
 * (songs via [SongArtistMap], albums via their songs) in the same transaction.
 * 2. Song/album author edit that is a 1:1 rename (exactly one token changed, the rest
 * identical) matching exactly one linked row -> that row is renamed (`modified:`) and
 * propagated to all of its copies; 0 or 2+ candidate rows -> local `modified:` write only.
 * 3. This launch sweep rewrites the copies from the links: when EVERY name of a
 * non-custom copy is backed by a linked row (a valid browse id — whole-token, or
 * each part of a duet "A & B" with the spaced separator), the copy is wiped and
 * rewritten with the COMPLETE list of linked row names (link order, cleaned+
 * trimmed names, distinct case-insensitively) — repairs case drift, prefix
 * leaks, duets, duplicates and partial lists; a copy carrying a name without a
 * linked row is skipped, the playback reconcile repairs it later. A custom
 * `modified:` copy is never touched by the sweep.
 *
 * Safety rules (all of them in the pure functions below, so they are unit-tested):
 * - the only rename candidates are the rows LINKED to the edited entity; no global
 *   search, no similarity, no substring replace;
 * - a token is the result of [cleanPrefix] applied to the whole text AND to every
 *   token (a leaked `modified:` inside a token is cleaned and matched as the name
 *   itself), split on `,`, trimmed; matching is whole-token, case-insensitive
 *   ("Aran" never touches "Aran One");
 * - a copy follows a rename only if its token equals the row's current name (the
 *   agreement rule) — an assumed divergence is preserved; the copy's `modified:` prefix
 *   is kept on replace;
 * - 0 or 2+ candidate rows -> no action on any row (local write only), in doubt nothing
 *   moves;
 * - no prefix is ever inserted at a token position: row names are cleaned before they
 *   are embedded in a copy, and user input is normalized with [normalizeText] — the
 *   `modified:` marker is applied once, at the head, so copies stay displayable via
 *   [cleanPrefix];
 * - a write only happens when the text actually changes; the sweep is idempotent (a
 *   converged database yields zero writes), so a failed run heals itself on the next
 *   launch.
 *
 * No toast: the changes are visible in the song/album lists, the sweep only logs
 * (tag `NameConvergence`): one line per rewritten copy, one line per skipped copy
 * WITH the reason (unbacked token(s) + the available linked row names, missing
 * links, album skip reasons), and a final `name convergence: changed=N | ...`
 * summary with the per-reason skip counters.
 *
 * Wired in [MainApplication] onCreate as the LAST pass of the sequential boot chain
 * (`DbCleanup` -> same-name dedup -> this sweep, one coroutine, one pass after the
 * other), so the dedup merges/pins are already in place. The sweep never mutates any
 * link, so a state left mid-way by a previous launch heals itself (idempotent).
 */
object NameConvergence {
    private const val TAG = "NameConvergence"

    /**
     * The launch pass, called from the sequential boot chain in [MainApplication]
     * (`DbCleanup` -> same-name dedup -> this sweep) — strictly AFTER the dedup
     * pass has committed its pins/merges (the chain is one sequential coroutine,
     * not concurrent).
     * Suspend: the chain is one sequential coroutine on the pool thread; the thread
     * is released while the network-bound dedup pass before it awaits its searches,
     * and this pass starts only once that one has finished.
     */
    internal suspend fun runPass() {
        runSweep(
            songTable = Database.songTable,
            artistTable = Database.artistTable,
            albumTable = Database.albumTable,
            songArtistMapTable = Database.songArtistMapTable,
            songAlbumMapTable = Database.songAlbumMapTable,
        )
    }

    /**
     * The sweep core, parameterized by the DAOs so it can be exercised in unit tests
     * against an in-memory database.
     *
     * Rewrites the stale copies from the links, never the other way around:
     * - song copies: when every token of [app.it.fast4x.rimusic.models.Song.artistsText]
     *   is covered (whole token or duet "A & B", ignoreCase) by the cleaned+trimmed
     *   name of one of the song's linked rows, the copy is rewritten with the
     *   COMPLETE list of those linked names (link order, cleaned+trimmed names,
     *   distinct case-insensitively) — repairs prefix leaks, duets, duplicates and
     *   completes partial lists; a name without a linked row skips the whole copy
     *   (the playback reconcile repairs it later);
     * - album copies: every song of the album must carry at least one link, and every
     *   song's set of linked names must be identical (an album mixing {Aran} and
     *   {Aran, B} is left alone); the album text is then rewritten from that shared
     *   set — re-cased in place when the names already match, or replaced whole when
     *   they diverged — but only when the copy carries the same number of names
     *   (the sweep refreshes, it never adds or removes a name).
     *
     * Custom `modified:` copies and copies without any link are untouched.
     *
     * Every rewritten copy and every skipped copy is logged with its reason
     * (unbacked token(s) + available rows, missing links, album skip reasons),
     * followed by a one-line summary with the per-reason skip counters — a
     * fully converged database logs zero change lines and zero unbacked/unlinked
     * skips.
     *
     * @return the number of copies rewritten (0 on a converged database)
     */
    internal suspend fun runSweep(
        songTable: SongTable,
        artistTable: ArtistTable,
        albumTable: AlbumTable,
        songArtistMapTable: SongArtistMapTable,
        songAlbumMapTable: SongAlbumMapTable,
    ): Int {
        // songId -> cleaned+trimmed names of its linked rows (one cached lookup
        // per row id; trimmed because the live database has row names with
        // trailing spaces)
        val pairs = songArtistMapTable.allPairsDirect()
        val nameCache = HashMap<String, String?>()
        fun rowNames(artistIds: List<String>): List<String> =
            artistIds.mapNotNull { id ->
                nameCache.getOrPut(id) { artistTable.findByIdDirect(id)?.name }
                    ?.let { cleanPrefix(it).trim() }
                    ?.takeIf { it.isNotBlank() }
            }
        val namesBySong: Map<String, List<String>> =
            pairs.groupBy { it.songId }.mapValues { (_, links) -> rowNames(links.map { it.artistId }) }

        var changed = 0
        var unbackedSongs = 0
        var unlinkedSongs = 0
        var customSongs = 0
        var unlinkedAlbums = 0
        var inconsistentAlbums = 0
        var countMismatchAlbums = 0
        var customAlbums = 0

        // 1) Song copies
        val songs = songTable.all().first()
        for (song in songs) {
            val text = song.artistsText ?: continue
            if (text.startsWith(MODIFIED_PREFIX)) {
                customSongs++ // custom copies are untouchable
                continue
            }
            val rowNames = namesBySong[song.id].orEmpty()
            if (rowNames.isEmpty()) {
                unlinkedSongs++ // no link: nothing to converge against
                Timber.tag(TAG).d("song %s: skipped (no linked rows) - \"%s\"", song.id, text)
                continue
            }
            convergeText(text, rowNames)?.let { converged ->
                songTable.updateArtists(song.id, converged)
                changed++
                Timber.tag(TAG).d("song %s: \"%s\" -> \"%s\"", song.id, text, converged)
            } ?: run {
                val uncovered = uncoveredTokens(text, rowNames)
                if (uncovered.isNotEmpty()) {
                    unbackedSongs++
                    Timber.tag(TAG).d(
                        "song %s: skipped (unbacked: %s) - \"%s\" [rows: %s]",
                        song.id,
                        uncovered.joinToString(" | "),
                        text,
                        rowNames.joinToString(", ")
                    )
                }
                // else: already converged, no write, nothing to log
            }
        }

        // 2) Album copies
        val albums = albumTable.all().first()
        for (album in albums) {
            val text = album.authorsText ?: continue
            if (text.startsWith(MODIFIED_PREFIX)) {
                customAlbums++ // custom copies are untouchable
                continue
            }
            val albumSongs = songAlbumMapTable.allSongsOfDirect(album.id)
            if (albumSongs.isEmpty()) continue
            // Every song of the album must carry the same non-empty set of linked
            // names; a song without any link silences the whole album. The sets are
            // compared order-insensitively (the link order can vary per song), while
            // the first song's order is kept for the rewrite.
            var consistent = true
            var silentSongId: String? = null
            var sharedNames: List<String>? = null
            for (albumSong in albumSongs) {
                val names = namesBySong[albumSong.id].orEmpty()
                if (names.isEmpty()) {
                    consistent = false
                    silentSongId = albumSong.id
                    break
                }
                when {
                    sharedNames == null -> sharedNames = names
                    sharedNames.toHashSet() != names.toHashSet() -> {
                        consistent = false
                        break
                    }
                }
            }
            if (!consistent) {
                if (silentSongId != null) {
                    unlinkedAlbums++
                    Timber.tag(TAG).d(
                        "album %s: skipped (song %s has no linked rows) - \"%s\"",
                        album.id, silentSongId, text
                    )
                } else {
                    inconsistentAlbums++
                    Timber.tag(TAG).d(
                        "album %s: skipped (songs disagree on linked names) - \"%s\"",
                        album.id, text
                    )
                }
                continue
            }
            val shared = sharedNames.orEmpty()
            convergeAlbumText(text, shared)?.let { converged ->
                albumTable.updateAuthors(album.id, converged)
                changed++
                Timber.tag(TAG).d("album %s: \"%s\" -> \"%s\"", album.id, text, converged)
            } ?: run {
                if (albumCountMismatch(text, shared)) {
                    countMismatchAlbums++
                    Timber.tag(TAG).d(
                        "album %s: skipped (name count %d != linked %d) - \"%s\"",
                        album.id, nameCount(text), shared.filter { it.isNotBlank() }.size, text
                    )
                }
                // else: already converged, no write, nothing to log
            }
        }

        Timber.tag(TAG).i(
            "name convergence: changed=%d | song skips: unbacked=%d, unlinked=%d, custom=%d" +
                " | album skips: unlinked=%d, inconsistent=%d, countMismatch=%d, custom=%d",
            changed, unbackedSongs, unlinkedSongs, customSongs,
            unlinkedAlbums, inconsistentAlbums, countMismatchAlbums, customAlbums
        )

        return changed
    }

    /**
     * Propagates an artist row rename to every copy linked to the row: the songs
     * directly mapped to it, and the albums containing at least one such song.
     *
     * A copy is only rewritten when one of its tokens equals [oldName] (whole token,
     * ignoreCase — the agreement rule); all occurrences of that token become
     * [newName], the copy's `modified:` prefix is kept, and the user's token order is
     * preserved. Divergent copies (token differs from the row's old name) are left
     * alone. No write happens when the text would not actually change.
     *
     * Non-suspend, DAO-direct: call it from inside a [Database.syncTransaction]
     * block (the dialogs do, wrapped in [Database.asyncTransaction] for the
     * lock-retry), so the row rename and every copy write land in one
     * transaction.
     *
     * Both names are [cleanPrefix]ed at entry, so a prefixed row name never reaches
     * a copy.
     *
     * @param oldName the row's current name (may carry the `modified:` prefix)
     * @param newName the row's new name (may carry the `modified:` prefix)
     * @return the number of copies rewritten
     */
    internal fun propagateRename(
        db: Database,
        artistId: String,
        oldName: String,
        newName: String,
    ): Int {
        // Trimmed where they are cleaned: the live database has row names with
        // trailing spaces, and an untrimmed old/new would silently reach zero
        // copies (the agreement rule is a whole-token equality).
        val old = cleanPrefix(oldName).trim()
        val new = cleanPrefix(newName).trim()
        if (old.isBlank()) return 0
        var changed = 0
        for (song in db.songArtistMapTable.allSongsByDirect(artistId)) {
            val text = song.artistsText ?: continue
            replaceToken(text, old, new)?.let { converged ->
                db.songTable.updateArtists(song.id, converged)
                changed++
            }
        }
        for (album in db.songAlbumMapTable.albumsOfArtistDirect(artistId)) {
            val text = album.authorsText ?: continue
            replaceToken(text, old, new)?.let { converged ->
                db.albumTable.updateAuthors(album.id, converged)
                changed++
            }
        }
        // Diagnostics: a rename that rewrites no copy at all (divergent copies,
        // no links, or an agreement-rule mismatch) is otherwise silent.
        if (changed == 0) {
            Timber.tag(TAG).d(
                "propagateRename %s \"\"%s\"\" -> \"\"%s\"\": 0 copies rewritten (divergent or unlinked)",
                artistId, old, new
            )
        }
        return changed
    }

    /**
     * Detects a 1:1 rename between two author texts: exactly one token changed, the
     * rest identical (order preserved, case-insensitive comparison).
     *
     * Token convention: [cleanPrefix] the text, split on `,`, trim, drop blanks —
     * the same convention as the YTM `artistsText` lists.
     *
     * @param oldText the previously stored (cleaned) text
     * @param newText the text the user just entered
     * @return the (old token, new token) pair, or null when the change is not a 1:1
     * rename (tokens added/removed, reordered, or more than one token changed)
     */
    internal fun diffSingleRename(oldText: String, newText: String): Pair<String, String>? {
        val oldTokens = tokensOf(oldText)
        val newTokens = tokensOf(newText)
        if (oldTokens.size != newTokens.size) return null
        var oldToken: String? = null
        var newToken: String? = null
        for (i in oldTokens.indices) {
            if (!oldTokens[i].equals(newTokens[i], ignoreCase = true)) {
                if (oldToken != null) return null // a second token changed: not a 1:1 rename
                oldToken = oldTokens[i]
                newToken = newTokens[i]
            }
        }
        if (oldToken == null || newToken == null) return null // nothing changed
        return oldToken to newToken
    }

    /**
     * The sweep rule for one copy. Guard: EVERY token of the copy must be covered
     * by the cleaned+trimmed name of one of the linked rows — either a whole-token
     * match, or a duet token "A & B" (separator exactly " & " WITH surrounding
     * spaces, never a bare "&") whose every part is covered. A name without a
     * linked row (missing id) — or a blank/unbacked duet part — skips the whole
     * copy, the playback reconcile repairs it later. When the guard passes, the
     * copy is wiped and rewritten with the COMPLETE list of linked row names
     * (link order, cleaned+trimmed names, DISTINCT case-insensitively — the first
     * occurrence wins, so a song linked to two rows of the same name displays it
     * once): that repairs prefix leaks, duets, broken displays, duplicates, and
     * completes partial lists.
     *
     * @param text the stored copy, possibly with a `modified:` custom prefix
     * @param rowNames the names of the linked rows, in link order (may carry the
     * `modified:` prefix)
     * @return the full distinct list of linked names, or null when the copy is
     * custom (`modified:`), has no tokens or no linked rows, carries a name
     * without a linked row (skip), or is already converged (no write)
     */
    internal fun convergeText(text: String, rowNames: List<String>): String? {
        if (text.startsWith(MODIFIED_PREFIX)) return null // custom copies are untouchable
        val tokens = tokensOf(text)
        if (tokens.isEmpty()) return null
        // Row names are cleaned AND trimmed where they are cleaned: the live
        // database has row names with trailing spaces (e.g. "RoughSketch  ").
        val cleanedRowNames = rowNames
            .map { cleanPrefix(it).trim() }
            .filter { it.isNotBlank() }
        if (cleanedRowNames.isEmpty()) return null
        if (tokens.any { !isCoveredByRowNames(it, cleanedRowNames) }) return null
        // The full list is distinct case-insensitively, keeping the first
        // occurrence in link order (duplicated rows never yield a double name).
        val distinctNames = mutableListOf<String>()
        for (name in cleanedRowNames) {
            if (distinctNames.none { it.equals(name, ignoreCase = true) }) {
                distinctNames.add(name)
            }
        }
        val converged = distinctNames.joinToString(SEPARATOR)
        // no-write rule: compare against the ORIGINAL text (a leak, a duet, a
        // duplicate or a broken display is a real change even when the tokens
        // already match)
        return if (converged == text) null else converged
    }

    /**
     * Coverage of one copy token by the cleaned+trimmed row names: a whole-token
     * match (ignoreCase), or a duet "A & B" (separator exactly " & " WITH
     * surrounding spaces — a bare "&" is never a separator, "COOL&CREATE" stays
     * one whole token) whose every part (cleaned + trimmed, whole-part match,
     * ignoreCase) is itself backed by a row name; a blank or unbacked part means
     * the token is NOT covered.
     */
    private fun isCoveredByRowNames(token: String, cleanedRowNames: List<String>): Boolean {
        if (cleanedRowNames.any { it.equals(token, ignoreCase = true) }) return true
        val parts = token.split(" & ").map { cleanPrefix(it.trim()).trim() }
        return parts.size > 1 &&
            parts.all { part ->
                part.isNotBlank() && cleanedRowNames.any { it.equals(part, ignoreCase = true) }
            }
    }

    /**
     * The copy's tokens that NO cleaned+trimmed row name backs (same coverage
     * rule as [convergeText]: whole token or every part of a duet). The sweep
     * logs exactly this list when it skips a copy, so a skipped copy is
     * diagnosable from the log alone.
     *
     * Empty when the copy is custom (`modified:`), has no tokens, or the linked
     * rows have no cleaned name at all (nothing to cover against).
     */
    internal fun uncoveredTokens(text: String, rowNames: List<String>): List<String> {
        if (text.startsWith(MODIFIED_PREFIX)) return emptyList()
        val tokens = tokensOf(text)
        if (tokens.isEmpty()) return emptyList()
        val cleanedRowNames = rowNames
            .map { cleanPrefix(it).trim() }
            .filter { it.isNotBlank() }
        if (cleanedRowNames.isEmpty()) return emptyList()
        return tokens.filter { !isCoveredByRowNames(it, cleanedRowNames) }
    }

    /**
     * True when an album copy carries a different NUMBER of names than the
     * shared row-name set: the refresh-only album rule then skips it (a name is
     * never added or removed by the sweep), so the mismatch is the reason the
     * copy was not converged.
     */
    internal fun albumCountMismatch(text: String, sharedNames: List<String>): Boolean =
        nameCount(text) != sharedNames.filter { it.isNotBlank() }.size

    /** The number of name tokens of a copy text ([tokensOf] convention). */
    internal fun nameCount(text: String): Int = tokensOf(text).size

    /**
     * The propagation rule for one copy: replaces every whole token of [text] that
     * equals [oldToken] (ignoreCase) with [newToken] CLEANED (ignoreCase match on
     * the token, [cleanPrefix] on the replacement), keeping all other tokens, the
     * user's token order, and the copy's `modified:` prefix — a prefix is never
     * inserted at a token position, the copy must stay displayable via [cleanPrefix].
     *
     * Whole-token match only: "Aran" never touches "Aran One".
     *
     * @return the rebuilt text, or null when no token matched (divergent copy — left
     * alone) or when the text would not actually change (no write)
     */
    internal fun replaceToken(text: String, oldToken: String, newToken: String): String? {
        val cleanNew = cleanPrefix(newToken)
        if (oldToken.isBlank() || cleanNew.isBlank()) return null
        val tokens = tokensOf(text)
        if (tokens.isEmpty()) return null
        var replaced = false
        val newTokens = tokens.map { token ->
            if (token.equals(oldToken, ignoreCase = true)) {
                replaced = true
                cleanNew
            } else {
                token
            }
        }
        if (!replaced) return null
        val prefix = if (text.startsWith(MODIFIED_PREFIX)) MODIFIED_PREFIX else ""
        val rebuilt = prefix + newTokens.joinToString(SEPARATOR)
        return if (rebuilt == text) null else rebuilt
    }

    /**
     * The candidate resolution of the song/album author dialogs: the linked rows whose
     * cleaned name equals [oldToken] (whole token, ignoreCase).
     *
     * @return the single candidate row, or null when there is none (the token has no
     * linked row) or two or more (ambiguity — in doubt, nothing moves)
     */
    internal fun uniqueCandidate(linkedArtists: List<Artist>, oldToken: String): Artist? {
        val matches = linkedArtists.filter {
            // trimmed like the sweep's row names: the live database has row
            // names with trailing spaces, an untrimmed name never matches
            cleanPrefix(it.name ?: "").trim().equals(oldToken, ignoreCase = true)
        }
        return if (matches.size == 1) matches.first() else null
    }

    /**
     * The sweep rule for an album copy: [sharedNames] is the single set of cleaned
     * row names shared by every song of the album (in the order of the first song's
     * links). The album text is rewritten only when it carries the same NUMBER of
     * names as that set — a copy with fewer or more tokens would lose or gain a
     * name, so it is left alone (the sweep refreshes, it never adds).
     *
     * When the tokens are exactly the shared names (ignoreCase), each token is
     * re-cased to the row name in place and the user's token order is preserved;
     * when the names have diverged (a rename the copy never received), the shared
     * set replaces the whole list, in the order of the links.
     *
     * @return the converged authors text, or null when the name count differs or
     * the copy is already converged (no write)
     */
    internal fun convergeAlbumText(text: String, sharedNames: List<String>): String? {
        val tokens = tokensOf(text)
        if (tokens.isEmpty()) return null
        val shared = sharedNames.filter { it.isNotBlank() }
        if (shared.size != tokens.size) return null // no name added, no name lost
        val sharedCounts = countsOf(shared.map { it.lowercase() })
        if (sharedCounts == countsOf(tokens.map { it.lowercase() })) {
            // Same names modulo case: re-case in place, preserving the user's order.
            val converged = tokens.joinToString(SEPARATOR) { token ->
                shared.first { it.equals(token, ignoreCase = true) }
            }
            return if (converged == tokens.joinToString(SEPARATOR)) null else converged
        }
        // Divergent names (e.g. a rename the copy never received): the shared set
        // replaces the whole list, in the order of the links.
        val converged = shared.joinToString(SEPARATOR)
        return if (converged == tokens.joinToString(SEPARATOR)) null else converged
    }

    /**
     * Normalizes user input for a custom `modified:` write: the tokens (see
     * [tokensOf]) re-joined with `", "` — every leading or leaked prefix cleaned, a
     * single clean list. The caller applies the storage marker once, at the head:
     * a prefix is never embedded at a token position.
     */
    internal fun normalizeText(text: String): String =
        tokensOf(text).joinToString(SEPARATOR)

    /**
     * Tokenizes an author text with the shared convention: [cleanPrefix] applied to
     * the whole text AND to every token (a leaked `modified:` inside a token is
     * cleaned and matched as the name itself), then split on `,`, trimmed, blanks
     * dropped.
     */
    private fun tokensOf(text: String): List<String> =
        cleanPrefix(text).split(",")
            .map { cleanPrefix(it.trim()).trim() }
            .filter { it.isNotBlank() }

    /** Case-insensitive frequency map (multiset), for the album sweep's set equality. */
    private fun countsOf(values: List<String>): Map<String, Int> = values.groupBy { it }.mapValues { it.value.size }

    private const val SEPARATOR = ", "
}
