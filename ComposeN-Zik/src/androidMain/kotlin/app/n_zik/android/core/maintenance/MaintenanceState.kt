package app.n_zik.android.core.maintenance

import android.content.Context
import app.n_zik.android.core.migration.ConvergenceSummary
import app.n_zik.android.utils.DataStoreUtils
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Per-group outcome of one same-name artist dedup pass, captured in every terminal
 * branch of the sweep loop (resolved, song-resolved, skipped with its reason, or
 * deferred). Only the group name, the status, the merge/flag counters and the skip
 * reason are persisted — the display text is built from string resources at read
 * time, so no user-facing string ever reaches SharedPreferences.
 */
data class DedupGroupRecord(
    val name: String,
    val status: DedupGroupStatus,
    val mergedRows: Int = 0,
    val flaggedRows: Int = 0,
    val skipReason: DedupSkipReason? = null,
)

/** Terminal status of one dedup group (one per judged group per pass). */
enum class DedupGroupStatus {
    /** Resolved by the primary YTM artist search. */
    RESOLVED,

    /** Resolved by the song-based fallback (the artist search found nothing). */
    SONG_RESOLVED,

    /** Left untouched; see [DedupGroupRecord.skipReason]. */
    SKIPPED,

    /** Already judged with an unchanged composition: no network search. */
    DEFERRED,
}

/** Why a dedup group was skipped (the reason the sweep already logs). */
enum class DedupSkipReason {
    /** The whole group name carries the `modified:` custom prefix. */
    CUSTOM_NAME,

    /** The launch was offline: no new resolution, retried on the next launch. */
    OFFLINE,

    /** The song fallback ended without a unanimous candidate (homonyms or an
     * unprovable row); the composition is remembered. */
    NO_UNANIMOUS_CANDIDATE,

    /** A network failure during the resolution (or the fallback's own calls);
     * the group is retried on the next launch. */
    NETWORK_FAILURE,
}

/**
 * One stale link removed by a successful artist link cleanup pass, persisted so the
 * Maintenance sheet can list what the last run cleaned (the expanded row shows one
 * line per removed link).
 */
data class DbCleanupLinkRecord(
    val songTitle: String,
    val artistName: String?,
)

/**
 * Persisted state of the last SUCCESSFUL same-name dedup pass (one key in
 * `app_settings`): the timestamp plus the [app.n_zik.android.core.migration.DedupResult]
 * counters and its per-group [records].
 */
data class MaintenanceDedupState(
    val timestamp: Long,
    val groups: Int,
    val resolved: Int,
    val songResolved: Int,
    val merged: Int,
    val flagged: Int,
    val skipped: Int,
    val deferred: Int,
    val localKept: Int,
    val records: List<DedupGroupRecord>,
)

/**
 * Persisted state of the last SUCCESSFUL name convergence pass: the timestamp
 * plus the sweep's [summary] (the 8 counters).
 */
data class MaintenanceConvergenceState(
    val timestamp: Long,
    val summary: ConvergenceSummary,
)

/**
 * Persisted state of the last SUCCESSFUL artist link cleanup pass: the timestamp,
 * the removed counter and the per-link [removedLinks] records.
 */
data class MaintenanceDbCleanupState(
    val timestamp: Long,
    val removed: Int,
    val removedLinks: List<DbCleanupLinkRecord> = emptyList(),
)

/**
 * Single persistence base of the three boot passes (spec "Maintenance — état de
 * l'app en un regard"): one SharedPreferences key per pass in the `app_settings`
 * file (via [DataStoreUtils]), written ONLY by the sequential boot chain in
 * [app.n_zik.android.MainApplication] after a successful pass — a failed pass
 * never writes, so the previously persisted state is kept and the pass retries
 * on the next launch.
 *
 * Serialization is `org.json` (already a dependency, no new one). Reads are
 * tolerant: an absent key or a corrupted JSON answers `null`, which the UI maps
 * to "no data" for the concerned row.
 */
object MaintenanceStateStore {
    private const val TAG = "MaintenanceState"

    /** SharedPreferences key of the dedup pass (one key per boot pass). */
    const val KEY_DEDUP = "maintenance_dedup"

    /** SharedPreferences key of the name convergence pass. */
    const val KEY_CONVERGENCE = "maintenance_convergence"

    /** SharedPreferences key of the artist link cleanup pass. */
    const val KEY_DB_CLEANUP = "maintenance_db_cleanup"

    // -- writes (last successful run only; the caller never writes after a failure) --

    fun saveDedup(context: Context, state: MaintenanceDedupState) {
        DataStoreUtils.saveString(context, KEY_DEDUP, dedupToJson(state))
    }

    fun saveConvergence(context: Context, state: MaintenanceConvergenceState) {
        DataStoreUtils.saveString(context, KEY_CONVERGENCE, convergenceToJson(state))
    }

    fun saveDbCleanup(context: Context, state: MaintenanceDbCleanupState) {
        DataStoreUtils.saveString(context, KEY_DB_CLEANUP, dbCleanupToJson(state))
    }

    // -- tolerant reads (absent or corrupted key -> null -> "no data") --

    /** @return the persisted dedup state, or null when absent or unreadable. */
    fun readDedup(context: Context): MaintenanceDedupState? =
        runCatching { dedupFromJson(DataStoreUtils.getString(context, KEY_DEDUP)) }
            .onFailure { Timber.tag(TAG).w(it, "Corrupted dedup state, reporting no data") }
            .getOrNull()

    /** @return the persisted convergence state, or null when absent or unreadable. */
    fun readConvergence(context: Context): MaintenanceConvergenceState? =
        runCatching { convergenceFromJson(DataStoreUtils.getString(context, KEY_CONVERGENCE)) }
            .onFailure { Timber.tag(TAG).w(it, "Corrupted convergence state, reporting no data") }
            .getOrNull()

    /** @return the persisted cleanup state, or null when absent or unreadable. */
    fun readDbCleanup(context: Context): MaintenanceDbCleanupState? =
        runCatching { dbCleanupFromJson(DataStoreUtils.getString(context, KEY_DB_CLEANUP)) }
            .onFailure { Timber.tag(TAG).w(it, "Corrupted cleanup state, reporting no data") }
            .getOrNull()

    // -- JSON (top-level required fields read strictly: any missing/corrupted
    // field throws, the caller maps it to null) --

    internal fun dedupToJson(state: MaintenanceDedupState): String {
        val records = JSONArray()
        state.records.forEach { record ->
            val entry = JSONObject()
                .put("n", record.name)
                .put("s", record.status.name)
                .put("m", record.mergedRows)
                .put("f", record.flaggedRows)
            record.skipReason?.let { entry.put("r", it.name) }
            records.put(entry)
        }
        return JSONObject()
            .put("ts", state.timestamp)
            .put("groups", state.groups)
            .put("resolved", state.resolved)
            .put("songResolved", state.songResolved)
            .put("merged", state.merged)
            .put("flagged", state.flagged)
            .put("skipped", state.skipped)
            .put("deferred", state.deferred)
            .put("localKept", state.localKept)
            .put("records", records)
            .toString()
    }

    internal fun dedupFromJson(json: String): MaintenanceDedupState {
        val obj = JSONObject(json)
        val records = mutableListOf<DedupGroupRecord>()
        obj.optJSONArray("records")?.let { array ->
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                records += DedupGroupRecord(
                    name = entry.getString("n"),
                    status = DedupGroupStatus.valueOf(entry.getString("s")),
                    mergedRows = entry.optInt("m", 0),
                    flaggedRows = entry.optInt("f", 0),
                    skipReason = entry.optString("r", "")
                        .takeIf { it.isNotEmpty() }
                        ?.let { DedupSkipReason.valueOf(it) },
                )
            }
        }
        return MaintenanceDedupState(
            timestamp = obj.getLong("ts"),
            groups = obj.getInt("groups"),
            resolved = obj.getInt("resolved"),
            songResolved = obj.getInt("songResolved"),
            merged = obj.getInt("merged"),
            flagged = obj.getInt("flagged"),
            skipped = obj.getInt("skipped"),
            deferred = obj.getInt("deferred"),
            localKept = obj.getInt("localKept"),
            records = records,
        )
    }

    internal fun convergenceToJson(state: MaintenanceConvergenceState): String =
        JSONObject()
            .put("ts", state.timestamp)
            .put("summary", summaryToJson(state.summary))
            .toString()

    /** The summary as an embedded JSON OBJECT (not a stringified one — the read side
     * uses getJSONObject("summary")). */
    internal fun summaryToJson(summary: ConvergenceSummary): JSONObject =
        JSONObject()
            .put("changed", summary.changed)
            .put("unbackedSongs", summary.unbackedSongs)
            .put("unlinkedSongs", summary.unlinkedSongs)
            .put("customSongs", summary.customSongs)
            .put("unlinkedAlbums", summary.unlinkedAlbums)
            .put("inconsistentAlbums", summary.inconsistentAlbums)
            .put("countMismatchAlbums", summary.countMismatchAlbums)
            .put("customAlbums", summary.customAlbums)

    internal fun convergenceFromJson(json: String): MaintenanceConvergenceState {
        val obj = JSONObject(json)
        val summary = obj.getJSONObject("summary")
        return MaintenanceConvergenceState(
            timestamp = obj.getLong("ts"),
            summary = ConvergenceSummary(
                changed = summary.getInt("changed"),
                unbackedSongs = summary.getInt("unbackedSongs"),
                unlinkedSongs = summary.getInt("unlinkedSongs"),
                customSongs = summary.getInt("customSongs"),
                unlinkedAlbums = summary.getInt("unlinkedAlbums"),
                inconsistentAlbums = summary.getInt("inconsistentAlbums"),
                countMismatchAlbums = summary.getInt("countMismatchAlbums"),
                customAlbums = summary.getInt("customAlbums"),
            ),
        )
    }

    internal fun dbCleanupToJson(state: MaintenanceDbCleanupState): String {
        val links = JSONArray()
        for (record in state.removedLinks) {
            links.put(
                JSONObject()
                    .put("t", record.songTitle)
                    .put("a", record.artistName ?: "")
            )
        }
        return JSONObject()
            .put("ts", state.timestamp)
            .put("removed", state.removed)
            .put("links", links)
            .toString()
    }

    internal fun dbCleanupFromJson(json: String): MaintenanceDbCleanupState {
        val obj = JSONObject(json)
        // `links` is absent in states written before per-link records existed
        val links = mutableListOf<DbCleanupLinkRecord>()
        obj.optJSONArray("links")?.let { array ->
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                links += DbCleanupLinkRecord(
                    songTitle = entry.getString("t"),
                    artistName = entry.optString("a").takeIf { it.isNotEmpty() },
                )
            }
        }
        return MaintenanceDbCleanupState(
            timestamp = obj.getLong("ts"),
            removed = obj.getInt("removed"),
            removedLinks = links,
        )
    }
}
