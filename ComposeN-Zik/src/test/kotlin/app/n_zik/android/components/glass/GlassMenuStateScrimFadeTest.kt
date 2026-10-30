package app.n_zik.android.components.glass

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.menu.ListMenu
import app.n_zik.android.components.theme.AppearanceMenuSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMenuStateScrimFadeTest {
    @get:Rule val compose = createComposeRule()

    private fun openSortingMenu(): Pair<GlassMenuHostState, MenuState> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        GlassStore(context).setProfile(GlassQuality.Balanced)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val host = GlassMenuHostState()
        val menu = MenuState()
        val shape = RoundedCornerShape(16.dp)
        compose.setContent {
            val white = rememberCanvasBackdrop { drawRect(Color.White) }
            SideEffect { host.backdrop = white }
            CompositionLocalProvider(LocalGlassMenuHost provides host, LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.size(360.dp, 640.dp).background(Color.White).testTag("scene")) {
                    AppearanceMenuSheet(menu.isDisplayed, menu::hide,
                        rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
                        AnimatedContent(menu.contentState, label = "MenuContentTransition") { target -> target.second() }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        compose.runOnIdle {
            menu.display {
                ListMenu.Menu(title = "Sorting order") {
                    ListMenu.Entry(text = "By most played song", icon = {}, onClick = menu::hide)
                }
            }
        }
        compose.waitForIdle()
        assertEquals(.25f, pageBrightness(), .02f)
        return host to menu
    }

    private fun pageBrightness(): Float =
        compose.onNodeWithTag("scene").captureToImage().toPixelMap()[2, 2].red

    private fun advance(time: Long) {
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(time)
        compose.waitForIdle()
    }

    @Test fun `real sorting menu row fades black while MenuState is already hidden`() {
        val (host, menu) = openSortingMenu()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("By most played song").performClick()
        advance(80)
        assertFalse(menu.isDisplayed)
        assertTrue("closing menu retains its overlay", host.dialogs.hasDialogs)
        val brightness = pageBrightness()
        assertTrue("black fades during the actual sheet hide: $brightness", brightness > .27f && brightness < .98f)
        advance(500)
        assertFalse(host.hasOverlays)
        assertEquals(1f, pageBrightness(), .02f)
    }

    @Test fun `reopening MenuState during hide cancels pending overlay disposal`() {
        val (host, menu) = openSortingMenu()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("By most played song").performClick()
        advance(80)
        compose.runOnIdle {
            menu.display {
                ListMenu.Menu(title = "Sorting order reopened") {
                    ListMenu.Entry(text = "Close reopened menu", icon = {}, onClick = menu::hide)
                }
            }
        }
        advance(500)
        assertTrue(menu.isDisplayed)
        assertTrue(host.dialogs.hasDialogs)
        assertEquals(.25f, pageBrightness(), .02f)
        compose.onNodeWithText("Close reopened menu").performClick()
        advance(500)
        assertFalse(host.hasOverlays)
        assertEquals(1f, pageBrightness(), .02f)
    }
}
