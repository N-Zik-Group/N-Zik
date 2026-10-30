package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
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
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassButtonScrollOwnershipTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var vertical: LazyListState
    private lateinit var horizontal: ScrollState
    private val settings = GlassSettings(quality = GlassQuality.Performance)

    @Test fun `liquid drag stays owned after leaving bounds and outside gestures scroll both axes`() {
        setScrollableContent {
            LiquidButton({}, null, Modifier.testTag("button").width(80.dp), settings = settings) {}
        }
        dragButtonAndAssertParentsStill()
        assertOutsideGesturesScroll()
    }

    @Test fun `liquid ownership also works with motion disabled`() {
        setScrollableContent {
            LiquidButton({}, null, Modifier.testTag("button").width(80.dp),
                settings = settings, isInteractive = false) {}
        }
        dragButtonAndAssertParentsStill()
        assertOutsideGesturesScroll()
    }

    @Test fun `grouped action owns drag and releases ownership for the next gesture`() {
        setScrollableContent {
            GlassActionGroup(settings, GlassArea.ActionButtons) {
                GlassActionGroupItem({}, Modifier.testTag("button")) {}
                GlassActionGroupItem({}) {}
            }
        }
        dragButtonAndAssertParentsStill()
        assertOutsideGesturesScroll()
    }

    @Test fun `enabled toolbar cell retains drag while a disabled cell allows scrolling`() {
        setScrollableContent {
            GlassToolbarRow(2, settings = settings) {
                GlassToolbarIcon(painterResource(R.drawable.play), Color.White, 32.dp,
                    true, onClick = {}, contentDescription = "enabled")
                GlassToolbarIcon(painterResource(R.drawable.play), Color.White, 32.dp,
                    false, onClick = {}, contentDescription = "disabled")
            }
        }
        compose.onNodeWithContentDescription("enabled").performTouchInput {
            swipe(center, center + Offset(-180f, -180f), 400)
        }
        assertParentsStill()
        compose.onNodeWithContentDescription("disabled").performTouchInput {
            swipe(center, center + Offset(0f, -180f), 400)
        }
        compose.runOnIdle { assertTrue(vertical.firstVisibleItemIndex > 0 || vertical.firstVisibleItemScrollOffset > 0) }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test fun `native short long and cancelled clicks survive and highlight tracks an owned drag until release`() {
        var clicks = 0
        var holds = 0
        lateinit var highlight: InteractiveHighlight
        setScrollableContent {
            val scope = rememberCoroutineScope()
            highlight = remember(scope) { InteractiveHighlight(scope) }
            Box(Modifier.size(80.dp).testTag("button").retainGlassButtonGesture()
                .combinedClickable(onClick = { clicks++ }, onLongClick = { holds++ })
                .then(highlight.modifier).then(highlight.gestureModifier))
        }
        compose.onNodeWithTag("button").performTouchInput { click() }
        compose.onNodeWithTag("button").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, clicks); assertEquals(1, holds) }
        compose.onNodeWithTag("button").performTouchInput {
            down(center)
            moveTo(center + Offset(-180f, -180f), 200)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertTrue(highlight.pressProgress > .5f); assertTrue(highlight.offset.getDistance() > 100f) }
        assertParentsStill()
        compose.onNodeWithTag("button").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2000)
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(1, holds)
            assertEquals(0f, highlight.pressProgress, .01f)
            assertEquals(0f, highlight.offset.getDistance(), 1f)
        }
        assertOutsideGesturesScroll()
    }

    private fun dragButtonAndAssertParentsStill() {
        compose.onNodeWithTag("button").performTouchInput {
            down(center)
            advanceEventTime(200)
            moveTo(center + Offset(-180f, -180f), 200)
            up()
        }
        assertParentsStill()
    }

    private fun assertParentsStill() = compose.runOnIdle {
        assertEquals(0, horizontal.value)
        assertEquals(0, vertical.firstVisibleItemIndex)
        assertEquals(0, vertical.firstVisibleItemScrollOffset)
    }

    private fun assertOutsideGesturesScroll() {
        compose.onNodeWithTag("horizontal").performTouchInput {
            swipe(Offset(250f, height - 5f), Offset(30f, height - 5f), 400)
        }
        compose.runOnIdle { assertTrue(horizontal.value > 0) }
        compose.onNodeWithTag("vertical").performTouchInput {
            swipe(Offset(width - 5f, height - 20f), Offset(width - 5f, 20f), 400)
        }
        compose.runOnIdle { assertTrue(vertical.firstVisibleItemIndex > 0 || vertical.firstVisibleItemScrollOffset > 0) }
    }

    private fun setScrollableContent(button: @Composable () -> Unit) = compose.setContent {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )) {
            vertical = rememberLazyListState()
            horizontal = rememberScrollState()
            LazyColumn(Modifier.size(300.dp, 320.dp).testTag("vertical"), state = vertical) {
                item {
                    Row(Modifier.width(300.dp).height(144.dp).testTag("horizontal").horizontalScroll(horizontal),
                        verticalAlignment = Alignment.CenterVertically) {
                        button()
                        Box(Modifier.width(1000.dp))
                    }
                }
                items(30) { Box(Modifier.size(300.dp, 80.dp)) }
            }
        }
    }
}
