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
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import kotlin.math.abs
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
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
    @Test fun `native short list opens at the bottom`() = checkMenu(false, false, actionCount = 3)
    @Test fun `shared short list opens at the bottom`() = checkMenu(true, false, actionCount = 3)

    private fun checkMenu(shared: Boolean, grid: Boolean, actionCount: Int = 20) {
        val host = GlassMenuHostState()
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        var visible by mutableStateOf(false)
        var viewportHeight = 0
        var contentTop = 0f
        var contentHeight = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassMenuHost provides if (shared) host else null) {
                state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                scope = rememberCoroutineScope()
                Text("Open export options", Modifier.clickable { visible = true })
                val content: @androidx.compose.runtime.Composable () -> Unit = {
                    Box(Modifier.fillMaxWidth().testTag("export-menu").onGloballyPositioned {
                        viewportHeight = it.findRootCoordinates().size.height
                        contentTop = it.positionInRoot().y
                        contentHeight = it.size.height
                    }) {
                        if (grid) GridMenu.Menu(title = "Export") {
                            items(actionCount) { index -> GridMenu.Entry(text = "Action $index", icon = {}, onClick = {}) }
                        } else ListMenu.Menu(title = "Export") {
                            repeat(actionCount) { index -> ListMenu.Entry(text = "Action $index", icon = {}, onClick = {}) }
                        }
                    }
                }
                if (shared) {
                    Box {
                        SharedGlassMenuSheet(visible, { visible = false }, state, maxHeightFraction = .5f,
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
        fun assertBottomPlacement() {
            val contentBottom = contentTop + contentHeight
            assertTrue("surface top should be in the lower half of the window: $contentTop / $viewportHeight",
                contentTop >= viewportHeight / 2f - 1f)
            assertTrue("surface bottom should reach the window bottom: $contentBottom / $viewportHeight",
                abs(contentBottom - viewportHeight) <= 1f)
        }
        compose.runOnIdle { assertBottomPlacement() }
        compose.runOnIdle { scope.launch { state.expand() } }
        compose.waitForIdle()
        val height = compose.onNodeWithTag("export-menu").fetchSemanticsNode().boundsInRoot.height
        val maxHeightPx = viewportHeight / 2f
        assertTrue("expanded surface must remain at most half the screen: $height > $maxHeightPx", height <= maxHeightPx + 1f)
        compose.runOnIdle { assertBottomPlacement() }
        val lastAction = "Action ${actionCount - 1}"
        if (grid) compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(lastAction))
        else compose.onNodeWithText(lastAction).performScrollTo()
        compose.onNodeWithText(lastAction).assertIsDisplayed()
    }
}
