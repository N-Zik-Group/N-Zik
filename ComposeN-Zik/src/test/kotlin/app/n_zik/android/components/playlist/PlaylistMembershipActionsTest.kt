package app.n_zik.android.components.playlist

import app.it.fast4x.rimusic.enums.QueueSwipeAction
import app.it.fast4x.rimusic.models.Playlist
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
            QueueSwipeAction.entries.forEach { action ->
                assertEquals(
                    if (action == QueueSwipeAction.RemoveFromQueue) QueueSwipeAction.NoAction else action,
                    playlistMembershipSwipeAction(playlist, action),
                )
                assertEquals(action, playlistMembershipSwipeAction(playlist.copy(isEditable = true), action))
            }
        }
        val local = Playlist(name = "Local", isEditable = false)
        assertEquals(QueueSwipeAction.RemoveFromQueue,
            playlistMembershipSwipeAction(local, QueueSwipeAction.RemoveFromQueue))
    }
}
