package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class RewindExportMenuHeightTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native list keeps half height after expand and last action reachable`() = checkMenu(false, false)
    @Test fun `native grid keeps half height after expand and last action reachable`() = checkMenu(false, true)
    @Test fun `shared list keeps half height after expand and last action reachable`() = checkMenu(true, false)
    @Test fun `shared grid keeps half height after expand and last action reachable`() = checkMenu(true, true)

    private fun checkMenu(shared: Boolean, grid: Boolean) {
        val host = GlassMenuHostState()
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        var visible by mutableStateOf(false)
        var maxHeightPx = 0f
        compose.setContent {
            maxHeightPx = with(LocalDensity.current) { 320.dp.toPx() }
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassMenuHost provides if (shared) host else null) {
                state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                scope = rememberCoroutineScope()
                Text("Open export options", Modifier.clickable { visible = true })
                val content: @androidx.compose.runtime.Composable () -> Unit = {
                    Box(Modifier.fillMaxWidth().testTag("export-menu")) {
                        if (grid) GridMenu.Menu(title = "Export") {
                            items(20) { index -> GridMenu.Entry(text = "Action $index", icon = {}, onClick = {}) }
                        } else ListMenu.Menu(title = "Export") {
                            repeat(20) { index -> ListMenu.Entry(text = "Action $index", icon = {}, onClick = {}) }
                        }
                    }
                }
                if (shared) {
                    Box {
                        SharedGlassMenuSheet(visible, { visible = false }, state, maxSheetHeight = 320.dp,
                            contentWindowInsets = { WindowInsets(0) }) { content() }
                        GlassDialogOverlayHost(host)
                    }
                } else SampledMenuSheet(visible, { visible = false }, sheetState = state, maxHeightFraction = .5f,
                    dragHandle = {}, contentWindowInsets = { WindowInsets(0) }) { content() }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Open export options").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("the button must open the sheet without manual expansion", state.isVisible) }
        compose.onNodeWithText("Action 0").assertIsDisplayed()
        compose.runOnIdle { scope.launch { state.expand() } }
        compose.waitForIdle()
        val height = compose.onNodeWithTag("export-menu").fetchSemanticsNode().boundsInRoot.height
        assertTrue("expanded surface must remain at most half the screen: $height > $maxHeightPx", height <= maxHeightPx + 1f)
        if (grid) compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Action 19"))
        else compose.onNodeWithText("Action 19").performScrollTo()
        compose.onNodeWithText("Action 19").assertIsDisplayed()
    }
}
