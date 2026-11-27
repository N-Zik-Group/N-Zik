package app.n_zik.android.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QueuePanelDragHandleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `queue handle exposes the same expand collapse and dismiss endpoints as dragging`() {
        var fraction = { 0f }
        var dismissals = 0
        compose.setContent {
            val height = remember { Animatable(0.65f) }
            val scope = rememberCoroutineScope()
            fraction = { height.value }
            Box(
                Modifier.size(48.dp).testTag("queue.handle")
                    .queuePanelDragHandle(height, 0.95f, 1000, scope) { dismissals++ }
            )
        }

        val handle = compose.onNodeWithTag("queue.handle")
        handle.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .performSemanticsAction(SemanticsActions.Expand) { it() }
        compose.runOnIdle { assertEquals(0.95f, fraction(), 0.0001f) }
        handle.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .performSemanticsAction(SemanticsActions.Collapse) { it() }
        compose.runOnIdle { assertEquals(0.65f, fraction(), 0.0001f) }
        handle.performSemanticsAction(SemanticsActions.Dismiss) { it() }
        compose.runOnIdle { assertEquals(1, dismissals) }
    }
}
