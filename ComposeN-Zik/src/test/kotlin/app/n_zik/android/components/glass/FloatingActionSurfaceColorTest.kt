package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.components.FabItem
import app.it.fast4x.rimusic.ui.components.MultiFabState
import app.it.fast4x.rimusic.ui.components.MultiFloatingActionsButton
import app.it.fast4x.rimusic.ui.components.SmallFloatingActionButtonRow
import app.it.fast4x.rimusic.ui.components.themed.PrimaryButton
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetXkey
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetYkey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w420dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FloatingActionSurfaceColorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `actual floating surfaces use elevated pitch black fill and preserve other palettes in both materials`() {
        RuntimeEnvironment.getApplication().preferences.edit()
            .putString(colorPaletteModeKey, ColorPaletteMode.Dark.name)
            .putFloat(multiFloatActionIconOffsetXkey, 0f)
            .putFloat(multiFloatActionIconOffsetYkey, 0f)
            .commit()
        val palette = mutableStateOf(PureBlackColorPalette)
        val glass = mutableStateOf(false)
        val clicks = mutableMapOf<String, Int>()
        val tags = listOf("up", "search", "home", "menu", "child")
        fun clicked(tag: String) { clicks[tag] = (clicks[tag] ?: 0) + 1 }
        compose.setContent {
            val appearance = Appearance(
                colorPalette = palette.value,
                typography = typographyOf(palette.value.text, true, false, FontType.Rubik),
                thumbnailShape = CircleShape, uiRoundnessShape = CircleShape,
                artistThumbnailShape = CircleShape,
            )
            // Opaque film and zero optics expose the caller's actual film color in the raster.
            val settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance,
                film = 1f, rim = 0f, blur = 0f, tintStrength = 0f, vibrancy = 0f,
                depth = 0f, amount = 0f)
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides settings) {
                Column(Modifier.background(Color.Magenta)) {
                    Row {
                        Box(Modifier.size(72.dp, 96.dp).testTag("up")) {
                            PrimaryButton({ clicked("up") }, R.drawable.chevron_up)
                        }
                        listOf("search" to R.drawable.search, "home" to R.drawable.floating_dock_home,
                            "menu" to R.drawable.menu).forEach { (tag, icon) ->
                            Box(Modifier.size(72.dp, 96.dp).testTag(tag)) {
                                MultiFloatingActionsButton(
                                    useAsActionsMenu = tag == "menu", fabIcon = painterResource(icon),
                                    items = emptyList(), onStateChanged = { clicked(tag) },
                                    onClick = { clicked(tag) },
                                )
                            }
                        }
                    }
                    Box(Modifier.testTag("child")) {
                        SmallFloatingActionButtonRow(
                            item = FabItem(painterResource(R.drawable.search), "child action") { clicked("child") },
                            showLabel = false,
                            stateTransition = updateTransition(MultiFabState.Expanded, label = "expanded child"),
                        )
                    }
                }
            }
        }
        fun action(tag: String): SemanticsNodeInteraction {
            val nodes = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(tag)))
            return nodes[nodes.fetchSemanticsNodes().lastIndex]
        }
        for (enabled in listOf(false, true)) {
            for (theme in listOf(PureBlackColorPalette, DefaultDarkColorPalette, DefaultLightColorPalette)) {
                compose.runOnIdle { palette.value = theme; glass.value = enabled }
                val expected = if (theme === PureBlackColorPalette) Color(0xFF1A1A1A) else theme.background2
                for (tag in tags) {
                    val node = action(tag)
                    val pixels = node.captureToImage().toPixelMap()
                    val sample = pixels[(pixels.width * .2f).toInt(), pixels.height / 2]
                    val message = "$tag, glass=$enabled, palette=$theme"
                    assertEquals(message, expected.red, sample.red, 2f / 255f)
                    assertEquals(message, expected.green, sample.green, 2f / 255f)
                    assertEquals(message, expected.blue, sample.blue, 2f / 255f)
                    var glyphPixels = 0
                    for (y in pixels.height / 3 until pixels.height * 2 / 3) {
                        for (x in pixels.width / 3 until pixels.width * 2 / 3) {
                            val pixel = pixels[x, y]
                            if (kotlin.math.abs(pixel.red - expected.red) > .1f ||
                                kotlin.math.abs(pixel.green - expected.green) > .1f ||
                                kotlin.math.abs(pixel.blue - expected.blue) > .1f) glyphPixels++
                        }
                    }
                    assertTrue("Glyph remains visible: $message", glyphPixels > 5)
                }
            }
        }
        tags.forEach { action(it).performClick() }
        compose.runOnIdle { tags.forEach { assertEquals("$it callback", 1, clicks[it]) } }
    }
}
