package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [YtSearchAccountToggle], the "Use account for searches" row
 * of the Accounts tab (mirroring the "Login for Browse" row it sits under):
 *
 * - hidden entirely when the YouTube login is disabled;
 * - greyed out (connect-first text) when logged out — the row gate is disabled AND
 *   a tap on the row's inner Switch (which does not receive `enabled`, so it stays
 *   interactive) never writes the preference or flips the flag: the composable
 *   guards the write itself;
 * - toggling it off while logged in persists the guest mode AND sets the in-memory
 *   [Innertube.useLoginForSearch] flag;
 * - a persisted OFF survives a later login (the login never resets the toggle —
 *   matrix line "OFF then login");
 * - the settings search filter matches the title AND the description (and hides the
 *   row on no match).
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class YtSearchAccountToggleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val title = context.getString(R.string.use_account_for_searches)
    private val connectFirst = context.getString(R.string.youtube_connect_first)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @After
    fun tearDown() {
        Innertube.useLoginForSearch = true
    }

    private fun showToggle(
        isYouTubeLoginEnabled: Boolean,
        isLoggedIn: Boolean,
        searchQuery: String,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                YtSearchAccountToggle(
                    isYouTubeLoginEnabled = isYouTubeLoginEnabled,
                    isLoggedIn = isLoggedIn,
                    searchQuery = searchQuery,
                )
            }
        }
    }

    @Test
    fun `the row is hidden when the YouTube login is disabled`() {
        showToggle(isYouTubeLoginEnabled = false, isLoggedIn = true, searchQuery = "")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).assertDoesNotExist()
    }

    /**
     * The row's inner switch. In this Compose version a plain [androidx.compose.foundation.clickable]
     * registers no [androidx.compose.ui.semantics.Role], so select by behavior in the
     * UNMERGED semantics tree (merged, the switch's semantics fold into the row's node):
     * the switch is the only node with an [SemanticsActions.OnClick] action that does
     * NOT carry [SemanticsProperties.Disabled] — the row's own clickable gate carries
     * it while logged out (it is disabled), the switch never does (it never receives
     * `enabled`). Only valid in the logged-out composition, where the row gate is
     * disabled; logged in, both nodes match.
     */
    private fun onRowSwitch(): SemanticsNodeInteraction {
        return composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
                and SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled),
            useUnmergedTree = true,
        )
    }

    @Test
    fun `a logged-out row shows the connect-first text and never writes the preference`() {
        Innertube.useLoginForSearch = true
        showToggle(isYouTubeLoginEnabled = true, isLoggedIn = false, searchQuery = "")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(connectFirst).assertIsDisplayed()

        // The greyed-out state: the row's clickable gate is disabled — a disabled
        // clickable surfaces as SemanticsProperties.Disabled in this Compose version
        // (the old SemanticsProperties.Clickable property no longer exists; Disabled
        // is presence-based, valued Unit).
        composeRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Disabled, Unit))
            .assertExists()

        // A tap on the title stops at the disabled row gate — onCheckedChange is
        // unreachable from there even without the guard.
        composeRule.onNodeWithText(title).performClick()
        composeRule.waitForIdle()

        // The row's inner Switch does not receive `enabled` (it stays interactive on
        // the greyed-out row), so a tap on the switch is the ONLY path that can
        // reach onCheckedChange here: the guard in YtSearchAccountToggle must
        // swallow it — no preference, no flag change.
        onRowSwitch().performClick()
        composeRule.waitForIdle()

        assertFalse(
            "no preference must be written while logged out",
            context.preferences.contains(useLoginForSearchKey)
        )
        assertTrue(
            "the in-memory flag must stay ON while logged out",
            Innertube.useLoginForSearch
        )
    }

    @Test
    fun `toggling off while logged in persists guest searches and sets the flag`() {
        showToggle(isYouTubeLoginEnabled = true, isLoggedIn = true, searchQuery = "")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).performClick()
        composeRule.waitForIdle()

        assertFalse(
            "the toggle must persist OFF",
            context.preferences.getBoolean(useLoginForSearchKey, true)
        )
        assertFalse("the in-memory flag must follow the toggle", Innertube.useLoginForSearch)
    }

    @Test
    fun `a persisted OFF survives a later login`() {
        // Matrix "OFF then login": the toggle was persisted OFF, the startup restore
        // applied it, then the user (re)connects — nothing resets the toggle.
        context.preferences.edit { putBoolean(useLoginForSearchKey, false) }
        applyUseLoginForSearch(context)
        assertFalse(Innertube.useLoginForSearch)

        var loggedIn by mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                YtSearchAccountToggle(
                    isYouTubeLoginEnabled = true,
                    isLoggedIn = loggedIn,
                    searchQuery = "",
                )
            }
        }
        composeRule.waitForIdle()

        loggedIn = true
        composeRule.waitForIdle()

        assertFalse(
            "the login must not reset the persisted OFF toggle",
            Innertube.useLoginForSearch
        )
        assertFalse(
            "the login must not rewrite the persisted OFF preference",
            context.preferences.getBoolean(useLoginForSearchKey, true)
        )
    }

    @Test
    fun `the search filter matches the description as well as the title`() {
        // "suggestions" only appears in the description, not in the title.
        showToggle(isYouTubeLoginEnabled = true, isLoggedIn = true, searchQuery = "suggestions")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun `the search filter matches the title`() {
        // "use" only appears in the title ("Use account for searches"), not in the
        // description — the title branch of the filter.
        showToggle(isYouTubeLoginEnabled = true, isLoggedIn = true, searchQuery = "use")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun `the search filter trims the query before matching`() {
        // "suggestions" occurs only at the end of the description — a stray trailing space
        // in the query must not hide the row on that near-match.
        showToggle(isYouTubeLoginEnabled = true, isLoggedIn = true, searchQuery = "suggestions ")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun `the search filter hides the row on no match`() {
        showToggle(isYouTubeLoginEnabled = true, isLoggedIn = true, searchQuery = "zzz-no-match")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).assertDoesNotExist()
    }
}
