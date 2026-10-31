package app.n_zik.android.components.glass

import android.app.Application
import android.os.Looper
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.TimeUnit

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

    @Test fun `actual chips share Glass press feedback and restore native ripple when global is off`() {
        val global = mutableStateOf(true)
        val material = mutableStateOf(true)
        val keyword = mutableStateOf(false)
        val nativeRipple = RippleConfiguration(color = Color.Red)
        var observed: RippleConfiguration? = nativeRipple
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape),
                LocalRippleConfiguration provides nativeRipple) {
                val settings = GlassSettings(enabled = global.value, quality = GlassQuality.Performance,
                    areaModes = mapOf(GlassArea.ActionButtons to
                        if (material.value) GlassAreaMode.GlassAndContour else GlassAreaMode.Disabled))
                GlassRippleProvider(settings) {
                    Box(Modifier.background(Color.Black).testTag("feedback.host").padding(12.dp)) {
                        val label: @androidx.compose.runtime.Composable () -> Unit = {
                            val configuration = LocalRippleConfiguration.current
                            SideEffect { observed = configuration }
                            Text("Chip")
                        }
                        if (keyword.value) GlassSuggestionChip({ clicks++ }, label,
                            Modifier.testTag("feedback.chip"), settings = settings)
                        else GlassFilterChip(true, { clicks++ }, label,
                            Modifier.testTag("feedback.chip"), settings = settings)
                    }
                }
            }
        }
        for (suggestion in listOf(false, true)) {
            compose.runOnIdle { keyword.value = suggestion; global.value = true; material.value = true }
            compose.mainClock.advanceTimeByFrame()
            val chip = compose.onNodeWithText("Chip")
            val bounds = chip.fetchSemanticsNode().boundsInRoot
            val before = compose.onNodeWithTag("feedback.host").captureToImage().toPixelMap()
            chip.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(800)
            compose.runOnIdle { assertNull("Glass owns the only visible feedback", observed) }
            assertEquals("Jelly keeps native hit bounds", bounds, chip.fetchSemanticsNode().boundsInRoot)
            val held = compose.onNodeWithTag("feedback.host").captureToImage().toPixelMap()
            assertEquals("Feedback stays bounded away from the host corner", before[0, 0], held[0, 0])
            assertTrue("The actual Material chip must paint held feedback",
                (0 until held.height).any { y -> (0 until held.width).any { x -> before[x, y] != held[x, y] } })
            chip.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1600)
            compose.runOnIdle { assertEquals(if (suggestion) 2 else 1, clicks); material.value = false }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertNull("Area OFF keeps the global Glass halo", observed); global.value = false }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertSame("Master OFF restores the native ripple", nativeRipple, observed) }
        }
    }

    @Test fun `chip jelly keeps raw hold after moving outside and returning without triggering a tap`() {
        val keyword = mutableStateOf(false)
        var clicks = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape)) {
                Box(Modifier.size(240.dp, 200.dp).background(Color.Black).testTag("raw.hold.host")
                    .padding(24.dp)) {
                    val settings = GlassSettings(quality = GlassQuality.Performance)
                    if (keyword.value) GlassSuggestionChip({ clicks++ }, { Text("Raw hold") }, settings = settings)
                    else GlassFilterChip(true, { clicks++ }, { Text("Raw hold") }, settings = settings)
                }
            }
        }
        for (suggestion in listOf(false, true)) {
            compose.runOnIdle { keyword.value = suggestion }
            // Commit the replacement chip and place its pointer nodes before dispatching down.
            repeat(2) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
            val chip = compose.onNodeWithText("Raw hold")
            val bounds = chip.fetchSemanticsNode().boundsInRoot
            val resting = compose.onNodeWithTag("raw.hold.host").captureToImage().toPixelMap()
            chip.performTouchInput { down(center) }
            // Complete the stationary long hold before claiming a drag outside the chip.
            shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(800)
            chip.performTouchInput {
                advanceEventTime(800)
                moveTo(center + Offset(0f, 140f), delayMillis = 100)
            }
            compose.mainClock.advanceTimeBy(300)
            val outside = compose.onNodeWithTag("raw.hold.host").captureToImage().toPixelMap()
            assertTrue("A raw press must remain visible after leaving the native chip bounds",
                (0 until outside.height).any { y -> (0 until outside.width).any { x -> outside[x, y] != resting[x, y] } })
            chip.performTouchInput {
                moveTo(center, delayMillis = 100)
            }
            compose.mainClock.advanceTimeBy(800)
            assertEquals("Raw motion must keep the original touch slot", bounds, chip.fetchSemanticsNode().boundsInRoot)
            val held = compose.onNodeWithTag("raw.hold.host").captureToImage().toPixelMap()
            chip.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1600)
            val released = compose.onNodeWithTag("raw.hold.host").captureToImage().toPixelMap()
            val directory = File("build/reports/glass-chip-pixels").apply { mkdirs() }
            for ((name, image) in listOf("resting" to resting, "outside" to outside, "held" to held, "released" to released)) {
                val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
                for (y in 0 until image.height) for (x in 0 until image.width) bitmap.setPixel(x, y, image[x, y].toArgb())
                File(directory, "$suggestion-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            val heldDifference = (0 until held.height).sumOf { y -> (0 until held.width).count { x -> held[x, y] != resting[x, y] } }
            val releasedDifference = (0 until released.height).sumOf { y -> (0 until released.width).count { x -> released[x, y] != resting[x, y] } }
            assertTrue("Feedback must survive Material's out-of-bounds cancellation until physical up; " +
                "suggestion=$suggestion heldVsRest=$heldDifference releasedVsRest=$releasedDifference",
                (0 until held.height).any { y -> (0 until held.width).any { x -> held[x, y] != released[x, y] } })
            compose.runOnIdle { assertEquals("Returning into the chip must not revive its canceled tap", 0, clicks) }
        }
    }

    @Test fun `chip jelly leaves drag cancellation and horizontal row scrolling to the native chip`() {
        val keyword = mutableStateOf(false)
        val wrapped = mutableStateOf(false)
        var observedScroll: ScrollState? = null
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape)) {
                key(keyword.value, wrapped.value) {
                    val scroll = rememberScrollState()
                    SideEffect { observedScroll = scroll }
                    Row(Modifier.size(180.dp, 120.dp).horizontalScroll(scroll)) {
                        val settings = GlassSettings(quality = GlassQuality.Performance)
                        if (!wrapped.value) {
                            if (keyword.value) SuggestionChip({ clicks++ }, { Text("Drag chip") })
                            else FilterChip(true, { clicks++ }, { Text("Drag chip") })
                        } else {
                            if (keyword.value) GlassSuggestionChip({ clicks++ }, { Text("Drag chip") }, settings = settings)
                            else GlassFilterChip(true, { clicks++ }, { Text("Drag chip") }, settings = settings)
                        }
                        Box(Modifier.size(500.dp, 100.dp))
                    }
                }
            }
        }
        for (suggestion in listOf(false, true)) {
            var nativeScroll = 0
            for (glass in listOf(false, true)) {
                compose.runOnIdle { keyword.value = suggestion; wrapped.value = glass }
                compose.onNodeWithText("Drag chip").performTouchInput {
                    down(center)
                    moveTo(center - Offset(100f, 0f), delayMillis = 150)
                    up()
                }
                compose.runOnIdle {
                    val offset = requireNotNull(observedScroll).value
                    assertEquals("A chip drag must cancel its click", 0, clicks)
                    assertTrue("$suggestion/$glass drag must scroll its parent", offset > 0)
                    if (!glass) nativeScroll = offset
                    else assertEquals("Glass preserves the native chip's scrolling", nativeScroll, offset)
                }
            }
        }
    }
}
