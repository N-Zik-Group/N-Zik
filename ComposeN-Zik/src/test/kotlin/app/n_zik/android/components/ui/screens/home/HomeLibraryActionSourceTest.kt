package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.n_zik.android.models.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class HomeLibraryActionSourceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `playlist tile exposes selection only while selecting`() {
        val selection = mutableStateOf<Boolean?>(null)
        compose.setContent {
            Box(
                Modifier.size(48.dp).testTag("playlist")
                    .playlistSelectionSemantics(selection.value)
                    .clickable { selection.value = selection.value?.not() },
            )
        }
        val tile = compose.onNodeWithTag("playlist")
        tile.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.runOnIdle { selection.value = false }
        tile.assertIsNotSelected()
        tile.performClick()
        tile.assertIsSelected()
    }

    // runBlocking bridges retained playback callbacks to synchronous JUnit assertions.
    @Test fun `retained toolbar resolves current targets once only when invoked`() = runBlocking {
        val targetId = mutableStateOf("first")
        val reads = mutableListOf<String>()
        lateinit var source: HomeSongAction
        var initialized = false
        compose.setContent {
            val id = targetId.value
            val action = rememberHomeSongAction("HomeLibrary") {
                if (id == "pending") null else {
                    { reads.add(id); listOf(Song.makePlaceholder(id)) }
                }
            }
            SideEffect {
                if (initialized) assertSame(source, action) else {
                    source = action
                    initialized = true
                }
            }
        }
        lateinit var first: suspend () -> List<Song>?
        compose.runOnIdle {
            first = requireNotNull(source.snapshot())
            targetId.value = "second"
        }
        compose.waitForIdle()
        lateinit var second: suspend () -> List<Song>?
        compose.runOnIdle { second = requireNotNull(source.snapshot()) }
        assertEquals(emptyList<String>(), reads)
        assertEquals(listOf("first"), first()?.map { it.id })
        assertEquals(listOf("second"), second()?.map { it.id })
        assertEquals(listOf("first", "second"), reads)
        compose.runOnIdle { targetId.value = "pending" }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(source.snapshot()) }
    }
}
