package app.n_zik.android.components.player

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.screens.player.components.controls.InfoAlbumAndArtistEssential
import app.it.fast4x.rimusic.ui.screens.player.components.controls.InfoAlbumAndArtistModern
import app.it.fast4x.rimusic.utils.playerControlsTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.glassButtonFeedback
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStandaloneActionSize
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.glassSurfaceTint
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class)
class PlayerInformationFavoriteActionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `favorite samples changing title pixels over its parent but excludes its own foreground`() {
        val titleColor = mutableStateOf(Color.Cyan)
        val showProbe = mutableStateOf(false)
        val mode = mutableStateOf(GlassAreaMode.GlassOnly)
        var renderedParent = Color.Transparent
        compose.setContent { AppearanceProvider {
            val parent = rememberCanvasBackdrop { drawRect(Color.Blue) }
            val settings = GlassSettings(enabled = true, blur = 0f, film = 0f, rim = 0f,
                depth = 0f, amount = 0f, vibrancy = 1f, contextModes = mapOf(
                    GlassContextArea(GlassScope.PlayerFavorite, GlassArea.ActionButtons) to mode.value))
            val parentFilm = glassSurfaceTint(settings).copy(alpha = .7f)
            SideEffect { renderedParent = parentFilm.compositeOver(Color.Blue) }
            CompositionLocalProvider(LocalGlassContainerBackdrop provides parent) {
                PlayerInformationGlassBlock(settings = settings.copy(film = .7f)) {
                    PlayerInformationGlassRegion("title.capture") {
                        PlayerInformationFavoriteBackdrop(active = true, settings = settings) { title, source ->
                            Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(100.dp).then(title).drawBehind {
                                    drawRect(titleColor.value, Offset(0f, 40.dp.toPx()), Size(size.width, 20.dp.toPx()))
                                })
                                // This foreground and the heart are siblings of the captured title.
                                Box(Modifier.size(100.dp).background(Color.Green))
                                PlayerInformationFavoriteAction(R.drawable.heart, Color.Red, {}, settings,
                                    ownBackdrop = source)
                                if (showProbe.value) Box(Modifier.size(100.dp).testTag("favorite.source")
                                    .drawPlainBackdrop(requireNotNull(source), { RectangleShape }, effects = {}))
                            }
                        }
                    }
                }
            }
        } }
        fun assertColor(expected: Color, actual: Color) {
            assertEquals(expected.red, actual.red, .03f)
            assertEquals(expected.green, actual.green, .03f)
            assertEquals(expected.blue, actual.blue, .03f)
        }
        for (color in listOf(Color.Cyan, Color.Yellow)) {
            compose.runOnIdle { titleColor.value = color; showProbe.value = false }
            val surface = compose.onNodeWithTag("player.informationFavorite.surface", useUnmergedTree = true)
                .captureToImage().toPixelMap()
            assertColor(color, surface[(surface.width * .15f).toInt(), surface.height / 2])
            compose.runOnIdle { showProbe.value = true }
            val source = compose.onNodeWithTag("favorite.source").captureToImage().toPixelMap()
            assertColor(color, source[source.width / 2, source.height / 2])
            assertColor(renderedParent, source[source.width / 2, source.height / 4])
        }
        for (nativeMode in listOf(GlassAreaMode.ContourOnly, GlassAreaMode.Disabled)) {
            compose.runOnIdle { mode.value = nativeMode }
            val source = compose.onNodeWithTag("favorite.source").captureToImage().toPixelMap()
            assertColor(renderedParent, source[source.width / 2, source.height / 2])
        }
    }

    @Test fun `passive surface overflow survives the favorite feedback canvas`() {
        compose.setContent { AppearanceProvider {
            Box(Modifier.size(80.dp).background(Color.White).testTag("overflow.scene"),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(48.dp).glassButtonFeedback(MutableInteractionSource(), RoundedCornerShape(16.dp),
                    animateMotion = false, drawHighlight = false, settings = GlassSettings(enabled = true),
                    clipContent = false).drawBehind {
                    drawRect(Color.Red, Offset(-6.dp.toPx(), 0f), Size(size.width + 12.dp.toPx(), size.height))
                })
            }
        } }
        val pixels = compose.onNodeWithTag("overflow.scene").captureToImage().toPixelMap()
        val targetLeft = pixels.width * .2f
        val pixel = pixels[(targetLeft - pixels.width * .04f).toInt(), pixels.height / 2]
        assertTrue("The passive material can paint outside the 48dp feedback shape",
            pixel.red > .9f && pixel.green < .2f && pixel.blue < .2f)
    }
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private val nav = mockk<NavController>(relaxed = true)

    @Before fun setup() {
        preferences.edit().clear().putString(playerControlsTypeKey, PlayerControlsType.Modern.name).commit()
        val player = mockk<Player>(relaxed = true)
        every { binder.player } returns player
        every { player.currentMediaItem } returns null
        val songs = mockk<SongTable>()
        every { songs.likeState(any()) } returns flowOf(null)
        mockkObject(Database)
        every { Database.songTable } returns songs
    }

    @After fun cleanup() {
        preferences.edit().clear().commit()
        unmockkAll()
    }

    @Test fun `favorite keeps shared compact feedback and a real 48dp touch target with material and contour`() {
        val enabled = mutableStateOf(false)
        val mode = mutableStateOf(GlassAreaMode.ContourOnly)
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.size(100.dp).testTag("favorite.scene"), contentAlignment = Alignment.Center) {
                    PlayerInformationFavoriteAction(R.drawable.heart, Color.White, { clicks++ },
                        GlassSettings(enabled = enabled.value, jellyEnabled = false,
                            areaModes = mapOf(GlassArea.ActionButtons to mode.value)))
                }
            }
        }
        for ((glass, treatment) in listOf(false to GlassAreaMode.Disabled,
            true to GlassAreaMode.ContourOnly, true to GlassAreaMode.GlassAndContour)) {
            compose.runOnIdle { enabled.value = glass; mode.value = treatment }
            val action = compose.onNodeWithTag("player.informationFavorite")
                .assertWidthIsEqualTo(GlassStandaloneActionSize).assertHeightIsEqualTo(GlassStandaloneActionSize)
            val scene = compose.onNodeWithTag("favorite.scene")
            val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
            val actionBounds = action.fetchSemanticsNode().boundsInRoot
            val centerInScene = actionBounds.center - sceneBounds.topLeft
            val touchHalfSize = actionBounds.width * (48.dp / GlassStandaloneActionSize) / 2f - 1f
            for (edge in 0..3) {
                val expectedClicks = clicks + 1
                // Inject through the parent, outside the40dp visual canvas, to verify Compose's
                // actual48dp minimum touch expansion rather than a semantic click shortcut.
                scene.performTouchInput {
                    click(centerInScene + when (edge) {
                        0 -> Offset(-touchHalfSize, 0f)
                        1 -> Offset(touchHalfSize, 0f)
                        2 -> Offset(0f, -touchHalfSize)
                        else -> Offset(0f, touchHalfSize)
                    })
                }
                compose.runOnIdle {
                    assertEquals("Each physical48dp edge reaches onClick despite the24dp layout seat: $glass $treatment edge=$edge",
                        expectedClicks, clicks)
                }
            }
            compose.onNodeWithTag("player.informationFavorite.glyph", useUnmergedTree = true)
                .assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
            val target = compose.onNodeWithTag("player.informationFavorite").fetchSemanticsNode().boundsInRoot
            val glyph = compose.onNodeWithTag("player.informationFavorite.glyph", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(target.center, glyph.center)
            compose.onAllNodes(hasClickAction()).assertCountEquals(1)
            if (glass) compose.onNodeWithTag("player.informationFavorite.surface", useUnmergedTree = true)
                .assertWidthIsEqualTo(GlassStandaloneActionSize).assertHeightIsEqualTo(GlassStandaloneActionSize)
            else compose.onNodeWithTag("player.informationFavorite.surface", useUnmergedTree = true).assertDoesNotExist()
            if (glass) {
                compose.mainClock.autoAdvance = false
                val before = scene.captureToImage().toPixelMap()
                action.performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(400)
                val held = scene.captureToImage().toPixelMap()
                var inside = 0
                var outside = 0
                for (y in 0 until held.height) for (x in 0 until held.width) {
                    val a = before[x, y]
                    val b = held[x, y]
                    val rgbChange = maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green),
                        kotlin.math.abs(a.blue - b.blue))
                    if (maxOf(rgbChange, kotlin.math.abs(a.alpha - b.alpha)) <= .025f) continue
                    val point = Offset(x + .5f, y + .5f) + sceneBounds.topLeft
                    if (point.x < actionBounds.left - 1f || point.x > actionBounds.right + 1f ||
                        point.y < actionBounds.top - 1f || point.y > actionBounds.bottom + 1f) outside++
                    else inside++
                }
                assertTrue("A held Glass press really paints inside the shared compact canvas: $treatment", inside > 10)
                assertEquals("The invisible48dp touch extension cannot become a larger Glass bubble: $treatment", 0, outside)
                action.performTouchInput { cancel() }
                compose.mainClock.advanceTimeBy(1600)
                compose.mainClock.autoAdvance = true
            }
        }
        assertEquals(12, clicks)
    }

    @Test @Config(qualifiers = "w800dp-h1200dp")
    fun `Essential short title stays centered beside the compact favorite`() {
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalGlassPreviewSettings provides GlassSettings(enabled = false)) {
                Column(Modifier.requiredWidth(400.dp).testTag("title.viewport")) {
                    ActualInformation(essential = true) { }
                }
            }
        } }
        val viewport = compose.onNodeWithTag("title.viewport").fetchSemanticsNode().boundsInRoot
        val title = compose.onAllNodesWithText("Title", useUnmergedTree = true)
            .fetchSemanticsNodes().first().boundsInRoot
        assertEquals(viewport.center.x, title.center.x, 1f)
    }

    @Test @Config(qualifiers = "w800dp-h1200dp")
    fun `actual information layouts retain full centered favorite through portrait policies`() {
        verifyInformationLayouts()
    }

    @Test @Config(qualifiers = "w800dp-h600dp-land")
    fun `actual information layouts retain full centered favorite through landscape policies`() {
        verifyInformationLayouts()
    }

    private fun verifyInformationLayouts() {
        val viewportWidth = mutableStateOf(300.dp)
        val essential = mutableStateOf(false)
        val glass = mutableStateOf(false)
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        var collapses = 0
        compose.setContent { AppearanceProvider {
            val settings = GlassSettings(enabled = glass.value, contextModes = mapOf(
                GlassContextArea(GlassScope.PlayerFavorite, GlassArea.ActionButtons) to mode.value,
                GlassContextArea(GlassScope.PlayerInformation, GlassArea.ActionButtons) to GlassAreaMode.Disabled,
                GlassContextArea(GlassScope.PlayerTransport, GlassArea.ActionButtons) to GlassAreaMode.Disabled,
            ))
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings, LocalContentColor provides Color.White) {
                Column(Modifier.requiredWidth(viewportWidth.value).background(Color.Black)
                    .testTag("information.viewport").padding(20.dp)) {
                    PlayerInformationGlassBlock {
                        PlayerInformationGlassRegion("info", vertical = essential.value, horizontalContentInset = 10.dp) {
                            ActualInformation(essential.value) { collapses++ }
                        }
                    }
                }
            }
        } }
        for (w in listOf(300.dp, 560.dp)) for (centered in listOf(false, true)) {
            compose.runOnIdle {
                viewportWidth.value = w; essential.value = centered; glass.value = false
                preferences.edit().putString(playerControlsTypeKey, PlayerControlsType.Essential.name).commit()
            }
            // The original24dp heart never exceeded the natural title row. The real layout with
            // that action absent is an independent baseline for the original metadata spacing.
            fun metadataCenter(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes().first().boundsInRoot.center.y
            val originalTitleCenter = metadataCenter("Title")
            val originalArtistCenter = metadataCenter("Artist")
            compose.runOnIdle {
                preferences.edit().putString(playerControlsTypeKey, PlayerControlsType.Modern.name).commit()
            }
            val native = compose.onNodeWithTag("player.informationFavorite").fetchSemanticsNode().boundsInRoot
            for ((enabled, treatment) in listOf(false to GlassAreaMode.Disabled,
                true to GlassAreaMode.Disabled, true to GlassAreaMode.ContourOnly,
                true to GlassAreaMode.GlassOnly, true to GlassAreaMode.GlassAndContour)) {
                compose.runOnIdle { glass.value = enabled; mode.value = treatment }
                assertEquals("Full favorite feedback preserves the original title row: $w essential=$centered $treatment",
                    originalTitleCenter, metadataCenter("Title"), .5f)
                assertEquals("Full favorite feedback preserves the original title/artist gap: $w essential=$centered $treatment",
                    originalArtistCenter, metadataCenter("Artist"), .5f)
                val target = compose.onNodeWithTag("player.informationFavorite")
                    .assertWidthIsEqualTo(GlassStandaloneActionSize).assertHeightIsEqualTo(GlassStandaloneActionSize)
                val bounds = target.fetchSemanticsNode().boundsInRoot
                val glyph = compose.onNodeWithTag("player.informationFavorite.glyph", useUnmergedTree = true)
                    .assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
                assertEquals("Native action stays fixed: $w essential=$centered $treatment", native, bounds)
                assertEquals(bounds.center, glyph.fetchSemanticsNode().boundsInRoot.center)
                val viewport = compose.onNodeWithTag("information.viewport").fetchSemanticsNode().boundsInRoot
                assertTrue("Entire target remains in the padded information viewport: $w essential=$centered $treatment target=$bounds viewport=$viewport", bounds.left >= viewport.left &&
                    bounds.right <= viewport.right && bounds.top >= viewport.top && bounds.bottom <= viewport.bottom)
                val surface = compose.onNodeWithTag("player.informationFavorite.surface", useUnmergedTree = true)
                if (enabled && treatment != GlassAreaMode.Disabled) {
                    assertEquals("Surface, ripple and glyph share a center", bounds, surface.fetchSemanticsNode().boundsInRoot)
                    val plate = compose.onNodeWithTag("player.informationBlock.surface", useUnmergedTree = true)
                        .fetchSemanticsNode().boundsInRoot
                    val glyphBounds = glyph.fetchSemanticsNode().boundsInRoot
                    // The independently scoped favorite decoration can overdraw the information
                    // plate. Its glyph still belongs to that plate; its full canvas belongs to
                    // the padded viewport checked above, without shifting the native title row.
                    assertTrue("The information plate contains the complete favorite glyph: plate=$plate glyph=$glyphBounds",
                        plate.left <= glyphBounds.left && plate.top <= glyphBounds.top &&
                            plate.right >= glyphBounds.right && plate.bottom >= glyphBounds.bottom)
                } else surface.assertDoesNotExist()
                val pixels = glyph.captureToImage().toPixelMap()
                assertTrue("The actual favorite glyph is painted, not merely measured: $treatment",
                    (0 until pixels.width).any { x -> (0 until pixels.height).any { y ->
                        val pixel = pixels[x, y]
                        pixel.red > .6f && pixel.red > pixel.green + .15f && pixel.red > pixel.blue + .15f
                    } })
                for (edge in 0..3) target.performTouchInput { click(when (edge) {
                    0 -> Offset(1f, center.y)
                    1 -> Offset(width - 1f, center.y)
                    2 -> Offset(center.x, 1f)
                    else -> Offset(center.x, height - 1f)
                }) }
                compose.runOnIdle { assertEquals("Favorite edges do not trigger adjacent metadata actions", 0, collapses) }
            }
        }
        compose.runOnIdle { preferences.edit().putString(playerControlsTypeKey, PlayerControlsType.Essential.name).commit() }
        compose.onNodeWithTag("player.informationFavorite").assertDoesNotExist()
    }

    @Test fun `material ripple stays centered on the rendered heart in actual Modern information`() {
        verifyNativeRipple(essential = false)
    }

    @Test fun `material ripple stays centered on the rendered heart in actual Essential information`() {
        verifyNativeRipple(essential = true)
    }

    private fun verifyNativeRipple(essential: Boolean) {
        val masterEnabled = mutableStateOf(false)
        var rootView: View? = null
        compose.mainClock.autoAdvance = false
        compose.setContent { AppearanceProvider {
            val view = LocalView.current
            SideEffect { rootView = view.rootView }
            CompositionLocalProvider(LocalGlassPreviewSettings provides GlassSettings(enabled = masterEnabled.value,
                contextModes = mapOf(GlassContextArea(GlassScope.PlayerFavorite, GlassArea.ActionButtons) to GlassAreaMode.Disabled)),
                LocalContentColor provides Color.White) {
                Column(Modifier.requiredWidth(300.dp).background(Color.Black).padding(20.dp)) {
                    ActualInformation(essential) {}
                    // Independent bare native ripple, including the real row's right-hand
                    // translation and compact seat. No project helper or animated icon button.
                    Row(Modifier.padding(horizontal = 10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(if (essential) 24.dp else 26.dp))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.layout { measurable, constraints ->
                            val canvas = measurable.measure(constraints)
                            val seat = 24.dp.roundToPx()
                            layout(canvas.width, seat) { canvas.placeRelative(0, (seat - canvas.height) / 2) }
                        }.size(GlassStandaloneActionSize)) {
                            Box(Modifier.matchParentSize().testTag("material.baseline")
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(), onClick = {}), contentAlignment = Alignment.Center) {
                                Image(painterResource(R.drawable.heart), null,
                                    colorFilter = ColorFilter.tint(Color.Red), modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        } }
        fun advanceNativeRipple() {
            // RippleDrawable uses Android's animation clock, independently of Compose's clock.
            repeat(60) {
                ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
                compose.mainClock.advanceTimeByFrame()
            }
            compose.waitForIdle()
        }
        fun ripples(view: View): List<RippleDrawable> =
            listOfNotNull(view.background as? RippleDrawable) +
                (if (view is ViewGroup) (0 until view.childCount).flatMap { ripples(view.getChildAt(it)) } else emptyList())
        fun assertNativePress(expectedWidth: Float, expectedHeight: Float) {
            val pressed = ripples(requireNotNull(rootView)).filter { android.R.attr.state_pressed in it.state }
            assertEquals("Exactly the real pointer-driven control must own a pressed platform ripple", 1, pressed.size)
            val ripple = pressed.single()
            assertTrue("The actual pressed platform drawable remains visible", ripple.isVisible)
            val state = ReflectionHelpers.getField<Any>(ripple, "mState")
            val colors = ReflectionHelpers.getField<ColorStateList>(state, "mColor")
            val nativeColor = colors.getColorForState(ripple.state, colors.defaultColor)
            assertTrue("The native ripple has a visible alpha", android.graphics.Color.alpha(nativeColor) > 0)
            assertEquals("The native ripple resolves the supplied white content color", 0xFFFFFF,
                nativeColor and 0xFFFFFF)
            assertEquals("Native feedback covers the shared compact canvas", expectedWidth.toInt(), ripple.bounds.width())
            assertEquals("Native feedback covers the shared compact canvas", expectedHeight.toInt(), ripple.bounds.height())
            val hotspot = Rect().also(ripple::getHotspotBounds)
            assertEquals(ripple.bounds, hotspot)
            // SDK32 is pinned above: inspect the actual setHotspot coordinates, not just layout.
            assertEquals("Native hotspot starts at the glyph center", expectedWidth / 2f,
                ReflectionHelpers.getField<Float>(ripple, "mPendingX"), .5f)
            assertEquals("Native hotspot starts at the glyph center", expectedHeight / 2f,
                ReflectionHelpers.getField<Float>(ripple, "mPendingY"), .5f)
        }
        for (master in listOf(false, true)) {
            compose.runOnIdle { masterEnabled.value = master }
            advanceNativeRipple()
            val baseline = compose.onNodeWithTag("material.baseline")
            val baselineBefore = baseline.captureToImage().toPixelMap()
            baseline.performTouchInput { down(center) }
            advanceNativeRipple()
            val baselineBounds = baseline.fetchSemanticsNode().boundsInRoot
            assertNativePress(baselineBounds.width, baselineBounds.height)
            val baselineHeld = baseline.captureToImage().toPixelMap()
            fun ripplePoints(before: PixelMap, held: PixelMap): List<Offset> = buildList {
                val glyphHalf = held.width * (24.dp / GlassStandaloneActionSize) / 2f
                for (y in 0 until held.height) for (x in 0 until held.width) {
                    // The glyph cannot supply raster evidence: compare its surrounding canvas.
                    if (kotlin.math.abs(x + .5f - held.width / 2f) <= glyphHalf &&
                        kotlin.math.abs(y + .5f - held.height / 2f) <= glyphHalf) continue
                    val a = before[x, y]
                    val b = held[x, y]
                    if (maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green),
                            kotlin.math.abs(a.blue - b.blue), kotlin.math.abs(a.alpha - b.alpha)) > .02f) {
                        add(Offset(x + .5f, y + .5f))
                    }
                }
            }
            val baselinePoints = ripplePoints(baselineBefore, baselineHeld)
            baseline.performTouchInput { up() }
            advanceNativeRipple()
            val target = compose.onNodeWithTag("player.informationFavorite")
                .assertWidthIsEqualTo(GlassStandaloneActionSize).assertHeightIsEqualTo(GlassStandaloneActionSize)
            val bounds = target.fetchSemanticsNode().boundsInRoot
            assertEquals("The independent baseline reproduces the actual row's right-hand translation",
                bounds.left, baselineBounds.left, .5f)
            assertEquals(bounds.width, baselineBounds.width, .5f)
            assertEquals(bounds.height, baselineBounds.height, .5f)
            val glyph = compose.onNodeWithTag("player.informationFavorite.glyph", useUnmergedTree = true)
                .assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp).fetchSemanticsNode().boundsInRoot
            assertEquals("The real painted heart and native feedback share their center", bounds.center, glyph.center)
            val resting = target.captureToImage().toPixelMap()
            target.performTouchInput { down(center) }
            advanceNativeRipple()
            assertNativePress(bounds.width, bounds.height)
            val pressed = target.captureToImage().toPixelMap()
            val points = ripplePoints(resting, pressed)
            if (baselinePoints.isEmpty()) {
                // Native graphics can omit Android ripple rasterization. The independent control
                // must reproduce that limitation; native pressed state, bounds and origin above
                // remain mandatory in every layout, so this branch neither skips nor passes on layout alone.
                assertEquals("Absent platform ripple raster affects both surrounding canvases: master=$master essential=$essential",
                    0, points.size)
            } else {
                assertTrue("Material ripple actually paints outside the glyph: master=$master essential=$essential baselinePoints=${baselinePoints.size} favoritePoints=${points.size}", points.isNotEmpty())
                assertEquals(pressed.width / 2f, points.map { it.x }.average().toFloat(), 2f)
                assertEquals(pressed.height / 2f, points.map { it.y }.average().toFloat(), 2f)
                assertTrue("Feedback reaches beyond the 24dp glyph", points.any {
                    it.x < pressed.width / 4f || it.x > pressed.width * .75f ||
                        it.y < pressed.height / 4f || it.y > pressed.height * .75f
                })
            }
            target.performTouchInput { up() }
            advanceNativeRipple()
            assertTrue("Releasing the pointer clears the native pressed state",
                ripples(requireNotNull(rootView)).none { android.R.attr.state_pressed in it.state })
        }
    }

    @Composable private fun ActualInformation(essential: Boolean, onCollapse: () -> Unit) {
        val media = UiMedia("information-favorite", "Title", "Artist", 120_000L, true)
        if (essential) InfoAlbumAndArtistEssential(binder, nav, "album", media, media.id, media.title,
            null, null, "Artist", false, onCollapse, disableScrollingText = true)
        else InfoAlbumAndArtistModern(binder, nav, "album", media, media.id, media.title,
            null, null, false, "Artist", onCollapse, disableScrollingText = true)
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape), content = content)
    }

}
