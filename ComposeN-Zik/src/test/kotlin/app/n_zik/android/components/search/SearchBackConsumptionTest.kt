package app.n_zik.android.components.search

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.tab.Search
import app.n_zik.android.core.navigation.rememberAppBackAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Dispatcher and toolbar integration for expandable and permanent search sessions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SearchBackConsumptionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val activity: ComponentActivity
        get() = (composeRule as AndroidComposeTestRule<*, *>).activity
    private val dispatcher get() = activity.onBackPressedDispatcher
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
            CompositionLocalProvider(LocalAppearance provides appearance) { block() }
        }
    }

    private fun settle() {
        repeat(40) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `expandable blank session wins over later parent and hidden sibling`() {
        lateinit var search: Search
        var parentBacks = 0
        content {
            search = Search(initialVisible = false)
            val hidden = Search()
            Column {
                search.SearchBar(this)
                hidden.SearchBar(this)
            }
            BackHandler { parentBacks++ }
        }
        composeRule.runOnIdle { search.onShortClick() }
        settle()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        settle()
        assertFalse(search.isVisible)
        assertFalse(search.isFocused)
        assertEquals(0, parentBacks)
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(1, parentBacks)
    }

    @Test
    fun `expandable predictive cancellation preserves pending focus and commit closes only search`() {
        lateinit var search: Search
        var parentBacks = 0
        content {
            search = Search()
            Column { search.SearchBar(this) }
            BackHandler { parentBacks++ }
        }
        composeRule.runOnIdle { search.onShortClick() }
        repeat(3) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackCancelled()
        }
        assertTrue(search.isVisible)
        assertTrue(search.isFocused)
        assertEquals(0, parentBacks)
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        settle()
        assertFalse(search.isVisible)
        assertFalse(search.isFocused)
        assertEquals(0, parentBacks)
    }

    @Test
    fun `submitted expandable search closes while preserving query`() {
        lateinit var search: Search
        var parentBacks = 0
        content {
            search = Search()
            Column { search.SearchBar(this) }
            BackHandler { parentBacks++ }
        }
        composeRule.runOnIdle {
            search.input = TextFieldValue("retained query", TextRange(3, 7))
            search.onShortClick()
        }
        settle()
        composeRule.onNode(hasSetTextAction()).performImeAction()
        settle()
        assertTrue(search.isVisible)
        assertFalse(search.isFocused)
        composeRule.runOnIdle {
            val selectionBeforeBack = search.input.selection
            dispatcher.onBackPressed()
            assertEquals(selectionBeforeBack, search.input.selection)
        }
        settle()
        assertFalse(search.isVisible)
        assertEquals("retained query", search.inputValue)
        assertEquals(0, parentBacks)
        composeRule.runOnIdle { search.onShortClick() }
        settle()
        composeRule.onNode(hasSetTextAction()).assertIsFocused().assertTextEquals("retained query")
    }

    @Test
    fun `untouched permanent pill does not consume and focused pill preserves visibility and text`() {
        lateinit var search: Search
        var parentBacks = 0
        content {
            search = Search(initialVisible = true)
            Column { search.SearchBar(this) }
            BackHandler { parentBacks++ }
        }
        settle()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(1, parentBacks)
        composeRule.runOnIdle {
            search.input = TextFieldValue("settings")
            search.isFocused = true
        }
        settle()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        settle()
        assertTrue(search.isVisible)
        assertFalse(search.isFocused)
        assertEquals("settings", search.inputValue)
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
        assertEquals(1, parentBacks)
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(2, parentBacks)
    }

    @Test
    fun `focused permanent empty submit keeps pill and releases back to parent`() {
        lateinit var search: Search
        var parentBacks = 0
        content {
            search = Search(initialVisible = true)
            Column { search.SearchBar(this) }
            BackHandler { parentBacks++ }
        }
        composeRule.runOnIdle { search.isFocused = true }
        settle()
        composeRule.onNode(hasSetTextAction()).assertIsFocused().performImeAction()
        settle()
        assertTrue(search.isVisible)
        assertFalse(search.isFocused)
        composeRule.onNode(hasSetTextAction()).assertIsNotFocused()
        assertEquals("", search.inputValue)
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(1, parentBacks)
    }

    @Test
    fun `same frame dismiss and reopen rearms the active callback`() {
        lateinit var search: Search
        var parentBacks = 0
        content {
            search = Search()
            Column { search.SearchBar(this) }
            BackHandler { parentBacks++ }
        }
        composeRule.runOnIdle { search.onShortClick() }
        settle()
        composeRule.runOnIdle {
            dispatcher.onBackPressed()
            search.onShortClick()
        }
        settle()
        assertTrue(search.isVisible)
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        settle()
        assertFalse(search.isVisible)
        assertEquals(0, parentBacks)
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(1, parentBacks)
    }

    @Test
    fun `controlled direct expandable consumes unfocused sessions adopted by playlist and podcast`() {
        val searching = mutableStateOf(false)
        var parentBacks = 0
        content {
            if (searching.value) {
                UnifiedSearchBar(
                    input = TextFieldValue("retained filter"),
                    onInputChanged = {},
                    placeholderRes = R.string.search,
                    onClear = {},
                    backSessionActive = searching.value,
                    onBackDismiss = { searching.value = false },
                    textFieldTestTag = "controlled",
                )
            }
            BackHandler { parentBacks++ }
        }
        composeRule.runOnIdle { searching.value = true }
        repeat(3) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("controlled").assertIsNotFocused().assertTextEquals("retained filter")
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        settle()
        assertFalse(searching.value)
        assertEquals(0, parentBacks)
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(1, parentBacks)

        listOf("playlist/PlaylistSongList.kt", "podcast/Podcast.kt").forEach { relativePath ->
            val sourcePath = "src/androidMain/kotlin/app/it/fast4x/rimusic/ui/screens/$relativePath"
            val source = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
                .flatMap { directory -> sequenceOf(File(directory, sourcePath), File(directory, "ComposeN-Zik/$sourcePath")) }
                .first { it.isFile }.readText()
            val searchCall = source.substringAfter("UnifiedSearchBar(").take(1500)
            assertTrue(relativePath, Regex("backSessionActive\\s*=\\s*searching").containsMatchIn(searchCall))
            assertTrue(relativePath, Regex("onBackDismiss\\s*=\\s*\\{\\s*searching\\s*=\\s*false\\s*}").containsMatchIn(searchCall))
        }
    }

    @Test
    fun `direct bar consumes predictive commit and actual toolbar after cancellation`() {
        lateinit var nav: NavHostController
        lateinit var toolbarBack: () -> Unit
        content {
            nav = rememberNavController()
            toolbarBack = rememberAppBackAction(nav)
            NavHost(navController = nav, startDestination = "home") {
                composable("home") { Box(Modifier.testTag("previous_destination")) }
                composable("settings") {
                    UnifiedSearchBar(
                        input = TextFieldValue("results"),
                        onInputChanged = {},
                        placeholderRes = R.string.search,
                        onClear = {},
                        textFieldTestTag = "direct",
                    )
                }
            }
        }
        composeRule.runOnIdle { nav.navigate("settings") }
        settle()
        composeRule.onNodeWithTag("unified_search_bar").performClick()
        settle()
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        settle()
        assertEquals("settings", nav.currentDestination?.route)
        composeRule.onNodeWithTag("previous_destination").assertDoesNotExist()
        composeRule.onNodeWithTag("direct").assertIsFocused()
        composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        settle()
        composeRule.onNodeWithTag("direct").assertIsFocused()
        composeRule.runOnIdle { toolbarBack() }
        settle()
        assertEquals("settings", nav.currentDestination?.route)
        composeRule.onNodeWithTag("direct").assertIsNotFocused().assertTextEquals("results")
        composeRule.onNodeWithTag("unified_search_bar").performClick()
        settle()
        composeRule.runOnIdle {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        settle()
        assertEquals("settings", nav.currentDestination?.route)
        composeRule.onNodeWithTag("direct").assertIsNotFocused().assertTextEquals("results")
        composeRule.runOnIdle { toolbarBack() }
        settle()
        assertEquals("home", nav.currentDestination?.route)
    }

    @Test
    fun `outgoing owner stops intercepting immediately when no longer resumed`() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        var parentBacks = 0
        lateinit var search: Search
        composeRule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        content {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                search = Search()
                Column { search.SearchBar(this) }
            }
            BackHandler { parentBacks++ }
        }
        composeRule.runOnIdle { search.onShortClick() }
        settle()
        composeRule.runOnIdle {
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            dispatcher.onBackPressed()
        }
        assertEquals(1, parentBacks)
        assertTrue(search.isVisible)
        assertTrue(search.isFocused)
        composeRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        settle()
        assertFalse(search.isVisible)
        assertEquals(1, parentBacks)

        composeRule.runOnIdle { search.onShortClick() }
        settle()
        composeRule.runOnIdle {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            dispatcher.onBackPressed()
        }
        assertEquals(2, parentBacks)
        assertTrue(search.isVisible)
        composeRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        settle()
        assertFalse(search.isVisible)
        assertEquals(2, parentBacks)
    }
}
