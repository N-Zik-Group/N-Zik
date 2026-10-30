package app.n_zik.android.components.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingActionPlacementTest {
    @Test fun `manual positions remain unchanged while the button stays reachable`() {
        val anchor = Rect(280f, 600f, 344f, 664f)
        val safe = Rect(0f, 24f, 360f, 776f)
        val manual = Offset(-160f, -300f)
        assertEquals(manual, reachableFloatingActionOffset(manual, anchor, safe))
    }

    @Test fun `rotation and side system bars clamp the whole button inside the window`() {
        val anchor = Rect(640f, 220f, 704f, 284f)
        val landscapeSafe = Rect(32f, 0f, 752f, 336f)
        assertEquals(Offset(-608f, -220f), reachableFloatingActionOffset(
            Offset(-900f, -500f), anchor, landscapeSafe,
        ))
        assertEquals(Offset(48f, 52f), reachableFloatingActionOffset(
            Offset(900f, 500f), anchor, landscapeSafe,
        ))
    }
}
