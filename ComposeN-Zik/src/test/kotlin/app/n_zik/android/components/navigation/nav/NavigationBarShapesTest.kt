package app.n_zik.android.components.navigation.nav

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NavigationBarShapesTest {
    @Test fun `bottom joins screen square in portrait and landscape while upper corners follow roundness`() {
        for (width in listOf(360f, 1000f)) for (direction in LayoutDirection.entries) {
            val shape = bottomNavigationBarShape(RoundedCornerShape(BoundedCornerSize(48.dp, .4f)))
            val outline = shape.createOutline(Size(width, 64f), direction, Density(1f)) as Outline.Rounded
            assertEquals(CornerRadius(25.6f), outline.roundRect.topLeftCornerRadius)
            assertEquals(CornerRadius(25.6f), outline.roundRect.topRightCornerRadius)
            assertEquals(CornerRadius.Zero, outline.roundRect.bottomLeftCornerRadius)
            assertEquals(CornerRadius.Zero, outline.roundRect.bottomRightCornerRadius)
        }
    }
    @Test fun `side panels keep screen corners square and content corners follow user roundness`() {
        for (direction in LayoutDirection.entries) for (onRight in listOf(false, true)) {
            val shape = sideNavigationBarShape(RoundedCornerShape(BoundedCornerSize(48.dp, .4f)), onRight, direction)
            val outline = shape.createOutline(Size(64f, 500f), direction, Density(1f)) as Outline.Rounded
            val inner = CornerRadius(25.6f)
            assertEquals(if (onRight) inner else CornerRadius.Zero, outline.roundRect.topLeftCornerRadius)
            assertEquals(if (onRight) inner else CornerRadius.Zero, outline.roundRect.bottomLeftCornerRadius)
            assertEquals(if (onRight) CornerRadius.Zero else inner, outline.roundRect.topRightCornerRadius)
            assertEquals(if (onRight) CornerRadius.Zero else inner, outline.roundRect.bottomRightCornerRadius)
        }
    }

    @Test fun `top bar joins header square and preserves bounded outer corners`() {
        val shape = topNavigationBarShape(RoundedCornerShape(BoundedCornerSize(48.dp, .4f)))
        val outline = shape.createOutline(Size(360f, 64f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
        assertEquals(CornerRadius.Zero, outline.roundRect.topLeftCornerRadius)
        assertEquals(CornerRadius.Zero, outline.roundRect.topRightCornerRadius)
        assertEquals(CornerRadius(25.6f), outline.roundRect.bottomLeftCornerRadius)
        assertEquals(CornerRadius(25.6f), outline.roundRect.bottomRightCornerRadius)
    }

    @Test fun `zero user roundness stays square`() {
        val shape = topNavigationBarShape(RoundedCornerShape(0.dp))
        assertTrue(shape.createOutline(Size(360f, 64f), LayoutDirection.Ltr, Density(1f)) is Outline.Rectangle)
    }
}
