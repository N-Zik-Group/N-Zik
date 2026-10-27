package app.n_zik.android.legacyoffmain.search

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.it.fast4x.compose.persist.PersistMap
import app.it.fast4x.compose.persist.PersistMapOwner
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.screens.searchresult.ItemsGridPage
import app.it.fast4x.rimusic.ui.screens.searchresult.ItemsPage
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerSheetState
import io.mockk.every
import io.mockk.mockk
import it.fast4x.innertube.Innertube
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalAnimationApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class ItemsPageEmptyFeedbackTest {
    @get:Rule val composeRule = createComposeRule()

    private val messages = mutableListOf<String>()
    private val page = mutableStateOf<Innertube.ItemsPage<Innertube.VideoItem>?>(
        Innertube.ItemsPage(emptyList(), null)
    )
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )
    private val sheetState = mockk<PlayerSheetState> { every { isVisible } returns false }

    private class PageContext(base: Context, page: MutableState<Innertube.ItemsPage<Innertube.VideoItem>?>) :
        ContextWrapper(base), PersistMapOwner {
        override val persistMap: PersistMap = hashMapOf("test-page" to page)
    }

    @Composable
    private fun content(
        grid: Boolean,
        query: String? = "missing",
        provider: (suspend (String?) -> Result<Innertube.ItemsPage<Innertube.VideoItem>?>?)? = null,
    ) {
        val base = LocalContext.current
        val context = remember { PageContext(base, page) }
        CompositionLocalProvider(
            LocalContext provides context,
            LocalAppearance provides appearance,
            LocalPlayerSheetState provides sheetState,
            LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
        ) {
            if (grid) {
                ItemsGridPage<Innertube.VideoItem>(
                    tag = "test-page",
                    searchQuery = query,
                    headerContent = {},
                    itemContent = {},
                    itemPlaceholderContent = {},
                    emptyItemsText = "Collection is empty",
                    itemsPageProvider = provider,
                    thumbnailSizeDp = 96.dp,
                    showEmptyMessage = { messages += it },
                )
            } else {
                ItemsPage<Innertube.VideoItem>(
                    tag = "test-page",
                    searchQuery = query,
                    headerContent = {},
                    itemContent = {},
                    itemPlaceholderContent = {},
                    emptyItemsText = "Collection is empty",
                    itemsPageProvider = provider,
                    showEmptyMessage = { messages += it },
                )
            }
        }
    }

    private fun advance(milliseconds: Long) {
        composeRule.mainClock.advanceTimeBy(milliseconds)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds))
    }

    @Test
    fun terminalListSearchDebouncesOnceAndKeepsTheGenericSentenceOutOfTheLayout() {
        val incidental = mutableStateOf(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { incidental.value; content(grid = false) }
        advance(200)
        composeRule.runOnIdle { assertEquals(0, messages.size) }
        composeRule.onNodeWithTag("search_no_results_text").assertDoesNotExist()
        advance(600)
        composeRule.runOnIdle { assertEquals(1, messages.size); incidental.value++ }
        advance(700)
        composeRule.runOnIdle { assertEquals(1, messages.size) }
    }

    @Test
    fun terminalGridSearchUsesTheSameFeedbackWithoutAnInlineSentence() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { content(grid = true) }
        advance(200)
        composeRule.runOnIdle { assertEquals(0, messages.size) }
        composeRule.onNodeWithTag("search_no_results_text").assertDoesNotExist()
        advance(600)
        composeRule.runOnIdle { assertEquals(1, messages.size) }
    }

    @Test
    fun emptyContinuationPageWaitsUntilTheSuccessfulTerminalPage() {
        page.value = Innertube.ItemsPage(emptyList(), "next-page")
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { content(grid = false) }
        advance(800)
        composeRule.runOnIdle {
            assertEquals(0, messages.size)
            page.value = Innertube.ItemsPage(emptyList(), null)
        }
        advance(200)
        composeRule.runOnIdle { assertEquals(0, messages.size) }
        advance(600)
        composeRule.runOnIdle { assertEquals(1, messages.size) }
    }

    @Test
    fun nullQueryPreservesTheInlineCollectionMessageInBothLayouts() {
        val grid = mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { content(grid.value, query = null) }
        advance(800)
        composeRule.onNodeWithText("Collection is empty").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, messages.size); grid.value = true }
        advance(800)
        composeRule.onNodeWithText("Collection is empty").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, messages.size) }
    }

    @Test
    fun initialNetworkFailureCannotBecomeAnEmptySearchToast() {
        page.value = null
        val calls = AtomicInteger()
        val provider: suspend (String?) -> Result<Innertube.ItemsPage<Innertube.VideoItem>?>? = {
            calls.incrementAndGet()
            Result.failure(IllegalStateException("Network unavailable"))
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { content(grid = false, provider = provider) }
        composeRule.waitUntil(timeoutMillis = 5_000) { calls.get() > 0 }
        advance(800)
        composeRule.runOnIdle { assertEquals(0, messages.size) }
        composeRule.onNodeWithTag("search_no_results_text").assertDoesNotExist()
    }
}
