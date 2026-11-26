package app.n_zik.android.components.ui.screens.artist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistOverviewSongListIndexTest {
    @Test
    fun `locates the song after earlier headings songs and carousels`() {
        val sections = listOf(
            listOf("opening") to 2,
            emptyList<String>() to 1,
            listOf("another", "target") to 0,
        )

        assertEquals(12, artistOverviewSongListIndex("target", hasInformation = true, sections))
    }

    @Test
    fun `search excludes the information row and filtered out songs`() {
        val sections = listOf(listOf("visible") to 0)

        assertEquals(4, artistOverviewSongListIndex("visible", hasInformation = false, sections))
        assertEquals(-1, artistOverviewSongListIndex("filtered", hasInformation = false, sections))
    }
}
