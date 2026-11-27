package app.n_zik.android.components.menu.playlist

import app.n_zik.android.R
import app.n_zik.android.ui.components.MenuState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionAddActionTest {
    @Test
    fun toolbarUsesOneAnchorAndPrefersTheConfiguredAddPosition() {
        assertEquals("add_to_playlist", playlistActionsAnchor(listOf("export_dialog", "add_to_playlist", "import_menu")))
        assertEquals("import_menu", playlistActionsAnchor(listOf("sort", "import_menu", "export_dialog")))
        assertEquals("export", playlistActionsAnchor(listOf("sort", "export"), exportId = "export"))
        assertNull(playlistActionsAnchor(listOf("sort", "sync")))
    }

    @Test
    fun importConfirmationReportsWhyTheTracksAreUnavailable() {
        assertEquals(R.string.common_loading_message, CollectionImportStatus.Loading.unavailableMessage)
        assertEquals(R.string.player_no_song_found, CollectionImportStatus.Empty.unavailableMessage)
        assertEquals(R.string.common_load_failed, CollectionImportStatus.Unavailable.unavailableMessage)
        assertNull(CollectionImportStatus.Ready.unavailableMessage)
        assertEquals(
            listOf(CollectionImportStatus.Loading, CollectionImportStatus.Ready),
            CollectionImportStatus.entries.filter(CollectionImportStatus::canOpenImport),
        )
    }

    @Test
    fun rootPickerBackRestoresTheActionsButDismissDoesNot() {
        val state = MenuState()
        var returns = 0
        state.display(onDestinationOpened = null, onReturn = { returns++ }) {}
        assertTrue(state.canGoBack)
        state.hide()
        assertEquals(0, returns)

        state.display(onDestinationOpened = null, onReturn = { returns++ }) {}
        state.pop()
        assertFalse(state.isDisplayed)
        assertEquals(1, returns)
    }

    @Test
    fun `return from a destination picker restores the choice only for the original menu and source`() {
        val state = MenuState()
        state.display {}
        val sourceEntry = checkNotNull(state.presentationState.second)
        sourceEntry.playlistActionsSourceId = "VLoriginal"
        assertTrue(state.isPlaylistChoiceOpen(sourceEntry, "VLoriginal"))

        state.display {}
        assertFalse(state.isPlaylistChoiceOpen(sourceEntry, "VLoriginal"))
        state.pop()
        val restoredEntry = checkNotNull(state.presentationState.second)
        assertTrue(state.isPlaylistChoiceOpen(restoredEntry, "VLoriginal"))
        assertFalse(state.isPlaylistChoiceOpen(restoredEntry, "VLdifferent"))

        state.hide()
        assertFalse(state.isPlaylistChoiceOpen(sourceEntry, "VLoriginal"))
        state.display {}
        assertFalse(state.isPlaylistChoiceOpen(checkNotNull(state.presentationState.second), "VLoriginal"))
    }
}
