package app.n_zik.android.core.migration

import android.content.Context
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.R
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.maintenance.DedupGroupRecord
import app.n_zik.android.core.maintenance.DedupGroupStatus
import app.n_zik.android.core.maintenance.DedupSkipReason
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.kreate.android.me.knighthat.utils.Toaster
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.artistPage
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Global dedup of same-name artist rows, re-run on every app launch (self-healing).
 *
 * YTM attributes the same artist to several different browse ids depending on the
 * playback context (topic pages, dead channels, empty shells), so one real artist
 * accumulates several same-name [Artist] rows, each collecting its own song links.
 * This sweep merges them back into a single canonical row per group.
 *
 * Decision per group (the YTM search browse id is the arbiter for every group,
 * followed ones included — a followed row can itself be a topic page):
 * 1. Search YTM for the group name -> its artist's browse id is the truth.
 * 2. A stored row whose id equals that browse id IS the truth row: it is kept,
 *    and its name is pinned to the group name with the `modified:` custom prefix.
 * 3. No stored row matches -> the canonical row is created (browse id, group name
 *    with the `modified:` prefix, search thumbnail) and the other rows are merged
 *    into it. No rich data is carried over — background fetches fill it in later.
 * 4. The artist search finds NOTHING -> the group is resolved through the rows'
 *    own songs (some artists are indexed by YTM only through their songs — topic
 *    pages, channels absent from the artist search). Each row proves itself
 *    with a bounded per-row budget of songs ([SONGS_PER_ROW], non-blank
 *    cleaned title): precise YTM song search, same-song
 *    confirmation (result video id == local song id, or exact title + same image
 *    + a byline entry named after the group), then navigation to the artist page
 *    from that byline entry. A candidate is valid only when the artist page
 *    exists and its name equals the group name ("X - Topic" is rejected). The
 *    merge needs UNANIMOUS agreement — every row yields a candidate and all of
 *    them point to one single browse id — and then runs the same merge as above.
 *    A single row no song can prove (unattributed UGC, unconfirmable) freezes
 *    the WHOLE group until its composition or song selection changes — by
 *    design, the fallback never merges a subset of the rows.
 *
 * Merging a duplicate row is atomic: its songs are re-linked onto the truth row
 * first, the user's follow/dislike move onto the truth row when the truth does not
 * carry them yet, and only then the row is deleted — no song can be left orphaned.
 *
 * Image safety (in case of doubt, never merge). Rows are judged against the images
 * of the OTHER STORED rows, not against the search thumbnail — YTM search returns
 * a differently sized/cropped photo of the same artist (round avatar vs banner),
 * so a raw URL comparison against it flags even identical pictures (device capture
 * 2026-09-26: the "Camellia" rows share one stored image yet were all flagged).
 * Stored rows of one artist share the picture they were fetched with:
 * - the truth row carries an image -> that image is the reference;
 * - otherwise (truth without image, or freshly created) -> the strict-majority
 *   image of the group is the reference; a tie, or a lone row without image in a
 *   conflicting group, means doubt -> flagged, not merged.
 *
 * Other safety rules:
 * - `LOCAL_ARTIST_` rows (user-localized, no browse id) are never a source nor a
 *   target of the merge — the YouTube rows of the group are still deduplicated;
 * - a group whose whole name carries the `modified:` prefix is custom user data:
 *   untouchable;
 * - a group already judged but not fully resolved (rows left flagged — possible
 *   homonyms) is remembered with its composition: on the next launch, an
 *   unchanged composition is skipped without any network search, and a changed
 *   composition is re-searched. Fully resolved groups forget their entry. The
 *   composition carries the row ids and — for groups judged by the song
 *   fallback — also the selected songs (primary-judged groups remember rows
 *   only): a changed composition is a new row, or different selected songs for
 *   a fallback-judged group.
 * - an offline launch -> the group is skipped and retried on the next launch,
 *   never remembered. The primary artist search answers null both when it
 *   matches nothing AND when the search itself fails (its contract is a single
 *   null), so BOTH cases run the song-based fallback (step 4): only a fallback
 *   that ends WITHOUT unanimous candidate (disagreement between rows, or a row
 *   no song can prove) is skipped with its composition remembered like flagged
 *   rows. A failure of the fallback's OWN network calls (song search, artist
 *   page fetch) still PROPAGATES -> the group is skipped, not remembered, and
 *   retried on the next launch.
 *
 * The `modified:` name on the canonical row is what protects it downstream:
 * StreamResolver's background fetch keeps custom names (`retainIfModified`), and
 * DbCleanup never judges the links of a `modified:` artist — so the re-linked
 * songs survive both of those sweeps.
 *
 * Network searches are rate-limited with a random 1-5 s gap between two
 * searches (same pattern as HomeSyncService, no per-launch cap) so a polluted
 * database converges in a single launch; the runs are idempotent, so a launch
 * with nothing to do is a cheap no-op.
 *
 * Wired in [MainApplication] onCreate as the SECOND pass of the sequential
 * artist-data boot chain (`DbCleanup` -> same-name dedup -> name convergence).
 */
object SameNameArtistDedup {
    /** Shared log tag (the [InnertubeSongTruthResolver] uses it too). */
    internal const val TAG = "SameNameArtistDedup"

    /** Prefix of user-localized artist ids (no YTM browse id behind them). */
    private const val LOCAL_ARTIST_ID_PREFIX = "LOCAL_ARTIST_"

    /** Songs per row the song-based fallback may try (network budget). */
    private const val SONGS_PER_ROW = 3

    /**
     * Schedules the sweep on [NzikDispatchers.DATA] and logs failures.
     * Runs on every launch, so a failed attempt retries on the next one.
     */
    fun run(context: Context) {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runCatching { runPass(context) }
                .onFailure { e ->
                    Timber.tag(TAG).e(e, "Same-name artist dedup failed (will retry on next launch)")
                }
        }
    }

    /**
     * The dedup pass, called from the sequential boot chain in [MainApplication]
     * (`DbCleanup` -> same-name dedup -> name convergence) or from [run].
     *
     * @return the [DedupResult] of the run (counters + the per-group records) so
     * the boot chain can persist the last successful run
     */
    internal suspend fun runPass(context: Context): DedupResult {
        val result = runDedup(
            artistTable = Database.artistTable,
            mapTable = Database.songArtistMapTable,
            resolver = InnertubeArtistSearchResolver(),
            songTruthResolver = InnertubeSongTruthResolver(),
            skipStore = PrefsSkipStore(context),
            online = NetworkQualityHelper.isNetworkAvailable(context),
        )
        Timber.tag(TAG).i(
            "Same-name artist dedup: groups=%d resolved=%d songResolved=%d merged=%d flagged=%d skipped=%d deferred=%d localKept=%d",
            result.groups, result.resolved, result.songResolved, result.merged, result.flagged,
            result.skipped, result.deferred, result.localKept,
        )
        if (result.merged > 0) {
            withContext(NzikDispatchers.UI) {
                runCatching {
                    Toaster.s(context.getString(R.string.same_name_artists_dedup_toast, result.merged))
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "Dedup succeeded but toast failed")
                }
            }
        }
        return result
    }

    /**
     * The sweep core, parameterized by the DAOs and the resolver so it can be
     * exercised in unit tests against an in-memory database with a fake resolver.
     *
     * @param songTruthResolver song-based fallback, used only when the primary
     * artist search finds nothing (see the class KDoc)
     * @param skipStore memory of groups already judged but not fully resolved
     * (null disables the memory, e.g. in unit tests)
     * @param online whether a network is available (offline: no new resolution,
     * the groups are retried on the next launch)
     */
    internal suspend fun runDedup(
        artistTable: ArtistTable,
        mapTable: SongArtistMapTable,
        resolver: ArtistSearchResolver,
        songTruthResolver: SongTruthResolver,
        skipStore: SkipStore?,
        online: Boolean,
    ): DedupResult {
        val groupNames = artistTable.sameNameGroups()
        Timber.tag(TAG).i("Identified %d same-name group(s)", groupNames.size)

        var resolved = 0
        var songResolved = 0
        var merged = 0
        var flagged = 0
        var skipped = 0
        var deferred = 0
        var localKept = 0
        // Per-group outcome captured in every terminal branch (name, status, merge/flag
        // counters or skip reason) so the boot chain can persist it — the per-group
        // detail exists only in the Timber logs otherwise.
        val records = mutableListOf<DedupGroupRecord>()

        for (groupName in groupNames) {
            val rows = artistTable.allByNameIgnoreCase(groupName)
            val ytRows = rows.filter { !it.id.startsWith(LOCAL_ARTIST_ID_PREFIX) }
            val localCount = rows.size - ytRows.size
            localKept += localCount
            if (localCount > 0) {
                Timber.tag(TAG).d(
                    "group '%s': %d local row(s) kept (user-localized, excluded from the merge)",
                    groupName, localCount,
                )
            }
            // Fewer than two YouTube rows: nothing to dedup (local rows are
            // user-localized and never enter the merge).
            if (ytRows.size < 2) continue
            // A whole group renamed by the user is custom data: untouchable.
            if (groupName.startsWith(MODIFIED_PREFIX)) {
                skipped++
                records += DedupGroupRecord(groupName, DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.CUSTOM_NAME)
                Timber.tag(TAG).d("group '%s': skipped (custom name)", groupName)
                continue
            }
            if (!online) {
                skipped++
                records += DedupGroupRecord(groupName, DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.OFFLINE)
                Timber.tag(TAG).d("group '%s': skipped (offline)", groupName)
                continue
            }
            // A group already judged, whose composition did not change, keeps its
            // verdict: its remaining rows were flagged on purpose (possible
            // homonyms), re-searching them would only burn the network budget.
            // A fallback-judged composition also carries the selected songs:
            // a changed selection is new evidence and re-opens the group.
            val remembered = skipStore?.rememberedComposition(groupName)
            if (remembered != null) {
                val rowIds = rows.mapTo(mutableSetOf()) { it.id }
                val songIds = ytRows.flatMap { row -> rowFallbackSongs(row.id, mapTable) }
                    .mapTo(mutableSetOf()) { it.id }
                if (remembered.rows == rowIds && (remembered.songs == null || remembered.songs == songIds)) {
                    deferred++
                    records += DedupGroupRecord(groupName, DedupGroupStatus.DEFERRED)
                    Timber.tag(TAG).d(
                        "group '%s': deferred (already judged, composition unchanged, no search)",
                        groupName,
                    )
                    continue
                }
            }
            runCatching {
                val found = resolver.searchArtist(groupName)
                if (found != null) {
                    val outcome = mergeGroup(groupName, ytRows, found, artistTable, mapTable)
                    resolved++
                    merged += outcome.merged
                    flagged += outcome.flagged
                    records += DedupGroupRecord(
                        groupName, DedupGroupStatus.RESOLVED,
                        mergedRows = outcome.merged, flaggedRows = outcome.flagged,
                    )
                    rememberRemainingComposition(groupName, artistTable, mapTable, skipStore, fallbackJudged = false)
                    return@runCatching
                }
                // Song-based fallback: the primary artist search found nothing,
                // so each row is proven by its own songs. A merge needs the
                // UNANIMOUS agreement of every row on one single browse id.
                Timber.tag(TAG).d("group '%s': no artist match or failed artist search, proving each row by its songs", groupName)
                // Resolve the rows one by one and stop as soon as unanimity is
                // impossible (a row with no candidate, or a row whose browse id
                // differs from the first): the remaining rows' songs never
                // reach the network.
                var truth: ResolvedArtist? = null
                for (row in ytRows) {
                    val candidate = songTruthResolver.resolveSongs(groupName, row, rowFallbackSongs(row.id, mapTable))
                    if (truth == null) {
                        if (candidate == null) break
                        truth = candidate
                    } else if (candidate?.browseId != truth.browseId) {
                        truth = null
                        break
                    }
                }
                if (truth == null) {
                    // Disagreement between rows (homonyms), or a row no song can
                    // prove (unattributed UGC): nothing moves, and the
                    // composition is remembered like flagged rows — a pure
                    // network failure is NOT remembered (onFailure below).
                    skipped++
                    records += DedupGroupRecord(
                        groupName, DedupGroupStatus.SKIPPED,
                        skipReason = DedupSkipReason.NO_UNANIMOUS_CANDIDATE,
                    )
                    rememberRemainingComposition(groupName, artistTable, mapTable, skipStore, fallbackJudged = true)
                    Timber.tag(TAG).d(
                        "group '%s': song fallback without unanimous candidate, skipped",
                        groupName,
                    )
                    return@runCatching
                }
                val outcome = mergeGroup(groupName, ytRows, truth, artistTable, mapTable)
                songResolved++
                merged += outcome.merged
                flagged += outcome.flagged
                records += DedupGroupRecord(
                    groupName, DedupGroupStatus.SONG_RESOLVED,
                    mergedRows = outcome.merged, flaggedRows = outcome.flagged,
                )
                rememberRemainingComposition(groupName, artistTable, mapTable, skipStore, fallbackJudged = true)
                Timber.tag(TAG).d("group '%s': songResolved, unanimous browse id %s", groupName, truth.browseId)
            }.onFailure { e ->
                skipped++
                records += DedupGroupRecord(
                    groupName, DedupGroupStatus.SKIPPED,
                    skipReason = DedupSkipReason.NETWORK_FAILURE,
                )
                Timber.tag(TAG).w(e, "group '%s': resolution failed, skipped (retried on next launch)", groupName)
            }
            // Random gap between two searches (same pattern as HomeSyncService),
            // to stay gentle on the API and avoid a regular bot-like cadence.
            delay((1000L..5000L).random())
        }

        return DedupResult(
            groupNames.size, resolved, songResolved, merged, flagged,
            skipped, deferred, localKept,
            records,
        )
    }

    /**
     * The songs the song-based fallback may try for [artistId]: at most
     * [SONGS_PER_ROW], songs with a non-blank CLEANED title (prefixes can
     * make a raw title look non-blank while its query degenerates — "e:"
     * cleans to ""), in Song.ROWID order — the order of
     * [SongArtistMapTable.allSongsByDirect] (the rest of the row's songs
     * never reach the network).
     *
     * The [Song.isYoutubeSong] flag is deliberately NOT part of the filter:
     * it is never set to true anywhere in production (it defaults to false
     * and no setter exists — the song-update flow only carries the stored
     * value), so filtering on it would silently empty every list in the field. A non-YouTube-source song is still safe: candidate validity
     * comes from the same-song confirmation (perfect video id match, or exact
     * title + same image) and the artist page's name equality, never from the
     * source flag.
     *
     * Maintenance: this decision rests on the flag never being set in
     * production — if a production setter for [Song.isYoutubeSong] ever
     * appears, revisit this filter.
     */
    private fun rowFallbackSongs(artistId: String, mapTable: SongArtistMapTable): List<Song> =
        mapTable.allSongsByDirect(artistId)
            .filter { it.cleanTitle().isNotBlank() }
            .take(SONGS_PER_ROW)

    /**
     * Remembers the group's current composition so the next launch skips it
     * without network while nothing changes; a fully resolved group (fewer
     * than 2 rows left) forgets its entry.
     *
     * A [fallbackJudged] verdict carries the selected songs of the remaining
     * YouTube rows (same selection as the proof): the songs are part of that
     * verdict, so a changed selection re-opens the group. A primary-judged
     * verdict remembers rows only.
     */
    private suspend fun rememberRemainingComposition(
        groupName: String,
        artistTable: ArtistTable,
        mapTable: SongArtistMapTable,
        skipStore: SkipStore?,
        fallbackJudged: Boolean,
    ) {
        val remaining = artistTable.allByNameIgnoreCase(groupName)
        if (remaining.size < 2) {
            skipStore?.remember(groupName, null)
            return
        }
        val ytRemaining = remaining.filter { !it.id.startsWith(LOCAL_ARTIST_ID_PREFIX) }
        val songs = if (fallbackJudged)
            ytRemaining.flatMap { row -> rowFallbackSongs(row.id, mapTable) }
                .mapTo(mutableSetOf()) { it.id }
        else
            null
        skipStore?.remember(groupName, RememberedComposition(remaining.mapTo(mutableSetOf()) { it.id }, songs))
    }

    /**
     * Merges every mergeable YouTube row of the group into the truth row — the
     * row whose id is the search browse id, or a freshly created canonical row.
     */
    private suspend fun mergeGroup(
        groupName: String,
        rows: List<Artist>,
        found: ResolvedArtist,
        artistTable: ArtistTable,
        mapTable: SongArtistMapTable,
    ): GroupMerge {
        val truthId = found.browseId
        val canonicalName = "$MODIFIED_PREFIX$groupName"
        // The truth row can also already exist UNDER A DIFFERENT NAME (its real
        // channel name, outside the same-name group): it is still the truth by
        // id, and it must be found for the name pin below.
        val existingTruth = rows.firstOrNull { it.id == truthId }
            ?: artistTable.findByIdDirect(truthId)

        if (existingTruth == null) {
            // The canonical artist is missing from the DB: create it. No rich data
            // carry-over — background fetches fill it in afterwards.
            Database.transaction {
                artistTable.insertIgnore(
                    Artist(
                        id = truthId,
                        name = canonicalName,
                        thumbnailUrl = found.thumbnailUrl,
                        isYoutubeArtist = true,
                    )
                )
            }
        } else if (existingTruth.name?.startsWith(MODIFIED_PREFIX) != true && existingTruth.name != canonicalName) {
            // Pin the truth row's name to the group name, marked custom:
            // StreamResolver then keeps it (retainIfModified) and DbCleanup never
            // judges its links. A name the user already customized is left alone.
            Database.transaction {
                artistTable.upsert(existingTruth.copy(name = canonicalName))
            }
        }

        // Image safety on the stored images (see the class KDoc for the rule):
        // the truth row's picture is the reference when it has one, otherwise the
        // group's strict-majority picture; a tie or a lone row without picture in
        // a conflicting group is doubt -> flagged, never merged.
        val truthImage = existingTruth?.thumbnailUrl
        val storedImages = rows.mapNotNull { it.thumbnailUrl }
        val imagesConflict = storedImages.distinct().size > 1
        val majorityImage = if (imagesConflict)
            storedImages.groupBy { it }
                .maxByOrNull { entry -> entry.value.size }
                ?.takeIf { it.value.size * 2 > storedImages.size }
                ?.key
        else
            null

        var merged = 0
        var flagged = 0
        for (row in rows) {
            if (row.id == truthId) continue
            val rowImage = row.thumbnailUrl
            val isFlagged = when {
                // D4: a row without a picture is neutral only in a group with no
                // image conflict; in a conflicting group it is doubt -> flagged,
                // whatever the truth row carries.
                rowImage == null -> imagesConflict
                truthImage != null -> rowImage != truthImage
                !imagesConflict -> false
                else -> rowImage != majorityImage
            }
            if (isFlagged) {
                flagged++
                Timber.tag(TAG).d(
                    "group '%s': row %s image conflicts with the group, flagged (not merged)",
                    groupName, row.id,
                )
                continue
            }

            Database.transaction {
                // A song already linked to the truth row keeps that link: drop the
                // duplicate link first, otherwise the re-link UPDATE below would
                // violate the (songId, artistId) primary key.
                mapTable.dropLinksAlreadyOn(row.id, truthId)
                // Re-link every remaining song of the duplicate row onto the truth
                // row BEFORE the row disappears: no song can be left orphaned.
                mapTable.updateArtistId(row.id, truthId)
                // The user's heart/dissent moves with the songs: carry the follow
                // and the dislike over onto the truth row when it does not carry them.
                val truth = artistTable.findByIdDirect(truthId)
                if (truth != null) {
                    val carryFollow = truth.bookmarkedAt == null && row.bookmarkedAt != null
                    val carryDislike = truth.dislikedAt == null && row.dislikedAt != null
                    if (carryFollow || carryDislike) {
                        artistTable.upsert(
                            truth.copy(
                                bookmarkedAt = truth.bookmarkedAt ?: row.bookmarkedAt,
                                dislikedAt = truth.dislikedAt ?: row.dislikedAt,
                            )
                        )
                    }
                }
                artistTable.deleteById(row.id)
                merged++
            }
            Timber.tag(TAG).d("group '%s': merged row %s into %s", groupName, row.id, truthId)
        }

        return GroupMerge(merged, flagged)
    }
}

/** Outcome of one resolved YTM artist search. */
internal data class ResolvedArtist(
    val browseId: String,
    val name: String?,
    val thumbnailUrl: String?,
)

/**
 * YTM artist search by name, injectable so the sweep core can be tested
 * offline with a fake.
 */
internal interface ArtistSearchResolver {

    /**
     * @param name artist name to search for (the group name)
     * @return the search's artist (browse id + thumbnail), null when the search
     * failed or returned nothing
     */
    suspend fun searchArtist(name: String): ResolvedArtist?
}

/**
 * Picks the searched artist out of a YTM artist search page.
 *
 * STRICT exact match (case-insensitive) only: the top result of an artist
 * search without an exact name match can be a DIFFERENT artist (homonym), and
 * merging the group onto it would violate "in case of doubt, never merge" —
 * the search thumbnail is not a reliable reference (rule D4), so no image
 * check can catch a foreign artist either. Null here means the group is
 * skipped and retried on the next launch.
 *
 * @param items the parsed items of the search page (may be null)
 * @param name the artist name searched (the group name)
 * @return the item whose name matches exactly, null when nothing does
 */
internal fun pickExactArtistMatch(
    items: List<Innertube.ArtistItem>?,
    name: String,
): Innertube.ArtistItem? =
    items?.firstOrNull { it.info?.name?.equals(name, ignoreCase = true) == true }

/** The [ArtistSearchResolver] backed by the YTM search API. */
internal class InnertubeArtistSearchResolver : ArtistSearchResolver {
    override suspend fun searchArtist(name: String): ResolvedArtist? {
        val page = Innertube.searchPage<Innertube.ArtistItem>(
            query = name,
            params = Innertube.SearchFilter.Artist.value,
        ) { content -> Innertube.ArtistItem.from(content) }?.getOrNull() ?: return null
        // Strict exact match only (see [pickExactArtistMatch]): the top result
        // without an exact name match may be another artist.
        val item = pickExactArtistMatch(page.items, name) ?: return null
        val browseId = item.info?.endpoint?.browseId ?: return null
        return ResolvedArtist(
            browseId = browseId,
            name = item.info?.name,
            thumbnailUrl = item.thumbnail?.url,
        )
    }
}

/**
 * Song-based truth resolution, used ONLY when the primary artist search finds
 * nothing (some artists are indexed by YTM only through their songs — topic
 * pages, channels absent from the artist search). Each row proves itself with
 * its own songs; a candidate is valid only when the artist page reached from
 * the confirmed song's byline entry exists and its name equals the group name
 * — the byline alone can be a wrong topic/channel attribution, and the page
 * (name + browse id + image) is the truth, never the byline.
 */
internal interface SongTruthResolver {

    /**
     * @param groupName the group name (the rows share it, case-insensitive)
     * @param row one row of the group, proven by its own songs
     * @param songs the row's songs to try — a bounded per-row budget (see
     * [SameNameArtistDedup]'s `SONGS_PER_ROW`), each with a non-blank cleaned
     * title
     * @return a valid candidate (the artist page reached from a confirmed
     * song's byline entry, whose page name equals the group name) or null when
     * no song yields one; a network failure PROPAGATES instead, so the group
     * is skipped without being remembered and retried on the next launch
     */
    suspend fun resolveSongs(groupName: String, row: Artist, songs: List<Song>): ResolvedArtist?
}

/**
 * The [SongTruthResolver] backed by the YTM song search + artist page APIs.
 * For each song (in order, bounded per-row budget — see [SameNameArtistDedup]'s
 * `SONGS_PER_ROW`):
 * 1. precise YTM song search (cleaned local title, + the group name when the
 *    title does not carry it);
 * 2. same-song confirmation: the result's video id equals the local song's id
 *    (perfect match), or the same-song triple holds — exact title
 *    (case-insensitive) + same image + (byline checked below, even for a
 *    perfect match);
 * 3. navigation to the artist page from the byline entry named after the
 *    group; the page name must equal the group name ("X - Topic" is rejected).
 *
 * Network calls are rate-limited with the same random 1-5 s gap as the artist
 * search. A failed call propagates (pure network failure -> retried on the
 * next launch, never remembered); an empty page or a missing byline entry is
 * a legitimate "no candidate" answer for the song.
 */
internal class InnertubeSongTruthResolver : SongTruthResolver {

    override suspend fun resolveSongs(groupName: String, row: Artist, songs: List<Song>): ResolvedArtist? {
        Timber.tag(SameNameArtistDedup.TAG).d(
            "group '%s' row %s: proving with %d song(s) %s",
            groupName, row.id, songs.size,
            if (songs.isEmpty()) "(none)" else songs.joinToString { it.cleanTitle() },
        )
        for (song in songs) {
            val candidate = candidateFromSong(groupName, song)
            if (candidate != null) {
                Timber.tag(SameNameArtistDedup.TAG).d(
                    "row %s: candidate %s from song %s",
                    row.id, candidate.browseId, song.id,
                )
                return candidate
            }
        }
        return null
    }

    private suspend fun candidateFromSong(groupName: String, song: Song): ResolvedArtist? {
        val title = song.cleanTitle()
        val query = if (title.contains(groupName, ignoreCase = true)) title else "$title $groupName"
        delay((1000L..5000L).random())
        Timber.tag(SameNameArtistDedup.TAG).d(
            "group '%s' song %s ('%s'): searching '%s'",
            groupName, song.id, title, query,
        )
        val page = Innertube.searchPage<Innertube.SongItem>(
            query = query,
            params = Innertube.SearchFilter.Song.value,
        ) { Innertube.SongItem.from(it) } ?: return null
        // Only the FIRST page of results is consulted (the continuation is
        // never followed): a song not on the first page yields no candidate
        // for this song.
        if (!page.isSuccess) {
            // A failed page (network error) propagates: the group is then
            // skipped WITHOUT being remembered and retried on the next launch.
            Timber.tag(SameNameArtistDedup.TAG).d(
                "group '%s' song %s: search failed (network) — the group is aborted (retried next launch, not remembered)",
                groupName, song.id,
            )
            page.getOrThrow() // throws the actual network exception
        }
        // A successful page with no items is a legitimate "no candidate"
        // answer for the song.
        val items = page.getOrThrow()?.items.orEmpty()
        Timber.tag(SameNameArtistDedup.TAG).d(
            "group '%s' song %s: %d search result(s)",
            groupName, song.id, items.size,
        )
        val match = pickConfirmedSong(song, items) ?: run {
            Timber.tag(SameNameArtistDedup.TAG).d(
                "group '%s' song %s ('%s'): no same-song match among the results (exact title + image, or video id), no candidate",
                groupName, song.id, title,
            )
            return null
        }
        val browseId = bylineBrowseId(match, groupName) ?: run {
            Timber.tag(SameNameArtistDedup.TAG).d(
                "group '%s' song %s: confirmed but the byline has no entry named '%s', no candidate",
                groupName, song.id, groupName,
            )
            return null
        }
        delay((1000L..5000L).random())
        val artistPageResult = Innertube.artistPage(browseId) ?: return null
        if (!artistPageResult.isSuccess) {
            Timber.tag(SameNameArtistDedup.TAG).d(
                "group '%s' song %s: artist page '%s' fetch failed (network) — the group is aborted (retried next launch, not remembered)",
                groupName, song.id, browseId,
            )
            artistPageResult.getOrThrow() // throws the actual network exception
        }
        val artistPage = artistPageResult.getOrThrow()
        if (!artistPageMatchesGroup(artistPage.name, groupName)) {
            Timber.tag(SameNameArtistDedup.TAG).d(
                "artist page '%s' for song %s is not the group '%s', no candidate",
                artistPage.name, song.id, groupName,
            )
            return null
        }
        return ResolvedArtist(
            browseId = browseId,
            name = artistPage.name,
            thumbnailUrl = artistPage.thumbnail?.url,
        )
    }
}

/**
 * Picks the search result that is the SAME song as the local [song].
 *
 * A result is confirmed when its video id equals the local song's id (perfect
 * match — the byline entry matching the group is still required by the
 * caller, it is the only navigation handle to the artist page), or when the
 * same-song triple holds: exact title (case-insensitive) + same image.
 * The triple is all or nothing, with one exception: when the local thumbnail
 * is null the image comparison is neutral (see [sameImage]) and an exact
 * title match alone confirms. Confirmed by similarity of titles only is
 * NEVER.
 *
 * YTM result titles are not normalized (suffixes like "(Official Audio)"):
 * a local title without the suffix does NOT confirm a suffixed result —
 * deliberate (strict agreement; in case of doubt, nothing moves).
 */
internal fun pickConfirmedSong(
    song: Song,
    items: List<Innertube.SongItem>,
): Innertube.SongItem? =
    items.firstOrNull { item ->
        val videoId = item.info?.endpoint?.videoId
        if (videoId != null && videoId == song.id) true
        else
            item.info?.name?.equals(song.cleanTitle(), ignoreCase = true) == true &&
                sameImage(song.thumbnailUrl, item.thumbnail?.url)
    }

/**
 * Same image = the same video id embedded in both thumbnail URLs (YTM
 * thumbnails are deterministic per video, `i.ytimg.com/vi/<videoId>/...`).
 * When at least one side embeds no video id, the full URLs are compared
 * instead: a plain URL equality when neither does, and effectively always
 * false when exactly one side does (the pictures are then different videos).
 * A null local thumbnail is neutral (title + byline suffice); a null result
 * thumbnail is not.
 */
internal fun sameImage(localThumbnail: String?, ytmThumbnail: String?): Boolean {
    if (localThumbnail == null) return true
    if (ytmThumbnail == null) return false
    val localVideoId = videoIdOf(localThumbnail)
    val ytmVideoId = videoIdOf(ytmThumbnail)
    return when {
        localVideoId != null && ytmVideoId != null -> localVideoId == ytmVideoId
        else -> localThumbnail == ytmThumbnail
    }
}

internal fun videoIdOf(url: String): String? =
    VIDEO_ID_IN_THUMB.find(url)?.groupValues?.get(1)

/**
 * The browse id to navigate to from a confirmed song: its byline must carry
 * an artist entry whose name equals the group name (case-insensitive). Null
 * means the song cannot be used as a candidate — an unattributed UGC upload,
 * or a byline that only names other artists.
 */
internal fun bylineBrowseId(item: Innertube.SongItem, groupName: String): String? =
    item.authors?.firstOrNull { it.name?.equals(groupName, ignoreCase = true) == true }
        ?.endpoint
        ?.browseId

/**
 * The artist page reached from a byline entry is the truth only when its name
 * equals the group name: "X - Topic" pages and homonym channels are rejected
 * (the byline does not always point at the right artist).
 */
internal fun artistPageMatchesGroup(pageName: String?, groupName: String): Boolean =
    pageName?.equals(groupName, ignoreCase = true) == true

private val VIDEO_ID_IN_THUMB = Regex("/vi/([A-Za-z0-9_-]{11})/")

/**
 * The remembered composition of a judged group (see [SkipStore]).
 *
 * @param rows the row ids of the group at verdict time
 * @param songs the selected fallback songs of the YouTube rows — null when the
 * group was judged by the PRIMARY artist search (the songs are not part of
 * that verdict); non-null when it was judged by the song fallback, where the
 * selected songs are part of the composition
 */
internal data class RememberedComposition(
    val rows: Set<String>,
    val songs: Set<String>? = null,
)

/**
 * Memory of groups that were judged but could not be fully resolved (rows left
 * flagged, possible homonyms), so they are not re-searched on every launch.
 *
 * The composition is song-aware (see [RememberedComposition]): primary-judged
 * groups remember rows only; fallback-judged groups remember rows + their
 * selected songs, so a changed selection is new evidence and re-opens the group.
 */
internal interface SkipStore {

    /**
     * @param groupName the group name
     * @return the remembered composition of the group, null when it is not
     * remembered
     */
    fun rememberedComposition(groupName: String): RememberedComposition?

    /**
     * Remember [composition] as the current composition of [groupName].
     * @param composition null forgets the entry (the group is fully resolved)
     */
    fun remember(groupName: String, composition: RememberedComposition?)
}

/**
 * [SkipStore] backed by SharedPreferences: a single small JSON blob
 * (group name -> composition), no schema involved.
 *
 * Current format: one JSON object per group — `{"rows": [...], "songs":
 * [...]}`, the `songs` key omitted when it is null (primary-judged group).
 * A present-but-malformed `songs` key (not a JSON array) is read as UNKNOWN
 * song state (`songs = emptySet()`); only an ABSENT key means `songs = null`.
 * Legacy format (rows-only key): a plain JSON array of row ids per group.
 * Such an entry was judged by a build that did not track songs — the song
 * state is unknown, so it is read as rows + `songs = emptySet()` (NOT null,
 * whose primary semantics would keep a group judged by an old fallback run
 * deferred forever). The group is then re-judged on the next launch and
 * rewritten in the object format — except when its rows currently select no
 * songs at all: the empty selection matches the remembered empty set and the
 * group stays deferred (nothing to re-prove; it re-opens as soon as a
 * qualifying song is linked).
 */
internal class PrefsSkipStore(context: Context) : SkipStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun rememberedComposition(groupName: String): RememberedComposition? = load()[groupName]

    override fun remember(groupName: String, composition: RememberedComposition?) {
        val map = load().toMutableMap()
        if (composition == null) map.remove(groupName) else map[groupName] = composition
        prefs.edit().putString(KEY_REMEMBERED, toJson(map)).apply()
    }

    private fun load(): Map<String, RememberedComposition> = runCatching {
        val json = JSONObject(prefs.getString(KEY_REMEMBERED, "{}") ?: "{}")
        buildMap {
            json.keys().forEach { name ->
                when (val value = json.get(name)) {
                    is JSONObject -> put(
                        name,
                        RememberedComposition(
                            rows = stringSetOf(value.optJSONArray("rows")),
                            // Absent key = songs null (primary-judged); present
                            // but malformed = UNKNOWN -> emptySet() (the group
                            // is re-judged once, then rewritten)
                            songs = if (value.has("songs")) value.optJSONArray("songs")?.let(::stringSetOf) ?: emptySet() else null,
                        )
                    )
                    // Legacy entry: a plain array of row ids, judged by a
                    // build that did not track songs -> songs unknown ->
                    // emptySet() (one re-judgment, then rewritten), never
                    // null (primary semantics would defer it forever)
                    is JSONArray -> put(name, RememberedComposition(stringSetOf(value), emptySet()))
                    else -> {} // Malformed entry: dropped, the group is re-judged
                }
            }
        }
    }.getOrDefault(emptyMap())

    private fun toJson(map: Map<String, RememberedComposition>): String {
        val json = JSONObject()
        map.forEach { (name, composition) ->
            val entry = JSONObject().put("rows", jsonArrayOf(composition.rows))
            composition.songs?.let { entry.put("songs", jsonArrayOf(it)) }
            json.put(name, entry)
        }
        return json.toString()
    }

    private fun stringSetOf(array: JSONArray?): Set<String> = buildSet(array?.length() ?: 0) {
        if (array != null) for (i in 0 until array.length()) add(array.getString(i))
    }

    private fun jsonArrayOf(ids: Set<String>): JSONArray = JSONArray().apply { ids.forEach(::put) }

    private companion object {
        const val PREFS_NAME = "same_name_artist_dedup"
        const val KEY_REMEMBERED = "remembered_groups"
    }
}

/**
 * Sweep summary.
 *
 * @param groups same-name groups identified (at least 2 rows per name)
 * @param resolved groups fully resolved by the primary artist search (merge
 * completed)
 * @param songResolved groups resolved by the song-based fallback (artist
 * search found nothing) — disjoint from [resolved]
 * @param merged duplicate rows re-linked and deleted (both resolution paths)
 * @param flagged rows left in place because their image conflicts with the group
 * @param skipped groups left untouched (offline, custom name, song fallback
 * without unanimous candidate, or a network failure during the fallback's OWN
 * calls — a failed song search or artist page fetch; a failed PRIMARY artist
 * search is not a direct skip, it enters the fallback)
 * @param deferred groups skipped because already judged with an unchanged composition
 * @param localKept `LOCAL_ARTIST_` rows excluded from the merge
 * @param records per-group outcome captured in every terminal branch (name,
 * status, merge/flag counters or skip reason) — persisted by the boot chain so
 * the Maintenance sheet can show the last run's detail
 */
internal data class DedupResult(
    val groups: Int,
    val resolved: Int,
    val songResolved: Int,
    val merged: Int,
    val flagged: Int,
    val skipped: Int,
    val deferred: Int,
    val localKept: Int,
    val records: List<DedupGroupRecord> = emptyList(),
)

/** Per-group merge outcome. */
private data class GroupMerge(
    val merged: Int,
    val flagged: Int,
)
