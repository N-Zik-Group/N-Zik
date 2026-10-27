package app.n_zik.android.components.dialog.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.Dialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Full wrapper regressions: list viewports reach body edges while padded actions stay fixed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SettingsDialogFooterBandsTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shape = RoundedCornerShape(20.dp)
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape
    )

    @After fun hideDialogs() {
        HomeTabsSettingsDialog.hideDialog()
        BurgerMenuSettingsDialog.hideDialog()
        QuickPicksContentSettingsDialog.hideDialog()
        HomeSongsSortSettingsDialog.hideDialog()
        HomeSongsToolbarSettingsDialog.hideDialog()
    }

    @Test fun `quick picks sections uses a real footer outside the scrolling list`() {
        verify(QuickPicksContentSettingsDialog, scrollIndex = 20)
    }

    @Test fun `home songs sort keeps tabs in body and actions in a padded footer`() {
        verify(HomeSongsSortSettingsDialog, scrollIndex = 10, hasBodyHeader = true)
    }

    @Test fun `home songs toolbar keeps tabs and an edge-to-edge list viewport`() {
        verify(HomeSongsToolbarSettingsDialog, scrollIndex = 10, hasBodyHeader = true)
    }

    @Config(qualifiers = "w640dp-h360dp-land")
    @Test fun `landscape toolbar keeps footer visible while its body shrinks`() {
        verify(HomeSongsToolbarSettingsDialog, scrollIndex = 10, hasBodyHeader = true)
    }

    @Test fun `burger add separator stays in the footer above its three standard actions`() {
        verify(BurgerMenuSettingsDialog, scrollIndex = 10, hasExtraFooterAction = true)
    }

    @Test fun `short home tabs list touches both body edges without decorative vertical gaps`() {
        verify(HomeTabsSettingsDialog, scrollIndex = 4, shortListLastIndex = 4)
    }

    private fun verify(
        dialog: Dialog,
        scrollIndex: Int,
        hasExtraFooterAction: Boolean = false,
        hasBodyHeader: Boolean = false,
        shortListLastIndex: Int? = null
    ) {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit()
        composeRule.mainClock.autoAdvance = false
        dialog.showDialog()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) { dialog.Render() }
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        val body = composeRule.onNodeWithTag("dialogFrameBody").getBoundsInRoot()
        val content = composeRule.onNodeWithTag("toggleListDialogContent").getBoundsInRoot()
        val footer = composeRule.onNodeWithTag("dialogFrameFooter").getBoundsInRoot()
        val card = composeRule.onNodeWithTag("dialogFrameCard").getBoundsInRoot()
        composeRule.onNodeWithTag("dialogFrameFooterDivider").assertExists()
        assertTrue("body must remain positive", content.bottom > content.top)
        assertEquals("content touches body top", 0f, (content.top - body.top).value, 1f)
        assertEquals("content touches body bottom", 0f, (body.bottom - content.bottom).value, 1f)
        assertEquals("left reading inset remains", 24f, (content.left - body.left).value, 1f)
        assertEquals("right reading inset remains", 24f, (body.right - content.right).value, 1f)
        val list = composeRule.onNodeWithTag("toggleListDialogList").getBoundsInRoot()
        assertEquals("list viewport touches body bottom", 0f, (body.bottom - list.bottom).value, 1f)
        val firstRow = composeRule.onNodeWithTag("toggleListDialogItem.0").getBoundsInRoot()
        assertEquals("top spacing scrolls inside viewport", 12f, (firstRow.top - list.top).value, 1f)
        shortListLastIndex?.let { index ->
            val lastRow = composeRule.onNodeWithTag("toggleListDialogItem.$index").getBoundsInRoot()
            assertEquals("short-list bottom spacing stays inside viewport", 12f, (list.bottom - lastRow.bottom).value, 1f)
        }
        if (hasBodyHeader) {
            val gap = composeRule.onNodeWithTag("toggleListDialogTabGap").getBoundsInRoot()
            assertEquals("compact tabs to list gap", 8f, (gap.bottom - gap.top).value, 1f)
            assertEquals("gap ends at list viewport", gap.bottom.value, list.top.value, 1f)
            assertTrue("tabs retain their touch target above gap", gap.top > body.top)
        } else {
            assertEquals("list viewport touches body top", 0f, (list.top - body.top).value, 1f)
        }
        assertTrue("footer sits after body", footer.top >= body.bottom)
        assertTrue("footer remains inside card", footer.bottom <= card.bottom)
        val labels = listOf(R.string.reset, R.string.cancel, R.string.dialog_apply).map { context.getString(it) }
        val pills = labels.map { composeRule.onNodeWithText(it).getBoundsInRoot() }
        pills.forEach { assertTrue("action lies in footer: $it", it.top >= footer.top && it.bottom <= footer.bottom) }
        val allFooterPills = if (hasExtraFooterAction) {
            val extra = composeRule.onNodeWithText(context.getString(R.string.add_separator)).getBoundsInRoot()
            val divider = composeRule.onNodeWithTag("dialogFrameFooterDivider").getBoundsInRoot()
            assertTrue("add separator stays inside footer below divider", extra.top >= footer.top && extra.top >= divider.bottom && extra.bottom <= footer.bottom)
            assertTrue("add separator sits above standard row", extra.bottom < pills.minOf { it.top })
            assertEquals("add separator is centered", (footer.left.value + footer.right.value) / 2, (extra.left.value + extra.right.value) / 2, 1f)
            pills + extra
        } else pills
        val topGap = allFooterPills.minOf { it.top.value } - footer.top.value
        val bottomGap = footer.bottom.value - allFooterPills.maxOf { it.bottom.value }
        assertEquals("footer top and bottom space", topGap, bottomGap, 1f)
        composeRule.onNodeWithTag("toggleListDialogList").performScrollToIndex(scrollIndex)
        assertEquals(footer, composeRule.onNodeWithTag("dialogFrameFooter").getBoundsInRoot())
        assertEquals(list, composeRule.onNodeWithTag("toggleListDialogList").getBoundsInRoot())
        composeRule.onNodeWithText(context.getString(R.string.reset)).performSemanticsAction(SemanticsActions.OnClick)
        assertTrue("reset retains the open draft", dialog.isActive)
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performSemanticsAction(SemanticsActions.OnClick)
        assertFalse("cancel dismisses without confirming", dialog.isActive)
    }
}
