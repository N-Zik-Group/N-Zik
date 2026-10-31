package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.FabItem
import app.it.fast4x.rimusic.ui.components.MultiFabState
import app.it.fast4x.rimusic.ui.components.SmallFloatingActionButtonRow
import app.it.fast4x.rimusic.ui.components.themed.PrimaryButton
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.search.SearchSuggestionEditAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassNativeActionContourTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real native primary small floating and suggestion controls gain only an edge in contour mode`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val clicks = IntArray(3)
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, artistThumbnailShape = shape, uiRoundnessShape = shape)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(quality = GlassQuality.Performance,
                    areaModes = mapOf(GlassArea.ActionButtons to mode.value))) {
                Column {
                    PrimaryButton({ clicks[0]++ }, R.drawable.play, Modifier.testTag("primary"))
                    Box(Modifier.testTag("small")) {
                        SmallFloatingActionButtonRow(FabItem(painterResource(R.drawable.play), "small action") { clicks[1]++ },
                            showLabel = false, stateTransition = updateTransition(MultiFabState.Expanded, label = "test"))
                    }
                    Box(Modifier.testTag("suggestion")) {
                        SearchSuggestionEditAction({ clicks[2]++ }) {
                            Box(Modifier.size(40.dp).background(Color.Magenta).clickable { clicks[2]++ }.testTag("suggestion.action"))
                        }
                    }
                }
            }
        }
        val tags = listOf("primary", "small", "suggestion")
        val nativeBounds = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val native = tags.map { compose.onNodeWithTag(it).captureToImage().toPixelMap() }
        compose.runOnIdle { mode.value = GlassAreaMode.ContourOnly }
        tags.forEachIndexed { index, tag ->
            val node = compose.onNodeWithTag(tag)
            assertEquals(nativeBounds[index], node.fetchSemanticsNode().boundsInRoot)
            val image = node.captureToImage().toPixelMap()
            var changed = 0
            for (y in 0 until image.height) for (x in 0 until image.width) {
                if (image[x, y] != native[index][x, y]) changed++
            }
            assertTrue("$tag must render its contour", changed > 0)
            // The central native fill and glyph remain untouched by a contour-only mode.
            val x = image.width / 2
            val y = image.height / 2
            assertEquals(native[index][x, y], image[x, y])
        }
        compose.onNodeWithTag("primary").performTouchInput { click() }
        compose.onNodeWithContentDescription("small action").performTouchInput { click() }
        compose.onNodeWithTag("suggestion.action").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(1, 1, 1), clicks.toList()) }
    }
}
