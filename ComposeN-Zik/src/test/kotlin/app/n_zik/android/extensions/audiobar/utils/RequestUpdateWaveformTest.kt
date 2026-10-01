package app.n_zik.android.extensions.audiobar.utils

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Regression coverage for the manual "Update waveform" menu flow being cancelled before it could
 * deliver a result: the menu's onClick used to launch WaveformExtractor.updateWaveform on the
 * button's own `rememberCoroutineScope`, which is torn down a few hundred ms after
 * menuState.hide() (exit animation). That raced fine against the OLD single-attempt, near-instant
 * call, but once retries/native extraction made the operation take longer, the coroutine was
 * cancelled before its result -- and its toast -- could ever be delivered (confirmed on-device:
 * a successful extraction logged "-> SUCCESS" but no success toast was shown).
 * requestUpdateWaveform fixes this by running on an object-owned SupervisorJob scope instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RequestUpdateWaveformTest {

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `delivers the result on the owned scope even without a caller coroutine kept alive`(@TempDir tempDir: File) {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir
        // The waveform dir is profile-aware (spec-profile-data-separation): the active profile
        // and its sharing flags come from the plain profile_preferences store.
        val profilePrefs = mockk<SharedPreferences>()
        every { profilePrefs.getString(any(), any()) } returns null
        every { profilePrefs.getBoolean(any(), any()) } returns false
        every { context.getSharedPreferences(any(), any()) } returns profilePrefs

        val latch = CountDownLatch(1)
        var delivered: WaveformResult? = null

        // No caches -> NoCache. The point isn't the classification, it's that the callback
        // fires at all: requestUpdateWaveform's own scope must survive independently of
        // whatever coroutine/composable triggered it (nothing here keeps this test's own
        // "caller" coroutine alive -- there isn't one; the call is a plain, non-suspending
        // fire-and-forget from this test method).
        WaveformExtractor.requestUpdateWaveform(context, "media-request-test", emptyList()) { result ->
            delivered = result
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "onResult callback was never delivered")
        assertEquals(WaveformResult.NoCache, delivered)
    }

    @Test
    fun `result is still delivered after the caller's own scope is cancelled`(@TempDir tempDir: File) {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir
        // The waveform dir is profile-aware (spec-profile-data-separation): the active profile
        // and its sharing flags come from the plain profile_preferences store.
        val profilePrefs = mockk<SharedPreferences>()
        every { profilePrefs.getString(any(), any()) } returns null
        every { profilePrefs.getBoolean(any(), any()) } returns false
        every { context.getSharedPreferences(any(), any()) } returns profilePrefs

        val latch = CountDownLatch(1)
        var delivered: WaveformResult? = null

        // Simulates the exact shape of the bug this fix closes: a caller (the menu's
        // rememberCoroutineScope) that gets cancelled shortly after triggering the request.
        // requestUpdateWaveform takes no scope from its caller -- it only schedules work on its
        // own object-owned scope -- so cancelling this wrapper must have no effect on delivery.
        // If a future change made requestUpdateWaveform launch on a caller-supplied scope again
        // (reintroducing the original bug), routing that scope in here would make this test fail.
        // UNDISPATCHED: the launch body (which never suspends, since requestUpdateWaveform is a
        // plain non-suspend fun) runs synchronously here, before `.launch()` returns -- so the
        // call is guaranteed to have already registered its work with WaveformExtractor's own
        // scope by the time callerScope.cancel() runs immediately after, removing any timing race.
        val callerScope = CoroutineScope(Job())
        callerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            WaveformExtractor.requestUpdateWaveform(context, "media-cancel-test", emptyList()) { result ->
                delivered = result
                latch.countDown()
            }
        }
        callerScope.cancel()

        assertTrue(latch.await(5, TimeUnit.SECONDS), "onResult callback was never delivered after caller scope cancellation")
        assertEquals(WaveformResult.NoCache, delivered)
    }
}
