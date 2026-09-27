package app.n_zik.android.download.utils

import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Startup download cleanup (user decision "wipe des téléchargements non-terminaux au
 * démarrage"): on app start, only the stuck (non-terminal) records — queued / in
 * progress / canceled — are removed, one by one: their stream URLs are expired, so they
 * can never resume. The completed (offline) library must NEVER be wiped: no
 * `removeAllDownloads()`, and the terminal records (completed / failed) are outside
 * [DownloadManager.getCurrentDownloads] by the media3 contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyDownloadHelperStartupWipeTest {

    /**
     * A real [Download] (the manager is the only mock): `DownloadRequest.id` is a public
     * FIELD in media3, not a getter — a MockK stub of it is never intercepted, so the
     * downloads must be real objects.
     */
    private fun download(id: String): Download =
        Download(
            DownloadRequest.Builder(id, Uri.parse("https://example.com/$id")).build(),
            Download.STATE_QUEUED,
            0L,
            0L,
            0L,
            0,
            Download.FAILURE_REASON_NONE
        )

    @Test
    fun stuckRecordsAreRemovedOneByOne() {
        val manager = mockk<DownloadManager>(relaxed = true)
        every { manager.currentDownloads } returns listOf(download("a"), download("b"), download("c"))

        MyDownloadHelper.wipeStuckDownloadsOnStart(manager)

        verify(exactly = 1) { manager.removeDownload("a") }
        verify(exactly = 1) { manager.removeDownload("b") }
        verify(exactly = 1) { manager.removeDownload("c") }
        verify(exactly = 0) { manager.removeAllDownloads() }
    }

    @Test
    fun aQueueWithOnlyTerminalRecordsIsLeftUntouched() {
        // currentDownloads empty = only terminal (completed / failed) records persisted:
        // the offline library must survive the restart
        val manager = mockk<DownloadManager>(relaxed = true)
        every { manager.currentDownloads } returns emptyList()

        MyDownloadHelper.wipeStuckDownloadsOnStart(manager)

        verify(exactly = 0) { manager.removeDownload(any()) }
        verify(exactly = 0) { manager.removeAllDownloads() }
    }
}
