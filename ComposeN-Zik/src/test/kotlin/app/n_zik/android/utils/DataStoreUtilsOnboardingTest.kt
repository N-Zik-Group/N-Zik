package app.n_zik.android.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the onboarding persistence added to [DataStoreUtils] (spec I/O matrix,
 * FIRST_LAUNCH / RELAUNCH rows): the `onboardingComplete` flag, the current step
 * (resume after a post-import restart) and the display-name source round-trip
 * through SharedPreferences and keep their defaults when absent.
 *
 * Robolectric is required (not a plain JVM unit test) because the helpers read and write
 * a real `SharedPreferences` file that needs an Android environment to shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DataStoreUtilsOnboardingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun onboardingFlagDefaultsToFalseWhenAbsent() {
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_ONBOARDING_COMPLETE, false))
    }

    @Test
    fun onboardingFlagRoundTrips() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_ONBOARDING_COMPLETE, true)
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_ONBOARDING_COMPLETE, false))
    }

    @Test
    fun onboardingFlagCanBeResetToFalse() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_ONBOARDING_COMPLETE, true)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_ONBOARDING_COMPLETE, false)
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_ONBOARDING_COMPLETE, true))
    }

    @Test
    fun onboardingStepDefaultsToEmptyWhenAbsent() {
        assertEquals("", DataStoreUtils.getString(context, DataStoreUtils.KEY_ONBOARDING_STEP, ""))
    }

    @Test
    fun onboardingStepRoundTripsAndClears() {
        DataStoreUtils.saveString(context, DataStoreUtils.KEY_ONBOARDING_STEP, "ACCOUNTS")
        assertEquals("ACCOUNTS", DataStoreUtils.getString(context, DataStoreUtils.KEY_ONBOARDING_STEP, ""))
        // The flow completion clears the persisted step so a later launch goes
        // straight to the main app instead of resuming mid-flow
        DataStoreUtils.saveString(context, DataStoreUtils.KEY_ONBOARDING_STEP, "")
        assertEquals("", DataStoreUtils.getString(context, DataStoreUtils.KEY_ONBOARDING_STEP, "ACCOUNTS"))
    }

    @Test
    fun displayNameSourceDefaultsToCustomWhenAbsent() {
        assertEquals(
            DataStoreUtils.DISPLAY_NAME_SOURCE_CUSTOM,
            DataStoreUtils.getString(context, DataStoreUtils.KEY_DISPLAY_NAME_SOURCE, DataStoreUtils.DISPLAY_NAME_SOURCE_CUSTOM)
        )
    }

    @Test
    fun displayNameSourceRoundTrips() {
        DataStoreUtils.saveString(context, DataStoreUtils.KEY_DISPLAY_NAME_SOURCE, DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE)
        assertEquals(
            DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE,
            DataStoreUtils.getString(context, DataStoreUtils.KEY_DISPLAY_NAME_SOURCE, DataStoreUtils.DISPLAY_NAME_SOURCE_CUSTOM)
        )
    }

    @Test
    fun onboardingKeysStayIndependent() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_ONBOARDING_COMPLETE, true)
        DataStoreUtils.saveString(context, DataStoreUtils.KEY_DISPLAY_NAME_SOURCE, DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE)
        DataStoreUtils.saveString(context, DataStoreUtils.KEY_USERNAME, "Danie")

        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_ONBOARDING_COMPLETE, false))
        assertEquals(
            DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE,
            DataStoreUtils.getString(context, DataStoreUtils.KEY_DISPLAY_NAME_SOURCE, DataStoreUtils.DISPLAY_NAME_SOURCE_CUSTOM)
        )
        assertEquals("Danie", DataStoreUtils.getString(context, DataStoreUtils.KEY_USERNAME, ""))
    }
}
