package app.n_zik.android

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.maintenance.MaintenanceConvergenceState
import app.n_zik.android.core.maintenance.MaintenanceDedupState
import app.n_zik.android.core.maintenance.MaintenanceDbCleanupState
import app.n_zik.android.core.maintenance.MaintenanceStateStore
import app.n_zik.android.core.migration.ConvergenceSummary
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wiring tests for the maintenance state written by the sequential boot chain in
 * [MainApplication.onCreate] (`DbCleanup` -> same-name dedup -> name convergence, one
 * fire-and-forget coroutine on the DATA pool):
 *
 * - a FAILING pass never writes: the previously persisted state is kept intact (the pass
 *   retries on the next launch, exactly like before the maintenance state existed);
 * - a SUCCESSFUL pass rewrites the persisted state with the fresh counters.
 *
 * Precedent: [app.n_zik.android.core.rescue.RescueKillWiringTest] (instantiating
 * [MainApplication] by hand under Robolectric) and
 * [app.n_zik.android.core.migration.SameNameArtistDedupTest] (redirecting
 * [DatabaseInitializer.Instance] to an in-memory Room database). The chain is
 * fire-and-forget, so the tests POLL the persisted state with a timeout instead of
 * awaiting a signal.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MaintenanceBootChainTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var db: DatabaseInitializer

    /** A 2023 timestamp: any rewrite by the chain (2026+) is distinguishable from the seed. */
    private val seedTimestamp = 1_700_000_000_000L

    @After
    fun tearDown() {
        unmockkAll()
        if (::db.isInitialized) db.close()
    }

    /**
     * Seeds the three persisted boot-pass states with distinctive values, so "kept" and
     * "rewritten" are unambiguous.
     */
    private fun seedPersistedState() {
        MaintenanceStateStore.saveDbCleanup(
            context,
            MaintenanceDbCleanupState(seedTimestamp, removed = 42, removedLinks = emptyList()),
        )
        MaintenanceStateStore.saveDedup(
            context,
            MaintenanceDedupState(
                timestamp = seedTimestamp,
                groups = 7,
                resolved = 1,
                songResolved = 2,
                merged = 3,
                flagged = 4,
                skipped = 5,
                deferred = 6,
                localKept = 0,
                records = emptyList(),
            ),
        )
        MaintenanceStateStore.saveConvergence(
            context,
            MaintenanceConvergenceState(seedTimestamp, ConvergenceSummary(9, 1, 1, 1, 1, 1, 1, 1)),
        )
    }

    /**
     * Starts the app exactly like [RescueKillWiringTest]: Robolectric pins a plain
     * Application, so [MainApplication] is never created by the runtime; attachBaseContext
     * is protected, hence the plain reflective call. The credential migration inside
     * onCreate builds an AndroidKeyStore-backed EncryptedSharedPreferences, which does not
     * exist on Robolectric's JVM: stub the entry point with a relaxed mock (the migration
     * loops over empty prefs, so nothing is actually migrated). Post-chain init (the
     * WorkManager worker scheduling) also requires WorkManager, which is not initialized in
     * this harness: it throws AFTER the maintenance chain has been launched, so the failure
     * is swallowed (test harness only — the chain itself starts well before it).
     */
    private fun launchApp() {
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns mockk<SharedPreferences>(relaxed = true)
        ShadowProcess.setProcessName(context.packageName)
        val app = MainApplication()
        ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        try {
            app.onCreate()
        } catch (e: Exception) {
            // Expected under Robolectric: post-chain init fails (WorkManager not initialized).
            // The maintenance chain was already launched, so the assertions below still verify
            // its full effect; if the chain itself had not started, the await() polling
            // would time out with a clear message instead.
        }
    }

    /** The boot chain is fire-and-forget on the DATA pool: poll [condition] until [timeoutMs]. */
    private fun await(timeoutMs: Long, what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for: $what" }
            Thread.sleep(25)
        }
    }

    /** Makes [DatabaseInitializer] answer with a database whose DAOs all throw. */
    private fun redirectDatabaseToFailing(ran: AtomicBoolean) {
        val failingDb = mockk<DatabaseInitializer> {
            // Every DAO access of the three passes throws, so each pass fails on its
            // first query — the whole chain fails, deterministically.
            every { songTable } answers { ran.set(true); throw IllegalStateException("no database in this test") }
            every { artistTable } answers { ran.set(true); throw IllegalStateException("no database in this test") }
            every { albumTable } answers { ran.set(true); throw IllegalStateException("no database in this test") }
            every { songArtistMapTable } answers { ran.set(true); throw IllegalStateException("no database in this test") }
            every { songAlbumMapTable } answers { ran.set(true); throw IllegalStateException("no database in this test") }
        }
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns failingDb
    }

    @Test
    fun aFailingPassKeepsThePreviouslyPersistedState() {
        seedPersistedState()
        val chainRan = AtomicBoolean(false)
        redirectDatabaseToFailing(chainRan)
        launchApp()

        // The chain is fire-and-forget: wait until it has at least attempted the passes.
        await(20_000, "the boot chain to attempt the passes") { chainRan.get() }

        // A failed pass never writes: every seeded value must be kept as-is.
        val dbCleanup = MaintenanceStateStore.readDbCleanup(context)
        assertNotNull("the persisted db-cleanup state must survive a failing pass", dbCleanup)
        assertEquals(seedTimestamp, dbCleanup!!.timestamp)
        assertEquals(42, dbCleanup.removed)

        val dedup = MaintenanceStateStore.readDedup(context)
        assertNotNull("the persisted dedup state must survive a failing pass", dedup)
        assertEquals(seedTimestamp, dedup!!.timestamp)
        assertEquals(7, dedup.groups)

        val convergence = MaintenanceStateStore.readConvergence(context)
        assertNotNull("the persisted convergence state must survive a failing pass", convergence)
        assertEquals(seedTimestamp, convergence!!.timestamp)
        assertEquals(9, convergence.summary.changed)
    }

    @Test
    fun aSuccessfulPassRewritesThePersistedState() {
        seedPersistedState()
        // An EMPTY in-memory database: all three passes succeed with zero counters.
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
        launchApp()

        // The chain is fire-and-forget: poll until all three passes have written their
        // fresh state (each keeps the SEQUENCE, so all three rewritten = chain done).
        await(30_000, "the boot chain to write all three states") {
            MaintenanceStateStore.readDbCleanup(context)?.timestamp != seedTimestamp &&
                MaintenanceStateStore.readDedup(context)?.timestamp != seedTimestamp &&
                MaintenanceStateStore.readConvergence(context)?.timestamp != seedTimestamp
        }

        val dbCleanup = MaintenanceStateStore.readDbCleanup(context)
        assertNotNull(dbCleanup)
        assertNotEquals("the db-cleanup state must be rewritten by the successful pass", seedTimestamp, dbCleanup!!.timestamp)
        assertTrue(dbCleanup.timestamp > seedTimestamp)
        assertEquals("an empty database has nothing to clean", 0, dbCleanup.removed)

        val dedup = MaintenanceStateStore.readDedup(context)
        assertNotNull(dedup)
        assertNotEquals("the dedup state must be rewritten by the successful pass", seedTimestamp, dedup!!.timestamp)
        assertEquals("an empty database has no same-name groups", 0, dedup.groups)
        assertTrue("an empty database has no dedup groups to record", dedup.records.isEmpty())

        val convergence = MaintenanceStateStore.readConvergence(context)
        assertNotNull(convergence)
        assertNotEquals("the convergence state must be rewritten by the successful pass", seedTimestamp, convergence!!.timestamp)
        assertEquals("an empty database is already converged", 0, convergence.summary.changed)
        assertEquals(0, convergence.summary.unbackedSongs)
    }
}
