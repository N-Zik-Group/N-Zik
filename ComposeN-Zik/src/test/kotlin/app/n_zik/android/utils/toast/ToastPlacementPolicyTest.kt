package app.n_zik.android.utils.toast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToastPlacementPolicyTest {
    @Test
    fun `small platform offsets retain breathing room above either navigation style`() {
        for (scale in listOf(1, 2, 3)) {
            for (top in listOf(922f, 891f)) {
                val geometry = BottomNavigationGeometry(top * scale, 1000 * scale, 24 * scale, 54 * scale, 8 * scale)
                assertEquals(((976 - top).toInt() + 8) * scale, toastOffsetAboveNavigation(20 * scale, geometry))
            }
        }
    }

    @Test
    fun `attached toast corrects the Android taskbar frame without borrowing app insets`() {
        // Device evidence: toast bottom=2740 at y=252, navigation top=2596.
        assertEquals(426, correctedToastBottomOffset(252, 2740, 2596 - 30))
    }

    @Test
    fun `attached correction preserves exact gap across different window origins and insets`() {
        assertEquals(282, correctedToastBottomOffset(252, 2596, 2566))
        assertEquals(426, correctedToastBottomOffset(252, 2940, 2766))
        assertEquals(252, correctedToastBottomOffset(252, 2566, 2566))
        assertEquals(222, correctedToastBottomOffset(252, 2536, 2566))
    }

    @Test
    fun `normal bottom preserves the platform gap and offset`() {
        assertEquals(64, toastOffsetAboveNavigation(64, geometry(barTop = 922f)))
    }

    @Test
    fun `floating bar preserves normal gap including its bottom margin`() {
        // Usable bottom 976, bar top 891: 64 px icon-only bar + 21 px above system inset.
        assertEquals(95, toastOffsetAboveNavigation(64, geometry(barTop = 891f)))
        assertEquals(115, toastOffsetAboveNavigation(64, geometry(barTop = 871f)))
    }

    @Test
    fun `system navigation is subtracted once even with larger three button inset`() {
        assertEquals(114, toastOffsetAboveNavigation(64, geometry(barTop = 848f, inset = 48)))
        assertEquals(114, toastOffsetAboveNavigation(64, geometry(barTop = 872f, inset = 24)))
    }

    @Test
    fun `window origin and resizing do not change gap`() {
        val original = geometry(barTop = 871f)
        val moved = original.copy(barTop = 1071f, windowBottom = 1200)
        assertEquals(toastOffsetAboveNavigation(64, original), toastOffsetAboveNavigation(64, moved))
        val resized = original.copy(barTop = 471f, windowBottom = 600)
        assertEquals(toastOffsetAboveNavigation(64, original), toastOffsetAboveNavigation(64, resized))
    }

    @Test
    fun `no bar and invalid measurement preserve platform defaults`() {
        assertEquals(64, toastOffsetAboveNavigation(64, null))
        for (top in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 976f, 1001f)) {
            assertEquals(64, toastOffsetAboveNavigation(64, geometry(barTop = top)))
        }
    }

    @Test
    fun `small OEM default cannot place toast over the bar`() {
        assertEquals(105, toastOffsetAboveNavigation(20, geometry(barTop = 871f)))
    }

    @Test
    fun `density scaling preserves visual spacing`() {
        assertEquals(230, toastOffsetAboveNavigation(128, BottomNavigationGeometry(1742f, 2000, 48, 108)))
    }

    private fun geometry(barTop: Float, inset: Int = 24) = BottomNavigationGeometry(
        barTop = barTop,
        windowBottom = 1000,
        systemBottomInset = inset,
        standardBarContentHeight = 54,
    )
}
