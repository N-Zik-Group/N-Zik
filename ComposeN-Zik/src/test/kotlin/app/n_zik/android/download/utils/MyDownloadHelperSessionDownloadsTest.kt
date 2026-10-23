package app.n_zik.android.download.utils

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Session-scoped Downloads row: [MyDownloadHelper.sessionDownloads] is the
 * maintenance sheet's view of the download library — it resets to empty on
 * every process start because only the download-manager events populate it,
 * never the init seed of [MyDownloadHelper.downloads] (which keeps the full
 * persistent library for the badges and the download manager UI).
 *
 * JUnit 4 + [RobolectricTestRunner] (executed through junit-vintage-engine on the JUnit
 * 5 platform), mirroring [MyDownloadHelperStaleRevalidationTest].
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MyDownloadHelperSessionDownloadsTest {

    @After
    fun resetHelperState() {
        MyDownloadHelper.downloads.value = emptyMap()
        MyDownloadHelper.sessionDownloads.value = emptyMap()
    }

    private fun download(id: String, state: Int, data: String = ""): Download {
        val builder = DownloadRequest.Builder(id, Uri.parse("https://music.example.com/stream/$id"))
        if (data.isNotEmpty()) builder.setData(data.encodeToByteArray())
        return Download(builder.build(), state, 0L, 0L, 0L, Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE)
    }

    @Test
    fun `the init seed never populates the session row`() {
        // getDownloads() at init seeds the shared map directly (no manager event) —
        // the session row must stay empty from startup
        MyDownloadHelper.downloads.value = mapOf(
            "MothToAFlame" to download("MothToAFlame", Download.STATE_COMPLETED, "Artist X - Song A"),
        )

        assertTrue(MyDownloadHelper.sessionDownloads.value.isEmpty())
        assertEquals(1, MyDownloadHelper.downloads.value.size)
    }

    @Test
    fun `a session download event lands in the session row`() {
        MyDownloadHelper.syncDownloads(download("MothToAFlame", Download.STATE_COMPLETED, "Artist X - Song A"))

        val session = MyDownloadHelper.sessionDownloads.value
        assertEquals(1, session.size)
        assertEquals(Download.STATE_COMPLETED, session["MothToAFlame"]?.state)
        // The event updates the shared map too (existing behavior)
        assertEquals(1, MyDownloadHelper.downloads.value.size)
    }

    @Test
    fun `a removing event clears the session row entry`() {
        MyDownloadHelper.syncDownloads(download("MothToAFlame", Download.STATE_COMPLETED, "Artist X - Song A"))
        MyDownloadHelper.syncDownloads(download("MothToAFlame", Download.STATE_REMOVING))

        assertTrue(MyDownloadHelper.sessionDownloads.value.isEmpty())
        assertFalse(MyDownloadHelper.downloads.value.containsKey("MothToAFlame"))
    }
}
