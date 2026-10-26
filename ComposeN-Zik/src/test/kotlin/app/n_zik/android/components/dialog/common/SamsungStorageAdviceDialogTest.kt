package app.n_zik.android.components.dialog.common

import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The pure show decision of [SamsungStorageAdviceDialog] (spec-samsung-storage-advice-dialog):
 * Samsung + never shown + the active profile still on system storage shows, every other
 * combination does not.
 */
class SamsungStorageAdviceDialogTest {

    @Test
    fun `shows on a Samsung device with system storage that was never shown`() {
        assertTrue(
            SamsungStorageAdviceDialog.shouldShow(
                isSamsung = true,
                alreadyShown = false,
                cacheLocation = ExoPlayerCacheLocation.System
            )
        )
    }

    @Test
    fun `does not show when the device is not Samsung`() {
        assertFalse(
            SamsungStorageAdviceDialog.shouldShow(
                isSamsung = false,
                alreadyShown = false,
                cacheLocation = ExoPlayerCacheLocation.System
            )
        )
    }

    @Test
    fun `does not show when the advice was already shown`() {
        assertFalse(
            SamsungStorageAdviceDialog.shouldShow(
                isSamsung = true,
                alreadyShown = true,
                cacheLocation = ExoPlayerCacheLocation.System
            )
        )
    }

    @Test
    fun `does not show when the cache location is already private`() {
        assertFalse(
            SamsungStorageAdviceDialog.shouldShow(
                isSamsung = true,
                alreadyShown = false,
                cacheLocation = ExoPlayerCacheLocation.Private
            )
        )
    }
}
