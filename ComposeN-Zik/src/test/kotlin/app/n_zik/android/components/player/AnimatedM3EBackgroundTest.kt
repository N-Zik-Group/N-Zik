package app.n_zik.android.components.player

import androidx.media3.common.Player
import kotlin.random.Random

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import app.n_zik.android.enums.AnimatedGradient

class AnimatedM3EBackgroundTest {
    private val theme = AnimatedGradient.M3EMorphingTheme

    @Test
    fun `buffering retracts unpinned shapes even when playback is requested`() {
        assertTrue(m3ePlaybackIsActive(Player.STATE_READY, isPlaying = true))
        assertFalse(m3ePlaybackIsActive(Player.STATE_READY, isPlaying = false))
        assertFalse(m3ePlaybackIsActive(Player.STATE_BUFFERING, isPlaying = true))
        assertTrue(m3eTrackRetractionTarget(alwaysShowShapes = true) > 0f)
    }

    @Test
    fun `reveal waits for current cover after skip retraction completes`() {
        val occurrences = PlayerM3EOccurrences()
        val first = occurrences.current
        occurrences.present(first, theme)
        occurrences.advance()
        val next = occurrences.current
        occurrences.present(first, theme)

        assertFalse(occurrences.isPresented(next, theme))
        occurrences.present(next, theme)
        assertTrue(occurrences.isPresented(next, theme))
    }

    @Test
    fun `each reveal from fully hidden assigns new positions across the display`() {
        val random = Random(11)
        val initial = requireNotNull(m3eRevealPositions(0f, random))
        val next = requireNotNull(m3eRevealPositions(0.01f, random))

        assertEquals(20, initial.size)
        assertEquals(20, initial.map { (it.x * 5).toInt() to (it.y * 4).toInt() }.toSet().size)
        assertEquals(20, next.map { (it.x * 5).toInt() to (it.y * 4).toInt() }.toSet().size)
        assertNotEquals(initial, next)
    }

    @Test
    fun `reversing a retract while visible retains shape positions`() {
        assertNull(m3eRevealPositions(0.011f, Random(11)))
        assertNull(m3eRevealPositions(0.5f, Random(11)))
    }

    @Test
    fun `mid transition retains half the painted area without dimming`() {
        val scale = m3eShapeScale(0.5f)

        assertEquals(0.5f, scale * scale, 0.0001f)
        assertEquals(m3eShapeAlpha(1f), m3eShapeAlpha(0.5f))
        assertTrue(m3eShapeAlpha(0.05f) < m3eShapeAlpha(0.5f))
        assertEquals(0f, m3eShapeScale(0f))
        assertEquals(0f, m3eShapeAlpha(0f))
    }

    @Test
    fun `always visible tracks retract partially and keep their existing positions`() {
        val hidden = m3eTrackRetractionTarget(alwaysShowShapes = false)
        val visible = m3eTrackRetractionTarget(alwaysShowShapes = true)

        assertEquals(0f, hidden)
        assertTrue(visible > hidden && visible < 1f)
        assertEquals(0.62f, m3eShapeScale(visible), 0.0001f)
        assertNull(m3eRevealPositions(visible, Random(11)))
    }

    @Test
    fun `an occurrence fires once after its cover is presented`() {
        val occurrences = PlayerM3EOccurrences()
        val first = occurrences.current

        assertFalse(occurrences.consume(first, theme))
        occurrences.present(first, theme)
        assertTrue(occurrences.consume(first, theme))

        occurrences.present(first, theme)
        assertFalse(occurrences.consume(first, theme))

        occurrences.advance()
        val next = occurrences.current
        assertFalse(occurrences.consume(next, theme))
        occurrences.present(next, theme)
        assertTrue(occurrences.consume(next, theme))
    }

    @Test
    fun `rapid skips discard earlier acknowledgments and pending impulses`() {
        val occurrences = PlayerM3EOccurrences()
        val first = occurrences.current
        occurrences.present(first, theme)
        occurrences.advance()
        val skipped = occurrences.current
        occurrences.advance()
        val latest = occurrences.current

        occurrences.present(skipped, theme)
        assertFalse(occurrences.consume(first, theme))
        assertFalse(occurrences.consume(skipped, theme))
        assertFalse(occurrences.consume(latest, theme))
        occurrences.present(latest, theme)
        occurrences.present(first, theme)
        assertTrue(occurrences.consume(latest, theme))
    }

    @Test
    fun `a mounted random gradient cannot consume the next renderer's release`() {
        val occurrences = PlayerM3EOccurrences()
        val incoming = AnimatedGradient.M3EMorphingCover
        occurrences.present(occurrences.current, theme)
        occurrences.advance()
        val next = occurrences.current

        occurrences.present(next, incoming)

        assertFalse(occurrences.isPresented(next, theme))
        assertFalse(occurrences.consume(next, theme))
        assertTrue(occurrences.isPresented(next, incoming))
        assertTrue(occurrences.consume(next, incoming))
        assertFalse(occurrences.consume(next, incoming))
    }
}
