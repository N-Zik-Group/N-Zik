package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.components.settings.common.settingsCardOverflowEnter
import app.n_zik.android.components.settings.common.settingsCardOverflowExit
import app.n_zik.android.components.ui.screens.profiles.ProfileCardSurface
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
class GlassCardControlOverflowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `settings and profile card skins cannot crop a held child capsule`() {
        val profile = mutableStateOf(false)
        val visible = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(24.dp)
            val settings = GlassSettings(enabled = true, quality = GlassQuality.Performance)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            ), LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Column(Modifier.size(280.dp, 200.dp).background(Color.Black).testTag("scene")) {
                        val content: @Composable ColumnScope.() -> Unit = {
                            GlassActionGroup(settings, GlassArea.ActionButtons, Modifier.padding(8.dp)) {
                                GlassActionGroupItem({}, Modifier.testTag("action")) {
                                    Box(Modifier.size(20.dp).background(Color.Blue))
                                }
                                GlassActionGroupItem({}) { Box(Modifier.size(20.dp).background(Color.Green)) }
                            }
                        }
                        GlassControlOverflowColumn {
                            AnimatedVisibility(visible.value, enter = settingsCardOverflowEnter, exit = settingsCardOverflowExit) {
                                Box(glassOverflowFade(400, 200)) {
                                    if (profile.value) ProfileCardSurface(
                                        Modifier.fillMaxWidth().testTag("card"), settings = settings, content = content,
                                    ) else SettingsCardSurface(
                                        Modifier.fillMaxWidth().testTag("card"), shape,
                                        Color.DarkGray, Color.Black, Color.Black, settings = settings, content = content,
                                    )
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Black))
                    }
                }
            }
        }
        fun glyphBounds(): Rect {
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            val ink = buildList {
                for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                    val color = pixels[x, y]
                    // The owning card fades during enter/exit. Follow the glyph's blue chroma,
                    // including dim edge pixels, rather than mistaking opacity for cropping.
                    if (color.blue > .05f && color.red < color.blue * .4f && color.green < color.blue * .4f) add(x to y)
                }
            }
            assertTrue("The held glyph remains visible", ink.isNotEmpty())
            return Rect(ink.minOf { it.first }.toFloat(), ink.minOf { it.second }.toFloat(),
                (ink.maxOf { it.first } + 1).toFloat(), (ink.maxOf { it.second } + 1).toFloat())
        }
        listOf(false, true).forEach { isProfile ->
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { profile.value = isProfile }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            val action = compose.onNodeWithTag("action")
            val card = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
            val resting = glyphBounds()
            action.performTouchInput { down(center); moveTo(center + Offset(0f, 500f), 100) }
            compose.mainClock.advanceTimeBy(700)
            val held = glyphBounds()
            assertTrue("Whole glyph crosses the $isProfile card skin and its following opaque sibling: card=$card resting=$resting held=$held",
                held.bottom > card.bottom && held.height >= resting.height)
            compose.runOnIdle { visible.value = false }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnIdle { visible.value = true }
            compose.mainClock.advanceTimeBy(32)
            val interrupted = glyphBounds()
            assertTrue("Interrupting card shrink with expand cannot slice the held glyph: resting=$resting held=$held interrupted=$interrupted",
                interrupted.height >= resting.height && interrupted.bottom > card.bottom)
            action.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1800)
            assertEquals(resting, glyphBounds())
        }
    }
}
