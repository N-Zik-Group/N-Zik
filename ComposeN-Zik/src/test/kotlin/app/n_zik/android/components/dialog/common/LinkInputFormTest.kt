package app.n_zik.android.components.dialog.common

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LinkInputFormTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun submitUsesTheProvidedLinkThenTheReplacementAndClearDisablesImport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initial = "https://music.youtube.com/watch?v=lvzGnko1zQA"
        val replacement = "https://www.youtube.com/watch?v=trinU3VD1Zo"
        val submissions = mutableListOf<String>()
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                InputTextField(initial, "YouTube link", enabled = true) { submissions += it }
            }
        }

        val importButton = compose.onNodeWithText(context.getString(R.string.common_action_import))
        importButton.performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(replacement)
        importButton.performClick()
        compose.onNodeWithText(context.getString(R.string.common_action_clear)).performClick()
        importButton.assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(initial, replacement), submissions) }
    }
}
