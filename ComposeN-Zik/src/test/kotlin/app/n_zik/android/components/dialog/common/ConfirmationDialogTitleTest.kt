package app.n_zik.android.components.dialog.common

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.R
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class ConfirmationDialogTitleTest {
    @get:Rule val composeRule = createComposeRule()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    @Test fun `clear confirmation exposes a centered heading and keeps callbacks separate`() {
        var confirmed = 0
        var dismissed = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                ConfirmationDialog(
                    title = "Clear permission activity history",
                    text = "Delete all recorded permission events?",
                    confirmText = "Clear",
                    cancelText = "Cancel",
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val header = composeRule.onNodeWithTag("dialogFrameHeader").getBoundsInRoot()
        val title = composeRule.onNodeWithText("Clear permission activity history").assertIsDisplayed().getBoundsInRoot()
        val body = composeRule.onNodeWithText("Delete all recorded permission events?").assertIsDisplayed().getBoundsInRoot()
        assertEquals((header.left.value + header.right.value) / 2, (title.left.value + title.right.value) / 2, 1f)
        assertTrue(title.bottom <= header.bottom)
        assertTrue(body.top >= header.bottom)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            assertEquals(0, confirmed)
            assertEquals(1, dismissed)
        }
        composeRule.onNodeWithText("Clear").performClick()
        composeRule.runOnIdle {
            assertEquals(1, confirmed)
            assertEquals(2, dismissed)
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Test fun `download all caller keeps a short heading count question and download action`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val visible = mutableStateOf(true)
        val songs = listOf(Song.makePlaceholder("title-test-1"), Song.makePlaceholder("title-test-2"))
        val dialog = DownloadAllSongsDialog(visible, { songs }, null)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                dialog.Render()
            }
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val header = composeRule.onNodeWithTag("dialogFrameHeader").getBoundsInRoot()
        val body = composeRule.onNodeWithTag("dialogFrameBody").getBoundsInRoot()
        val title = composeRule.onNodeWithText(context.getString(R.string.dialog_download_songs)).assertIsDisplayed().getBoundsInRoot()
        val question = composeRule.onNodeWithText(context.getString(R.string.do_you_really_want_to_download_all_count, 2)).assertIsDisplayed().getBoundsInRoot()
        assertTrue(title.top >= header.top && title.bottom <= header.bottom)
        assertTrue(question.top >= body.top && question.bottom <= body.bottom)
        composeRule.onNodeWithText(context.getString(R.string.download)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.confirm)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeRule.runOnIdle { assertEquals(false, visible.value) }
    }

    @Test fun `import confirmation does not abort its chain through dismissal`() {
        var started = 0
        var aborted = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                ConfirmationDialog(
                    title = "Import backup",
                    text = "This backup belongs to another profile.",
                    confirmText = "Import",
                    cancelText = "Cancel",
                    onConfirm = { started++ },
                    onDismiss = { aborted++ },
                    dismissOnConfirm = false,
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Import").performClick()
        composeRule.runOnIdle {
            assertEquals(1, started)
            assertEquals(0, aborted)
        }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            assertEquals(1, started)
            assertEquals(1, aborted)
        }
    }

}
