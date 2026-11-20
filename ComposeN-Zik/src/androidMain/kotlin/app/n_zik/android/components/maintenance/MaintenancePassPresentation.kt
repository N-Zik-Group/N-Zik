package app.n_zik.android.components.maintenance

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.cleanPrefix
import app.n_zik.android.core.maintenance.DedupGroupRecord
import app.n_zik.android.core.maintenance.DedupGroupStatus
import app.n_zik.android.core.maintenance.DedupSkipReason
import app.n_zik.android.core.maintenance.MaintenanceConvergenceState
import app.n_zik.android.core.maintenance.MaintenanceDbCleanupState
import app.n_zik.android.core.maintenance.MaintenanceDedupState
import app.n_zik.android.core.maintenance.MaintenanceDownloadRevalidationState
import app.n_zik.android.core.maintenance.MaintenanceDownloadSelfHealState
import app.n_zik.android.core.migration.ConvergenceSummary
import app.n_zik.android.download.utils.downloadDisplayName
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** One expanded detail row of a chevron line (name + status): shared by the dedup group list, the artist-link-cleanup removed-link list, the download re-validation removed-track list and the download self-heal healed-track line. */
internal data class GroupLine(
    val name: String,
    val status: String,
)

/** The expansion lines of the artist-link-cleanup row: one line per removed link (song title + removed artist name), mirroring the dedup group lines — so the five Database rows share the same "summary on top, detail list underneath" shape when opened or closed. */
internal fun dbCleanupLines(state: MaintenanceDbCleanupState?): List<GroupLine> =
    state?.removedLinks?.map { link -> GroupLine(cleanPrefix(link.songTitle), cleanPrefix(link.artistName.orEmpty())) }
        ?: emptyList()

/** The expanded artist-link-cleanup message when there are no per-link lines: "No data" without a state, the shared clean sentence on a clean run, the shared counter sentence on a state predating per-link records (one version cycle). */
internal fun dbCleanupEmptyRes(state: MaintenanceDbCleanupState?): Pair<Int, Int?> = when {
    state == null -> R.string.maintenance_no_data to null
    state.removed > 0 -> R.string.maintenance_pass_cleaned to state.removed
    else -> R.string.maintenance_pass_clean to null
}

/** One part of a pass status cell: the string resource plus its counter argument (null for the fixed "No data" / "Nothing to clean" sentences). */
internal data class PassStatusPart(
    val resId: Int,
    val count: Int? = null,
)

/**
 * The trailing status cell of a boot-pass row: one part per NON-ZERO STATE of the
 * last run ("N item(s) cleaned" / "N item(s) skipped" / "N item(s) deferred"), joined
 * by " · " by [passStatusText] — every state that happened is shown, none hidden behind
 * a single word; the per-reason / per-item breakdown lives in the expansion (user
 * decision 2026-10-23). Shared by the sheet's chevron row and the settings card's pass
 * table so the two can never drift.
 */
internal data class PassStatus(
    val parts: List<PassStatusPart>,
) {
    companion object {
        /** No readable state (never ran / the run failed this launch). */
        val NoData = PassStatus(listOf(PassStatusPart(R.string.maintenance_no_data)))

        /** A run that found nothing to do. */
        val NothingToClean = PassStatus(listOf(PassStatusPart(R.string.maintenance_pass_clean)))
    }
}

/** The trailing status of the "Same-name artist dedup" boot-pass row: one part per non-zero state — cleaned (the groups actually resolved, primary plus song-fallback) / skipped / deferred — the expansion lines name each group with its reason, the fixed "No data" / "Nothing to clean" parts otherwise. */
internal fun dedupStatusRes(state: MaintenanceDedupState?): PassStatus =
    when {
        state == null -> PassStatus.NoData
        else -> {
            val parts = ArrayList<PassStatusPart>(3)
            val cleaned = state.resolved + state.songResolved
            if (cleaned > 0) parts.add(PassStatusPart(R.string.maintenance_pass_cleaned, cleaned))
            if (state.skipped > 0) parts.add(PassStatusPart(R.string.maintenance_pass_skipped, state.skipped))
            if (state.deferred > 0) parts.add(PassStatusPart(R.string.maintenance_pass_deferred, state.deferred))
            if (parts.isEmpty()) PassStatus.NothingToClean else PassStatus(parts)
        }
    }

/** The trailing status of the "Name convergence" boot-pass row: one part per non-zero state — cleaned (the copies actually rewritten) / skipped (all per-reason skips combined — the expansion lines break them down by reason), the fixed "No data" / "Nothing to clean" parts otherwise. */
internal fun convergenceStatusRes(state: MaintenanceConvergenceState?): PassStatus =
    when {
        state == null -> PassStatus.NoData
        else -> {
            val parts = ArrayList<PassStatusPart>(2)
            if (state.summary.changed > 0) parts.add(PassStatusPart(R.string.maintenance_pass_cleaned, state.summary.changed))
            if (state.summary.totalSkipped > 0) parts.add(PassStatusPart(R.string.maintenance_pass_skipped, state.summary.totalSkipped))
            if (parts.isEmpty()) PassStatus.NothingToClean else PassStatus(parts)
        }
    }

/** The trailing status of the "Artist link cleanup" boot-pass row: the single "N item(s) cleaned" part on a removing run, the fixed "No data" / "Nothing to clean" parts otherwise (removal-only pass — no skip notion). */
internal fun dbCleanupStatusRes(state: MaintenanceDbCleanupState?): PassStatus =
    when {
        state == null -> PassStatus.NoData
        state.removed > 0 -> PassStatus(listOf(PassStatusPart(R.string.maintenance_pass_cleaned, state.removed)))
        else -> PassStatus.NothingToClean
    }

/** The expansion lines of the "Download re-validation" row: one line per stale track removed by the last startup (title + mediaId), mirroring the dedup group lines and the cleanup link lines. */
internal fun downloadRevalidationLines(state: MaintenanceDownloadRevalidationState?): List<GroupLine> =
    state?.removedTracks?.map { track -> GroupLine(downloadDisplayName(track.title), track.mediaId) }
        ?: emptyList()

/** The expanded download re-validation message when there are no per-track lines: "No data" without a state, the shared clean sentence on a clean run, the shared counter sentence on a state predating per-track records (one version cycle). */
internal fun downloadRevalidationEmptyRes(state: MaintenanceDownloadRevalidationState?): Pair<Int, Int?> = when {
    state == null -> R.string.maintenance_no_data to null
    state.removed > 0 -> R.string.maintenance_pass_cleaned to state.removed
    else -> R.string.maintenance_pass_clean to null
}

/** The trailing status of the "Download re-validation" boot-pass row: the single "N item(s) cleaned" part on a removing run, the fixed "No data" / "Nothing to clean" parts otherwise (removal-only pass — no skip notion). */
internal fun downloadRevalidationStatusRes(state: MaintenanceDownloadRevalidationState?): PassStatus =
    when {
        state == null -> PassStatus.NoData
        state.removed > 0 -> PassStatus(listOf(PassStatusPart(R.string.maintenance_pass_cleaned, state.removed)))
        else -> PassStatus.NothingToClean
    }

/** The expansion lines of the "Download self-heal" row: the single healed track (title + mediaId); the startup clean run (empty mediaId) has no lines — the expanded row shows the clean sentence instead (see [downloadSelfHealStatusRes]). */
internal fun downloadSelfHealLines(state: MaintenanceDownloadSelfHealState?): List<GroupLine> =
    state?.takeIf { it.mediaId.isNotEmpty() }?.let { listOf(GroupLine(cleanPrefix(it.title), it.mediaId)) }
        ?: emptyList()

/** The trailing status of the "Download self-heal" row: "No data" before the first startup run, the clean part on a startup run with nothing to heal, the single "1 item(s) cleaned" part once a real self-heal overwrote the startup state. */
internal fun downloadSelfHealStatusRes(state: MaintenanceDownloadSelfHealState?): PassStatus =
    when {
        state == null -> PassStatus.NoData
        state.mediaId.isEmpty() -> PassStatus.NothingToClean
        else -> PassStatus(listOf(PassStatusPart(R.string.maintenance_pass_cleaned, 1)))
    }

/** Resolves a pass status cell to its display text (the parts joined by " · ") — the single formatting point shared by the sheet's chevron rows, the card's table and the expansions' top summary line. */
@Composable
internal fun passStatusText(status: PassStatus): String {
    val texts = ArrayList<String>(status.parts.size)
    for (part in status.parts) {
        val count = part.count
        texts.add(if (count != null) maintenanceCount(part.resId, count) else stringResource(part.resId))
    }
    return texts.joinToString(" · ")
}

@Composable
internal fun maintenanceCount(resId: Int, count: Int): String = when (resId) {
    R.string.maintenance_pass_cleaned -> pluralStringResource(R.plurals.maintenance_pass_cleaned_count, count, count)
    R.string.maintenance_pass_deferred -> pluralStringResource(R.plurals.maintenance_pass_deferred_count, count, count)
    R.string.maintenance_pass_skipped -> pluralStringResource(R.plurals.maintenance_pass_skipped_count, count, count)
    R.string.maintenance_dedup_flagged -> pluralStringResource(R.plurals.maintenance_dedup_flagged_count, count, count)
    R.string.maintenance_dedup_merged -> pluralStringResource(R.plurals.maintenance_dedup_merged_count, count, count)
    else -> stringResource(resId, count)
}

/** Whether a pass status carries at least one state part (a non-null counter) — the fixed "No data" / "Nothing to clean" sentences carry none. The expanded rows use it to decide whether to repeat the status as their top summary line: a fixed sentence is already what the expansion shows, so it is never repeated on top of it. */
internal fun passHasState(status: PassStatus): Boolean =
    status.parts.any { it.count != null }

/** One convergence detail line of the expanded convergence row: the label string resource plus its count argument (null for the "No data" / clean fallbacks). */
internal data class ConvergenceLine(
    val resId: Int,
    val count: Int? = null,
)

/**
 * The expanded convergence detail lines, one per NON-ZERO sweep counter, in the
 * [ConvergenceSummary] field order (copies rewritten first, then the per-reason
 * skips) — the per-reason breakdown of the status, which the sheet's expansion repeats
 * on one line on top (the state totals' only home). A null summary (no readable state)
 * answers the shared "No data" line; an all-zero summary the shared clean line.
 */
internal fun convergenceDetailLines(summary: ConvergenceSummary?): List<ConvergenceLine> {
    if (summary == null) return listOf(ConvergenceLine(R.string.maintenance_no_data))
    val lines = ArrayList<ConvergenceLine>(8)
    if (summary.changed > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_copies_rewritten, summary.changed))
    if (summary.unbackedSongs > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_unbacked_songs, summary.unbackedSongs))
    if (summary.unlinkedSongs > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_unlinked_songs, summary.unlinkedSongs))
    if (summary.customSongs > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_custom_songs, summary.customSongs))
    if (summary.unlinkedAlbums > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_unlinked_albums, summary.unlinkedAlbums))
    if (summary.inconsistentAlbums > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_inconsistent_albums, summary.inconsistentAlbums))
    if (summary.countMismatchAlbums > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_count_mismatch_albums, summary.countMismatchAlbums))
    if (summary.customAlbums > 0) lines.add(ConvergenceLine(R.string.maintenance_convergence_custom_albums, summary.customAlbums))
    if (lines.isEmpty()) lines.add(ConvergenceLine(R.string.maintenance_pass_clean))
    return lines
}

/**
 * The display of one dedup group record: its status plus a short detail (when relevant).
 * When a group both merged and flagged rows, BOTH counters are shown
 * ([detailResId] with [detailArgs] = merged count and [detailArgs2] = flagged count).
 */
internal data class DedupGroupDisplay(
    val statusResId: Int,
    val detailResId: Int? = null,
    val detailArgs: Int? = null,
    val detailArgs2: Int? = null,
)

/** Maps a persisted [DedupGroupRecord] to its display (status + short reason), so the sheet stays a dumb view and the mapping is unit-tested. */
internal fun dedupGroupDisplay(record: DedupGroupRecord): DedupGroupDisplay {
    val statusResId = when (record.status) {
        DedupGroupStatus.RESOLVED -> R.string.maintenance_status_resolved
        DedupGroupStatus.SONG_RESOLVED -> R.string.maintenance_status_song_resolved
        DedupGroupStatus.SKIPPED -> R.string.maintenance_status_skipped
        DedupGroupStatus.DEFERRED -> R.string.maintenance_status_deferred
    }
    val detailResId: Int?
    val detailArgs: Int?
    val detailArgs2: Int?
    when {
        record.status == DedupGroupStatus.SKIPPED -> {
            detailResId = when (record.skipReason) {
                DedupSkipReason.CUSTOM_NAME -> R.string.maintenance_skip_custom_name
                DedupSkipReason.OFFLINE -> R.string.maintenance_skip_offline
                DedupSkipReason.NO_UNANIMOUS_CANDIDATE -> R.string.maintenance_skip_no_candidate
                DedupSkipReason.NETWORK_FAILURE -> R.string.maintenance_skip_network_failure
                null -> null
            }
            detailArgs = null
            detailArgs2 = null
        }
        record.mergedRows > 0 && record.flaggedRows > 0 -> {
            detailResId = R.string.maintenance_dedup_merged_flagged
            detailArgs = record.mergedRows
            detailArgs2 = record.flaggedRows
        }
        record.mergedRows > 0 -> {
            detailResId = R.string.maintenance_dedup_merged
            detailArgs = record.mergedRows
            detailArgs2 = null
        }
        record.flaggedRows > 0 -> {
            detailResId = R.string.maintenance_dedup_flagged
            detailArgs = record.flaggedRows
            detailArgs2 = null
        }
        else -> {
            detailResId = null
            detailArgs = null
            detailArgs2 = null
        }
    }
    return DedupGroupDisplay(statusResId, detailResId, detailArgs, detailArgs2)
}

/** Formats an epoch-millis timestamp in the display locale and device zone. */
internal fun formatTimestamp(
    millis: Long,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale)
    .format(Instant.ofEpochMilli(millis).atZone(zone))

/**
 * The chevron rows of the boot passes share one trailing format: the last run's timestamp
 * in front, its status after ("27/09/2026 14:30 · 3 copies rewritten"). A missing timestamp
 * (the pass never ran) answers the plain [noDataText] status.
 */
internal fun lastRunLine(
    timestamp: Long?,
    statusText: String,
    noDataText: String,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
): String =
    if (timestamp == null) noDataText else "${formatTimestamp(timestamp, locale, zone)} · $statusText"
