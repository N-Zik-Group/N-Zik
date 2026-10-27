package app.n_zik.android.components.ui.screens.home

import android.app.Application
import android.os.Looper
import java.time.Duration
import org.robolectric.Shadows
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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

    @Test
    fun `returning Home restores completed entrance without another loading frame`() {
        val showHome = mutableStateOf(true)
        val renderedReadiness = mutableListOf<Boolean>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val holder = rememberSaveableStateHolder()
            if (showHome.value) {
                holder.SaveableStateProvider("home") {
                    val ready = rememberHomeEntranceReady("bound-service").value
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
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            renderedReadiness.clear()
            showHome.value = true
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertEquals(listOf(true), renderedReadiness) }
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
