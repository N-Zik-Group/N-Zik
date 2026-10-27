package app.n_zik.android.components.settings.common

import android.app.Application
import android.os.Looper
import org.robolectric.Shadows
import java.time.Duration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSearchFeedbackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun targetBlinksExactlyFourTimesAndCancellationClearsHighlight() = runTest {
        val registry = SettingsTargetRegistry()
        val job = launch { registry.blink("target") }
        runCurrent()
        repeat(4) {
            assertEquals("target", registry.highlighted)
            advanceTimeBy(400)
            runCurrent()
            assertNull(registry.highlighted)
            advanceTimeBy(200)
            runCurrent()
        }
        job.join()
        assertNull(registry.highlighted)
        val cancelled = launch { registry.blink("other") }
        runCurrent()
        assertEquals("other", registry.highlighted)
        cancelled.cancel()
        runCurrent()
        assertNull(registry.highlighted)
    }

    private fun advanceComposeTime(milliseconds: Long) {
        composeRule.mainClock.advanceTimeBy(milliseconds)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
    }

    @Test fun noResultToastWaitsForTypingAndDoesNotRepeatOnRecomposition() {
        val query = mutableStateOf("")
        val noResults = mutableStateOf(false)
        val incidental = mutableStateOf(0)
        val messages = mutableListOf<String>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalSettingsSearchMessage provides { messages += it }) {
                incidental.value
                SettingsSearchEmptyFeedback(query.value, noResults.value)
            }
        }
        composeRule.runOnIdle { query.value = "z"; noResults.value = true }
        advanceComposeTime(200)
        composeRule.runOnIdle { query.value = "zz" }
        advanceComposeTime(200)
        composeRule.runOnIdle { query.value = "zzz" }
        advanceComposeTime(200)
        assertEquals(0, messages.size)
        advanceComposeTime(600)
        composeRule.runOnIdle { assertEquals(1, messages.size); incidental.value++ }
        advanceComposeTime(1000)
        composeRule.runOnIdle { assertEquals(1, messages.size); query.value = "match"; noResults.value = false }
        advanceComposeTime(600)
        composeRule.runOnIdle { assertEquals(1, messages.size) }
    }
}
