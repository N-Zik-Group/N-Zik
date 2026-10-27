package app.n_zik.android.components.search

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SearchEmptyFeedbackTest {
    @get:Rule val composeRule = createComposeRule()

    private fun advance(milliseconds: Long) {
        // State writes from runOnIdle are outside the test coroutine's snapshot interceptor.
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeBy(milliseconds, ignoreFrameDuration = true)
    }

    @Test
    fun emptySearchWaitsExactlyFiveHundredMilliseconds() {
        val messages = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { SearchEmptyFeedback("missing", true) { messages += it } }
        advance(499)
        composeRule.runOnIdle { assertEquals(0, messages.size) }
        advance(1)
        composeRule.runOnIdle { assertEquals(1, messages.size) }
    }

    @Test
    fun onlyFinalTypingToastsAndUnrelatedRecompositionDoesNotRepeatIt() {
        val query = mutableStateOf("")
        val incidental = mutableStateOf(0)
        val messages = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            incidental.value
            SearchEmptyFeedback(query.value, empty = true, showMessage = { messages += it })
        }
        advance(700)
        composeRule.runOnIdle { assertEquals(0, messages.size); query.value = "a" }
        advance(200)
        composeRule.runOnIdle { query.value = "ab" }
        advance(200)
        composeRule.runOnIdle { query.value = "abc" }
        advance(200)
        composeRule.runOnIdle { assertEquals(0, messages.size) }
        advance(600)
        composeRule.runOnIdle { assertEquals(1, messages.size); incidental.value++ }
        advance(1000)
        composeRule.runOnIdle { assertEquals(1, messages.size) }
    }

    @Test
    fun resultsAndClearingCancelPendingFeedback() {
        val query = mutableStateOf("missing")
        val empty = mutableStateOf(true)
        val messages = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SearchEmptyFeedback(query.value, empty.value, showMessage = { messages += it })
        }
        advance(200)
        composeRule.runOnIdle { empty.value = false }
        advance(700)
        composeRule.runOnIdle { assertEquals(0, messages.size); empty.value = true }
        advance(200)
        composeRule.runOnIdle { query.value = "  " }
        advance(700)
        composeRule.runOnIdle { assertEquals(0, messages.size) }
    }

    @Test
    fun loadingOrUnsuccessfulOrStaleResultsCannotAnnounceEmpty() {
        val query = mutableStateOf("old")
        val completedQuery = mutableStateOf<String?>(null)
        val successful = mutableStateOf(false)
        val loading = mutableStateOf(true)
        val messages = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SearchEmptyFeedback(
                query.value,
                empty = successful.value && completedQuery.value == query.value,
                loading = loading.value,
                showMessage = { messages += it }
            )
        }
        advance(700)
        composeRule.runOnIdle { loading.value = false }
        advance(700)
        composeRule.runOnIdle { query.value = "latest"; successful.value = true; completedQuery.value = "old" }
        advance(700)
        composeRule.runOnIdle { assertEquals(0, messages.size); completedQuery.value = "latest" }
        advance(700)
        composeRule.runOnIdle { assertEquals(1, messages.size) }
    }

    @Test
    fun callbackChangesAreCurrentWithoutRestartingTheDebounce() {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        val callback = mutableStateOf<(String) -> Unit>({ first += it })
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { SearchEmptyFeedback("missing", true, showMessage = callback.value) }
        advance(300)
        composeRule.runOnIdle { callback.value = { second += it } }
        advance(300)
        composeRule.runOnIdle { assertEquals(0, first.size); assertEquals(1, second.size) }
    }

    @Test
    fun enteringLoadingCancelsPendingFeedbackAndRestartsOnCompletion() {
        val loading = mutableStateOf(false)
        val messages = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { SearchEmptyFeedback("missing", true, loading.value) { messages += it } }
        advance(200)
        composeRule.runOnIdle { loading.value = true }
        advance(700)
        composeRule.runOnIdle { assertEquals(0, messages.size); loading.value = false }
        advance(200)
        composeRule.runOnIdle { assertEquals(0, messages.size) }
        advance(500)
        composeRule.runOnIdle { assertEquals(1, messages.size) }
    }
}
