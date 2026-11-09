package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import it.fast4x.innertube.Innertube

/** Verifies account-search visibility, disabled interactions and persisted login choices. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class YtSearchAccountToggleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val title = context.getString(R.string.settings_youtube_authenticated_search_label)
    private val connectFirst = context.getString(R.string.settings_youtube_connection_required_description)

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

    private fun onRowSwitch(): SemanticsNodeInteraction = composeRule.onNode(
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
        useUnmergedTree = true,
    )

    @Test
    fun `a logged-out row shows the connect-first text and never writes the preference`() {
        Innertube.useLoginForSearch = true
        showToggle(isYouTubeLoginEnabled = true, isLoggedIn = false, searchQuery = "")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(connectFirst).assertIsDisplayed()

        val row = composeRule.onNodeWithText(title)
        val switch = onRowSwitch()
        row.assertIsNotEnabled()
        switch.assertIsNotEnabled().assertIsOff()

        // Exercise both semantic click paths; neither may change a logged-out account.
        row.performClick()
        composeRule.waitForIdle()
        switch.performClick()
        composeRule.waitForIdle()
        switch.assertIsOff()

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
        // applied it, then the user (re)connects, nothing resets the toggle.
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
        // description, the title branch of the filter.
        showToggle(isYouTubeLoginEnabled = true, isLoggedIn = true, searchQuery = "use")
        composeRule.waitForIdle()

        composeRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun `the search filter trims the query before matching`() {
        // "suggestions" occurs only at the end of the description, a stray trailing space
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
