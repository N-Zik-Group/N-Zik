package app.n_zik.android.ui.items

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TileBookmarkStateTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `batched neutral state never opens a tile observation`() {
        val batchState = mutableStateOf<Boolean?>(null)
        val rowState = MutableStateFlow<Boolean?>(false)
        var rowQueries = 0

        compose.setContent {
            val state = tileBookmarkState("album", batchState.value, observeBookmarkState = false) {
                rowQueries++
                rowState
            }
            BasicText(state.toString())
        }

        compose.onNodeWithText("null").assertIsDisplayed()
        compose.runOnIdle { batchState.value = true }
        compose.onNodeWithText("true").assertIsDisplayed()
        compose.runOnIdle { batchState.value = null }
        compose.onNodeWithText("null").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, rowQueries) }
    }

    @Test
    fun `standalone tile observes its row and reacts to changes`() {
        val rowState = MutableStateFlow<Boolean?>(true)
        val requestedIds = mutableListOf<String>()

        compose.setContent {
            val state = tileBookmarkState("artist", null, observeBookmarkState = true) { id ->
                requestedIds.add(id)
                rowState
            }
            BasicText(state.toString())
        }

        compose.waitUntil(5_000) { compose.onAllNodesWithText("true").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { rowState.value = false }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("false").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(listOf("artist"), requestedIds) }
    }
}
