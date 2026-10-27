package app.n_zik.android.components.dialog.common

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DialogFrameBandsTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null
    private val palette = ColorPalette.fallback.copy(
        background0 = Color(0xFF391D47), background1 = Color(0xFF693875)
    )
    private val shape = RoundedCornerShape(20.dp)
    private val appearance = Appearance(
        colorPalette = palette, typography = Typography.fallback,
        thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape
    )

    @Before fun isolateProfile() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("dialog_frame_bands_test", context)
        context.preferences.edit().clear().putString(colorPaletteModeKey, ColorPaletteMode.Dark.name).commit()
        composeRule.mainClock.autoAdvance = false
    }

    @After fun restoreProfile() {
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    private fun content(block: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) { block() }
        }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
    }

    @Test fun `normal frame paints alternate header footer and principal body with boundary lines`() {
        verifyColors(pitchBlack = false)
    }

    @Test fun `pitch black frame keeps gray bands around a darker body`() {
        context.preferences.edit().putString(colorPaletteModeKey, ColorPaletteMode.PitchBlack.name).commit()
        verifyColors(pitchBlack = true)
    }

    private fun verifyColors(pitchBlack: Boolean) {
        content {
            DialogFrame(onDismiss = {}, title = "Title", footer = { BasicText("Action") }) {
                Box(Modifier.fillMaxWidth().height(60.dp))
            }
        }
        val alternate = if (pitchBlack) Color(0xFF1A1A1A) else palette.background1
        val principal = if (pitchBlack) Color(0xFF101010) else palette.background0
        listOf("dialogFrameHeader" to alternate, "dialogFrameBody" to principal,
            "dialogFrameFooter" to alternate).forEach { (tag, expected) ->
            val pixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
            // Rounded card corners contain the elevated shadow; sample the center of
            // each band near its edge, clear of both the clipping arc and text.
            val y = if (tag == "dialogFrameFooter") pixels.height - 4 else 4
            assertEquals(tag, expected.toArgb(), pixels[pixels.width / 2, y].toArgb())
        }
        val dividerColors = listOf("dialogFrameHeaderDivider", "dialogFrameFooterDivider").map { tag ->
            val bounds = composeRule.onNodeWithTag(tag).getBoundsInRoot()
            assertTrue("thin visible divider: $bounds", bounds.bottom > bounds.top && bounds.bottom - bounds.top <= 1.dp)
            val pixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
            val color = pixels[pixels.width / 2, 0].toArgb()
            assertTrue("divider differs from its background", color != alternate.toArgb())
            color
        }
        assertEquals("both boundary lines use the same menu divider color", dividerColors.first(), dividerColors.last())
    }

    @Test fun `multiline title centers every line independently of start aligned body`() {
        content {
            DialogFrame(onDismiss = {}, title = "A long title that wraps across several lines on this small screen",
                horizontalAlignment = Alignment.Start) { BasicText("Body") }
        }
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag("dialogFrameTitle").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.single().lineCount > 1)
        assertEquals(TextAlign.Center, results.single().layoutInput.style.textAlign)
        val header = composeRule.onNodeWithTag("dialogFrameHeader").getBoundsInRoot()
        val title = composeRule.onNodeWithTag("dialogFrameTitle").getBoundsInRoot()
        assertEquals((header.left + header.right).value / 2, (title.left + title.right).value / 2, 1f)
    }

    @Test fun `missing title and actions omit their bands and lines`() {
        // This fixture has no marquee or infinite animation: let semantics checks
        // advance recomposition after each optional-slot state change.
        composeRule.mainClock.autoAdvance = true
        val bands = mutableStateOf(false to false)
        content {
            val (title, footer) = bands.value
            DialogFrame(onDismiss = {}, title = if (title) "Title" else null,
                footer = if (footer) ({ BasicText("Action") }) else null) { BasicText("Body") }
        }
        listOf(false to false, true to false, false to true).forEach { (title, footer) ->
            composeRule.runOnIdle { bands.value = title to footer }
            composeRule.mainClock.advanceTimeByFrame()
            listOf("dialogFrameHeader", "dialogFrameHeaderDivider").forEach {
                if (title) composeRule.onNodeWithTag(it).assertExists() else composeRule.onNodeWithTag(it).assertDoesNotExist()
            }
            listOf("dialogFrameFooter", "dialogFrameFooterDivider").forEach {
                if (footer) composeRule.onNodeWithTag(it).assertExists() else composeRule.onNodeWithTag(it).assertDoesNotExist()
            }
        }
    }

    @Config(qualifiers = "w640dp-h360dp-land")
    @Test fun `long scrolling body shrinks while title and footer stay visible and action fires once`() {
        var clicks = 0
        content {
            DialogFrame(onDismiss = {}, title = "Title", footer = {
                ButtonRow(listOf(DialogButton("Confirm", { clicks++ }, primary = true)))
            }) {
                LazyColumn(Modifier.weight(1f, fill = false).testTag("longBody")) {
                    items(100) { BasicText("Item $it", Modifier.height(40.dp)) }
                }
            }
        }
        val header = composeRule.onNodeWithTag("dialogFrameHeader").getBoundsInRoot()
        val footer = composeRule.onNodeWithTag("dialogFrameFooter").getBoundsInRoot()
        val card = composeRule.onNodeWithTag("dialogFrameCard").getBoundsInRoot()
        assertTrue(header.bottom > header.top && footer.bottom > footer.top)
        assertTrue(footer.bottom <= card.bottom)
        composeRule.onNodeWithTag("longBody").performScrollToIndex(99)
        assertEquals(header, composeRule.onNodeWithTag("dialogFrameHeader").getBoundsInRoot())
        assertEquals(footer, composeRule.onNodeWithTag("dialogFrameFooter").getBoundsInRoot())
        composeRule.onNodeWithText("Confirm").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, clicks)
    }
}
