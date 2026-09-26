package app.n_zik.android.core.migration

import android.content.Context
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.R
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongArtistMapTable
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.kreate.android.me.knighthat.utils.Toaster
import it.fast4x.innertube.Innertube
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
 *   homonyms) is remembered with its row composition: on the next launch, an
 *   unchanged composition is skipped without any network search, and a changed
 *   composition (a new row appeared) is re-searched. Fully resolved groups forget
 *   their entry.
 * - no search result, a failed search, or an offline launch -> the group is
 *   skipped and retried on the next launch.
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
 * Wired in [MainApplication] onCreate, right after [DbCleanup.run].
 */
object SameNameArtistDedup {
    private const val TAG = "SameNameArtistDedup"

    /** Prefix of user-localized artist ids (no YTM browse id behind them). */
    private const val LOCAL_ARTIST_ID_PREFIX = "LOCAL_ARTIST_"

    /**
     * Schedules the sweep on [NzikDispatchers.DATA] and logs failures.
     * Runs on every launch, so a failed attempt retries on the next one.
     */
    fun run(context: Context) {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runCatching {
                val result = runDedup(
                    artistTable = Database.artistTable,
                    mapTable = Database.songArtistMapTable,
                    resolver = InnertubeArtistSearchResolver(),
                    skipStore = PrefsSkipStore(context),
                    online = NetworkQualityHelper.isNetworkAvailable(context),
                )
                Timber.tag(TAG).i(
                    "Same-name artist dedup: groups=%d resolved=%d merged=%d flagged=%d skipped=%d deferred=%d localKept=%d",
                    result.groups, result.resolved, result.merged, result.flagged,
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
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Same-name artist dedup failed (will retry on next launch)")
            }
        }
    }

    /**
     * The sweep core, parameterized by the DAOs and the resolver so it can be
     * exercised in unit tests against an in-memory database with a fake resolver.
     *
     * @param skipStore memory of groups already judged but not fully resolved
     * (null disables the memory, e.g. in unit tests)
     * @param online whether a network is available (offline: no new resolution,
     * the groups are retried on the next launch)
     */
    internal suspend fun runDedup(
        artistTable: ArtistTable,
        mapTable: SongArtistMapTable,
        resolver: ArtistSearchResolver,
        skipStore: SkipStore?,
        online: Boolean,
    ): DedupResult {
        val groupNames = artistTable.sameNameGroups()
        Timber.tag(TAG).i("Identified %d same-name group(s)", groupNames.size)

        var resolved = 0
        var merged = 0
        var flagged = 0
        var skipped = 0
        var deferred = 0
        var localKept = 0

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
                Timber.tag(TAG).d("group '%s': skipped (custom name)", groupName)
                continue
            }
            if (!online) {
                skipped++
                Timber.tag(TAG).d("group '%s': skipped (offline)", groupName)
                continue
            }
            // A group already judged, whose composition did not change, keeps its
            // verdict: its remaining rows were flagged on purpose (possible
            // homonyms), re-searching them would only burn the network budget.
            val remembered = skipStore?.rememberedRows(groupName)
            if (remembered != null && remembered == rows.mapTo(mutableSetOf()) { it.id }) {
                deferred++
                Timber.tag(TAG).d(
                    "group '%s': deferred (already judged, composition unchanged, no search)",
                    groupName,
                )
                continue
            }
            runCatching {
                val found = resolver.searchArtist(groupName)
                if (found == null) {
                    skipped++
                    Timber.tag(TAG).d("group '%s': no YTM match, skipped", groupName)
                    return@runCatching
                }
                val outcome = mergeGroup(groupName, ytRows, found, artistTable, mapTable)
                resolved++
                merged += outcome.merged
                flagged += outcome.flagged
                // Remember the remaining composition so the next launch skips the
                // group while nothing changes; a fully resolved group forgets it.
                val remaining = artistTable.allByNameIgnoreCase(groupName)
                skipStore?.remember(
                    groupName,
                    if (remaining.size >= 2) remaining.mapTo(mutableSetOf()) { it.id } else null,
                )
            }.onFailure { e ->
                skipped++
                Timber.tag(TAG).w(e, "group '%s': resolution failed, skipped (retried on next launch)", groupName)
            }
            // Random gap between two searches (same pattern as HomeSyncService),
            // to stay gentle on the API and avoid a regular bot-like cadence.
            delay((1000L..5000L).random())
        }

        return DedupResult(groupNames.size, resolved, merged, flagged, skipped, deferred, localKept)
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
 * Memory of groups that were judged but could not be fully resolved (rows left
 * flagged, possible homonyms), so they are not re-searched on every launch.
 */
internal interface SkipStore {

    /**
     * @param groupName the group name
     * @return the remembered row ids of the group, null when it is not remembered
     */
    fun rememberedRows(groupName: String): Set<String>?

    /**
     * Remember [rowIds] as the current composition of [groupName].
     * @param rowIds null forgets the entry (the group is fully resolved)
     */
    fun remember(groupName: String, rowIds: Set<String>?)
}

/**
 * [SkipStore] backed by SharedPreferences: a single small JSON blob
 * (group name -> row ids), no schema involved.
 */
internal class PrefsSkipStore(context: Context) : SkipStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun rememberedRows(groupName: String): Set<String>? = load()[groupName]

    override fun remember(groupName: String, rowIds: Set<String>?) {
        val map = load().toMutableMap()
        if (rowIds == null) map.remove(groupName) else map[groupName] = rowIds
        prefs.edit().putString(KEY_REMEMBERED, toJson(map)).apply()
    }

    private fun load(): Map<String, Set<String>> = runCatching {
        val json = JSONObject(prefs.getString(KEY_REMEMBERED, "{}") ?: "{}")
        buildMap {
            json.keys().forEach { name ->
                val array = json.getJSONArray(name)
                put(name, buildSet(array.length()) {
                    for (i in 0 until array.length()) add(array.getString(i))
                })
            }
        }
    }.getOrDefault(emptyMap())

    private fun toJson(map: Map<String, Set<String>>): String {
        val json = JSONObject()
        map.forEach { (name, ids) ->
            val array = JSONArray()
            ids.forEach(array::put)
            json.put(name, array)
        }
        return json.toString()
    }

    private companion object {
        const val PREFS_NAME = "same_name_artist_dedup"
        const val KEY_REMEMBERED = "remembered_groups"
    }
}

/**
 * Sweep summary.
 *
 * @param groups same-name groups identified (at least 2 rows per name)
 * @param resolved groups fully resolved (search answered AND merge completed)
 * @param merged duplicate rows re-linked and deleted
 * @param flagged rows left in place because their image conflicts with the group
 * @param skipped groups left untouched (offline, no search match, search failure, custom name, ...)
 * @param deferred groups skipped because already judged with an unchanged composition
 * @param localKept `LOCAL_ARTIST_` rows excluded from the merge
 */
internal data class DedupResult(
    val groups: Int,
    val resolved: Int,
    val merged: Int,
    val flagged: Int,
    val skipped: Int,
    val deferred: Int,
    val localKept: Int,
)

/** Per-group merge outcome. */
private data class GroupMerge(
    val merged: Int,
    val flagged: Int,
)
