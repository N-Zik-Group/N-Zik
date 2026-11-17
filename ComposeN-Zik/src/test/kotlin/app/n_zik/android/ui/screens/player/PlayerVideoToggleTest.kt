package app.n_zik.android.ui.screens.player

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.enums.PlayerOpeningMode
import app.n_zik.android.ui.screens.player.components.PlayerAudioVideoToggle
import app.n_zik.android.ui.screens.player.components.PlayerMusicVideo
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-rUS")
class PlayerVideoToggleTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `loading video keeps compact progress semantics and selection bounds in native and glass modes`() {
        val glass = mutableStateOf(false)
        val session = PlayerMusicVideo().apply { selectVideo(true) }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape,
                ),
                LocalGlassPreviewSettings provides GlassSettings(enabled = glass.value, rippleEnabled = false),
            ) {
                PlayerAudioVideoToggle(session, onSearchManually = {})
            }
        }
        val loading = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        ) and hasAnyAncestor(hasContentDescription("Video"))
        for (enabled in listOf(false, true)) {
            composeRule.runOnIdle {
                glass.value = enabled
                session.ready = false
            }
            val video = composeRule.onNodeWithContentDescription("Video")
            video.assertIsSelected()
            val loadingBounds = video.fetchSemanticsNode().boundsInRoot
            composeRule.onNode(loading, useUnmergedTree = true)
                .assertWidthIsEqualTo(16.dp)
                .assertHeightIsEqualTo(16.dp)
            composeRule.runOnIdle { session.ready = true }
            composeRule.onNode(loading, useUnmergedTree = true).assertDoesNotExist()
            video.assertIsSelected()
            assertEquals(loadingBounds, video.fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    @Config(sdk = [32])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `native audio video toggle preserves colored idle and selected onAccent glyphs`() {
        val idle = Color(0xFFE78AFA)
        val selected = Color(0xFF30B0FF)
        val palette = ColorPalette.fallback.copy(text = idle, onAccent = selected,
            accent = Color(0xFF302040), background2 = Color(0xFFEEEEEE))
        val session = PlayerMusicVideo().apply { ready = true }
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(palette, Typography.fallback, CircleShape, CircleShape, CircleShape),
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
            ) {
                PlayerAudioVideoToggle(session, onSearchManually = {})
            }
        }
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val seen = mutableSetOf<Int>()
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) seen.add(pixels[x, y].toArgb())
        assertTrue("Idle video glyph keeps its selected color", idle.toArgb() in seen)
        assertTrue("Selected audio glyph keeps onAccent", selected.toArgb() in seen)
    }

    @Test
    @Config(sdk = [32])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `selected video receives a long press through the Glass indicator`() {
        val session = PlayerMusicVideo().apply {
            selectVideo(true)
            ready = true
        }
        var searches = 0
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape,
                ),
                LocalGlassPreviewSettings provides GlassSettings(enabled = true, rippleEnabled = false),
            ) {
                PlayerAudioVideoToggle(
                    session = session,
                    onSearchManually = { searches++ },
                )
            }
        }
        val video = composeRule.onNodeWithContentDescription("Video")
        video.assertIsSelected()
        video.performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals(1, searches) }
    }

    @Test
    fun `video long press opens search and a short press selects video`() {
        val choices = mutableListOf<Boolean>()
        val session = PlayerMusicVideo { choices.add(it) }
        var searches = 0
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape,
                ),
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
            ) {
                PlayerAudioVideoToggle(
                    session = session,
                    onSearchManually = { searches++ },
                )
            }
        }
        val video = composeRule.onNodeWithContentDescription("Video")
        video.assertIsNotSelected()
        video.performSemanticsAction(SemanticsActions.OnLongClick)
        video.assertIsNotSelected()
        composeRule.runOnIdle { assertEquals(1, searches) }

        video.performClick()
        video.assertIsSelected()
        composeRule.runOnIdle { session.unavailable() }
        composeRule.onNodeWithText("Search manually").assertDoesNotExist()
        video.performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        composeRule.runOnIdle {
            assertEquals(2, searches)
            assertEquals(listOf(true), choices)
        }
    }

    @Test
    fun `opening selects the indicator without changing the remembered manual choice`() {
        val choices = mutableListOf<Boolean>()
        val session = PlayerMusicVideo { choices.add(it) }

        session.applyOpeningMode(true)
        assertEquals(true, session.selected)
        assertEquals(1, session.openingSelection)
        assertEquals(emptyList<Boolean>(), choices)

        session.chooseMode(false)
        assertEquals(false, session.selected)
        assertEquals(1, session.openingSelection)
        assertEquals(listOf(false), choices)
    }

    @Test
    fun `opening modes use the preference or remembered choice`() {
        assertEquals(false, PlayerOpeningMode.Audio.opensInVideo(lastModeWasVideo = true))
        assertEquals(true, PlayerOpeningMode.Video.opensInVideo(lastModeWasVideo = false))
        assertEquals(false, PlayerOpeningMode.RememberLast.opensInVideo(lastModeWasVideo = false))
        assertEquals(true, PlayerOpeningMode.RememberLast.opensInVideo(lastModeWasVideo = true))
    }
}
