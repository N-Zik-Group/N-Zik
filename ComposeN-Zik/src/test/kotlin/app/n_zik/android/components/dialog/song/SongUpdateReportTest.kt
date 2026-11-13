package app.n_zik.android.components.dialog.song

import java.io.IOException
import java.net.SocketTimeoutException
import android.content.Context
import android.widget.Toast
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.appContext

class SongUpdateReportTest {
    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `wrapped network failure is recognized without parsing exception text`() {
        assertEquals(SongUpdateIssue.NETWORK, classifySongUpdateFetchFailure(IllegalStateException(SocketTimeoutException())))
        assertEquals(SongUpdateIssue.METADATA_UNAVAILABLE, classifySongUpdateFetchFailure(IllegalStateException("network down")))
    }

    @Test
    fun `wrapped cancellation is never presented as a network error`() {
        val cancellation = CancellationException("cancelled")
        try {
            classifySongUpdateFetchFailure(IOException(cancellation))
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun `mixed batch displays one grouped summary with each affected song counted once`() {
        val context = mockk<Context>()
        val reports = listOf(
            SongUpdateReport(true, true, emptySet()),
            SongUpdateReport(false, true, setOf(SongUpdateIssue.NETWORK)),
            SongUpdateReport(false, false, setOf(SongUpdateIssue.NETWORK)),
        )
        every { context.getString(R.string.song_update_batch_result, 1, 1, 1) } returns "Updated: 1; partial: 1; failed: 1"
        every { context.getString(R.string.song_update_error_network) } returns "Connection failed"
        every { context.getString(R.string.song_update_batch_reason_count, "Connection failed", 2) } returns "Connection failed (2)"
        val expected = "Updated: 1; partial: 1; failed: 1\nConnection failed (2)"
        assertEquals(expected, songUpdateBatchMessage(context, reports))
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(Toaster)
        every { Toaster.w(any<String>(), any<Int>()) } just runs
        showSongUpdateBatchReport(reports)
        verify(exactly = 1) { Toaster.w(expected, Toast.LENGTH_LONG) }
        verify(exactly = 0) { Toaster.done() }
        verify(exactly = 0) { Toaster.e(any<String>(), any<Int>()) }
        verify(exactly = 0) { Toaster.s(any<Int>(), any<Long>()) }
    }
}
