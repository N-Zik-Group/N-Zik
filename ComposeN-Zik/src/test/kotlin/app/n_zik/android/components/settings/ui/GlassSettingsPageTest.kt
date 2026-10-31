package app.n_zik.android.components.settings.ui

import androidx.compose.foundation.layout.WindowInsets

import app.n_zik.android.LocalPlayerAwareWindowInsets

import android.app.Application
import android.content.Context
import android.os.Looper
import java.time.Duration
import org.robolectric.Shadows
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.material3.Text
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.settings.common.SettingsTargetId
import app.n_zik.android.components.settings.common.SettingsTargetHighlighted
import app.n_zik.android.components.settings.common.locationDescription
import app.n_zik.android.R
import app.n_zik.android.components.onboarding.OnboardingGlassScreen
import app.n_zik.android.components.navigation.LocalAppHeaderContentInset
import app.n_zik.android.components.navigation.LocalAppHeaderOverlayInset
import app.n_zik.android.components.navigation.appHeaderOverlayTopPx
import app.n_zik.android.LocalTopBarOffset
import app.n_zik.android.core.navigation.ScaffoldBarVisibilityState
import app.n_zik.android.components.ui.screens.home.HomeHeaderMotionRegistry
import app.n_zik.android.components.player.APP_HEADER_HEIGHT
import androidx.compose.ui.platform.LocalDensity
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.contextMode
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.GlassSceneContent
import app.n_zik.android.components.glass.GlassModalContent
import app.n_zik.android.components.glass.GlassDialogOverlayHost
import app.n_zik.android.components.glass.LocalGlassMenuHost
import app.n_zik.android.components.glass.GlassRenderer
import app.n_zik.android.components.glass.glassRenderer
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.LocalGlassArtworkColor
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassNavKey
import app.n_zik.android.components.glass.glassProfileDefaults
import app.n_zik.android.components.glass.mode
import app.n_zik.android.components.glass.rememberGlassSettings
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class GlassSettingsPageTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store by lazy { GlassStore(context) }
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = RoundedCornerShape(12.dp), uiRoundnessShape = RoundedCornerShape(16.dp),
        artistThumbnailShape = RoundedCornerShape(12.dp),
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().putBoolean(glassEnabledKey, false).commit()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        store.setProfile(GlassQuality.Performance)
    }

    @Test fun `global interaction controls persist independently without removing the preview`() {
        showPage()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("interactions")
        composeRule.onNodeWithTag("settings.glass.ripple.enabled").performScrollTo().performClick()
        composeRule.runOnIdle { assertFalse(store.read().rippleEnabled) }
        composeRule.onNodeWithTag("settings.glass.jelly.enabled").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertFalse(store.read().jellyEnabled)
            assertFalse(store.read().rippleEnabled)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings.glass.ripple.intensity").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.glass.ripple.size").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.glass.jelly.intensity").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.glass.ripple.enabled").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings.glass.jelly.enabled").performScrollTo().performClick()
        listOf("ripple.intensity" to .35f, "ripple.size" to 1.5f, "jelly.intensity" to .6f).forEach { (tag, value) ->
            composeRule.onNodeWithTag("settings.glass.$tag").performScrollTo()
            composeRule.onNode(
                SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress) and
                    hasAnyAncestor(hasTestTag("settings.glass.$tag")), useUnmergedTree = true,
            ).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        }
        composeRule.runOnIdle {
            val reloaded = GlassStore(context).read()
            assertEquals(.35f, reloaded.rippleIntensity, .01f)
            assertEquals(1.5f, reloaded.rippleSize, .01f)
            assertEquals(.6f, reloaded.jellyIntensity, .01f)
            assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
        }
        composeRule.onNodeWithTag("settings.glass.preview.true").assertIsDisplayed()
        composeRule.onNodeWithTag("settings.glass.preview.false").assertIsDisplayed()
    }

    @Test fun `global search reaches every Glass option with its actual subgroup location`() {
        val targets = GlassSearchOptions.map { it.target }
        assertEquals(targets.size, targets.map { it.id }.toSet().size)
        GlassSearchOptions.forEach { option ->
            val results = app.n_zik.android.components.settings.common.searchSettingsEntries(context, context.getString(option.target.labelRes))
            assertTrue("Missing ${option.target.id}", results.any { it.id == option.target.id })
            val location = option.target.locationDescription(context)
            assertTrue(location.startsWith("${context.getString(R.string.settings)} › ${context.getString(R.string.liquid_glass)}"))
            assertFalse(location.contains(context.getString(R.string.settings_category_interface)))
            assertTrue(location.contains(context.getString(R.string.liquid_glass)))
            assertTrue(location.contains(context.getString(option.sectionTitle)))
        }
        GlassDestinationGroup.entries.forEach { group ->
            glassDestinationRows(group).forEach { row ->
                assertEquals("destination.${group.name}", glassSearchOption(row.searchId)?.section)
            }
        }
    }

    @Test fun `search opens a collapsed scoped destination and highlights the exact row without writes`() {
        val target = "glass.destination.Onboarding.SettingsCards"
        val before = context.preferences.all.toMap()
        composeRule.mainClock.autoAdvance = false
        showPage(target = target)
        pumpSearchUntil {
            runCatching {
                composeRule.onNode(SemanticsMatcher.expectValue(SettingsTargetId, target), useUnmergedTree = true)
                    .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, true))
            }.isSuccess
        }
        composeRule.onNode(SemanticsMatcher.expectValue(SettingsTargetId, target), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("settings.glass.mode.Onboarding.SettingsCards").assertIsDisplayed()
        assertEquals(before, context.preferences.all)
    }

    @Test fun `search reveals disabled dependent interaction without changing its switch`() {
        store.setBoolean(app.n_zik.android.components.glass.glassRippleEnabledKey, false)
        val target = "glass.ripple.size"
        val before = context.preferences.all.toMap()
        composeRule.mainClock.autoAdvance = false
        showPage(target = target)
        pumpSearchUntil {
            runCatching {
                composeRule.onNode(SemanticsMatcher.expectValue(SettingsTargetId, target), useUnmergedTree = true)
                    .assertIsDisplayed().assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, true))
            }.isSuccess
        }
        composeRule.onNode(SemanticsMatcher.expectValue(SettingsTargetId, target), useUnmergedTree = true).assertIsDisplayed()
        assertFalse(store.read().rippleEnabled)
        assertEquals(before, context.preferences.all)
    }

    private fun pumpSearchUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(50)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            if (condition()) return
        }
        assertTrue("The exact Glass option must become visible and highlighted", condition())
    }

    private fun scrollToArea(area: GlassArea) {
        val group = when (area) {
            GlassArea.Nav, GlassArea.Header -> "Navigation"
            GlassArea.MiniPlayer -> "Playback"
            GlassArea.Menus, GlassArea.Dropdowns, GlassArea.Dialogs, GlassArea.DialogButtons -> "Windows"
            else -> "Shared"
        }
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("destination.$group")
        if (composeRule.onAllNodesWithTag("settings.glass.mode.${area.name}").fetchSemanticsNodes().isEmpty())
            composeRule.onNodeWithTag("settings.glass.group.$group.toggle").performClick()
        composeRule.onNodeWithTag("settings.glass.mode.${area.name}").performScrollTo()
    }

    @Test fun `destination cards animate opening and closing without changing saved modes`() {
        showPage()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("destination.Navigation")
        val group = composeRule.onNodeWithTag("settings.glass.group.Navigation")
        val toggle = composeRule.onNodeWithTag("settings.glass.group.Navigation.toggle")
        // The last compact groups cannot always align at the viewport top. Measure the
        // whole card, rather than bounds clipped by LazyColumn or the pinned comparison.
        fun height() = group.getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        val collapsedHeight = height()
        val before = store.read()
        composeRule.mainClock.autoAdvance = false
        try {
            fun startTransition() {
                toggle.performClick()
                // Lazy subcomposition needs its measure pass before the transition's
                // first frame can establish a nonzero target height.
                repeat(2) {
                    composeRule.mainClock.advanceTimeByFrame()
                    composeRule.waitForIdle()
                }
            }
            startTransition()
            composeRule.mainClock.advanceTimeBy(100)
            composeRule.waitForIdle()
            val openingHeight = height()
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
            val expandedHeight = height()
            assertTrue("Opening: $collapsedHeight < $openingHeight < $expandedHeight",
                openingHeight > collapsedHeight && openingHeight < expandedHeight)
            startTransition()
            composeRule.mainClock.advanceTimeBy(64)
            composeRule.waitForIdle()
            val closingHeight = height()
            assertTrue("Closing: $collapsedHeight < $closingHeight < $expandedHeight",
                closingHeight > collapsedHeight && closingHeight < expandedHeight)
            composeRule.mainClock.advanceTimeBy(300)
            composeRule.waitForIdle()
            assertEquals(collapsedHeight, height(), 1f)
            assertEquals(before, store.read())
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test fun `each Glass choice explains its purpose instead of showing inheritance jargon`() {
        showPage()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("destination.Pages")
        composeRule.onNodeWithTag("settings.glass.group.Pages.toggle").performClick()
        val description = context.getString(R.string.glass_role_home_actions_description)
        val row = composeRule.onNodeWithTag("settings.glass.area.Home.ActionButtons")
        row.performScrollTo()
        composeRule.onNode(hasText(description) and hasAnyAncestor(hasTestTag("settings.glass.area.Home.ActionButtons")))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_inheritance_note)).assertCountEquals(0)
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_inherited)).assertCountEquals(0)
        composeRule.runOnIdle { store.setContextMode(GlassScope.Home, GlassArea.ActionButtons, GlassAreaMode.Disabled) }
        row.performScrollTo()
        composeRule.onNode(hasText(description) and hasAnyAncestor(hasTestTag("settings.glass.area.Home.ActionButtons")))
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag("settings.glass.inherit.Home.ActionButtons").assertCountEquals(0)
    }

    @Test fun `player choices persist and matching the general choice needs no extra setting`() {
        showPage()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("destination.Playback")
        composeRule.onNodeWithTag("settings.glass.group.Playback.toggle").performClick()
        composeRule.onNodeWithTag("settings.glass.mode.PlayerTransport.ActionButtons").performScrollTo().performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_disabled)).onLast().performClick()
        composeRule.waitForIdle()
        assertEquals(GlassAreaMode.Disabled, store.read().contextMode(GlassScope.PlayerTransport, GlassArea.ActionButtons))
        assertEquals(GlassAreaMode.GlassAndContour, store.read().contextMode(GlassScope.PlayerSeek, GlassArea.ActionButtons))
        composeRule.onAllNodesWithTag("settings.glass.inherit.PlayerTransport.ActionButtons").assertCountEquals(0)
        composeRule.onNodeWithTag("settings.glass.mode.PlayerTransport.ActionButtons").performScrollTo().performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_glass_and_contour)).onLast().performClick()
        composeRule.waitForIdle()
        assertTrue(store.read().contextModes.isEmpty())
        composeRule.runOnIdle { store.setAreaMode(GlassArea.ActionButtons, GlassAreaMode.ContourOnly) }
        composeRule.waitForIdle()
        assertEquals(GlassAreaMode.ContourOnly, store.read().contextMode(GlassScope.PlayerTransport, GlassArea.ActionButtons))
    }

    private fun showPage(onboarding: Boolean = false, headerInset: Dp = 0.dp, target: String = "") {
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance, LocalGlassArtworkColor provides Color.Magenta, LocalAppHeaderContentInset provides { headerInset }, LocalAppHeaderOverlayInset provides { headerInset }) {
                if (onboarding) OnboardingGlassScreen(onComplete = {}) else GlassSettingsPage(target = target)
            }
        }
    }

    /** Matches the activity's onboarding scene: AppNavigation has not installed its page backdrop. */
    private fun showOnboardingInActivityScene(): GlassMenuHostState {
        val host = GlassMenuHostState()
        composeRule.setContent {
            val settings = rememberGlassSettings()
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance, LocalGlassMenuHost provides host) {
                Box {
                    GlassSceneContent(host,
                        enabled = settings.enabled && glassRenderer(settings) != GlassRenderer.Tint) {
                        GlassModalContent(host) { OnboardingGlassScreen(onComplete = {}) }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals(null, host.backdrop)
        return host
    }

    @Test fun `onboarding quality picker uses activity scene and saves through real selection`() {
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        store.setProfile(GlassQuality.Balanced)
        val host = showOnboardingInActivityScene()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("material")
        composeRule.onNodeWithTag("settings.glass.quality").performClick()
        composeRule.onNodeWithTag("glassDialogOverlay").assertIsDisplayed()
        assertTrue(host.dialogs.hasDialogs)
        composeRule.onAllNodesWithText(context.getString(R.string.glass_quality)).onLast().performClick()
        composeRule.waitForIdle()
        assertEquals(GlassQuality.Quality, GlassStore(context).read().quality)
        composeRule.onNodeWithTag("settings.glass.quality").performClick()
        composeRule.onNodeWithTag("glassDialogOverlay").assertIsDisplayed()
        assertSelectedChoice(R.string.glass_quality)
        composeRule.onAllNodesWithText(context.getString(R.string.glass_balanced)).onLast().performClick()
        composeRule.waitForIdle()
        assertEquals(GlassQuality.Balanced, GlassStore(context).read().quality)
    }

    @Test fun `onboarding numeric dialog saves and rereads while activity hosts Glass dialogs`() {
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        store.setProfile(GlassQuality.Balanced)
        showOnboardingInActivityScene()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("colour")
        val label = context.getString(R.string.glass_film)
        scrollControlsToText(label)
        composeRule.onNodeWithText(label).performClick()
        composeRule.onNodeWithTag("glassDialogOverlay").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("0.263")
        composeRule.onNodeWithText(context.getString(R.string.dialog_save)).performClick()
        composeRule.waitForIdle()
        assertEquals(.263f, GlassStore(context).read().film, .0001f)
        composeRule.onNodeWithText(label).performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("0.263")
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { GlassStore(context).setFloat(glassFilmKey, .418f) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("0.418")
    }

    @Test fun `onboarding area choices save independently and update after an external store write`() {
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        store.setProfile(GlassQuality.Balanced)
        showOnboardingInActivityScene()
        scrollToArea(GlassArea.Nav)
        composeRule.onNodeWithTag("settings.glass.mode.Nav").performClick()
        composeRule.onNodeWithTag("glassDialogOverlay").assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_contour_only)).onLast().performClick()
        composeRule.waitForIdle()
        assertEquals(GlassAreaMode.ContourOnly, GlassStore(context).read().mode(GlassArea.Nav))
        assertEquals(GlassAreaMode.GlassAndContour, GlassStore(context).read().mode(GlassArea.Header))
        composeRule.onNodeWithTag("settings.glass.mode.Nav")
            .assert(SemanticsMatcher("Saved mode is shown") { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .any { it.text.contains(context.getString(R.string.glass_mode_contour_only)) }
            })
        composeRule.runOnIdle { GlassStore(context).setAreaMode(GlassArea.Nav, GlassAreaMode.GlassOnly) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings.glass.mode.Nav").performClick()
        composeRule.onNodeWithTag("glassDialogOverlay").assertIsDisplayed()
        assertSelectedChoice(R.string.glass_mode_glass_only)
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_disabled)).onLast().performClick()
        composeRule.waitForIdle()
        assertEquals(GlassAreaMode.Disabled, GlassStore(context).read().mode(GlassArea.Nav))
    }

    private fun assertSelectedChoice(label: Int) {
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true) and
                hasAnySibling(hasText(context.getString(label))),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test fun `onboarding master off preserves native dialogs and still saves values`() {
        showOnboardingInActivityScene()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("material")
        composeRule.onNodeWithTag("settings.glass.quality").performClick()
        composeRule.onNodeWithTag("glassDialogOverlay").assertDoesNotExist()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_quality)).onLast().performClick()
        composeRule.waitForIdle()
        assertEquals(GlassQuality.Quality, GlassStore(context).read().quality)
        assertFalse(context.preferences.getBoolean(glassEnabledKey, true))
        composeRule.onNodeWithTag("settings.glass.quality").performClick()
        composeRule.onNodeWithTag("glassDialogOverlay").assertDoesNotExist()
        assertSelectedChoice(R.string.glass_quality)
    }

    @Test fun `settings provides simultaneous local ON and OFF previews and every Glass group`() {
        assertCompleteGlassSurface(onboarding = false)
    }

    @Test fun `pinned comparison follows shared header hide and reveal without losing scroll`() {
        val scaffold = ScaffoldBarVisibilityState()
        val motion = HomeHeaderMotionRegistry(scaffold.topOffset)
        val statusInset = 24.dp
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalTopBarOffset provides scaffold.topOffset,
                LocalAppHeaderContentInset provides { APP_HEADER_HEIGHT + statusInset },
                LocalAppHeaderOverlayInset provides {
                    with(density) {
                        appHeaderOverlayTopPx(APP_HEADER_HEIGHT.toPx(), statusInset.toPx(), scaffold.topOffset.floatValue).toDp()
                    }
                },
            ) { GlassSettingsPage() }
        }
        val controls = composeRule.onNodeWithTag("settings.glass.controls")
        controls.performScrollToKey("destination.Shared")
        composeRule.waitForIdle()
        val initialScroll = controls.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val preview = composeRule.onNodeWithTag("settings.glass.preview")
        fun assertTop(expected: Dp) {
            composeRule.waitForIdle()
            assertEquals(with(composeRule.density) { (expected + 12.dp).toPx() }, preview.fetchSemanticsNode().boundsInRoot.top, 1f)
            listOf(true, false).forEach { enabled -> composeRule.onNodeWithTag("settings.glass.preview.$enabled").assertIsDisplayed() }
            assertEquals(initialScroll, controls.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), .01f)
        }
        assertTop(APP_HEADER_HEIGHT + statusInset)
        composeRule.runOnIdle { motion.scrollBy(with(composeRule.density) { -APP_HEADER_HEIGHT.toPx() / 2 }, with(composeRule.density) { APP_HEADER_HEIGHT.toPx() }) }
        assertTop(APP_HEADER_HEIGHT / 2 + statusInset)
        composeRule.runOnIdle { motion.scrollBy(with(composeRule.density) { -APP_HEADER_HEIGHT.toPx() }, with(composeRule.density) { APP_HEADER_HEIGHT.toPx() }) }
        assertTop(statusInset)
        composeRule.runOnIdle { scaffold.reveal(); motion.sync() }
        assertTop(APP_HEADER_HEIGHT + statusInset)
        composeRule.onAllNodesWithTag("settings.glass.preview").assertCountEquals(1)
    }

    @Test fun `comparison stays in one fixed position while scrolling all Glass controls`() {
        showPage(headerInset = 56.dp)
        val preview = composeRule.onNodeWithTag("settings.glass.preview")
        val initialBounds = preview.fetchSemanticsNode().boundsInRoot
        assertTrue("Preview clears the app header", initialBounds.top >= with(composeRule.density) { 56.dp.toPx() })
        val controls = composeRule.onNodeWithTag("settings.glass.controls")
        listOf("colour", "edges", "destination.Shared").forEach { key ->
            controls.performScrollToKey(key)
            listOf(true, false).forEach { enabled ->
                composeRule.onNodeWithTag("settings.glass.preview.$enabled").assertIsDisplayed()
            }
            composeRule.onAllNodesWithTag("settings.glass.preview").assertCountEquals(1)
            assertEquals(initialBounds, preview.fetchSemanticsNode().boundsInRoot)
            assertTrue("Controls stay below the comparison", controls.fetchSemanticsNode().boundsInRoot.top >= initialBounds.bottom)
        }
        composeRule.onNodeWithText(context.getString(R.string.page_glass_summary)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land")
    fun `short landscape keeps both previews visible and the final area reachable`() {
        showPage(headerInset = 56.dp)
        val controls = composeRule.onNodeWithTag("settings.glass.controls")
        scrollToArea(GlassArea.SearchBars)
        composeRule.onNodeWithTag("settings.glass.mode.SearchBars").assertIsDisplayed().performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_contour_only)).onLast().performClick()
        assertEquals(GlassAreaMode.ContourOnly, store.read().mode(GlassArea.SearchBars))
        listOf(true, false).forEach { enabled ->
            composeRule.onNodeWithTag("settings.glass.preview.$enabled").assertIsDisplayed()
        }
        assertTrue("Controls retain at least half the usable height", controls.fetchSemanticsNode().boundsInRoot.height > composeRule.onNodeWithTag("settings.glass.preview").fetchSemanticsNode().boundsInRoot.height)
    }

    @Test fun `onboarding provides the same complete Glass controls and local comparison`() {
        assertCompleteGlassSurface(onboarding = true)
        val controls = composeRule.onNodeWithTag("settings.glass.controls")
        controls.performScrollToKey("onboarding.footer")
        composeRule.onNodeWithTag("onboarding.glass.continue").assertIsDisplayed()
            .assert(hasAnyAncestor(hasTestTag("settings.glass.controls")))
        composeRule.onNodeWithTag("settings.glass.preview").assertDoesNotExist()
        controls.performScrollToKey("header")
        composeRule.onNodeWithTag("onboarding.glass.continue").assertDoesNotExist()
    }

    private fun assertCompleteGlassSurface(onboarding: Boolean) {
        val before = store.read()
        showPage(onboarding)
        listOf(true, false).forEach { enabled ->
            composeRule.onNodeWithTag("settings.glass.preview.$enabled")
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    context.getString(if (enabled) R.string.on else R.string.off),
                ))
        }
        val controls = composeRule.onNodeWithTag("settings.glass.controls")
        val groups = listOf(
            "material" to R.string.glass_group_material,
            "colour" to R.string.glass_group_colour,
            "readability" to R.string.glass_group_readability,
            "edges" to R.string.glass_group_edges,
        )
        groups.forEach { (key, label) ->
            controls.performScrollToKey(key)
            composeRule.onNodeWithText(context.getString(label)).assertExists()
        }
        GlassArea.entries.forEach { area ->
            scrollToArea(area)
            composeRule.onNodeWithTag("settings.glass.mode.${area.name}").assertExists()
        }
        assertEquals(before, store.read())
        assertFalse("Preview copies never persist a master toggle", context.preferences.getBoolean(glassEnabledKey, true))
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [32])
    fun `comparison renders different native mini player material without persisting forced modes`() {
        store.setFloat(glassFilmKey, .1f)
        val before = store.read()
        showPage()
        fun pixels(enabled: Boolean) = composeRule.onNode(
            hasTestTag("settings.glass.miniPlayer") and
                hasAnyAncestor(hasTestTag("settings.glass.preview.$enabled")),
        ).captureToImage().toPixelMap()
        val on = pixels(true)
        val off = pixels(false)
        assertEquals(on.width, off.width)
        assertEquals(on.height, off.height)
        val differences = (0 until on.width).sumOf { x ->
            (0 until on.height).count { y -> on[x, y] != off[x, y] }
        }
        assertTrue("ON must render its Glass material while OFF uses the native component fill", differences > on.width)
        assertEquals(before, store.read())
        assertFalse(context.preferences.getBoolean(glassEnabledKey, true))

        val initialBounds = composeRule.onNodeWithTag("settings.glass.preview").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("colour")
        val filmLabel = context.getString(R.string.glass_film)
        scrollControlsToText(filmLabel)
        composeRule.onNodeWithText(filmLabel).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("0.85")
        composeRule.onNodeWithText(context.getString(R.string.dialog_save)).performClick()
        composeRule.waitForIdle()
        val updated = pixels(true)
        val updatedDifferences = (0 until on.width).sumOf { x ->
            (0 until on.height).count { y -> on[x, y] != updated[x, y] }
        }
        assertTrue("Pinned ON comparison reflects a real material edit immediately", updatedDifferences > on.width)
        assertEquals(initialBounds, composeRule.onNodeWithTag("settings.glass.preview").fetchSemanticsNode().boundsInRoot)
        composeRule.onNodeWithTag("settings.glass.preview.false").assertIsDisplayed()
        assertEquals(.85f, store.read().film, .001f)
        assertFalse("Editing live material preserves the disabled master", context.preferences.getBoolean(glassEnabledKey, true))
    }

    @Test fun `Glass page shows its description below the title`() {
        showPage()
        composeRule.onNodeWithText(context.getString(R.string.page_glass_summary)).assertIsDisplayed()
    }

    @Test fun `readability sliders save dialog and menu dimming independently while master is off`() {
        showPage()
        val dialogLabel = context.getString(R.string.glass_dialog_dimming)
        val menuLabel = context.getString(R.string.glass_menu_dimming)
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("readability")
        scrollControlsToText(dialogLabel)
        composeRule.onNodeWithText(dialogLabel).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("0.2")
        composeRule.onNodeWithText(context.getString(R.string.dialog_save)).performClick()
        scrollControlsToText(menuLabel)
        composeRule.onNodeWithText(menuLabel).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("0.8")
        composeRule.onNodeWithText(context.getString(R.string.dialog_save)).performClick()
        composeRule.waitForIdle()
        assertEquals(.2f, store.read().dialogDimming, .001f)
        assertEquals(.8f, store.read().menuDimming, .001f)
        assertFalse(context.preferences.getBoolean(glassEnabledKey, true))
    }

    private fun scrollControlsToText(label: String) {
        val list = composeRule.onNodeWithTag("settings.glass.controls")
        repeat(8) {
            composeRule.waitForIdle()
            val viewport = list.fetchSemanticsNode()
            val row = composeRule.onAllNodesWithText(label).fetchSemanticsNodes().singleOrNull()
            val relativeTop = row?.let { it.positionInRoot.y - viewport.positionInRoot.y }
            if (row != null && relativeTop != null && relativeTop >= 0f && relativeTop + row.size.height <= viewport.size.height) return
            val distance = relativeTop?.minus(64f) ?: (viewport.size.height * .5f)
            list.performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, distance) }
        }
        assertTrue("Control should become visible after bounded list scrolling: $label", false)
    }

    @Test fun `fresh master defaults off in both settings and switch`() {
        context.preferences.edit().remove(glassEnabledKey).commit()
        lateinit var observed: GlassSettings
        composeRule.setContent {
            val settings = rememberGlassSettings()
            SideEffect { observed = settings }
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) { UiGlassCard() }
        }
        composeRule.waitForIdle()
        assertFalse(observed.enabled)
        assertFalse(context.preferences.contains(glassEnabledKey))
        composeRule.onNodeWithTag("settings.glass.master").performClick()
        composeRule.waitForIdle()
        confirmEnableWarning()
        assertTrue(observed.enabled)
        assertTrue(context.preferences.getBoolean(glassEnabledKey, false))
    }

    @Test fun `saved disabled master survives defaults profile changes and reset`() {
        lateinit var observed: GlassSettings
        composeRule.setContent {
            val settings = rememberGlassSettings()
            SideEffect { observed = settings }
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) { UiGlassCard() }
        }
        composeRule.waitForIdle()
        assertFalse(observed.enabled)
        composeRule.runOnIdle {
            store.setProfile(GlassQuality.Quality)
            store.reset()
        }
        composeRule.waitForIdle()
        assertFalse(observed.enabled)
        assertFalse(context.preferences.getBoolean(glassEnabledKey, true))
        composeRule.onNodeWithTag("settings.glass.master").performClick()
        composeRule.waitForIdle()
        confirmEnableWarning()
        assertTrue(observed.enabled)
    }

    @Test fun masterIsAvailableWithoutChangingFineMaterialPreferences() {
        store.setFloat(glassFilmKey, .263f)
        val before = store.read()
        showPage()
        composeRule.onNodeWithTag("settings.glass.preview").assertIsDisplayed()
        composeRule.onNodeWithTag("settings.glass.master").performClick()
        confirmEnableWarning()
        assertTrue(context.preferences.getBoolean(glassEnabledKey, false))
        assertEquals(before, store.read())
        composeRule.onNodeWithTag("settings.glass.master").performClick()
        assertFalse(context.preferences.getBoolean(glassEnabledKey, true))
        composeRule.onNodeWithTag("settings.glass.preview").assertIsDisplayed()
        composeRule.onNodeWithTag("settings.glass.back").assertDoesNotExist()
        assertEquals(.263f, store.read().film)
    }

    @Test fun `real master toggles preserve navigation composition and page scroll`() {
        lateinit var navigation: NavHostController
        lateinit var searchQuery: MutableState<String>
        lateinit var compositionIdentity: Any
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) {
                GlassRippleProvider {
                    navigation = rememberNavController()
                    searchQuery = remember { mutableStateOf("") }
                    compositionIdentity = remember { Any() }
                    NavHost(navigation, startDestination = "home",
                        enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
                        composable("home") { Text("Home") }
                        composable("glass") { GlassSettingsPage() }
                    }
                }
            }
        }
        composeRule.runOnIdle { navigation.navigate("glass"); searchQuery.value = "glass filter" }
        composeRule.onNodeWithTag("settings.glass.preview").assertIsDisplayed()
        val originalNavigation = navigation
        val originalIdentity = compositionIdentity
        val controls = composeRule.onNodeWithTag("settings.glass.controls")
        controls.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 24f) }
        composeRule.waitForIdle()
        val offset = controls.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Regression requires a nonzero settings scroll", offset > 0f)
        val before = store.read()
        listOf(true, false, true).forEach { enabled ->
            composeRule.onNodeWithTag("settings.glass.master").performClick()
            composeRule.waitForIdle()
            if (enabled) confirmEnableWarning()
            composeRule.onNodeWithTag("settings.glass.preview").assertIsDisplayed()
            composeRule.runOnIdle {
                assertSame(originalNavigation, navigation)
                assertSame(originalIdentity, compositionIdentity)
                assertEquals("glass", navigation.currentDestination?.route)
                assertEquals("home", navigation.previousBackStackEntry?.destination?.route)
                assertEquals("glass filter", searchQuery.value)
                assertEquals(enabled, context.preferences.getBoolean(glassEnabledKey, !enabled))
                assertEquals(before, store.read())
            }
            assertEquals(offset, controls.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), .01f)
        }
    }

    private fun confirmEnableWarning() {
        composeRule.onNodeWithText(context.getString(R.string.glass_enable_warning_description)).assertIsDisplayed()
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
        composeRule.onNodeWithText(context.getString(R.string.glass_enable_confirm)).performClick()
        composeRule.waitForIdle()
    }

    @Test fun `enable warning can be cancelled without changing Glass preferences`() {
        showPage()
        val before = store.read()
        composeRule.onNodeWithTag("settings.glass.master").performClick()
        composeRule.onNodeWithText(context.getString(R.string.glass_enable_warning_description)).assertIsDisplayed()
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
        composeRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        composeRule.waitForIdle()
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
        assertEquals(before, store.read())
        composeRule.onNodeWithTag("settings.glass.master").performClick()
        confirmEnableWarning()
        assertTrue(context.preferences.getBoolean(glassEnabledKey, false))
        assertEquals(before, store.read())
    }

    @Test fun nativeNumericDialogRoundTripPreservesFineMaterialValue() {
        store.setFloat(glassFilmKey, .263f)
        showPage()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("colour")
        scrollControlsToText(context.getString(R.string.glass_film))
        composeRule.onNodeWithText(context.getString(R.string.glass_film)).performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("0.263")
        composeRule.onNodeWithText(context.getString(R.string.dialog_save)).performClick()
        assertEquals(.263f, store.read().film)
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
    }

    @Test fun `area picker offers all four modes while the master stays disabled`() {
        store.setFloat(glassFilmKey, .263f)
        showPage()
        scrollToArea(GlassArea.Nav)
        val modes = listOf(
            R.string.glass_mode_disabled to GlassAreaMode.Disabled,
            R.string.glass_mode_contour_only to GlassAreaMode.ContourOnly,
            R.string.glass_mode_glass_only to GlassAreaMode.GlassOnly,
            R.string.glass_mode_glass_and_contour to GlassAreaMode.GlassAndContour,
        )
        modes.forEach { (label, mode) ->
            composeRule.onNodeWithTag("settings.glass.mode.Nav").performClick()
            modes.forEach { (choice, _) ->
                composeRule.onAllNodesWithText(context.getString(choice)).onLast().assertIsDisplayed()
            }
            composeRule.onAllNodesWithText(context.getString(label)).onLast().performClick()
            assertEquals(mode, store.read().mode(GlassArea.Nav))
            assertEquals(.263f, store.read().film)
            assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
        }
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("header")
        composeRule.onNodeWithTag("settings.glass.preview").assertIsDisplayed()
        composeRule.onNodeWithTag("settings.glass.reset").assertDoesNotExist()
    }

    @Test fun choosingAProfilePreservesItsDefaultsAndLeavesMasterDisabled() {
        store.setFloat(glassFilmKey, .263f)
        store.setBoolean(glassNavKey, false)
        showPage()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("material")
        composeRule.onNodeWithTag("settings.glass.quality").performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_quality)).onLast().performClick()
        assertEquals(glassProfileDefaults(GlassQuality.Quality).copy(nav = false), store.read())
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("header")
        composeRule.onNodeWithTag("settings.glass.preview").assertIsDisplayed()
    }

    @Test fun `shared area choices remain independently editable inside five destination groups`() {
        val before = store.read()
        showPage()
        GlassArea.entries.forEach { area ->
            scrollToArea(area)
            composeRule.onNodeWithTag("settings.glass.area.${area.name}").assertExists()
            composeRule.onNodeWithTag("settings.glass.mode.${area.name}").performClick()
            composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_contour_only)).onLast().performClick()
            val after = store.read()
            assertEquals(GlassAreaMode.ContourOnly, after.mode(area))
            GlassArea.entries.dropWhile { it != area }.drop(1).forEach { other ->
                assertEquals("Changing $area must preserve $other", before.mode(other), after.mode(other))
            }
        }
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
    }

    @Test fun `images offer only disabled or contour and explain their sharp interior`() {
        showPage()
        scrollToArea(GlassArea.Images)
        composeRule.onNodeWithTag("settings.glass.mode.Images").performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_disabled)).onLast().assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_contour_only)).onLast().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.glass_mode_glass_only)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.glass_mode_glass_and_contour)).assertDoesNotExist()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_disabled)).onLast().performClick()
        assertEquals(GlassAreaMode.Disabled, store.read().mode(GlassArea.Images))
        composeRule.onNodeWithText(context.getString(R.string.glass_where_image_rims_description)).assertExists()
        composeRule.onNodeWithTag("settings.glass.mode.Images").performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_contour_only)).onLast().performClick()
        assertEquals(GlassAreaMode.ContourOnly, store.read().mode(GlassArea.Images))
    }

    @Test fun nativeTintSourceDialogExposesDynamicArtworkWithoutChangingMaterialValues() {
        store.setFloat(glassFilmKey, .263f)
        showPage()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("colour")
        composeRule.onNodeWithTag("settings.glass.tintSource").performClick()
        composeRule.onNodeWithText(context.getString(R.string.liquid_glass_artwork)).performClick()
        assertEquals(GlassTintSource.Artwork, store.read().source)
        assertEquals(.263f, store.read().film)
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
    }

    @Test fun hueAndColourPickerAreOnlyAvailableForCustomSource() {
        store.setTint(GlassTintSource.Custom)
        showPage()
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("colour")
        composeRule.onNodeWithTag("settings.glass.hue").assertExists()
        composeRule.onNodeWithTag("settings.glass.tint").assertExists()
        GlassTintSource.entries.filter { it != GlassTintSource.Custom }.forEach { source ->
            composeRule.runOnIdle { store.setTint(source) }
            composeRule.onNodeWithTag("settings.glass.hue").assertDoesNotExist()
            composeRule.onNodeWithTag("settings.glass.tint").assertDoesNotExist()
            assertEquals(source, store.read().source)
        }
        composeRule.runOnIdle { store.setTint(GlassTintSource.Custom) }
        composeRule.onNodeWithTag("settings.glass.hue").assertExists()
        composeRule.onNodeWithTag("settings.glass.tint").assertExists()
    }
}
