package app.n_zik.android.components.glass

import android.app.Application
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.n_zik.android.R
import app.n_zik.android.components.search.SearchSuggestionEditAction
import app.n_zik.android.components.ui.UiSmallFloatingActionButton
import app.n_zik.android.components.ui.UiButton
import app.n_zik.android.components.ui.UiTextButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MissingIconFeedbackPathsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `filled and text feedback adapters preserve Material layout and suppress only their internal ripple`() {
        val glass = mutableStateOf(false)
        var clicks = 0
        val suppressed = BooleanArray(2)
        compose.setContent {
            val settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance)
            CompositionLocalProvider(LocalAppearance provides Appearance.restore(listOf(null, null, 12f, 8f, 48f)),
                LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Column {
                        UiButton({ clicks++ }, Modifier.testTag("filled.adapter")) {
                            suppressed[0] = LocalRippleConfiguration.current == null
                            Text("Retry")
                        }
                        androidx.compose.material3.Button({}, Modifier.testTag("filled.baseline")) { Text("Retry") }
                        UiTextButton({ clicks++ }, Modifier.testTag("text.adapter")) {
                            suppressed[1] = LocalRippleConfiguration.current == null
                            Text("Export")
                        }
                        androidx.compose.material3.TextButton({}, Modifier.testTag("text.baseline")) { Text("Export") }
                    }
                }
            }
        }
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { glass.value = enabled }
            for (kind in listOf("filled", "text")) {
                val actual = compose.onNodeWithTag("$kind.adapter")
                val baseline = compose.onNodeWithTag("$kind.baseline").fetchSemanticsNode().boundsInRoot
                val bounds = actual.fetchSemanticsNode().boundsInRoot
                assertEquals(baseline.width, bounds.width, .1f)
                assertEquals(baseline.height, bounds.height, .1f)
                actual.performTouchInput { click() }
            }
            compose.runOnIdle { assertEquals(listOf(enabled, enabled), suppressed.toList()) }
        }
        compose.runOnIdle { assertEquals(4, clicks) }
    }

    @Test fun `both themed native icon overloads reserve a bounded platform ripple on the padded canvas`() {
        var rootView: View? = null
        val clicks = IntArray(2)
        compose.setContent {
            val view = LocalView.current
            SideEffect { rootView = view.rootView }
            CompositionLocalProvider(LocalAppearance provides Appearance.restore(listOf(null, null, 12f, 8f, 48f)),
                LocalGlassPreviewSettings provides GlassSettings(enabled = false)) {
                Column {
                    app.it.fast4x.rimusic.ui.components.themed.IconButton(
                        { clicks[0]++ }, R.drawable.play, Color.White,
                        Modifier.testTag("resource.action").padding(11.dp).size(20.dp))
                    app.it.fast4x.rimusic.ui.components.themed.IconButton(
                        { clicks[1]++ }, Icons.Default.Computer, Color.White,
                        Modifier.testTag("vector.action").padding(11.dp).size(20.dp))
                }
            }
        }
        fun ripples(view: View): List<RippleDrawable> = listOfNotNull(view.background as? RippleDrawable) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { ripples(view.getChildAt(it)) } else emptyList())
        listOf("resource.action", "vector.action").forEachIndexed { index, tag ->
            val action = compose.onNodeWithTag(tag)
            action.assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
            action.performTouchInput { down(Offset(1f, center.y)) }
            ShadowLooper.idleMainLooper(160, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeByFrame()
            val width = action.fetchSemanticsNode().boundsInRoot.width
            compose.runOnIdle {
                val pressed = ripples(requireNotNull(rootView)).filter { android.R.attr.state_pressed in it.state }
                assertEquals(1, pressed.size)
                assertEquals(width.toInt(), pressed.single().bounds.width())
                assertEquals(width.toInt(), pressed.single().bounds.height())
                assertTrue(pressed.single().radius >= kotlin.math.sqrt(2f) * width / 2f - .5f)
            }
            action.performTouchInput { up() }
            compose.runOnIdle { assertEquals(1, clicks[index]) }
        }
    }

    @Test fun `suggestion native canvas and small FAB retain one owner and independent glyphs across modes`() {
        val enabled = mutableStateOf(false)
        val radius = mutableStateOf(0f)
        val clicks = IntArray(2)
        var fabRippleSuppressed = false
        compose.setContent {
            val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance)
            CompositionLocalProvider(LocalAppearance provides Appearance.restore(listOf(null, null, 12f, radius.value, 48f)),
                LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Column {
                        SearchSuggestionEditAction({ clicks[0]++ }) {
                            Image(painterResource(R.drawable.pencil), null,
                                Modifier.size(22.dp).testTag("suggestion.glyph"))
                        }
                        UiSmallFloatingActionButton({ clicks[1]++ }, Modifier.testTag("small.fab")) {
                            fabRippleSuppressed = LocalRippleConfiguration.current == null
                            Image(painterResource(R.drawable.play), null,
                                Modifier.size(24.dp).testTag("fab.glyph"))
                        }
                    }
                }
            }
        }
        for (glass in listOf(false, true)) for (roundness in listOf(0f, 8f, 48f)) {
            compose.runOnIdle { enabled.value = glass; radius.value = roundness }
            compose.onAllNodes(hasClickAction()).assertCountEquals(2)
            val suggestion = compose.onAllNodes(hasClickAction())[0]
            val suggestionSize = if (glass) 40.dp else 42.dp
            suggestion.assertWidthIsEqualTo(suggestionSize).assertHeightIsEqualTo(suggestionSize)
            compose.onNodeWithTag("small.fab").assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(40.dp)
            compose.onNodeWithTag("fab.glyph", true).assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
            if (!glass) compose.onNodeWithTag("suggestion.glyph", true)
                .assertWidthIsEqualTo(22.dp).assertHeightIsEqualTo(22.dp)
            suggestion.performTouchInput { click(Offset(1f, center.y)) }
            compose.onNodeWithTag("small.fab").performTouchInput { click(Offset(width - 1f, center.y)) }
            compose.runOnIdle { assertEquals(glass, fabRippleSuppressed) }
        }
        compose.runOnIdle { assertEquals(listOf(6, 6), clicks.toList()) }
    }
}
