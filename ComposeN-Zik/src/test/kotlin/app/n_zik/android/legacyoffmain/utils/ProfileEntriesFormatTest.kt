package app.n_zik.android.legacyoffmain.utils

import app.it.fast4x.rimusic.utils.parseProfileEntries
import app.it.fast4x.rimusic.utils.parseProfileIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests the profile names file format v2 (spec-profiles-page-face, import/export
 * adaptation): [parseProfileEntries] reads one line per profile — either a bare
 * ID (legacy format) or `id<TAB>display name` — and [parseProfileIds] stays the
 * ID projection of the same parser, so legacy files keep working unchanged.
 */
class ProfileEntriesFormatTest {

    // --- parseProfileEntries

    @Test
    fun `a bare id line is read as an entry without a name`() {
        assertEquals(listOf("work" to ""), parseProfileEntries("work\n"))
    }

    @Test
    fun `an id and a name separated by a tab are read as an entry`() {
        assertEquals(listOf("work" to "Boulot"), parseProfileEntries("work\tBoulot\n"))
    }

    @Test
    fun `the name keeps the rest of the line after the first tab`() {
        // Round-trip stable: the writer emits `id<TAB>name`, so any tab inside the
        // name must survive a read — the split is anchored on the first tab only.
        assertEquals(listOf("work" to "Boulot\txtra"), parseProfileEntries("work\tBoulot\txtra\n"))
    }

    @Test
    fun `lines are trimmed and blank lines are dropped`() {
        assertEquals(listOf("work" to "Boulot"), parseProfileEntries("\n  work\tBoulot  \n\n"))
    }

    @Test
    fun `legacy and v2 lines mix in one file`() {
        assertEquals(listOf("john" to "", "jane" to "Jane D."), parseProfileEntries("john\njane\tJane D.\n"))
    }

    @Test
    fun `crlf line endings are supported`() {
        assertEquals(listOf("work" to "Boulot"), parseProfileEntries("work\tBoulot\r\n"))
    }

    @Test
    fun `a separators-only line is dropped`() {
        assertEquals(emptyList<Pair<String, String>>(), parseProfileEntries("\t\n"))
    }

    @Test
    fun `an unsafe id is dropped even when it carries a name`() {
        assertEquals(emptyList<Pair<String, String>>(), parseProfileEntries("a/b\tName\n"))
    }

    @Test
    fun `the base profile is never an entry`() {
        assertEquals(emptyList<Pair<String, String>>(), parseProfileEntries("default\tNzikFan\n"))
    }

    @Test
    fun `a duplicated id keeps the first entry`() {
        assertEquals(listOf("work" to "Boulot"), parseProfileEntries("work\tBoulot\nwork\tWork\n"))
    }

    // --- parseProfileIds (derived projection)

    @Test
    fun `parseProfileIds follows the entries`() {
        assertEquals(listOf("work", "jane"), parseProfileIds("work\tBoulot\njane\n"))
    }
}
