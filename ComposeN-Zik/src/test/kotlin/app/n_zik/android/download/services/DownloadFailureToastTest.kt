package app.n_zik.android.download.services

import app.n_zik.android.R
import app.n_zik.android.playback.exceptions.StreamResolutionOfflineException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** S5 (offline gate, 2026-10-22): download failure toast mapping — a download that failed because the network was down (the offline fast-fail marker [StreamResolutionOfflineException]) must map to "No Internet", not the misleading "An unknown playback error has occurred" (the second "exo" toast of the offline double-toast defect, device-verified 2026-10-22). */
class DownloadFailureToastTest {

    @Test
    fun `the offline fast-fail marker maps to no internet`() {
        val cause = StreamResolutionOfflineException("RmYCOm4ehKs")

        assertEquals(R.string.error_no_internet, downloadFailureToastRes(httpCode = null, specificCause = cause))
    }

    @Test
    fun `the offline fast-fail marker nested in the chain is found`() {
        val nested = IOException("load failed", StreamResolutionOfflineException("RmYCOm4ehKs"))

        val specificCause = findSpecificDownloadCause(nested)

        assertTrue(specificCause is StreamResolutionOfflineException)
    }

    @Test
    fun `dns failures still map to the network error toast`() {
        val cause = UnknownHostException("youtube.com")

        assertEquals(R.string.error_a_network_error_has_occurred, downloadFailureToastRes(httpCode = null, specificCause = cause))
    }

    @Test
    fun `403 responses still map to the server restrictions toast`() {
        assertEquals(
            R.string.error_this_song_cannot_be_played_due_to_server_restrictions,
            downloadFailureToastRes(httpCode = 403, specificCause = null)
        )
    }

    // P11 (review, 2026-10-23): the fallback is now download-domain — a failed download
    // must never toast the playback-domain "unknown playback error".
    @Test
    fun `unrecognized causes fall back to the download-domain toast`() {
        val cause = IOException("boom")

        assertEquals(
            R.string.download_failed,
            downloadFailureToastRes(httpCode = null, specificCause = cause)
        )
    }

    @Test
    fun `a null exception falls back to the download-domain toast`() {
        assertEquals(
            R.string.download_failed,
            downloadFailureToastRes(httpCode = null, specificCause = null)
        )
    }

    // A5b (offline audit, 2026-10-22): raw network failures map to the network-error toast
    // instead of the misleading "unknown playback error".
    @Test
    fun `a socket timeout failure maps to the network error toast`() {
        val cause = SocketTimeoutException("connect timed out")

        assertEquals(R.string.error_a_network_error_has_occurred, downloadFailureToastRes(httpCode = null, specificCause = cause))
    }

    @Test
    fun `a socket exception failure maps to the network error toast`() {
        val cause = SocketException("connection reset")

        assertEquals(R.string.error_a_network_error_has_occurred, downloadFailureToastRes(httpCode = null, specificCause = cause))
    }

    @Test
    fun `a raw socket timeout nested in the chain is found`() {
        val nested = IOException("download failed", SocketTimeoutException("timed out"))

        val specificCause = findSpecificDownloadCause(nested)

        assertTrue(specificCause is SocketTimeoutException)
    }

    @Test
    fun `a disk failure falls back to the download-domain toast`() {
        val cause = FileNotFoundException("/data/download.mp3")

        assertEquals(
            R.string.download_failed,
            downloadFailureToastRes(httpCode = null, specificCause = findSpecificDownloadCause(cause))
        )
    }
}
