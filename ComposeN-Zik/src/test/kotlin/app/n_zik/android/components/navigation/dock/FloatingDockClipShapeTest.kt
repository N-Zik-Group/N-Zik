package app.n_zik.android.components.navigation.dock

import android.app.Application
import android.graphics.Region
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingDockClipShapeTest {
    @Test
    fun `animated clip keeps user corners and translates the actual outline`() {
        fun region(shape: Shape): Region = Region().apply {
            val path = floatingDockClipPath(shape, Size(80f, 40f), 10f, LayoutDirection.Ltr, Density(1f))
            setPath(path.asAndroidPath(), Region(0, 0, 100, 50))
        }
        val square = region(RectangleShape)
        val rounded = region(RoundedCornerShape(8.dp))
        val cut = region(CutCornerShape(8.dp))
        assertTrue(square.contains(10, 0))
        assertFalse(rounded.contains(10, 0))
        assertTrue("8dp corners must not become a half-height pill", rounded.contains(12, 4))
        assertFalse(cut.contains(12, 4))
        listOf(square, rounded, cut).forEach { outline ->
            assertFalse(outline.contains(9, 20))
            assertTrue(outline.contains(10, 20))
            assertTrue(outline.contains(89, 20))
            assertFalse(outline.contains(90, 20))
        }
    }
}
