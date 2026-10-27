package app.n_zik.android.components.dialog.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class, qualifiers = "w600dp-h640dp")
class SettingsDialogTabRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private fun content(labels: List<String>, width: Dp = 360.dp, fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalDensity provides Density(density.density, fontScale),
            ) {
                MaterialTheme {
                    var selected by remember { mutableIntStateOf(0) }
                    Box(Modifier.width(width)) {
                        SettingsDialogTabRow(labels, selected) { selected = it }
                    }
                }
            }
        }
    }

    @Test
    fun `short category labels are centered with symmetric edge insets and selection`() {
        content(listOf("All", "Favorites", "Disliked"))
        val container = composeRule.onNodeWithTag("settingsDialogTabContainer").getUnclippedBoundsInRoot()
        val row = composeRule.onNodeWithTag("settingsDialogTabRow").getUnclippedBoundsInRoot()
        val first = composeRule.onNodeWithTag("settingsDialogTab0").getUnclippedBoundsInRoot()
        val last = composeRule.onNodeWithTag("settingsDialogTab2").getUnclippedBoundsInRoot()
        assertTrue((row.right - row.left) < (container.right - container.left))
        assertEquals((row.left - container.left).value, (container.right - row.right).value, 1f)
        assertEquals(8f, (first.left - row.left).value, 1f)
        assertEquals(8f, (row.right - last.right).value, 1f)

        composeRule.onNodeWithTag("settingsDialogTab2").performClick().assertIsSelected()
        composeRule.waitForIdle()
        val indicator = composeRule.onNodeWithTag("settingsDialogTabIndicator").getUnclippedBoundsInRoot()
        assertEquals(last.left.value, indicator.left.value, 1f)
        assertEquals((last.right - last.left).value, (indicator.right - indicator.left).value, 1f)
    }

    @Test
    fun `localized labels overflow narrow rows and selecting the last tab scrolls it into view`() {
        content(listOf("Bibliotheque", "Albums favoris", "Albums non aimes"), width = 240.dp)
        assertOverflowAndSelectLast()
    }

    @Test
    fun `large fonts retain horizontal scrolling instead of compressing category labels`() {
        content(listOf("All", "Favorites", "Disliked"), fontScale = 2f)
        assertOverflowAndSelectLast()
    }

    private fun assertOverflowAndSelectLast() {
        val container = composeRule.onNodeWithTag("settingsDialogTabContainer").getUnclippedBoundsInRoot()
        val row = composeRule.onNodeWithTag("settingsDialogTabRow").getUnclippedBoundsInRoot()
        val last = composeRule.onNodeWithTag("settingsDialogTab2").getUnclippedBoundsInRoot()
        assertEquals((container.right - container.left).value, (row.right - row.left).value, 1f)
        assertTrue(last.right > row.right)
        composeRule.onNodeWithTag("settingsDialogTab2").performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settingsDialogTab2").assertIsDisplayed().assertIsSelected()
        val selected = composeRule.onNodeWithTag("settingsDialogTab2").getUnclippedBoundsInRoot()
        assertTrue(selected.right <= row.right)
        assertTrue(selected.left >= row.left)
    }
}
