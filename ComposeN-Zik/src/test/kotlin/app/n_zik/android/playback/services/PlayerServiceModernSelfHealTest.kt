package app.n_zik.android.playback.services

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.core.maintenance.MaintenanceStateStore
import app.n_zik.android.download.utils.MyDownloadHelper
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * S2 (Phase 1 download fix): the pre-playback self-heal seam [selfHealStaleDownloadEntry] —
 * an indexed entry whose span files were wiped gets removed from the index, the re-download
 * toast is shown, and the healed track (mediaId + title, blank title falls back to the
 * mediaId) is persisted for the Maintenance sheet; a track with intact files is left
 * untouched and persists nothing.
 *
 * JUnit 4 + [RobolectricTestRunner] (executed through junit-vintage-engine on the JUnit
 * 5 platform), mirroring [StaleDownloadEntryTest]: a real [SimpleCache] backs
 * [isStaleDownloadEntry], the [MyDownloadHelper.downloads] map carries the "downloaded"
 * claim, and the index removal + toast are stubbed at the object level with concrete
 * arguments (the Toaster vararg overload does not match via anyVararg).
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerServiceModernSelfHealTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var cache: SimpleCache

    @After
    fun resetHelperState() {
        unmockkObject(MyDownloadHelper)
        unmockkObject(Toaster)
        if (::cache.isInitialized) cache.release()
        MyDownloadHelper.downloads.value = emptyMap()
        // The healed track is persisted by the self-heal — clear the key so the static
        // never leaks into the next test class of the same JVM fork
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit()
            .remove(MaintenanceStateStore.KEY_DOWNLOAD_SELF_HEAL)
            .apply()
    }

    private fun download(id: String): Download {
        val request = DownloadRequest.Builder(id, Uri.parse("https://music.example.com/stream/$id")).build()
        return Download(request, Download.STATE_COMPLETED, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
    }

    private fun mediaItem(mediaId: String, title: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .apply { title?.let { setMediaMetadata(MediaMetadata.Builder().setTitle(it).build()) } }
            .build()

    /** Creates the cache and commits one complete span under [id]; returns the span file. */
    private fun setupCacheWithSpan(id: String): File {
        cache = SimpleCache(
            tempFolder.root,
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context)
        )
        cache.startReadWriteNonBlocking(id, 0L, 1000L)
        val file = cache.startFile(id, 0L, 1000L)
        file.writeBytes(ByteArray(1000))
        cache.commitFile(file, 1000L)
        return file
    }

    private fun stubIndexRemoval() {
        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.removeDownload(any(), any()) } just Runs
    }

    @Test
    fun `a stale entry is removed, toasted and persisted`() {
        val file = setupCacheWithSpan("ABXtWqmArUU")
        // The OS/OEM wipe: the span file is gone, the index still reports it
        assertTrue(file.delete())
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf("ABXtWqmArUU" to download("ABXtWqmArUU"))
        stubIndexRemoval()
        mockkObject(Toaster)
        every { Toaster.w(R.string.download_stale_removed, "Artist X - Song A", duration = Toast.LENGTH_LONG) } just Runs

        val healed = selfHealStaleDownloadEntry(context, mediaItem("ABXtWqmArUU", "Artist X - Song A"), cache)

        assertTrue("the stale entry must be healed", healed)
        verify(exactly = 1) { MyDownloadHelper.removeDownload(context, any()) }
        val state = requireNotNull(MaintenanceStateStore.readDownloadSelfHeal(context)) {
            "the healed track must be persisted for the Maintenance sheet"
        }
        assertEquals("ABXtWqmArUU", state.mediaId)
        assertEquals("Artist X - Song A", state.title)
    }

    @Test
    fun `an intact entry is left untouched and persists nothing`() {
        setupCacheWithSpan("na47wMFfQCo")
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf("na47wMFfQCo" to download("na47wMFfQCo"))
        stubIndexRemoval()
        mockkObject(Toaster)

        val healed = selfHealStaleDownloadEntry(context, mediaItem("na47wMFfQCo", "Artist Y - Song B"), cache)

        assertFalse("an intact entry must not be healed", healed)
        verify(exactly = 0) { MyDownloadHelper.removeDownload(any(), any()) }
        assertNull("no self-heal state may be persisted for an intact entry", MaintenanceStateStore.readDownloadSelfHeal(context))
    }

    @Test
    fun `a missing title falls back to the mediaId`() {
        val file = setupCacheWithSpan("Zk9qWbR7xMk")
        assertTrue(file.delete())
        MyDownloadHelper.downloadCache = cache
        MyDownloadHelper.downloads.value = mapOf("Zk9qWbR7xMk" to download("Zk9qWbR7xMk"))
        stubIndexRemoval()
        mockkObject(Toaster)
        every { Toaster.w(R.string.download_stale_removed, "", duration = Toast.LENGTH_LONG) } just Runs

        val healed = selfHealStaleDownloadEntry(context, mediaItem("Zk9qWbR7xMk"), cache)

        assertTrue("the stale entry must be healed", healed)
        val state = requireNotNull(MaintenanceStateStore.readDownloadSelfHeal(context)) {
            "the healed track must be persisted for the Maintenance sheet"
        }
        assertEquals("Zk9qWbR7xMk", state.mediaId)
        assertEquals("Zk9qWbR7xMk", state.title)
    }
}
