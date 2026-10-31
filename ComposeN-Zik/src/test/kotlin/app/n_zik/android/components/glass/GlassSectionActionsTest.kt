package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
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
class GlassSectionActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `zero hides actions one forms a bubble and two or three share a capsule`() {
        val count = mutableStateOf(0)
        val clicks = IntArray(3)
        compose.setContent {
            AppearanceProvider {
                Box {
                    GlassSectionActions(
                        List(count.value) { index ->
                            GlassSectionAction(R.drawable.play, "action.$index") { clicks[index]++ }
                        },
                        Modifier.testTag("actions"),
                        GlassSettings(quality = GlassQuality.Performance),
                    )
                }
            }
        }
        compose.onNodeWithTag("actions").assertDoesNotExist()
        listOf(1 to 40.dp, 2 to 88.dp, 3 to 132.dp).forEach { (size, width) ->
            compose.runOnIdle { count.value = size }
            compose.onNodeWithTag("actions").assertWidthIsEqualTo(width).assertHeightIsEqualTo(40.dp)
            repeat(size) { index ->
                compose.onNodeWithContentDescription("action.$index").performTouchInput { click() }
            }
        }
        compose.runOnIdle { assertEquals(listOf(3, 2, 1), clicks.toList()) }
    }

    @Test fun `contour preserves native heading geometry and callbacks when material is disabled`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        var clicks = 0
        compose.setContent {
            AppearanceProvider {
                GlassSectionActions(
                    listOf(
                        GlassSectionAction(R.drawable.dice, "shuffle", nativeEndPadding = 12.dp) { clicks++ },
                        GlassSectionAction(R.drawable.chevron_forward, "more") { clicks += 10 },
                    ),
                    Modifier.testTag("actions"),
                    GlassSettings(quality = GlassQuality.Performance,
                        areaModes = mapOf(GlassArea.ActionButtons to mode.value)),
                )
            }
        }
        val nativeBounds = compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("shuffle").performTouchInput { click() }
        compose.runOnIdle { mode.value = GlassAreaMode.ContourOnly }
        assertEquals(nativeBounds, compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("more").performTouchInput { click() }
        compose.runOnIdle { assertEquals(11, clicks) }
        listOf(GlassAreaMode.GlassOnly, GlassAreaMode.GlassAndContour).forEach { materialMode ->
            compose.runOnIdle { mode.value = materialMode }
            compose.onNodeWithTag("actions").assertWidthIsEqualTo(88.dp).assertHeightIsEqualTo(40.dp)
        }
    }

    @Test fun `native section spacing is absorbed by action seats and loading retains the row width`() {
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        val loading = mutableStateOf(false)
        compose.setContent {
            AppearanceProvider {
                GlassSectionActions(List(3) { index ->
                    GlassSectionAction(R.drawable.play, "native.$index",
                        nativeEndPadding = if (index < 2) 12.dp else 0.dp,
                        loading = index == 0 && loading.value) {}
                }, Modifier.testTag("native.actions"), GlassSettings(
                    areaModes = mapOf(GlassArea.ActionButtons to mode.value)))
            }
        }
        for (nativeMode in listOf(GlassAreaMode.Disabled, GlassAreaMode.ContourOnly)) {
            compose.runOnIdle { mode.value = nativeMode; loading.value = false }
            compose.onNodeWithTag("native.actions").assertWidthIsEqualTo(126.dp)
                .assertHeightIsEqualTo(42.dp)
            val bounds = (0..2).map { compose.onNodeWithContentDescription("native.$it")
                .fetchSemanticsNode().boundsInRoot }
            bounds.zipWithNext().forEach { (left, right) ->
                assertEquals(left.width, right.center.x - left.center.x, .01f)
            }
            compose.runOnIdle { loading.value = true }
            compose.onNodeWithTag("native.actions").assertWidthIsEqualTo(126.dp)
                .assertHeightIsEqualTo(42.dp)
            compose.onNodeWithContentDescription("native.0").assertDoesNotExist()
        }
    }

    @Test fun `single action expands beyond its resting contour and follows drag in both renderers`() {
        var clicks = 0
        val quality = mutableStateOf(GlassQuality.Balanced)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.testTag("single.host").padding(12.dp)) {
                    GlassSectionActions(
                        listOf(GlassSectionAction(R.drawable.play, "single") { clicks++ }),
                        Modifier.testTag("single.actions"),
                        GlassSettings(quality = quality.value, film = 1f),
                    )
                }
            }
        }
        val action = compose.onNodeWithContentDescription("single")
        val slot = compose.onNodeWithTag("single.actions")
        listOf(GlassQuality.Balanced, GlassQuality.Performance).forEachIndexed { index, renderer ->
            compose.runOnIdle { quality.value = renderer }
            compose.mainClock.advanceTimeByFrame()
            val bounds = slot.fetchSemanticsNode().boundsInRoot
            val host = compose.onNodeWithTag("single.host")
            val localBounds = bounds.translate(-host.fetchSemanticsNode().boundsInRoot.topLeft)
            fun renderedPixels(outside: Rect? = null): List<Color> {
                val pixels = host.captureToImage().toPixelMap()
                return (0 until pixels.height).flatMap { y ->
                    (0 until pixels.width).filter { x ->
                        outside == null || !outside.contains(Offset(x + 0.5f, y + 0.5f))
                    }.map { x -> pixels[x, y] }
                }
            }
            val resting = renderedPixels()
            val restingExterior = renderedPixels(localBounds)
            action.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(800)
            compose.runOnIdle { assertEquals(index * 2, clicks) }
            assertEquals(bounds, slot.fetchSemanticsNode().boundsInRoot)
            assertTrue("$renderer must expand the painted contour", restingExterior != renderedPixels(localBounds))
            val held = renderedPixels()
            action.performTouchInput { moveTo(center + Offset(width * 0.35f, 0f)) }
            compose.mainClock.advanceTimeBy(160)
            assertTrue("$renderer must follow the finger", held != renderedPixels())
            action.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1600)
            compose.runOnIdle { assertEquals(index * 2 + 1, clicks) }
            assertEquals(bounds, slot.fetchSemanticsNode().boundsInRoot)
            assertTrue("$renderer must restore the resting exterior", restingExterior == renderedPixels(localBounds))
            // The 40% backdrop capture rerasterizes the curved rim after a transform.
            // Require a mean error below one 8-bit channel step, while the exterior is exact.
            val restored = renderedPixels()
            val channelDeltas = resting.zip(restored).flatMap { (before, after) ->
                listOf(
                    kotlin.math.abs(before.red - after.red),
                    kotlin.math.abs(before.green - after.green),
                    kotlin.math.abs(before.blue - after.blue),
                    kotlin.math.abs(before.alpha - after.alpha),
                )
            }
            val meanDelta = channelDeltas.average()
            assertTrue(
                "$renderer resting mean channel delta=$meanDelta, max=${channelDeltas.maxOrNull()}",
                meanDelta <= 1.0 / 255.0,
            )
            action.performTouchInput { click() }
            compose.mainClock.advanceTimeBy(1600)
            compose.runOnIdle { assertEquals(index * 2 + 2, clicks) }
        }
    }

    @Test fun `sync replaces refresh inside same capsule without a clickable loading target`() {
        val syncing = mutableStateOf(false)
        var refreshes = 0
        var expands = 0
        compose.setContent {
            AppearanceProvider {
                GlassSectionActions(listOf(
                    GlassSectionAction(R.drawable.refresh, "refresh", loading = syncing.value) { refreshes++ },
                    GlassSectionAction(R.drawable.chevron_down, "expand") { expands++ },
                ), Modifier.testTag("sync.actions"), GlassSettings(quality = GlassQuality.Performance))
            }
        }
        val bounds = compose.onNodeWithTag("sync.actions").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("refresh").performTouchInput { click() }
        compose.runOnIdle { syncing.value = true }
        compose.onNodeWithContentDescription("refresh").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate)).assertExists()
        assertEquals(bounds, compose.onNodeWithTag("sync.actions").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("sync.actions").performTouchInput { click(androidx.compose.ui.geometry.Offset(22f, 20f)) }
        compose.onNodeWithContentDescription("expand").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, refreshes); assertEquals(1, expands); syncing.value = false }
        compose.onNodeWithContentDescription("refresh").assertExists()
    }

    @Test fun `section capsule separates only its final action`() {
        compose.setContent {
            AppearanceProvider {
                GlassSectionActions(List(3) { GlassSectionAction(
                    if (it == 2) R.drawable.chevron_forward else R.drawable.play, "action.$it", separateBefore = it == 2) {} },
                    Modifier.testTag("separators"),
                    GlassSettings(quality = GlassQuality.Performance, film = 1f, rim = 1f))
            }
        }
        val pixels = compose.onNodeWithTag("separators").captureToImage().toPixelMap()
        val y = pixels.height / 2
        val firstBoundary = pixels.width / 3
        val lastBoundary = pixels.width * 2 / 3
        assertEquals(pixels[firstBoundary - 2, y], pixels[firstBoundary, y])
        assertTrue(pixels[lastBoundary, y].luminance() > pixels[firstBoundary, y].luminance() + .1f)
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
