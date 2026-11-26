package app.n_zik.android.components.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryBrowseDestinationTest {
    @Test
    fun `an explicit edited origin resolves exact IDs without matching a similar name or ID`() {
        val entries = mapOf(
            "old_browse_id_modified:MPREnew" to "MPREoriginal",
            "old_browse_id_modified:MPREprevious" to "MPREoriginal",
            "old_browse_id_modified:MPREother" to "MPREoriginal-other",
            "unrelated" to "MPREoriginal",
        )

        assertEquals(
            setOf("modified:MPREnew", "modified:MPREprevious"),
            editedBrowseCandidates("MPREoriginal", entries).toSet(),
        )
        assertEquals(
            setOf("modified:MPREnew", "modified:MPREprevious"),
            editedBrowseCandidates("modified:MPREprevious", entries).toSet(),
        )
        assertTrue(editedBrowseCandidates("MPREorigi", entries).isEmpty())
    }

    @Test
    fun `playlist origins match YouTube's raw and VL forms but keep podcast identities distinct`() {
        val entries = mapOf(
            "playlist_original_browse_id_4" to "VLPLsample",
            "playlist_original_browse_id_7" to "MPSPPLsample",
            "playlist_original_browse_id_8" to "",
            "playlist_original_browse_id_bad" to "VLPLsample",
        )

        assertEquals(listOf(4L), editedPlaylistRows("PLsample", entries))
        assertEquals(listOf(4L), editedPlaylistRows("VLPLsample", entries))
        assertEquals(listOf(7L), editedPlaylistRows("MPSPPLsample", entries))
    }
}
