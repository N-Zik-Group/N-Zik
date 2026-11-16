package app.n_zik.android.download.utils

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadService
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.artistTextOrDb
import app.n_zik.android.utils.downloadSyncedLyrics
import app.n_zik.android.utils.isNetworkConnected
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyDownloadHelperCommandOrderTest {
    @Test
    fun `remove waits for its track to be scheduled and proceeds while lyrics are loading`() {
        val commands = Channel<Pair<String, String>>(Channel.UNLIMITED)
        val metadataStarted = CompletableDeferred<Unit>()
        val releaseMetadata = CompletableDeferred<Unit>()
        val lyricsStarted = CompletableDeferred<Unit>()
        val releaseLyrics = CompletableDeferred<Unit>()
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun startService(service: Intent): ComponentName? {
                commands.trySend(checkNotNull(service.action) to checkNotNull(service.getStringExtra(DownloadService.KEY_CONTENT_ID)))
                return service.component
            }
        }
        val pending = MediaItem.Builder().setMediaId("pending-track")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Track").build())
            .build()
        val other = MediaItem.Builder().setMediaId("other-track").build()

        mockkObject(Database)
        mockkStatic("app.n_zik.android.utils.UtilsKt", "app.n_zik.android.utils.MediaItemUtilsKt")
        val originalSchedule = MyDownloadHelper.scheduleDownloadRequest
        try {
            every { Database.asyncTransaction(any(), any()) } just Runs
            every { isNetworkConnected(context) } returns true
            coEvery { pending.artistTextOrDb() } coAnswers {
                metadataStarted.complete(Unit)
                releaseMetadata.await()
                "Artist"
            }
            coEvery { downloadSyncedLyrics(any()) } coAnswers {
                lyricsStarted.complete(Unit)
                releaseLyrics.await()
            }
            MyDownloadHelper.scheduleDownloadRequest = { _, _, request ->
                commands.trySend(DownloadService.ACTION_ADD_DOWNLOAD to request.id)
                Result.success(Unit)
            }

            // JUnit 4 requires a synchronous test entry point for the suspended assertions.
            runBlocking {
                withTimeout(5_000) {
                    MyDownloadHelper.addDownload(context, pending)
                    metadataStarted.await()
                    MyDownloadHelper.removeDownload(context, pending)
                    MyDownloadHelper.removeDownload(context, other)

                    assertEquals(DownloadService.ACTION_REMOVE_DOWNLOAD to other.mediaId, commands.receive())
                    assertNull(withTimeoutOrNull(100) { commands.receive() })
                    releaseMetadata.complete(Unit)

                    assertEquals(DownloadService.ACTION_ADD_DOWNLOAD to pending.mediaId, commands.receive())
                    lyricsStarted.await()
                    assertEquals(DownloadService.ACTION_REMOVE_DOWNLOAD to pending.mediaId, commands.receive())
                }
            }
        } finally {
            releaseMetadata.complete(Unit)
            releaseLyrics.complete(Unit)
            MyDownloadHelper.scheduleDownloadRequest = originalSchedule
            unmockkStatic("app.n_zik.android.utils.UtilsKt", "app.n_zik.android.utils.MediaItemUtilsKt")
            unmockkObject(Database)
        }
    }
}
