package app.n_zik.android.components.navigation.dock

import android.app.Application
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class ClassicBottomDockNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeClassicKeepsFullWidthAndSystemAreaThroughoutFold() = verifyFold(glass = false, row = 54)
    @Test fun glassClassicKeepsOriginalRowAndHomeUnfolds() = verifyFold(glass = true, row = 64)
    @Test fun nativeClassicWithoutBottomMiniKeepsTabsInteractive() = verifyFold(glass = false, row = 54, bottomMini = false)
    @Test fun glassClassicWithoutBottomMiniKeepsTabsInteractive() = verifyFold(glass = true, row = 64, bottomMini = false)

    @Test fun nativeTopSharesItsRowWithAttachedTopMini() = verifyFold(glass = false, row = 59, bottomMini = false, top = true, attachedMini = true)
    @Test fun glassTopSharesItsRowWithAttachedTopMini() = verifyFold(glass = true, row = 64, bottomMini = false, top = true, attachedMini = true)
    @Test fun nativeTopKeepsTabsWhenMiniIsAtBottom() = verifyFold(glass = false, row = 59, bottomMini = true, top = true, attachedMini = false)
    @Test fun glassTopKeepsTabsWhenMiniIsAtBottom() = verifyFold(glass = true, row = 64, bottomMini = true, top = true, attachedMini = false)

    private fun verifyFold(glass: Boolean, row: Int, bottomMini: Boolean = true, top: Boolean = false, attachedMini: Boolean = bottomMini) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        setActiveProfile("default", context)
        context.preferences.edit().clear().putBoolean(glassEnabledKey, glass).commit()
        val clock = mutableStateOf(0f)
        val state = FloatingDockState()
        val bottomPadding = if (top) 0 else 24
        var mounts = 0
        var disposals = 0
        var tabTaps = 0
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.width(360.dp).testTag("classic")) {
                    ClassicBottomDockNavigation(
                        dock = FloatingDockEnvironment(state, "home", true, clock, bottomMini, attachedMini = attachedMini),
                        rowHeight = row.dp,
                        bottomPadding = bottomPadding.dp,
                        top = top,
                        tabsCount = 4,
                        selectedIndex = 0,
                    ) { modifier, _ ->
                        DisposableEffect(Unit) {
                            mounts++
                            onDispose { disposals++ }
                        }
                        Box(modifier.testTag("tabs").clickable { tabTaps++ })
                    }
                }
            }
        }
        // No invisible Home node may intercept the selected tab at rest.
        compose.onNodeWithTag("tabs").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, tabTaps) }
        listOf(.25f, .75f, 1f).forEach { p ->
            compose.runOnIdle { state.foldTarget = 1f; clock.value = p }
            compose.onNodeWithTag("classic").assertWidthIsEqualTo(360.dp).assertHeightIsEqualTo((row + bottomPadding).dp)
            compose.runOnIdle { assertEquals(1, mounts); assertEquals(0, disposals) }
        }
        val home = compose.onNodeWithContentDescription(context.getString(R.string.home))
        if (!attachedMini) {
            home.assertDoesNotExist()
            compose.onNodeWithTag("tabs").performTouchInput { click(center) }
            compose.runOnIdle {
                assertEquals(2, tabTaps)
                assertEquals(1f, clock.value)
                assertEquals(1, mounts)
                assertEquals(0, disposals)
            }
            return
        }
        home.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        val homeBounds = home.fetchSemanticsNode().boundsInRoot
        val host = compose.onNodeWithTag("classic").fetchSemanticsNode().boundsInRoot
        val pixelPerDp = host.width / 360f
        assertEquals(host.left + 16f * pixelPerDp, homeBounds.left, 1f)
        assertEquals(host.top + (row - 48) / 2f * pixelPerDp, homeBounds.top, 1f)
        home.performClick()
        compose.runOnIdle { assertEquals(0f, state.foldTarget); clock.value = 0f }
        compose.onNodeWithTag("tabs").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(2, tabTaps); assertEquals(1, mounts); assertEquals(0, disposals) }
    }
}
