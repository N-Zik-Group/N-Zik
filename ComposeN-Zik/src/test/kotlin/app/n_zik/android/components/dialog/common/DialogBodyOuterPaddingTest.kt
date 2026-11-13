package app.n_zik.android.components.dialog.common

import android.app.Application
import android.text.format.Formatter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS-w360dp-h640dp")
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

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h460dp")
    fun samsungAdviceRenderSeparatesBodyTextAndWrapsActionLabels() {
        val dialog = SamsungStorageAdviceDialog
        val previousBytes = dialog.storageBytes
        val previouslyActive = dialog.isActive
        val context = ApplicationProvider.getApplicationContext<Application>()
        val bytes = 1_048_576L
        try {
            dialog.storageBytes = bytes
            dialog.showDialog()
            composeRule.mainClock.autoAdvance = false
            composeRule.setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(
                    LocalAppearance provides appearance,
                    LocalDensity provides Density(density, fontScale = 1.3f),
                ) {
                    dialog.Render()
                }
            }
            composeRule.mainClock.advanceTimeBy(300)
            composeRule.waitForIdle()
            composeRule.mainClock.autoAdvance = true

            val message = composeRule.onNodeWithText(context.getString(R.string.storage_samsung_advice_message))
            val summary = composeRule.onNodeWithText(
                context.getString(R.string.storage_samsung_system_usage_message, Formatter.formatShortFileSize(context, bytes))
            )
            assertTrue(summary.getUnclippedBoundsInRoot().top > message.getUnclippedBoundsInRoot().bottom)
            message.performScrollTo().assertIsDisplayed()
            assertVerticallyUnclipped(message)
            summary.performScrollTo().assertIsDisplayed()
            assertVerticallyUnclipped(summary)
            for (label in listOf(R.string.storage_samsung_action_keep_system, R.string.storage_samsung_action_use_private)) {
                val layouts = mutableListOf<TextLayoutResult>()
                val node = composeRule.onNodeWithText(context.getString(label), useUnmergedTree = true).assertIsDisplayed()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertTrue("the label must allow wrapping", layout.layoutInput.softWrap && layout.layoutInput.maxLines > 1)
                assertFalse(layout.hasVisualOverflow)
                assertVerticallyUnclipped(node)
            }
        } finally {
            dialog.isActive = previouslyActive
            dialog.storageBytes = previousBytes
        }
    }

    private fun assertVerticallyUnclipped(node: SemanticsNodeInteraction) {
        val bounds = node.getBoundsInRoot()
        val unclipped = node.getUnclippedBoundsInRoot()
        assertEquals(unclipped.top.value, bounds.top.value, 1f)
        assertEquals(unclipped.bottom.value, bounds.bottom.value, 1f)
    }

    private fun assertSamsungBody(storageBytes: Long, expectedGap: Float) {
        val previous = SamsungStorageAdviceDialog.storageBytes
        try {
            SamsungStorageAdviceDialog.storageBytes = storageBytes
            showBody { SamsungStorageAdviceDialog.DialogBody() }
            val context = ApplicationProvider.getApplicationContext<Application>()
            val message = context.getString(R.string.storage_samsung_advice_message)
            val summary = if (storageBytes == 0L) null else context.getString(
                R.string.storage_samsung_system_usage_message,
                Formatter.formatShortFileSize(context, storageBytes),
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
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
    }
}
