package app.n_zik.android.components.dialog.common

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.dialog.media.YouTubeLinkImportDialog
import app.n_zik.android.components.settings.common.SettingsResetCard
import app.n_zik.android.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class DialogPresenceTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null

    @Before fun isolate() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("dialog_presence_test", context)
        context.preferences.edit().clear().commit()
        compose.mainClock.autoAdvance = false
    }

    @After fun restore() {
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    private fun content(block: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = ColorPalette.fallback, typography = Typography.fallback,
                thumbnailShape = RoundedCornerShape(16.dp), uiRoundnessShape = RoundedCornerShape(16.dp),
                artistThumbnailShape = RoundedCornerShape(16.dp)
            )) { block() }
        }
        advance(32)
    }

    private fun advance(millis: Long = 400) {
        // The paused clock needs externally written snapshots delivered before frame time moves.
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    @Test fun `surface appears before body and closing folds before disposal`() {
        val visible = mutableStateOf(true)
        var presentation: DialogPresentation? = null
        var disposed = false
        content {
            DialogPresence(visible = visible.value) {
                presentation = LocalDialogPresentation.current
                DisposableEffect(Unit) { onDispose { disposed = true } }
                DialogFrame(onDismiss = {}, title = "Title", footer = { BasicText("Footer") }) {
                    Box(Modifier.height(120.dp))
                }
            }
        }
        assertEquals(0f, dialogBodyFraction(requireNotNull(presentation).motion.value), 0f)
        advance()
        val fullBounds = compose.onNodeWithTag("dialogFrameBody").getBoundsInRoot()
        val fullHeight = fullBounds.bottom - fullBounds.top
        assertTrue(fullHeight > 120.dp)
        compose.runOnIdle { visible.value = false }
        advance(160)
        assertFalse(disposed)
        assertFalse(requireNotNull(presentation).interactive)
        assertTrue(dialogBodyFraction(requireNotNull(presentation).motion.value) in 0f..1f)
        advance(32)
        assertEquals(0f, dialogBodyFraction(requireNotNull(presentation).motion.value), 0f)
        assertFalse(disposed)
        advance(80)
        assertTrue(disposed)
    }

    @Test fun `confirmation callbacks run immediately once in their existing order`() {
        val visible = mutableStateOf(true)
        val calls = mutableListOf<String>()
        var disposed = false
        content {
            DialogPresence(visible = visible.value) {
                DisposableEffect(Unit) { onDispose { disposed = true } }
                ConfirmationDialog(
                    title = "Question", text = "Body", confirmText = "Confirm",
                    onConfirm = { calls += "confirm" },
                    onDismiss = { calls += "dismiss"; visible.value = false }
                )
            }
        }
        advance()
        compose.onNodeWithText("Confirm").performClick()
        compose.runOnIdle {
            assertEquals(listOf("confirm", "dismiss"), calls)
            assertFalse(disposed)
        }
        advance()
        assertEquals(2, calls.size)
        assertTrue(disposed)
    }

    @Test fun `retired confirmation rejects a captured action`() {
        val visible = mutableStateOf(true)
        val calls = mutableListOf<String>()
        content {
            DialogPresence(visible = visible.value) {
                ConfirmationDialog(
                    title = "Question", text = "Body", confirmText = "Confirm",
                    onConfirm = { calls += "confirm" },
                    onDismiss = { calls += "dismiss"; visible.value = false }
                )
            }
        }
        advance()
        val click = requireNotNull(compose.onNodeWithText("Confirm")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.runOnIdle {
            click()
            assertEquals(listOf("confirm", "dismiss"), calls)
        }
        advance(16)
        compose.onNodeWithText("Confirm").assertDoesNotExist()
        compose.runOnIdle {
            click()
            assertEquals(listOf("confirm", "dismiss"), calls)
        }
        advance()
        assertEquals(listOf("confirm", "dismiss"), calls)
    }

    @Test fun `nullable payload survives closing and clears after exit`() {
        val target = mutableStateOf<String?>("original")
        var shown: String? = null
        var disposed = false
        content {
            DialogPresence(target = target.value) { payload ->
                shown = payload
                DisposableEffect(Unit) { onDispose { disposed = true } }
                DialogFrame(onDismiss = {}, title = payload, footer = { BasicText("Done") }) {
                    BasicText(payload)
                }
            }
        }
        advance()
        compose.runOnIdle { target.value = null }
        advance(100)
        assertEquals("original", shown)
        assertFalse(disposed)
        advance()
        assertTrue(disposed)
    }

    @Test fun `reopening reverses current window without disposing its content`() {
        val visible = mutableStateOf(true)
        var mounts = 0
        var disposals = 0
        content {
            DialogPresence(visible = visible.value) {
                DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                DialogFrame(onDismiss = {}, title = "Title", footer = { BasicText("Done") }) {
                    BasicText("Body")
                }
            }
        }
        advance()
        compose.runOnIdle { visible.value = false }
        advance(100)
        compose.runOnIdle { visible.value = true }
        advance()
        assertEquals(1, mounts)
        assertEquals(0, disposals)
        compose.onNodeWithTag("dialogFrameBody").assertExists()
    }

    @Test fun `frame consumes inherited presentation and nested windows are independent`() {
        val childVisible = mutableStateOf(false)
        var parent: DialogPresentation? = null
        var child: DialogPresentation? = null
        content {
            DialogPresence(visible = true) {
                parent = LocalDialogPresentation.current
                DialogFrame(onDismiss = {}, title = "Parent", footer = { BasicText("Done") }) {
                    DialogPresence(visible = childVisible.value) {
                        child = LocalDialogPresentation.current
                        DialogFrame(onDismiss = {}, title = "Child", footer = { BasicText("Child done") }) {
                            BasicText("Child body")
                        }
                    }
                }
            }
        }
        advance()
        compose.runOnIdle { childVisible.value = true }
        advance()
        assertTrue(parent !== child)
        compose.runOnIdle { childVisible.value = false }
        advance()
        assertTrue(requireNotNull(parent).interactive)
        compose.onNodeWithText("Parent").assertExists()
        compose.onNodeWithText("Child").assertDoesNotExist()
    }

    @Test fun `owner disposal cancels an exit immediately`() {
        val owner = mutableStateOf(true)
        val visible = mutableStateOf(true)
        var disposed = false
        content {
            if (owner.value) {
                DialogPresence(visible = visible.value) {
                    DisposableEffect(Unit) { onDispose { disposed = true } }
                    DialogFrame(onDismiss = {}, title = "Owned", footer = { BasicText("Done") }) {
                        BasicText("Body")
                    }
                }
            }
        }
        advance()
        compose.runOnIdle { visible.value = false }
        advance(32)
        compose.runOnIdle { owner.value = false }
        advance(16)
        assertTrue(disposed)
        compose.onNodeWithText("Owned").assertDoesNotExist()
    }

    @Test fun `missing bands use only the short TV lifetime`() {
        val visible = mutableStateOf(true)
        var disposed = false
        var presentation: DialogPresentation? = null
        content {
            DialogPresence(visible = visible.value) {
                presentation = LocalDialogPresentation.current
                DisposableEffect(Unit) { onDispose { disposed = true } }
                DialogFrame(onDismiss = {}) { BasicText("No bands") }
            }
        }
        advance()
        assertEquals(80, requireNotNull(presentation).durationMillis)
        compose.runOnIdle { visible.value = false }
        advance(32)
        assertFalse(disposed)
        advance(96)
        assertTrue(disposed)
    }

    @Test fun `motion staging has a single meeting seam and unscaled body allocation`() {
        assertEquals(0f, dialogLineFraction(0f, true), 0f)
        assertTrue(dialogLineFraction(30f / 220f, true) in 0f..1f)
        assertEquals(0f, dialogTvFraction(30f / 220f, true), .00001f)
        assertEquals(1f, dialogTvFraction(80f / 220f, true), .00001f)
        assertEquals(0f, dialogBodyFraction(80f / 220f), .00001f)
        assertTrue(dialogBodyFraction(150f / 220f) in .5f..1f)
        assertEquals(1f, dialogBodyFraction(1f), 0f)
        assertEquals(0f, dialogFlashAlpha(0f, true), .00001f)
        assertEquals(0f, dialogFlashAlpha(1f, true), .00001f)
        assertTrue(dialogFlashAlpha(40f / 220f, true) > .5f)
        assertTrue(dialogLineFraction(48f / 220f, true) < 1f)
        assertTrue(dialogTvFraction(48f / 220f, true) > 0f)
        assertEquals(80, dialogDuration(false))
        assertEquals(220, dialogDuration(true))
    }

    @Test fun `reset scroll viewport touches separators and retains its scrollable top inset`() {
        content {
            SettingsResetCard(scopes = (0..7).toList(), target = "", resetContent = {})
        }
        compose.onNodeWithTag("settings.reset.open").performClick()
        advance()
        val viewport = compose.onNodeWithTag("settings.reset.dialog").getBoundsInRoot()
        val top = compose.onNodeWithTag("dialogFrameHeaderDivider").getBoundsInRoot()
        val bottom = compose.onNodeWithTag("dialogFrameFooterDivider").getBoundsInRoot()
        val first = compose.onNodeWithTag("settings.reset.scope.0").getBoundsInRoot()
        assertEquals(top.bottom.value, viewport.top.value, .5f)
        assertEquals(bottom.top.value, viewport.bottom.value, .5f)
        assertTrue(first.top - viewport.top >= 24.dp)
    }
    @Test fun `retiring content hides accessibility actions immediately`() {
        val visible = mutableStateOf(true)
        content {
            DialogPresence(visible = visible.value) {
                ConfirmationDialog(
                    title = "Question", text = "Body", confirmText = "Confirm",
                    onConfirm = {}, onDismiss = { visible.value = false }
                )
            }
        }
        advance()
        compose.runOnIdle { visible.value = false }
        advance(16)
        compose.onNodeWithText("Confirm").assertDoesNotExist()
    }

    @Test fun `settings input dismissal is immediate event driven and once only`() {
        val visible = mutableStateOf(true)
        val events = mutableListOf<String>()
        content {
            DialogPresence(visible = visible.value) {
                SettingsInputDialog(
                    title = "Input", initialValue = "value",
                    onSetValue = { events += it },
                    onDismiss = { events += "dismiss"; visible.value = false }
                ).Render()
            }
        }
        advance()
        compose.onNodeWithText(context.getString(R.string.dialog_save)).performClick()
        compose.runOnIdle { assertEquals(listOf("value", "dismiss"), events) }
        advance()
        assertEquals(listOf("value", "dismiss"), events)
    }

    @Test fun `retiring value selector consumes touches at its former row coordinates`() {
        val visible = mutableStateOf(true)
        var selections = 0
        var dismissals = 0
        var disposed = false
        content {
            DialogPresence(visible = visible.value) {
                DisposableEffect(Unit) { onDispose { disposed = true } }
                ValueSelectorDialog(
                    title = "Choose", selectedValue = "First",
                    values = listOf("First", "Second", "Third"),
                    onValueSelected = { selections++ }, onDismiss = { dismissals++ }
                )
            }
        }
        advance()
        val viewport = compose.onNodeWithTag("dialogFrameViewport")
        val rowCenter = compose.onNodeWithText("Second").fetchSemanticsNode().boundsInRoot.center
        val touchPosition = rowCenter - viewport.fetchSemanticsNode().boundsInRoot.topLeft
        compose.runOnIdle { visible.value = false }
        advance(16)
        assertFalse(disposed)
        compose.onNodeWithText("Second").assertDoesNotExist()
        viewport.performTouchInput { click(touchPosition) }
        compose.runOnIdle {
            assertEquals(0, selections)
            assertEquals(0, dismissals)
            assertFalse(disposed)
        }
        advance()
        assertTrue(disposed)
    }

    @Test fun `retiring YouTube import ignores an IME action captured while open`() {
        val visible = mutableStateOf(true)
        var imports = 0
        var dismissals = 0
        var disposed = false
        content {
            DialogPresence(visible = visible.value) {
                DisposableEffect(Unit) { onDispose { disposed = true } }
                YouTubeLinkImportDialog(
                    onImport = { imports++ }, onDismiss = { dismissals++ }
                )
            }
        }
        advance()
        val input = compose.onNode(hasSetTextAction())
        input.performTextInput("https://youtube.com/playlist?list=PLretirement")
        advance(16)
        val imeAction = requireNotNull(input.fetchSemanticsNode().config[SemanticsActions.OnImeAction].action)
        compose.runOnIdle { visible.value = false }
        advance(16)
        assertFalse(disposed)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle {
            imeAction()
            assertEquals(0, imports)
            assertEquals(0, dismissals)
            assertFalse(disposed)
        }
        advance()
        assertTrue(disposed)
    }

    @Test fun `motion updates layers without recomposing the dialog root`() {
        var compositions = 0
        content {
            DialogPresence(visible = true) {
                SideEffect { compositions++ }
                DialogFrame(onDismiss = {}, title = "Title", footer = { BasicText("Footer") }) {
                    Box(Modifier.height(120.dp))
                }
            }
        }
        val afterInitial = compositions
        advance()
        assertEquals(afterInitial, compositions)
    }

    @Test fun `replacement during retirement can only act on its own payload`() {
        val target = mutableStateOf<String?>("original")
        val calls = mutableListOf<String>()
        var mounts = 0
        content {
            DialogPresence(target = target.value) { payload ->
                DisposableEffect(Unit) { mounts++; onDispose { } }
                ConfirmationDialog(
                    title = payload, text = "Body", confirmText = "Confirm",
                    onConfirm = { calls += payload }, onDismiss = { target.value = null }
                )
            }
        }
        advance()
        compose.runOnIdle { target.value = null }
        advance(100)
        assertTrue(calls.isEmpty())
        compose.runOnIdle { target.value = "replacement" }
        advance()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(listOf("replacement"), calls)
        assertEquals(1, mounts)
    }

    @Test fun `initially inactive owners do not schedule a no op animation`() {
        var mounts = 0
        content {
            DialogPresence(visible = false) {
                DisposableEffect(Unit) { mounts++; onDispose { } }
            }
        }
        val beforeIdle = compose.mainClock.currentTime
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(0, mounts)
        assertTrue(compose.mainClock.currentTime - beforeIdle <= 32L)
    }

}
