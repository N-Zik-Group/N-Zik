package app.n_zik.android.playback.services

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.LOCAL_KEY_PREFIX
import app.n_zik.android.playback.exceptions.StreamResolutionOfflineException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

/**
 * S5/S7 (Phase 1 download fix, 2026-10-21): the offline-gate predicates —
 * [isDnsFailure] (the real offline log shows the DNS failure nested three levels deep,
 * `UnexpectedLoaderException -> PlaybackException -> UnknownHostException`, which the
 * previous depth-1/2 check in `onPlayerError` missed entirely: retry storm instead of
 * "no internet") and [shouldWaitForNetwork] (the full gate decision).
 *
 * Runs under [RobolectricTestRunner] (executed through junit-vintage-engine on the JUnit
 * 5 platform) instead of plain JVM: the public [PlaybackException] constructor calls
 * `Clock.DEFAULT.elapsedRealtime()` -> `android.os.SystemClock`, which is an unmocked
 * stub outside Robolectric ("not mocked" RuntimeException).
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineGateDnsTest {

    private fun nestedChain(root: Throwable): PlaybackException {
        val inner = PlaybackException(
            "Source error",
            root,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        return PlaybackException(
            "UnexpectedLoaderException",
            inner,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
    }

    @Test
    fun `a dns failure nested three levels deep is detected`() {
        val error = nestedChain(UnknownHostException("media3.googlevideo.com"))

        assertTrue(isDnsFailure(error))
    }

    @Test
    fun `an unresolved address nested in the chain is detected`() {
        // No-arg constructor: the android.jar stub used by unit-test compilation only
        // exposes the no-arg form of this exception (message constructors stripped).
        val error = nestedChain(UnresolvedAddressException())

        assertTrue(isDnsFailure(error))
    }

    @Test
    fun `a direct dns cause is detected`() {
        val error = PlaybackException(
            "msg",
            UnknownHostException("x"),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )

        assertTrue(isDnsFailure(error))
    }

    @Test
    fun `a chain without any dns failure is not a dns failure`() {
        val error = nestedChain(java.io.IOException("read failed"))

        assertFalse(isDnsFailure(error))
    }

    @Test
    fun `an error without any cause is not a dns failure`() {
        val error = PlaybackException("403", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)

        assertFalse(isDnsFailure(error))
    }

    @Test
    fun `a dns failure beyond the hop limit is out of reach and the walk terminates`() {
        // The walk is bounded by DIAGNOSTIC_CAUSE_HOP_LIMIT (16) — a DNS failure nested
        // deeper than that is unreachable, which also proves the walk terminates on
        // pathologically long (or cyclic) cause chains instead of hanging: 21 levels
        // here, 16 hops max.
        var error: PlaybackException = PlaybackException(
            "root",
            UnknownHostException("x"),
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        repeat(20) { i ->
            error = PlaybackException("wrap$i", error, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }

        assertFalse(isDnsFailure(error))
    }

    @Test
    fun `a plain unknown host exception is detected as a dns failure`() {
        // The predicate is widened to Throwable (the plan signature) — a bare
        // UnknownHostException, not wrapped in a PlaybackException, is still detected.
        assertTrue(isDnsFailure(UnknownHostException("media3.googlevideo.com")))
    }

    @Test
    fun `no network available waits for the network regardless of the error`() {
        val error = PlaybackException("403", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)

        assertTrue(shouldWaitForNetwork(error, networkAvailable = false))
    }

    @Test
    fun `a dns failure nested in the chain waits for the network when online`() {
        val error = nestedChain(UnknownHostException("media3.googlevideo.com"))

        assertTrue(shouldWaitForNetwork(error, networkAvailable = true))
    }

    @Test
    fun `a connection failure code nested in the chain waits for the network when online`() {
        val error = nestedChain(
            PlaybackException(
                "connect",
                null,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            )
        )

        assertTrue(shouldWaitForNetwork(error, networkAvailable = true))
    }

    @Test
    fun `a connection timeout code nested in the chain waits for the network when online`() {
        val error = nestedChain(
            PlaybackException(
                "timeout",
                null,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            )
        )

        assertTrue(shouldWaitForNetwork(error, networkAvailable = true))
    }

    @Test
    fun `an unrelated error while online does not wait for the network`() {
        val error = nestedChain(java.io.IOException("read failed"))

        assertFalse(shouldWaitForNetwork(error, networkAvailable = true))
    }

    // S5 load-level gate (2026-10-22): isOfflineLoadFailure decides whether the
    // load-error retry policy gives up immediately (C.TIME_UNSET) so onPlayerError
    // fires — instead of the offline LOAD_RETRY storm.
    @Test
    fun `a load failure while the network is unavailable is an offline load failure`() {
        assertTrue(isOfflineLoadFailure(IOException("read failed"), networkAvailable = false))
    }

    @Test
    fun `a dns failure in the load chain is an offline load failure even when online`() {
        val error = IOException("stream load", UnknownHostException("music.youtube.com"))

        assertTrue(isOfflineLoadFailure(error, networkAvailable = true))
    }

    @Test
    fun `a plain load failure while the network is available is not an offline load failure`() {
        assertFalse(isOfflineLoadFailure(IOException("read failed"), networkAvailable = true))
    }

    // S5 fast-fail at resolution time (2026-10-22): shouldFastFailOffline decides
    // whether DataSpec.process throws before the first Innertube call — network-only
    // ids only; local files must never be fast-failed (they need no network).
    @Test
    fun `an 11-char network video id fast-fails while offline`() {
        assertTrue(shouldFastFailOffline("3_g2un5M350", networkConnected = false))
    }

    @Test
    fun `an 11-char network video id does not fast-fail while online`() {
        assertFalse(shouldFastFailOffline("3_g2un5M350", networkConnected = true))
    }

    @Test
    fun `a local file id never fast-fails, even offline`() {
        assertFalse(shouldFastFailOffline("${LOCAL_KEY_PREFIX}path/to/file.mp3", networkConnected = false))
    }

    // S5 offline fast-fail marker (2026-10-22): the marker must be recognized even when
    // the connectivity monitor still reports online — the stale-state right after a
    // wifi switch is exactly when a plain-IOException fast-fail slipped through the
    // gate into the recovery path (the double-toast defect).
    @Test
    fun `the offline fast-fail marker is an offline load failure even when online`() {
        assertTrue(isOfflineLoadFailure(StreamResolutionOfflineException("3_g2un5M350"), networkAvailable = true))
    }

    @Test
    fun `the offline fast-fail marker is an offline load failure when offline`() {
        assertTrue(isOfflineLoadFailure(StreamResolutionOfflineException("3_g2un5M350"), networkAvailable = false))
    }

    @Test
    fun `an error carrying the offline fast-fail marker waits for the network when online`() {
        val error = nestedChain(StreamResolutionOfflineException("3_g2un5M350"))

        assertTrue(shouldWaitForNetwork(error, networkAvailable = true))
    }

    @Test
    fun `the offline fast-fail marker is detected in the cause chain`() {
        assertTrue(isStreamResolutionOffline(nestedChain(StreamResolutionOfflineException("3_g2un5M350"))))
        assertFalse(isStreamResolutionOffline(nestedChain(java.io.IOException("read failed"))))
    }

    // A3 (offline audit, 2026-10-22): isRawConnectionFailure detects the stale-monitor hole —
    // a raw network I/O failure with no marker/DNS signature.
    @Test
    fun `a raw socket exception in the chain is a raw connection failure`() {
        assertTrue(isRawConnectionFailure(nestedChain(java.net.SocketException("connection reset"))))
    }

    @Test
    fun `a socket timeout in the chain is a raw connection failure`() {
        assertTrue(isRawConnectionFailure(nestedChain(java.net.SocketTimeoutException("read timed out"))))
    }

    @Test
    fun `a plain io exception in the chain is a raw connection failure`() {
        assertTrue(isRawConnectionFailure(nestedChain(IOException("broken pipe"))))
    }

    @Test
    fun `the offline fast-fail marker is not a raw connection failure`() {
        assertFalse(isRawConnectionFailure(nestedChain(StreamResolutionOfflineException("3_g2un5M350"))))
    }

    @Test
    fun `a chain without any io exception is not a raw connection failure`() {
        assertFalse(isRawConnectionFailure(nestedChain(IllegalStateException("boom"))))
    }

    @Test
    fun `an io exception beyond the diagnostic hop limit is not a raw connection failure`() {
        var deep: Throwable = IOException("deep")
        repeat(16) { deep = IllegalStateException("wrap", deep) }

        assertFalse(isRawConnectionFailure(deep))
    }
}
