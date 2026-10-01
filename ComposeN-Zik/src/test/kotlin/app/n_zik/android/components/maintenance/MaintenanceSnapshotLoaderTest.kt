package app.n_zik.android.components.maintenance

import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.MainApplication
import app.n_zik.android.core.backup.BackupManager
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.profileShareKey
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.listentogether.ListenTogetherClient
import androidx.media3.datasource.cache.Cache
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * Chunk-isolation tests for [loadMaintenanceSnapshot] (spec "Maintenance — état de
 * l'app en un regard"): a failing disk-I/O chunk blanks ONLY its own row — the snapshot
 * stays partial with every other field present, instead of aborting the whole sheet.
 *
 * Plain Robolectric application (no app init): the seeded crash log + seeded prefs make
 * the healthy chunks deterministic, and three chunks (images cache, downloads cache,
 * library counts) are made to throw by mocking — each must blank only its own row.
 * (The library chunk is forced to fail because the lazy Room instance would otherwise
 * self-initialize under Robolectric with an empty file-backed database.) The sync-time
 * and MusicBrainz chunks fall back to their placeholders by default, without touching
 * the other rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MaintenanceSnapshotLoaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // The lazy Room instance would self-initialize under Robolectric (empty file-backed
        // database, all counts 0): force it to throw so the library chunk fails BY ITS OWN
        // FAULT and blanks only its own row.
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } throws IllegalStateException("no database in this test")
        // A ListenTogetherClient singleton may leak into this JVM from other test classes
        // (the same Robolectric classloader is reused for the same SDK): stub getInstance()
        // so the chunk sees "no client" regardless of test execution order.
        mockkObject(ListenTogetherClient.Companion)
        every { ListenTogetherClient.getInstance() } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /** Runs [loadMaintenanceSnapshot] and polls for completion (the loader hops to the DATA pool). */
    private fun loadSnapshot(): MaintenanceSnapshot {
        var result: MaintenanceSnapshot? = null
        val scope = CoroutineScope(SupervisorJob())
        val job = scope.launch {
            result = withTimeout(60_000) { loadMaintenanceSnapshot(context) }
        }
        val deadline = System.currentTimeMillis() + 65_000
        while (job.isActive) {
            check(System.currentTimeMillis() < deadline) { "loadMaintenanceSnapshot did not finish in time" }
            // The scheduled-jobs chunk awaits WorkManager futures, which may complete on the
            // main looper: keep it idle while the DATA-pool work runs.
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        return result ?: error("the snapshot was not produced")
    }

    private fun seedCrashLog() {
        val logsDir = File(context.filesDir, "logs")
        logsDir.mkdirs()
        fun block(timestamp: String, stackLine: String) = buildString {
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
        }
        File(logsDir, "N-Zik_crash_log.txt")
            .writeText(block("2026-09-01T10:00:00.000", "java.lang.RuntimeException: First") +
                block("2026-09-26T14:30:45.123", "java.lang.IllegalStateException: Second"))
    }

    private fun seedPrefs() {
        context.preferences.edit {
            putBoolean(logDebugEnabledKey, true)
        }
    }

    private fun failImagesAndDownloadsChunks() {
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.getCacheSize() } throws IllegalStateException("simulated images cache failure")
        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.getDownloadCache(any()) } throws IllegalStateException("simulated downloads cache failure")
    }

    @Test
    fun aFailingChunkBlanksOnlyItsOwnRow() {
        seedCrashLog()
        seedPrefs()
        failImagesAndDownloadsChunks()

        val snapshot = loadSnapshot()

        // The two failing chunks: their rows are blanked (null)
        assertNull("the images chunk must be blanked by its own failure", snapshot.imagesCache)
        assertNull("the downloads chunk must be blanked by its own failure", snapshot.downloadsCache)

        // Every other field of the partial snapshot is still present
        assertEquals("the download-cache config row must be present", false, snapshot.downloadsCacheDisabled)
        assertEquals("the downloads pause flag row must be present", false, snapshot.downloadsPaused)

        // Seeded crash log: the crash row is parsed from the LAST block, unaffected by the failures
        assertEquals("2026-09-26T14:30:45.123", snapshot.lastCrash?.first)
        assertEquals("java.lang.IllegalStateException: Second", snapshot.lastCrash?.second)

        // Seeded prefs: the prefs-backed rows are unaffected
        assertTrue("the debug-logs row must read the seeded pref", snapshot.debugLogsEnabled)

        // The library chunk fails by its own fault (stubbed database) and blanks only its
        // own row; the sibling DB file-size chunk is still present
        assertNull("the library counts chunk must be blanked by its own failure", snapshot.libraryCounts)
        assertEquals("the DB file size row (sibling chunk) must still be present", 0L, snapshot.dbSizeBytes)
        assertEquals("the sync-time chunk must fall back to its placeholder", 0L, snapshot.lastSyncTime)
        assertEquals("the MusicBrainz chunk must fall back to its placeholder", 0L, snapshot.mbRateLimitedSeconds)

        // Absent persisted keys answer null (tolerant reads), never a failure
        assertNull(snapshot.dedup)
        assertNull(snapshot.convergence)
        assertNull(snapshot.dbCleanup)
        // LT_NEVER_STARTED (stubbed null client): the distinct "Not started" label, not null
        assertEquals(R.string.maintenance_lt_not_started, snapshot.listenTogether?.stateResId)
        assertEquals(MainApplication.CookieStatus.NOT_LOGGED_IN, snapshot.ytSession)
        assertEquals(BackupManager.INTERVAL_NONE, snapshot.backupInterval)

        // The scheduled-jobs chunk: when it completed, it reports exactly the seven pinned jobs
        snapshot.jobs?.let { jobs ->
            assertEquals(MAINTENANCE_JOB_NAMES, jobs.map { it.name })
            assertEquals(jobs.firstOrNull { it.name == "AutoBackupWorker" }, snapshot.backupJob)
        }
    }

    @Test
    fun aLiveProfileSeparateDirsAreCountedInTheStorageRows() {
        // A live user profile ("work") with its own (separate) coil + downloads dirs, and a
        // second profile ("home") sharing BOTH — its dirs map onto the base, which must be
        // counted exactly once (spec-profile-data-separation).
        File(context.filesDir, "Profiles_names.txt").writeText("work\nhome\n")
        context.profilePreferences.edit {
            putBoolean(profileShareKey(ProfileDataItem.IMAGES, "home"), true)
            putBoolean(profileShareKey(ProfileDataItem.DOWNLOADS, "home"), true)
        }
        File(context.cacheDir, "coil_work").apply { mkdirs() }
        File(context.cacheDir, "coil_work/a.bin").writeBytes(ByteArray(100))
        File(context.cacheDir, "exo_downloads_work").apply { mkdirs() }
        File(context.cacheDir, "exo_downloads_work/dl.bin").writeBytes(ByteArray(200))
        // The base dirs hold data too — a broken dedup would walk them a second time
        // (via "home" or instead of the active-profile exclusion) and inflate the totals.
        File(context.cacheDir, "coil").apply { mkdirs() }
        File(context.cacheDir, "coil/base.bin").writeBytes(ByteArray(50))
        File(context.cacheDir, "exo_downloads").apply { mkdirs() }
        File(context.cacheDir, "exo_downloads/base_dl.bin").writeBytes(ByteArray(50))

        // The live (memory-tracked) sizes are non-deterministic across test JVMs (a stale
        // lazy DiskCache / download cache from another test class may point at old dirs):
        // stub the tracked reads to 0 — NOT a failure injection — so the disk walk, the
        // cross-profile aggregation under test, is exact.
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.getCacheSize() } returns 0L
        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.getDownloadCache(any()) } returns mockk<Cache> {
            every { cacheSpace } returns 0L
        }

        val snapshot = loadSnapshot()

        // images = active tracked (0, stubbed) + the other distinct coil dirs walked:
        // exactly coil_work (100). The base coil is the active dir (excluded) and "home"
        // maps onto it (deduped) — so the base is counted exactly once, via the tracked read.
        assertNotNull("the images row must be present without any failure injection", snapshot.imagesCache)
        assertEquals(100L, snapshot.imagesCache!!.first)
        // downloads = active tracked (0, stubbed) + the other distinct download dirs walked:
        // exactly exo_downloads_work (200) — same base-counted-once property.
        assertNotNull("the downloads row must be present without any failure injection", snapshot.downloadsCache)
        assertEquals(200L, snapshot.downloadsCache!!.first)
    }
}
