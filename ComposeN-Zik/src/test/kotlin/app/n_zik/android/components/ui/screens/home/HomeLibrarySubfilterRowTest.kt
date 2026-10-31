package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class HomeLibrarySubfilterRowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `All to Rewind and YouTube use the same row expansion and padding`() {
        val kind = mutableStateOf<HomeLibrarySubfilter?>(null)
        compose.setContent {
            Column(Modifier.width(240.dp)) {
                HomeLibrarySubfilterRow(kind.value, Modifier.testTag("row")) {
                    Box(Modifier.width(48.dp).height(32.dp).testTag("chip"))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        fun samples(target: HomeLibrarySubfilter): List<Float> {
            compose.runOnIdle {
                kind.value = target
                // Deliver external snapshot writes before advancing the frozen animation clock.
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeByFrame()
            val heights = mutableListOf<Float>()
            for (delta in listOf(16L, 32L, 48L, 128L)) {
                compose.mainClock.advanceTimeBy(delta)
                compose.waitForIdle()
                heights += compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height
            }
            val chip = compose.onNodeWithTag("chip").fetchSemanticsNode().boundsInRoot
            assertEquals(with(compose.density) { 16.dp.toPx() }, chip.left, 0.01f)
            assertEquals(with(compose.density) { 40.dp.toPx() }, heights.last(), 0.01f)
            compose.runOnIdle { kind.value = null; Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeBy(256)
            compose.waitForIdle()
            return heights
        }
        val youtube = samples(HomeLibrarySubfilter.YouTube)
        val rewind = samples(HomeLibrarySubfilter.Rewind)
        youtube.zip(rewind).forEach { (a, b) -> assertEquals(a, b, 0.01f) }
        assertTrue(youtube.first() < youtube.last())
    }

    @Test fun `switching between submenus retains one full height row`() {
        val kind = mutableStateOf<HomeLibrarySubfilter?>(HomeLibrarySubfilter.YouTube)
        compose.setContent {
            Column(Modifier.width(240.dp)) {
                HomeLibrarySubfilterRow(kind.value, Modifier.testTag("row")) {
                    Box(Modifier.width(48.dp).height(32.dp).testTag(it.name))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(256)
        compose.runOnIdle { kind.value = HomeLibrarySubfilter.Rewind; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(with(compose.density) { 40.dp.toPx() },
            compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height, 0.01f)
        compose.onNodeWithTag(HomeLibrarySubfilter.Rewind.name).assertExists()
    }
}
