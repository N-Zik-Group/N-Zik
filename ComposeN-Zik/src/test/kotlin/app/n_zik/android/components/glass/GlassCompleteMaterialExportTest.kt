package app.n_zik.android.components.glass

import android.app.Application
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.drawBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.glass.backdrop.highlight.Highlight
import app.n_zik.android.components.glass.backdrop.highlight.HighlightStyle
import app.n_zik.android.components.glass.backdrop.shadow.InnerShadow
import app.n_zik.android.components.glass.backdrop.shadow.Shadow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassCompleteMaterialExportTest {
    @get:Rule(order = 0) val nativeTraversals = object : ExternalResource() {
        override fun before() {
            if (Build.VERSION.SDK_INT >= 33) ShadowView.setUseRealDrawTraversals(true)
        }

        override fun after() {
            if (Build.VERSION.SDK_INT >= 33) ShadowView.setUseRealDrawTraversals(false)
        }
    }

    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun `initial inner shadow paints the same native source and exported material`() {
        compose.setContent {
            val source = rememberCanvasBackdrop { }
            val exported = rememberLayerBackdrop()
            Row(Modifier.background(Color.Black)) {
                listOf(false, true).forEach { export ->
                    Box(Modifier.size(100.dp).testTag(if (export) "material" else "native").drawBackdrop(
                        backdrop = source, shape = { RoundedCornerShape(36.dp) }, effects = {},
                        shadow = { Shadow(radius = 3.dp) },
                        highlight = { Highlight(width = 5.dp, blurRadius = 0.dp,
                            style = HighlightStyle.Plain(Color.Yellow, BlendMode.SrcOver)) },
                        innerShadow = { InnerShadow(radius = 0.dp, offset = DpOffset(0.dp, 8.dp),
                            color = Color.Black.copy(alpha = .5f)) },
                        exportedBackdrop = if (export) exported else null,
                        onDrawSurface = { drawRect(Color.Cyan) },
                    ))
                }
                Box(Modifier.size(100.dp).testTag("export").drawBehind { drawLayer(exported.graphicsLayer) })
            }
        }
        val native = compose.onNodeWithTag("native").captureToImage().toPixelMap()
        val material = compose.onNodeWithTag("material").captureToImage().toPixelMap()
        val export = compose.onNodeWithTag("export").captureToImage().toPixelMap()
        val mid = native.width / 2
        assertTrue("initial native shadow must be visible: ${native[mid, 2]}", native[mid, 2].green < .75f)
        assertEquals("source shadow", native[mid, 2].green, material[mid, 2].green, .03f)
        assertEquals("export shadow", material[mid, 2].green, export[mid, 2].green, .03f)
    }

    @Test fun `translucent export isolates additive rim from the scene backing`() {
        compose.setContent {
            val source = rememberCanvasBackdrop { }
            val exported = rememberLayerBackdrop()
            Row(Modifier.background(Color.Red)) {
                Box(Modifier.size(100.dp).testTag("material").drawBackdrop(
                    backdrop = source, shape = { RoundedCornerShape(24.dp) }, effects = {},
                    shadow = null, exportedBackdrop = exported,
                    highlight = { Highlight(width = 5.dp, blurRadius = 0.dp,
                        style = HighlightStyle.Plain(Color.White.copy(alpha = .4f), BlendMode.Plus)) },
                    onDrawSurface = { drawRect(Color.Green.copy(alpha = .4f)) },
                ))
                Box(Modifier.size(100.dp).testTag("export").drawBehind {
                    drawLayer(exported.graphicsLayer)
                })
            }
        }
        val source = compose.onNodeWithTag("material").captureToImage().toPixelMap()
        val export = compose.onNodeWithTag("export").captureToImage().toPixelMap()
        val mid = source.width / 2
        listOf(mid to 2, mid to mid, 1 to 1).forEach { (x, y) ->
            assertEquals("isolated red at $x,$y", source[x, y].red, export[x, y].red, .03f)
            assertEquals("isolated green at $x,$y", source[x, y].green, export[x, y].green, .03f)
            assertEquals("isolated blue at $x,$y", source[x, y].blue, export[x, y].blue, .03f)
        }
    }

    @Test fun `passive fallback export contains the same inset border and open menu bottom`() {
        val menu = mutableStateOf(false)
        val exportEnabled = mutableStateOf(true)
        val generic = mutableStateOf(false)
        compose.setContent {
            val shape = if (generic.value) GenericShape { size, _ ->
                moveTo(0f, size.height)
                lineTo(0f, 24f)
                quadraticBezierTo(0f, 0f, 24f, 0f)
                lineTo(size.width - 24f, 0f)
                quadraticBezierTo(size.width, 0f, size.width, 24f)
                lineTo(size.width, size.height)
                close()
            } else RoundedCornerShape(topStart = 24.dp, topEnd = 12.dp,
                bottomStart = 0.dp, bottomEnd = 0.dp)
            val exported = rememberLayerBackdrop()
            CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
                typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                Row(Modifier.background(Color.Black)) {
                    Box(Modifier.size(100.dp).testTag("material").glassMaterial(
                        settings = GlassSettings(enabled = true, film = 1f, rim = 1f),
                        shape = shape, separateWindow = true, highlightAlpha = 1f,
                        filmColor = Color.Green, exportedBackdrop = if (exportEnabled.value) exported else null,
                        menu = menu.value,
                    ))
                    Box(Modifier.size(100.dp).testTag("export").drawBehind {
                        drawLayer(exported.graphicsLayer)
                    })
                }
            }
        }
        fun checkRim() {
            val source = compose.onNodeWithTag("material").captureToImage().toPixelMap()
            val export = compose.onNodeWithTag("export").captureToImage().toPixelMap()
            val mid = source.width / 2
            assertTrue("fixture must render a contrasting native rim", source[mid, 0] != source[mid, mid])
            listOf(mid to 0, 0 to mid, mid to source.height - 1, 1 to 1).forEach { (x, y) ->
                assertEquals("exported rim red at $x,$y", source[x, y].red, export[x, y].red, .03f)
                assertEquals("exported rim green at $x,$y", source[x, y].green, export[x, y].green, .03f)
                assertEquals("exported rim blue at $x,$y", source[x, y].blue, export[x, y].blue, .03f)
            }
            // Keep the control at the same device coordinates: translating curved
            // paths can change native rasterization at the antialiased boundary.
            compose.runOnIdle { exportEnabled.value = false }
            val native = compose.onNodeWithTag("material").captureToImage().toPixelMap()
            for (y in 0 until source.height) for (x in 0 until source.width) {
                assertEquals("native rim red at $x,$y", native[x, y].red, source[x, y].red, .03f)
                assertEquals("native rim green at $x,$y", native[x, y].green, source[x, y].green, .03f)
                assertEquals("native rim blue at $x,$y", native[x, y].blue, source[x, y].blue, .03f)
            }
            compose.runOnIdle { exportEnabled.value = true }
        }
        checkRim()
        compose.runOnIdle { menu.value = true }
        checkRim()
        compose.runOnIdle { menu.value = false; generic.value = true }
        checkRim()
    }

    @Config(sdk = [33])
    @Test fun `export matches rounded material and changing rim without foreground or idle captures`() {
        val film = mutableStateOf(Color.Green)
        val rim = mutableStateOf(Color.White)
        val corners = mutableStateOf(24.dp)
        val shadows = mutableStateOf(false)
        var captures = 0
        var paints = 0
        var outerShadowReads = 0
        var innerShadowReads = 0
        var lastInnerShadowEnabled = false
        compose.setContent {
            val source = rememberCanvasBackdrop { captures++; drawRect(Color.Blue) }
            val exported = rememberLayerBackdrop()
            val preview = remember(exported) { MaterialPreviewBackdrop(exported) }
            val shape = remember { { RoundedCornerShape(corners.value) } }
            val surface: DrawScope.() -> Unit = remember { { paints++; drawRect(film.value) } }
            val highlight = remember { {
                Highlight(width = 5.dp, blurRadius = 0.dp,
                    style = HighlightStyle.Plain(rim.value, BlendMode.SrcOver))
            } }
            Row(Modifier.background(Color.Black)) {
                Box(Modifier.size(100.dp).testTag("material").drawBackdrop(
                    backdrop = source, shape = shape, effects = {},
                    shadow = { outerShadowReads++; if (shadows.value) Shadow(radius = 3.dp) else null },
                    innerShadow = {
                        innerShadowReads++
                        lastInnerShadowEnabled = shadows.value
                        if (lastInnerShadowEnabled) InnerShadow(radius = 0.dp,
                            offset = DpOffset(0.dp, 8.dp), color = Color.Black.copy(alpha = .5f)) else null
                    },
                    highlight = highlight, exportedBackdrop = exported, onDrawSurface = surface,
                ), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(20.dp).background(Color.Red))
                }
                Box(Modifier.size(100.dp).testTag("export").drawPlainBackdrop(
                    backdrop = preview, shape = { RectangleShape }, effects = {},
                ))
            }
        }
        fun assertMaterial(expectedFilm: Color, expectedRim: Color) {
            val source = compose.onNodeWithTag("material").captureToImage().toPixelMap()
            val export = compose.onNodeWithTag("export").captureToImage().toPixelMap()
            fun assertColor(label: String, expected: Color, actual: Color) {
                assertEquals("$label red", expected.red, actual.red, .03f)
                assertEquals("$label green", expected.green, actual.green, .03f)
                assertEquals("$label blue", expected.blue, actual.blue, .03f)
            }
            val mid = source.width / 2
            assertColor("source foreground", Color.Red, source[mid, mid])
            assertColor("export excludes foreground", expectedFilm, export[mid, mid])
            assertColor("source rim", expectedRim, source[mid, 2])
            assertColor("export includes the rendered rim", source[mid, 2], export[mid, 2])
            assertColor("rounded export corner matches rendered source", source[1, 1], export[1, 1])
            assertColor("live rounded edge matches rendered source", source[15, 5], export[15, 5])
        }
        assertMaterial(Color.Green, Color.White)
        compose.runOnIdle { rim.value = Color.Yellow }
        assertMaterial(Color.Green, Color.Yellow)
        compose.runOnIdle { film.value = Color.Cyan; corners.value = 36.dp }
        assertMaterial(Color.Cyan, Color.Yellow)
        val unshadedSource = compose.onNodeWithTag("material").captureToImage().toPixelMap()
        val paintsBeforeShadow = paints
        val outerReadsBeforeShadow = outerShadowReads
        val innerReadsBeforeShadow = innerShadowReads
        compose.runOnIdle { shadows.value = true }
        val shadedSource = compose.onNodeWithTag("material").captureToImage().toPixelMap()
        val shadedExport = compose.onNodeWithTag("export").captureToImage().toPixelMap()
        val mid = shadedSource.width / 2
        var maximumDarkening = 0f
        var darkestX = 0
        var darkestY = 0
        for (y in 0 until shadedSource.height) for (x in 0 until shadedSource.width) {
            val darkening = unshadedSource[x, y].green - shadedSource[x, y].green
            if (darkening > maximumDarkening) {
                maximumDarkening = darkening
                darkestX = x
                darkestY = y
            }
        }
        assertTrue("enabled inner shadow must visibly darken material, max=$maximumDarkening at $darkestX,$darkestY; " +
            "paints=$paintsBeforeShadow->$paints, outer=$outerReadsBeforeShadow->$outerShadowReads, " +
            "inner=$innerReadsBeforeShadow->$innerShadowReads, enabled=$lastInnerShadowEnabled",
            maximumDarkening > .2f)
        listOf(darkestX to darkestY, mid to 2, 2 to mid, mid to shadedSource.height - 2, 15 to 5).forEach { (x, y) ->
            assertEquals("shadow red at $x,$y", shadedSource[x, y].red, shadedExport[x, y].red, .03f)
            assertEquals("shadow green at $x,$y", shadedSource[x, y].green, shadedExport[x, y].green, .03f)
            assertEquals("shadow blue at $x,$y", shadedSource[x, y].blue, shadedExport[x, y].blue, .03f)
        }
        compose.runOnIdle { shadows.value = false }
        assertMaterial(Color.Cyan, Color.Yellow)
        val settledCaptures = captures
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.onNodeWithTag("export").captureToImage()
        }
        assertTrue("source must actually render", settledCaptures > 0)
        assertEquals("static exported material must settle", settledCaptures, captures)
    }
}

/** Samples an exported material at its own origin while retaining the normal revision contract. */
private class MaterialPreviewBackdrop(private val source: LayerBackdrop) : Backdrop {
    override val isCoordinatesDependent = false
    override val contentVersion: Int get() = source.contentVersion

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        drawLayer(source.graphicsLayer)
    }
}
