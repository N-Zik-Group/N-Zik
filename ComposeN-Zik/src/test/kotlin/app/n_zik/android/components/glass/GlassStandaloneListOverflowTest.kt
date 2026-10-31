package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassStandaloneListOverflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real standalone locator survives following opaque lazy row and releases its elevation`() {
        val enabled = mutableStateOf(true)
        var clicks = 0
        var raised = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            ), LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    LazyColumn(Modifier.size(240.dp, 160.dp).background(Color.Black).testTag("list")) {
                        item("header") {
                            GlassControlOverflowColumn {
                                raised = LocalGlassGroupElevation.current?.raised == true
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.weight(1f))
                                    GlassToolbarRow(1, settings = settings) {
                                        if (!GlassToolbarIcon(painterResource(R.drawable.locate), Color.Blue, 24.dp,
                                            enabled = true, onClick = { clicks++ }, contentDescription = "locator")) {
                                            Box(Modifier.size(40.dp).clickable { clicks++ }, contentAlignment = Alignment.Center) {
                                                Icon(painterResource(R.drawable.locate), "locator", Modifier.size(20.dp), tint = Color.Blue)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item("song") {
                            // LocalPlaylistSongs gives its opaque song rows an explicit zIndex of 2.
                            Box(Modifier.fillMaxWidth().height(100.dp).zIndex(2f).background(Color.Black))
                        }
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("list")
        val locator = compose.onNodeWithContentDescription("locator")
        fun blueBounds(): Rect {
            val image = list.captureToImage().toPixelMap()
            val ink = buildList {
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    val color = image[x, y]
                    if (color.blue > .8f && color.red < .4f && color.green < .4f) add(x to y)
                }
            }
            assertTrue("The real locate vector must paint visible pixels", ink.isNotEmpty())
            return Rect(ink.minOf { it.first }.toFloat(), ink.minOf { it.second }.toFloat(),
                (ink.maxOf { it.first } + 1).toFloat(), (ink.maxOf { it.second } + 1).toFloat())
        }
        val resting = blueBounds()
        locator.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
        compose.mainClock.advanceTimeBy(700)
        val held = blueBounds()
        assertTrue("The lower part of the pin remains visible beyond the header item", held.bottom > 44f)
        assertTrue("The following row cannot chop the glyph", held.height >= resting.height)
        compose.runOnIdle { assertTrue(raised); assertEquals(0, clicks) }
        locator.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(resting, blueBounds())
        compose.runOnIdle { assertTrue(!raised) }
        locator.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle { assertEquals(1, clicks); enabled.value = false }
        val native = blueBounds()
        locator.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
        compose.mainClock.advanceTimeBy(700)
        assertEquals(native, blueBounds())
        compose.runOnIdle { assertTrue(!raised) }
        locator.performTouchInput { cancel() }
    }
}
