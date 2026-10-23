package app.n_zik.android.download.utils

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.downloadSyncedLyrics
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A2 (offline audit, 2026-10-22) / P14 (review, 2026-10-23): a failed download SCHEDULING
 * (the `Context.download` call in [MyDownloadHelper.addDownloadInternal]) must toast the
 * download-domain `download_failed` string — delete the A2 failure handling in
 * [MyDownloadHelper.addDownloadInternal] and this test fails.
 *
 * The legacy `Context.download` wrapper is `inline reified` (not stubbable), so the test
 * drives the `scheduleDownloadRequest` seam with a failing lambda; `downloadSyncedLyrics`
 * is stubbed (it reads the Room `Database`, uninitialized here) and the media item carries
 * a null artwork, which makes the `preloadImage("null")` call a no-op.
 *
 * JUnit 4 + [RobolectricTestRunner] (executed through junit-vintage-engine on the JUnit 5
 * platform), mirroring [MyDownloadHelperInitS1OffMainTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyDownloadHelperScheduleFailureToastTest {

    @Before
    fun initDependencies() {
        // MainApplication is final, so Dependencies.application is a mock wired to this
        // app's context (same pattern as MyDownloadHelperInitS1OffMainTest).
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns ApplicationProvider.getApplicationContext<Context>()
        Dependencies.init(mainApplication)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a failed download scheduling shows the download-domain toast`() {
        mockkObject(Toaster)
        mockkStatic("app.it.fast4x.rimusic.utils.UtilsKt")
        coEvery { downloadSyncedLyrics(any()) } just Runs

        val original = MyDownloadHelper.scheduleDownloadRequest
        try {
            MyDownloadHelper.scheduleDownloadRequest = { _, _ ->
                Result.failure(IllegalStateException("schedule failed"))
            }
            // runBlocking: JUnit 4 has no coroutine runner — drives the suspend core synchronously.
            runBlocking {
                MyDownloadHelper.addDownloadInternal(
                    ApplicationProvider.getApplicationContext(),
                    MediaItem.Builder()
                        .setMediaId("abc123def45")
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle("Test Title")
                                .setArtist("Test Artist")
                                .build()
                        )
                        .build()
                )
            }
            verify(exactly = 1) { Toaster.e(R.string.download_failed) }
        } finally {
            MyDownloadHelper.scheduleDownloadRequest = original
        }
    }
}
