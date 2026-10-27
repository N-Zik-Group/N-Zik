package app.n_zik.android.components.settings.privacy

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.time.Duration
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class PermissionUsageDialogTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        val app = mockk<MainApplication>()
        every { app.applicationContext } returns context
        Dependencies.init(app)
    }

    private fun pumpUntil(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(100)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            if (condition()) return
        }
        assertTrue(label, condition())
    }

    private fun show() {
        composeRule.mainClock.autoAdvance = false
        val registry = SettingsTargetRegistry()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsTargets provides registry) {
                PermissionUsageSettingsCard()
            }
        }
        pumpUntil("opening row composed") {
            runCatching { composeRule.onNodeWithTag("settings.target.privacy.permissions.open", useUnmergedTree = true).fetchSemanticsNode() }.isSuccess
        }
        composeRule.mainClock.advanceTimeBy(400)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
    }

    @Test fun pauseStaysOnPageAndTheHistoryDialogClearsOnlyAfterConfirmation() {
        seedEvents(1)
        val before = context.preferences.all.toMap()
        show()
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        composeRule.onNodeWithTag("settings.target.privacy.permissions.pause", useUnmergedTree = true).assertExists()
        assertEquals("the closed card must not mutate journal settings", before, context.preferences.all)
        composeRule.onNodeWithTag("settings.target.privacy.permissions.pause", useUnmergedTree = true).performClick()
        assertTrue(context.preferences.getBoolean(PermissionUsageJournal.PAUSED_KEY, false))
        composeRule.onNodeWithTag("settings.target.privacy.permissions.open", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick) { it() }
        pumpUntil("journal dialog") { runCatching { composeRule.onNodeWithTag("permission_journal_list").fetchSemanticsNode() }.isSuccess }
        composeRule.onAllNodes(isDialog()).assertCountEquals(1)
        composeRule.onAllNodes(hasText(context.getString(R.string.permission_journal_pause)) and hasAnyAncestor(isDialog())).assertCountEquals(0)
        composeRule.onNodeWithText(context.getString(R.string.permission_journal_description)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.permission_journal_clear_description)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.clear)).performClick()
        pumpUntil("clear confirmation") { runCatching { composeRule.onNodeWithText(context.getString(R.string.permission_journal_clear_confirm)).fetchSemanticsNode() }.isSuccess }
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertEquals(1, PermissionUsageJournal.read(context.preferences).size)
        composeRule.onNodeWithText(context.getString(R.string.clear)).performClick()
        pumpUntil("second clear confirmation") { runCatching { composeRule.onNodeWithText(context.getString(R.string.confirm)).fetchSemanticsNode() }.isSuccess }
        composeRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
        pumpUntil("confirmed clear processed") { PermissionUsageJournal.read(context.preferences).isEmpty() }
        composeRule.onNodeWithText(context.getString(R.string.done)).performClick()
        pumpUntil("dialog dismissed") { composeRule.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        assertTrue("clear must not resume paused recording", context.preferences.getBoolean(PermissionUsageJournal.PAUSED_KEY, false))
    }

    @Test fun fullJournalRemainsBoundedAndTheLastEventIsReachable() {
        seedEvents(200)
        show()
        composeRule.onNodeWithTag("settings.target.privacy.permissions.open", useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick) { it() }
        val lastText = eventText(199)
        pumpUntil("last journal event reachable") {
            runCatching {
                composeRule.onNodeWithTag("permission_journal_list").performScrollToIndex(199)
                composeRule.onNodeWithText(lastText).assertIsDisplayed()
            }.isSuccess
        }
        val bounds = composeRule.onNodeWithTag("permission_journal_dialog").getBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top <= 512.dp)
        assertTrue(bounds.right - bounds.left <= 342.dp)
        val done = composeRule.onNodeWithText(context.getString(R.string.done)).assertIsDisplayed().getBoundsInRoot()
        val clear = composeRule.onNodeWithText(context.getString(R.string.clear)).assertIsDisplayed().getBoundsInRoot()
        assertEquals("Footer actions share one bottom row", done.top, clear.top)
        assertTrue("Clear precedes Done", clear.right <= done.left)
        composeRule.onNodeWithText(context.getString(R.string.clear)).performClick()
        pumpUntil("clear footer remains reachable with a full journal") {
            runCatching { composeRule.onNodeWithText(context.getString(R.string.permission_journal_clear_confirm)).assertIsDisplayed() }.isSuccess
        }
        assertFalse(context.preferences.getBoolean(PermissionUsageJournal.PAUSED_KEY, false))
    }

    private fun seedEvents(count: Int) {
        val array = JSONArray()
        repeat(count) { index ->
            array.put(JSONObject().put("time", timestamp(index)).put("feature", PermissionFeature.MUSIC_RECOGNITION.name)
                .put("permission", JournalPermission.MICROPHONE.name).put("kind", PermissionEventKind.USED.name))
        }
        context.preferences.edit().putString(PermissionUsageJournal.EVENTS_KEY, array.toString()).commit()
    }
    private fun timestamp(index: Int) = 1_700_000_000_000L - index * 60_000L
    private fun eventText(index: Int) = context.getString(R.string.permission_journal_event,
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timestamp(index))),
        context.getString(R.string.permission_feature_music_recognition), context.getString(R.string.permission_label_microphone),
        context.getString(R.string.permission_event_used))
}
