package app.n_zik.android.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.ui.screens.find.FindUiState
import app.n_zik.android.components.ui.screens.find.MusicAudioSearchContent
import app.n_zik.android.recognition.RecognizedTrack
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.mode
import app.n_zik.android.components.glass.inScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w440dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchOverlayGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `Search role selects controls independently and keeps common card role`() {
        val native = RoundedCornerShape(12.dp)
        val global = RoundedCornerShape(28.dp)
        val settings = GlassSettings(
            areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled,
                GlassArea.SettingsCards to GlassAreaMode.ContourOnly),
            contextModes = mapOf(GlassContextArea(GlassScope.Search, GlassArea.ActionButtons) to GlassAreaMode.GlassOnly),
        ).inScope(GlassScope.Search)
        assertEquals(GlassAreaMode.GlassOnly, settings.mode(GlassArea.ActionButtons))
        assertEquals(GlassAreaMode.ContourOnly, settings.mode(GlassArea.SettingsCards))
        assertSame(global, searchOverlayShape(native, global))
        assertSame(global, searchOverlayShape(RoundedCornerShape(10.dp), global))
        assertSame(global, searchOverlayShape(RoundedCornerShape(18.dp), global))
        assertSame(CircleShape, searchOverlayShape(CircleShape, global))
    }

    @Test fun `native overlay action retains exact fill size and single callback`() = checkAction(false)
    @Test fun `Glass overlay action retains size and single callback`() = checkAction(true)

    @Test fun `Glass overlay opt out removes held highlight while retaining Jelly and clicks`() {
        val shape = RoundedCornerShape(0.dp)
        val settings = GlassSettings(quality = GlassQuality.Performance, rim = 0f)
            .inScope(GlassScope.Search)
        val clicks = IntArray(2)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides
                Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)) {
                Column(Modifier.background(Color.Black)) {
                    listOf(false, true).forEachIndexed { index, indication ->
                        Box(Modifier.size(96.dp, 40.dp).testTag("action.$index")
                            .searchOverlayAction(shape, Color(0xFF397159), nativeIndication = indication,
                                settings = settings, onClick = { clicks[index]++ })) {
                            Box(Modifier.size(16.dp).testTag("content.$index"))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        for (index in 0..1) {
            val action = compose.onNodeWithTag("action.$index")
            val content = compose.onNodeWithTag("content.$index", useUnmergedTree = true)
            val idleBounds = content.fetchSemanticsNode().boundsInRoot
            val idle = action.captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
            action.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(800)
            val held = action.captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
            if (index == 0) {
                assertEquals("Opted-out action has no held highlight", idle.red, held.red, 1f / 255f)
                assertEquals(idle.green, held.green, 1f / 255f)
                assertEquals(idle.blue, held.blue, 1f / 255f)
            } else {
                assertTrue("Opted-in action keeps its held highlight", held.red > idle.red + .02f)
            }
            assertTrue("Both actions retain Jelly", content.fetchSemanticsNode().boundsInRoot.width > idleBounds.width)
            action.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1800)
            action.assertWidthIsEqualTo(96.dp).assertHeightIsEqualTo(40.dp)
        }
        compose.runOnIdle { assertEquals(listOf(1, 1), clicks.toList()) }
    }

    @Test fun `action and result card render Glass from the dimmed backdrop instead of native fill`() {
        val shape = RoundedCornerShape(16.dp)
        val fill = Color(0xFF397159)
        // Balanced on API 32 exercises sampled material; Performance intentionally uses tint only.
        val settings = GlassSettings(quality = GlassQuality.Balanced, film = .35f, vibrancy = 1f,
            tintStrength = 0f, blur = 0f, rim = 0f, amount = 0f, depth = 0f,
            areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled,
                GlassArea.SettingsCards to GlassAreaMode.GlassOnly),
            contextModes = mapOf(GlassContextArea(GlassScope.Search, GlassArea.ActionButtons) to GlassAreaMode.GlassOnly))
            .inScope(GlassScope.Search)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides
                Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)) {
                Column(Modifier.background(Color.Black)) {
                    listOf("native" to Color.Red, "red" to Color.Red, "blue" to Color.Blue).forEach { (name, color) ->
                        val source = rememberCanvasBackdrop { drawRect(color) }
                        val selected = settings.copy(enabled = name != "native")
                        CompositionLocalProvider(LocalGlassBackdrop provides source,
                            LocalGlassContainerBackdrop provides null) {
                            Row {
                                Box(Modifier.size(96.dp, 40.dp).testTag("action.$name")
                                    .searchOverlayAction(shape, fill, nativeIndication = false,
                                        settings = selected, onClick = {}))
                                Box(Modifier.size(96.dp, 40.dp).testTag("card.$name")
                                    .searchOverlaySurface(shape, fill, area = GlassArea.SettingsCards,
                                        settings = selected))
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        fun center(tag: String): Color {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        fun distance(first: Color, second: Color): Float =
            kotlin.math.abs(first.red - second.red) + kotlin.math.abs(first.green - second.green) +
                kotlin.math.abs(first.blue - second.blue)
        listOf("action", "card").forEach { role ->
            val native = center("$role.native")
            val red = center("$role.red")
            val blue = center("$role.blue")
            assertEquals("Native $role keeps its original opaque fill", fill, native)
            assertTrue("Glass $role must replace the native fill", distance(native, red) > .05f)
            assertTrue("Glass $role must respond to the backdrop below the overlay scrim",
                distance(red, blue) > .025f)
            // A red source behind the 85% black overlay must stay dark at zero tint.
            assertTrue("Glass $role must include the visible overlay scrim", red.red < .5f)
        }
    }

    @Test fun `actual voice and music panels resolve labels and result actions against their own white film`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val shape = RoundedCornerShape(12.dp)
        val white = GlassSettings(source = GlassTintSource.Custom, customColor = Color.White.toArgb(),
            tintStrength = 1f, film = 1f, quality = GlassQuality.Performance, rim = 0f)
        val settings = mutableStateOf(white)
        val voice = mutableStateOf(true)
        val error = mutableStateOf<String?>("Recognition error")
        val result = mutableStateOf<FindUiState>(FindUiState.Error("Recognition error"))
        var cancels = 0
        var retries = 0
        var plays = 0
        var searches = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides
                Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings.value) {
                Box(Modifier.background(Color.Black)) {
                    if (voice.value) VoiceSearchOverlay(true, "", false, error.value,
                        onRetry = { retries++ }, onCancel = { cancels++ })
                    else MusicAudioSearchContent(result.value, 0, false, false,
                        onDismiss = { cancels++ }, onRetry = { retries++ },
                        onPlay = { plays++ }, onSearch = { searches++ })
                }
            }
        }
        fun ink(text: String): Color {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single().layoutInput.style.color
        }
        val cancel = context.getString(R.string.common_action_cancel)
        val retry = context.getString(R.string.voice_search_retry)
        val details = context.getString(R.string.recognition_action_more_details)
        for (isVoice in listOf(true, false)) {
            compose.runOnIdle { voice.value = isVoice }
            for (material in listOf(true, false)) {
                compose.runOnIdle { settings.value = white.copy(enabled = material) }
                assertEquals(if (material) Color.Black else Color.White, ink(cancel))
                assertEquals(if (material) Color.Black else Color.White, ink(retry))
                assertEquals("The message remains on the black scrim", Color.White, ink("Recognition error"))
                compose.onNodeWithText(cancel).performClick()
                compose.onNodeWithText(retry).performClick()
            }
        }
        compose.runOnIdle { settings.value = white; voice.value = true; error.value = null }
        assertEquals(Color.Black, ink(cancel))
        compose.runOnIdle { voice.value = false; result.value = FindUiState.Idle }
        assertEquals(Color.Black, ink(cancel))
        compose.runOnIdle { result.value = FindUiState.Success(RecognizedTrack("Known title", "Known artist")) }
        compose.mainClock.advanceTimeBy(500)
        assertEquals(Color.Black, ink("Known title"))
        assertEquals("Readable secondary gray retains its tone", DefaultDarkColorPalette.textSecondary, ink("Known artist"))
        assertEquals(Color.Black.copy(alpha = DefaultDarkColorPalette.onAccent.alpha), ink(details))
        compose.onNodeWithText(details).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.player_action_play)).performClick()
        compose.runOnIdle {
            assertEquals(4, cancels)
            assertEquals(4, retries)
            assertEquals(1, plays)
            assertEquals(1, searches)
        }
    }

    private fun checkAction(enabled: Boolean) {
        val native = RoundedCornerShape(12.dp)
        val global = RoundedCornerShape(28.dp)
        val fill = Color(0xFF397159)
        val settings = GlassSettings(enabled = enabled, quality = GlassQuality.Performance,
            areaModes = mapOf(GlassArea.ActionButtons to GlassAreaMode.Disabled),
            contextModes = mapOf(GlassContextArea(GlassScope.Search, GlassArea.ActionButtons) to GlassAreaMode.GlassOnly))
            .inScope(GlassScope.Search)
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides
                Appearance(DefaultDarkColorPalette, Typography.fallback, global, global, global)) {
                Column(Modifier.background(Color.Black)) {
                    Box(Modifier.size(96.dp, 40.dp).testTag("action")
                        .searchOverlayAction(native, fill, nativeIndication = false, settings = settings,
                            onClick = { clicks++ }))
                }
            }
        }
        val action = compose.onNodeWithTag("action")
        action.assertWidthIsEqualTo(96.dp).assertHeightIsEqualTo(40.dp)
        if (!enabled) {
            val pixels = action.captureToImage().toPixelMap()
            assertEquals(fill, pixels[pixels.width / 2, pixels.height / 2])
        }
        action.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
