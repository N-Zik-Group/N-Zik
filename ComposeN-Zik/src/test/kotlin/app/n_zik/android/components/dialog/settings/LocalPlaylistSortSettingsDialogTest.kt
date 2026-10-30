package app.n_zik.android.components.dialog.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class LocalPlaylistSortSettingsDialogTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun hideDialog() {
        LocalPlaylistSortSettingsDialog.hideDialog()
    }

    @Test fun `local playlist sort configurator identifies the playlist detail screen`() {
        val shape = RoundedCornerShape(20.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape,
            uiRoundnessShape = shape,
            artistThumbnailShape = shape
        )
        LocalPlaylistSortSettingsDialog.showDialog()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                LocalPlaylistSortSettingsDialog.Render()
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.local_playlist_sort) + " - Sort")
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.playlists) + " - Sort")
            .assertDoesNotExist()
    }
}
