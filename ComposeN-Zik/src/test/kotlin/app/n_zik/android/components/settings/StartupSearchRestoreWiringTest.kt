package app.n_zik.android.components.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.MainApplication
import it.fast4x.innertube.Innertube
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

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
 * Template: [app.n_zik.android.MaintenanceBootChainTest.launchApp] — it executes the
 * real `MainApplication.onCreate()` under Robolectric (stubs the keystore-backed
 * `encryptedPreferences`, pins the process name, calls the protected
 * `attachBaseContext` reflectively, and swallows the post-restore WorkManager failure
 * of the test harness). The restore call is synchronous and sits BEFORE that failure
 * point, so no polling is needed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StartupSearchRestoreWiringTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        unmockkAll()
        Innertube.useLoginForSearch = true
    }

    private fun launchApp() {
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns mockk<SharedPreferences>(relaxed = true)
        ShadowProcess.setProcessName(context.packageName)
        val app = MainApplication()
        ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(app, context)
        try {
            app.onCreate()
        } catch (e: Exception) {
            // Expected under Robolectric: post-restore init fails (WorkManager not
            // initialized). The useLoginForSearch restore is synchronous and lands
            // before that point, so the assertions below still verify it directly.
        }
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
        Innertube.useLoginForSearch = false // stale in-memory value from before startup

        launchApp()

        assertTrue(Innertube.useLoginForSearch)
    }
}
