package app.n_zik.android.components.settings.ui

import androidx.compose.foundation.layout.WindowInsets

import app.n_zik.android.LocalPlayerAwareWindowInsets

import android.app.Application
import android.content.Context
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
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
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
import app.n_zik.android.R
import app.n_zik.android.components.onboarding.OnboardingGlassScreen
import app.n_zik.android.components.glass.GlassArea
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

    private fun showPage(onboarding: Boolean = false) {
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance, LocalGlassArtworkColor provides Color.Magenta) {
                if (onboarding) OnboardingGlassScreen(onComplete = {}) else GlassSettingsPage()
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
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("area.Nav")
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

    @Test fun `onboarding provides the same complete Glass controls and local comparison`() {
        assertCompleteGlassSurface(onboarding = true)
        composeRule.onNodeWithTag("onboarding.glass.continue").assertIsDisplayed()
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
            controls.performScrollToKey("area.${area.name}")
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

    @Test fun `fresh master defaults on in both settings and switch`() {
        context.preferences.edit().remove(glassEnabledKey).commit()
        lateinit var observed: GlassSettings
        composeRule.setContent {
            val settings = rememberGlassSettings()
            SideEffect { observed = settings }
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance) { UiGlassCard() }
        }
        composeRule.waitForIdle()
        assertTrue(observed.enabled)
        assertFalse(context.preferences.contains(glassEnabledKey))
        composeRule.onNodeWithTag("settings.glass.master").performClick()
        composeRule.waitForIdle()
        assertFalse(observed.enabled)
        assertFalse(context.preferences.getBoolean(glassEnabledKey, true))
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
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("area.Nav")
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
        assertEquals(glassProfileDefaults(GlassQuality.Quality), store.read())
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("header")
        composeRule.onNodeWithTag("settings.glass.preview").assertIsDisplayed()
    }

    @Test fun `each area has its own native card and independent mode picker`() {
        val before = store.read()
        showPage()
        GlassArea.entries.forEach { area ->
            composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("area.${area.name}")
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
        composeRule.onNodeWithTag("settings.glass.controls").performScrollToKey("area.Images")
        composeRule.onNodeWithTag("settings.glass.mode.Images").performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_disabled)).onLast().assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_contour_only)).onLast().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.glass_mode_glass_only)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.glass_mode_glass_and_contour)).assertDoesNotExist()
        composeRule.onAllNodesWithText(context.getString(R.string.glass_mode_disabled)).onLast().performClick()
        assertEquals(GlassAreaMode.Disabled, store.read().mode(GlassArea.Images))
        composeRule.onNodeWithText(context.getString(R.string.glass_image_contour_note)).assertExists()
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
