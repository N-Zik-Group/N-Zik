package app.n_zik.android.download.services

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.os.Looper
import androidx.core.app.AppLocalesStorageHelper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadCursor
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.R
import app.n_zik.android.awaitLibraryReady
import app.n_zik.android.core.backup.isLibraryReady
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.core.notifications.channelId
import app.n_zik.android.download.utils.MyDownloadHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.justRun
import io.mockk.verify
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.text.NumberFormat
import java.util.Locale

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DownloadNotificationLifecycleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val download = Download(
        DownloadRequest.Builder("track", Uri.parse("https://example.com/track"))
            .setData("Track title".encodeToByteArray()).build(),
        Download.STATE_QUEUED, 0L, 0L, 100L,
        Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE
    )

    @After
    fun tearDown() {
        MyDownloadHelper.resetBatch()
        unmockkAll()
    }

    @Test
    fun `listener retains only application and posts resume after service destruction`() {
        val controller = Robolectric.buildService(TestService::class.java).create()
        val listener = MyDownloadService.TerminalStateNotificationHelper(controller.get(), 8990)
        val contextField = listener.javaClass.getDeclaredField("context").apply { isAccessible = true }
        assertSame(context.applicationContext, contextField.get(listener))
        controller.destroy()
        MyDownloadHelper.startBatchDownload(4)
        val manager = mockk<DownloadManager> {
            every { currentDownloads } returns listOf(download)
            every { downloadsPaused } returns true
        }

        listener.onIdle(manager)

        val notification = shadowOf(notifications).getNotification(8991)
        assertNotNull(notification)
        assertEquals(4, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertFalse(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(DownloadService.ACTION_RESUME_DOWNLOADS,
            shadowOf(notification.actions[0].actionIntent).savedIntent.action)
        assertNull(shadowOf(notifications).getNotification(8990))
    }

    @Test
    fun `active progress keeps action identities and removes paused notification`() {
        val builder = DownloadProgressNotification(context)
        notifications.notify(8991, builder.build(listOf(download), true, 4, 2))
        val notification = builder.build(listOf(download), false, 4, 2)

        assertNull(shadowOf(notifications).getNotification(8991))
        assertEquals(channelId(MyDownloadHelper.DOWNLOAD_NOTIFICATION_CHANNEL_ID, getActiveProfile(context)), notification.channelId)
        assertEquals("Track title", notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(2, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(listOf(DownloadService.ACTION_PAUSE_DOWNLOADS,
            MyDownloadService.ACTION_CANCEL_ONGOING, DownloadService.ACTION_REMOVE_ALL_DOWNLOADS),
            notification.actions.map { shadowOf(it.actionIntent).savedIntent.action })
        assertEquals(listOf(3, 1, 2), notification.actions.map { shadowOf(it.actionIntent).requestCode })
        assertTrue(notification.actions.all {
            shadowOf(it.actionIntent).savedIntent.component?.className == MyDownloadService::class.java.name
        })
    }

    @Test
    @Config(sdk = [32], qualifiers = "en")
    fun `foreground download text and percent follow the saved app language`() {
        val previous = AppLocalesStorageHelper.readLocales(context)
        try {
            AppLocalesStorageHelper.persistLocales(context, "fr")
            val notification = DownloadProgressNotification(context).build(listOf(download), false, 0, 0)
            val percent = NumberFormat.getPercentInstance(Locale.FRENCH).format(0)
            assertEquals("1 en cours ($percent)", notification.extras.getString(Notification.EXTRA_SUB_TEXT))
            assertEquals("Annuler", notification.actions[1].title.toString())
            assertEquals(1, shadowOf(notification.actions[1].actionIntent).requestCode)
        } finally {
            AppLocalesStorageHelper.persistLocales(context, previous)
        }
    }

    @Test
    fun `empty idle clears progress pause flag and batch`() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { app.n_zik.android.appContext() } returns context
        MyDownloadHelper.startBatchDownload(3)
        context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("downloads_paused_state", true).commit()
        val builder = DownloadProgressNotification(context)
        notifications.notify(8989, builder.build(emptyList(), true, 3, 0))
        notifications.notify(8991, builder.build(emptyList(), true, 3, 0))
        val manager = mockk<DownloadManager> {
            every { currentDownloads } returns emptyList()
            every { downloadsPaused } returns true
        }

        MyDownloadService.TerminalStateNotificationHelper(context, 8990).onIdle(manager)

        assertNull(shadowOf(notifications).getNotification(8989))
        assertNull(shadowOf(notifications).getNotification(8991))
        assertEquals(0, MyDownloadHelper.batchTotal)
        assertFalse(context.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)
            .getBoolean("downloads_paused_state", true))
    }

    @Test
    fun `completion still posts once at terminal id and clears its counters`() {
        mockkObject(WaveformExtractor)
        justRun { WaveformExtractor.deleteWaveform(any(), any()) }
        val manager = mockk<DownloadManager> {
            every { currentDownloads } returns listOf(download)
            every { downloadsPaused } returns true
        }
        val completed = Download(download.request, Download.STATE_COMPLETED, 0L, 0L,
            100L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
        val listener = MyDownloadService.TerminalStateNotificationHelper(context, 8990)

        listener.onDownloadChanged(manager, completed, null)
        listener.onIdle(manager)

        val terminal = shadowOf(notifications).getNotification(8990)
        assertNotNull(terminal)
        assertEquals(context.getString(R.string.downloads_completed, 1),
            terminal.extras.getString(Notification.EXTRA_TEXT))
        verify(exactly = 1) { WaveformExtractor.deleteWaveform(context.applicationContext, "track") }
        notifications.cancel(8990)
        listener.onIdle(manager)
        assertNull(shadowOf(notifications).getNotification(8990))
        assertNotNull(shadowOf(notifications).getNotification(8991))
    }

    @Test
    fun `cancel ongoing closes cursor after iteration and after removal failure`() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { app.n_zik.android.appContext() } returns context
        mockkObject(MyDownloadHelper)
        mockkStatic(DownloadService::class)
        val cursor = mockk<DownloadCursor>(relaxed = true)
        val manager = mockk<DownloadManager> {
            every { downloadIndex.getDownloads() } returns cursor
        }
        every { MyDownloadHelper.getDownloadManager(any()) } returns manager
        val service = Robolectric.buildService(MyDownloadService::class.java).get()
        every { cursor.moveToNext() } returnsMany listOf(true, false)
        every { cursor.download } returns download
        justRun { DownloadService.sendRemoveDownload(service, MyDownloadService::class.java, "track", false) }

        service.cancelOngoingDownloads()

        verify(exactly = 1) { cursor.close() }
        verify(exactly = 1) {
            DownloadService.sendRemoveDownload(service, MyDownloadService::class.java, "track", false)
        }
        every { cursor.moveToNext() } returns true
        every {
            DownloadService.sendRemoveDownload(service, MyDownloadService::class.java, "track", false)
        } throws IllegalStateException("Removal failed")

        assertThrows(IllegalStateException::class.java) { service.cancelOngoingDownloads() }

        verify(exactly = 2) { cursor.close() }
    }

    @Test
    fun `pending download command opens the manager and replays once recovery finishes`() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        mockkStatic("app.n_zik.android.MainApplicationKt")
        mockkStatic("app.n_zik.android.core.backup.LibraryStartupWorkerGateKt")
        every { app.n_zik.android.appContext() } returns context
        val readiness = CompletableDeferred<Unit>()
        every { any<Context>().isLibraryReady() } answers { readiness.isCompleted }
        coEvery { any<Context>().awaitLibraryReady() } coAnswers { readiness.await() }

        mockkObject(MyDownloadHelper)
        val manager = mockk<DownloadManager>(relaxed = true)
        val cursor = mockk<DownloadCursor>(relaxed = true)
        every { manager.downloadIndex.getDownloads() } returns cursor
        every { MyDownloadHelper.getDownloadManager(any()) } returns manager

        val controller = Robolectric.buildService(MyDownloadService::class.java).create()
        val service = controller.get()
        val mainLooper = shadowOf(Looper.getMainLooper())
        val command = Intent(MyDownloadService.ACTION_CANCEL_ONGOING)

        try {
            mainLooper.idle()
            assertEquals(Service.START_REDELIVER_INTENT, service.onStartCommand(command, 0, 1))
            verify(exactly = 0) { MyDownloadHelper.getDownloadManager(any()) }
            verify(exactly = 0) { cursor.moveToNext() }

            readiness.complete(Unit)
            mainLooper.idle()
            verify(exactly = 1) { manager.resumeDownloads() }
            verify(exactly = 1) { cursor.moveToNext() }
            verify(exactly = 1) { cursor.close() }
        } finally {
            controller.destroy()
        }
    }

    class TestService : Service() {
        override fun onBind(intent: Intent?): IBinder? = null
    }
}
