package app.n_zik.android.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Tests [resolveDisplayName]: the shared display-name resolution (spec I/O matrix,
 * NAME_YT / NAME_CUSTOM rows) — youtube source only wins with a logged-in account and a
 * captured name, otherwise the custom name, otherwise the default; never blank.
 */
class DisplayNameResolutionTest {

    private val defaultName = "N-Zik Fan"

    @Test
    fun youtubeSourceResolvesTheAccountNameWhenLoggedIn() {
        assertEquals(
            "Danie",
            resolveDisplayName(source = "youtube", ytLoggedIn = true, ytName = "Danie", customName = "Custom", default = defaultName)
        )
    }

    @Test
    fun youtubeSourceFallsBackToCustomWhenNotLoggedIn() {
        assertEquals(
            "Custom",
            resolveDisplayName(source = "youtube", ytLoggedIn = false, ytName = "Danie", customName = "Custom", default = defaultName)
        )
    }

    @Test
    fun youtubeSourceFallsBackToCustomWhenTheAccountNameIsBlank() {
        assertEquals(
            "Custom",
            resolveDisplayName(source = "youtube", ytLoggedIn = true, ytName = "   ", customName = "Custom", default = defaultName)
        )
    }

    @Test
    fun youtubeSourceFallsBackToDefaultWhenCustomIsBlankToo() {
        assertEquals(
            defaultName,
            resolveDisplayName(source = "youtube", ytLoggedIn = false, ytName = "", customName = "", default = defaultName)
        )
    }

    @Test
    fun customSourceAlwaysUsesTheCustomName() {
        assertEquals(
            "Custom",
            resolveDisplayName(source = "custom", ytLoggedIn = true, ytName = "Danie", customName = "Custom", default = defaultName)
        )
    }

    @Test
    fun customSourceFallsBackToDefaultWhenBlank() {
        assertEquals(
            defaultName,
            resolveDisplayName(source = "custom", ytLoggedIn = true, ytName = "Danie", customName = "  ", default = defaultName)
        )
    }

    @Test
    fun anUnknownSourceFallsBackLikeCustom() {
        assertEquals(
            "Custom",
            resolveDisplayName(source = "unknown", ytLoggedIn = true, ytName = "Danie", customName = "Custom", default = defaultName)
        )
    }

    @Test
    fun theResultIsNeverBlank() {
        val names = listOf(
            resolveDisplayName(source = "youtube", ytLoggedIn = false, ytName = "", customName = "", default = defaultName),
            resolveDisplayName(source = "custom", ytLoggedIn = false, ytName = "", customName = "", default = defaultName),
            resolveDisplayName(source = "youtube", ytLoggedIn = true, ytName = "   ", customName = "  ", default = defaultName)
        )
        names.forEach { name ->
            assertFalse(name.isBlank(), "the display name must never be blank: '$name'")
        }
    }
}
