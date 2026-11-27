package app.n_zik.android.components.menu.playlist

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.ui.components.MenuState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class PlaylistActionsDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sameFourEntriesKeepTheUnavailableActionsVisible() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val menu = MenuState()
        val source = mutableStateOf(false)
        var imported = 0
        var added = 0
        val shape = RoundedCornerShape(16.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                PlaylistImportAddDialog(
                    visible = true,
                    canAdd = true,
                    canImport = true,
                    importFromCurrentCollection = source.value,
                    menuState = menu,
                    onDismiss = {},
                    onImport = { imported++ },
                    onAdd = { added++ },
                )
            }
        }
        val add = compose.onNodeWithText(app.getString(R.string.playlist_action_add))
        val importCollection = compose.onNodeWithText(app.getString(R.string.playlist_action_import_collection))
        val importFiles = compose.onNodeWithText(app.getString(R.string.playlist_action_import_from_source))
        val export = compose.onNodeWithText(app.getString(R.string.playlist_action_export))
        add.assertIsEnabled().performClick()
        importCollection.assertIsNotEnabled()
        importFiles.assertIsEnabled().performClick()
        export.assertIsNotEnabled()

        compose.runOnIdle { source.value = true }
        importFiles.assertIsNotEnabled()
        importCollection.assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, added)
            assertEquals(2, imported)
        }
    }
}
