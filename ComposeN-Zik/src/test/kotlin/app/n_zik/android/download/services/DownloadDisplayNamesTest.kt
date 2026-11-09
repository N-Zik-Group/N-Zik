package app.n_zik.android.download.services

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DownloadDisplayNamesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanup() {
        MyDownloadHelper.resetBatch()
        unmockkAll()
    }

    private fun download(label: String, state: Int = Download.STATE_QUEUED) = Download(
        DownloadRequest.Builder("modified:track-id", Uri.parse("https://example.com/track"))
            .setData(label.encodeToByteArray()).build(),
        state, 0L, 0L, 100L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE,
    )

    @Test
    fun `progress and paused notifications hide old request markers without mutating requests`() {
        val raw = "modified:Artist: collective - modified:e:Title: live"
        val item = download(raw)
        val builder = DownloadProgressNotification(context)

        for (paused in listOf(false, true)) {
            val notification = builder.build(listOf(item), paused, 1, 0)
            assertEquals("Artist: collective - Title: live", notification.extras.getString(Notification.EXTRA_TEXT))
        }
        assertEquals(raw, item.request.data.decodeToString())
        assertEquals("modified:track-id", item.request.id)
    }

    @Test
    fun `terminal notification cleans old labels and keeps ordinary title punctuation`() {
        mockkObject(WaveformExtractor)
        justRun { WaveformExtractor.deleteWaveform(any(), any()) }
        val raw = "modified:Artist - modified:Title - modified: phrase"
        val completed = download(raw, Download.STATE_COMPLETED)
        val manager = mockk<DownloadManager> {
            every { currentDownloads } returns listOf(completed)
            every { downloadsPaused } returns true
        }
        val listener = MyDownloadService.TerminalStateNotificationHelper(context, 8990)

        listener.onDownloadChanged(manager, completed, null)
        listener.onIdle(manager)

        val notifications = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(notifications).getNotification(8990)
        assertEquals("Artist - Title - modified: phrase", notification.extras.getString(Notification.EXTRA_SUB_TEXT))
        assertEquals(raw, completed.request.data.decodeToString())
    }
}
