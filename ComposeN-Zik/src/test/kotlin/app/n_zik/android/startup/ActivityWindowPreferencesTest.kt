package app.n_zik.android.startup

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import app.n_zik.android.components.settings.other.applyOtherDefaults
import app.n_zik.android.utils.closeWithBackButtonKey
import app.n_zik.android.utils.isKeepScreenOnEnabledKey
import app.n_zik.android.utils.preferences

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActivityWindowPreferencesTest {
    @Test
    fun `keep screen on updates immediately and the settings reset clears the flag`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val preferences = activity.preferences
        try {
            preferences.edit { putBoolean(isKeepScreenOnEnabledKey, true) }
            activity.observeWindowPreferences()
            assertTrue(activity.keepsScreenOn())

            applyOtherDefaults(activity)
            assertFalse(activity.keepsScreenOn())

            preferences.edit { putBoolean(isKeepScreenOnEnabledKey, true) }
            assertTrue(activity.keepsScreenOn())
        } finally {
            controller.pause().stop().destroy()
        }
        preferences.edit { remove(isKeepScreenOnEnabledKey) }
        assertTrue(activity.keepsScreenOn())
    }

    @Test
    fun `back preference updates in place and gives later navigation callbacks priority`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val preferences = activity.preferences
        val dispatcher = activity.onBackPressedDispatcher
        try {
            preferences.edit { putBoolean(closeWithBackButtonKey, false) }
            activity.observeWindowPreferences()
            assertTrue(dispatcher.hasEnabledCallbacks())

            var navigations = 0
            val navigationCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    navigations++
                }
            }
            dispatcher.addCallback(navigationCallback)
            dispatcher.onBackPressed()
            assertEquals(1, navigations)
            navigationCallback.remove()

            preferences.edit { putBoolean(closeWithBackButtonKey, true) }
            assertFalse(dispatcher.hasEnabledCallbacks())
            preferences.edit { putBoolean(closeWithBackButtonKey, false) }
            assertTrue(dispatcher.hasEnabledCallbacks())
        } finally {
            controller.pause().stop().destroy()
        }
        assertFalse(dispatcher.hasEnabledCallbacks())
    }

    @Test
    @Config(sdk = [32])
    fun `older devices do not install the back fallback`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        try {
            activity.preferences.edit { putBoolean(closeWithBackButtonKey, false) }
            activity.observeWindowPreferences()
            assertFalse(activity.onBackPressedDispatcher.hasEnabledCallbacks())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun ComponentActivity.keepsScreenOn(): Boolean =
        (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
}
