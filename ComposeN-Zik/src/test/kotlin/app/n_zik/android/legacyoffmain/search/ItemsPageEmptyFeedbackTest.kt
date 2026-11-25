package app.n_zik.android.legacyoffmain.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density

import android.app.Application
import app.n_zik.android.R
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.n_zik.android.compose.persist.PersistMap
import app.n_zik.android.compose.persist.PersistMapOwner
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.screens.player.PlayerSheetState
import app.n_zik.android.ui.screens.searchresult.ItemsGridPage
import app.n_zik.android.ui.screens.searchresult.ItemsPage
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerSheetState
import io.mockk.every
import io.mockk.mockk
import app.n_zik.innertube.Innertube
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import kotlinx.coroutines.CompletableDeferred
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
        contentPadding: PaddingValues = PaddingValues(0.dp),
        header: @Composable () -> Unit = {},
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
                    headerContent = { header() },
                    contentPadding = contentPadding,
                    itemContent = {},
                    itemPlaceholderContent = { Box(Modifier.height(20.dp).testTag("pending-page")) },
                    emptyItemsText = "Collection is empty",
                    itemsPageProvider = provider,
                    thumbnailSizeDp = 96.dp,
                    showEmptyMessage = { messages += it },
                )
            } else {
                ItemsPage<Innertube.VideoItem>(
                    tag = "test-page",
                    searchQuery = query,
                    headerContent = { header() },
                    contentPadding = contentPadding,
                    itemContent = { Box(Modifier.height(20.dp)) },
                    itemPlaceholderContent = { Box(Modifier.height(20.dp).testTag("pending-page")) },
                    emptyItemsText = "Collection is empty",
                    itemsPageProvider = provider,
                    showEmptyMessage = { messages += it },
                )
            }
        }
    }

    private fun enableNetwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = checkNotNull(context.getSystemService(ConnectivityManager::class.java))
        val capabilities = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        Shadows.shadowOf(manager).setNetworkCapabilities(checkNotNull(manager.activeNetwork), capabilities)
    }

    @Test
    fun initialRequestShowsPlaceholdersUntilAnEmptyResultInGrid() {
        enableNetwork()
        page.value = null
        val result = CompletableDeferred<Result<Innertube.ItemsPage<Innertube.VideoItem>?>>()
        val provider: suspend (String?) -> Result<Innertube.ItemsPage<Innertube.VideoItem>?> = { result.await() }
        composeRule.setContent { content(grid = true, query = null, provider = provider) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("pending-page").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Collection is empty").assertDoesNotExist()
        result.complete(Result.success(Innertube.ItemsPage(emptyList(), null)))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Collection is empty").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Collection is empty").assertIsDisplayed()
    }

    @Test
    fun failedContinuationKeepsItsPageAndWaitsForAnExplicitRetry() {
        enableNetwork()
        val item = mockk<Innertube.VideoItem> { every { key } returns "saved-video" }
        page.value = Innertube.ItemsPage(listOf(item), "next-page")
        val calls = AtomicInteger()
        val provider: suspend (String?) -> Result<Innertube.ItemsPage<Innertube.VideoItem>?> = { token ->
            check(token == "next-page")
            if (calls.incrementAndGet() == 1) Result.failure(IllegalStateException("Network unavailable"))
            else Result.success(null)
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val retry = context.getString(R.string.common_action_retry)
        composeRule.setContent { content(grid = false, provider = provider) }
        composeRule.waitUntil("Continuation requested", timeoutMillis = 5_000) { calls.get() == 1 }
        composeRule.waitUntil("Localized retry button", timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(retry).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            assertEquals(1, calls.get())
            assertEquals(listOf(item), page.value?.items)
        }
        composeRule.onAllNodesWithTag("pending-page").assertCountEquals(0)
        composeRule.onNodeWithText(retry).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { page.value?.continuation == null }
        composeRule.runOnIdle {
            assertEquals(2, calls.get())
            assertEquals(listOf(item), page.value?.items)
        }
    }

    @Test
    fun repeatedContinuationTokenStopsAutomaticPagingInGrid() {
        enableNetwork()
        page.value = Innertube.ItemsPage(emptyList(), "repeated")
        val calls = AtomicInteger()
        val provider: suspend (String?) -> Result<Innertube.ItemsPage<Innertube.VideoItem>?> = { token ->
            calls.incrementAndGet()
            Result.success(Innertube.ItemsPage(emptyList(), token))
        }
        composeRule.setContent { content(grid = true, provider = provider) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle {
            assertEquals(1, calls.get())
            assertEquals(0, messages.size)
        }
        composeRule.onAllNodesWithTag("pending-page").assertCountEquals(0)
    }

    @Test
    fun loadingAndTerminalPagesKeepTheSameHeaderClearanceInListAndGrid() {
        val grid = mutableStateOf(false)
        page.value = null
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Box(Modifier.size(300.dp, 400.dp).testTag("viewport")) {
                    content(
                        grid = grid.value,
                        contentPadding = PaddingValues(start = 40.dp, top = 100.dp, end = 20.dp, bottom = 120.dp),
                        header = { Box(Modifier.fillMaxWidth().height(24.dp).testTag("page-header")) },
                    )
                }
            }
        }
        fun assertHeaderClearance() {
            val viewport = composeRule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val header = composeRule.onNodeWithTag("page-header").fetchSemanticsNode().boundsInRoot
            assertEquals(viewport.top + 100f, header.top, 0f)
            assertEquals(viewport.left + 40f, header.left, 0f)
            assertEquals(viewport.right - 20f, header.right, 0f)
        }
        assertHeaderClearance()
        composeRule.runOnIdle { page.value = Innertube.ItemsPage(emptyList(), null) }
        assertHeaderClearance()
        composeRule.runOnIdle { grid.value = true; page.value = null }
        assertHeaderClearance()
        composeRule.runOnIdle { page.value = Innertube.ItemsPage(emptyList(), null) }
        assertHeaderClearance()
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
