package app.n_zik.android.legacyoffmain.utils

import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.isProfileNameValid
import app.it.fast4x.rimusic.utils.takenProfileNames
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the display-name validation shared by profile creation and rename
 * (spec-profiles-page-face): [isProfileNameValid] — the trimmed candidate is
 * non-blank, file-name safe and not taken (compared trimmed and case-insensitively);
 * [takenProfileNames] — the resolved display names of every profile (the base
 * included) plus the reserved [DEFAULT_PROFILE_ID] (a profile named "default" would
 * share the base data files). In a rename, the profile's own display name is excluded
 * from the taken set, so it is not a collision for itself.
 */
class ProfileRenameValidationTest {

    private val defaultName = "N-Zik Fan"

    // The on-page profiles: "p1" was renamed to "Alice", "p2" was never renamed.
    private val ids = listOf("p1", "p2")
    private val displayNameOf: (String) -> String? = { id -> if (id == "p1") "Alice" else null }

    // Creation validation: every display name on the page + the reserved "default".
    private val creationTaken = takenProfileNames(ids, displayNameOf, defaultName)

    @Test
    fun theTakenSetCoversEveryProfileAndTheReservedDefault() {
        assertTrue(creationTaken.containsAll(listOf("Alice", "p2", defaultName, DEFAULT_PROFILE_ID)))
    }

    // --- creation

    @Test
    fun aBlankNameIsRejected() {
        assertFalse(isProfileNameValid("", creationTaken))
        assertFalse(isProfileNameValid("   ", creationTaken))
    }

    @Test
    fun aNameMatchingAnotherProfileIsRejected() {
        assertFalse(isProfileNameValid("Alice", creationTaken))
        assertFalse(isProfileNameValid("p2", creationTaken))
    }

    @Test
    fun theBaseDisplayNameIsRejected() {
        assertFalse(isProfileNameValid(defaultName, creationTaken))
    }

    @Test
    fun theReservedDefaultIsRejected() {
        assertFalse(isProfileNameValid(DEFAULT_PROFILE_ID, creationTaken))
    }

    @Test
    fun aPathUnsafeNameIsRejected() {
        assertFalse(isProfileNameValid("a/b", creationTaken))
        assertFalse(isProfileNameValid("a:b", creationTaken))
        assertFalse(isProfileNameValid("..", creationTaken))
    }

    @Test
    fun aFreshSafeNameIsAccepted() {
        assertTrue(isProfileNameValid("Danie", creationTaken))
    }

    @Test
    fun aNameMatchingAnotherProfileCaseInsensitivelyIsRejected() {
        assertFalse(isProfileNameValid("alice", creationTaken))
        assertFalse(isProfileNameValid("ALICE", creationTaken))
    }

    @Test
    fun aPaddedNameStillCollidesWithTheTrimmedTakenName() {
        assertFalse(isProfileNameValid(" Alice ", creationTaken))
        assertTrue(isProfileNameValid(" Bob ", creationTaken))
    }

    // --- rename (the profile's own display name is not a collision for itself)

    private val renameTakenForP1 = creationTaken - "Alice"

    @Test
    fun renamingToItsOwnNameIsAllowed() {
        assertTrue(isProfileNameValid("Alice", renameTakenForP1))
    }

    @Test
    fun renamingToAnotherProfileNameIsRejected() {
        assertFalse(isProfileNameValid("p2", renameTakenForP1))
        assertFalse(isProfileNameValid(defaultName, renameTakenForP1))
    }

    @Test
    fun renamingToTheReservedDefaultIsRejected() {
        assertFalse(isProfileNameValid(DEFAULT_PROFILE_ID, renameTakenForP1))
    }

    @Test
    fun renamingToANewSafeNameIsAllowed() {
        assertTrue(isProfileNameValid("Bob", renameTakenForP1))
    }

    @Test
    fun renamingTheBaseProfileToItsDefaultNameIsAllowed() {
        val renameTakenForBase = creationTaken - defaultName
        assertTrue(isProfileNameValid(defaultName, renameTakenForBase))
    }

    @Test
    fun renamingTheBaseProfileToTheReservedDefaultIsRejected() {
        val renameTakenForBase = creationTaken - defaultName
        assertFalse(isProfileNameValid(DEFAULT_PROFILE_ID, renameTakenForBase))
    }
}
