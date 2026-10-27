package app.n_zik.android

import android.app.Application
import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.privacy.blockScreenCaptureKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MainActivityScreenCapturePolicyTest {
    @Test fun activityStartBindsPersistedPolicyAndDestroyReleasesItsListener() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setActiveProfile("screen-capture-activity-test", context)
        val prefs = context.preferences
        prefs.edit().clear().putBoolean(blockScreenCaptureKey, true).commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        var destroyed = false
        try {
            controller.create().start()
            val activity = controller.get()
            fun secure() = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            assertTrue("onStart must apply the persisted policy before resume", secure())
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            prefs.edit().putBoolean(blockScreenCaptureKey, false).commit()
            assertFalse(secure())
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
            controller.stop().destroy()
            destroyed = true
            prefs.edit().putBoolean(blockScreenCaptureKey, true).commit()
            assertFalse("destroyed Activity must no longer observe preference changes", secure())
        } finally {
            if (!destroyed) controller.destroy()
            prefs.edit().clear().commit()
            setActiveProfile("default", context)
        }
    }
}
