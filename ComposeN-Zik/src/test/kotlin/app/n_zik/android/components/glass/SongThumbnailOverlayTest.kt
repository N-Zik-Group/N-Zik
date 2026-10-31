package app.n_zik.android.components.glass

import android.app.Application
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.getDislikedIcon
import app.it.fast4x.rimusic.utils.getLikedIcon
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.math.abs
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SongThumbnailOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real song retains native thumbnail overlay and offset like pixels in both glass modes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().clear().putBoolean(disableScrollingTextKey, true).commit()
        val glass = mutableStateOf(false)
        val liked = mutableStateOf(true)
        val shape = RoundedCornerShape(8.dp)
        val palette = DefaultDarkColorPalette
        val appearance = Appearance(palette, typographyOf(Color.White, true, false, FontType.Rubik),
            shape, shape, shape)
        compose.setContent {
            val settings = GlassSettings(enabled = glass.value)
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides settings) {
                GlassRippleProvider(settings) {
                    Column(Modifier.size(280.dp, 180.dp)) {
                        SongItem(
                            song = Song("local:thumbnail", "Song", "Artist", "1:00", null),
                            isLiked = liked.value, inPlaylist = false, showThumbnail = false,
                            backgroundColor = Color.Black, modifier = Modifier.testTag("song"),
                            thumbnailOverlay = { Cover("actual.cover") },
                        )
                        // Original native cover renderer, including the intentional overlap
                        // of the 12dp like glyph across the cover's bottom-left edge.
                        Box(Modifier.fillMaxWidth().height(Dimensions.thumbnails.song + 16.dp)
                            .background(Color.Black).testTag("reference")) {
                            Box(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                                .size(Dimensions.thumbnails.song)) {
                                Cover("reference.cover")
                                Image(painterResource(if (liked.value) getLikedIcon() else getDislikedIcon()),
                                    null, colorFilter = ColorFilter.tint(
                                        if (liked.value) palette.favoritesIcon else palette.red),
                                    modifier = Modifier.align(Alignment.BottomStart)
                                        .absoluteOffset(x = (-8).dp).padding(2.dp).size(12.dp).clip(shape))
                            }
                        }
                    }
                }
            }
        }
        for (enabled in listOf(false, true)) for (isLiked in listOf(true, false)) {
            compose.runOnIdle { glass.value = enabled; liked.value = isLiked }
            val cover = compose.onNodeWithTag("actual.cover", useUnmergedTree = true)
            cover.assertWidthIsEqualTo(Dimensions.thumbnails.song)
                .assertHeightIsEqualTo(Dimensions.thumbnails.song)
            val actualRow = compose.onNodeWithTag("song").captureToImage().toPixelMap()
            val referenceRow = compose.onNodeWithTag("reference").captureToImage().toPixelMap()
            val coverBounds = cover.fetchSemanticsNode().boundsInRoot
            val rowBounds = compose.onNodeWithTag("song").fetchSemanticsNode().boundsInRoot
            val referenceBounds = compose.onNodeWithTag("reference.cover", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val referenceRoot = compose.onNodeWithTag("reference").fetchSemanticsNode().boundsInRoot
            val margin = ((coverBounds.width / Dimensions.thumbnails.song.value) * 10).roundToInt()
            val actualX = (coverBounds.left - rowBounds.left).roundToInt()
            val actualY = (coverBounds.top - rowBounds.top).roundToInt()
            val expectedX = (referenceBounds.left - referenceRoot.left).roundToInt()
            val expectedY = (referenceBounds.top - referenceRoot.top).roundToInt()
            assertEquals(expectedX, actualX)
            assertEquals(expectedY, actualY)
            var differences = 0
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            val examples = mutableListOf<String>()
            val actualGlyph = mutableSetOf<Pair<Int, Int>>()
            val expectedGlyph = mutableSetOf<Pair<Int, Int>>()
            val glyphCanvasSize = (coverBounds.width / Dimensions.thumbnails.song.value * 16).roundToInt()
            for (y in 0 until coverBounds.height.roundToInt()) {
                for (x in -margin until coverBounds.width.roundToInt()) {
                    val actual = actualRow[actualX + x, actualY + y]
                    val expected = referenceRow[expectedX + x, expectedY + y]
                    // Native clip layers can round antialiased edge channels by one byte.
                    // Keep exact glyph coverage separately from color precision.
                    if (y >= coverBounds.height.roundToInt() - glyphCanvasSize && x < glyphCanvasSize / 2) {
                        // This region has only black/blue/green backgrounds (red channel zero).
                        if (actual.red > 0f) actualGlyph += x to y
                        if (expected.red > 0f) expectedGlyph += x to y
                    }
                    if (listOf(abs(actual.red - expected.red), abs(actual.green - expected.green),
                            abs(actual.blue - expected.blue), abs(actual.alpha - expected.alpha)).any { it > 1f / 255f + .000001f }) {
                        differences++
                        minX = minOf(minX, x); maxX = maxOf(maxX, x)
                        minY = minOf(minY, y); maxY = maxOf(maxY, y)
                        if (examples.size < 8) examples += "($x,$y)=$actual vs $expected"
                    }
                }
            }
            if (differences > 0 || actualGlyph != expectedGlyph) for (tag in listOf("song", "reference")) {
                val file = File(System.getProperty("java.io.tmpdir"), "song-overlay-$tag-$enabled-$isLiked.png")
                file.outputStream().use { out ->
                    compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                        .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            assertEquals("Every covered glyph pixel must survive, including the portion outside the cover",
                expectedGlyph, actualGlyph)
            assertTrue("The original glyph straddles the cover edge", actualGlyph.any { it.first < 0 } && actualGlyph.any { it.first >= 0 })
            val actualGlyphBounds = listOf(actualGlyph.minOf { it.first }, actualGlyph.minOf { it.second },
                actualGlyph.maxOf { it.first }, actualGlyph.maxOf { it.second })
            val expectedGlyphBounds = listOf(expectedGlyph.minOf { it.first }, expectedGlyph.minOf { it.second },
                expectedGlyph.maxOf { it.first }, expectedGlyph.maxOf { it.second })
            assertEquals("The complete native glyph retains its original bounds", expectedGlyphBounds, actualGlyphBounds)
            assertEquals("Full cover overlay and uncropped like glyph must retain native pixels; glass=$enabled liked=$isLiked; " +
                "diff=($minX,$minY)-($maxX,$maxY); actualCover=$coverBounds row=$rowBounds; " +
                "referenceCover=$referenceBounds row=$referenceRoot; examples=$examples; images=${System.getProperty("java.io.tmpdir")}",
                0, differences)
            val pixels = cover.captureToImage().toPixelMap()
            assertTrue("The caller's bottom overlay stays above the cover", pixels[pixels.width / 2, pixels.height - 2].green > .9f)
        }
    }

    @Composable private fun BoxScope.Cover(tag: String) {
        Box(Modifier.fillMaxSize().background(Color.Blue).testTag(tag)) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp).background(Color.Green))
            Box(Modifier.align(Alignment.Center).size(8.dp).background(Color.Magenta))
        }
    }
}
