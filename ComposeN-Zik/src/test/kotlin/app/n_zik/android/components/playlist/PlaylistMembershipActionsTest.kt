package app.n_zik.android.components.playlist

import app.n_zik.android.enums.QueueSwipeAction
import app.n_zik.android.models.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistMembershipActionsTest {
    private val readonly = listOf(
        Playlist(name = "YouTube", browseId = "PLsource", isEditable = false),
        Playlist(name = "Podcast", browseId = "MPSPopaque", isEditable = false),
    )

    @Test fun `select all exposes whole playlist unlink only after selection is cleared`() {
        readonly.forEach { playlist ->
            assertFalse(canOfferPlaylistDeleteAction(playlist, hasSelectedSongs = true))
            assertTrue(canOfferPlaylistDeleteAction(playlist, hasSelectedSongs = false))
            assertTrue(canOfferPlaylistDeleteAction(playlist.copy(isEditable = true), hasSelectedSongs = true))
        }
        assertFalse(canOfferPlaylistDeleteAction(null, hasSelectedSongs = true))
    }

    @Test fun `readonly removes destructive swipe in either direction and keeps every other configured action`() {
        readonly.forEach { playlist ->
            val editable = canEditPlaylistMembershipInView(playlist, forceOnDevice = false)
            val editableCopy = canEditPlaylistMembershipInView(playlist.copy(isEditable = true), forceOnDevice = false)
            QueueSwipeAction.entries.forEach { action ->
                assertEquals(
                    if (action == QueueSwipeAction.RemoveFromQueue) QueueSwipeAction.NoAction else action,
                    playlistMembershipSwipeAction(editable, action),
                )
                assertEquals(action, playlistMembershipSwipeAction(editableCopy, action))
            }
        }
        val local = Playlist(name = "Local", isEditable = false)
        val editable = canEditPlaylistMembershipInView(local, forceOnDevice = false)
        assertEquals(QueueSwipeAction.RemoveFromQueue, playlistMembershipSwipeAction(editable, QueueSwipeAction.RemoveFromQueue))
    }

    @Test fun `opening an editable local playlist from On device suppresses only its removal gesture`() {
        val playlist = Playlist(name = "Local", isEditable = true)
        val editable = canEditPlaylistMembershipInView(playlist, forceOnDevice = false)
        val editableOnDevice = canEditPlaylistMembershipInView(playlist, forceOnDevice = true)
        assertTrue(editable)
        assertFalse(editableOnDevice)

        QueueSwipeAction.entries.forEach { action ->
            assertEquals(action, playlistMembershipSwipeAction(editable, action))
            assertEquals(
                if (action == QueueSwipeAction.RemoveFromQueue) QueueSwipeAction.NoAction else action,
                playlistMembershipSwipeAction(editableOnDevice, action),
            )
        }
    }
}
