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
 * Tests the Rewind toggles added to [DataStoreUtils] (spec GH-275, roadmap item 1): all six
 * keys must default to `true` on read — a fresh or existing install keeps the current
 * all-enabled behavior until the user flips something — and round-trip through the
 * `app_settings` SharedPreferences.
 *
 * Robolectric is required (not a plain JVM unit test) because the helpers read and write
 * a real `SharedPreferences` file that needs an Android environment to shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DataStoreUtilsRewindTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun rewindTogglesDefaultToTrueWhenAbsent() {
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true))
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true))
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, true))
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, true))
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, true))
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, true))
    }

    @Test
    fun rewindMasterRoundTrips() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, false)
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true))
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true)
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, false))
    }

    @Test
    fun rewindTypeTogglesRoundTrip() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, false)
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true))
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, true))
        // The global toggle keeps its default (true) until it is written
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, true))
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, false)
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, true))
    }

    @Test
    fun rewindNotificationTogglesRoundTrip() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, false)
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, true))
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, true))
    }

    /**
     * The app header (hamburger menu) stays composed across the whole session, so the menu
     * item's visibility must follow same-process writes in real time (spec GH-275, user
     * device feedback) — this is the listener mechanism behind
     * `rememberDataStoreBooleanPreference`, tested without a composition.
     */
    @Test
    fun booleanPreferenceListenerFollowsSameProcessWritesInRealTime() {
        var observed: Boolean? = null
        val listener = createBooleanPreferenceListener(
            context,
            DataStoreUtils.KEY_REWIND_ENABLED,
            true
        ) { observed = it }
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(listener)

        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, false)
        assertEquals(
            "a same-process write must reach the registered listener",
            false,
            observed
        )

        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true)
        assertEquals(true, observed)

        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    @Test
    fun rewindKeysStayIndependent() {
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, false)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, true)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, true)
        DataStoreUtils.saveBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, false)

        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_ENABLED, true))
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_ENABLED, true))
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_ENABLED, true))
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_GLOBAL_ENABLED, true))
        assertTrue(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_MONTHLY_NOTIF_ENABLED, true))
        assertFalse(DataStoreUtils.getBoolean(context, DataStoreUtils.KEY_REWIND_YEARLY_NOTIF_ENABLED, true))
    }
}
