package app.n_zik.android.intents

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RemoteAlbumLinkTest {
    @Test
    fun `the first track can reference a different edition`() = runTest {
        val editions = mapOf("MPREstandard" to "OLAK5uy_standard", "MPREdeluxe" to "OLAK5uy_deluxe")

        val album = selectVerifiedAlbumEdition("VLOLAK5uy_deluxe", listOf("MPREstandard", "MPREdeluxe")) {
            editions[it]
        }

        assertEquals("MPREdeluxe", album)
    }

    @Test
    fun `an unknown edition does not open the first track's album`() = runTest {
        val album = selectVerifiedAlbumEdition("VLOLAK5uy_deluxe", listOf("MPREstandard")) { "OLAK5uy_standard" }

        assertNull(album)
    }
}
