package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import java.util.concurrent.TimeUnit
import org.robolectric.Shadows.shadowOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.components.ui.screens.profiles.ProfileCardSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCombinedBackdrop
import androidx.compose.ui.graphics.drawscope.DrawScope

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassInlineParentBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `badges sample changing settings and profile skin in optical tint and native card modes`() {
        val fill = mutableStateOf(Color.Green)
        val mode = mutableStateOf(GlassAreaMode.GlassOnly)
        val quality = mutableStateOf(GlassQuality.Balanced)
        val enabled = mutableStateOf(true)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(enabled = enabled.value, quality = quality.value,
                film = 1f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f, rim = 0f,
                areaModes = mapOf(GlassArea.SettingsCards to mode.value))
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette.copy(background0 = Color(0xFF101010), background1 = fill.value),
                Typography.fallback, shape, shape, shape), LocalGlassPreviewSettings provides settings) {
                Column(Modifier.background(Color.Black)) {
                    SettingsCardSurface(Modifier.size(180.dp, 100.dp).testTag("settings"), shape,
                        fill.value, Color.Black, Color.Black, settings = settings) {
                        Box(Modifier.padding(16.dp).size(32.dp)
                            .iconBadgeBackground(Color.Transparent, shape, settings.copy(film = 0f)))
                    }
                    ProfileCardSurface(Modifier.size(180.dp, 100.dp).testTag("profile"), settings = settings) {
                        Box(Modifier.padding(16.dp).size(32.dp)
                            .iconBadgeBackground(Color.Transparent, shape, settings.copy(film = 0f)))
                    }
                }
            }
        }
        for (renderer in GlassQuality.entries) for (cardMode in listOf(
            GlassAreaMode.GlassOnly, GlassAreaMode.ContourOnly, GlassAreaMode.Disabled,
        )) for (active in listOf(true, false)) for (color in listOf(Color.Green, Color.Blue)) {
            compose.runOnIdle {
                quality.value = renderer; mode.value = cardMode; enabled.value = active; fill.value = color
            }
            for (tag in listOf("settings", "profile")) {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                val parent = pixels[120, 32]
                val child = pixels[32, 32]
                assertEquals("$tag $renderer $cardMode enabled=$active parent red", color.red, parent.red, .03f)
                assertEquals("$tag $renderer $cardMode enabled=$active parent green", color.green, parent.green, .03f)
                assertEquals("$tag $renderer $cardMode enabled=$active parent blue", color.blue, parent.blue, .03f)
                assertEquals("$tag $renderer $cardMode enabled=$active red", parent.red, child.red, .03f)
                assertEquals("$tag $renderer $cardMode enabled=$active green", parent.green, child.green, .03f)
                assertEquals("$tag $renderer $cardMode enabled=$active blue", parent.blue, child.blue, .03f)
            }
        }
    }

    @Test fun `real search badge follows its bar fill instead of page fallback`() {
        val fill = mutableStateOf(Color.Green)
        val quality = mutableStateOf(GlassQuality.Balanced)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(quality = quality.value, film = .5f, blur = 0f,
                vibrancy = 0f, depth = 0f, amount = 0f, rim = 0f)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette.copy(background0 = Color(0xFF101010), accent = Color.Red),
                Typography.fallback, shape, shape, shape), LocalGlassPreviewSettings provides settings) {
                UnifiedSearchBar(TextFieldValue(""), {}, R.string.search,
                    baseSurface = fill.value, onClear = {})
            }
        }
        for (renderer in GlassQuality.entries) {
            fun badgePixel(color: Color): Pair<Color, Color> {
                compose.runOnIdle { quality.value = renderer; fill.value = color }
                compose.waitForIdle()
                shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeBy(1200)
                compose.waitForIdle()
                val bar = compose.onNodeWithTag("unified_search_bar")
                // Robolectric's capture draws outside a frame. The first pass publishes
                // the new exported material; deliver that draw's snapshot notifications
                // before checking retained descendant layers on the next pass.
                bar.captureToImage()
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                shadowOf(Looper.getMainLooper()).idleFor(32, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val barBounds = bar.fetchSemanticsNode().boundsInRoot
                val searchLabel = ApplicationProvider.getApplicationContext<Application>().getString(R.string.search)
                val glyph = compose.onNodeWithContentDescription(searchLabel, useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                val pixels = bar.captureToImage().toPixelMap()
                // The badge extends 7dp beyond the 18dp glyph on each side.
                val badgeX = (glyph.left - barBounds.left - 3f).toInt()
                val badgeY = (glyph.top - barBounds.top - 3f).toInt()
                val badge = pixels[badgeX, badgeY]
                val parent = pixels[pixels.width - 20, pixels.height / 2]
                return parent to badge
            }
            val (greenParent, green) = badgePixel(Color.Green)
            val (blueParent, blue) = badgePixel(Color.Blue)
            val evidence = "$renderer parent=$greenParent -> $blueParent badge=$green -> $blue"
            assertTrue(evidence, greenParent.green > blueParent.green + .05f)
            assertTrue(evidence, blueParent.blue > greenParent.blue + .05f)
            assertTrue(evidence, green.green > blue.green + .05f)
            assertTrue(evidence, blue.blue > green.blue + .05f)
        }
    }

    @Test fun `parent film updates concurrent direct and combined consumers then stops drawing`() {
        val fill = mutableStateOf(Color.Green)
        val settings = GlassSettings(film = 0f, blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f, rim = 0f)
        var exported: LayerBackdrop? = null
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)) {
                val emptyDraw: DrawScope.() -> Unit = remember { {} }
                val clear = rememberCanvasBackdrop(emptyDraw)
                val page = rememberCanvasBackdrop { drawRect(Color.Black) }
                GlassMaterialContainerScope(Modifier.size(160.dp, 180.dp).testTag("concurrent-parent"), material = { layer ->
                    SideEffect { exported = layer }
                    Modifier.glassMaterial(settings.copy(film = .5f), shape, ownBackdrop = page,
                        filmColor = fill.value, exportedBackdrop = layer, drawShadow = false)
                }) { surface ->
                    Column(surface) {
                        val parent = requireNotNull(LocalGlassContainerBackdrop.current)
                        val two = rememberCombinedBackdrop(parent, clear)
                        val three = rememberCombinedBackdrop(clear, parent, clear)
                        val many = rememberCombinedBackdrop(*arrayOf(clear, clear, parent, clear))
                        for ((index, source) in listOf(parent, two, three, many).withIndex()) {
                            Box(Modifier.size(80.dp, 40.dp).testTag("consumer-$index")
                                .glassMaterial(settings.copy(film = .5f), shape, ownBackdrop = source,
                                    filmColor = Color.Red.copy(alpha = .1f), drawShadow = false))
                        }
                    }
                }
            }
        }
        for (color in listOf(Color.Green, Color.Blue, Color.Red, Color.Blue)) {
            compose.runOnIdle { fill.value = color }
            val parentNode = compose.onNodeWithTag("concurrent-parent")
            val parentBounds = parentNode.fetchSemanticsNode().boundsInRoot
            // Capture the ancestor only: capturing each child separately can itself
            // force its retained layer to refresh and conceal stale descendants.
            val pixels = parentNode.captureToImage().toPixelMap()
            val expectedParent = color.copy(alpha = .5f).compositeOver(Color.Black)
            val expected = Color.Red.copy(alpha = .05f).compositeOver(expectedParent)
            for (index in 0..3) {
                val bounds = compose.onNodeWithTag("consumer-$index").fetchSemanticsNode().boundsInRoot
                val actual = pixels[(bounds.center.x - parentBounds.left).toInt(),
                    (bounds.center.y - parentBounds.top).toInt()]
                assertEquals("consumer $index follows $color red", expected.red, actual.red, .03f)
                assertEquals("consumer $index follows $color green", expected.green, actual.green, .03f)
                assertEquals("consumer $index follows $color blue", expected.blue, actual.blue, .03f)
            }
        }
        compose.waitForIdle()
        val settledVersion = requireNotNull(exported).contentVersion
        repeat(6) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        assertEquals("unchanged exported material must not feed a redraw loop", settledVersion,
            requireNotNull(exported).contentVersion)
    }
}
