package app.n_zik.android.components.dialog.common

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.dialog.tab.DeleteAllDownloadedSongsDialog
import app.n_zik.android.components.dialog.tab.DownloadAllSongsDialog
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.FontType
import app.n_zik.android.models.Song
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
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
        val songs = listOf(Song.makePlaceholder("title-test-1"), Song.makePlaceholder("title-test-2"))
        val pendingSongs = mutableStateOf<List<Song>?>(songs)
        val dialog = DownloadAllSongsDialog(pendingSongs, { songs }, null)
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
        val title = composeRule.onNodeWithText(context.getString(R.string.downloads_download_dialog_title)).assertIsDisplayed().getBoundsInRoot()
        val question = composeRule.onNodeWithText(context.getString(R.string.downloads_download_all_count_confirmation, 2)).assertIsDisplayed().getBoundsInRoot()
        assertTrue(title.top >= header.top && title.bottom <= header.bottom)
        assertTrue(question.top >= body.top && question.bottom <= body.bottom)
        composeRule.onNodeWithText(context.getString(R.string.downloads_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.common_action_confirm)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.common_action_cancel)).performClick()
        composeRule.runOnIdle { assertEquals(null, pendingSongs.value) }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Test fun `a failed download can open the retry confirmation`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val failed = Song.makePlaceholder("failed")
        val completed = Song.makePlaceholder("completed")
        val pendingSongs = mutableStateOf<List<Song>?>(null)
        val dialog = DownloadAllSongsDialog(pendingSongs, { listOf(failed, completed) }, null)
        val previousDownloads = MyDownloadHelper.downloads.value
        MyDownloadHelper.downloads.value = mapOf(
            failed.id to download(failed.id, Download.STATE_FAILED),
            completed.id to download(completed.id, Download.STATE_COMPLETED),
        )
        try {
            dialog.onShortClick()
            composeRule.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    dialog.Render()
                }
            }

            composeRule.onNodeWithText(
                context.getString(R.string.downloads_download_all_count_confirmation, 1)
            ).assertIsDisplayed()
        } finally {
            MyDownloadHelper.downloads.value = previousDownloads
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Test fun `download removal confirms the captured target after selection clears`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val selected = Song.makePlaceholder("selected-in-progress")
        val unselected = Song.makePlaceholder("unselected-completed")
        var selection = listOf(selected)
        val pendingSongs = mutableStateOf<List<Song>?>(null)
        val dialog = DeleteAllDownloadedSongsDialog(pendingSongs, { selection }, null)
        val previousDownloads = MyDownloadHelper.downloads.value
        MyDownloadHelper.downloads.value = mapOf(
            selected.id to download(selected.id, Download.STATE_DOWNLOADING),
            unselected.id to download(unselected.id, Download.STATE_COMPLETED),
        )
        mockkStatic(::appContext)
        every { appContext() } returns context
        mockkObject(MyDownloadHelper)
        every { MyDownloadHelper.removeDownload(any(), any()) } just Runs
        try {
            dialog.onShortClick()
            composeRule.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    dialog.Render()
                }
            }
            composeRule.runOnIdle { selection = listOf(unselected, selected) }
            composeRule.onNodeWithText(
                context.getString(R.string.downloads_delete_all_count_confirmation, 1)
            ).assertIsDisplayed()
            composeRule.onNodeWithText(context.getString(R.string.common_action_delete)).performClick()

            verify(timeout = 5_000, exactly = 1) {
                MyDownloadHelper.removeDownload(context, match { it.mediaId == selected.id })
            }
            verify(exactly = 1) { MyDownloadHelper.removeDownload(any(), any()) }
            composeRule.runOnIdle { assertEquals(null, pendingSongs.value) }
        } finally {
            unmockkObject(MyDownloadHelper)
            unmockkStatic(::appContext)
            MyDownloadHelper.downloads.value = previousDownloads
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun download(id: String, state: Int): Download = Download(
        DownloadRequest.Builder(id, Uri.parse("https://example.test/$id")).build(),
        state,
        0L,
        0L,
        0L,
        Download.STOP_REASON_NONE,
        if (state == Download.STATE_FAILED) Download.FAILURE_REASON_UNKNOWN else Download.FAILURE_REASON_NONE,
    )

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
