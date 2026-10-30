package app.n_zik.android.components.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class CollectionPageStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `feedback clears top player and side rail while its scroll viewport stays stable`() {
        val padding = mutableStateOf(PaddingValues(start = 48.dp, top = 120.dp, bottom = 80.dp))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(300.dp, 400.dp)) {
                    CollectionPageStatus(padding.value) {
                        Box(Modifier.fillMaxSize().testTag("feedback"))
                    }
                }
            }
        }

        val viewport = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val initial = compose.onNodeWithTag("feedback").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport.left + 48f, initial.left, 0f)
        assertEquals(viewport.top + 120f, initial.top, 0f)
        assertEquals(viewport.bottom - 80f, initial.bottom, 0f)

        compose.runOnIdle { padding.value = PaddingValues(end = 48.dp, top = 60.dp) }

        val updated = compose.onNodeWithTag("feedback").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport, compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot)
        assertEquals(viewport.left, updated.left, 0f)
        assertEquals(viewport.right - 48f, updated.right, 0f)
        assertEquals(viewport.top + 60f, updated.top, 0f)
        assertEquals(viewport.bottom, updated.bottom, 0f)
    }
}
