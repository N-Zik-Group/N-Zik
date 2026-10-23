package app.n_zik.android.extensions.lastfm

import it.fast4x.lastfm.utils.LastFmAuthUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Tests [LastFmAuthUtils.generateSignature] against the official Last.fm API auth spec (section 8 "Signing Calls"): parameters sorted by key, concatenated as key+value, excluding format/api_sig, with the raw secret appended and the MD5 hex digest in lowercase. */
class LastFmAuthUtilsTest {

    @Test
    fun `official auth spec vector produces the documented signature`() {
        val params = mapOf(
            "api_key" to "xxxxxxxxxx",
            "method" to "auth.getSession",
            "token" to "yyyyyy"
        )

        val signature = LastFmAuthUtils.generateSignature(params, "ilovecher")

        // md5("api_keyxxxxxxxxxxmethodauth.getSessiontokenyyyyyyilovecher")
        assertEquals("b87d61da3cda91a8b6746c4aef55d6f8", signature)
    }

    @Test
    fun `format and api_sig parameters are excluded from the signature`() {
        val base = mapOf(
            "api_key" to "xxxxxxxxxx",
            "method" to "auth.getSession",
            "token" to "yyyyyy"
        )
        val withExtras = mapOf(
            "api_key" to "xxxxxxxxxx",
            "method" to "auth.getSession",
            "token" to "yyyyyy",
            "format" to "json",
            "api_sig" to "stale-value"
        )

        assertEquals(
            LastFmAuthUtils.generateSignature(base, "ilovecher"),
            LastFmAuthUtils.generateSignature(withExtras, "ilovecher")
        )
    }

    @Test
    fun `parameters are concatenated in alphabetical key order regardless of input order`() {
        val forward = mapOf(
            "api_key" to "xxxxxxxxxx",
            "method" to "auth.getSession",
            "token" to "yyyyyy"
        )
        val reversed = linkedMapOf(
            "token" to "yyyyyy",
            "method" to "auth.getSession",
            "api_key" to "xxxxxxxxxx"
        )

        assertEquals(
            LastFmAuthUtils.generateSignature(forward, "ilovecher"),
            LastFmAuthUtils.generateSignature(reversed, "ilovecher")
        )
    }

    @Test
    fun `signature is a 32 character lowercase hex string`() {
        val signature = LastFmAuthUtils.generateSignature(
            mapOf("api_key" to "k", "method" to "auth.getMobileSession"),
            "s"
        )

        assertEquals(32, signature.length)
        assertTrue(signature.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
