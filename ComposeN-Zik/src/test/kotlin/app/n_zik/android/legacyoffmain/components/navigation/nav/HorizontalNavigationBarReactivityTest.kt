package app.n_zik.android.legacyoffmain.components.navigation.nav

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.ui.components.navigation.nav.HorizontalNavigationBar
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.putEnum
import app.n_zik.android.R
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression (spec-full-offline-mode wave 2): [HorizontalNavigationBar] used to render
 * from a `remember { buttonList }` getter that froze the button list at the first
 * generation — when the tab set changed (quickpicks leaving on full-offline enable,
 * re-entering on disable), the bar kept drawing the stale icons. The bar must re-read
 * the live list on every recomposition, no re-navigation included.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = NavReactTestApplication::class)
class HorizontalNavigationBarReactivityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private val navController = mockk<NavController>(relaxed = true)

    // Mirrors Skeleton: a fresh bar instance per recomposition, filled with the
    // current tab list — no remembered bar, no re-navigation.
    @Composable
    private fun navBar(tabs: List<String>) {
        val bar = HorizontalNavigationBar(tabIndex = 0, onTabChanged = {}, navController = navController)
        bar.add { item ->
            // item() is @Composable: iterate with a plain for-loop, not a lambda
            for (index in tabs.indices) item(index, tabs[index], R.drawable.library)
        }
        bar.Draw()
    }

    /**
     * Advances the frozen frame clock in two rounds interleaved with idle waits: a state
     * write from the test body is recomposed during the idle pass, not during the clock
     * advance — the idle processes the recomposition, the next advance runs the frames.
     */
    private fun settle() {
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()
    }

    @Test
    fun `tab list changes update the rendered buttons without re-navigation`() {
        // The default NavigationBarType is IconOnly (no text rendered) — the text-based
        // assertions below need labels. ApplicationProvider resolves to the same
        // application context lineage as LocalContext.current.preferences in the composable.
        ApplicationProvider.getApplicationContext<Application>()
            .preferences
            .edit()
            .putEnum(navigationBarTypeKey, NavigationBarType.IconAndText)
            .commit()
        composeRule.mainClock.autoAdvance = false
        var tabs by mutableStateOf(listOf("Songs", "Artists"))
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.fillMaxSize()) { navBar(tabs) }
            }
        }
        settle()
        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onNodeWithText("Artists").assertIsDisplayed()

        // Mode on: the list shrinks — the replaced tab must leave the bar
        tabs = listOf("Songs", "Albums")
        settle()
        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onNodeWithText("Albums").assertIsDisplayed()
        composeRule.onNodeWithText("Artists").assertIsNotDisplayed()

        // Mode off: the list grows — the re-entering tab must show up
        tabs = listOf("Songs", "Artists", "QuickPicks")
        settle()
        composeRule.onNodeWithText("Artists").assertIsDisplayed()
        composeRule.onNodeWithText("QuickPicks").assertIsDisplayed()
    }
}

/** Test-only application: the app's MainApplication.onCreate() is not available on the Robolectric JVM. */
class NavReactTestApplication : Application()
