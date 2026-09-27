package app.n_zik.android.core.migration

import app.it.fast4x.rimusic.models.Artist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the name convergence rules ([NameConvergence]) — the pure
 * functions that carry the frozen rules:
 * - a token is the result of `cleanPrefix` applied to the whole text AND to
 * every token (a leaked prefix inside a token is cleaned and matched as the
 * name itself), split on `,`, trimmed; matching is whole-token and
 * case-insensitive ("Aran" never touches "Aran One");
 * - a copy follows a rename only if its token equals the row's current name
 * (agreement rule), and its `modified:` prefix is kept on replace;
 * - a 1:1 rename is exactly one token changed, the rest identical;
 * - the sweep rewrites a copy with the COMPLETE list of linked row names (link
 * order, cleaned+trimmed names, distinct case-insensitively) when every copy
 * name is backed by a linked row (whole token, or each part of a duet "A & B"
 * with the spaced " & " separator — a bare "&" is never a separator) — repairs
 * prefix leaks, duets, duplicates and completes partial lists; a name without
 * a linked id skips the copy; custom `modified:` copies are never touched;
 * - no prefix is ever inserted at a token position, and no write happens for a
 * text that did not actually change.
 */
class NameConvergenceTest {

    // ---- diffSingleRename (1:1 rename detection) ----

    @Test
    fun singleTokenChangeIsARename() {
        assertEquals(
            "Aran" to "Aran (UK)",
            NameConvergence.diffSingleRename("Aran", "Aran (UK)")
        )
    }

    @Test
    fun oneChangedTokenAmongSeveralIsARename() {
        assertEquals(
            "Aran" to "Aran (UK)",
            NameConvergence.diffSingleRename("Aran, B", "Aran (UK), B")
        )
    }

    @Test
    fun tokenWithSurroundingSpacesIsTrimmed() {
        assertEquals(
            "Aran" to "Aran (UK)",
            NameConvergence.diffSingleRename(" Aran ", "Aran (UK)")
        )
    }

    @Test
    fun reorganizationIsNotARename() {
        // every token moved: more than one position changed
        assertNull(NameConvergence.diffSingleRename("Aran, B", "B, Aran"))
    }

    @Test
    fun severalTokensChangedIsNotARename() {
        assertNull(NameConvergence.diffSingleRename("Aran, B", "C, D"))
    }

    @Test
    fun tokenAddedIsNotARename() {
        assertNull(NameConvergence.diffSingleRename("Aran", "Aran (UK), B"))
    }

    @Test
    fun tokenRemovedIsNotARename() {
        assertNull(NameConvergence.diffSingleRename("Aran, B", "C"))
    }

    @Test
    fun unchangedTextIsNotARename() {
        assertNull(NameConvergence.diffSingleRename("Aran, B", "Aran, B"))
    }

    @Test
    fun caseOnlyChangeIsNotARename() {
        // zero tokens changed case-insensitively: nothing to rename
        assertNull(NameConvergence.diffSingleRename("Aran", "aran"))
    }

    @Test
    fun blankToNonBlankIsNotARename() {
        assertNull(NameConvergence.diffSingleRename("", "Aran"))
    }

    @Test
    fun blankToBlankIsNotARename() {
        assertNull(NameConvergence.diffSingleRename("", ""))
    }

    @Test
    fun diffIgnoresAModifiedPrefixOnTheOldText() {
        assertEquals(
            "Aran" to "Aran (UK)",
            NameConvergence.diffSingleRename("modified:Aran", "Aran (UK)")
        )
    }

    // ---- replaceToken (copy propagation) ----

    @Test
    fun matchingTokenIsReplacedKeepingOtherTokensAndOrder() {
        assertEquals(
            "Aran (UK), B",
            NameConvergence.replaceToken("Aran, B", "Aran", "Aran (UK)")
        )
    }

    @Test
    fun modifiedPrefixOfTheCopyIsKeptOnReplace() {
        // matrix: `modified:Aran` -> `modified:Aran (UK)` (token = row name)
        assertEquals(
            "modified:Aran (UK)",
            NameConvergence.replaceToken("modified:Aran", "Aran", "Aran (UK)")
        )
    }

    @Test
    fun divergentModifiedCopyIsLeftAlone() {
        // matrix: `modified:Aran One` stays (token != row name)
        assertNull(NameConvergence.replaceToken("modified:Aran One", "Aran", "Aran (UK)"))
    }

    @Test
    fun wholeTokenOnlySubstringsAreNeverTouched() {
        // "Aran" must never touch "Aran One"
        assertNull(NameConvergence.replaceToken("Aran One", "Aran", "Aran (UK)"))
        assertNull(NameConvergence.replaceToken("Aran One, B", "Aran", "Aran (UK)"))
    }

    @Test
    fun matchIsCaseInsensitiveWithCanonicalCasing() {
        assertEquals("Aran (UK), B", NameConvergence.replaceToken("ARAN, B", "Aran", "Aran (UK)"))
        assertEquals("Aran (UK)", NameConvergence.replaceToken("aran", "Aran", "Aran (UK)"))
    }

    @Test
    fun allOccurrencesOfTheTokenAreReplaced() {
        assertEquals(
            "Aran (UK), Aran (UK)",
            NameConvergence.replaceToken("Aran, Aran", "Aran", "Aran (UK)")
        )
    }

    @Test
    fun unmatchedTokenYieldsNoWrite() {
        assertNull(NameConvergence.replaceToken("Aran", "B", "C"))
    }

    @Test
    fun identicalTokenYieldsNoWrite() {
        // no actual change: no write
        assertNull(NameConvergence.replaceToken("Aran", "Aran", "Aran"))
    }

    @Test
    fun blankTextYieldsNoWrite() {
        assertNull(NameConvergence.replaceToken("", "Aran", "Aran (UK)"))
    }

    @Test
    fun aPrefixedNewTokenIsCleanedBeforeEmbedding() {
        // a prefixed name must never be inserted at a token position: the copy
        // must stay displayable via cleanPrefix
        assertEquals(
            "Hatsune Miku, B",
            NameConvergence.replaceToken("Aran, B", "Aran", "modified:Hatsune Miku")
        )
        assertEquals(
            "modified:Hatsune Miku",
            NameConvergence.replaceToken("modified:Aran", "Aran", "modified:Hatsune Miku")
        )
    }

    // ---- normalizeText (user input normalization) ----

    @Test
    fun normalizeTextKeepsACleanList() {
        assertEquals("Aran (UK)", NameConvergence.normalizeText("Aran (UK)"))
    }

    @Test
    fun normalizeTextStripsALeadingModifiedPrefix() {
        assertEquals("Aran, B", NameConvergence.normalizeText("modified:Aran, B"))
    }

    @Test
    fun normalizeTextStripsMultiplePrefixes() {
        // anti double-prefix modified:modified:
        assertEquals("Aran", NameConvergence.normalizeText("modified:modified:Aran"))
    }

    @Test
    fun normalizeTextTrimsEveryToken() {
        assertEquals("Aran, B", NameConvergence.normalizeText("  Aran ,\tB  "))
    }

    @Test
    fun normalizeTextCleansAMidStringLeak() {
        assertEquals(
            "Yunosuke, Hatsune Miku",
            NameConvergence.normalizeText("Yunosuke, modified:Hatsune Miku")
        )
    }

    @Test
    fun normalizeTextOnBlankInputIsEmpty() {
        assertEquals("", NameConvergence.normalizeText("   "))
    }

    // ---- convergeText (sweep rule for a song copy) ----

    @Test
    fun caseDriftConvergesToTheRowName() {
        // matrix: copy "ARAN", linked row "Aran" (post-merge) -> copy "Aran"
        assertEquals("Aran", NameConvergence.convergeText("ARAN", listOf("Aran")))
    }

    @Test
    fun rowNameWithModifiedPrefixIsCleanedBeforeTheMatch() {
        // the copy agrees with the row modulo case: the modified: prefix of the
        // row name is cleaned before the comparison
        assertEquals("Aran (UK)", NameConvergence.convergeText("aran (uk)", listOf("modified:Aran (UK)")))
    }

    @Test
    fun aNameWithoutALinkedRowSkipsTheWholeCopy() {
        // matrix: "Aran, NoAki" with only the Aran link -> the copy is skipped
        // (the playback reconcile repairs the link later), unchanged
        assertNull(NameConvergence.convergeText("Aran, NoAki", listOf("Aran")))
        // same for a single name the linked rows do not carry
        assertNull(NameConvergence.convergeText("Aran", listOf("Aran (UK)")))
    }

    @Test
    fun theRewrittenListFollowsTheLinkOrder() {
        // the copy's own token order is discarded: the full list is written in
        // link order
        assertEquals("Aran, B", NameConvergence.convergeText("b, aRaN", listOf("Aran", "B")))
    }

    @Test
    fun aPartialListIsCompletedWithTheMissingLinkedName() {
        // matrix: copy "Aran", links "Aran" + "B" (both valid) -> "Aran, B"
        assertEquals("Aran, B", NameConvergence.convergeText("Aran", listOf("Aran", "B")))
    }

    @Test
    fun aMidStringPrefixLeakIsRepaired() {
        // matrix: copy "Yunosuke, modified:Hatsune Miku", links "Yunosuke" +
        // "modified:Hatsune Miku" -> the leaked token is cleaned, full list written
        assertEquals(
            "Yunosuke, Hatsune Miku",
            NameConvergence.convergeText(
                "Yunosuke, modified:Hatsune Miku",
                listOf("Yunosuke", "modified:Hatsune Miku")
            )
        )
    }

    @Test
    fun customCopyIsNeverTouchedByTheSweep() {
        // matrix: `modified:...` stays whatever the linked rows say
        assertNull(NameConvergence.convergeText("modified:Aran", listOf("Aran (UK)")))
    }

    @Test
    fun copyWithoutLinkedRowsIsUntouched() {
        // matrix: non-empty copy, 0 links -> unchanged (same for rows whose names
        // clean down to nothing)
        assertNull(NameConvergence.convergeText("Aran", emptyList()))
        assertNull(NameConvergence.convergeText("Aran", listOf("")))
    }

    @Test
    fun blankCopyYieldsNoWrite() {
        assertNull(NameConvergence.convergeText("", listOf("Aran")))
    }

    @Test
    fun alreadyConvergedCopyYieldsNoWrite() {
        assertNull(NameConvergence.convergeText("Aran", listOf("Aran")))
        // idempotence: a second sweep over the converged text writes nothing
        assertNull(NameConvergence.convergeText("Aran (UK)", listOf("modified:Aran (UK)")))
    }

    @Test
    fun aCoveredDuetIsWipedAndReplacedByTheCommaList() {
        // matrix: copy "Miyamori Bungaku & Hatsune Miku" (one duet token, spaced
        // " & " separator), links "Miyamori Bungaku" + "modified:Hatsune Miku"
        // -> wipe + full comma list
        assertEquals(
            "Miyamori Bungaku, Hatsune Miku",
            NameConvergence.convergeText(
                "Miyamori Bungaku & Hatsune Miku",
                listOf("Miyamori Bungaku", "modified:Hatsune Miku")
            )
        )
    }

    @Test
    fun aDuetWithAnUnbackedPartSkipsTheWholeCopy() {
        // the real data of that song: Kagamine Len has no linked row -> the duet
        // token is not covered, the copy is skipped (the playback reconcile
        // repairs the link later)
        assertNull(
            NameConvergence.convergeText("Miyamori Bungaku & Kagamine Len", listOf("Miyamori Bungaku"))
        )
    }

    @Test
    fun aBareAmpersandIsNotADuetSeparator() {
        // "COOL&CREATE" has no spaces around the "&": it stays ONE whole token,
        // covered by the row of the same name; the full list is written
        assertEquals(
            "COOL&CREATE, Create",
            NameConvergence.convergeText("COOL&CREATE", listOf("COOL&CREATE", "Create"))
        )
    }

    @Test
    fun duplicatedLinkedRowsYieldTheNameOnce() {
        // a song linked to two rows both named "BlackY" displays the name once:
        // an already-polluted copy is repaired down to the single name
        assertEquals("BlackY", NameConvergence.convergeText("BlackY, BlackY", listOf("BlackY", "BlackY")))
        // ...and a converged copy yields no write
        assertNull(NameConvergence.convergeText("BlackY", listOf("BlackY", "BlackY")))
    }

    @Test
    fun theDistinctListKeepsTheFirstOccurrenceInLinkOrder() {
        // case-insensitive distinct: "SIRIUS" and "SiriuS" are the same name,
        // the first occurrence in link order wins
        assertEquals("SIRIUS", NameConvergence.convergeText("SiriuS", listOf("SIRIUS", "SiriuS")))
    }

    @Test
    fun rowNamesAreTrimmedWhereTheyAreCleaned() {
        // the live database has row names with trailing spaces ("RoughSketch  "):
        // trimmed before the match, so an already-equal copy yields no write...
        assertNull(NameConvergence.convergeText("RoughSketch", listOf("RoughSketch   ")))
        // ...and a copy with trailing spaces is repaired to the trimmed name
        assertEquals("RoughSketch", NameConvergence.convergeText("RoughSketch ", listOf("RoughSketch   ")))
    }

    // ---- skip diagnostics (uncoveredTokens / albumCountMismatch / nameCount) ----

    @Test
    fun uncoveredTokensNamesOnlyTheTokensWithoutALinkedRow() {
        // "Aran (UK)" is backed, "Nobody" is not
        assertEquals(
            listOf("Nobody"),
            NameConvergence.uncoveredTokens("Aran (UK), Nobody", listOf("Aran (UK)"))
        )
        // whole-token match only: "Aran" is NOT backed by the row "Aran (UK)"
        assertEquals(
            listOf("Aran", "Nobody"),
            NameConvergence.uncoveredTokens("Aran, Nobody", listOf("Aran (UK)"))
        )
    }

    @Test
    fun uncoveredTokensIsCaseInsensitiveAndCleansRowNames() {
        // row "modified:Aran (UK)" backs the token "aran (uk)"
        assertTrue(NameConvergence.uncoveredTokens("Aran (UK)", listOf("modified:Aran (UK)")).isEmpty())
        // row name with trailing spaces still backs the trimmed token
        assertTrue(NameConvergence.uncoveredTokens("RoughSketch", listOf("RoughSketch   ")).isEmpty())
    }

    @Test
    fun uncoveredTokensReportsADuetWhenOnePartIsUnbacked() {
        // both parts backed: covered
        assertTrue(NameConvergence.uncoveredTokens("Aran & B", listOf("Aran", "B")).isEmpty())
        // one part unbacked: the WHOLE duet token is reported
        assertEquals(
            listOf("Miyamori Bungaku & Kagamine Len"),
            NameConvergence.uncoveredTokens("Miyamori Bungaku & Kagamine Len", listOf("Miyamori Bungaku"))
        )
    }

    @Test
    fun uncoveredTokensIsEmptyForCustomCopiesBlankCopiesAndMissingRows() {
        assertTrue(NameConvergence.uncoveredTokens("modified:Aran", listOf("Aran")).isEmpty())
        assertTrue(NameConvergence.uncoveredTokens("", listOf("Aran")).isEmpty())
        assertTrue(NameConvergence.uncoveredTokens("Aran", emptyList()).isEmpty())
        assertTrue(NameConvergence.uncoveredTokens("Aran", listOf("")).isEmpty())
    }

    @Test
    fun albumCountMismatchFlagsOnlyNameCountDrift() {
        assertTrue(NameConvergence.albumCountMismatch("Aran", listOf("Aran", "B")))
        assertTrue(NameConvergence.albumCountMismatch("Aran, B", listOf("Aran")))
        assertFalse(NameConvergence.albumCountMismatch("Aran", listOf("Aran")))
        assertFalse(NameConvergence.albumCountMismatch("Aran, B", listOf("Aran", "B")))
        // blank shared names do not count
        assertFalse(NameConvergence.albumCountMismatch("Aran", listOf("Aran", "")))
    }

    @Test
    fun nameCountCountsTheCopyTokensWithTheSharedConvention() {
        assertEquals(1, NameConvergence.nameCount("Aran"))
        assertEquals(2, NameConvergence.nameCount("Aran, B"))
        // a leaked prefix inside a token is cleaned, not a new token
        assertEquals(2, NameConvergence.nameCount("Aran, modified:B"))
        assertEquals(0, NameConvergence.nameCount("  "))
    }

    // ---- convergeAlbumText (sweep rule for an album copy) ----

    @Test
    fun albumCaseDriftConvergesToTheRowName() {
        assertEquals("Aran", NameConvergence.convergeAlbumText("ARAN", listOf("Aran")))
    }

    @Test
    fun albumRefreshesFromTheSharedRowSetAfterANameDiverged() {
        // matrix: album "Aran", every song linked to the row "Aran (UK)" ->
        // the copy is refreshed from the links
        assertEquals("Aran (UK)", NameConvergence.convergeAlbumText("Aran", listOf("Aran (UK)")))
    }

    @Test
    fun albumNeverGainsAName() {
        // one token in the copy, two shared names: the second must not be added
        assertNull(NameConvergence.convergeAlbumText("Aran", listOf("Aran (UK)", "B")))
    }

    @Test
    fun albumNeverLosesAName() {
        // two tokens in the copy, one shared name: the copy would lose a name
        assertNull(NameConvergence.convergeAlbumText("Aran, B", listOf("Aran (UK)")))
    }

    @Test
    fun albumCaseFixKeepsTheUserTokenOrder() {
        // same names modulo case: re-cased in place, order preserved
        assertEquals("B, Aran", NameConvergence.convergeAlbumText("b, ARAN", listOf("Aran", "B")))
    }

    @Test
    fun albumAlreadyConvergedYieldsNoWrite() {
        assertNull(NameConvergence.convergeAlbumText("Aran (UK)", listOf("Aran (UK)")))
    }

    @Test
    fun blankAlbumTextYieldsNoWrite() {
        assertNull(NameConvergence.convergeAlbumText("", listOf("Aran")))
    }

    // ---- uniqueCandidate (dialog candidate resolution) ----

    private fun artist(id: String, name: String) = Artist(id = id, name = name)

    @Test
    fun exactlyOneLinkedRowWithThatNameIsTheCandidate() {
        val row = artist("UC1", "Aran")
        assertEquals(row, NameConvergence.uniqueCandidate(listOf(artist("UC2", "B"), row), "Aran"))
    }

    @Test
    fun noLinkedRowWithThatNameMeansNoCandidate() {
        assertNull(NameConvergence.uniqueCandidate(listOf(artist("UC1", "B")), "NoAki"))
        assertNull(NameConvergence.uniqueCandidate(emptyList(), "Aran"))
    }

    @Test
    fun twoOrMoreLinkedRowsWithThatNameMeansNoCandidate() {
        // ambiguity: in doubt, nothing moves
        assertNull(
            NameConvergence.uniqueCandidate(
                listOf(artist("UC1", "Aran"), artist("UC2", "Aran")),
                "Aran"
            )
        )
    }

    @Test
    fun candidateMatchIsWholeTokenAndCaseInsensitive() {
        // "Aran" does not match the row "Aran One"
        assertNull(NameConvergence.uniqueCandidate(listOf(artist("UC1", "Aran One")), "Aran"))
        // but case does not matter
        assertEquals(
            artist("UC1", "ARAN"),
            NameConvergence.uniqueCandidate(listOf(artist("UC1", "ARAN")), "aran")
        )
    }

    @Test
    fun candidateMatchIgnoresTheRowModifiedPrefix() {
        assertEquals(
            artist("UC1", "modified:Aran"),
            NameConvergence.uniqueCandidate(listOf(artist("UC1", "modified:Aran")), "Aran")
        )
    }

    @Test
    fun candidateMatchIgnoresTrailingSpacesInTheRowName() {
        // the live database has row names with trailing spaces ("RoughSketch  "):
        // the stored name must still match the trimmed copy token
        assertEquals(
            artist("UC1", "Aran  "),
            NameConvergence.uniqueCandidate(listOf(artist("UC1", "Aran  ")), "Aran")
        )
        // a trailing space on the row name must not create ambiguity or a miss
        assertNull(
            NameConvergence.uniqueCandidate(
                listOf(artist("UC1", "Aran"), artist("UC2", "Aran ")),
                "Aran"
            )
        )
    }
}
