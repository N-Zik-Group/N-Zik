package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.n_zik.android.components.ui.screens.home.quickpicks.genericYtmSectionItems
import it.fast4x.innertube.requests.HomePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HomeReturnCompositionTest {
    @get:Rule val composeRule = createComposeRule()

    private fun applyChangesAndAdvanceFrame() {
        // runOnIdle writes are outside the test coroutine snapshot interceptor.
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `returning Home restores completed entrance without another loading frame`() {
        val showHome = mutableStateOf(true)
        val renderedReadiness = mutableListOf<Boolean>()
        lateinit var entranceAlpha: State<Float>
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val holder = rememberSaveableStateHolder()
            if (showHome.value) {
                holder.SaveableStateProvider("home") {
                    val ready = rememberHomeEntranceReady("bound-service").value
                    entranceAlpha = rememberHomeEntranceAlpha(ready, animationsEnabled = true)
                    SideEffect { renderedReadiness += ready }
                }
            }
        }
        composeRule.runOnIdle { assertFalse(renderedReadiness.last()) }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 2_000) { renderedReadiness.lastOrNull() == true }
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            assertTrue(renderedReadiness.last())
            showHome.value = false
        }
        applyChangesAndAdvanceFrame()
        composeRule.runOnIdle {
            renderedReadiness.clear()
            showHome.value = true
        }
        applyChangesAndAdvanceFrame()
        composeRule.runOnIdle {
            assertEquals(listOf(true), renderedReadiness)
            assertEquals(0f, entranceAlpha.value, 0.001f)
        }
        composeRule.mainClock.advanceTimeBy(160)
        composeRule.runOnIdle { assertTrue(entranceAlpha.value > 0f && entranceAlpha.value < 1f) }
        composeRule.mainClock.advanceTimeBy(320)
        composeRule.runOnIdle { assertEquals(1f, entranceAlpha.value, 0.001f) }
    }

    @Test
    fun `ordinary recomposition does not restart or advance entrance`() {
        val unrelated = mutableStateOf(0)
        lateinit var entranceAlpha: State<Float>
        var compositions = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            unrelated.value
            entranceAlpha = rememberHomeEntranceAlpha(ready = true, animationsEnabled = true)
            SideEffect { compositions++ }
        }
        composeRule.mainClock.advanceTimeBy(160)
        val beforeRecomposition = entranceAlpha.value
        composeRule.runOnIdle { unrelated.value++ }
        applyChangesAndAdvanceFrame()
        composeRule.runOnIdle {
            assertTrue(entranceAlpha.value >= beforeRecomposition)
            assertEquals(2, compositions)
        }
        composeRule.mainClock.advanceTimeBy(320)
        composeRule.runOnIdle {
            assertEquals(1f, entranceAlpha.value, 0.001f)
            assertEquals(2, compositions)
            unrelated.value++
        }
        applyChangesAndAdvanceFrame()
        composeRule.runOnIdle { assertEquals(1f, entranceAlpha.value, 0.001f) }
    }

    @Test
    fun `disabled animations show ready content immediately but preserve readiness gate`() {
        val ready = mutableStateOf(false)
        lateinit var entranceAlpha: State<Float>
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            entranceAlpha = rememberHomeEntranceAlpha(ready.value, animationsEnabled = false)
        }
        composeRule.runOnIdle {
            assertEquals(0f, entranceAlpha.value, 0.001f)
            ready.value = true
        }
        applyChangesAndAdvanceFrame()
        composeRule.runOnIdle { assertEquals(1f, entranceAlpha.value, 0.001f) }
    }

    @Test
    fun `generic carousel rows outside viewport do not compose`() {
        val page = HomePage(
            sections = List(40) { index ->
                HomePage.Section("Recommendations $index", null, null, null, emptyList())
            },
            chips = null
        )
        val composedTitles = mutableSetOf<String>()
        composeRule.setContent {
            LazyColumn(Modifier.size(width = 320.dp, height = 200.dp)) {
                genericYtmSectionItems(page) { sectionPage ->
                    SideEffect { composedTitles += sectionPage.sections.single().title }
                    Box(Modifier.height(100.dp))
                }
            }
        }
        composeRule.runOnIdle {
            assertTrue(composedTitles.contains("Recommendations 0"))
            assertFalse(composedTitles.contains("Recommendations 39"))
            assertTrue("Composed ${composedTitles.size} rows for a two-row viewport", composedTitles.size < 10)
        }
    }
}
