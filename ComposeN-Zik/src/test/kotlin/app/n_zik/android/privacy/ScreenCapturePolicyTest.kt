package app.n_zik.android.privacy

import android.app.Activity
import android.app.Application
import android.view.WindowManager
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ScreenCapturePolicyTest {
    private fun Activity.secure() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    @Test fun persistedPolicyAppliesBeforeDisplayAndTracksChangesWithoutClearingOtherFlags() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        setActiveProfile("default", activity)
        activity.preferences.edit().clear().putBoolean(blockScreenCaptureKey, true).commit()
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val binding = ScreenCapturePolicy.bind(activity)
        assertTrue(activity.secure())
        activity.preferences.edit().putBoolean(blockScreenCaptureKey, false).commit()
        assertFalse(activity.secure())
        assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        activity.preferences.edit().putBoolean(blockScreenCaptureKey, true).commit()
        assertTrue(activity.secure())
        binding.close()
        activity.preferences.edit().putBoolean(blockScreenCaptureKey, false).commit()
        assertTrue("disposed binding must not receive changes", activity.secure())
        controller.destroy()
    }

    @Test fun disablingSettingPreservesSecureFlagOwnedByAnotherPolicy() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        setActiveProfile("default", activity)
        activity.preferences.edit().clear().commit()
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val binding = ScreenCapturePolicy.bind(activity)
        activity.preferences.edit().putBoolean(blockScreenCaptureKey, false).commit()
        assertTrue(activity.secure())
        binding.close()
        controller.destroy()
    }
}
