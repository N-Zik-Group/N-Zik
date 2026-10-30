package app.n_zik.android.components.glass

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
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
class GlassAuditHelperTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `preview descendants resolve local settings in either global master state without writes`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val store = GlassStore(context)
        val global = mutableStateOf(false)
        var outside: GlassSettings? = null
        val on = GlassSettings(enabled = true, source = GlassTintSource.None, rim = 1f)
        val off = on.copy(enabled = false)
        compose.setContent {
            AuditAppearance {
                // Reading global settings outside each preview must retain the persisted master.
                val persisted = rememberGlassSettings()
                SideEffect { outside = persisted }
                Column {
                    listOf(on, off).forEach { config ->
                        CompositionLocalProvider(LocalGlassPreviewSettings provides config) {
                            val resolved = rememberGlassSettings()
                            SideEffect { assertEquals(config, resolved) }
                            Box(Modifier.size(40.dp).background(Color.Black)
                                .glassImageRim(RectangleShape).testTag("rim.${config.enabled}"))
                        }
                    }
                    // Forces recomposition after a preference update without replacing either preview.
                    Text(global.value.toString())
                }
            }
        }
        listOf(false, true).forEach { enabled ->
            compose.runOnIdle {
                // The master preference uses the active profile through the same app accessor.
                context.preferences.edit().putBoolean(glassEnabledKey, enabled).commit()
                global.value = enabled
            }
            compose.waitForIdle()
            val before = store.read()
            val onPixels = compose.onNodeWithTag("rim.true").captureToImage().toPixelMap()
            val offPixels = compose.onNodeWithTag("rim.false").captureToImage().toPixelMap()
            assertNotEquals(onPixels[0, onPixels.height / 2], offPixels[0, offPixels.height / 2])
            compose.runOnIdle {
                assertEquals(enabled, outside?.enabled)
                assertEquals(before, store.read())
            }
        }
    }

    @Test fun `Glass chips retain Row weight and alignment and honor explicit rectangular shape`() {
        var outlines = 0
        val customShape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                outlines++
                return RectangleShape.createOutline(size, layoutDirection, density)
            }
        }
        compose.setContent {
            AuditAppearance {
                Row(Modifier.width(300.dp).height(100.dp).background(Color.Black).testTag("row")) {
                    GlassFilterChip(true, {}, { Text("Filter") },
                        Modifier.weight(1f).align(Alignment.Bottom).testTag("filter"),
                        settings = GlassSettings(quality = GlassQuality.Performance))
                    GlassSuggestionChip({}, { Text("Keyword") },
                        Modifier.weight(2f).align(Alignment.Top).testTag("keyword"),
                        shape = customShape, settings = GlassSettings(quality = GlassQuality.Performance))
                }
            }
        }
        val row = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        val filter = compose.onNodeWithTag("filter").fetchSemanticsNode().boundsInRoot
        val keyword = compose.onNodeWithTag("keyword").fetchSemanticsNode().boundsInRoot
        assertEquals(row.width / 3f, filter.width, 1f)
        assertEquals(row.width * 2f / 3f, keyword.width, 1f)
        val density = row.width / 300f
        // Material allocates 48dp touch slots around the visible 32dp chip.
        assertEquals(row.bottom - 24f * density, filter.center.y, .1f)
        assertEquals(row.top + 24f * density, keyword.center.y, .1f)
        compose.onNodeWithTag("keyword").captureToImage()
        compose.runOnIdle { assertTrue("Explicit shape must create the Glass chip outline", outlines > 0) }
    }

    @Test fun `reduced motion retains native ripple configuration and visible held feedback`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val previous = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val native = RippleConfiguration(Color.White)
            var observed: RippleConfiguration? = null
            val source = MutableInteractionSource()
            compose.mainClock.autoAdvance = false
            compose.setContent {
                AuditAppearance {
                    CompositionLocalProvider(LocalRippleConfiguration provides native) {
                        PageButton({}, Modifier.size(160.dp, 48.dp).testTag("button"),
                            interactionSource = source,
                            settings = GlassSettings(quality = GlassQuality.Performance)) {
                            val ripple = LocalRippleConfiguration.current
                            SideEffect { observed = ripple }
                        }
                    }
                }
            }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertSame(native, observed) }
            val button = compose.onNodeWithTag("button")
            val before = button.captureToImage().toPixelMap()
            button.performTouchInput { down(center) }
            ShadowLooper.idleMainLooper(150, TimeUnit.MILLISECONDS)
            compose.mainClock.advanceTimeBy(800)
            val held = button.captureToImage().toPixelMap()
            assertNotEquals(before[before.width / 2, before.height / 2], held[held.width / 2, held.height / 2])
            button.performTouchInput { up() }
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous)
        }
    }

    @Test fun `portrait border chooses exactly native border or Glass rim`() {
        val enabled = mutableStateOf(false)
        val config = GlassSettings(source = GlassTintSource.None, rim = 0f)
        compose.setContent {
            CompositionLocalProvider(LocalGlassPreviewSettings provides config.copy(enabled = enabled.value)) {
                Column {
                    Box(Modifier.size(40.dp).background(Color.Black)
                        .glassImageBorder(RectangleShape, BorderStroke(1.dp, Color.White)).testTag("portrait"))
                    Box(Modifier.size(40.dp).background(Color.Black)
                        .border(1.dp, Color.White, RectangleShape).testTag("native"))
                }
            }
        }
        val native = compose.onNodeWithTag("native").captureToImage().toPixelMap()
        val off = compose.onNodeWithTag("portrait").captureToImage().toPixelMap()
        for (y in 0 until off.height) for (x in 0 until off.width) assertEquals(native[x, y], off[x, y])
        compose.runOnIdle { enabled.value = true }
        val on = compose.onNodeWithTag("portrait").captureToImage().toPixelMap()
        assertEquals(Color.Black, on[0, on.height / 2])
    }
}

@Composable
private fun AuditAppearance(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    CompositionLocalProvider(LocalAppearance provides Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
    ), content = content)
}
