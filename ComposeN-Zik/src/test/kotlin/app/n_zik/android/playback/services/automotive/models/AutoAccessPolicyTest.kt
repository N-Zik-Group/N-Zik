package app.n_zik.android.playback.services.automotive.models

import android.app.Application
import androidx.media3.common.MediaItem
import android.net.Uri
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.models.Format
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.ext.FormatWithSong
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.DislikedExclusion
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoAccessPolicyTest {
    @After fun cleanup() = unmockkAll()

    private fun item(id: String, key: String = id) = MediaItem.Builder().setMediaId(id)
        .setUri("https://example.com/$key").setCustomCacheKey(key).build()

    // JUnit is synchronous; bridge the suspend access-policy query.
    @Test fun `offline accepts only completed downloads and local occurrences using canonical cache keys`() = runBlocking {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        val downloads = MyDownloadHelper
        val priorDownloads = downloads.downloads
        fun download(id: String, state: Int) = Download(DownloadRequest.Builder(id, Uri.parse("https://example.com/$id")).build(),
            state, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
        val complete = download("done", Download.STATE_COMPLETED)
        val pending = download("pending", Download.STATE_DOWNLOADING)
        downloads.downloads = MutableStateFlow(mapOf("done" to complete, "pending" to pending))
        try {
            val policy = AutoAccessPolicy(RuntimeEnvironment.getApplication(), Database, downloads, null)
            val rows = listOf(item("album/x/done", "done"), item("pending"), item("album/x/local:9", "local:9"), item("done"))
            assertEquals(listOf(0, 2, 3), policy.eligible(rows).map { it.index })
            assertFalse(policy.allowsCommand(AutoSessionConstants.ACTION_START_RADIO))
            assertFalse(policy.allowsCommand(AutoSessionConstants.ACTION_TOGGLE_DOWNLOAD))
            assertTrue(policy.allowsCommand(AutoSessionConstants.ACTION_TOGGLE_LIKE))
        } finally { downloads.downloads = priorDownloads }
    }

    // JUnit is synchronous; bridge the suspend access-policy query.
    @Test fun `offline full cache requires a positive known length and complete span`() = runBlocking {
        mockkObject(NetworkQualityHelper, Database)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        val formats = mockk<FormatTable>()
        every { Database.formatTable } returns formats
        val rows = listOf("full" to 100L, "partial" to 100L, "unknown" to null, "zero" to 0L).map { (id, length) ->
            FormatWithSong(Format(songId = id, contentLength = length), Song.makePlaceholder(id))
        }
        every { formats.allWithSongs(any(), any()) } returns flowOf(rows)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.cache.isCached("full", 0L, 100L) } returns true
        every { binder.cache.isCached("partial", 0L, 100L) } returns false
        val downloads = MyDownloadHelper
        val original = downloads.downloads
        downloads.downloads = MutableStateFlow(emptyMap())
        try {
            val policy = AutoAccessPolicy(RuntimeEnvironment.getApplication(), Database, downloads, binder)
            assertEquals(listOf(0, 4), policy.eligible(listOf(
                item("context/full", "full"), item("partial"), item("unknown"), item("zero"), item("full")
            )).map { it.index })
        } finally { downloads.downloads = original }
    }

    // JUnit is synchronous; bridge the suspend access-policy query.
    @Test fun `category actions reapply changing exclusions in captured order while detail disliked and quick picks retain policy`() = runBlocking {
        mockkObject(NetworkQualityHelper, DislikedExclusion)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        var excluded = setOf<String>()
        coEvery { DislikedExclusion.excludedSongIds(any(), any()) } answers { excluded }
        val policy = AutoAccessPolicy(RuntimeEnvironment.getApplication(), Database, mockk(), null)
        val rows = listOf(item("one"), item("context/two", "two"), item("one"))
        assertEquals(listOf(0, 1, 2), policy.eligible(rows, AutoSessionConstants.ID_SONGS_ALL).map { it.index })
        excluded = setOf("two")
        assertEquals(listOf(0, 2), policy.eligible(rows, AutoSessionConstants.ID_SONGS_ALL).map { it.index })
        for (parent in listOf("album/x", "artist/x", AutoSessionConstants.ID_SONGS_DISLIKED, AutoSessionConstants.ID_QUICK_PICKS)) {
            assertEquals(listOf(0, 1, 2), policy.eligible(rows, parent).map { it.index })
        }
    }
}
