package app.n_zik.android.components.ui.screens.rewind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for the empty-month random replies ([randomEmptyMonthMessage]): every tap on an
 * empty finished month answers with one of the [EMPTY_MONTH_SPAM_MESSAGES] (random, no
 * rotation, no kick — user decision, 2026-09-24).
 */
class RewindEmptyMonthSpamTest {

    @Test
    fun theInjectedIndexSelectsTheMatchingMessage() {
        val messages = intArrayOf(11, 22, 33)
        assertEquals(11, randomEmptyMonthMessage(messages, nextIndex = { 0 }))
        assertEquals(22, randomEmptyMonthMessage(messages, nextIndex = { 1 }))
        assertEquals(33, randomEmptyMonthMessage(messages, nextIndex = { 2 }))
    }

    @Test
    fun indexesAreReducedModuloTheListSize() {
        val messages = intArrayOf(11, 22, 33)
        // 4 % 3 = 1 → the second message, never an out-of-bounds pick
        assertEquals(22, randomEmptyMonthMessage(messages, nextIndex = { 4 }))
    }

    @Test
    fun theBundledMessageListIsNotEmpty() {
        assertTrue(EMPTY_MONTH_SPAM_MESSAGES.isNotEmpty())
    }

    @Test
    fun theDefaultRandomPickerAlwaysReturnsARealMessage() {
        repeat(500) {
            assertTrue(EMPTY_MONTH_SPAM_MESSAGES.contains(randomEmptyMonthMessage(EMPTY_MONTH_SPAM_MESSAGES)))
        }
    }
}
