package app.n_zik.android.components.dialog.tab

import java.util.Optional
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.util.UnstableApi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.MenuState

@UnstableApi
class DeleteSongDialogTest {
    @Test
    fun `dismissing song deletion returns to the owning menu`() {
        val menuState = MenuState().apply { display {} }
        val dialog = DeleteSongDialog(mutableStateOf(false), menuState, binder = null)
        dialog.song = Optional.of(Song(id = "track", title = "Track", durationText = null, thumbnailUrl = null))
        dialog.onShortClick()

        dialog.onDismiss()

        assertFalse(dialog.isActive)
        assertFalse(dialog.song.isPresent)
        assertTrue(menuState.isDisplayed)
    }
}
