package app.n_zik.android.components.settings

import app.n_zik.android.components.settings.common.canOpenUpdater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterDebugAccessTest {
    @Test
    fun debugPreferenceCannotUnlockUnsupportedProductionBuild() {
        assertFalse(canOpenUpdater(autoUpdate = false, debugBuild = false, debugAccess = true))
        assertFalse(canOpenUpdater(autoUpdate = false, debugBuild = true, debugAccess = false))
        assertTrue(canOpenUpdater(autoUpdate = true, debugBuild = false, debugAccess = false))
    }
}
