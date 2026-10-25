package app.n_zik.android.legacyoffmain.ui.components.navigation.header

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.components.navigation.header.ActionBar
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.enablePictureInPictureKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.utils.DataStoreUtils
import androidx.navigation.NavController
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
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
 * Assembly test for the customizable burger menu (spec-customizable-burger-menu, goal C):
 * the real legacy [ActionBar] renders the stored order as menu rows - the default seed
 * shows every item in the default order with the separator between PIP and Settings, a
 * stored per-item toggle off hides its row without shifting the rest, and a runtime
 * condition always beats a stored toggle (full offline mode hides Listen Together even
 * when its stored toggle is on; the rewind master hides Rewind even when its stored
 * toggle is on).
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine on the JUnit 5
 * platform (`createComposeRule()` only works with JUnit 4), with the plain [Application] so
 * the app's heavy init is skipped. The menu is a themed [androidx.compose.material3.DropdownMenu]
 * (non-lazy: every row composes) and overflows the 640dp window, so the row assertions use
 * `assertExists` (a row is not "displayed" when the popup overflows the viewport). The
 * profile face resolves on a background dispatcher and the overflowing labels run an
 * infinite marquee, so the Compose clock is frozen and pumped (same template as
 * `RewindSectionCollageCardTest.composeWaitUntil`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class BurgerMenuAssemblyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app: Context = ApplicationProvider.getApplicationContext()
    private val dataStore: SharedPreferences get() =
        app.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before
    fun setUp() {
        // The PIP row needs the process-wide isPipSupported val (cached on first
        // access, so the feature must be declared before the composition reads it)
        // plus the per-profile toggle, which defaults off.
        Shadows.shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        app.preferences.edit().putBoolean(enablePictureInPictureKey, true).commit()
        // MainApplication is final, so Dependencies.application is a mock wired to this
        // Robolectric app (the header resolves app-wide state through the service locator).
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
    }

    @After
    fun tearDown() {
        unmockkAll()
        dataStore.edit().also { edit ->
            edit.remove(DataStoreUtils.KEY_BURGER_MENU_ORDER)
            edit.remove(DataStoreUtils.KEY_REWIND_ENABLED)
            DataStoreUtils.DEFAULT_BURGER_MENU_ORDER.forEach { id ->
                edit.remove(DataStoreUtils.burgerMenuToggleKey(id))
            }
        }.commit()
        app.preferences.edit()
            .remove(enablePictureInPictureKey)
            .remove(fullOfflineModeKey)
            .commit()
    }

    /**
     * Polls [condition] while advancing BOTH the frozen Compose main clock and the
     * Robolectric main looper - the face resolves on NzikDispatchers.DATA, so clock
     * advances alone are not guaranteed to pump the resulting recomposition (extends
     * the `OfflineContentTest.composeWaitUntil` rationale to background-emitted state).
     */
    private fun composeWaitUntil(label: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            composeRule.waitForIdle()
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("[$label] not met within ${timeoutMs}ms", condition())
    }

    /**
     * Composes the real header (frozen clock: the overflowing labels marquee forever)
     * and opens the menu by clicking the profile face - the base profile's
     * deterministic initials ("NF" for the base profile name) replace the burger icon.
     */
    private fun openMenu() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                ActionBar(navController = mockk<NavController>(relaxed = true))
            }
        }
        composeWaitUntil("the profile face rendered its initials", 10_000) {
            composeRule.onAllNodesWithText("NF").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("NF").performClick()
        composeWaitUntil("the burger menu opened", 5_000) {
            composeRule.onAllNodesWithText(app.getString(R.string.history)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The default-seed rows in render order (label resources). */
    private val defaultVisibleLabels = listOf(
        R.string.history,
        R.string.statistics,
        R.string.rewind,
        R.string.enable_offline_mode,
        R.string.listen_together,
        R.string.bridge_server,
        R.string.profiles,
        R.string.menu_go_to_picture_in_picture,
        R.string.settings,
        R.string.modules_title,
        R.string.enable_log_debug,
        R.string.maintenance,
        R.string.rescue_center,
    )

    @Test
    fun theDefaultSeedRendersEveryVisibleRowInOrderWithTheSeparatorGap() {
        openMenu()

        val labels = defaultVisibleLabels.map { app.getString(it) }
        labels.forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }

        // The merged row nodes carry the full 48dp row bounds: their y-tops must follow
        // the stored default order.
        val tops = labels.map { label ->
            composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        }
        for (i in 1 until tops.size) {
            assertTrue(
                "row ${labels[i - 1]} (top ${tops[i - 1]}) must be above row ${labels[i]} (top ${tops[i]})",
                tops[i - 1] < tops[i]
            )
        }

        // The default order carries one separator between PIP and Settings: a visible
        // gap where the rows are otherwise adjacent.
        val pipBottom = composeRule
            .onNodeWithText(app.getString(R.string.menu_go_to_picture_in_picture))
            .fetchSemanticsNode()
            .boundsInRoot
            .bottom
        val settingsTop = composeRule
            .onNodeWithText(app.getString(R.string.settings))
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(
            "the default separator must sit between the PIP and Settings rows (pip bottom $pipBottom, settings top $settingsTop)",
            settingsTop > pipBottom
        )
    }

    @Test
    fun aStoredToggleOffHidesTheRowWithoutShiftingTheRest() {
        dataStore.edit()
            .putBoolean(DataStoreUtils.burgerMenuToggleKey("rewind"), false)
            .commit()
        openMenu()

        composeRule.onNodeWithText(app.getString(R.string.rewind)).assertDoesNotExist()

        // The hidden row's neighbours keep their order; the row after the gap still
        // renders right where the hidden one used to sit in the stored order.
        val historyTop = composeRule
            .onNodeWithText(app.getString(R.string.history))
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val statisticsTop = composeRule
            .onNodeWithText(app.getString(R.string.statistics))
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val offlineTop = composeRule
            .onNodeWithText(app.getString(R.string.enable_offline_mode))
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue("History must stay above Statistics", historyTop < statisticsTop)
        assertTrue("Offline mode must stay directly after the hidden Rewind row", statisticsTop < offlineTop)
        defaultVisibleLabels.map { app.getString(it) }
            .filter { it != app.getString(R.string.rewind) }
            .forEach { composeRule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun theRuntimeStateOverridesTheStoredToggles() {
        // Stored: the listen_together toggle is explicitly on (a stored toggle off would
        // hide the row too, which the other test covers).
        dataStore.edit()
            .putBoolean(DataStoreUtils.burgerMenuToggleKey("listen_together"), true)
            .commit()
        // Runtime: full offline mode ON (the per-profile live flag) and the rewind
        // master off (the global store flag the header reads live).
        app.preferences.edit().putBoolean(fullOfflineModeKey, true).commit()
        DataStoreUtils.saveBoolean(app, DataStoreUtils.KEY_REWIND_ENABLED, false)
        openMenu()

        // Offline mode wins over the stored toggle: the entry is closed while the
        // offline row shows its live "Disable" label.
        composeRule.onNodeWithText(app.getString(R.string.listen_together)).assertDoesNotExist()
        composeRule.onNodeWithText(app.getString(R.string.disable_offline_mode)).assertExists()
        // The rewind master hides the row even though its stored toggle is on.
        composeRule.onNodeWithText(app.getString(R.string.rewind)).assertDoesNotExist()
        // The rest of the menu is untouched.
        composeRule.onNodeWithText(app.getString(R.string.history)).assertExists()
        composeRule.onNodeWithText(app.getString(R.string.settings)).assertExists()
    }
}
