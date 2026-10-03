package app.n_zik.android.playback.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [SessionMetadataFallbacks]: which session metadata fields
 * count as missing (null / blank / literal "null") and therefore must be
 * replaced by the localized unknown placeholders on the session surfaces.
 */
class SessionMetadataFallbacksTest {

    @Test
    fun `needsFallback is true for null blank and literal-null texts`() {
        assertTrue(SessionMetadataFallbacks.needsFallback(null))
        assertTrue(SessionMetadataFallbacks.needsFallback(""))
        assertTrue(SessionMetadataFallbacks.needsFallback("   "))
        assertTrue(SessionMetadataFallbacks.needsFallback("null"))
    }

    @Test
    fun `needsFallback is false for present texts`() {
        assertFalse(SessionMetadataFallbacks.needsFallback("Artist"))
        assertFalse(SessionMetadataFallbacks.needsFallback("Unknown Artist"))
        assertFalse(SessionMetadataFallbacks.needsFallback("nullified"))
        assertFalse(SessionMetadataFallbacks.needsFallback("title:with:colons"))
    }
}
