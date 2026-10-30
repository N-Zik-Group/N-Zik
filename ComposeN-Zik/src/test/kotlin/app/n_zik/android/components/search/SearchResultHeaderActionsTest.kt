package app.n_zik.android.components.search

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Rect
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.LocalGlassMenuTrigger
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.GlassOverlayHost
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.menu.GlassDropdownMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class SearchResultHeaderActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native middle globe sends each neighbor to its own side and returns continuously`() {
        lateinit var trigger: GlassMenuTriggerState
        val owner = Any()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appearance = Appearance(colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(enabled = false)) {
                Box(Modifier.size(360.dp, 80.dp), contentAlignment = Alignment.TopCenter) {
                    SearchResultHeaderActions(true, {}) {
                        trigger = requireNotNull(LocalGlassMenuTrigger.current)
                        SearchResultHeaderIcon(R.drawable.discover, "native globe", {}) {
                            IconButton(onClick = {}) { Icon(painterResource(R.drawable.discover), "native globe") }
                        }
                        SearchResultHeaderIcon(R.drawable.sort_grid, "native layout", {}) {
                            IconButton(onClick = {}) { Icon(painterResource(R.drawable.sort_grid), "native layout") }
                        }
                    }
                }
            }
        }
        val pencil = compose.onNodeWithContentDescription(context.getString(R.string.search))
        val layout = compose.onNodeWithContentDescription("native layout")
        val before = listOf(pencil.fetchSemanticsNode().boundsInRoot, layout.fetchSemanticsNode().boundsInRoot)
        val source = trigger.bounds
        compose.runOnIdle {
            trigger.claim(owner)
            trigger.updateMenuBounds(owner, Rect(source.left - source.width, source.top, source.right + source.width, source.bottom))
        }
        var previous = before.map { it.left }
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val positions = listOf(pencil.fetchSemanticsNode().boundsInRoot.left, layout.fetchSemanticsNode().boundsInRoot.left)
            positions.zip(previous).forEach { (now, last) ->
                assertTrue("native sibling must move continuously: $last -> $now; source=$source", kotlin.math.abs(now - last) < source.width / 2)
            }
            previous = positions
            assertEquals(source, trigger.bounds)
        }
        assertTrue("pencil nudges left without escaping the group",
            before[0].left - previous[0] in 1f..(source.width / 4f + 1f))
        assertTrue("layout nudges right without escaping the group",
            previous[1] - before[1].left in 1f..(source.width / 4f + 1f))
        compose.runOnIdle { trigger.release(owner) }
        repeat(40) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val positions = listOf(pencil.fetchSemanticsNode().boundsInRoot.left, layout.fetchSemanticsNode().boundsInRoot.left)
            positions.zip(previous).forEach { (now, last) ->
                assertTrue("release must not snap native siblings: $last -> $now; source=$source", kotlin.math.abs(now - last) < source.width / 2)
            }
            previous = positions
        }
        compose.mainClock.advanceTimeBy(1600)
        compose.waitForIdle()
        assertEquals(before, listOf(pencil.fetchSemanticsNode().boundsInRoot, layout.fetchSemanticsNode().boundsInRoot))
    }

    @Test fun `native globe transfers its glyph when dropdown uses glass material`() {
        assertNativeGlobeTransfer(globalGlass = true)
    }

    @Test fun `native globe transfers its glyph when glass is disabled`() {
        assertNativeGlobeTransfer(globalGlass = false)
    }

    private fun assertNativeGlobeTransfer(globalGlass: Boolean) {
        val host = GlassMenuHostState()
        val expanded = mutableStateOf(false)
        var selected = false
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalGlassMenuHost provides host,
                LocalGlassPreviewSettings provides GlassSettings(enabled = globalGlass,
                    areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled)),
            ) {
                Box {
                    SearchResultHeaderActions(false, {}) {
                        Box {
                            SearchResultHeaderIcon(R.drawable.discover, "globe", { expanded.value = true }) {
                                IconButton(onClick = { expanded.value = true }) {
                                    Icon(painterResource(R.drawable.discover), "globe")
                                }
                            }
                            GlassDropdownMenu(expanded.value, { expanded.value = false }, Color.DarkGray, CircleShape) {
                                DropdownMenuItem(text = { Text("Native filter") }, onClick = {
                                    selected = true
                                    expanded.value = false
                                })
                            }
                        }
                    }
                    GlassOverlayHost(host)
                }
            }
        }
        val initialBounds = compose.onNodeWithContentDescription("globe").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("globe").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            val entry = requireNotNull(host.entry)
            val trigger = requireNotNull(entry.trigger)
            assertEquals(trigger.bounds, entry.anchor)
            assertEquals(initialBounds.center.x, entry.anchor.center.x, .5f)
            assertEquals(initialBounds.center.y, entry.anchor.center.y, .5f)
            assertTrue("source includes the full button rather than only its inset semantics",
                entry.anchor.left <= initialBounds.left && entry.anchor.top <= initialBounds.top &&
                    entry.anchor.right >= initialBounds.right && entry.anchor.bottom >= initialBounds.bottom)
            assertNotNull("the overlay must receive the native globe glyph", trigger.content)
            assertTrue(trigger.isTransferred)
        }
        compose.onNodeWithContentDescription("globe").assertDoesNotExist()
        compose.onNodeWithText("Native filter").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(selected)
            assertNull(host.entry)
        }
        assertEquals(initialBounds, compose.onNodeWithContentDescription("globe").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `globe transfers into its dropdown and three action capsule rejoins after closing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = context.preferences.getBoolean(glassEnabledKey, false)
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        try {
            val host = GlassMenuHostState()
            val expanded = mutableStateOf(false)
            var selected = 0
            val clicks = IntArray(2)
            val appearance = Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
            )
            compose.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassMenuHost provides host) {
                    Box(Modifier.fillMaxWidth()) {
                        Box(Modifier.align(Alignment.TopEnd)) {
                            SearchResultHeaderActions(true, { clicks[0]++ }) {
                                Box {
                                    SearchResultHeaderIcon(R.drawable.discover, "globe", { expanded.value = true }) {}
                                    GlassDropdownMenu(expanded.value, { expanded.value = false }, Color.Transparent, CircleShape) {
                                        DropdownMenuItem(text = { Text("Choose filter") }, onClick = {
                                            selected++
                                            expanded.value = false
                                        })
                                    }
                                }
                                SearchResultHeaderIcon(R.drawable.sort_grid, "layout", { clicks[1]++ }) {}
                            }
                        }
                        GlassOverlayHost(host)
                    }
                }
            }
            val globe = compose.onNodeWithContentDescription("globe").fetchSemanticsNode().boundsInRoot
            val layout = compose.onNodeWithContentDescription("layout").fetchSemanticsNode().boundsInRoot
            val pencil = compose.onNodeWithContentDescription(context.getString(R.string.search)).fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription("globe").performClick()
            compose.waitForIdle()
            compose.runOnIdle {
                assertNotNull(host.entry?.trigger)
                assertEquals(globe, host.entry?.anchor)
                assertEquals(globe, host.entry?.trigger?.bounds)
            }
            compose.onNodeWithContentDescription("globe").assertDoesNotExist()
            compose.onNodeWithText("Choose filter").performClick()
            compose.waitForIdle()
            compose.runOnIdle {
                assertNull(host.entry)
                assertEquals(1, selected)
            }
            assertEquals(globe, compose.onNodeWithContentDescription("globe").fetchSemanticsNode().boundsInRoot)
            assertEquals(layout, compose.onNodeWithContentDescription("layout").fetchSemanticsNode().boundsInRoot)
            assertEquals(pencil, compose.onNodeWithContentDescription(context.getString(R.string.search)).fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithContentDescription("layout").performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.search)).performClick()
            compose.runOnIdle { assertEquals(listOf(1, 1), clicks.toList()) }
        } finally {
            context.preferences.edit().putBoolean(glassEnabledKey, previous).commit()
        }
    }

    @Test fun `visible result controls remain independent when switching between two and three grouped actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        val showToggle = mutableStateOf(false)
        val clicks = IntArray(3)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                SearchResultHeaderActions(showToggle.value, { clicks[0]++ }) {
                    SearchResultHeaderIcon(R.drawable.discover, "filter", { clicks[1]++ }) {}
                    if (showToggle.value) {
                        SearchResultHeaderIcon(R.drawable.sort_grid, "layout", { clicks[2]++ }) {}
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("layout").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.search)).performTouchInput { click() }
        compose.onNodeWithContentDescription("filter").performTouchInput { click() }
        compose.runOnIdle { showToggle.value = true }
        compose.onNodeWithContentDescription("layout").assertWidthIsEqualTo(44.dp).performTouchInput { click() }
        compose.onNodeWithContentDescription("filter").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(1, 2, 1), clicks.toList()) }
    }
}
