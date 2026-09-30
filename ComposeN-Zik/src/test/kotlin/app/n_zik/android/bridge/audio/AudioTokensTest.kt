package app.n_zik.android.bridge.audio

import app.n_zik.android.bridge.AudioQuality
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val TRACK = "dQw4w9WgXcQ"
private const val DEVICE = "Ab3dE5fG7hI"
private const val NOW = 1_790_000_000_000L

class AudioTokensTest {

    private val now = AtomicLong(NOW)
    private val tokens = AudioTokens(clock = now::get)
    private val paired = { id: String -> id == DEVICE }

    private fun forge(quality: AudioQuality = AudioQuality.HIGH, expiresAtMs: Long = NOW + 60_000) =
        tokens.forge(TRACK, quality, DEVICE, expiresAtMs)

    @Test
    fun `a forged token is valid for its track and carries its quality`() {
        val check = tokens.check(forge(AudioQuality.LOW), TRACK, paired)

        assertEquals(AudioTokenCheck.Valid(DEVICE, AudioQuality.LOW, NOW + 60_000), check)
    }

    @Test
    fun `token is URL safe and at most 512 characters`() {
        val token = forge()

        assertTrue(token.length <= 512)
        assertEquals(token, URLEncoder.encode(token, Charsets.UTF_8.name()))
    }

    @Test
    fun `tampered signature, quality or expiry is AUDIO_URL_INVALID`() {
        val token = forge()
        val (device, quality, expiry, mac) = token.split('.')
        val flipped = (if (mac.first() == 'A') 'B' else 'A') + mac.drop(1)

        assertEquals(AudioTokenCheck.Invalid, tokens.check("$device.$quality.$expiry.$flipped", TRACK, paired))
        assertEquals(AudioTokenCheck.Invalid, tokens.check("$device.low.$expiry.$mac", TRACK, paired))
        assertEquals(AudioTokenCheck.Invalid, tokens.check("$device.$quality.${expiry.toLong() + 1}.$mac", TRACK, paired))
    }

    @Test
    fun `malformed or missing token is AUDIO_URL_INVALID`() {
        listOf(null, "", "abc", "a.b.c", "a.b.c.d.e", "$DEVICE.ultra.1.x", "$DEVICE.high.-1.x", "x".repeat(513))
            .forEach { assertEquals(AudioTokenCheck.Invalid, tokens.check(it, TRACK, paired), "token=$it") }
    }

    @Test
    fun `token forged for another track is AUDIO_URL_INVALID`() {
        assertEquals(AudioTokenCheck.Invalid, tokens.check(forge(), "otherTrack1", paired))
    }

    @Test
    fun `token forged before a restart is AUDIO_URL_INVALID with the new key`() {
        val restarted = AudioTokens(clock = now::get)

        assertEquals(AudioTokenCheck.Invalid, restarted.check(forge(), TRACK, paired))
    }

    @Test
    fun `token of a revoked device is DEVICE_REVOKED`() {
        assertEquals(AudioTokenCheck.Revoked, tokens.check(forge(), TRACK) { false })
    }

    @Test
    fun `token past its expiry is AUDIO_URL_EXPIRED, valid up to the exact instant`() {
        val token = forge(expiresAtMs = NOW + 1_000)

        now.set(NOW + 1_000)
        assertTrue(tokens.check(token, TRACK, paired) is AudioTokenCheck.Valid)
        now.set(NOW + 1_001)
        assertEquals(AudioTokenCheck.Expired, tokens.check(token, TRACK, paired))
    }

    @Test
    fun `validation order is invalid, then revoked, then expired`() {
        val token = forge(expiresAtMs = NOW - 1)
        var pairingAsked = false

        // A forged-looking token is invalid before its device is even looked up
        assertEquals(AudioTokenCheck.Invalid, tokens.check(token, "otherTrack1") { pairingAsked = true; false })
        assertEquals(false, pairingAsked)
        // Revoked wins over expired
        assertEquals(AudioTokenCheck.Revoked, tokens.check(token, TRACK) { false })
        assertEquals(AudioTokenCheck.Expired, tokens.check(token, TRACK, paired))
    }

    @Test
    fun `expiry is forge plus duration plus 2 min, capped at 15 min, 10 min when unknown`() {
        assertEquals(NOW + 212_000 + 120_000, tokens.expiryFor(NOW, 212_000))
        assertEquals(NOW + 900_000, tokens.expiryFor(NOW, 780_000))
        assertEquals(NOW + 900_000, tokens.expiryFor(NOW, 3_600_000))
        assertEquals(NOW + 600_000, tokens.expiryFor(NOW, null))
        assertEquals(NOW + 600_000, tokens.expiryFor(NOW, 0))
    }
}
