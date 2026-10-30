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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDialog
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

    @Test fun `whole surface animates and closing retains it until the fade ends`() {
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
        assertTrue(dialogFadeAlpha(requireNotNull(presentation).motion.value) < 1f)
        advance()
        val fullBounds = compose.onNodeWithTag("dialogFrameBody").getBoundsInRoot()
        val fullHeight = fullBounds.bottom - fullBounds.top
        assertTrue(fullHeight > 120.dp)
        compose.runOnIdle { visible.value = false }
        advance(160)
        assertFalse(disposed)
        assertFalse(requireNotNull(presentation).interactive)
        val closingAlpha = dialogFadeAlpha(requireNotNull(presentation).motion.value)
        assertTrue(closingAlpha > 0f && closingAlpha < 1f)
        advance(120)
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

    @Test fun `missing bands use the same whole surface animation lifetime`() {
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
        assertEquals(DIALOG_MOTION_DURATION_MS, requireNotNull(presentation).durationMillis)
        compose.runOnIdle { visible.value = false }
        advance(32)
        assertFalse(disposed)
        advance(240)
        assertTrue(disposed)
    }

    @Test fun `fade and subtle uniform scale share a bounded reversible path`() {
        assertEquals(0f, dialogFadeAlpha(0f), 0f)
        assertEquals(1f, dialogFadeAlpha(1f), 0f)
        assertEquals(.96f, dialogScale(0f), 0f)
        assertEquals(1f, dialogScale(1f), 0f)
        val opening = (0..20).map { dialogFadeAlpha(it / 20f) }
        assertTrue(opening.zipWithNext().all { (previous, next) -> next >= previous })
        val closing = (20 downTo 0).map { dialogFadeAlpha(it / 20f) }
        assertEquals(opening, closing.reversed())
        assertEquals(0f, dialogFadeAlpha(-1f), 0f)
        assertEquals(1f, dialogFadeAlpha(2f), 0f)
        assertTrue(dialogScale(.5f) > .96f && dialogScale(.5f) < 1f)
    }

    @Test fun `reset scroll viewport touches neighboring sections and retains its scrollable top inset`() {
        content {
            SettingsResetCard(scopes = (0..7).toList(), target = "", resetContent = {})
        }
        compose.onNodeWithTag("settings.reset.open").performClick()
        advance()
        val viewport = compose.onNodeWithTag("settings.reset.dialog").getBoundsInRoot()
        val top = compose.onNodeWithTag("dialogFrameHeader").getBoundsInRoot()
        val bottom = compose.onNodeWithTag("dialogFrameFooter").getBoundsInRoot()
        val first = compose.onNodeWithTag("settings.reset.scope.0").getBoundsInRoot()
        assertEquals(top.bottom.value, viewport.top.value, .5f)
        assertEquals(bottom.top.value, viewport.bottom.value, .5f)
        assertTrue(first.top - viewport.top >= 24.dp)
    }
    @Test fun `reset chooser and scoped reset fade through one retained window`() {
        var shownScope: Int? = null
        var presentation: DialogPresentation? = null
        content {
            SettingsResetCard(scopes = listOf(0, 1), target = "", resetContent = { scope ->
                shownScope = scope
                presentation = LocalDialogInput.current
                BasicText("Reset scope $scope")
            })
        }
        compose.onNodeWithTag("settings.reset.open").performClick()
        advance()
        val retiredScopeClick = requireNotNull(compose.onNodeWithTag("settings.reset.scope.1")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.onNodeWithTag("settings.reset.scope.0").performClick()
        advance(100)
        // A content swap must not remove the modal or expose the Settings page mid-handoff.
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithTag("dialogFrameViewport").assertExists()
        assertEquals(null, shownScope)
        compose.runOnIdle { retiredScopeClick() }
        advance(180)
        assertEquals(0, shownScope)
        val retainedPresentation = requireNotNull(presentation)
        assertTrue(retainedPresentation.motion.value > 0f && retainedPresentation.motion.value < 1f)
        advance()
        compose.onNodeWithText("Reset scope 0").assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_reset_scope_back)).performClick()
        advance(100)
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        assertFalse(retainedPresentation.interactive)
        assertTrue(retainedPresentation.motion.value > 0f && retainedPresentation.motion.value < 1f)
        advance()
        compose.onNodeWithTag("settings.reset.scope.1").performClick()
        advance()
        assertEquals(1, shownScope)
        assertTrue(retainedPresentation === presentation)
        compose.onNodeWithText("Reset scope 1").assertExists()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
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

    @Test fun `consecutive payloads fade through the same window with only the current action enabled`() {
        val target = mutableStateOf<String?>("First")
        var shown = ""
        var presentation: DialogPresentation? = null
        var mounts = 0
        val calls = mutableListOf<String>()
        content {
            DialogPresence(target = target.value) { payload ->
                shown = payload
                presentation = LocalDialogPresentation.current
                DisposableEffect(Unit) { mounts++; onDispose { } }
                ConfirmationDialog(title = payload, text = "Body", confirmText = "Confirm",
                    onConfirm = { calls += payload }, onDismiss = {})
            }
        }
        advance()
        val oldClick = requireNotNull(compose.onNodeWithText("Confirm")
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.runOnIdle { target.value = "Second" }
        advance(100)
        assertEquals("First", shown)
        assertFalse(requireNotNull(presentation).interactive)
        assertTrue(requireNotNull(presentation).motion.value > 0f)
        assertTrue(requireNotNull(presentation).motion.value < 1f)
        compose.runOnIdle { oldClick() }
        assertTrue(calls.isEmpty())
        advance(180)
        assertEquals("Second", shown)
        assertTrue(requireNotNull(presentation).interactive)
        assertTrue(requireNotNull(presentation).motion.value > 0f)
        assertTrue(requireNotNull(presentation).motion.value < 1f)
        advance()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(listOf("Second"), calls)
        assertEquals(1, mounts)
    }

    @Test fun `replacing again or returning during handoff cancels stale payloads`() {
        val target = mutableStateOf<String?>("First")
        var shown = ""
        var presentation: DialogPresentation? = null
        content {
            DialogPresence(target = target.value) { payload ->
                shown = payload
                presentation = LocalDialogPresentation.current
                DialogFrame(onDismiss = {}, title = payload) { BasicText("Body") }
            }
        }
        advance()
        compose.runOnIdle { target.value = "Second" }
        advance(64)
        compose.runOnIdle { target.value = "Third" }
        advance(32)
        compose.runOnIdle { target.value = "First" }
        advance()
        assertEquals("First", shown)
        assertEquals(1f, requireNotNull(presentation).motion.value, 0f)
        assertTrue(requireNotNull(presentation).interactive)
        compose.onNodeWithText("Second").assertDoesNotExist()
        compose.onNodeWithText("Third").assertDoesNotExist()
        compose.runOnIdle { target.value = null }
        advance()
        compose.onNodeWithTag("dialogFrameCard").assertDoesNotExist()
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
