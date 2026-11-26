package app.n_zik.android.components.tab

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
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
class ItemSelectorCompletionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `delayed completion keeps the replacement selection`() {
        lateinit var selector: ItemSelector<String>
        compose.setContent {
            val current = ItemSelector<String>()
            SideEffect { selector = current }
        }

        compose.runOnIdle {
            selector.isActive = true
            selector.add("Song being removed")
            val finishRemoval = selector.captureSelectionCompletion()
            selector.isActive = false
            selector.isActive = true
            selector.add("Next selection")

            finishRemoval()

            assertTrue(selector.isActive)
            assertEquals(listOf("Next selection"), selector.toList())

            selector.captureSelectionCompletion()()
            assertFalse(selector.isActive)
            assertTrue(selector.isEmpty())
        }
    }
}
