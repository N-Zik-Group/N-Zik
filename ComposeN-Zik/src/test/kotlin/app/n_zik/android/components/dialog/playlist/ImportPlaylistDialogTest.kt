package app.n_zik.android.components.dialog.playlist

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ImportPlaylistDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `opening and reopening uses the latest source title without overwriting an open draft`() {
        val sourceTitle = mutableStateOf("")
        lateinit var dialog: ImportPlaylistDialog
        compose.setContent {
            val importDialog = ImportPlaylistDialog(initialValue = sourceTitle.value) {}
            SideEffect { dialog = importDialog }
        }

        compose.runOnIdle { sourceTitle.value = "Podcast title" }
        compose.runOnIdle {
            dialog.onShortClick()
            assertTrue(dialog.isActive)
            assertEquals("Podcast title", dialog.value.text)
            dialog.value = TextFieldValue("Draft")
            sourceTitle.value = "Updated podcast title"
        }
        compose.runOnIdle {
            assertEquals("Draft", dialog.value.text)
            dialog.hideDialog()
            assertFalse(dialog.isActive)
            dialog.onShortClick()
            assertTrue(dialog.isActive)
            assertEquals("Updated podcast title", dialog.value.text)
        }
    }
}
