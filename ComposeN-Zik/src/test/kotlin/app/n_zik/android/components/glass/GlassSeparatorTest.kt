package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassSeparatorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `functional separators use original rim in glass only and preserve native disabled paths`() {
        val settings = GlassSettings(rim = .7f)
        assertFalse(settings.copy(enabled = false).usesGlassSeparator(GlassArea.Menus))
        assertFalse(settings.copy(areaModes = mapOf(GlassArea.Menus to GlassAreaMode.Disabled))
            .usesGlassSeparator(GlassArea.Menus))
        listOf(GlassAreaMode.ContourOnly, GlassAreaMode.GlassOnly, GlassAreaMode.GlassAndContour).forEach { mode ->
            val selected = settings.copy(areaModes = mapOf(GlassArea.Menus to mode))
            assertTrue(selected.usesGlassSeparator(GlassArea.Menus))
            assertEquals(.7f, glassEdgeLightStops(selected, Color.Red).first().second.alpha, .002f)
        }
        val tint = Color(0xFF4080C0)
        val fullTint = settings.copy(tintStrength = 1f)
        val edge = glassEdgeLightColor(fullTint, tint)
        assertEquals(tint, edge)
        val stops = glassEdgeLightStops(fullTint, tint)
        assertTrue(stops.map { it.second.alpha }.distinct().size > 2)
        stops.forEach { (_, color) ->
            assertEquals(edge.red, color.red, .002f)
            assertEquals(edge.green, color.green, .002f)
            assertEquals(edge.blue, color.blue, .002f)
        }
        assertTrue(glassEdgeLightStops(settings.copy(rim = 0f), tint).all { it.second.alpha == 0f })
        assertEquals(20f, glassActionSeparatorHeight(42f, 20f), 0f)
        assertEquals(6f, glassActionSeparatorHeight(12f, 20f), 0f)
    }

    @Test fun `home paints only short settings boundary without changing bounds and hides transferred separator`() {
        val settings = mutableStateOf(GlassSettings(rim = 1f, tintStrength = 1f,
            source = GlassTintSource.Custom, customColor = 0xFFFF0000.toInt(),
            areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.GlassOnly)))
        val transferred = mutableStateOf(false)
        compose.setContent {
            Box(Modifier.size(GlassActionCellWidth * 3, GlassActionCellHeight)
                .background(Color.Black).glassActionGroupSeparators(
                    settings.value, GlassArea.ActionButtons, GlassActionCellWidth,
                    transferred = { transferred.value }, separatorBefore = setOf(2),
                ).testTag("group"))
        }
        val bounds = compose.onNodeWithTag("group").fetchSemanticsNode().boundsInRoot
        val image = compose.onNodeWithTag("group").captureToImage().toPixelMap()
        val first = image.width / 3
        val second = image.width * 2 / 3
        for (y in 0 until image.height) {
            for (x in first - 2..first + 2) assertEquals(Color.Black, image[x, y])
        }
        val paintedRows = (0 until image.height).filter { y ->
            (second - 2..second + 2).any { x -> image[x, y] != Color.Black }
        }
        assertTrue(paintedRows.isNotEmpty())
        assertTrue(paintedRows.first() > 0)
        assertTrue(paintedRows.last() < image.height - 1)
        assertTrue(paintedRows.size <= image.height / 2 + 2)
        val middle = image[second, image.height / 2]
        assertTrue(middle.red > 0f && middle.green == 0f && middle.blue == 0f)
        // The short separator must stay continuous rather than fading out midway.
        (paintedRows.first() + 2 until paintedRows.last() - 1).forEach { y ->
            assertEquals(middle.red, image[second, y].red, .01f)
        }

        listOf("transfer", "master", "area", "rim").forEach { state ->
            compose.runOnIdle {
                transferred.value = state == "transfer"
                settings.value = settings.value.copy(enabled = state != "master", rim = if (state == "rim") 0f else 1f,
                    areaModes = mapOf(GlassArea.ActionButtons to if (state == "area") GlassAreaMode.Disabled else GlassAreaMode.GlassOnly))
            }
            assertEquals(bounds, compose.onNodeWithTag("group").fetchSemanticsNode().boundsInRoot)
            val hidden = compose.onNodeWithTag("group").captureToImage().toPixelMap()
            for (y in 0 until hidden.height) for (x in 0 until hidden.width) assertEquals(Color.Black, hidden[x, y])
        }
    }
}
