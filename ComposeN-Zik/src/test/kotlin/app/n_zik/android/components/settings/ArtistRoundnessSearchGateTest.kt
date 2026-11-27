package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.UiInterfaceCard
import app.n_zik.android.components.tab.Search
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf

/** Verifies the interface card's search gate and the display section's row filter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ArtistRoundnessSearchGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val artistRoundnessLabel: String = context.getString(R.string.artist_roundness)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )


    private fun interfaceSettings(query: String) {
        composeRule.setContent {
            val input = remember { mutableStateOf(TextFieldValue(query)) }
            val search = Search(initialVisible = true, inputState = input)
            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalAppearance provides appearance,
            ) {
                UiInterfaceCard(search)
            }
        }
    }

    @Test
    fun artistRoundnessRowIsComposedWhenSearchMatchesItsLabel() {
        interfaceSettings(artistRoundnessLabel)
        assertEquals(1, composeRule.onAllNodesWithText(artistRoundnessLabel).fetchSemanticsNodes().size)
    }

    @Test
    fun artistRoundnessRowIsRemovedWhenSearchMatchesNoCardLabel() {
        interfaceSettings("zzz_no_match_query")
        assertEquals(0, composeRule.onAllNodesWithText(artistRoundnessLabel).fetchSemanticsNodes().size)
    }
}
