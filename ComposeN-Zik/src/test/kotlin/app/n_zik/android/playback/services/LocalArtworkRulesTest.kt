package app.n_zik.android.playback.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [LocalArtworkRules]: which session artwork URIs are swapped for
 * the signed provider URI (Android Auto home card), and who may read MediaStore art
 * through the exported provider.
 */
class LocalArtworkRulesTest {

    private val authority = "com.nevar.nzik.debug.artwork"

    @Test
    fun `local MediaStore albumart URI is swapped for the provider`() {
        assertTrue(
            LocalArtworkRules.needsProviderArtwork(
                "local:42",
                "content://media/external/audio/albumart/3528816931700557034",
                authority
            )
        )
    }

    @Test
    fun `local resource, file or missing artwork is swapped for the provider`() {
        assertTrue(LocalArtworkRules.needsProviderArtwork("local:42", "android.resource://app/drawable/ic_launcher_box", authority))
        assertTrue(LocalArtworkRules.needsProviderArtwork("local:42", "file:///data/cover.jpg", authority))
        assertTrue(LocalArtworkRules.needsProviderArtwork("local:42", null, authority))
        assertTrue(LocalArtworkRules.needsProviderArtwork("browse/path/local:42", "", authority))
    }

    @Test
    fun `provider URI and remote artwork are kept`() {
        assertFalse(LocalArtworkRules.needsProviderArtwork("local:42", "content://$authority/local%3A42", authority))
        assertFalse(LocalArtworkRules.needsProviderArtwork("local:42", "https://lh3.googleusercontent.com/x", authority))
    }

    @Test
    fun `stream custom cover file is swapped for the provider`() {
        assertTrue(
            LocalArtworkRules.needsProviderArtwork(
                "D-N_-8jq03Y",
                "file:///data/user/0/com.nevar.nzik.debug/files/covers/cover_D-N_-8jq03Y.jpg",
                authority
            )
        )
    }

    @Test
    fun `streams without a custom cover are never rewritten`() {
        assertFalse(LocalArtworkRules.needsProviderArtwork("dQw4w9WgXcQ", "content://media/external/audio/albumart/1", authority))
        assertFalse(LocalArtworkRules.needsProviderArtwork("dQw4w9WgXcQ", null, authority))
    }

    @Test
    fun `song and MediaStore ids are parsed from media ids`() {
        assertEquals("local:42", LocalArtworkRules.songIdOf("songs/local:42"))
        assertEquals(42L, LocalArtworkRules.mediaStoreAudioIdOf("local:42"))
        assertNull(LocalArtworkRules.mediaStoreAudioIdOf("dQw4w9WgXcQ"))
        assertNull(LocalArtworkRules.mediaStoreAudioIdOf("local:notanumber"))
    }

    @Test
    fun `only the app and media renderers may read MediaStore art`() {
        val own = "com.nevar.nzik.debug"
        assertTrue(LocalArtworkRules.isTrustedCaller(own, own))
        assertTrue(LocalArtworkRules.isTrustedCaller(LocalArtworkRules.ANDROID_AUTO_PACKAGE, own))
        assertFalse(LocalArtworkRules.isTrustedCaller("com.example.random", own))
        assertFalse(LocalArtworkRules.isTrustedCaller(null, own))
    }
}
