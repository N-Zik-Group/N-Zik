package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
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
class GlassToolbarButtonsTest {
    @get:Rule val compose = createComposeRule()
    private val settings = GlassSettings(quality = GlassQuality.Performance)

    @Test fun `long icon overloads retain caller modifier on action container in native and Glass rows`() {
        val glass = mutableStateOf(false)
        var clicks = 0
        var holds = 0
        compose.setContent {
            AppearanceProvider {
                GlassToolbarRow(2, Modifier.testTag("long.row"), settings.copy(enabled = glass.value)) {
                    TabToolBar.Icon(painterResource(R.drawable.play),
                        modifier = Modifier.size(36.dp).align(Alignment.Bottom).padding(2.dp).testTag("long.painter"),
                        onClick = { clicks++ }, onLongClick = { holds++ })
                    TabToolBar.Icon(iconId = R.drawable.play,
                        modifier = Modifier.size(40.dp).align(Alignment.Bottom).padding(2.dp).testTag("long.resource"),
                        onShortClick = { clicks++ }, onLongClick = { holds++ })
                }
            }
        }
        listOf(false, true).forEach { enabled ->
            compose.runOnIdle { glass.value = enabled }
            listOf("long.painter", "long.resource").forEach { tag ->
                val action = compose.onNodeWithTag(tag, useUnmergedTree = true)
                assertTrue("$tag must tag the clickable container", hasClickAction().matches(action.fetchSemanticsNode()))
                // Padding remains outside the hit semantics, just as in the native overload.
                val targetSize = if (tag == "long.painter") 32.dp else 36.dp
                action.assertWidthIsEqualTo(targetSize).assertHeightIsEqualTo(targetSize)
                val row = compose.onNodeWithTag("long.row").fetchSemanticsNode().boundsInRoot
                val bounds = action.fetchSemanticsNode().boundsInRoot
                val density = bounds.width / targetSize.value
                assertEquals("Bottom alignment must reach the row edge before padding", row.bottom - 2f * density, bounds.bottom, .5f)
                action.performTouchInput { click() }
                action.performTouchInput { longClick() }
            }
        }
        compose.runOnIdle { assertEquals(4, clicks); assertEquals(4, holds) }
    }

    @Test fun `short icon overload keeps caller tag on glyph in native and Glass rows`() {
        val glass = mutableStateOf(false)
        compose.setContent {
            AppearanceProvider {
                GlassToolbarRow(1, settings = settings.copy(enabled = glass.value)) {
                    TabToolBar.Icon(iconId = R.drawable.play,
                        modifier = Modifier.padding(2.dp).testTag("short.glyph"), onClick = {})
                }
            }
        }
        listOf(false, true).forEach { enabled ->
            compose.runOnIdle { glass.value = enabled }
            assertFalse(hasClickAction().matches(compose.onNodeWithTag("short.glyph", useUnmergedTree = true).fetchSemanticsNode()))
        }
    }

    @Test fun `single uses forty multiple use compact cells and preserve short long disabled callbacks`() {
        val count = mutableStateOf(1)
        val enabled = mutableStateOf(true)
        var clicks = 0
        var holds = 0
        compose.setContent {
            AppearanceProvider {
                GlassToolbarRow(count.value, Modifier.testTag("row"), settings) {
                    repeat(count.value) { index ->
                        GlassToolbarIcon(painterResource(R.drawable.play), Color.White, 32.dp,
                            enabled.value, onClick = { clicks++ }, onLongClick = { holds++ },
                            contentDescription = "action.$index")
                    }
                }
            }
        }
        compose.onNodeWithTag("row").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
        compose.onNodeWithContentDescription("action.0").performTouchInput { click() }
        compose.onNodeWithContentDescription("action.0").performTouchInput { longClick() }
        compose.runOnIdle { count.value = 3 }
        compose.onNodeWithTag("row").assertWidthIsEqualTo(132.dp).assertHeightIsEqualTo(40.dp)
        compose.onNodeWithContentDescription("action.2").performTouchInput { longClick() }
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithContentDescription("action.1").assertIsNotEnabled()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(2, holds) }
    }

    @Test fun `adapter retains custom loading composition and latest callback and label`() {
        val loading = mutableStateOf(false)
        val label = mutableStateOf("before")
        var clicks = 0
        val custom = object : Button {
            @Composable override fun ToolBarButton() {
                if (loading.value) CircularProgressIndicator(Modifier.testTag("loading"))
                else GlassToolbarIcon(painterResource(R.drawable.play), Color.White, 32.dp, true,
                    onClick = { clicks += if (label.value == "after") 10 else 1 },
                    contentDescription = label.value)
            }
        }
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.width(300.dp)) {
                    GlassToolbarButtons(listOf(custom), settings = settings, disableAnimation = true)
                }
            }
        }
        compose.onNodeWithContentDescription("before").performTouchInput { click() }
        compose.runOnIdle { loading.value = true }
        compose.onNodeWithTag("loading").assertExists()
        compose.onNodeWithContentDescription("before").assertDoesNotExist()
        compose.runOnIdle { label.value = "after"; loading.value = false }
        compose.onNodeWithContentDescription("after").performTouchInput { click() }
        compose.runOnIdle { assertEquals(11, clicks) }
    }

    @Test fun `icon hook declines rendering outside scope and in disabled material mode`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        var handled = true
        compose.setContent {
            AppearanceProvider {
                GlassToolbarRow(1, settings = settings.copy(areaModes = mapOf(GlassArea.ActionButtons to mode.value))) {
                    handled = GlassToolbarIcon(painterResource(R.drawable.play), Color.White, 32.dp,
                        true, onClick = {}, contentDescription = "scoped")
                }
            }
        }
        compose.runOnIdle { assertEquals(false, handled); mode.value = GlassAreaMode.ContourOnly }
        compose.runOnIdle { assertEquals(false, handled); mode.value = GlassAreaMode.GlassOnly }
        compose.onNodeWithContentDescription("scoped").assertExists()
        compose.runOnIdle { assertEquals(true, handled) }
    }

    @Test fun `overflow reserves the original last visible slot for ellipsis`() {
        val controls = List(5) { index -> object : Button {
            @Composable override fun ToolBarButton() {
                GlassToolbarIcon(painterResource(R.drawable.play), Color.White, 32.dp, true,
                    onClick = {}, contentDescription = "overflow.$index")
            }
        } }
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.width(200.dp)) {
                    GlassToolbarButtons(controls, settings = settings, disableAnimation = true)
                }
            }
        }
        compose.onNodeWithContentDescription("overflow.0").assertExists()
        compose.onNodeWithContentDescription("overflow.1").assertExists()
        compose.onNodeWithContentDescription("overflow.2").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(3)
    }

    @Test fun `narrow inline toolbar fits every original action without needing a button drag`() {
        var lastClicks = 0
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.width(176.dp)) {
                    GlassToolbarRow(8, Modifier.testTag("narrow.row"), settings) {
                        repeat(8) { index ->
                            GlassToolbarIcon(painterResource(R.drawable.play), Color.White, 32.dp,
                                true, onClick = { if (index == 7) lastClicks++ },
                                contentDescription = "inline.$index")
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("narrow.row").assertWidthIsEqualTo(176.dp).assertHeightIsEqualTo(40.dp)
        repeat(8) { compose.onNodeWithContentDescription("inline.$it").assertWidthIsEqualTo(22.dp) }
        compose.onNodeWithContentDescription("inline.7").performTouchInput { click() }
        compose.onNodeWithTag("narrow.row").assertWidthIsEqualTo(176.dp)
        compose.runOnIdle { assertEquals(1, lastClicks) }
    }

    @Test fun `fresh control instances update callbacks without replaying toolbar transitions`() {
        val generation = mutableStateOf(0)
        var clickedGeneration = -1
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                val value = generation.value
                val controls = List(2) { index -> object : Button {
                    @Composable override fun ToolBarButton() {
                        GlassToolbarIcon(painterResource(R.drawable.play), Color.White, 32.dp, true,
                            onClick = { clickedGeneration = value }, contentDescription = "stable.$index")
                    }
                } }
                Box(Modifier.width(200.dp)) { GlassToolbarButtons(controls, settings = settings) }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        repeat(3) { value ->
            compose.runOnIdle { generation.value = value + 1 }
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(hasClickAction()).assertCountEquals(2)
            compose.onNodeWithContentDescription("stable.0").performTouchInput { click() }
            compose.runOnIdle { assertEquals(value + 1, clickedGeneration) }
        }
    }

    @Test fun `follow and detail toolbar form one centered unit in native and glass modes`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val controls = List(2) { object : Button {
            @Composable override fun ToolBarButton() {
                TabToolBar.Icon(painterResource(R.drawable.play), onClick = {})
            }
        } }
        compose.setContent {
            AppearanceProvider {
                Row(Modifier.width(360.dp).testTag("detail.viewport"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(100.dp, 40.dp).testTag("detail.follow"))
                    GlassToolbarButtons(controls, modifier = Modifier.fillMaxWidth(.8f).testTag("detail.bar"),
                        settings = settings.copy(areaModes = mapOf(GlassArea.ActionButtons to mode.value)))
                }
            }
        }
        GlassAreaMode.entries.forEach { value ->
            compose.runOnIdle { mode.value = value }
            val viewport = compose.onNodeWithTag("detail.viewport").fetchSemanticsNode().boundsInRoot
            val follow = compose.onNodeWithTag("detail.follow").fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("detail.bar").fetchSemanticsNode().boundsInRoot
            assertEquals(viewport.center.x, (follow.left + bar.right) / 2f, .5f)
        }
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
