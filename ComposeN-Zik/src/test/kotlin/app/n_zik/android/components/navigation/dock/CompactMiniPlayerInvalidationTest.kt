package app.n_zik.android.components.navigation.dock

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CompactMiniPlayerInvalidationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `fold updates readers without invalidating unrelated mini content`() {
        val progress = mutableFloatStateOf(0f)
        var readerUpdates = 0
        var unrelatedUpdates = 0
        var observed = 0f
        val reader: (Float) -> Unit = { observed = it; readerUpdates++ }
        val unrelated: () -> Unit = { unrelatedUpdates++ }
        compose.setContent {
            CompositionLocalProvider(LocalCompactMiniPlayerProgress provides progress.floatValue) {
                ProgressConsumer(reader)
                UnrelatedContent(unrelated)
            }
        }
        val initialUnrelated = unrelatedUpdates
        val initialReader = readerUpdates
        for (step in 1..5) compose.runOnIdle { progress.floatValue = step / 5f }
        compose.runOnIdle {
            assertEquals(1f, observed, 0f)
            assertTrue(readerUpdates > initialReader)
            assertEquals(initialUnrelated, unrelatedUpdates)
        }
    }
}

@Composable
private fun ProgressConsumer(record: (Float) -> Unit) {
    val progress = LocalCompactMiniPlayerProgress.current
    SideEffect { record(progress) }
}

@Composable
private fun UnrelatedContent(record: () -> Unit) {
    SideEffect { record() }
}
