package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassDialogHostTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `secure policy uses native window and leaves activity flags unchanged`() {
        val host = GlassMenuHostState()
        var policy by mutableStateOf(androidx.compose.ui.window.SecureFlagPolicy.SecureOn)
        var window: android.view.Window? = null
        var activityWindow: android.view.Window? = null
        composeRule.setContent {
            val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
            activityWindow = activity.window
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                GlassDialogWindow(true, {}, DialogProperties(securePolicy = policy)) {
                    val view = androidx.compose.ui.platform.LocalView.current
                    window = (view.parent as androidx.compose.ui.window.DialogWindowProvider).window
                    BasicText("Native secure content")
                }
                GlassDialogOverlayHost(host)
            }
        }
        composeRule.waitForIdle()
        val secure = android.view.WindowManager.LayoutParams.FLAG_SECURE
        composeRule.runOnIdle {
            assertFalse(host.dialogs.hasDialogs)
            assertTrue(requireNotNull(window).attributes.flags and secure != 0)
            assertEquals(0, requireNotNull(activityWindow).attributes.flags and secure)
            policy = androidx.compose.ui.window.SecureFlagPolicy.SecureOff
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertFalse(host.dialogs.hasDialogs)
            assertEquals(0, requireNotNull(window).attributes.flags and secure)
            assertEquals(0, requireNotNull(activityWindow).attributes.flags and secure)
        }
    }

    @Test fun `unsupported rendering or missing backdrop keeps platform dialog`() {
        val enabled = GlassSettings(enabled = true, menus = true)
        assertTrue(canHostGlassDialog(enabled, GlassRenderer.Refraction, true))
        assertTrue(canHostGlassDialog(enabled, GlassRenderer.Blur, true))
        assertFalse(canHostGlassDialog(enabled, GlassRenderer.Tint, true))
        assertFalse(canHostGlassDialog(enabled, GlassRenderer.Refraction, false))
        assertFalse(canHostGlassDialog(enabled.copy(enabled = false), GlassRenderer.Refraction, true))
        assertFalse(canHostGlassDialog(enabled.copy(dialogs = false), GlassRenderer.Refraction, true))
        assertTrue(canHostGlassDialog(enabled.copy(menus = false), GlassRenderer.Refraction, true))
        assertTrue(canHostGlassMenuSheet(enabled.copy(dialogs = false), GlassRenderer.Refraction, true))
        assertFalse(canHostGlassMenuSheet(enabled.copy(menus = false), GlassRenderer.Refraction, true))
        assertFalse(canHostGlassMenuSheet(enabled.copy(enabled = false), GlassRenderer.Refraction, true))
        assertFalse(canHostGlassMenuSheet(enabled, GlassRenderer.Tint, true))
        assertFalse(canHostGlassMenuSheet(enabled, GlassRenderer.Refraction, false))
    }

    @Test fun `shared dialog accepts text while covered page is inaccessible and outside dismisses once`() {
        val host = GlassMenuHostState()
        var visible by mutableStateOf(true)
        var text by mutableStateOf("")
        var dismissals = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box {
                    GlassModalContent(host) {
                        BasicText("Covered page")
                        if (visible) GlassDialogWindow(true, { dismissals++; visible = false }, DialogProperties()) {
                            BasicTextField(text, { text = it }, Modifier.size(180.dp, 60.dp).testTag("input"))
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.onNodeWithText("Covered page").assertDoesNotExist()
        composeRule.onNodeWithTag("input").performTextInput("Glass input")
        composeRule.onNodeWithTag("input").assertTextEquals("Glass input")
        composeRule.onNodeWithTag("glassDialogOverlay").performTouchInput { click(topLeft) }
        composeRule.onNodeWithTag("input").assertDoesNotExist()
        composeRule.onNodeWithText("Covered page").assertExists()
        assertEquals(1, dismissals)
        assertFalse(host.dialogs.hasDialogs)
    }

    @Test fun `outside policy and owner disposal preserve dialog lifetime`() {
        val host = GlassMenuHostState()
        var mounted by mutableStateOf(true)
        var dismissals = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box {
                    if (mounted) GlassDialogWindow(true, { dismissals++ },
                        DialogProperties(dismissOnClickOutside = false)) {
                        BasicText("Modal", Modifier.testTag("modal"))
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.onNodeWithTag("glassDialogOverlay").performTouchInput { click(topLeft) }
        composeRule.onNodeWithTag("modal").assertExists()
        assertEquals(0, dismissals)
        composeRule.runOnIdle { mounted = false }
        composeRule.onNodeWithTag("glassDialogOverlay").assertDoesNotExist()
        assertFalse(host.dialogs.hasDialogs)
    }

    @Test fun `missing shared host composes fallback content`() {
        composeRule.setContent {
            GlassDialogWindow(true, {}, DialogProperties()) { BasicText("Fallback") }
        }
        composeRule.onNodeWithText("Fallback").assertExists()
        composeRule.onNodeWithTag("glassDialogOverlay").assertDoesNotExist()
    }

    @Test fun `closing nested dialog reveals retained parent content`() {
        val host = GlassMenuHostState()
        var childVisible by mutableStateOf(true)
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box {
                    GlassDialogWindow(true, {}, DialogProperties()) {
                        BasicText("Parent", Modifier.testTag("parent"))
                        if (childVisible) GlassDialogWindow(true, { childVisible = false }, DialogProperties()) {
                            BasicText("Child", Modifier.testTag("child"))
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.onNodeWithTag("child").assertExists()
        composeRule.onNodeWithTag("parent").assertDoesNotExist()
        assertEquals(2, host.dialogs.entries.size)
        composeRule.runOnIdle { childVisible = false }
        composeRule.onNodeWithTag("child").assertDoesNotExist()
        composeRule.onNodeWithTag("parent").assertExists()
        assertEquals(1, host.dialogs.entries.size)
    }

    @Test fun `dismissal never invents focus or focuses a search field on a new page`() {
        val host = GlassMenuHostState()
        var visible by mutableStateOf(false)
        var session by mutableStateOf("home")
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box {
                    GlassModalContent(host, focusSessionKey = session) {
                        key(session) { BasicTextField("", {}, Modifier.testTag("search.$session")) }
                        if (visible) GlassDialogWindow(true, {}, DialogProperties()) { BasicText("Modal") }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.onNodeWithTag("search.home").assertIsNotFocused()
        composeRule.runOnIdle { visible = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible = false }
        composeRule.onNodeWithTag("search.home").assertIsNotFocused()
        composeRule.onNodeWithTag("search.home").performClick().assertIsFocused()
        composeRule.runOnIdle { visible = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle { session = "settings"; visible = false }
        composeRule.onNodeWithTag("search.settings").assertIsNotFocused()
    }

    @Test fun `nested and final dismissal restore the previously focused input`() {
        val host = GlassMenuHostState()
        var parentVisible by mutableStateOf(false)
        var childVisible by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box {
                    GlassModalContent(host) {
                        BasicTextField("Page", {}, Modifier.testTag("pageInput"))
                        if (parentVisible) GlassDialogWindow(true, { parentVisible = false }, DialogProperties()) {
                            BasicTextField("Parent", {}, Modifier.testTag("parentInput"))
                            if (childVisible) GlassDialogWindow(true, { childVisible = false }, DialogProperties()) {
                                BasicTextField("Child", {}, Modifier.testTag("childInput"))
                            }
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.onNodeWithTag("pageInput").performClick().assertIsFocused()
        composeRule.runOnIdle { parentVisible = true }
        composeRule.onNodeWithTag("parentInput").performClick().assertIsFocused()
        composeRule.runOnIdle { childVisible = true }
        composeRule.onNodeWithTag("childInput").performClick().assertIsFocused()
        composeRule.runOnIdle { childVisible = false }
        composeRule.onNodeWithTag("parentInput").assertIsFocused()
        composeRule.runOnIdle { parentVisible = false }
        composeRule.onNodeWithTag("pageInput").assertIsFocused()
    }

    @Test fun `nested return animates retained parent without exposing covered page input`() {
        val host = GlassMenuHostState()
        var childVisible by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box {
                    GlassModalContent(host) {
                        BasicTextField("", {}, Modifier.testTag("search"))
                        GlassDialogWindow(true, {}, DialogProperties()) {
                            BasicText("Parent", Modifier.testTag("parent"))
                            if (childVisible) GlassDialogWindow(true, { childVisible = false }, DialogProperties()) {
                                BasicText("Child", Modifier.testTag("child"))
                            }
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        fun advance(millis: Long) {
            composeRule.runOnIdle { Snapshot.sendApplyNotifications() }
            composeRule.mainClock.advanceTimeBy(millis)
            composeRule.waitForIdle()
        }
        advance(300)
        val parent = host.dialogs.entries.single()
        composeRule.runOnIdle { childVisible = true }
        advance(300)
        assertEquals(0f, parent.exposure.value, 0f)
        composeRule.onNodeWithTag("parent").assertDoesNotExist()
        composeRule.onNodeWithTag("child").assertExists()
        assertEquals(2, host.dialogs.entries.size)
        composeRule.runOnIdle { childVisible = false }
        advance(96)
        assertTrue(parent.exposure.value > 0f && parent.exposure.value < 1f)
        composeRule.onNodeWithTag("parent").assertExists()
        composeRule.onNodeWithTag("child").assertDoesNotExist()
        composeRule.onNodeWithTag("search").assertDoesNotExist()
        assertEquals(1, host.dialogs.entries.size)
        advance(300)
        assertEquals(1f, parent.exposure.value, 0f)
    }
}
