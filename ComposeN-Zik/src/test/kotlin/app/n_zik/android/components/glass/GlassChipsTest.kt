package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.Backdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassChipsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `custom chips preserve native alpha geometry and clicks without sampling their page`() {
        val glass = mutableStateOf(false)
        val wrapped = mutableStateOf(false)
        var clicks = 0
        val forbiddenPage = object : Backdrop {
            override val isCoordinatesDependent = false
            override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?) { error("Chip sampled its own page") }
        }
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape),
                LocalGlassBackdrop provides forbiddenPage) {
                val fill = Color.Red.copy(alpha = .15f)
                Box(Modifier.size(80.dp, 32.dp).testTag("custom")
                    .then(if (wrapped.value) Modifier.glassChipBackground(fill, shape,
                        GlassSettings(enabled = glass.value, quality = GlassQuality.Balanced))
                    else Modifier.background(fill, shape))
                    .clickable { clicks++ })
            }
        }
        val native = compose.onNodeWithTag("custom").captureToImage().toPixelMap()
        compose.runOnIdle { wrapped.value = true }
        val off = compose.onNodeWithTag("custom").captureToImage().toPixelMap()
        for (y in 0 until off.height) for (x in 0 until off.width) assertEquals(native[x, y], off[x, y])
        compose.runOnIdle { glass.value = true }
        val material = compose.onNodeWithTag("custom").captureToImage()
        assertEquals(native.width, material.width)
        assertEquals(native.height, material.height)
        compose.onNodeWithTag("custom").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun `disabled Glass renders exact native filter and keyword chips in both themes`() {
        val wrapped = mutableStateOf(false)
        val dark = mutableStateOf(true)
        val selected = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Column {
                    val shape = RoundedCornerShape(7.dp)
                    val colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Blue, selectedContainerColor = Color.Red,
                        labelColor = Color.White, selectedLabelColor = Color.Yellow)
                    if (wrapped.value) GlassFilterChip(selected.value, {}, { Text("Filter") },
                        Modifier.testTag("filter"), enabled.value, shape, colors, GlassSettings(enabled = false))
                    else FilterChip(selected.value, {}, { Text("Filter") }, Modifier.testTag("filter"),
                        enabled = enabled.value, shape = shape, colors = colors)
                    val keywordColors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color.Blue, labelColor = Color.Yellow)
                    val border = BorderStroke(1.dp, Color.Red)
                    if (wrapped.value) GlassSuggestionChip({}, { Text("Keyword") },
                        Modifier.testTag("keyword"), enabled.value, shape, keywordColors, border,
                        GlassSettings(enabled = false))
                    else SuggestionChip({}, { Text("Keyword") }, Modifier.testTag("keyword"),
                        enabled = enabled.value, shape = shape, colors = keywordColors, border = border)
                }
            }
        }
        for (isDark in listOf(true, false)) for (isSelected in listOf(true, false)) for (isEnabled in listOf(true, false)) {
            compose.runOnIdle { dark.value = isDark; selected.value = isSelected; enabled.value = isEnabled; wrapped.value = false }
            val expected = listOf("filter", "keyword").map { compose.onNodeWithTag(it).captureToImage().toPixelMap() }
            compose.runOnIdle { wrapped.value = true }
            listOf("filter", "keyword").forEachIndexed { index, tag ->
                val actual = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                assertEquals(expected[index].width, actual.width)
                assertEquals(expected[index].height, actual.height)
                for (y in 0 until actual.height) for (x in 0 until actual.width) {
                    assertEquals("$tag at $x,$y", expected[index][x, y], actual[x, y])
                }
            }
        }
    }

    @Test fun `all area modes retain native bounds selection and disabled tap behavior`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val enabled = mutableStateOf(true)
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape)) {
                Column {
                    val settings = GlassSettings(enabled = true, quality = GlassQuality.Performance,
                        areaModes = mapOf(GlassArea.ActionButtons to mode.value))
                    GlassFilterChip(true, { clicks++ }, { Text("Selected") }, Modifier.testTag("filter"),
                        enabled = enabled.value, settings = settings)
                    GlassSuggestionChip({ clicks++ }, { Text("Keyword") }, Modifier.testTag("keyword"),
                        enabled = enabled.value, settings = settings)
                }
            }
        }
        val tags = listOf("filter", "keyword")
        val original = tags.associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.size }
        GlassAreaMode.entries.forEach { value ->
            compose.runOnIdle { mode.value = value; enabled.value = true }
            tags.forEach { tag ->
                assertEquals(original.getValue(tag), compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.size)
            }
            compose.onNodeWithText("Selected").assertIsSelected().performTouchInput { click() }
            compose.onNodeWithText("Keyword").performTouchInput { click() }
            compose.runOnIdle { enabled.value = false }
            compose.onNodeWithText("Selected").assertIsNotEnabled().performTouchInput { click() }
            compose.onNodeWithText("Keyword").assertIsNotEnabled().performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(GlassAreaMode.entries.size * tags.size, clicks) }
    }
}
