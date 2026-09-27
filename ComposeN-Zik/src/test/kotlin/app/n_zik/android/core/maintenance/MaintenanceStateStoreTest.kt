package app.n_zik.android.core.maintenance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.migration.ConvergenceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract of [MaintenanceStateStore] (spec "Maintenance — état de l'app en un
 * regard"): the three boot passes persist their last SUCCESSFUL run as one JSON
 * key each in the `app_settings` SharedPreferences file.
 *
 * - save -> read round-trip for the three states (dedup with its full per-group
 *   records, convergence with its 8-counter summary, db cleanup);
 * - absent key -> null ("no data" for the row, FIRST_INSTALL edge case);
 * - corrupted JSON -> null (the strict top-level read throws, the read maps it
 *   to null via runCatching — a bad pass never erases the previous state, and a
 *   bad blob never crashes the sheet);
 * - record entries are tolerant: missing `m`/`f`/`r` fields fall back to their
 *   defaults (forward compatibility for older blobs).
 *
 * Robolectric provides the SharedPreferences backing + the real org.json
 * (same pattern as SameNameArtistDedupPrefsSkipStoreTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MaintenanceStateStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ---- dedup ----

    @Test
    fun dedupSaveThenReadReturnsTheSameState() {
        val state = MaintenanceDedupState(
            timestamp = 1_750_000_000_000L,
            groups = 6,
            resolved = 2,
            songResolved = 1,
            merged = 3,
            flagged = 1,
            skipped = 2,
            deferred = 1,
            localKept = 4,
            records = listOf(
                DedupGroupRecord("A", DedupGroupStatus.RESOLVED, mergedRows = 2, flaggedRows = 1),
                DedupGroupRecord("B", DedupGroupStatus.SONG_RESOLVED, mergedRows = 1),
                DedupGroupRecord("C", DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.CUSTOM_NAME),
                DedupGroupRecord("D", DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.OFFLINE),
                DedupGroupRecord("E", DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.NO_UNANIMOUS_CANDIDATE),
                DedupGroupRecord("F", DedupGroupStatus.SKIPPED, skipReason = DedupSkipReason.NETWORK_FAILURE),
                DedupGroupRecord("G", DedupGroupStatus.DEFERRED),
                DedupGroupRecord("H", DedupGroupStatus.SKIPPED),
            ),
        )

        MaintenanceStateStore.saveDedup(context, state)

        assertEquals(state, MaintenanceStateStore.readDedup(context))
    }

    @Test
    fun dedupReadReturnsNullWhenTheKeyIsAbsent() {
        assertNull(MaintenanceStateStore.readDedup(context))
    }

    @Test
    fun dedupReadReturnsNullOnCorruptedJson() {
        corrupt(MaintenanceStateStore.KEY_DEDUP, "not a json at all")
        assertNull(MaintenanceStateStore.readDedup(context))
        corrupt(MaintenanceStateStore.KEY_DEDUP, """{"ts":1}""") // missing top-level fields
        assertNull(MaintenanceStateStore.readDedup(context))
    }

    @Test
    fun dedupRecordEntriesAreTolerantToMissingCounters() {
        val json = """{"ts":1,"groups":1,"resolved":0,"songResolved":0,"merged":0,"flagged":0,"skipped":1,"deferred":0,"localKept":0,"records":[{"n":"A","s":"SKIPPED"}]}"""
        val state = MaintenanceStateStore.dedupFromJson(json)

        val record = state.records.single()
        assertEquals("A", record.name)
        assertEquals(DedupGroupStatus.SKIPPED, record.status)
        assertEquals(0, record.mergedRows)
        assertEquals(0, record.flaggedRows)
        assertNull(record.skipReason)
    }

    @Test
    fun dedupFromJsonThrowsOnCorruptedJson() {
        assertThrows("not a json") { MaintenanceStateStore.dedupFromJson("not a json") }
        assertThrows("missing top-level fields") {
            MaintenanceStateStore.dedupFromJson("""{"ts":1}""")
        }
        assertThrows("unknown status value") {
            MaintenanceStateStore.dedupFromJson(
                """{"ts":1,"groups":0,"resolved":0,"songResolved":0,"merged":0,"flagged":0,"skipped":0,"deferred":0,"localKept":0,"records":[{"n":"A","s":"BOGUS"}]}"""
            )
        }
    }

    // ---- convergence ----

    @Test
    fun convergenceSaveThenReadReturnsTheSameState() {
        val state = MaintenanceConvergenceState(
            timestamp = 1_750_000_001_000L,
            summary = ConvergenceSummary(
                changed = 3,
                unbackedSongs = 1,
                unlinkedSongs = 2,
                customSongs = 4,
                unlinkedAlbums = 5,
                inconsistentAlbums = 6,
                countMismatchAlbums = 7,
                customAlbums = 8,
            ),
        )

        MaintenanceStateStore.saveConvergence(context, state)

        assertEquals(state, MaintenanceStateStore.readConvergence(context))
    }

    @Test
    fun convergenceReadReturnsNullWhenAbsentOrCorrupted() {
        assertNull(MaintenanceStateStore.readConvergence(context))
        corrupt(MaintenanceStateStore.KEY_CONVERGENCE, "{corrupt")
        assertNull(MaintenanceStateStore.readConvergence(context))
    }

    @Test
    fun convergenceFromJsonThrowsOnCorruptedJson() {
        assertThrows("missing summary") {
            MaintenanceStateStore.convergenceFromJson("""{"ts":1}""")
        }
        assertThrows("not a json") { MaintenanceStateStore.convergenceFromJson("nope") }
    }

    // ---- db cleanup ----

    @Test
    fun dbCleanupSaveThenReadReturnsTheSameState() {
        val state = MaintenanceDbCleanupState(
            timestamp = 1_750_000_002_000L,
            removed = 2,
            removedLinks = listOf(
                DbCleanupLinkRecord("Song A", "Artist X"),
                DbCleanupLinkRecord("Song B", null),
            ),
        )

        MaintenanceStateStore.saveDbCleanup(context, state)

        assertEquals(state, MaintenanceStateStore.readDbCleanup(context))
    }

    @Test
    fun dbCleanupFromJsonWithoutLinksKeyKeepsTheCounterWithNoRecords() {
        // States written before per-link records existed carry no `links` array
        val state = MaintenanceStateStore.dbCleanupFromJson("""{"ts":1,"removed":3}""")

        assertEquals(3, state.removed)
        assertTrue(state.removedLinks.isEmpty())
    }

    @Test
    fun dbCleanupReadReturnsNullWhenAbsentOrCorrupted() {
        assertNull(MaintenanceStateStore.readDbCleanup(context))
        corrupt(MaintenanceStateStore.KEY_DB_CLEANUP, """{"ts":"x"}""")
        assertNull(MaintenanceStateStore.readDbCleanup(context))
    }

    @Test
    fun dbCleanupFromJsonThrowsOnCorruptedJson() {
        assertThrows("missing removed") { MaintenanceStateStore.dbCleanupFromJson("""{"ts":1}""") }
        assertThrows("not a json") { MaintenanceStateStore.dbCleanupFromJson("...") }
    }

    // ---- helpers ----

    /** Writes a raw (corrupted) value straight into the `app_settings` file. */
    private fun corrupt(key: String, value: String) {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    /** JUnit4-friendly assertThrows (the vintage engine's JUnit version is not pinned here). */
    private fun assertThrows(label: String, block: () -> Unit) {
        try {
            block()
        } catch (expected: Exception) {
            return
        }
        throw AssertionError("Expected a throwable ($label) but nothing was thrown")
    }
}
