package app.n_zik.android

import android.widget.Toast
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.download.utils.MyDownloadHelper
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * S1 (Phase 1 download fix, 2026-10-22): the startup stale-download warning parked in
 * [MyDownloadHelper.pendingStaleStartupCount] is consumed on window focus — the re-validation
 * runs before the app window is visible, where the system silently drops toasts. The contract
 * pinned here: shown (and the counter reset) only while the window has focus, exactly once,
 * with the parked count. The focus trigger is the real [MainActivity.onWindowFocusChanged]
 * (robolectric.properties pins a plain Application — same harness as
 * MainActivityProfileShortcutLifecycleTest). The Toaster call is stubbed with concrete
 * arguments (the vararg overload does not match via anyVararg), and the controller is
 * destroyed in teardown so the activity's lifecycleScope collector cannot fire in the next
 * test (statics persist across methods in the same sandbox).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainActivityStaleStartupWarningTest {

    private lateinit var controller: ActivityController<MainActivity>

    private val activity get() = controller.get()

    @Before
    fun setUp() {
        mockkObject(Toaster)
        every { Toaster.w(R.string.download_stale_startup_removed, 2, duration = Toast.LENGTH_LONG) } just Runs
        every { Toaster.w(R.string.download_stale_startup_removed, 1, duration = Toast.LENGTH_LONG) } just Runs
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.destroy()
        MyDownloadHelper.pendingStaleStartupCount.value = 0
        unmockkAll()
    }

    @Test
    fun theParkedStaleCountToastsOnceOnTheFirstWindowFocusAndResets() {
        MyDownloadHelper.pendingStaleStartupCount.value = 2
        Robolectric.buildActivity(MainActivity::class.java).also { controller = it }.create().resume()

        activity.onWindowFocusChanged(true)

        verify(exactly = 1) { Toaster.w(R.string.download_stale_startup_removed, 2, duration = Toast.LENGTH_LONG) }
        assertEquals(0, MyDownloadHelper.pendingStaleStartupCount.value)

        // A second focus cycle with an empty parking lot adds no toast.
        activity.onWindowFocusChanged(false)
        activity.onWindowFocusChanged(true)
        verify(exactly = 1) { Toaster.w(R.string.download_stale_startup_removed, 2, duration = Toast.LENGTH_LONG) }
    }

    @Test
    fun aParkedCountStaysParkedWhileTheWindowHasNoFocus() {
        Robolectric.buildActivity(MainActivity::class.java).also { controller = it }.create().resume()
        activity.onWindowFocusChanged(false)

        MyDownloadHelper.pendingStaleStartupCount.value = 1

        verify(exactly = 0) { Toaster.w(R.string.download_stale_startup_removed, 1, duration = Toast.LENGTH_LONG) }
        assertEquals(1, MyDownloadHelper.pendingStaleStartupCount.value)

        activity.onWindowFocusChanged(true)
        verify(exactly = 1) { Toaster.w(R.string.download_stale_startup_removed, 1, duration = Toast.LENGTH_LONG) }
        assertEquals(0, MyDownloadHelper.pendingStaleStartupCount.value)
    }
}
