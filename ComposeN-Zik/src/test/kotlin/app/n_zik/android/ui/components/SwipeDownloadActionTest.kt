package app.n_zik.android.ui.components

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.DownloadedStateMedia
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SwipeDownloadActionTest {
    private val context = mockk<Context>()
    private val media = MediaItem.Builder().setMediaId("abc123def45").build()
    private val start = mockk<() -> Unit>(relaxed = true)

    @Before
    fun mockDownloads() {
        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.removeDownload(context, any()) } returns Unit
    }

    @After
    fun restoreDownloads() {
        unmockkObject(MyDownloadHelper)
    }

    @Test
    fun `active requests and completed downloads are removed through the download helper`() {
        for (state in listOf(Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_RESTARTING)) {
            toggleSwipeDownload(context, media, state, DownloadedStateMedia.CACHED, start)
        }
        toggleSwipeDownload(context, media, Download.STATE_COMPLETED, DownloadedStateMedia.DOWNLOADED, start)
        toggleSwipeDownload(context, media, Download.STATE_COMPLETED, DownloadedStateMedia.CACHED_AND_DOWNLOADED, start)

        verify(exactly = 5) { MyDownloadHelper.removeDownload(context, media) }
        verify(exactly = 0) { start() }
    }

    @Test
    fun `a remote song without a request starts and a local file is ignored`() {
        val local = MediaItem.Builder().setMediaId("local:42").build()
        toggleSwipeDownload(context, media, Download.STATE_STOPPED, DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED, start)
        toggleSwipeDownload(context, local, Download.STATE_STOPPED, DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED, start)
        toggleSwipeDownload(context, local, Download.STATE_COMPLETED, DownloadedStateMedia.DOWNLOADED, start)

        verify(exactly = 1) { start() }
        verify(exactly = 0) { MyDownloadHelper.removeDownload(context, any()) }
    }
}
