package app.n_zik.android.components.dialog.common

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class RawUrlImportDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `footer actions preserve clipboard empty validation and exact raw callback`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val initial = "https://music.youtube.com/playlist?list=VLabc&extra=1"
        clipboard.setPrimaryClip(ClipData.newPlainText("", initial))
        val imported = mutableListOf<String>()
        var dismissed = 0
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                RawUrlImportDialog(
                    title = "Import YouTube link",
                    placeholder = "URL",
                    onDismiss = { dismissed++ },
                    onImport = { imported.add(it) },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val field = composeRule.onNode(hasSetTextAction())
        field.assertTextEquals(initial)
        val footer = composeRule.onNodeWithTag("dialogFrameFooter").getBoundsInRoot()
        val divider = composeRule.onNodeWithTag("dialogFrameFooterDivider").getBoundsInRoot()
        val body = composeRule.onNodeWithTag("dialogFrameBody").getBoundsInRoot()
        assertTrue(field.getBoundsInRoot().bottom <= body.bottom)
        listOf(R.string.paste, R.string.clear, R.string.cancel, R.string.import_button).forEach {
            val bounds = composeRule.onNodeWithText(context.getString(it)).assertIsDisplayed().getBoundsInRoot()
            assertTrue(bounds.top >= divider.bottom && bounds.top >= footer.top)
            assertTrue(bounds.bottom <= footer.bottom)
        }
        composeRule.onNodeWithText(context.getString(R.string.import_button)).performClick()
        composeRule.runOnIdle { assertEquals(listOf(initial), imported) }
        composeRule.onNodeWithText(context.getString(R.string.clear)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.import_button)).performClick()
        composeRule.runOnIdle { assertEquals(listOf(initial), imported) }
        val pasted = "  https://youtube.com/playlist?list=raw  "
        composeRule.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("", pasted)) }
        composeRule.onNodeWithText(context.getString(R.string.paste)).performClick()
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        field.assertTextEquals(pasted)
        composeRule.onNodeWithText(context.getString(R.string.import_button)).performClick()
        field.performTextReplacement(" ")
        composeRule.onNodeWithText(context.getString(R.string.import_button)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(initial, pasted, " "), imported)
            assertEquals(0, dismissed)
        }
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeRule.runOnIdle { assertEquals(1, dismissed) }
    }
}
