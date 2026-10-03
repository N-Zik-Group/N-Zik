package app.n_zik.android.components.ui.screens.home.quickpicks

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.BrowseEndpoint
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.requests.HomePage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Compose test for the mixed generic YTM home sections ([GenericYtmSections]) —
 * user request 2026-10-03: artist items in these sections get the album-size
 * circular thumbnail with the name centered below (album card geometry) instead
 * of the small song-size row. Video items keep the original wide 16:9 card
 * (only the "Retrospective" section uses the album format — covered by
 * [YtmRetrospectiveSectionAlbumFormatTest]).
 *
 * JUnit 4 + Robolectric with the plain [Application] (MainApplication.onCreate
 * needs AndroidKeyStore); the Room DB is the shared [DatabaseInitializer.Instance]
 * (like states are queried for the artist keys).
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class GenericYtmSectionsItemSizesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    // The album and song sizes are deliberately distinct so a card rendered at
    // the wrong (song) size is detectable.
    private val albumSize = 100.dp
    private val songSize = 54.dp

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        // runBlocking is forced by the synchronous test setup API
        // (AGENTS.md runBlocking exception); the DB is empty, no seeding needed.
        runBlocking { withContext(NzikDispatchers.DATA) { DatabaseInitializer.Instance.clearAllTables() } }
    }

    @After
    fun tearDown() {
        runBlocking { withContext(NzikDispatchers.DATA) { DatabaseInitializer.Instance.clearAllTables() } }
    }

    private fun artistItem(): Innertube.ArtistItem =
        Innertube.ArtistItem(
            info = Innertube.Info(
                name = "Test Artist",
                endpoint = NavigationEndpoint.Endpoint.Browse(browseId = "artist-1"),
            ),
            subscribersCountText = null,
            thumbnail = Thumbnail(url = "https://i.ytimg.com/vi/artist-1/hqdefault.jpg", height = null, width = null),
        )

    private fun homePage(items: List<Innertube.Item>, title: String): HomePage =
        HomePage(
            sections = listOf(
                HomePage.Section(
                    title = title,
                    label = null,
                    thumbnail = null,
                    endpoint = BrowseEndpoint(browseId = "FEmusic_test_section"),
                    items = items,
                ),
            ),
            chips = null,
        )

    private fun content(items: List<Innertube.Item>, title: String) {
        // No marquee animation frames to pump: the texts are asserted as-is.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                GenericYtmSections(
                    homePageInit = homePage(items, title),
                    displayedSectionTitles = mutableSetOf(),
                    itemInHorizontalGridWidth = 100.dp,
                    albumThumbnailSizePx = 0,
                    albumThumbnailSizeDp = albumSize,
                    songThumbnailSizePx = 0,
                    songThumbnailSizeDp = songSize,
                    playlistThumbnailSizePx = 0,
                    playlistThumbnailSizeDp = 100.dp,
                    disableScrollingText = true,
                    endPaddingValues = PaddingValues(),
                    navController = mockk<NavController>(relaxed = true),
                    onAlbumClick = {},
                    onArtistClick = {},
                    onPlaylistClick = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * The card node as seen from one of its texts: the widest ancestor up to (but
     * excluding) any full-width viewport. Must be called with a node from the
     * *unmerged* semantics tree — the merged tree collapses the card's layout
     * nodes, so the card's fixed width is not reachable by walking up from the
     * text. The album-format card is a fixed-width ItemContainer column
     * (thumbnail width plus horizontal padding); a row-format card or a
     * song-sized card would produce a different (smaller or unfixed) width. The
     * walk stops at the widest non-viewport node and bails cleanly at the root.
     */
    private fun cardNodeOf(text: SemanticsNodeInteraction): SemanticsNodeInteraction {
        var node: SemanticsNodeInteraction = text
        var card: SemanticsNodeInteraction = text
        var width = Dp(0f)
        var depth = 0
        while (depth < 8) {
            val parent = node.onParent()
            val w: Dp
            try {
                w = parent.getBoundsInRoot().let { it.right - it.left }
            } catch (e: AssertionError) {
                break // root reached: no more parents
            }
            node = parent
            if (w > 250.dp) break // a full-width viewport, not the card
            if (w > width) {
                card = node
                width = w
            }
            depth++
        }
        return card
    }

    private fun cardWidthOf(text: SemanticsNodeInteraction): Dp {
        val bounds = cardNodeOf(text).getBoundsInRoot()
        return bounds.right - bounds.left
    }

    @Test
    fun artistCardUsesTheAlbumSizeWithTheNameCenteredBelow() {
        content(listOf(artistItem()), title = "Artist picks")

        val nameNode = composeRule.onNodeWithText("Test Artist", useUnmergedTree = true)
        nameNode.assertIsDisplayed()

        // The card must be rendered at the album size, not the song size: the
        // album card (thumbnail + padding) is measurably wider than the song
        // card would be, and a row-format card would not have a fixed width.
        val cardWidth = cardWidthOf(nameNode)
        assertTrue(
            "the artist card must be rendered at the album size, card width=$cardWidth",
            cardWidth >= albumSize && cardWidth <= albumSize + 60.dp,
        )

        // The name is horizontally centered on the card (a row-format card would
        // place it to the right of the circle).
        val cardBounds = cardNodeOf(nameNode).getBoundsInRoot()
        val nameBounds = nameNode.getBoundsInRoot()
        val cardCenter = cardBounds.left + (cardBounds.right - cardBounds.left) * 0.5f
        val nameCenter = nameBounds.left + (nameBounds.right - nameBounds.left) * 0.5f
        assertTrue(
            "the artist name must be centered on the card, card=$cardCenter name=$nameCenter",
            abs((nameCenter - cardCenter).value).dp <= 2.dp,
        )
    }
}
