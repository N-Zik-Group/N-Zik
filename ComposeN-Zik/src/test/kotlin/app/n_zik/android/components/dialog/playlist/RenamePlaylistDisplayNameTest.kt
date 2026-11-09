package app.n_zik.android.components.dialog.playlist

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import app.it.fast4x.rimusic.models.Playlist
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistTable
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RenamePlaylistDisplayNameTest {
    @get:Rule val compose = createComposeRule()

    @After fun cleanup() = unmockkAll()

    @Test
    fun `rename input hides markers while unchanged and renamed saves preserve stored flags and identity`() {
        val rawName = "pinned:monthly:account:modified:Playlist: live"
        val playlist = mutableStateOf(Playlist(42, rawName, browseId = "browse-id", isYoutubePlaylist = true))
        val table = mockk<PlaylistTable>()
        mockkObject(Database)
        every { Database.playlistTable } returns table
        every { Database.asyncTransaction(any(), any()) } answers { secondArg<Database.() -> Unit>().invoke(Database) }
        every { table.update(any()) } answers { playlist.value = firstArg(); 1 }
        lateinit var dialog: RenamePlaylistDialog
        compose.setContent {
            val current = playlist.value
            dialog = RenamePlaylistDialog { current }
        }

        compose.runOnIdle {
            dialog.showDialog()
            assertEquals("Playlist: live", dialog.value.text)
            assertEquals(rawName, playlist.value.name)
            dialog.onSet(dialog.value.text)
            assertEquals(rawName, playlist.value.name)
            dialog.showDialog()
            dialog.onSet("New: playlist")
        }
        compose.runOnIdle {
            assertEquals("pinned:monthly:account:modified:New: playlist", playlist.value.name)
            assertEquals(42L, playlist.value.id)
            assertEquals("browse-id", playlist.value.browseId)
            assertEquals(true, playlist.value.isYoutubePlaylist)
            dialog.showDialog()
            assertEquals("New: playlist", dialog.value.text)
        }
    }
}
