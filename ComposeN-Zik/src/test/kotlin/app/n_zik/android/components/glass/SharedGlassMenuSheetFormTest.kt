package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import app.it.fast4x.rimusic.utils.preferences
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.menu.GlassDropdownMenu
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SharedGlassMenuSheetFormTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `sheet veto of Hidden keeps visible modal and scrim until a later accepted dismissal`() {
        val host = GlassMenuHostState()
        var acceptHidden = false
        var dismissals = 0
        var visible by mutableStateOf(true)
        lateinit var sheet: SheetState
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host, LocalAppearance provides appearance) {
                Box {
                    sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
                        confirmValueChange = { it != SheetValue.Hidden || acceptHidden })
                    SharedGlassMenuSheet(visible, { dismissals++; visible = false }, sheet) {
                        Box(Modifier.fillMaxWidth().height(240.dp).testTag("veto-sheet"))
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("glassDialogOverlay").performTouchInput { click(topLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("veto-sheet").assertExists()
        composeRule.onNodeWithTag("glassDialogScrim", useUnmergedTree = true).assertExists()
        composeRule.runOnIdle {
            assertTrue(sheet.isVisible)
            assertEquals(0, dismissals)
            assertTrue(host.dialogs.entries.single().scrimProgress() > .99f)
            acceptHidden = true
        }
        composeRule.onNodeWithTag("glassDialogOverlay").performTouchInput { click(topLeft) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, dismissals)
            assertFalse(host.hasOverlays)
        }
    }

    @Test
    fun `dropdown action starts real sheet without fading it behind the retained closing dropdown`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val previous = context.preferences.getBoolean(glassEnabledKey, false)
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        try {
            val host = GlassMenuHostState()
            var expanded by mutableStateOf(true)
            var visible by mutableStateOf(false)
            var actions = 0
            var dismissals = 0
            lateinit var sheet: SheetState
            val shape = RoundedCornerShape(24.dp)
            val appearance = Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )
            composeRule.setContent {
                CompositionLocalProvider(LocalGlassMenuHost provides host, LocalAppearance provides appearance) {
                    Box {
                        GlassModalContent(host) {
                            GlassDropdownMenu(expanded, { expanded = false }, Color.Transparent, shape) {
                                DropdownMenuItem(text = { Text("Open real sheet") }, onClick = {
                                    actions++
                                    expanded = false
                                    visible = true
                                })
                            }
                            sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                            SharedGlassMenuSheet(visible, { dismissals++; visible = false }, sheet) {
                                Box(Modifier.fillMaxWidth().height(240.dp).testTag("replacement-sheet"))
                            }
                        }
                        GlassDialogOverlayHost(host)
                        GlassOverlayHost(host)
                    }
                }
            }
            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = false
            composeRule.onNodeWithText("Open real sheet").performClick()
            composeRule.runOnIdle {
                assertEquals("the real dropdown action runs exactly once", 1, actions)
                Snapshot.sendApplyNotifications()
            }
            repeat(8) {
                composeRule.mainClock.advanceTimeBy(32)
                composeRule.waitForIdle()
                composeRule.runOnIdle {
                    assertEquals(1, host.dialogs.entries.size)
                    assertEquals("the successor stays exposed throughout dropdown exit", 1f,
                        host.dialogs.entries.single().exposure.value, .001f)
                    assertTrue(host.hasOverlays)
                    assertEquals(1, actions)
                    assertEquals(0, dismissals)
                }
                composeRule.onNodeWithTag("glassDialogScrim", useUnmergedTree = true).assertExists()
            }
            composeRule.runOnIdle {
                assertFalse(requireNotNull(host.entry).visible)
                assertTrue("sheet motion starts before the 380 ms dropdown close finishes", sheet.targetValue != SheetValue.Hidden)
            }
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("glassDialogOverlay").performTouchInput { click(topLeft) }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertEquals(1, dismissals)
                assertFalse(host.hasOverlays)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
            context.preferences.edit().putBoolean(glassEnabledKey, previous).commit()
        }
    }

    @Test
    fun `shared form keeps input above changing IME inset and dismisses once`() {
        val host = GlassMenuHostState()
        var visible by mutableStateOf(true)
        var text by mutableStateOf("")
        var imeBottom by mutableStateOf(0)
        var dismissals = 0
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host, LocalAppearance provides appearance) {
                Box {
                    GlassModalContent(host) {
                        Box(Modifier.testTag("covered-page"))
                        SharedGlassMenuSheet(
                            showSheet = visible,
                            onDismissRequest = { dismissals++; visible = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            contentWindowInsets = { WindowInsets(bottom = imeBottom) },
                        ) {
                            Column(Modifier.fillMaxWidth().height(240.dp).testTag("form")) {
                                BasicTextField(text, { text = it }, Modifier.testTag("input"))
                            }
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("covered-page").assertDoesNotExist()
        val originalBottom = composeRule.onNodeWithTag("form").fetchSemanticsNode().boundsInRoot.bottom
        composeRule.onNodeWithTag("input").performTextInput("room name")
        composeRule.onNodeWithTag("input").assertTextEquals("room name")
        composeRule.runOnIdle { imeBottom = 160 }
        composeRule.waitForIdle()
        val insetBottom = composeRule.onNodeWithTag("form").fetchSemanticsNode().boundsInRoot.bottom
        assertEquals("the form moves above the reserved keyboard area", 160f, originalBottom - insetBottom, 1f)
        composeRule.onNodeWithTag("glassDialogOverlay").performTouchInput { click(topLeft) }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, dismissals)
            assertFalse(host.dialogs.hasDialogs)
        }
        composeRule.onNodeWithTag("covered-page").assertExists()
    }
}
