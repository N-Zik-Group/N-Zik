package app.n_zik.android.components.settings.common

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPlayerRestartStateTest {
    @Test fun severalSettingsShareOneRestartAndOneSuccessfulCompletion() {
        val state = SettingsPlayerRestartState()
        state.requireRestart()
        state.requireRestart()
        assertTrue(state.pending)

        val request = requireNotNull(state.begin())
        assertNull(state.begin())
        assertTrue(state.complete(request, ready = true))
        assertFalse(state.pending)
        assertFalse(state.restarting)
    }

    @Test fun failedRestartAndChangesMadeWhileStartingRemainPending() {
        val state = SettingsPlayerRestartState()
        state.requireRestart()
        assertFalse(state.complete(requireNotNull(state.begin()), ready = false))
        assertTrue(state.pending)

        val request = requireNotNull(state.begin())
        state.requireRestart()
        assertFalse(state.complete(request, ready = true))
        assertTrue(state.pending)

        val retry = requireNotNull(state.begin())
        assertFalse(state.complete(request, ready = true))
        assertTrue(state.restarting)
        assertTrue(state.complete(retry, ready = true))
        assertFalse(state.pending)
    }

    @Test fun configurationRestoreKeepsPendingChangesAndAllowsInterruptedRestartToRetry() {
        val state = SettingsPlayerRestartState()
        state.requireRestart()
        state.begin()
        val saver = SettingsPlayerRestartState.saver("profile-a")
        val saved = requireNotNull(with(saver) { SaverScope { true }.save(state) })
        val restored = requireNotNull(saver.restore(saved))

        assertNull(SettingsPlayerRestartState.saver("profile-b").restore(saved))
        assertTrue(restored.pending)
        assertFalse(restored.restarting)
        assertTrue(restored.complete(requireNotNull(restored.begin()), ready = true))
    }
}
