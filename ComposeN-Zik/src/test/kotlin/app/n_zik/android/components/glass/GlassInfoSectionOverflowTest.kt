package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.musicbrainz.InfoAndCommunity
import app.n_zik.android.components.ui.screens.album.Translate
import dev.rebelonion.translator.Language
import dev.rebelonion.translator.Translator
import io.mockk.mockk
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
class GlassInfoSectionOverflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `actual info refresh and expand capsule draws completely beyond its heading`() {
        val enabled = mutableStateOf(true)
        val translator = mockk<Translator>()
        var resyncs = 0
        var refresh = ""
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            val palette = DefaultDarkColorPalette.copy(textSecondary = Color.Blue)
            val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance)
            refresh = stringResource(R.string.mb_resync)
            val translate = Translate.init()
            CompositionLocalProvider(LocalAppearance provides Appearance(
                palette, Typography.fallback, shape, shape, shape,
            ), LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    LazyColumn(Modifier.size(360.dp, 360.dp).background(Color.Black).testTag("scene")) {
                        item {
                            InfoAndCommunity(
                                rating = null, ratingVotes = null, year = null, countryCode = null,
                                keywords = null, links = null, bio = null, translate = translate,
                                translator = translator, languageDestination = Language.AUTO,
                                onResyncClick = { resyncs++ }, onInsightsClick = {},
                            )
                        }
                        item { Box(Modifier.fillMaxWidth().height(160.dp).background(Color.Black)) }
                    }
                }
            }
        }
        val scene = compose.onNodeWithTag("scene")
        val action = compose.onNodeWithContentDescription(refresh)
        val slot = action.fetchSemanticsNode().boundsInRoot
        // Sample the refresh cell only, excluding the heading text and the Insights action below.
        fun glyphBounds(): Rect {
            val pixels = scene.captureToImage().toPixelMap()
            val scanSlot = action.fetchSemanticsNode().boundsInRoot
            val ink = buildList {
                for (y in 0 until (scanSlot.bottom + 50f).toInt()) {
                    for (x in (scanSlot.left - 4f).toInt() until scanSlot.right.toInt()) {
                        val color = pixels[x, y]
                        if (color.blue > .8f && color.red < .4f && color.green < .4f) add(x to y)
                    }
                }
            }
            assertTrue("The real refresh vector must remain visible", ink.isNotEmpty())
            return Rect(ink.minOf { it.first }.toFloat(), ink.minOf { it.second }.toFloat(),
                (ink.maxOf { it.first } + 1).toFloat(), (ink.maxOf { it.second } + 1).toFloat())
        }
        val resting = glyphBounds()
        action.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
        compose.mainClock.advanceTimeBy(700)
        val held = glyphBounds()
        assertTrue("Held glyphs cross the old heading clip, including its 8dp bottom padding",
            held.bottom > slot.bottom + 8f)
        assertTrue("The complete glyph survives rather than losing its lower half", held.height >= resting.height)
        assertEquals("Motion must not move hit slots", slot, action.fetchSemanticsNode().boundsInRoot)
        action.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1800)
        assertEquals(resting, glyphBounds())
        compose.runOnIdle { assertEquals(0, resyncs) }
        action.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle { assertEquals(1, resyncs); enabled.value = false }
        // OFF has no spring to inspect; let Compose settle the replacement native layout.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val native = glyphBounds()
        action.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
        compose.mainClock.advanceTimeBy(700)
        assertEquals("Glass OFF retains native geometry", native, glyphBounds())
        action.performTouchInput { cancel() }
    }
}
