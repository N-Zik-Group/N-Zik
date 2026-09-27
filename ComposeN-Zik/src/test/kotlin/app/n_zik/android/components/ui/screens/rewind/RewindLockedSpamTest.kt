package app.n_zik.android.components.ui.screens.rewind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the locked-period spam rotation ([nextLockedSpamReply]): each tap on a locked
 * month cell or year row answers with the next message of [LOCKED_SPAM_MESSAGES] in order,
 * and the tap that reaches [LOCKED_SPAM_KICK_THRESHOLD] kicks the user back from the home
 * with the final message ("chépa, j'ai dit non, arrêter" — user decision, 2026-09-24).
 */
class RewindLockedSpamTest {

    private val kickRes = 999

    @Test
    fun eachTapAnswersWithTheNextMessageInOrder() {
        assertEquals(LOCKED_SPAM_MESSAGES[0], nextLockedSpamReply(0, LOCKED_SPAM_MESSAGES, kickRes).messageRes)
        assertEquals(LOCKED_SPAM_MESSAGES[1], nextLockedSpamReply(1, LOCKED_SPAM_MESSAGES, kickRes).messageRes)
        assertEquals(LOCKED_SPAM_MESSAGES[2], nextLockedSpamReply(2, LOCKED_SPAM_MESSAGES, kickRes).messageRes)
        assertEquals(LOCKED_SPAM_MESSAGES[3], nextLockedSpamReply(3, LOCKED_SPAM_MESSAGES, kickRes).messageRes)
        assertEquals(LOCKED_SPAM_MESSAGES[4], nextLockedSpamReply(4, LOCKED_SPAM_MESSAGES, kickRes).messageRes)
    }

    @Test
    fun noKickBeforeTheThreshold() {
        (0 until LOCKED_SPAM_KICK_THRESHOLD - 1).forEach { count ->
            assertFalse(
                "tap ${count + 1} should not kick",
                nextLockedSpamReply(count, LOCKED_SPAM_MESSAGES, kickRes).kicks
            )
        }
    }

    @Test
    fun theThresholdThTapKicksWithTheFinalMessage() {
        val reply = nextLockedSpamReply(LOCKED_SPAM_KICK_THRESHOLD - 1, LOCKED_SPAM_MESSAGES, kickRes)
        assertTrue(reply.kicks)
        assertEquals(kickRes, reply.messageRes)
    }

    @Test
    fun customThresholdsAreHonored() {
        val reply = nextLockedSpamReply(1, LOCKED_SPAM_MESSAGES, kickRes, threshold = 2)
        assertTrue(reply.kicks)
        assertEquals(kickRes, reply.messageRes)
    }
}
