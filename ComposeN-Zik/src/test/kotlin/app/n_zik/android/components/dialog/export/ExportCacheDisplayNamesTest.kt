package app.n_zik.android.components.dialog.export

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.models.Format
import app.n_zik.android.models.Song
import app.n_zik.android.appContext
import app.n_zik.android.components.player.lyrics.LyricsFileCodec
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.modules.FfmpegModule
import app.n_zik.android.modules.ModuleState
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ExportCacheDisplayNamesTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test fun `cache LRC sidecar strips Lyricsfile storage metadata and keeps the timestamp`() {
        val imported = LyricsFileCodec.decode(
            """
            version: "1.0"
            metadata:
              title: Example
              artist: Example Artist
            lines:
              - text: First line
                start_ms: 1234
                end_ms: 4000
            """.trimIndent()
        )

        assertEquals(LyricsType.Synced, imported.type)
        assertEquals("[00:01.234]First line", ExportCacheDialog.toLrcSidecar(imported.type, imported.text))
    }

    // The export API launches a job; wait for its real progress and document-creation path.
    @Test fun `batch export cleans progress and filename while reading the original song id`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val song = Song("modified:source-id", "modified:e:Title: live", "modified:Artist", "3:00", null)
        val formatTable = mockk<FormatTable>()
        val cache = mockk<Cache>()
        val binder = mockk<PlayerServiceModern.Binder>()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(Database, FfmpegModule)
        every { Database.formatTable } returns formatTable
        every { formatTable.findBySongId(song.id) } returns flowOf(Format(song.id, mimeType = "audio/ogg", contentLength = 100))
        every { binder.cache } returns cache
        every { binder.downloadCache } returns cache
        every { cache.isCached(song.id, 0, 100) } returns true
        coEvery { FfmpegModule.ensureReady() } returns ModuleState.READY
        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.createDocument(any(), any(), any(), any()) } returns null
        val progress = mutableListOf<String>()
        var result: Pair<Int, Int>? = null

        ExportCacheDialog.batchExport(
            Uri.parse("content://test.documents/tree/root"), listOf(song), binder,
            onProgress = { _, _, title -> progress += title },
            onComplete = { success, failed -> result = success to failed },
        ).join()

        assertEquals(listOf("Title: live"), progress)
        verify { DocumentsContract.createDocument(any(), any(), "audio/ogg", "Title_ live - Artist.ogg") }
        verify { formatTable.findBySongId("modified:source-id") }
        assertEquals(0 to 1, result)
        assertEquals("modified:e:Title: live", song.title)
        assertEquals("modified:Artist", song.artistsText)
    }
}
