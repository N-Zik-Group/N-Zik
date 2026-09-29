package app.n_zik.android.legacyoffmain.utils

import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.PROFILE_NAMES_FILE_NAME
import app.it.fast4x.rimusic.utils.isProfileIdSafe
import app.it.fast4x.rimusic.utils.parseProfileIds
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the pure profile-store helpers (spec-profiles-page-face): [parseProfileIds]
 * (the [PROFILE_NAMES_FILE_NAME] content — trim, drop blanks and unsafe lines, first
 * occurrence, file order, the base [DEFAULT_PROFILE_ID] never returned),
 * [resolveProfileDisplayName] (stored name / ID / base default) and [isProfileIdSafe]
 * (a stable ID becomes part of file names, so path separators and the traversal
 * sequences are rejected).
 */
class ProfileInfoTest {

    private val defaultName = "N-Zik Fan"

    // --- parseProfileIds

    @Test
    fun emptyContentYieldsNoProfiles() {
        assertEquals(emptyList<String>(), parseProfileIds(""))
    }

    @Test
    fun blankLinesAreDropped() {
        assertEquals(listOf("john"), parseProfileIds("\n\njohn\n\n"))
    }

    @Test
    fun theLinesAreTrimmed() {
        assertEquals(listOf("john", "jane"), parseProfileIds("  john  \n\tjane\n"))
    }

    @Test
    fun onlyTheFirstOccurrenceOfAnIdIsKept() {
        assertEquals(listOf("john", "jane"), parseProfileIds("john\njane\njohn\n"))
    }

    @Test
    fun theBaseIdIsNeverReturned() {
        assertEquals(listOf("john"), parseProfileIds("default\njohn\n"))
    }

    @Test
    fun theFileOrderIsKept() {
        assertEquals(listOf("zoe", "adam", "carl"), parseProfileIds("zoe\nadam\ncarl\n"))
    }

    @Test
    fun windowsLineEndingsAreHandled() {
        assertEquals(listOf("john", "jane"), parseProfileIds("john\r\njane\r\n"))
    }

    @Test
    fun unsafeLinesAreDroppedSoTheyCannotResolveToADataDirectory() {
        assertEquals(listOf("john"), parseProfileIds("john\na/b\n..\n.\n"))
    }

    // --- resolveProfileDisplayName

    @Test
    fun theStoredNameWinsOverTheId() {
        assertEquals("Alice", resolveProfileDisplayName("alice123", "Alice", defaultName))
    }

    @Test
    fun aBlankStoredNameFallsBackToTheId() {
        assertEquals("alice123", resolveProfileDisplayName("alice123", "   ", defaultName))
    }

    @Test
    fun aMissingStoredNameFallsBackToTheId() {
        assertEquals("alice123", resolveProfileDisplayName("alice123", null, defaultName))
    }

    @Test
    fun theStoredNameIsTrimmedBeforeItIsShown() {
        assertEquals("Alice", resolveProfileDisplayName("alice123", "  Alice  ", defaultName))
    }

    @Test
    fun theBaseProfileFallsBackToTheDefaultName() {
        assertEquals(defaultName, resolveProfileDisplayName(DEFAULT_PROFILE_ID, null, defaultName))
    }

    @Test
    fun aBlankStoredNameOfTheBaseProfileFallsBackToTheDefaultName() {
        assertEquals(defaultName, resolveProfileDisplayName(DEFAULT_PROFILE_ID, "  ", defaultName))
    }

    // --- isProfileIdSafe

    @Test
    fun aPlainNameIsSafe() {
        assertTrue(isProfileIdSafe("john"))
    }

    @Test
    fun aNameWithDotsOrSpacesIsSafe() {
        assertTrue(isProfileIdSafe("john.doe"))
        assertTrue(isProfileIdSafe("john doe"))
    }

    @Test
    fun aDottedNameOtherThanDotDotIsSafe() {
        assertTrue(isProfileIdSafe("a..b"))
    }

    @Test
    fun theForwardSlashIsRejected() {
        assertFalse(isProfileIdSafe("a/b"))
    }

    @Test
    fun theBackslashIsRejected() {
        assertFalse(isProfileIdSafe("a\\b"))
    }

    @Test
    fun theColonIsRejected() {
        assertFalse(isProfileIdSafe("a:b"))
    }

    @Test
    fun theTraversalSequenceIsRejected() {
        assertFalse(isProfileIdSafe(".."))
    }

    @Test
    fun theCurrentDirDotIsRejected() {
        assertFalse(isProfileIdSafe("."))
    }

    @Test
    fun archiveDirectivePrefixesAreRejected() {
        // The profile-state archive reserves the __face__ / __name__ line prefixes:
        // a profile ID starting with "__" would be misparsed as an archive directive.
        assertFalse(isProfileIdSafe("__face__work"))
        assertFalse(isProfileIdSafe("__name__default"))
        assertFalse(isProfileIdSafe("__"))
    }

    @Test
    fun aNameOnlyContainingDashesUnderscoresAfterThePrefixIsStillSafe() {
        assertTrue(isProfileIdSafe("_work"))
        assertTrue(isProfileIdSafe("face_work"))
    }
}
