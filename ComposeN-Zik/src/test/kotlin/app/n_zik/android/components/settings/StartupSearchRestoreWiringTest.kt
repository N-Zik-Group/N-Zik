package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.MainApplication
import app.n_zik.android.core.backup.LibraryStartupState
import app.n_zik.android.listentogether.ListenTogetherPlayerBridge
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.preferences
import app.n_zik.innertube.Innertube
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess
import java.util.concurrent.TimeUnit

/**
 * Wiring test for the startup restore CALL SITE itself: [MainApplication.onCreate]
 * must call [applyUseLoginForSearch] next to the `useLoginForBrowse` restore, so a
 * persisted toggle survives an app restart:
 *
 * - a persisted OFF (stale in-memory ON) -> `Innertube.useLoginForSearch == false`
 *   after `onCreate()`;
 * - an absent preference (stale in-memory OFF) -> `== true` (the default) after
 *   `onCreate()`.
 *
 * Library recovery now runs before the main application restores its session.
 * Pump the main looper until that startup work completes or reaches WorkManager,
 * which is unavailable in this fixture.
 *
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StartupSearchRestoreWiringTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        unmockkAll()
        Innertube.useLoginForSearch = true
    }

    private fun launchApp() {
        mockkObject(ListenTogetherPlayerBridge)
        every { ListenTogetherPlayerBridge.ensureBound(any()) } just runs
        mockkStatic("app.n_zik.android.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns mockk<SharedPreferences>(relaxed = true)
        ShadowProcess.setProcessName(context.packageName)
        val app = MainApplication()
        ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        app.onCreate()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        val mainLooper = shadowOf(Looper.getMainLooper())
        while (System.nanoTime() < deadline) {
            try {
                mainLooper.idle()
            } catch (error: IllegalStateException) {
                // The session is restored before startup schedules the first WorkManager job.
                if (error.message?.startsWith("WorkManager is not initialized") == true) return
                throw error
            }
            when (val state = app.libraryStartup.state.value) {
                LibraryStartupState.Ready -> return
                is LibraryStartupState.Failed -> throw AssertionError("Library recovery failed", state.cause)
                LibraryStartupState.Waiting -> Thread.sleep(10)
            }
        }
        throw AssertionError("Main application did not restore its session in time")
    }

    @Test
    fun `onCreate restores a persisted OFF toggle`() {
        context.preferences.edit { putBoolean(useLoginForSearchKey, false) }
        Innertube.useLoginForSearch = true // stale in-memory value from before startup

        launchApp()

        assertFalse(Innertube.useLoginForSearch)
    }

    @Test
    fun `onCreate defaults an absent toggle to ON`() {
        context.preferences.edit { remove(useLoginForSearchKey) }
        Innertube.useLoginForSearch = false // stale in-memory value from before startup

        launchApp()

        assertTrue(Innertube.useLoginForSearch)
    }
}
