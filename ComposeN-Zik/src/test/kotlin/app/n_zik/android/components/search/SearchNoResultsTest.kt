package app.n_zik.android.components.search

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SearchNoResultsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun messageTracksTheAvailableAreaWhenTheHeaderAndKeyboardChange() {
        val header = mutableStateOf(120.dp)
        val height = mutableStateOf(560.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.size(320.dp, height.value).padding(top = header.value, bottom = 48.dp)) {
                    Box(Modifier.fillMaxSize().testTag("available_results")) {
                        SearchNoResults(text = "No results found")
                    }
                }
            }
        }
        fun assertCentered() {
            val available = composeRule.onNodeWithTag("available_results").fetchSemanticsNode().boundsInRoot
            val text = composeRule.onNodeWithTag("search_no_results_text").fetchSemanticsNode().boundsInRoot
            assertEquals(available.center.x, text.center.x, 1f)
            assertEquals(available.center.y, text.center.y, 1f)
        }
        assertCentered()
        composeRule.runOnIdle { header.value = 64.dp; height.value = 360.dp }
        assertCentered()
    }
}
