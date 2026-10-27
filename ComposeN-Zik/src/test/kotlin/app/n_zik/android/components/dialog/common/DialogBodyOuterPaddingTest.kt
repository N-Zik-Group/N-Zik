package app.n_zik.android.components.dialog.common

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.maintenance.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class DialogBodyOuterPaddingTest {
    @get:Rule val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Test fun emptySyncResultHasNoLeadingGap() = assertSyncBody(emptyList(), expectedGap = 0f)

    @Test fun syncResultKeepsTheGapBeforeItsSummary() =
        assertSyncBody(listOf("Library" to true), expectedGap = 8f)

    private fun assertSyncBody(results: List<Pair<String, Boolean>>, expectedGap: Float) {
        val dialog = SyncResultDialog(mutableStateOf(true), results, elapsedSeconds = 2)
        showBody(bodyPadding = dialog.bodyPadding) { dialog.DialogBody() }
        val context = ApplicationProvider.getApplicationContext<Application>()
        val summary = context.getString(R.string.sync_result_completed, 2L)
        val textHeight = textHeight(summary) + if (results.isEmpty()) 0f else textHeight("\u2713 Library")
        val body = composeRule.onNodeWithTag("dialogFrameBody").getBoundsInRoot()
        assertEquals("frame inset plus actual text and internal gap", 48f + textHeight + expectedGap, (body.bottom - body.top).value, 1f)
    }

    @Test fun samsungAdviceWithoutSummaryHasNoOuterMessagePadding() = assertSamsungBody(storageBytes = 0L, expectedGap = 0f)

    @Test fun samsungAdviceKeepsOnlyItsMessageToSummaryGap() = assertSamsungBody(storageBytes = 1_048_576L, expectedGap = 20f)

    private fun assertSamsungBody(storageBytes: Long, expectedGap: Float) {
        val previous = SamsungStorageAdviceDialog.storageBytes
        try {
            SamsungStorageAdviceDialog.storageBytes = storageBytes
            showBody { SamsungStorageAdviceDialog.DialogBody() }
            val context = ApplicationProvider.getApplicationContext<Application>()
            val message = context.getString(R.string.samsung_storage_advice_message)
            val summary = if (storageBytes == 0L) null else context.getString(
                R.string.samsung_storage_advice_storage,
                formatBytes(storageBytes),
            )
            val textHeight = textHeight(message) + (summary?.let(::textHeight) ?: 0f)
            val body = composeRule.onNodeWithTag("dialogFrameBody").getBoundsInRoot()
            assertEquals("frame inset plus actual text and optional gap", 48f + textHeight + expectedGap, (body.bottom - body.top).value, 1f)
        } finally {
            SamsungStorageAdviceDialog.storageBytes = previous
        }
    }

    private fun textHeight(text: String): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return with(composeRule.density) { layouts.single().size.height.toDp().value }
    }

    private fun showBody(bodyPadding: PaddingValues = PaddingValues(24.dp), content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                DialogFrame(onDismiss = {}, title = "Padding regression", bodyPadding = bodyPadding) { content() }
            }
        }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
    }
}
