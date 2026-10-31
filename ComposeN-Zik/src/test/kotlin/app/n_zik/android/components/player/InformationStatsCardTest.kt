package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.menu.info.InformationStatsCard
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
class InformationStatsCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `real stats card preserves native pixels and follows Details cards independently of shared`() {
        val native = mutableStateOf(true)
        val master = mutableStateOf(false)
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val palette = DefaultDarkColorPalette.copy(accent = Color.Blue, background0 = Color.Black)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = palette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape)) {
                Box(Modifier.size(160.dp, 100.dp).background(Color.White).testTag("stats.root")) {
                    val modifier = Modifier.size(120.dp, 70.dp).testTag("stats.card")
                    if (native.value) {
                        // Original Card call before the modern surface hook.
                        Card(modifier, shape = shape,
                            colors = CardDefaults.cardColors(containerColor = palette.accent.copy(alpha = .2f))) {
                            Box(Modifier.size(20.dp).background(Color.Red))
                        }
                    } else {
                        InformationStatsCard(modifier, GlassSettings(enabled = master.value,
                            quality = GlassQuality.Performance, film = .8f, rim = 1f, tintStrength = 0f,
                            areaModes = mapOf(GlassArea.SettingsCards to GlassAreaMode.Disabled,
                                GlassArea.ActionButtons to GlassAreaMode.Disabled),
                            contextModes = mapOf(GlassContextArea(GlassScope.Details, GlassArea.SettingsCards) to mode.value))) {
                            Box(Modifier.size(20.dp).background(Color.Red))
                        }
                    }
                }
            }
        }
        fun pixels(): PixelMap {
            compose.waitForIdle()
            compose.onNodeWithTag("stats.card").assertWidthIsEqualTo(120.dp).assertHeightIsEqualTo(70.dp)
            return compose.onNodeWithTag("stats.root").captureToImage().toPixelMap()
        }
        fun changed(a: PixelMap, b: PixelMap): Int {
            var count = 0
            for (y in 0 until a.height) for (x in 0 until a.width) if (a[x, y] != b[x, y]) count++
            return count
        }
        val baseline = pixels()
        compose.runOnIdle { native.value = false }
        for (value in GlassAreaMode.entries) {
            compose.runOnIdle { mode.value = value }
            assertEquals("Master OFF matches the actual original Card for $value", 0, changed(baseline, pixels()))
        }
        compose.runOnIdle { master.value = true; mode.value = GlassAreaMode.Disabled }
        val disabled = pixels()
        assertEquals("Details Disabled preserves native fill", 0, changed(baseline, disabled))
        compose.runOnIdle { mode.value = GlassAreaMode.ContourOnly }
        val contour = pixels()
        assertTrue("Details contour decorates the native card", changed(disabled, contour) > 10)
        compose.runOnIdle { mode.value = GlassAreaMode.GlassOnly }
        val glass = pixels()
        assertTrue("Details material works while Shared SettingsCards is Disabled", changed(disabled, glass) > 10)
        compose.runOnIdle { mode.value = GlassAreaMode.GlassAndContour }
        assertTrue("GlassOnly removes the separate contour", changed(glass, pixels()) > 10)
    }
}
