package app.n_zik.android.core.network

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose test for [OfflineMode.rememberEnabled] (spec-full-offline-mode): the
 * listener-backed composable read used by the call sites must flip live when
 * the profile-scoped `fullOfflineModeKey` preference changes at runtime.
 *
 * JUnit 4 + [RobolectricTestRunner] on the JUnit 5 platform (vintage engine),
 * same harness as OfflineModeBadgeTest — the preference store is reached from
 * the test body through the application context (the same profile-scoped file
 * as the content context).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = OfflineModeLiveFlipTestApplication::class)
class OfflineModeLiveFlipTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val captured = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        captured.clear()
        ApplicationProvider.getApplicationContext<Context>()
            .preferences.edit()
            .remove(fullOfflineModeKey)
            .apply()
    }

    /**
     * Settle after a test-body preference write: the recomposition is processed
     * on the idle pass, not during the clock advance — advance, then idle.
     */
    private fun settle() {
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
    }

    @Test
    fun `rememberEnabled flips live when the mode preference changes`() {
        composeRule.setContent {
            val enabled by OfflineMode.rememberEnabled()
            captured.add(enabled)
        }
        settle()
        assertFalse(captured.last())

        ApplicationProvider.getApplicationContext<Context>()
            .preferences.edit()
            .putBoolean(fullOfflineModeKey, true)
            .apply()
        settle()
        assertTrue(captured.last())

        ApplicationProvider.getApplicationContext<Context>()
            .preferences.edit()
            .putBoolean(fullOfflineModeKey, false)
            .apply()
        settle()
        assertFalse(captured.last())
    }
}

/** Test-only application: the app's MainApplication.onCreate() is not available on the Robolectric JVM. */
class OfflineModeLiveFlipTestApplication : Application()
