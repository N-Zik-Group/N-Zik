package app.n_zik.android.components.dialog.song

import java.util.concurrent.Executor

import android.content.Context
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadService
import io.mockk.coEvery
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.song
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import app.n_zik.android.models.Song
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.download.services.MyDownloadService
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.playback.services.PlayerServiceModern

/** Uses runBlocking to await suspend update calls from synchronous JUnit tests. */
class SongUpdateStorageResetTest {
    private val context = mockk<Context>(relaxed = true)
    private val songTable = mockk<SongTable>(relaxed = true)
    private val formatTable = mockk<FormatTable>(relaxed = true)
    private val internal = mockk<DatabaseInitializer>(relaxed = true)
    private val cache = mockk<Cache>(relaxed = true)
    private val downloadCache = mockk<Cache>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>()
    private val writtenRows = mutableListOf<Song>()
    private var profile = "active"
    private var inTransaction = false
    private val stored = Song("abcdefghijk", "Stored title", "Stored artist", "3:00", "cover", 123L, 4567L)
        .apply { playCount = 7 }

    @Before
    fun setup() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkStatic("app.n_zik.android.utils.ProfilePreferencesKt")
        every { getActiveProfile(any()) } answers { profile }
        mockkStatic("app.n_zik.innertube.requests.QueueKt")
        coEvery { Innertube.song(any()) } returns null
        mockkObject(Database)
        every { Database.songTable } returns songTable
        every { Database.formatTable } returns formatTable
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns internal
        every { internal.transactionExecutor } returns Executor { it.run() }
        every { Database.syncTransaction(any()) } answers {
            inTransaction = true
            try {
                firstArg<Database.() -> Unit>().invoke(Database)
            } finally {
                inTransaction = false
            }
        }
        every { songTable.findByIdDirect(any()) } returns stored
        every { songTable.updateReplace(any()) } answers { writtenRows.add(firstArg()) }
        every { binder.cache } returns cache
        every { binder.downloadCache } returns downloadCache
        mockkObject(WaveformExtractor)
        every { WaveformExtractor.deleteWaveformOrThrow(any(), any()) } just runs
        mockkStatic(DownloadService::class)
        every { DownloadService.sendRemoveDownload(context, MyDownloadService::class.java, any(), false) } answers {
            assertFalse("File removal must follow the Room commit", inTransaction)
            assertTrue(writtenRows.isNotEmpty())
        }
        mockkObject(Toaster)
        every { Toaster.done() } just runs
        every { Toaster.e(any<Int>(), any<Int>()) } just runs
        every { Toaster.e(any<String>(), any<Int>()) } just runs
        every { Toaster.w(any<String>(), any<Int>()) } just runs
        every { Toaster.s(any(), any<Long>()) } just runs
    }

    @After
    fun teardown() = unmockkAll()

    @Test
    fun `offline option defaults off and needs no provider fetch`() {
        assertFalse(SongUpdateSelection().offlineDownload)
        assertTrue(SongUpdateSelection(offlineDownload = true).isAnySelected)
        assertFalse(SongUpdateSelection(offlineDownload = true).needsFetch)
    }

    @Test
    fun `cache reset without its service fails before changing any data`() = runBlocking {
        runSongUpdate(null, stored, SongUpdateSelection(cache = true, favorite = true), "active")
        verify(exactly = 1) { Toaster.e(any<String>(), android.widget.Toast.LENGTH_LONG) }
        verify(exactly = 0) { Toaster.done() }
        verify { songTable wasNot Called }
        verify { formatTable wasNot Called }
        verify(exactly = 0) { WaveformExtractor.deleteWaveformOrThrow(any(), any()) }
        verify(exactly = 0) { DownloadService.sendRemoveDownload(any(), any(), any(), any()) }
        coVerify(exactly = 0) { Innertube.song(any()) }
    }

    @Test
    fun `download only removes through the service after commit and preserves playback cache`() = runBlocking {
        assertTrue(runSongUpdateCore(null, stored, SongUpdateSelection(offlineDownload = true), "active"))
        verify(exactly = 1) { DownloadService.sendRemoveDownload(context, MyDownloadService::class.java, stored.id, false) }
        verify(exactly = 0) { cache.removeResource(any()) }
        verify(exactly = 0) { downloadCache.removeResource(any()) }
        verify(exactly = 0) { WaveformExtractor.deleteWaveformOrThrow(any(), any()) }
        verify { formatTable wasNot Called }
        assertEquals(listOf(stored), writtenRows)
        assertEquals(7, writtenRows.single().playCount)
        coVerify(exactly = 0) { Innertube.song(any()) }
    }

    @Test
    fun `cache only never sends download removal or changes shared format`() = runBlocking {
        runSongUpdateCore(binder, stored, SongUpdateSelection(cache = true), "active")
        verify(exactly = 1) { cache.removeResource(stored.id) }
        verify(exactly = 1) { WaveformExtractor.deleteWaveformOrThrow(context, stored.id) }
        verify(exactly = 0) { downloadCache.removeResource(any()) }
        verify(exactly = 0) { DownloadService.sendRemoveDownload(any(), any(), any(), any()) }
        verify { formatTable wasNot Called }
        coVerify(exactly = 0) { Innertube.song(any()) }
    }

    @Test
    fun `selecting both storage actions dispatches each independently`() = runBlocking {
        runSongUpdateCore(binder, stored, SongUpdateSelection(cache = true, offlineDownload = true), "active")
        verify(exactly = 1) { cache.removeResource(stored.id) }
        verify(exactly = 1) { DownloadService.sendRemoveDownload(context, MyDownloadService::class.java, stored.id, false) }
        verify(exactly = 0) { downloadCache.removeResource(any()) }
        verify { formatTable wasNot Called }
    }

    @Test
    fun `download reset does not remove local media`() = runBlocking {
        val local = stored.copy(id = "local:track")
        every { songTable.findByIdDirect(local.id) } returns local
        runSongUpdateCore(binder, local, SongUpdateSelection(offlineDownload = true), "active")
        verify(exactly = 0) { DownloadService.sendRemoveDownload(any(), any(), any(), any()) }
    }

    @Test
    fun `profile switch after commit prevents the download request`() = runBlocking {
        every { songTable.updateReplace(any()) } answers {
            writtenRows.add(firstArg())
            profile = "other"
        }
        assertFalse(runSongUpdateCore(binder, stored, SongUpdateSelection(offlineDownload = true), "active"))
        verify(exactly = 0) { DownloadService.sendRemoveDownload(any(), any(), any(), any()) }
    }

    @Test
    fun `download request failure reports an error instead of a done toast`() = runBlocking {
        every { DownloadService.sendRemoveDownload(context, MyDownloadService::class.java, stored.id, false) } throws
            IllegalStateException("Service could not accept the request")
        runSongUpdate(binder, stored, SongUpdateSelection(offlineDownload = true), "active")
        verify(exactly = 1) { Toaster.e(any<String>(), android.widget.Toast.LENGTH_LONG) }
        verify(exactly = 0) { Toaster.done() }
    }
}
