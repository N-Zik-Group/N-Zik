package app.n_zik.android.legacyoffmain.utils

import app.it.fast4x.rimusic.utils.splitArtistNames
import it.fast4x.innertube.models.ArtistConjunctions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Contract for the legacy [splitArtistNames] (`Utils.kt`, `app.it.fast4x.rimusic.utils`) —
 * the string split shared by the "More of X" menus, the song info screen and the
 * import. Bug 2026-09-26 (device capture 12:02): the localized conjunction word
 * matched as a substring inside a name — "and" inside "Grand" — so
 * "Grand Corps Malade, Kimberose" split into junk fragments ["G", "r", "Corps
 * Malade", "Kimberose"]. The conjunction must now be matched as a whole word;
 * "&" and "," stay delimiters (device captures 2026-09-26 11:40 / 12:02).
 *
 * [ArtistConjunctions.conjunctions] is a mutable global (set from the localized
 * string at app startup) — every test pins it, and the teardown restores the
 * module default.
 */
class SplitArtistNamesOffMainTest {

    @AfterEach
    fun restoreConjunctions() {
        ArtistConjunctions.conjunctions = listOf("and")
    }

    @Test
    fun `conjunction substring inside a name does not split`() {
        ArtistConjunctions.conjunctions = listOf("and")
        assertEquals(
            listOf("Grand Corps Malade", "Kimberose"),
            "Grand Corps Malade, Kimberose".splitArtistNames()
        )
    }

    @Test
    fun `standalone conjunction still splits`() {
        ArtistConjunctions.conjunctions = listOf("and")
        assertEquals(listOf("A", "B"), "A and B".splitArtistNames())
        assertEquals(listOf("A", "B"), "A AND B".splitArtistNames())
    }

    @Test
    fun `french conjunction is whole-word too`() {
        ArtistConjunctions.conjunctions = listOf("et")
        assertEquals(listOf("Petit", "X"), "Petit, X".splitArtistNames())
        assertEquals(listOf("A", "B"), "A et B".splitArtistNames())
    }

    @Test
    fun `ampersand and comma stay delimiters`() {
        ArtistConjunctions.conjunctions = listOf("and")
        assertEquals(listOf("Bigflo", "Oli"), "Bigflo & Oli".splitArtistNames())
        assertEquals(listOf("COOL", "CREATE"), "COOL&CREATE".splitArtistNames())
    }

    @Test
    fun `without conjunctions only ampersand and comma split`() {
        ArtistConjunctions.conjunctions = emptyList()
        assertEquals(listOf("A and B"), "A and B".splitArtistNames())
        assertEquals(listOf("A", "B"), "A, B".splitArtistNames())
    }

    @Test
    fun `cjk names survive the split`() {
        ArtistConjunctions.conjunctions = listOf("and")
        assertEquals(
            listOf("雨良 Amala", "Kimberose"),
            "雨良 Amala, Kimberose".splitArtistNames()
        )
    }

    @Test
    fun `null and blank strings yield no names`() {
        ArtistConjunctions.conjunctions = listOf("and")
        assertEquals(emptyList<String>(), null.splitArtistNames())
        assertEquals(emptyList<String>(), "   ".splitArtistNames())
    }
}
