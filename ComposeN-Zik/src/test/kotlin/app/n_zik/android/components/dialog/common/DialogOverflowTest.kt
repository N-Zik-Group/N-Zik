package app.n_zik.android.components.dialog.common

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.formatAsDuration
import app.n_zik.android.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for issue #896 — dialog button rows must never overflow the dialog frame:
 * every pill is bounded to its equally weighted cell and kept on a single line
 * (continuous marquee), on any screen width and any locale.
 *
 * The #896 defect was the legacy pill rows sizing themselves to their labels: on narrow
 * dialogs a long label pushed the later pills off-screen. The canon [ButtonRow] (and the
 * [ToggleListDialog] pills and labels, which reuse it) must instead stay single-line and
 * cell-bounded. The assertions below are relative — density- and font-independent:
 *  - cell-bounded: a long label renders no wider than its weighted cell, not its natural width;
 *  - single-line: a long label is no taller than a short label (a wrapped one would be taller).
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on the
 * JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4), with the plain
 * [Application] so the app's heavy init (DI, Room, player) is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class DialogOverflowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun content(block: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                block()
            }
        }
    }

    // This resolved ui-unit build's DpRect exposes only left/top/right/bottom, so derive
    // width/height explicitly (the landscape width test uses the same idiom).
    private fun widthOf(bounds: DpRect): Dp = bounds.right - bounds.left
    private fun heightOf(bounds: DpRect): Dp = bounds.bottom - bounds.top

    // A wrapped second line adds far more than this; a height spread within it ⇒ single line.
    private val singleLineTolerance = 10.dp

    @Test
    fun `button row pills stay single-line and cell-bounded in a narrow dialog`() {
        val labels = listOf("A very long reset label", "A very long cancel label", "ok")

        content {
            Box(modifier = Modifier.width(150.dp)) {
                ButtonRow(
                    buttons = labels.map { label ->
                        DialogButton(text = label, primary = label == "ok", onClick = {})
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Each equally weighted cell is (150 - 2·16 padding - 2·8 spacing) / 3 ≈ 34dp. A #896
        // regression (pills sized to their label) would render the long labels >100dp wide;
        // the fix bounds every pill to its ~34dp cell regardless of label length.
        labels.forEach { label ->
            val node = composeRule.onNodeWithText(label)
            node.assertIsDisplayed()
            val w = widthOf(node.getBoundsInRoot())
            assertTrue("pill '$label' overflows its ~34dp cell (width=$w)", w < 40.dp)
        }

        // Single line: every pill has the same height — a long label that wrapped to a second
        // line would be taller than the short single-line "ok" pill.
        val heights = labels.map { heightOf(composeRule.onNodeWithText(it).getBoundsInRoot()) }
        val spread = heights.maxOrNull()!! - heights.minOrNull()!!
        assertTrue(
            "a long pill wrapped to a second line (heights=$heights, spread=$spread)",
            spread <= singleLineTolerance
        )
    }

    @Test
    fun `toggle list dialog renders its three pills single-line`() {
        content {
            ToggleListDialog(
                items = listOf(
                    ToggleItem(
                        id = "option_one",
                        iconRes = R.drawable.reorder,
                        label = "First option",
                        preferenceKey = "dialog_overflow_one",
                        defaultValue = true
                    ),
                    ToggleItem(
                        id = "option_two",
                        iconRes = R.drawable.reorder,
                        label = "Second option",
                        preferenceKey = "dialog_overflow_two",
                        defaultValue = false
                    )
                ),
                checkedStatesOverride = listOf(true, false),
                onReset = {},
                onCancel = {},
                onConfirm = {}
            )
        }
        composeRule.waitForIdle()

        val pills = listOf(
            app.getString(R.string.reset),
            app.getString(R.string.cancel),
            app.getString(R.string.ok)
        )
        val heights = pills.map { pill ->
            composeRule.onNodeWithText(pill).assertIsDisplayed()
            heightOf(composeRule.onNodeWithText(pill).getBoundsInRoot())
        }
        val spread = heights.maxOrNull()!! - heights.minOrNull()!!
        assertTrue(
            "a pill wrapped to a second line (heights=$heights, spread=$spread)",
            spread <= singleLineTolerance
        )
    }

    @Test
    fun `toggle list dialog keeps a long label on a single line`() {
        val shortLabel = "Short"
        val longLabel = "A rather long option label that cannot fit in a narrow cell"

        content {
            ToggleListDialog(
                items = listOf(
                    ToggleItem(
                        id = "option_short",
                        iconRes = R.drawable.reorder,
                        label = shortLabel,
                        preferenceKey = "dialog_overflow_short",
                        defaultValue = false
                    ),
                    ToggleItem(
                        id = "option_long",
                        iconRes = R.drawable.reorder,
                        label = longLabel,
                        preferenceKey = "dialog_overflow_long",
                        defaultValue = false
                    )
                ),
                checkedStatesOverride = listOf(false, false)
            )
        }
        composeRule.waitForIdle()

        // The short label is trivially single-line; if the long label wrapped, it would be
        // taller. Equal height proves the marquee keeps it on a single line.
        val shortHeight =
            heightOf(composeRule.onNodeWithText(shortLabel).assertIsDisplayed().getBoundsInRoot())
        val longHeight =
            heightOf(composeRule.onNodeWithText(longLabel).assertIsDisplayed().getBoundsInRoot())
        assertTrue(
            "long label wrapped to a second line (short=$shortHeight, long=$longHeight)",
            longHeight <= shortHeight + singleLineTolerance
        )
    }

    @Config(qualifiers = "w320dp-h470dp")
    @Test
    fun `sleep timer slider stays bounded to the card width on a narrow screen`() {
        content {
            SleepTimerDialog(
                sleepTimerMillisLeft = null,
                timeRemaining = 0L,
                onDismiss = {},
                onCancelSleepTimer = {},
                onStartSleepTimer = {}
            )
        }
        composeRule.waitForIdle()

        // The default view is the stepper; tap the duration to switch to the circular slider.
        composeRule
            .onNodeWithText(app.getString(R.string.left, formatAsDuration(5L * 60 * 1000)))
            .performClick()
        composeRule.waitForIdle()

        // Dialog windows report differently to assertIsDisplayed under Robolectric
        // (the landscape width test uses the same direct-bounds idiom), so the nodes
        // are asserted through their bounds instead.
        val card = composeRule.onNodeWithTag("dialogFrameCard").getBoundsInRoot()
        val slider = composeRule.onNodeWithTag("sleepTimerSlider").getBoundsInRoot()
        // The card (95% of a 320dp screen ≈ 304dp) insets its body by 16dp (card padding)
        // + 24dp (body padding) on each side, so the slider — fillMaxWidth under its 300dp
        // cap — must render at most ~224dp wide, never the legacy fixed 300dp size that
        // overflowed the narrow card (issue #896).
        assertTrue(
            "slider overflows the card's available width (card=${widthOf(card)}, slider=${widthOf(slider)})",
            widthOf(slider) <= widthOf(card) - 75.dp
        )
    }
}
