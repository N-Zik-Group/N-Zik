package app.n_zik.android.ui.components.themed

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.dialog.playlist.NewPlaylistDialog
import app.n_zik.android.components.settings.accounts.isYouTubeSyncEnabled
import app.n_zik.android.components.tab.ItemSelector
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.enums.FontType
import app.n_zik.android.models.Playlist
import app.n_zik.android.ui.components.LocalMenuBack
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class PlaylistsMenuTargetTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the device picker accepts local playlists and shows YouTube sources with a lock`() {
        val local = Playlist(name = "Local")
        val imported = local.copy(browseId = "SPOTIFY_IMPORT")
        val linked = local.copy(browseId = "VLPL123")
        val remote = local.copy(isYoutubePlaylist = true)

        assertTrue(isPlaylistPickerTarget(local, localDestinationsOnly = true))
        assertTrue(isPlaylistPickerTarget(imported, localDestinationsOnly = true))
        assertFalse(isPlaylistPickerTarget(linked, localDestinationsOnly = true))
        assertFalse(isPlaylistPickerTarget(remote, localDestinationsOnly = true))
        assertTrue(isPlaylistPickerTarget(linked, localDestinationsOnly = false))
        assertTrue(isPlaylistPickerTarget(remote, localDestinationsOnly = false))
        val locked = linked.copy(isEditable = false, isYoutubePlaylist = true)
        assertFalse(isPlaylistPickerTarget(locked, localDestinationsOnly = false))
        assertTrue(isPlaylistPickerVisible(locked))
        assertTrue(isPlaylistPickerVisible(linked))
        assertFalse(isPlaylistPickerVisible(locked.copy(isYoutubePlaylist = false)))
    }

    @Test
    fun `a device destination is created locally even when YouTube sync is enabled`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(application, DatabaseInitializer::class.java).build()
        val created = CompletableDeferred<Playlist>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns application
        mockkObject(DatabaseInitializer.Companion, Toaster, Dependencies)
        every { Dependencies.application } returns mainApplication
        mockkStatic(::isYouTubeSyncEnabled)
        every { DatabaseInitializer.Instance } returns database
        every { isYouTubeSyncEnabled() } returns true
        justRun { Toaster.done() }
        try {
            lateinit var dialog: NewPlaylistDialog
            compose.setContent {
                dialog = NewPlaylistDialog(localOnly = true) { created.complete(it) }
            }
            compose.runOnIdle { dialog.onSet("Device playlist") }
            compose.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                created.isCompleted
            }
            val playlist = runBlocking { created.await() }
            assertTrue(isPlaylistPickerTarget(playlist, localDestinationsOnly = true))
            assertEquals(playlist, runBlocking { database.playlistTable.getAll().single() })
            verify(exactly = 0) { isYouTubeSyncEnabled() }
        } finally {
            unmockkStatic(::isYouTubeSyncEnabled)
            unmockkObject(DatabaseInitializer.Companion, Toaster, Dependencies)
            database.close()
        }
    }

    @Test
    fun `a playlist linked after picking reports the stale destination and retains selected tracks`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(application, DatabaseInitializer::class.java).build()
        val load = CompletableDeferred<List<MediaItem>>()
        val unavailableToast = CompletableDeferred<Unit>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns application
        mockkObject(DatabaseInitializer.Companion, Toaster, Dependencies)
        every { Dependencies.application } returns mainApplication
        every { DatabaseInitializer.Instance } returns database
        every { Toaster.w(R.string.playlist_add_destination_unavailable) } answers {
            unavailableToast.complete(Unit)
            Unit
        }
        justRun { Toaster.w(R.string.playlist_add_destination_locked) }
        try {
            val local = Playlist(name = "Device destination")
            val playlistId = runBlocking { database.playlistTable.insert(local) }
            val locked = Playlist(
                name = "Locked YouTube destination",
                browseId = "VLPLlocked",
                isEditable = false,
                isYoutubePlaylist = true,
            )
            val lockedId = runBlocking { database.playlistTable.insert(locked) }
            val menuState = MenuState()
            val parentMenu: @Composable () -> Unit = {}
            val songId = "device-track"
            lateinit var selector: ItemSelector<String>
            lateinit var picker: PlaylistsMenu
            compose.setContent {
                val shape = RoundedCornerShape(24.dp)
                CompositionLocalProvider(
                    LocalMenuState provides menuState,
                    LocalMenuBack provides if (menuState.hasPrevious) menuState::pop else null,
                    LocalAppearance provides Appearance(DefaultDarkColorPalette,
                        typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape),
                ) {
                    selector = ItemSelector()
                    picker = PlaylistsMenu.deferred(
                        navController = rememberNavController(),
                        captureMediaItems = { suspend { load.await() } },
                        onFailure = { error, _ -> throw AssertionError(error) },
                        itemSelector = selector,
                        localDestinationsOnly = true,
                    )
                    if (menuState.isDisplayed) menuState.contentState.second()
                }
            }
            compose.runOnIdle {
                selector.isActive = true
                selector.toggle(songId) { it }
                menuState.display(parentMenu)
                picker.onShortClick()
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(local.name).fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText(application.getString(R.string.playlist_action_add)).assertCountEquals(1)
            compose.onAllNodesWithText(application.getString(R.string.library_playlists_title)).assertCountEquals(0)
            compose.onNodeWithContentDescription(application.getString(R.string.common_action_back)).performClick()
            compose.runOnIdle {
                assertTrue(menuState.isDisplayed)
                assertEquals(parentMenu, menuState.contentState.second)
                picker.onShortClick()
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText(local.name).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(application.getString(R.string.playlist_add_destination_locked))
                .assertExists()
            compose.onNodeWithText(locked.name).performClick()
            verify(exactly = 1) { Toaster.w(R.string.playlist_add_destination_locked) }
            assertTrue(runBlocking { database.songPlaylistMapTable.songIdsInPlaylist(lockedId).isEmpty() })
            compose.onNodeWithText(local.name).performClick()
            runBlocking { database.playlistTable.update(local.copy(id = playlistId, browseId = "VLPL123")) }
            load.complete(listOf(MediaItem.Builder().setMediaId(songId).build()))

            val nextWrite = CompletableDeferred<Unit>()
            Database.asyncTransaction { nextWrite.complete(Unit) }
            compose.waitUntil(5_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                nextWrite.isCompleted && unavailableToast.isCompleted
            }
            verify(exactly = 1) { Toaster.w(R.string.playlist_add_destination_unavailable) }
            assertTrue(runBlocking { database.songPlaylistMapTable.songIdsInPlaylist(playlistId).isEmpty() })
            compose.runOnIdle {
                assertTrue(selector.isActive)
                assertEquals(listOf(songId), selector.toList())
            }
        } finally {
            load.cancel()
            unmockkObject(DatabaseInitializer.Companion, Toaster, Dependencies)
            database.close()
        }
    }
}
