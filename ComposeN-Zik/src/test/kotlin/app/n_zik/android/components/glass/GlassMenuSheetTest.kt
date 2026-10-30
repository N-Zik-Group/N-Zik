package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class GlassMenuSheetTest {
    @get:Rule val composeRule = createComposeRule()
    private val shape = RoundedCornerShape(24.dp)
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
        thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
    )

    @Test
    fun `shared sheet retains its state and outside dismissal completes before unregistering`() {
        val host = GlassMenuHostState()
        var visible by mutableStateOf(true)
        var dismissals = 0
        lateinit var state: SheetState
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host, LocalAppearance provides appearance) {
                Box {
                    state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                    GlassModalContent(host) {
                        Box(Modifier.testTag("covered-page"))
                        GlassMenuSheet(visible, { dismissals++; visible = false }, state) {
                            Box(Modifier.fillMaxWidth().height(500.dp).testTag("sheet-content"))
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(state.isVisible)
            assertTrue(host.dialogs.hasDialogs)
        }
        composeRule.onNodeWithTag("covered-page").assertDoesNotExist()
        composeRule.onNodeWithTag("glassDialogOverlay").performTouchInput { click(topLeft) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, dismissals)
            assertFalse(state.isVisible)
            assertFalse(host.dialogs.hasDialogs)
        }
        composeRule.onNodeWithTag("covered-page").assertExists()
    }

    @Test
    fun `owner driven hide unregisters without another dismissal callback`() {
        val host = GlassMenuHostState()
        var visible by mutableStateOf(true)
        var dismissals = 0
        lateinit var state: SheetState
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host, LocalAppearance provides appearance) {
                Box {
                    state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                    GlassMenuSheet(visible, { dismissals++ }, state) {
                        Box(Modifier.fillMaxWidth().height(500.dp).testTag("sheet-content"))
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(state.isVisible)
            visible = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0, dismissals)
            assertFalse(state.isVisible)
            assertFalse(host.dialogs.hasDialogs)
        }
    }

    @Test
    fun `swiping the shared sheet down dismisses its owner once`() {
        val host = GlassMenuHostState()
        var visible by mutableStateOf(true)
        var dismissals = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host, LocalAppearance provides appearance) {
                Box {
                    GlassMenuSheet(visible, { dismissals++; visible = false },
                        rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
                        Box(Modifier.fillMaxWidth().height(500.dp).testTag("sheet-content"))
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sheet-content").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, dismissals)
            assertFalse(host.dialogs.hasDialogs)
        }
    }
}
