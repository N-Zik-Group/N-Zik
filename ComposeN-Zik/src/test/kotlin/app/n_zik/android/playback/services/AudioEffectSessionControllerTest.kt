package app.n_zik.android.playback.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AudioEffectSessionControllerTest {
    private val announcements = mutableListOf<Pair<Boolean, Int>>()
    private val controller = AudioEffectSessionController { opened, sessionId ->
        announcements += opened to sessionId
    }

    @Test
    fun `invalid startup ids are ignored until a valid session arrives`() {
        controller.sync(-1, true)
        controller.sync(0, true)
        controller.close()
        assertEquals(emptyList<Pair<Boolean, Int>>(), announcements)

        controller.sync(41, true)
        assertEquals(listOf(true to 41), announcements)
    }

    @Test
    fun `unchanged open session is announced once`() {
        repeat(3) { controller.sync(41, true) }

        assertEquals(listOf(true to 41), announcements)
    }

    @Test
    fun `session change while still ready closes old session before opening replacement`() {
        controller.sync(41, true)
        controller.sync(42, true)
        controller.sync(42, true)

        assertEquals(listOf(true to 41, false to 41, true to 42), announcements)
    }

    @Test
    fun `pause closes remembered id and resume reopens current session`() {
        controller.sync(41, true)
        controller.sync(42, false)
        controller.sync(42, false)
        controller.sync(42, true)
        controller.close()
        controller.close()

        assertEquals(
            listOf(true to 41, false to 41, true to 42, false to 42),
            announcements
        )
    }

    @Test
    fun `losing a valid id closes only the previously announced session`() {
        controller.sync(41, true)
        controller.sync(0, true)
        controller.sync(-1, true)
        controller.close()

        assertEquals(listOf(true to 41, false to 41), announcements)
    }

    @Test
    fun `failed open propagates and can retry without a spurious close`() {
        val failure = IllegalStateException("open failed")
        var failOpen = true
        val controller = AudioEffectSessionController { opened, sessionId ->
            if (opened && failOpen) throw failure
            announcements += opened to sessionId
        }

        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            controller.sync(41, true)
        })
        controller.close()
        assertEquals(emptyList<Pair<Boolean, Int>>(), announcements)

        failOpen = false
        controller.sync(41, true)
        controller.close()
        assertEquals(listOf(true to 41, false to 41), announcements)
    }

    @Test
    fun `failed close retains old session and blocks replacement until retry succeeds`() {
        val failure = IllegalStateException("close failed")
        var failClose = true
        val controller = AudioEffectSessionController { opened, sessionId ->
            if (!opened && failClose) throw failure
            announcements += opened to sessionId
        }
        controller.sync(41, true)

        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            controller.sync(42, true)
        })
        assertEquals(listOf(true to 41), announcements)

        failClose = false
        controller.sync(42, true)
        controller.close()
        controller.close()
        assertEquals(
            listOf(true to 41, false to 41, true to 42, false to 42),
            announcements
        )
    }

    @Test
    fun `failed replacement open does not close the old session twice`() {
        val failure = IllegalStateException("replacement open failed")
        var failReplacement = true
        val controller = AudioEffectSessionController { opened, sessionId ->
            if (opened && sessionId == 42 && failReplacement) throw failure
            announcements += opened to sessionId
        }
        controller.sync(41, true)

        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            controller.sync(42, true)
        })
        controller.close()
        assertEquals(listOf(true to 41, false to 41), announcements)

        failReplacement = false
        controller.sync(42, true)
        assertEquals(listOf(true to 41, false to 41, true to 42), announcements)
    }
}
