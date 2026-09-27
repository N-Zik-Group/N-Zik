package app.n_zik.android.core.rewind

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.it.fast4x.rimusic.models.Event
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.Dependencies
import app.n_zik.android.MainActivity
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.RewindReminderWorker
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowInstrumentation
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Test-only application: the workers post on the application-created "rewind" channel
 * (shared with the deck reminder workers — no new channel). [MainApplication] is not used
 * because its onCreate runs the AndroidKeyStore credential migration, which is unavailable
 * on the Robolectric JVM; this app just creates that one channel, exactly like
 * MainApplication.createNotificationChannels does.
 */
class RewindWorkerTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            RewindReminderWorker.CHANNEL_ID,
            getString(R.string.rw_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/**
 * Tests the rewind playlist workers (spec 2) without a live WorkManager:
 * - the creation / notification gate predicates and the pure delay math of
 *   [RewindMonthlyPlaylistWorker] and [RewindYearlyPlaylistWorker];
 * - [doWork] behavior against a real Room database: finished-period creation,
 *   the "already exists" total skip, the toggle-off skip, the notification gating
 *   (toggle off and POST_NOTIFICATIONS denied -> created without notification),
 *   and the self-reschedule seam;
 * - [RewindPostImportRegenerationWorker]: every existing rewind type is regenerated
 *   from the imported history, deleted playlists stay deleted, and a closed database
 *   makes the run retry so it survives the post-import restart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = RewindWorkerTestApplication::class)
class RewindPlaylistWorkersTest {

    @Before
    fun initDependencies() {
        val app = ApplicationProvider.getApplicationContext<RewindWorkerTestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
    }

    // ------------------------------------------------------------------
    // Monthly worker — gates and delay math
    // ------------------------------------------------------------------

    @Test
    fun monthlyGateDefaultsOnAndFollowsTheCreationToggle() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(RewindMonthlyPlaylistWorker.isMonthlyPlaylistEnabled(app))
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, false)
        assertFalse(RewindMonthlyPlaylistWorker.isMonthlyPlaylistEnabled(app))
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, true)
        assertTrue(RewindMonthlyPlaylistWorker.isMonthlyPlaylistEnabled(app))
    }

    @Test
    fun monthlyNotifGateIsOffWhenTheCreationToggleIsOff() {
        val app = RuntimeEnvironment.getApplication()
        setMonthlyToggles(creation = false, notif = true)
        assertFalse(
            "a type off implies its notification off, even with the notif toggle on",
            RewindMonthlyPlaylistWorker.isMonthlyPlaylistNotificationEnabled(app)
        )
        setMonthlyToggles(creation = true, notif = true)
        assertTrue(RewindMonthlyPlaylistWorker.isMonthlyPlaylistNotificationEnabled(app))
    }

    @Test
    fun monthlyDelayIsTheNextMonthStartAndJitterOnlyPushesLater() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.of(2026, 9, 24, 12, 0, 0, 0, zone)
        val expected = Duration.between(now, ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, zone)).toMillis()
        assertEquals(expected, RewindMonthlyPlaylistWorker.msUntilNextMonthStart(now))
        // strictly in the future even on the 1st itself (the fire must compute the
        // finished month, never the current one)
        val firstOfMonth = ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, zone)
        assertTrue(RewindMonthlyPlaylistWorker.msUntilNextMonthStart(firstOfMonth) > 0)
        // positive-only jitter: it can push later, and the clamp keeps the delay >= 0
        assertEquals(10_000L, RewindMonthlyPlaylistWorker.applyJitter(5_000L, 5_000L))
        assertEquals(0L, RewindMonthlyPlaylistWorker.applyJitter(-50_000L, 10_000L))
    }

    // ------------------------------------------------------------------
    // Monthly worker — doWork
    // ------------------------------------------------------------------

    @Test
    fun monthlyDoWorkCreatesTheFinishedMonthPlaylistAndPostsTheNotification() {
        cleanUp(finishedMonthlyName())
        seedFinishedPeriod("mth")
        setMonthlyToggles(creation = true, notif = true)
        setPostNotificationsPermission(granted = true)
        val reschedules = mutableListOf<Context>()
        val worker = monthlyWorker(reschedules)

        val result = runWorker(worker)

        assertTrue(result is ListenableWorker.Result.Success)
        runBlocking {
            awaitPlaylistContaining(finishedMonthlyName(), listOf("mth_seed_a", "mth_seed_b", "mth_seed_c"))
        }
        val notifications = postedNotifications()
        assertEquals(1, notifications.size)
        val title = notifications.first().extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        val finishedMonthName = finishedMonth().month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        assertTrue("the notification should offer the $finishedMonthName playlist, got: \"$title\"", title.contains(finishedMonthName))
        val pending = notifications.first().contentIntent
        assertNotNull("the playlist notification must carry a content intent", pending)
        val contentIntent = shadowOf(pending).savedIntent
        // opens the app without extras: the user navigates to the Playlists tab
        assertEquals(MainActivity::class.java.name, contentIntent.component?.className)
        assertEquals("the worker must self-reschedule after the run", 1, reschedules.size)
    }

    @Test
    fun monthlyDoWorkSkipsWhenThePlaylistAlreadyExists() {
        val name = finishedMonthlyName()
        cleanUp(name)
        seedFinishedPeriod("mth")
        // a pre-existing playlist (e.g. created by the deck pill) with its own content
        insertSong("mth_existing")
        val existingId = insertPlaylist(name)
        runBlocking {
            withContext(NzikDispatchers.DATA) {
                Database.songPlaylistMapTable.map("mth_existing", existingId)
            }
        }
        setMonthlyToggles(creation = true, notif = true)
        setPostNotificationsPermission(granted = true)
        val reschedules = mutableListOf<Context>()
        val worker = monthlyWorker(reschedules)

        val result = runWorker(worker)

        assertTrue(result is ListenableWorker.Result.Success)
        // total skip: same playlist row, original content, no notification
        assertEquals(existingId, runBlocking { Database.playlistTable.findByName(name).first()?.id })
        assertEquals(listOf("mth_existing"), runBlocking { songsIn(existingId) })
        assertEquals(0, postedNotifications().size)
        assertEquals(1, reschedules.size)
    }

    @Test
    fun monthlyDoWorkSkipsCreationWhenTheCreationToggleIsOff() {
        cleanUp(finishedMonthlyName())
        seedFinishedPeriod("mth")
        setMonthlyToggles(creation = false, notif = true)
        val reschedules = mutableListOf<Context>()
        val worker = monthlyWorker(reschedules)

        val result = runWorker(worker)

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(null, runBlocking { Database.playlistTable.findByName(finishedMonthlyName()).first() })
        assertEquals(0, postedNotifications().size)
        // the cadence step still runs (with the gate off it cancels the pending work)
        assertEquals(1, reschedules.size)
    }

    @Test
    fun monthlyDoWorkCreatesWithoutNotificationWhenTheNotifToggleIsOff() {
        cleanUp(finishedMonthlyName())
        seedFinishedPeriod("mth")
        setMonthlyToggles(creation = true, notif = false)
        val reschedules = mutableListOf<Context>()
        val worker = monthlyWorker(reschedules)

        runWorker(worker)

        // the write goes through asyncTransaction (fire-and-forget): poll until visible
        runBlocking { awaitPlaylistVisible(finishedMonthlyName()) }
        assertEquals("the playlist is the purpose, the notification is optional", 0, postedNotifications().size)
        assertEquals(1, reschedules.size)
    }

    @Test
    fun monthlyDoWorkCreatesWithoutNotificationWhenPostNotificationsIsDenied() {
        cleanUp(finishedMonthlyName())
        seedFinishedPeriod("mth")
        setMonthlyToggles(creation = true, notif = true)
        setPostNotificationsPermission(granted = false)
        val reschedules = mutableListOf<Context>()
        val worker = monthlyWorker(reschedules)

        val result = runWorker(worker)

        assertTrue(result is ListenableWorker.Result.Success)
        // a denied POST_NOTIFICATIONS must not skip the creation (poll: fire-and-forget write)
        runBlocking { awaitPlaylistVisible(finishedMonthlyName()) }
        assertEquals(0, postedNotifications().size)
        assertEquals(1, reschedules.size)
    }

    // ------------------------------------------------------------------
    // Yearly worker — gates and delay math
    // ------------------------------------------------------------------

    @Test
    fun yearlyGateDefaultsOnAndFollowsTheCreationToggle() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(RewindYearlyPlaylistWorker.isYearlyPlaylistEnabled(app))
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, false)
        assertFalse(RewindYearlyPlaylistWorker.isYearlyPlaylistEnabled(app))
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, true)
        assertTrue(RewindYearlyPlaylistWorker.isYearlyPlaylistEnabled(app))
    }

    @Test
    fun yearlyNotifGateIsOffWhenTheCreationToggleIsOff() {
        val app = RuntimeEnvironment.getApplication()
        setYearlyToggles(creation = false, notif = true)
        assertFalse(RewindYearlyPlaylistWorker.isYearlyPlaylistNotificationEnabled(app))
        setYearlyToggles(creation = true, notif = true)
        assertTrue(RewindYearlyPlaylistWorker.isYearlyPlaylistNotificationEnabled(app))
    }

    @Test
    fun yearlyDelayIsTheNextJanuaryStartAndJitterOnlyPushesLater() {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.of(2026, 9, 24, 12, 0, 0, 0, zone)
        val expected = Duration.between(now, ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, zone)).toMillis()
        assertEquals(expected, RewindYearlyPlaylistWorker.msUntilNextYearStart(now))
        // strictly in the future even on the 1st of January itself
        val januaryFirst = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
        assertTrue(RewindYearlyPlaylistWorker.msUntilNextYearStart(januaryFirst) > 0)
        assertEquals(10_000L, RewindYearlyPlaylistWorker.applyJitter(5_000L, 5_000L))
        assertEquals(0L, RewindYearlyPlaylistWorker.applyJitter(-50_000L, 10_000L))
    }

    // ------------------------------------------------------------------
    // Yearly worker — doWork
    // ------------------------------------------------------------------

    @Test
    fun yearlyDoWorkCreatesTheFinishedYearPlaylistAndPostsTheNotification() {
        cleanUp(finishedYearlyName())
        seedFinishedYear()
        setYearlyToggles(creation = true, notif = true)
        setPostNotificationsPermission(granted = true)
        val reschedules = mutableListOf<Context>()
        val worker = yearlyWorker(reschedules)

        val result = runWorker(worker)

        assertTrue(result is ListenableWorker.Result.Success)
        runBlocking {
            awaitPlaylistContaining(finishedYearlyName(), listOf("yr_seed_a", "yr_seed_b"))
        }
        val notifications = postedNotifications()
        assertEquals(1, notifications.size)
        val title = notifications.first().extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertTrue(
            "the notification should mention the finished year, got: \"$title\"",
            title.contains(finishedYear().toString())
        )
        assertEquals(1, reschedules.size)
    }

    @Test
    fun yearlyDoWorkSkipsCreationWhenTheCreationToggleIsOff() {
        cleanUp(finishedYearlyName())
        seedFinishedYear()
        setYearlyToggles(creation = false, notif = true)
        val reschedules = mutableListOf<Context>()
        val worker = yearlyWorker(reschedules)

        val result = runWorker(worker)

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(null, runBlocking { Database.playlistTable.findByName(finishedYearlyName()).first() })
        assertEquals(0, postedNotifications().size)
        assertEquals(1, reschedules.size)
    }

    @Test
    fun yearlyDoWorkCreatesWithoutNotificationWhenPostNotificationsIsDenied() {
        cleanUp(finishedYearlyName())
        seedFinishedYear()
        setYearlyToggles(creation = true, notif = true)
        setPostNotificationsPermission(granted = false)
        val reschedules = mutableListOf<Context>()
        val worker = yearlyWorker(reschedules)

        runWorker(worker)

        // a denied POST_NOTIFICATIONS must not skip the creation (poll: fire-and-forget write)
        runBlocking { awaitPlaylistVisible(finishedYearlyName()) }
        assertEquals(0, postedNotifications().size)
        assertEquals(1, reschedules.size)
    }

    // ------------------------------------------------------------------
    // Post-import regeneration
    // ------------------------------------------------------------------

    @Test
    fun postImportRegeneratesEveryExistingRewindType() = runBlocking {
        // three existing rewind playlists with stale content (windows picked in the
        // distant past, in different years, so they cannot collide with the other
        // tests' seeded events — and the monthly window cannot fall inside the yearly one)
        val monthlyName = RewindPlaylists.monthlyName(2023, 3)
        val yearlyName = RewindPlaylists.yearlyName(2024)
        val alltimeName = RewindPlaylists.ALLTIME_NAME
        insertSong("imp_old_m")
        insertSong("imp_old_y")
        insertSong("imp_old_a")
        val oldMonthlyId = insertPlaylist(monthlyName)
        val oldYearlyId = insertPlaylist(yearlyName)
        val oldAlltimeId = insertPlaylist(alltimeName)
        // a regular (non-rewind) playlist: the job must leave it alone
        insertSong("imp_custom")
        val customId = insertPlaylist("my custom playlist")
        // Room blocking DAOs must stay off the test thread (Room main-thread guard)
        withContext(NzikDispatchers.DATA) {
            Database.songPlaylistMapTable.map("imp_old_m", oldMonthlyId)
            Database.songPlaylistMapTable.map("imp_old_y", oldYearlyId)
            Database.songPlaylistMapTable.map("imp_old_a", oldAlltimeId)
            Database.songPlaylistMapTable.map("imp_custom", customId)
        }

        // the "imported history": new events in each window
        insertSong("imp_new_m")
        insertSong("imp_new_y")
        insertSong("imp_new_a")
        val (mFrom, _) = RewindPlaylists.windowFor(monthlyName)!! // name built from a real date: non-null
        val (yFrom, _) = RewindPlaylists.windowFor(yearlyName)!! // name built from a real date: non-null
        withContext(NzikDispatchers.DATA) {
            Database.eventTable.insertIgnore(Event(songId = "imp_new_m", timestamp = mFrom + 60_000, playTime = 800_000))
            Database.eventTable.insertIgnore(Event(songId = "imp_new_y", timestamp = yFrom + 60_000, playTime = 700_000))
            Database.eventTable.insertIgnore(Event(songId = "imp_new_a", timestamp = 1_577_836_800_000, playTime = 900_000))
        }

        val result = runWorker(postImportWorker())

        assertTrue(result is ListenableWorker.Result.Success)
        // the writes go through asyncTransaction (fire-and-forget): poll the settled content
        val newMonthlyId = awaitPlaylistContaining(monthlyName, listOf("imp_new_m"))
        val newYearlyId = awaitPlaylistContaining(yearlyName, listOf("imp_new_y"))
        val newAlltimeId = awaitPlaylistContaining(alltimeName, listOf("imp_new_a", "imp_new_m", "imp_new_y"))
        assertTrue("the monthly playlist was recreated, not updated in place", oldMonthlyId != newMonthlyId)
        assertTrue("the yearly playlist was recreated, not updated in place", oldYearlyId != newYearlyId)
        assertTrue("the alltime playlist was recreated, not updated in place", oldAlltimeId != newAlltimeId)
        assertEquals("only the imported history of that month", listOf("imp_new_m"), songsIn(newMonthlyId))
        assertEquals("only the imported history of that year", listOf("imp_new_y"), songsIn(newYearlyId))
        // alltime = the whole imported history (other tests may have seeded more events
        // in the shared [0, now] window, so containment + relative top order are asserted)
        val alltimeSongs = songsIn(newAlltimeId)
        assertTrue(alltimeSongs.contains("imp_new_a"))
        assertTrue(alltimeSongs.contains("imp_new_m"))
        assertTrue(alltimeSongs.contains("imp_new_y"))
        assertTrue("alltime keeps the top order (900s > 800s > 700s)", alltimeSongs.indexOf("imp_new_a") < alltimeSongs.indexOf("imp_new_m"))
        assertTrue("alltime keeps the top order (800s > 700s)", alltimeSongs.indexOf("imp_new_m") < alltimeSongs.indexOf("imp_new_y"))
        assertEquals("a non-rewind playlist is untouched", listOf("imp_custom"), songsIn(customId))
    }

    @Test
    fun postImportLeavesDeletedPlaylistsDeleted() = runBlocking {
        val name = RewindPlaylists.monthlyName(2022, 5)
        cleanUp(name)
        insertSong("imp_keep_new")
        val (from, _) = RewindPlaylists.windowFor(name)!! // name built from a real date: non-null
        withContext(NzikDispatchers.DATA) {
            Database.eventTable.insertIgnore(Event(songId = "imp_keep_new", timestamp = from + 60_000, playTime = 60_000))
        }

        // first run: the playlist exists, so it is regenerated
        insertPlaylist(name)
        runWorker(postImportWorker())
        awaitPlaylistContaining(name, listOf("imp_keep_new")) // regenerated: the new content is visible

        // the user deletes it: a second run must not resurrect it
        withContext(NzikDispatchers.DATA) {
            val playlist = Database.playlistTable.findByName(name).first()!! // just verified above
            Database.songPlaylistMapTable.clear(playlist.id)
            Database.playlistTable.delete(playlist)
        }
        runWorker(postImportWorker())

        assertEquals("the auto never recreates deleted rewind playlists", null, playlistIdNow(name))
    }

    @Test
    fun postImportRetriesWhenTheDatabaseIsClosed() {
        // the import closes the database before the app restarts: a live run would hit the
        // dead Room singleton, so the run must retry and survive the restart (noted
        // deviation from the frozen spec — guarded, not crashable)
        val field = Database::class.java.getDeclaredField("isClosed")
        field.isAccessible = true
        try {
            field.setBoolean(Database, true)
            val result = runWorker(postImportWorker())
            assertTrue("a closed database must make the run retry, not fail", result is ListenableWorker.Result.Retry)
        } finally {
            field.setBoolean(Database, false)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun finishedMonth(): LocalDate = LocalDate.now().minusMonths(1)
    private fun finishedMonthlyName(): String = RewindPlaylists.monthlyName(finishedMonth().year, finishedMonth().monthValue)
    private fun finishedYear(): Int = LocalDate.now().year - 1
    private fun finishedYearlyName(): String = RewindPlaylists.yearlyName(finishedYear())

    private fun setMonthlyToggles(creation: Boolean, notif: Boolean) {
        val app = RuntimeEnvironment.getApplication()
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_ENABLED, creation)
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_MONTHLY_PLAYLIST_NOTIF_ENABLED, notif)
    }

    private fun setYearlyToggles(creation: Boolean, notif: Boolean) {
        val app = RuntimeEnvironment.getApplication()
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_ENABLED, creation)
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_YEARLY_PLAYLIST_NOTIF_ENABLED, notif)
    }

    /**
     * Robolectric 4.17 keeps the runtime permission state inside [ShadowInstrumentation], but its
     * `grantPermissions`/`denyPermissions` methods are package-private there — the test reaches
     * them through reflection (same approach as RewindReminderWorkerTest).
     */
    private fun setPostNotificationsPermission(granted: Boolean) {
        val instrumentation = shadowOf(ShadowInstrumentation.getInstrumentation())
        val method = ShadowInstrumentation::class.java.declaredMethods
            .first { it.name == (if (granted) "grantPermissions" else "denyPermissions") && it.parameterCount == 1 }
        method.isAccessible = true
        method.invoke(instrumentation, arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    private fun postedNotifications(): List<Notification> =
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java))
            .allNotifications

    /** Deletes the finished-period playlist if a previous test left it behind (the DB persists across methods). */
    private fun cleanUp(name: String) {
        runBlocking {
            Database.playlistTable.findByName(name).first()?.let { playlist ->
                withContext(NzikDispatchers.DATA) {
                    Database.songPlaylistMapTable.clear(playlist.id)
                    Database.playlistTable.delete(playlist)
                }
            }
        }
    }

    /** Seeds three songs (distinct playtimes) with events inside the finished month window. */
    private fun seedFinishedPeriod(prefix: String) {
        val name = finishedMonthlyName()
        val (from, _) = RewindPlaylists.windowFor(name)!! // name built from a real date: non-null
        insertSong("${prefix}_seed_a")
        insertSong("${prefix}_seed_b")
        insertSong("${prefix}_seed_c")
        runBlocking {
            withContext(NzikDispatchers.DATA) {
                Database.eventTable.insertIgnore(Event(songId = "${prefix}_seed_a", timestamp = from + 60_000, playTime = 100_000))
                Database.eventTable.insertIgnore(Event(songId = "${prefix}_seed_b", timestamp = from + 120_000, playTime = 60_000))
                Database.eventTable.insertIgnore(Event(songId = "${prefix}_seed_c", timestamp = from + 180_000, playTime = 30_000))
            }
        }
    }

    /** Seeds two songs (distinct playtimes) with events inside the finished year window. */
    private fun seedFinishedYear() {
        val name = finishedYearlyName()
        val (from, _) = RewindPlaylists.windowFor(name)!! // name built from a real date: non-null
        insertSong("yr_seed_a")
        insertSong("yr_seed_b")
        runBlocking {
            withContext(NzikDispatchers.DATA) {
                Database.eventTable.insertIgnore(Event(songId = "yr_seed_a", timestamp = from + 60_000, playTime = 100_000))
                Database.eventTable.insertIgnore(Event(songId = "yr_seed_b", timestamp = from + 120_000, playTime = 60_000))
            }
        }
    }

    private fun insertSong(id: String) {
        runBlocking {
            withContext(NzikDispatchers.DATA) {
                Database.songTable.upsert(Song.makePlaceholder(id))
            }
        }
    }

    private fun insertPlaylist(name: String): Long =
        runBlocking {
            withContext(NzikDispatchers.DATA) {
                Database.playlistTable.insert(Playlist(name = name))
            }
        }

    /**
     * Runs [ListenableWorker.doWork] off the test thread: on the Robolectric JVM the test
     * thread is the main thread, and Room's main-thread guard would reject the worker's
     * blocking DAO calls — a real WorkManager run happens on a worker thread anyway.
     */
    private fun runWorker(worker: CoroutineWorker): ListenableWorker.Result =
        runBlocking { withContext(NzikDispatchers.DATA) { worker.doWork() } }

    /**
     * Polls until the rewind playlist is visible. The generation path writes through
     * asyncTransaction (fire-and-forget): doWork returning does not mean the write is done.
     */
    private suspend fun awaitPlaylistVisible(name: String): Long {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            val id = Database.playlistTable.findByName(name).first()?.id
            if (id != null) return id
            assertTrue("timed out waiting for the rewind playlist write", System.currentTimeMillis() < deadline)
            delay(50)
        }
    }

    /**
     * Polls until the rewind playlist exists AND contains all of [expectedSongs]: the
     * playlist row appears before its mappings inside the fire-and-forget transaction, so
     * existence alone is not a completion signal.
     */
    private suspend fun awaitPlaylistContaining(name: String, expectedSongs: List<String>): Long {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            val id = Database.playlistTable.findByName(name).first()?.id
            val songs = id?.let { Database.songPlaylistMapTable.allSongsOf(it).first() }?.map { it.id }
                ?: emptyList()
            if (id != null && expectedSongs.all { songs.contains(it) }) return id
            assertTrue(
                "timed out waiting for the rewind playlist content of \"$name\"",
                System.currentTimeMillis() < deadline
            )
            delay(50)
        }
    }

    private suspend fun playlistIdNow(name: String): Long? =
        Database.playlistTable.findByName(name).first()?.id

    private suspend fun songsIn(playlistId: Long): List<String> =
        Database.songPlaylistMapTable.allSongsOf(playlistId).first().map { it.id }

    private fun monthlyWorker(reschedules: MutableList<Context>): RewindMonthlyPlaylistWorker {
        val worker = RewindMonthlyPlaylistWorker(
            RuntimeEnvironment.getApplication(),
            mockk<WorkerParameters>(relaxed = true)
        )
        // the real self-reschedule needs a live WorkManager (unreachable from JVM tests)
        worker.rescheduleHook = { reschedules.add(it) }
        return worker
    }

    private fun yearlyWorker(reschedules: MutableList<Context>): RewindYearlyPlaylistWorker {
        val worker = RewindYearlyPlaylistWorker(
            RuntimeEnvironment.getApplication(),
            mockk<WorkerParameters>(relaxed = true)
        )
        worker.rescheduleHook = { reschedules.add(it) }
        return worker
    }

    private fun postImportWorker(): RewindPostImportRegenerationWorker =
        RewindPostImportRegenerationWorker(
            RuntimeEnvironment.getApplication(),
            mockk<WorkerParameters>(relaxed = true)
        )
}
