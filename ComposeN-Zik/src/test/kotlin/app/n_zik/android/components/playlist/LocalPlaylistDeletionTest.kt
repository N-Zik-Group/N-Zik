package app.n_zik.android.components.playlist

import app.n_zik.android.models.Playlist
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalPlaylistDeletionTest {
    @Test
    fun `only an editable linked playlist supplies a normalized remote deletion id`() {
        val linked = Playlist(name = "Linked", browseId = "VLPLsample", isYoutubePlaylist = true)
        assertEquals("PLsample", linked.youtubeDeletionId())
        assertNull(linked.copy(browseId = "modified:VLPLsample").youtubeDeletionId())
        assertEquals("PLsample", linked.copy(browseId = "PLsample").youtubeDeletionId())
        assertNull(linked.copy(isEditable = false).youtubeDeletionId())
        assertNull(linked.copy(isYoutubePlaylist = false).youtubeDeletionId())
        assertNull(linked.copy(browseId = "VLLM").youtubeDeletionId())
        assertNull(linked.copy(browseId = "MPSPsample").youtubeDeletionId())
    }
}
