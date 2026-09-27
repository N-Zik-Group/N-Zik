package app.n_zik.android.components.maintenance

import androidx.media3.exoplayer.offline.Download
import androidx.work.WorkInfo
import app.n_zik.android.R
import app.n_zik.android.core.maintenance.DedupGroupRecord
import app.n_zik.android.core.migration.ConvergenceSummary
import app.n_zik.android.core.maintenance.DedupGroupStatus
import app.n_zik.android.core.maintenance.DedupSkipReason
import app.n_zik.android.core.maintenance.DbCleanupLinkRecord
import app.n_zik.android.core.maintenance.MaintenanceConvergenceState
import app.n_zik.android.core.maintenance.MaintenanceDbCleanupState
import app.n_zik.android.core.maintenance.MaintenanceDedupState
import app.n_zik.android.updater.services.UpdateDownloadManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/**
 * Pure display helpers of the Maintenance sheet (spec "Maintenance — état de
 * l'app en un regard"): the sheet stays a dumb view, the mappings are tested
 * here. No Android context is needed (R is a constant class, WorkInfo is plain
 * Java), so this runs as a plain unit test.
 */
class MaintenanceSheetMappingTest {

    // ---- dedup group display ----

    @Test
    fun resolvedGroupShowsItsMergedCount() {
        val display = dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.RESOLVED, mergedRows = 2))

        assertEquals(R.string.maintenance_status_resolved, display.statusResId)
        assertEquals(R.string.maintenance_dedup_merged, display.detailResId)
        assertEquals(2, display.detailArgs)
        assertNull(display.detailArgs2)
    }

    @Test
    fun resolvedGroupShowingBothMergedAndFlaggedUsesTheCombinedString() {
        val display = dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.RESOLVED, mergedRows = 2, flaggedRows = 1))

        assertEquals(R.string.maintenance_status_resolved, display.statusResId)
        assertEquals(R.string.maintenance_dedup_merged_flagged, display.detailResId)
        assertEquals(2, display.detailArgs)
        assertEquals(1, display.detailArgs2)
    }

    @Test
    fun resolvedGroupWithoutMergesShowsItsFlaggedCount() {
        val display = dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.RESOLVED, flaggedRows = 1))

        assertEquals(R.string.maintenance_status_resolved, display.statusResId)
        assertEquals(R.string.maintenance_dedup_flagged, display.detailResId)
        assertEquals(1, display.detailArgs)
        assertNull(display.detailArgs2)
    }

    @Test
    fun resolvedGroupWithoutMergesNorFlagsHasNoDetail() {
        val display = dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.RESOLVED))

        assertEquals(R.string.maintenance_status_resolved, display.statusResId)
        assertNull(display.detailResId)
        assertNull(display.detailArgs)
        assertNull(display.detailArgs2)
    }

    @Test
    fun songResolvedGroupUsesTheSameDetailMapping() {
        val display = dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.SONG_RESOLVED, mergedRows = 3))

        assertEquals(R.string.maintenance_status_song_resolved, display.statusResId)
        assertEquals(R.string.maintenance_dedup_merged, display.detailResId)
        assertEquals(3, display.detailArgs)
        assertNull(display.detailArgs2)
    }

    @Test
    fun skippedGroupsShowTheirReason() {
        assertEquals(
            R.string.maintenance_skip_custom_name,
            dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.CUSTOM_NAME)).detailResId,
        )
        assertEquals(
            R.string.maintenance_skip_offline,
            dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.OFFLINE)).detailResId,
        )
        assertEquals(
            R.string.maintenance_skip_no_candidate,
            dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.NO_UNANIMOUS_CANDIDATE)).detailResId,
        )
        assertEquals(
            R.string.maintenance_skip_network_failure,
            dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.NETWORK_FAILURE)).detailResId,
        )
    }

    @Test
    fun skippedGroupWithoutReasonHasNoDetail() {
        val display = dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.SKIPPED))

        assertEquals(R.string.maintenance_status_skipped, display.statusResId)
        assertNull(display.detailResId)
        assertNull(display.detailArgs)
    }

    @Test
    fun deferredGroupHasNoDetail() {
        val display = dedupGroupDisplay(DedupGroupRecord("A", DedupGroupStatus.DEFERRED))

        assertEquals(R.string.maintenance_status_deferred, display.statusResId)
        assertNull(display.detailResId)
        assertNull(display.detailArgs)
    }

    // ---- scheduled jobs ----

    @Test
    fun absentJobsAreQuiet() {
        assertEquals(emptyList<JobSnapshot>(), abnormalJobs(emptyList()))
        assertEquals(
            emptyList<JobSnapshot>(),
            abnormalJobs(listOf(JobSnapshot("W", state = null, runAtStartTime = 0L))),
        )
    }

    @Test
    fun succeededJobsAreOk() {
        assertEquals(
            emptyList<JobSnapshot>(),
            abnormalJobs(listOf(JobSnapshot("W", state = WorkInfo.State.SUCCEEDED, runAtStartTime = 0L))),
        )
    }

    @Test
    fun failedAndCancelledJobsAreTheOnlyAbnormalOnes() {
        val failed = JobSnapshot("FAILED", state = WorkInfo.State.FAILED, runAtStartTime = 0L)
        val cancelled = JobSnapshot("CANCELLED", state = WorkInfo.State.CANCELLED, runAtStartTime = 0L)

        assertEquals(listOf(failed, cancelled), abnormalJobs(listOf(failed, cancelled)))
    }

    @Test
    fun inFlightJobsAreNotAbnormal() {
        // ENQUEUED / BLOCKED / RUNNING are in-flight states, not alarm states (review #3)
        val jobs = listOf(
            JobSnapshot("RUNNING", state = WorkInfo.State.RUNNING, runAtStartTime = 0L),
            JobSnapshot("ENQUEUED", state = WorkInfo.State.ENQUEUED, runAtStartTime = 1L),
            JobSnapshot("BLOCKED", state = WorkInfo.State.BLOCKED, runAtStartTime = 1L),
        )

        assertEquals(emptyList<JobSnapshot>(), abnormalJobs(jobs))
    }

    @Test
    fun workStateMapsToItsDisplayString() {
        assertEquals(R.string.maintenance_migration_done, workStateResId(WorkInfo.State.SUCCEEDED))
        assertEquals(R.string.maintenance_work_failed, workStateResId(WorkInfo.State.FAILED))
        assertEquals(R.string.maintenance_work_running, workStateResId(WorkInfo.State.RUNNING))
        assertEquals(R.string.maintenance_work_pending, workStateResId(WorkInfo.State.ENQUEUED))
        assertEquals(R.string.maintenance_work_cancelled, workStateResId(WorkInfo.State.CANCELLED))
    }

    @Test
    fun dbCleanupEmptyResMapsNoDataCleanAndCounterSentence() {
        assertEquals(R.string.maintenance_no_data to null, dbCleanupEmptyRes(null))
        assertEquals(
            R.string.maintenance_db_cleanup_detail_clean to null,
            dbCleanupEmptyRes(MaintenanceDbCleanupState(timestamp = 1L, removed = 0)),
        )
        // A state predating per-link records (no `links` array) still answers the
        // counter sentence — now with its argument (the literal `%d` bug)
        assertEquals(
            R.string.maintenance_db_cleanup_detail_removed to 3,
            dbCleanupEmptyRes(MaintenanceDbCleanupState(timestamp = 1L, removed = 3)),
        )
    }

    @Test
    fun dbCleanupLinesMapsOneLinePerRemovedLink() {
        val state = MaintenanceDbCleanupState(
            timestamp = 1L,
            removed = 2,
            removedLinks = listOf(
                DbCleanupLinkRecord("Song A", "Artist X"),
                DbCleanupLinkRecord("Song B", null),
            ),
        )

        val lines = dbCleanupLines(state)

        assertEquals(2, lines.size)
        assertEquals("Song A", lines[0].name)
        assertEquals("Artist X", lines[0].status)
        assertEquals("Song B", lines[1].name)
        assertEquals("", lines[1].status)
        assertTrue(dbCleanupLines(null).isEmpty())
    }

    // ---- shared pass-status cells (the settings card's table reuses them) ----

    @Test
    fun dedupStatusResMapsSummaryCountersAndNoData() {
        val noData = dedupStatusRes(null)
        assertEquals(R.string.maintenance_no_data, noData.first)
        assertNull(noData.second)

        val pair = dedupStatusRes(
            MaintenanceDedupState(
                timestamp = 1L,
                groups = 4,
                resolved = 2,
                songResolved = 1,
                merged = 3,
                flagged = 1,
                skipped = 1,
                deferred = 0,
                localKept = 0,
                records = emptyList(),
            ),
        )
        assertEquals(R.string.maintenance_dedup_summary, pair.first)
        assertArrayEquals(intArrayOf(4, 2, 1), pair.second)
    }

    @Test
    fun convergenceStatusResMapsChangedNoChangeAndNoData() {
        val noData = convergenceStatusRes(null)
        assertEquals(R.string.maintenance_no_data, noData.first)
        assertNull(noData.second)

        val changed = convergenceStatusRes(
            MaintenanceConvergenceState(1L, ConvergenceSummary(3, 0, 0, 0, 0, 0, 0, 0)),
        )
        assertEquals(R.string.maintenance_convergence_changed, changed.first)
        assertArrayEquals(intArrayOf(3), changed.second)

        val clean = convergenceStatusRes(
            MaintenanceConvergenceState(1L, ConvergenceSummary(0, 1, 0, 0, 0, 0, 0, 0)),
        )
        assertEquals(R.string.maintenance_convergence_no_change, clean.first)
        assertNull(clean.second)
    }

    @Test
    fun dbCleanupStatusResMapsRemovedCleanAndNoData() {
        val noData = dbCleanupStatusRes(null)
        assertEquals(R.string.maintenance_no_data, noData.first)
        assertNull(noData.second)

        val removed = dbCleanupStatusRes(MaintenanceDbCleanupState(timestamp = 1L, removed = 2))
        assertEquals(R.string.maintenance_db_cleanup_removed, removed.first)
        assertArrayEquals(intArrayOf(2), removed.second)

        val clean = dbCleanupStatusRes(MaintenanceDbCleanupState(timestamp = 1L, removed = 0))
        assertEquals(R.string.maintenance_db_cleanup_clean, clean.first)
        assertNull(clean.second)
    }

    // ---- convergence detail lines (the expanded convergence row) ----

    @Test
    fun allZeroConvergenceSummaryShowsTheNoChangeLine() {
        val lines = convergenceDetailLines(ConvergenceSummary(0, 0, 0, 0, 0, 0, 0, 0))

        assertEquals(1, lines.size)
        assertEquals(R.string.maintenance_convergence_no_change, lines[0].resId)
        assertNull(lines[0].count)
    }

    @Test
    fun nullConvergenceSummaryShowsTheNoDataLine() {
        val lines = convergenceDetailLines(null)

        assertEquals(1, lines.size)
        assertEquals(R.string.maintenance_no_data, lines[0].resId)
        assertNull(lines[0].count)
    }

    @Test
    fun onlyNonZeroConvergenceCountersGetLinesInFieldOrder() {
        val lines = convergenceDetailLines(
            ConvergenceSummary(
                changed = 2,
                unbackedSongs = 0,
                unlinkedSongs = 1,
                customSongs = 0,
                unlinkedAlbums = 0,
                inconsistentAlbums = 3,
                countMismatchAlbums = 0,
                customAlbums = 0,
            ),
        )

        assertEquals(3, lines.size)
        assertEquals(R.string.maintenance_convergence_copies_rewritten, lines[0].resId)
        assertEquals(2, lines[0].count)
        assertEquals(R.string.maintenance_convergence_unlinked_songs, lines[1].resId)
        assertEquals(1, lines[1].count)
        assertEquals(R.string.maintenance_convergence_inconsistent_albums, lines[2].resId)
        assertEquals(3, lines[2].count)
    }

    @Test
    fun everyNonZeroConvergenceCounterGetsALine() {
        val lines = convergenceDetailLines(ConvergenceSummary(1, 2, 3, 4, 5, 6, 7, 8))

        assertEquals(8, lines.size)
        assertEquals(R.string.maintenance_convergence_copies_rewritten, lines[0].resId)
        assertEquals(1, lines[0].count)
        assertEquals(R.string.maintenance_convergence_custom_albums, lines[7].resId)
        assertEquals(8, lines[7].count)
    }

    // ---- jobs detail lines (the expanded jobs row) ----

    @Test
    fun jobDetailLinesSortAlphabeticallyAndFlagTheAbnormalOnes() {
        val lines = jobDetailLines(
            listOf(
                JobSnapshot("B", state = WorkInfo.State.FAILED, runAtStartTime = 0L),
                JobSnapshot("A", state = WorkInfo.State.SUCCEEDED, runAtStartTime = 0L),
                JobSnapshot("C", state = WorkInfo.State.CANCELLED, runAtStartTime = 0L),
            ),
        )

        assertEquals(listOf("A", "B", "C"), lines.map { it.name })
        assertEquals(false, lines[0].abnormal)
        assertEquals(R.string.maintenance_migration_done, lines[0].stateResId)
        assertEquals(true, lines[1].abnormal)
        assertEquals(R.string.maintenance_work_failed, lines[1].stateResId)
        assertEquals(true, lines[2].abnormal)
        assertEquals(R.string.maintenance_work_cancelled, lines[2].stateResId)
    }

    @Test
    fun allOkJobsAreNeutralAndANeverEnqueuedJobAnswersNeverRun() {
        val lines = jobDetailLines(
            listOf(
                JobSnapshot("A", state = WorkInfo.State.RUNNING, runAtStartTime = 0L),
                JobSnapshot("B", state = null, runAtStartTime = 0L),
            ),
        )

        assertEquals(2, lines.size)
        assertEquals(false, lines.any { it.abnormal })
        assertEquals(R.string.maintenance_work_running, lines[0].stateResId)
        assertEquals(R.string.maintenance_work_never, lines[1].stateResId)
    }

    @Test
    fun absentOrEmptyJobsAnswerNoLines() {
        assertEquals(emptyList<JobDetailLine>(), jobDetailLines(null))
        assertEquals(emptyList<JobDetailLine>(), jobDetailLines(emptyList()))
    }

    // ---- sync row (accounts-card alignment) ----

    @Test
    fun syncRowRunningKeepsItsOwnWordingInAccent() {
        val row = syncRow(true, 1_000_000L, 500_000L)

        assertEquals(R.string.maintenance_sync_ytm_running, row.resId)
        assertNull(row.arg)
        assertEquals(RowColorKey.Accent, row.color)
    }

    @Test
    fun syncRowIdleShowsTheRelativeWholeMinutesInMuted() {
        // 3 minutes and 59 seconds ago -> 3 whole minutes
        val row = syncRow(false, 1_000_000L, 779_000L)

        assertEquals(R.string.sync_status_last, row.resId)
        assertEquals(3L, row.arg)
        assertEquals(RowColorKey.Secondary, row.color)
    }

    @Test
    fun syncRowNeverSyncedReusesTheCardResourceInMuted() {
        val row = syncRow(false, 1_000_000L, 0L)

        assertEquals(R.string.sync_status_never, row.resId)
        assertNull(row.arg)
        assertEquals(RowColorKey.Secondary, row.color)
    }

    @Test
    fun syncRowClockRollbackNeverRendersNegativeMinutes() {
        // lastSync in the future (NTP / user clock rollback): clamped to 0, not negative
        val row = syncRow(false, 1_000_000L, 1_500_000L)

        assertEquals(R.string.sync_status_last, row.resId)
        assertEquals(0L, row.arg)
        assertEquals(RowColorKey.Secondary, row.color)
    }

    // ---- listen-together color key (ConnectionStatusCard alignment) ----

    @Test
    fun ltColorKeyAlignsWithTheConnectionCard() {
        assertEquals(RowColorKey.Accent, ltColorKey(R.string.maintenance_lt_connected))
        assertEquals(RowColorKey.AccentSoft, ltColorKey(R.string.maintenance_lt_connecting))
        assertEquals(RowColorKey.AccentSoft, ltColorKey(R.string.maintenance_lt_reconnecting))
        assertEquals(RowColorKey.Red, ltColorKey(R.string.maintenance_lt_error))
        assertEquals(RowColorKey.Secondary, ltColorKey(R.string.maintenance_lt_disconnected))
        assertEquals(RowColorKey.Secondary, ltColorKey(null))
    }

    // ---- update row (About-card alignment) ----

    @Test
    fun updateRowWithoutTheFeatureIsNotAvailable() {
        val row = updateRow(false, true, UpdateDownloadManager.DownloadState.Idle)

        assertEquals(R.string.maintenance_update_unavailable, row.resId)
        assertEquals(RowColorKey.Secondary, row.color)
    }

    @Test
    fun updateRowIdleWithKnownVersionIsAvailableInAccent() {
        val row = updateRow(true, true, UpdateDownloadManager.DownloadState.Idle)

        assertEquals(R.string.update_available, row.resId)
        assertEquals(RowColorKey.Accent, row.color)
    }

    @Test
    fun updateRowIdleWithoutKnownVersionIsUpToDate() {
        val row = updateRow(true, false, UpdateDownloadManager.DownloadState.Idle)

        assertEquals(R.string.up_to_date, row.resId)
        assertEquals(RowColorKey.Secondary, row.color)
    }

    @Test
    fun updateRowDownloadStatesKeepTheirMapping() {
        val completed = updateRow(true, false, UpdateDownloadManager.DownloadState.Completed("file.apk"))
        assertEquals(R.string.maintenance_update_completed, completed.resId)
        assertEquals(RowColorKey.Secondary, completed.color)

        val failed = updateRow(true, false, UpdateDownloadManager.DownloadState.Failed("network"))
        assertEquals(R.string.maintenance_update_failed, failed.resId)
        assertEquals(RowColorKey.Red, failed.color)
    }

    // ---- MusicBrainz cooldown ----

    @Test
    fun mbCooldownRoundsSecondsUpToWholeMinutes() {
        assertEquals(0L, mbCooldownMinutes(0L))
        assertEquals(1L, mbCooldownMinutes(1L))
        assertEquals(1L, mbCooldownMinutes(60L))
        assertEquals(2L, mbCooldownMinutes(61L))
        assertEquals(2L, mbCooldownMinutes(120L))
    }

    // ---- debug logs row (export availability) ----

    @Test
    fun debugLogsOffHidesExport() {
        val row = debugLogsRow(false, false)

        assertEquals(R.string.maintenance_debug_off, row.baseResId)
        assertFalse(row.restartNeeded)
        assertFalse(row.exportVisible)
    }

    @Test
    fun debugLogsOnWithLogFileShowsExport() {
        val row = debugLogsRow(true, true)

        assertEquals(R.string.maintenance_debug_on, row.baseResId)
        assertFalse(row.restartNeeded)
        assertTrue(row.exportVisible)
    }

    @Test
    fun debugLogsOnWithoutLogFileHidesExportAndFlagsRestartNeeded() {
        val row = debugLogsRow(true, false)

        assertEquals(R.string.maintenance_debug_on, row.baseResId)
        assertTrue(row.restartNeeded)
        assertFalse(row.exportVisible)
    }

    // ---- downloads detail line (state mapping) ----

    @Test
    fun downloadWaitingMapsToWaitingInAccent() {
        val line = downloadDetailLine("Artist - Title", Download.STATE_QUEUED)

        assertEquals(R.string.maintenance_download_queued, line.stateResId)
        assertEquals(RowColorKey.Accent, line.color)
    }

    @Test
    fun downloadInProgressMapsToInProgressInAccent() {
        val downloading = downloadDetailLine("Artist - Title", Download.STATE_DOWNLOADING)
        assertEquals(R.string.maintenance_download_in_progress, downloading.stateResId)
        assertEquals(RowColorKey.Accent, downloading.color)

        val restarting = downloadDetailLine("Artist - Title", Download.STATE_RESTARTING)
        assertEquals(R.string.maintenance_download_in_progress, restarting.stateResId)
        assertEquals(RowColorKey.Accent, restarting.color)
    }

    @Test
    fun downloadCompletedMapsToCompletedInNeutral() {
        val line = downloadDetailLine("Artist - Title", Download.STATE_COMPLETED)

        assertEquals(R.string.maintenance_download_completed, line.stateResId)
        assertEquals(RowColorKey.Secondary, line.color)
    }

    @Test
    fun downloadFailedMapsToFailedInRed() {
        val line = downloadDetailLine("Artist - Title", Download.STATE_FAILED)

        assertEquals(R.string.maintenance_download_failed, line.stateResId)
        assertEquals(RowColorKey.Red, line.color)
    }

    @Test
    fun downloadPausedMapsToPausedInBlue() {
        val line = downloadDetailLine("Artist - Title", Download.STATE_STOPPED)

        assertEquals(R.string.maintenance_downloads_paused, line.stateResId)
        assertEquals(RowColorKey.Blue, line.color)
    }

    @Test
    fun blankDownloadTitleFallsBackToUnknownTitle() {
        assertEquals(R.string.unknown_title, downloadDetailLine(null, Download.STATE_COMPLETED).titleResId)
        assertEquals(R.string.unknown_title, downloadDetailLine("   ", Download.STATE_COMPLETED).titleResId)
        assertNull(downloadDetailLine("Artist - Title", Download.STATE_COMPLETED).titleResId)
    }

    // ---- last crash block ----

    private fun crashBlock(timestamp: String, stackLine: String, causedBy: String? = null) = buildString {
        appendLine(timestamp)
        appendLine()
        appendLine("N-Zik Crash Report")
        appendLine()
        appendLine("Device: test device")
        appendLine()
        appendLine("Stacktrace:")
        appendLine("=".repeat(50))
        appendLine()
        appendLine(stackLine)
        appendLine("\tat com.example.App.main(App.kt:1)")
        if (causedBy != null) {
            appendLine()
            appendLine("Caused by: $causedBy")
            appendLine("\tat com.example.App.root(App.kt:2)")
        }
    }

    @Test
    fun singleCrashBlockParsesTimestampAndFirstStackLine() {
        val block = crashBlock("2026-09-26T14:30:45.123", "java.lang.RuntimeException: Boom")

        val parsed = parseLastCrashBlock(block)

        assertEquals("2026-09-26T14:30:45.123", parsed?.first)
        assertEquals("java.lang.RuntimeException: Boom", parsed?.second)
    }

    @Test
    fun lastCrashBlockWinsOverEarlierOnes() {
        val content = crashBlock("2026-09-01T10:00:00.000", "java.lang.RuntimeException: First") +
            crashBlock("2026-09-26T14:30:45.123", "java.lang.IllegalStateException: Second")

        val parsed = parseLastCrashBlock(content)

        assertEquals("2026-09-26T14:30:45.123", parsed?.first)
        assertEquals("java.lang.IllegalStateException: Second", parsed?.second)
    }

    @Test
    fun crashBlockWithoutTimestampDoesNotParse() {
        assertNull(parseLastCrashBlock(crashBlock("not a timestamp", "java.lang.RuntimeException: Boom").lines().drop(1).joinToString("\n")))
    }

    @Test
    fun crashBlockWithoutStacktraceDoesNotParse() {
        val content = buildString {
            appendLine("2026-09-26T14:30:45.123")
            appendLine()
            appendLine("N-Zik Crash Report")
            appendLine()
            appendLine("Device: test device")
        }

        assertNull(parseLastCrashBlock(content))
    }

    @Test
    fun stacktraceWithoutAReadableLineDoesNotParse() {
        val content = buildString {
            appendLine("2026-09-26T14:30:45.123")
            appendLine()
            appendLine("Stacktrace:")
            appendLine("=".repeat(50))
            appendLine()
        }

        assertNull(parseLastCrashBlock(content))
    }

    // A trailing block cut off by a brutal process death must not hide the previous
    // complete block (the crash row must not report "None" while an older block is
    // still readable).

    @Test
    fun aPartialLastBlockFallsBackToThePreviousCompleteBlock() {
        val content = crashBlock("2026-09-01T10:00:00.000", "java.lang.RuntimeException: First") +
            buildString {
                appendLine("2026-09-26T14:30:45.123")
                appendLine()
                appendLine("N-Zik Crash Report")
            }

        val parsed = parseLastCrashBlock(content)

        assertEquals("2026-09-01T10:00:00.000", parsed?.first)
        assertEquals("java.lang.RuntimeException: First", parsed?.second)
    }

    @Test
    fun aMarkerWithoutAStackLineFallsBackToThePreviousCompleteBlock() {
        val content = crashBlock("2026-09-01T10:00:00.000", "java.lang.RuntimeException: First") +
            buildString {
                appendLine("2026-09-26T14:30:45.123")
                appendLine("Stacktrace:")
                appendLine("=".repeat(50))
            }

        val parsed = parseLastCrashBlock(content)

        assertEquals("2026-09-01T10:00:00.000", parsed?.first)
        assertEquals("java.lang.RuntimeException: First", parsed?.second)
    }

    @Test
    fun aSinglePartialBlockParsesNothing() {
        val content = buildString {
            appendLine("2026-09-26T14:30:45.123")
            appendLine("Stacktrace:")
        }

        assertNull(parseLastCrashBlock(content))
    }

    // ---- formatting ----

    @Test
    fun formatBytesAlwaysUsesMbWithRootLocale() {
        assertEquals("0.0 MB", formatBytes(0L))
        assertEquals("1.0 MB", formatBytes(1_048_576L))
        assertEquals("5.1 MB", formatBytes(5_375_942L))
        assertEquals("1024.0 MB", formatBytes(1_073_741_824L))
    }

    @Test
    fun formatTimestampUsesTheDaySlashMonthPatternInTheGivenZone() {
        // 1750000000000 ms = 2025-06-15T15:06:40Z
        assertEquals("15/06/2025 15:06", formatTimestamp(1_750_000_000_000L, ZoneOffset.UTC))
    }

    @Test
    fun lastRunLinePutsTheDateInFrontOfTheStatus() {
        // 1750000000000 ms = 2025-06-15T15:06:40Z
        val line = lastRunLine(1_750_000_000_000L, "3 copies rewritten", "No data", ZoneOffset.UTC)

        assertEquals("15/06/2025 15:06 · 3 copies rewritten", line)
    }

    @Test
    fun lastRunLineAnswersNoDataWithoutATimestamp() {
        assertEquals("No data", lastRunLine(null, "3 copies rewritten", "No data"))
    }
}
