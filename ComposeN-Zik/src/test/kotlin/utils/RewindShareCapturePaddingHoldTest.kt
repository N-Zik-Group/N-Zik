package utils

import app.n_zik.android.components.ui.screens.rewind.rewindShareCaptureActive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the share-capture bar-padding hold ([rewindShareCaptureActive]): the
 * 16-image PixelCopy flow hides the system bars and the slide shells keep their safe-area
 * padding through the ignoring-visibility insets only while this state is true. It must be
 * off by default — a stuck-true state would make the live deck pad with hidden-bar insets
 * outside any capture.
 */
class RewindShareCapturePaddingHoldTest {

    @Test
    fun paddingHoldIsOffByDefault() {
        assertFalse(rewindShareCaptureActive.value)
    }

    @Test
    fun paddingHoldRoundTrip() {
        rewindShareCaptureActive.value = true
        assertTrue(rewindShareCaptureActive.value)
        rewindShareCaptureActive.value = false
        assertFalse(rewindShareCaptureActive.value)
    }
}
