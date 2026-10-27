package app.n_zik.android.components.ui.screens.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.core.navigation.rememberAppBackAction
import app.n_zik.android.core.navigation.ScreenTransitions
import app.it.fast4x.rimusic.enums.TransitionEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.core.navigation.LocalSettingsBackNavigation
import app.n_zik.android.core.navigation.SettingsBackNavigation
import app.n_zik.android.components.settings.common.hasAutoUpdate
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.updater.services.Updater
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Wiring tests for the settings host (spec-settings-global-page, rows
 * ENTRY_DEFAULT, ENTRY_DEEPLINK, NAV_ITEM, UPDATE_ITEM, SEARCH_GLOBAL,
 * SUBPAGE_BACK, SUBPAGE_TRANSITION): entering without deep-link params shows
 * the global page; tapping an item opens the matching category (its own
 * navigation destination, so the standard screen transition plays and back
 * returns to the global page); tapping Update navigates to the existing
 * updater route on the app-level NavHost; a deep link (tab + focus) opens the
 * tab directly, bypassing the global page, but back still returns to the
 * global page first (the tab is pushed on top of it); tapping a card search
 * result opens its category and brings the card into view.
 *
 * The host is composed on a FROZEN clock and pumped by hand: the global
 * page's Update row trailing text is an infinite marquee, which would keep
 * the real clock from ever settling (same harness as
 * SettingsGlobalPageWiringTest). The debug build ships with IS_AUTOUPDATE=false,
 * so [hasAutoUpdate] is stubbed to true.
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine
 * on the JUnit 5 platform (`createComposeRule()` only works with JUnit 4),
 * with the plain [Application] so the app's heavy init is skipped (same
 * harness as [app.n_zik.android.legacyoffmain.ui.screens.settings.BurgerMenuFocusScrollTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SettingsScreenHostWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val navController = mockk<NavController>()
    private val backNavigation = SettingsBackNavigation { "settings" }

    /**
     * The Compose test activity: the 1.12.1 [ComposeContentTestRule] interface
     * no longer exposes it, but the rule implementation still does.
     */
    private val testActivity: ComponentActivity
        get() = (composeRule as AndroidComposeTestRule<*, *>).activity

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before
    fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns context
        Dependencies.init(mainApplication)
        // The embedded legacy tab reads the keystore-backed prefs
        // (isYouTubeLoginEnabled); AndroidKeyStore is unavailable on the JVM,
        // so stub the extension away (same pattern as
        // AccountsSettingsEmbeddingWiringTest).
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns mockk<SharedPreferences>(relaxed = true)
        // The host composes the global page; the debug build has
        // IS_AUTOUPDATE=false, whose blocked Update row marquee is an infinite
        // animation that would keep the frozen clock from ever settling.
        mockkStatic("app.n_zik.android.components.settings.common.SettingsAutoUpdateKt")
        every { hasAutoUpdate() } returns true
        Updater.githubRelease = null
        every { navController.navigate(any<String>()) } just runs
        every { navController.popBackStack(NavRoutes.home.name, false) } returns true
    }

    @After
    fun tearDown() {
        Updater.githubRelease = null
        unmockkAll()
    }

    /**
     * Drives the frozen Compose clock and the Robolectric main looper until
     * [condition] holds (or [timeoutMs] elapses). No `waitForIdle` in the loop:
     * the infinite marquee keeps a frozen clock from ever settling, so
     * advancing the clock IS the pump (also settles the 350 ms screen
     * transitions between the global page and the tab destinations).
     */
    private fun pumpUntil(label: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            if (condition()) return
        }
        assertTrue("[$label] not met within ${timeoutMs}ms", condition())
    }

    /** Composes the host on a frozen clock and pumps until the global page is settled. */
    private fun showHost(initialTab: Int = 0, focus: String = "") {
        composeRule.mainClock.autoAdvance = false
        // Models the parent host callback; the nested host takes priority only above its root.
        testActivity.onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navController.popBackStack(NavRoutes.home.name, false)
            }
        })
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalSettingsBackNavigation provides backNavigation,
            ) {
                SettingsScreen(
                    navController = navController,
                    initialTab = initialTab,
                    focus = focus,
                )
            }
        }
        // Deep link: pump until the tab is composed AND the global page is
        // gone (they overlap during the 350 ms transition, so the tab
        // alone is not enough to declare the transition settled)
        pumpUntil("the host is composed", 30_000) {
            runCatching {
                if (initialTab != 0 || focus.isNotEmpty()) tabIsComposed() && globalPageIsGone()
                else globalPageIsComposed()
            }.getOrDefault(false)
        }
    }

    private fun globalPageIsComposed(): Boolean =
        runCatching {
            composeRule.onNodeWithTag("settings_global_update").fetchSemanticsNode()
            composeRule.onNodeWithTag("settings_global_category_about").fetchSemanticsNode()
        }.isSuccess

    private fun globalPageIsGone(): Boolean =
        composeRule.onAllNodesWithTag("settings_global_update").fetchSemanticsNodes().isEmpty()

    private fun tabIsComposed(): Boolean =
        composeRule
            .onAllNodesWithText(
                context.getString(R.string.picture_in_picture),
                substring = false,
                useUnmergedTree = true
            )
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun categoryIsComposed(): Boolean = runCatching {
        composeRule.onNodeWithTag("settings.category.playback", useUnmergedTree = true).fetchSemanticsNode()
    }.isSuccess

    @Test
    fun enteringWithoutADeepLinkShowsTheGlobalPage() {
        showHost(initialTab = 0, focus = "")

        composeRule.onNodeWithTag("settings_global_update").fetchSemanticsNode()
        composeRule.onNodeWithTag("settings_global_category_about").fetchSemanticsNode()
    }

    @Test
    fun theDeepLinkOpensTheTabDirectlyWithoutTheGlobalPage() {
        showHost(initialTab = 0, focus = "pip")

        assertTrue("the global page must not stay composed once the tab is open", globalPageIsGone())
        assertTrue("the General tab's PiP card must be composed", tabIsComposed())
    }

    @Test
    fun tappingACategoryFromTheGlobalPageOpensItsThematicContent() {
        showHost(initialTab = 0, focus = "")

        composeRule.onNodeWithTag("settings_global_category_playback").performClick()
        pumpUntil("the Playback category is composed", 30_000) {
            // The global page and the tab overlap during the 350 ms
            // transition: pump until the page is fully gone too
            categoryIsComposed() && globalPageIsGone()
        }

        assertTrue("the global page must not stay composed once the tab is open", globalPageIsGone())
    }

    @Test
    fun disabledAnimationsDoNotAddASettingsSpecificFade() {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit()
            .putString(app.it.fast4x.rimusic.utils.transitionEffectKey, "None").commit()
        showHost()
        composeRule.onNodeWithTag("settings_global_category_playback").performClick()
        repeat(4) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        assertTrue(globalPageIsGone())
        assertTrue(categoryIsComposed())
    }

    @Test
    fun forwardFadeKeepsBothPagesDuringOneTransitionThenOnlyTheCategory() {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit()
            .putString(app.it.fast4x.rimusic.utils.transitionEffectKey, "Fade").commit()
        showHost()
        composeRule.onNodeWithTag("settings_global_category_playback").performClick()
        repeat(5) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        assertTrue("Global remains during the shared fade", globalPageIsComposed())
        assertTrue("The category enters during the same fade", categoryIsComposed())
        repeat(30) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        assertTrue("One 350ms transition removes Global", globalPageIsGone())
        assertTrue(categoryIsComposed())
    }

    @Test
    fun backFromACategoryReturnsToTheGlobalPage() {
        showHost(initialTab = 0, focus = "")

        composeRule.onNodeWithTag("settings_global_category_playback").performClick()
        pumpUntil("the Playback category is composed", 30_000) {
            // The global page and the tab overlap during the 350 ms
            // transition: pump until the page is fully gone too
            categoryIsComposed() && globalPageIsGone()
        }
        assertTrue("the global page must not stay composed once the tab is open", globalPageIsGone())

        // The tab is its own destination: the native back pops it and returns
        // to the global page (the standard screen transition plays).
        testActivity.onBackPressedDispatcher.onBackPressed()
        pumpUntil("the global page is back after the tab pop", 30_000) {
            globalPageIsComposed() && !categoryIsComposed()
        }

        assertTrue("the tab must not stay composed after back", !categoryIsComposed())
    }

    @Test
    fun predictiveBackPreviewsGlobalAndCancellationKeepsTheCategory() {
        showHost()
        composeRule.onNodeWithTag("settings_global_category_playback").performClick()
        pumpUntil("category settled", 30_000) { categoryIsComposed() && globalPageIsGone() }
        val dispatcher = testActivity.onBackPressedDispatcher
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame(); composeRule.waitForIdle() }
        assertTrue("Predictive back composes the previous page before commit", globalPageIsComposed())
        assertTrue(categoryIsComposed())
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        pumpUntil("cancel preserves category", 30_000) { categoryIsComposed() && globalPageIsGone() }
        verify(exactly = 0) { navController.popBackStack(NavRoutes.home.name, false) }
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(150f, 0f, 0.7f, BackEventCompat.EDGE_LEFT))
        }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame(); composeRule.waitForIdle() }
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        pumpUntil("commit returns global", 30_000) { globalPageIsComposed() && !categoryIsComposed() }
        verify(exactly = 0) { navController.popBackStack(NavRoutes.home.name, false) }
    }

    @Test
    fun realParentHostPreviewsPreviousRouteAndKeepsItsStackOnCancelledRootBack() {
        lateinit var outer: NavHostController
        lateinit var router: SettingsBackNavigation
        lateinit var toolbarBack: () -> Unit
        var nestedObservedInParent = false
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            outer = rememberNavController()
            val entry by outer.currentBackStackEntryAsState()
            router = remember { SettingsBackNavigation { entry?.destination?.route } }
            // Observe production state in the parent composition, as AppNavigation does.
            nestedObservedInParent = router.hasNestedBackStack
            val transitions = remember { ScreenTransitions(TransitionEffect.Fade) }
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalSettingsBackNavigation provides router,
            ) {
                toolbarBack = rememberAppBackAction(outer)
                NavHost(
                    outer,
                    startDestination = "home",
                    enterTransition = transitions.enterTransition,
                    exitTransition = transitions.exitTransition,
                    popEnterTransition = transitions.popEnterTransition,
                    popExitTransition = transitions.popExitTransition,
                ) {
                    composable("home") { Text("Home", Modifier.testTag("outer_home")) }
                    composable("previous") { Text("Previous", Modifier.testTag("outer_previous")) }
                    composable("settings") { SettingsScreen(navController = outer) }
                }
            }
        }
        pumpUntil("outer home", 30_000) { outer.currentDestination?.route == "home" }
        composeRule.runOnIdle { outer.navigate("previous") }
        repeat(30) { composeRule.mainClock.advanceTimeByFrame(); composeRule.waitForIdle() }
        composeRule.runOnIdle { outer.navigate("settings") }
        pumpUntil("Settings root settled", 30_000) {
            globalPageIsComposed() && composeRule.onAllNodesWithTag("outer_previous").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(!nestedObservedInParent)
        composeRule.onNodeWithTag("settings_global_category_playback").performClick()
        pumpUntil("parent observes nested stack", 30_000) {
            nestedObservedInParent && categoryIsComposed() && globalPageIsGone()
        }
        composeRule.runOnIdle { toolbarBack() }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame(); composeRule.waitForIdle() }
        assertTrue("Toolbar pop retains outgoing category during its inverse fade", categoryIsComposed())
        assertTrue("Toolbar pop animates the hub back in", globalPageIsComposed())
        pumpUntil("nested pop keeps Settings outer entry", 30_000) {
            !nestedObservedInParent && globalPageIsComposed() && !categoryIsComposed()
        }
        assertTrue(outer.currentDestination?.route == "settings")
        val dispatcher = testActivity.onBackPressedDispatcher
        fun progressRootBack() {
            composeRule.runOnIdle {
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
            }
            repeat(5) { composeRule.mainClock.advanceTimeByFrame(); composeRule.waitForIdle() }
        }
        progressRootBack()
        composeRule.onNodeWithTag("outer_previous").fetchSemanticsNode()
        assertTrue(globalPageIsComposed())
        assertTrue(outer.currentDestination?.route == "settings")
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        pumpUntil("root cancellation preserves Settings", 30_000) {
            globalPageIsComposed() && composeRule.onAllNodesWithTag("outer_previous").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(outer.currentDestination?.route == "settings")
        progressRootBack()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        pumpUntil("root gesture commits to actual prior route", 30_000) {
            outer.currentDestination?.route == "previous" && globalPageIsGone()
        }
        composeRule.onNodeWithTag("outer_previous").fetchSemanticsNode()
        assertTrue(!router.isActive)
        assertTrue(!nestedObservedInParent)
        assertTrue(outer.previousBackStackEntry?.destination?.route == "home")

        composeRule.runOnIdle { outer.navigate("settings") }
        pumpUntil("Settings settled for toolbar root back", 30_000) {
            globalPageIsComposed() && composeRule.onAllNodesWithTag("outer_previous").fetchSemanticsNodes().isEmpty()
        }
        composeRule.runOnIdle { toolbarBack() }
        repeat(5) { composeRule.mainClock.advanceTimeByFrame(); composeRule.waitForIdle() }
        assertTrue("Toolbar root pop retains outgoing Settings during fade", globalPageIsComposed())
        composeRule.onNodeWithTag("outer_previous").fetchSemanticsNode()
        pumpUntil("toolbar matches Android root target", 30_000) {
            outer.currentDestination?.route == "previous" && globalPageIsGone()
        }
        composeRule.runOnIdle { toolbarBack() }
        pumpUntil("toolbar returns Home", 30_000) { outer.currentDestination?.route == "home" }
        composeRule.runOnIdle { toolbarBack() }
        assertTrue("Outgoing toolbar cannot exit Home on a second tap", outer.currentDestination?.route == "home")
    }

    @Test
    fun backFromTheGlobalPageReturnsHome() {
        showHost(initialTab = 0, focus = "")

        testActivity.onBackPressedDispatcher.onBackPressed()
        verify(exactly = 1) { navController.popBackStack(NavRoutes.home.name, false) }
    }

    @Test
    fun backToHomeEnabledStillReturnsFromDeepLinkedTabToGlobalFirst() {
        showHost(initialTab = 0, focus = "pip")
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!backNavigation.handleBack()) {
                    navController.popBackStack(NavRoutes.home.name, false)
                }
            }
        }
        testActivity.onBackPressedDispatcher.addCallback(callback)
        try {
            testActivity.onBackPressedDispatcher.onBackPressed()
            pumpUntil("deep link returns to global", 30_000) {
                globalPageIsComposed() && !tabIsComposed()
            }
            verify(exactly = 0) { navController.popBackStack(NavRoutes.home.name, false) }
            testActivity.onBackPressedDispatcher.onBackPressed()
            verify(exactly = 1) { navController.popBackStack(NavRoutes.home.name, false) }
        } finally {
            callback.remove()
        }
    }

    @Test
    fun toolbarBackReturnsToGlobalBeforeLeavingSettings() {
        showHost(initialTab = 0, focus = "pip")
        composeRule.runOnIdle { assertTrue(backNavigation.handleBack()) }
        pumpUntil("toolbar returns to global", 30_000) {
            globalPageIsComposed() && !tabIsComposed()
        }
        verify(exactly = 0) { navController.popBackStack(NavRoutes.home.name, false) }
        composeRule.runOnIdle { assertTrue(backNavigation.handleBack()) }
        verify(exactly = 1) { navController.popBackStack(NavRoutes.home.name, false) }
    }

    @Test
    fun tappingTheUpdateItemNavigatesToTheUpdaterRoute() {
        showHost(initialTab = 0, focus = "")

        composeRule.onNodeWithTag("settings_global_update").performClick()
        composeRule.mainClock.advanceTimeBy(250)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))

        verify { navController.navigate(NavRoutes.updater.name) }
    }

    @Test
    fun theCardSearchResultOpensItsCategoryAndScrollsToTheCard() {
        showHost(initialTab = 0, focus = "")
        composeRule.onNodeWithTag("settings_global_search")
            .performTextInput(context.getString(R.string.queue_management))
        val result = composeRule.onNodeWithTag("settings_global_result_0_${R.string.queue_management}")
        pumpUntil("the card result is composed", 30_000) {
            runCatching { result.fetchSemanticsNode() }.isSuccess
        }
        result.performSemanticsAction(SemanticsActions.OnClick) { it() }
        pumpUntil("the Playback category is composed", 30_000) {
            categoryIsComposed() && globalPageIsGone()
        }
        pumpUntil("the searched card is visible", 30_000) {
            runCatching {
                composeRule.onNodeWithTag("settings.target.card.0.queue_management", useUnmergedTree = true)
                    .assertIsDisplayed()
            }.isSuccess
        }
        // New category navigation must remain inside Settings, not the outer app controller.
        verify(exactly = 0) { navController.navigate(any<String>()) }
    }
}
