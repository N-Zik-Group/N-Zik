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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.preferences
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
                    SearchResultHeaderActions(
                        onEditQuery = {},
                        filter = SearchResultFilterAction(onOpen = {}) {
                            trigger = requireNotNull(LocalGlassMenuTrigger.current)
                        },
                        onToggleLayout = {},
                    )
                }
            }
        }
        val pencil = compose.onNodeWithContentDescription(context.getString(R.string.search_action_edit_query))
        val layout = compose.onNodeWithContentDescription(context.getString(R.string.accessibility_switch_mode))
        pencil.assertWidthIsEqualTo(36.dp)
        compose.onNodeWithContentDescription(context.getString(R.string.accessibility_filter)).assertWidthIsEqualTo(36.dp)
        layout.assertWidthIsEqualTo(36.dp)
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filterLabel = context.getString(R.string.accessibility_filter)
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
                    SearchResultHeaderActions(
                        onEditQuery = {},
                        filter = SearchResultFilterAction(onOpen = { expanded.value = true }) {
                            GlassDropdownMenu(expanded.value, { expanded.value = false }, Color.DarkGray, CircleShape) {
                                DropdownMenuItem(text = { Text("Native filter") }, onClick = {
                                    selected = true
                                    expanded.value = false
                                })
                            }
                        },
                    )
                    GlassOverlayHost(host)
                }
            }
        }
        val initialBounds = compose.onNodeWithContentDescription(filterLabel).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(filterLabel).performClick()
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
        compose.onNodeWithContentDescription(filterLabel).assertDoesNotExist()
        compose.onNodeWithText("Native filter").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(selected)
            assertNull(host.entry)
        }
        assertEquals(initialBounds, compose.onNodeWithContentDescription(filterLabel).fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `globe transfers into its dropdown and three action capsule rejoins after closing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filterLabel = context.getString(R.string.accessibility_filter)
        val editLabel = context.getString(R.string.search_action_edit_query)
        val layoutLabel = context.getString(R.string.accessibility_switch_mode)
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
                            SearchResultHeaderActions(
                                onEditQuery = { clicks[0]++ },
                                filter = SearchResultFilterAction(onOpen = { expanded.value = true }) {
                                    GlassDropdownMenu(expanded.value, { expanded.value = false }, Color.Transparent, CircleShape) {
                                        DropdownMenuItem(text = { Text("Choose filter") }, onClick = {
                                            selected++
                                            expanded.value = false
                                        })
                                    }
                                },
                                onToggleLayout = { clicks[1]++ },
                            )
                        }
                        GlassOverlayHost(host)
                    }
                }
            }
            val globe = compose.onNodeWithContentDescription(filterLabel).fetchSemanticsNode().boundsInRoot
            val layout = compose.onNodeWithContentDescription(layoutLabel).fetchSemanticsNode().boundsInRoot
            val pencil = compose.onNodeWithContentDescription(editLabel).fetchSemanticsNode().boundsInRoot
            compose.onNodeWithContentDescription(filterLabel).performClick()
            compose.waitForIdle()
            compose.runOnIdle {
                assertNotNull(host.entry?.trigger)
                assertEquals(globe, host.entry?.anchor)
                assertEquals(globe, host.entry?.trigger?.bounds)
            }
            compose.onNodeWithContentDescription(filterLabel).assertDoesNotExist()
            compose.onNodeWithText("Choose filter").performClick()
            compose.waitForIdle()
            compose.runOnIdle {
                assertNull(host.entry)
                assertEquals(1, selected)
            }
            assertEquals(globe, compose.onNodeWithContentDescription(filterLabel).fetchSemanticsNode().boundsInRoot)
            assertEquals(layout, compose.onNodeWithContentDescription(layoutLabel).fetchSemanticsNode().boundsInRoot)
            assertEquals(pencil, compose.onNodeWithContentDescription(editLabel).fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithContentDescription(layoutLabel).performClick()
            compose.onNodeWithContentDescription(editLabel).performClick()
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
                SearchResultHeaderActions(
                    onEditQuery = { clicks[0]++ },
                    filter = SearchResultFilterAction(onOpen = { clicks[1]++ }) {},
                    onToggleLayout = if (showToggle.value) ({ clicks[2]++ }) else null,
                )
            }
        }
        val layout = compose.onNodeWithContentDescription(context.getString(R.string.accessibility_switch_mode))
        val filter = compose.onNodeWithContentDescription(context.getString(R.string.accessibility_filter))
        layout.assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.search_action_edit_query)).performTouchInput { click() }
        filter.performTouchInput { click() }
        compose.runOnIdle { showToggle.value = true }
        layout.assertWidthIsEqualTo(36.dp).performTouchInput { click() }
        filter.performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(1, 2, 1), clicks.toList()) }
    }

    @Test fun `search headers without a filter only reserve the visible seats in both modes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val glassEnabled = mutableStateOf(false)
        val showToggle = mutableStateOf(false)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(enabled = glassEnabled.value),
            ) {
                SearchResultHeaderActions(
                    onEditQuery = {},
                    onToggleLayout = if (showToggle.value) ({}) else null,
                    modifier = Modifier.testTag("search-result-actions"),
                )
            }
        }

        val header = compose.onNodeWithTag("search-result-actions")
        val layout = compose.onNodeWithContentDescription(context.getString(R.string.accessibility_switch_mode))
        val edit = compose.onNodeWithContentDescription(context.getString(R.string.search_action_edit_query))
        listOf(false, true).forEach { glass ->
            compose.runOnIdle {
                glassEnabled.value = glass
                showToggle.value = false
            }
            header.assertWidthIsEqualTo(36.dp)
            layout.assertDoesNotExist()

            compose.runOnIdle { showToggle.value = true }
            header.assertWidthIsEqualTo(72.dp)
            val editBounds = edit.fetchSemanticsNode().boundsInRoot
            val layoutBounds = layout.fetchSemanticsNode().boundsInRoot
            assertEquals(editBounds.right, layoutBounds.left, .5f)
        }
    }
}
