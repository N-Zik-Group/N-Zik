package app.n_zik.android.core.network

import android.app.Application
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OfflineModeProfileFlowTest {
    @Test fun `flow rebinds on profile change and detaches previous preferences`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val profiles = context.profilePreferences
        val originalProfile = profiles.getString(activeProfileKey, null)
        val values = mutableListOf<Boolean>()
        profiles.edit().putString(activeProfileKey, "aa-flow-a").commit()
        val a = context.preferences
        a.edit().remove(fullOfflineModeKey).commit()
        profiles.edit().putString(activeProfileKey, "aa-flow-b").commit()
        val b = context.preferences
        b.edit().remove(fullOfflineModeKey).commit()
        profiles.edit().putString(activeProfileKey, "aa-flow-a").commit()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            OfflineMode.enabledFlow(context).collect { values += it }
        }
        try {
            runCurrent()
            a.edit().putBoolean(fullOfflineModeKey, true).commit()
            runCurrent()
            profiles.edit().putString(activeProfileKey, "aa-flow-b").commit()
            runCurrent()
            a.edit().putBoolean(fullOfflineModeKey, false).commit()
            runCurrent()
            b.edit().putBoolean(fullOfflineModeKey, true).commit()
            runCurrent()
            assertEquals(listOf(false, true, false, true), values)
        } finally {
            job.cancel()
            runCurrent()
            a.edit().remove(fullOfflineModeKey).commit()
            b.edit().remove(fullOfflineModeKey).commit()
            profiles.edit().apply { if (originalProfile == null) remove(activeProfileKey) else putString(activeProfileKey, originalProfile) }.commit()
        }
    }
}
