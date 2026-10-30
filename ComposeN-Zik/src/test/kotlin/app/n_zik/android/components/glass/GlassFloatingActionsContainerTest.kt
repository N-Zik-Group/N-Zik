package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.components.themed.FloatingActions
import app.it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.ScrollingInfo
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.floatActionIconOffsetXkey
import app.it.fast4x.rimusic.utils.floatActionIconOffsetYkey
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetXkey
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetYkey
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.R
import app.n_zik.android.components.navigation.FloatingActionSiblingSpace
import app.n_zik.android.components.navigation.movableFloatingAction
import app.n_zik.android.utils.toast.trackBottomNavigationForToasts
import app.n_zik.android.utils.toast.trackToastObstruction
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w420dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassFloatingActionsContainerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real floating containers keep visible glyphs and callbacks through glass and native modes`() {
        val context = RuntimeEnvironment.getApplication()
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        GlassStore(context).setAreaMode(GlassArea.ActionButtons, GlassAreaMode.GlassAndContour)
        val search = mutableStateOf(false)
        var scrollClicks = 0
        var searchClicks = 0
        var measuredPadding = 0.dp
        val sheet = mockk<PlayerSheetState> { every { isVisible } returns false }
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        val scrolling = MutableTransitionState<ScrollingInfo?>(ScrollingInfo(isScrollingDown = false, isFar = true))
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance, LocalPlayerSheetState provides sheet,
                // The mini-player starts at y=400 in this 640dp content viewport.
                LocalPlayerAwareWindowInsets provides WindowInsets(bottom = 240.dp),
            ) {
                val backdrop = rememberGlassFloatingActionBackdrop()
                Box(Modifier.size(360.dp, 640.dp).testTag("page")) {
                    Box(Modifier.fillMaxSize().glassFloatingActionContent(backdrop).background(Color.Black))
                    GlassFloatingActions(backdrop) {
                        measuredPadding = glassFloatingActionsBottomPadding(124.dp, WindowInsets(0, 0, 0, 0))
                        if (search.value) {
                            MultiFloatingActionsContainer(
                                modifier = Modifier.testTag("actions"), iconId = R.drawable.search,
                                onClick = { searchClicks++ },
                            )
                        } else {
                            FloatingActions(
                                transitionState = scrolling, windowInsets = LocalPlayerAwareWindowInsets.current,
                                modifier = Modifier.testTag("actions"), onScrollToTop = { scrollClicks++ },
                            )
                        }
                    }
                    Box(Modifier.offset(y = 400.dp).size(360.dp, 80.dp)
                        .background(Color.DarkGray).trackToastObstruction().testTag("mini"))
                    Box(Modifier.offset(y = 560.dp).size(360.dp, 80.dp)
                        .background(Color.DarkGray).trackBottomNavigationForToasts(true, 64.dp))
                }
            }
        }
        fun assertGlyphAndClick() {
            val actions = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("actions")))
            val action = actions[actions.fetchSemanticsNodes().lastIndex]
            action.assertIsDisplayed()
            if (context.preferences.getBoolean(glassEnabledKey, false)) {
                val miniTop = compose.onNode(hasTestTag("mini")).fetchSemanticsNode().boundsInRoot.top
                assertTrue("Measured chrome must raise the 124dp native padding to at least 240dp",
                    measuredPadding >= 240.dp)
                assertEquals("Padding uses the 640dp content parent rather than the larger window", 248.dp, measuredPadding)
                assertTrue("Floating glyph must clear the measured mini-player body",
                    action.fetchSemanticsNode().boundsInRoot.bottom <= miniTop + 1f)
            }
            val pixels = action.captureToImage().toPixelMap()
            var brightPixels = 0
            for (y in 0 until pixels.height) {
                for (x in 0 until pixels.width) {
                    val pixel = pixels[x, y]
                    if (pixel.red > .7f && pixel.green > .7f && pixel.blue > .7f) brightPixels++
                }
            }
            assertTrue("The actual floating glyph must remain drawn", brightPixels > 10)
            action.performClick()
        }
        assertGlyphAndClick()
        compose.runOnIdle { assertEquals(1, scrollClicks); search.value = true }
        assertGlyphAndClick()
        compose.runOnIdle {
            assertEquals(1, searchClicks)
            context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        }
        assertGlyphAndClick()
        compose.runOnIdle { assertEquals(2, searchClicks); search.value = false }
        assertGlyphAndClick()
        compose.runOnIdle { assertEquals(2, scrollClicks) }
    }

    @Test fun `native floating padding also clears the modern content reserve`() {
        var padding = 0.dp
        compose.setContent {
            padding = glassFloatingActionsBottomPadding(
                nativePadding = 105.dp, windowInsets = WindowInsets(bottom = 200.dp),
            )
        }
        compose.runOnIdle { assertEquals(200.dp, padding) }
    }

    @Test fun `default sibling actions clear page reserves and each other in native and glass modes`() {
        val context = RuntimeEnvironment.getApplication()
        context.preferences.edit()
            .putBoolean(glassEnabledKey, false)
            .putFloat(floatActionIconOffsetXkey, 0f).putFloat(floatActionIconOffsetYkey, 0f)
            .putFloat(multiFloatActionIconOffsetXkey, 0f).putFloat(multiFloatActionIconOffsetYkey, 0f)
            .commit()
        GlassStore(context).setAreaMode(GlassArea.ActionButtons, GlassAreaMode.GlassAndContour)
        val insets = mutableStateOf(WindowInsets(left = 0.dp, right = 72.dp, bottom = 180.dp))
        val pageHeight = mutableStateOf(640.dp)
        val scrolling = MutableTransitionState<ScrollingInfo?>(ScrollingInfo(isScrollingDown = false, isFar = true))
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape, uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalPlayerAwareWindowInsets provides insets.value) {
                Box(Modifier.size(360.dp, pageHeight.value).testTag("page")) {
                    FloatingActions(
                        transitionState = scrolling, windowInsets = insets.value,
                        modifier = Modifier.testTag("up"), onScrollToTop = {}, reservedEndSpace = FloatingActionSiblingSpace,
                    )
                    MultiFloatingActionsContainer(
                        modifier = Modifier.testTag("search"), iconId = R.drawable.search, onClick = {},
                    )
                }
            }
        }
        fun bounds(tag: String) = compose.onAllNodes(
            hasClickAction() and hasAnyAncestor(hasTestTag(tag)),
        ).let { it[it.fetchSemanticsNodes().lastIndex].fetchSemanticsNode().boundsInRoot }
        fun assertPlacement(rightReserve: Float, bottomReserve: Float) {
            val page = compose.onNode(hasTestTag("page")).fetchSemanticsNode().boundsInRoot
            val scale = page.width / 360f
            val search = bounds("search")
            val up = bounds("up")
            assertTrue("Search must clear the page's right rail", search.right <= page.right - rightReserve * scale)
            assertTrue("Search must clear bottom mini and navigation", search.bottom <= page.bottom - bottomReserve * scale)
            assertTrue("Default up and search actions must not overlap", up.right + 8f * scale <= search.left)
        }
        assertPlacement(72f, 180f)
        compose.runOnIdle {
            context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        }
        assertPlacement(72f, 180f)
        compose.runOnIdle {
            // Top mini-player or no media contributes no bottom mini-player reserve.
            insets.value = WindowInsets(left = 72.dp, right = 0.dp, bottom = 24.dp)
            pageHeight.value = 320.dp
        }
        assertPlacement(0f, 24f)
    }

    @Test fun `unreachable saved offsets clamp the rendered button without overwriting preferences`() {
        val savedX = mutableStateOf(-20f)
        val savedY = mutableStateOf(-80f)
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("window")) {
                Box(Modifier.align(Alignment.BottomEnd).movableFloatingAction(savedX, savedY)
                    .size(64.dp).testTag("movable"))
            }
        }
        val window = compose.onNode(hasTestTag("window")).fetchSemanticsNode().boundsInRoot
        val initial = compose.onNode(hasTestTag("movable")).fetchSemanticsNode().boundsInRoot
        assertEquals(window.right - 20f, initial.right, 1f)
        assertEquals(window.bottom - 80f, initial.bottom, 1f)
        compose.runOnIdle { savedX.value = -10000f; savedY.value = 10000f }
        val clamped = compose.onNode(hasTestTag("movable")).fetchSemanticsNode().boundsInRoot
        assertTrue(clamped.left >= window.left && clamped.right <= window.right)
        assertTrue(clamped.top >= window.top && clamped.bottom <= window.bottom)
        compose.runOnIdle {
            assertEquals(-10000f, savedX.value, 0f)
            assertEquals(10000f, savedY.value, 0f)
        }
    }
}
