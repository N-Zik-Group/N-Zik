package app.n_zik.android.updater.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.ScrollState
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.LocalGlassScope
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
class UpdaterGlassComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `material off retains native card fill and working actions`() =
        checkContent(enabled = false, cards = GlassAreaMode.GlassAndContour, actions = GlassAreaMode.GlassAndContour)

    @Test fun `cards can use Glass independently from action buttons`() =
        checkContent(cards = GlassAreaMode.GlassOnly, actions = GlassAreaMode.Disabled)

    @Test fun `action Glass retains native status card when card area is disabled`() =
        checkContent(cards = GlassAreaMode.Disabled, actions = GlassAreaMode.GlassAndContour)

    @Test fun `contour only preserves native fill and translation long click`() =
        checkContent(cards = GlassAreaMode.ContourOnly, actions = GlassAreaMode.ContourOnly)

    @Test fun `Glass cards and actions preserve translated accordion content`() =
        checkContent(cards = GlassAreaMode.GlassAndContour, actions = GlassAreaMode.GlassOnly)


    @Test fun `Glass accordion swipes scroll and footer reveals content below`() = checkScrollAndFooter(true)

    @Test fun `native accordion swipes scroll and final content clears opaque footer`() = checkScrollAndFooter(false)

    private fun checkScrollAndFooter(glass: Boolean) {
        val settings = GlassSettings(enabled = glass, quality = GlassQuality.Performance,
            scope = GlassScope.SettingsProfiles, jellyEnabled = true)
        lateinit var observedScroll: ScrollState
        compose.setContent {
            val shape = RoundedCornerShape(20.dp)
            val scroll = rememberScrollState()
            observedScroll = scroll
            val backdrop = rememberLayerBackdrop()
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
            ) {
                UpdaterGlassScope {
                    GlassRippleProvider(settings) {
                        Scaffold(Modifier.width(320.dp).height(480.dp), bottomBar = {
                            UpdaterBottomPanel(Modifier.fillMaxWidth().height(64.dp).testTag("pane"), backdrop) { }
                        }) { padding ->
                            // Like UpdateScreen: Scaffold padding is INSIDE verticalScroll.
                            Column(Modifier.fillMaxSize().testTag("list").layerBackdrop(backdrop)
                                .background(Color.Red).verticalScroll(scroll).padding(padding)) {
                                ChangelogCard("Added:\n" + (1..40).joinToString("\n") { "- Change $it" },
                                    null, ColorPaletteMode.Dark)
                                Box(Modifier.fillMaxWidth().height(480.dp).background(Color.Blue))
                                Text("Last release note", Modifier.fillMaxWidth().background(Color.Blue).testTag("last"))
                            }
                        }
                    }
                }
            }
        }
        fun panePixel(): Color {
            // The visible skin is a sibling behind the tagged content slot: capture the
            // completed root scene, not the empty slot's isolated display list.
            val bounds = compose.onNodeWithTag("pane").fetchSemanticsNode().boundsInRoot
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            return pixels[bounds.center.x.toInt(), bounds.center.y.toInt()]
        }
        fun swipeCard() = compose.onNodeWithTag("list").performTouchInput {
            // Start within the expanded card, above the fixed footer, with no hold.
            swipe(Offset(center.x, height * .70f), Offset(center.x, height * .15f), 180)
        }
        compose.onNodeWithText("Change 1").assertIsDisplayed()
        val before = panePixel()
        swipeCard()
        compose.runOnIdle { assertTrue("Dragging over the expanded card must scroll", observedScroll.value > 0) }
        var revealedMovingContent = false
        repeat(8) {
            swipeCard()
            val after = panePixel()
            revealedMovingContent = revealedMovingContent ||
                kotlin.math.abs(before.red - after.red) + kotlin.math.abs(before.blue - after.blue) > .1f
            if (!glass) assertEquals("The native footer remains opaque while content scrolls", before, after)
        }
        compose.onNodeWithTag("last").assertIsDisplayed()
        if (glass) assertTrue("A transparent Glass pane must reveal the content moving behind it", revealedMovingContent)
    }

    private fun checkContent(
        enabled: Boolean = true,
        cards: GlassAreaMode,
        actions: GlassAreaMode,
    ) {
        val settings = GlassSettings(
            enabled = enabled, quality = GlassQuality.Performance,
            scope = GlassScope.SettingsProfiles,
            // Conflicting shared defaults prove that the settings category owns this page.
            areaModes = mapOf(GlassArea.SettingsCards to GlassAreaMode.GlassAndContour,
                GlassArea.ActionButtons to GlassAreaMode.GlassAndContour),
            contextModes = mapOf(
                GlassContextArea(GlassScope.SettingsProfiles, GlassArea.SettingsCards) to cards,
                GlassContextArea(GlassScope.SettingsProfiles, GlassArea.ActionButtons) to actions,
            ),
        )
        var clicks = 0
        var translations = 0
        var languageSelections = 0
        val nativeFill = Color(0xFF27496A)
        compose.setContent {
            val shape = RoundedCornerShape(20.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
            ) {
                UpdaterGlassScope {
                    assertEquals(GlassScope.SettingsProfiles, LocalGlassScope.current)
                    GlassRippleProvider(settings) {
                        Column(Modifier.width(320.dp).background(DefaultDarkColorPalette.background0)) {
                            UpdaterCard(
                                modifier = Modifier.fillMaxWidth().testTag("status"),
                                colors = CardDefaults.cardColors(containerColor = nativeFill),
                            ) { Box(Modifier.fillMaxWidth().height(64.dp)) }
                            UpdaterActionCard(
                                onClick = { clicks++ }, modifier = Modifier.fillMaxWidth().testTag("check"),
                                colors = CardDefaults.cardColors(containerColor = nativeFill),
                            ) { Text("Check update", Modifier.padding(16.dp)) }
                            UpdaterIconAction(
                                onClick = { translations++ }, onLongClick = { languageSelections++ },
                                modifier = Modifier.size(40.dp).testTag("translate"),
                            ) { Text("T") }
                            UpdaterBottomPanel(Modifier.fillMaxWidth().height(48.dp).testTag("footer")) { }
                            ChangelogCard("Added:\n- New feature", "Ajout:\n- Nouveaute", ColorPaletteMode.Dark)
                        }
                    }
                }
            }
        }
        if (!enabled || !cards.hasMaterial) {
            val image = compose.onNodeWithTag("status").captureToImage().toPixelMap()
            assertEquals(nativeFill, image[image.width / 2, image.height / 2])
            val footer = compose.onNodeWithTag("footer").captureToImage().toPixelMap()
            assertEquals("The fixed panel uses an opaque native fill",
                DefaultDarkColorPalette.background0, footer[footer.width / 2, footer.height / 2])
        }
        compose.onNodeWithTag("check").performClick()
        compose.onNodeWithTag("translate").performClick()
        compose.onNodeWithTag("translate").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(1, translations)
            assertEquals(1, languageSelections)
        }
        compose.onNodeWithText("Nouveaute").assertIsDisplayed()
        compose.onNodeWithText("Ajout").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Nouveaute").assertDoesNotExist()
        compose.onNodeWithText("Ajout").performClick()
        compose.onNodeWithText("Nouveaute").assertIsDisplayed()
        val action = compose.onNodeWithTag("check").fetchSemanticsNode().boundsInRoot
        assertTrue("The card action retains its full-width touch target", action.width > 250f)
    }
}
