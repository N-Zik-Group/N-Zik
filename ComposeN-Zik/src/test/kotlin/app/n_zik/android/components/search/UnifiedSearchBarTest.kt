package app.n_zik.android.components.search

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.tab.Search
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The unified app-wide search bar: the text field and every bubble (the always-present clear
 * bubble plus the host action bubbles) are composed with their test tags, and the clear bubble
 * closes the search (calls `onClear` and reports the loss of focus).
 *
 * Focus is driven through the bar's own [FocusRequester] instead of a synthesized click: the
 * bubble slots are offset off-screen while the bar is unfocused, so a click on a bubble is only
 * reachable after the focus choreography has run. `mainClock.autoAdvance = false` keeps the
 * choreography deterministic: the clock is advanced explicitly past the animation durations.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on the
 * JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4), with the plain
 * [Application] so the app's heavy init (DI, Room, player) is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class UnifiedSearchBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private fun content(block: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                block()
            }
        }
    }

    @Test
    fun `keyboard search submits input and clears focus without clearing results`() {
        var submitted = false
        var focused = false
        val requester = FocusRequester()
        content {
            LaunchedEffect(Unit) { requester.requestFocus() }
            UnifiedSearchBar(
                input = TextFieldValue("query"),
                onInputChanged = {},
                placeholderRes = R.string.search,
                onClear = {},
                onSearch = { submitted = true },
                onFocused = { focused = it },
                focusRequester = requester,
                textFieldTestTag = "ime_search",
            )
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        assertTrue(focused)
        composeRule.onNodeWithTag("ime_search").performImeAction()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        assertTrue("Search callback receives submission", submitted)
        assertTrue("IME action removes focus", !focused)
        composeRule.onNodeWithTag("ime_search").assertTextEquals("query")
    }

    @Test
    fun `toolbar opening focuses the newly mounted field even with another hidden search`() {
        lateinit var search: Search
        content {
            search = Search()
            val otherPageSearch = Search()
            Column {
                search.SearchBar(this)
                otherPageSearch.SearchBar(this)
            }
        }
        composeRule.runOnIdle { search.onShortClick() }
        repeat(35) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.onNode(hasSetTextAction()).performImeAction()
        repeat(35) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        assertTrue("An empty submitted search closes", !search.isVisible)
    }

    @Test
    fun `always visible settings search does not grab focus when entering the page`() {
        content {
            val search = Search(initialVisible = true)
            Column { search.SearchBar(this) }
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun `search wrapper does not clip bubbles travelling below the bar`() {
        content {
            val search = Search(initialVisible = true)
            LaunchedEffect(Unit) { search.isFocused = true }
            Column { search.SearchBar(this) }
        }

        composeRule.mainClock.advanceTimeBy(280)
        composeRule.waitForIdle()
        val travelling = composeRule.onNodeWithTag("unified_search_bubble_${R.drawable.close}")
            .fetchSemanticsNode().boundsInRoot
        val bar = composeRule.onNodeWithTag("unified_search_bar").fetchSemanticsNode().boundsInRoot
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        val settled = composeRule.onNodeWithTag("unified_search_bubble_${R.drawable.close}")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Bubble travels below the bar", travelling.bottom > bar.bottom)
        assertTrue("Full bubble remains unclipped", travelling.height >= settled.height - 1f)
    }

    @Test
    fun `the bar renders the text field, the clear bubble and the host action bubbles`() {
        content {
            UnifiedSearchBar(
                input = TextFieldValue(""),
                onInputChanged = {},
                placeholderRes = R.string.search,
                actionBubbles = listOf(
                    SearchBarBubble(
                        iconRes = R.drawable.link,
                        contentDescriptionRes = R.string.search,
                        onClick = {},
                    ),
                ),
                onClear = {},
            )
        }

        composeRule.onNodeWithTag("unified_search_bar").assertIsDisplayed()

        // Checked via the collection's semantics nodes (this Compose version's
        // collection has no assertExists)
        assertTrue(
            "the clear (X) bubble is composed",
            composeRule.onAllNodesWithTag("unified_search_bubble_${R.drawable.close}").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "the host action bubble is composed",
            composeRule.onAllNodesWithTag("unified_search_bubble_${R.drawable.link}").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `the clear bubble closes the search and reports unfocus`() {
        var cleared = false
        var focused = false
        val focusRequester = FocusRequester()

        content {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            UnifiedSearchBar(
                input = TextFieldValue(""),
                onInputChanged = {},
                placeholderRes = R.string.search,
                onClear = { cleared = true },
                onFocused = { focused = it },
                focusRequester = focusRequester,
            )
        }

        // Let the focus choreography settle (bar resize + bubble trip)
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        assertTrue("focus is reported on the bar", focused)

        composeRule.onNodeWithTag("unified_search_bubble_${R.drawable.close}").performClick()
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.waitForIdle()
        assertTrue("onClear was called by the X bubble", cleared)
        assertTrue("the loss of focus is reported after the clear", !focused)
    }
}
