package app.n_zik.android.components.ui.screens.home.quickpicks

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose test for the YTM "Retrospective" home section ([GenericYtmSections]) —
 * user request 2026-09-30: the section's video items must be shown in the album
 * format (square thumbnail, title/artist centered below), not as the wide video
 * cards with the duration badge. Any other video section keeps the video format.
 *
 * The album format is pinned through what only the video card renders: the
 * duration badge overlay. JUnit 4 + Robolectric with the plain [Application]
 * (MainApplication.onCreate needs AndroidKeyStore); the Room DB is the shared
 * [DatabaseInitializer.Instance] (like states are queried for the video keys).
 */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class YtmRetrospectiveSectionAlbumFormatTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

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

    private fun videoItem(videoId: String, name: String): Innertube.VideoItem =
        Innertube.VideoItem(
            info = Innertube.Info(
                name = name,
                endpoint = NavigationEndpoint.Endpoint.Watch(videoId = videoId),
            ),
            authors = listOf(
                Innertube.Info(name = "Test Artist", endpoint = null),
            ),
            viewsText = "1M views",
            durationText = "3:45",
            thumbnail = Thumbnail(url = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg", height = null, width = null),
        )

    private fun section(title: String): HomePage =
        HomePage(
            sections = listOf(
                HomePage.Section(
                    title = title,
                    label = null,
                    thumbnail = null,
                    endpoint = BrowseEndpoint(browseId = "FEmusic_test_section"),
                    items = listOf(videoItem("vid-1", "Retrospective Video")),
                ),
            ),
            chips = null,
        )

    private fun content(section: HomePage) {
        // No marquee animation frames to pump: the texts are asserted as-is.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                GenericYtmSections(
                    homePageInit = section,
                    displayedSectionTitles = mutableSetOf(),
                    itemInHorizontalGridWidth = 100.dp,
                    albumThumbnailSizePx = 0,
                    albumThumbnailSizeDp = 100.dp,
                    songThumbnailSizePx = 0,
                    songThumbnailSizeDp = 100.dp,
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

    @Test
    fun retrospectiveSectionVideosUseTheAlbumFormat() {
        content(section("Retrospective"))

        // The album card shows the video title below the square thumbnail…
        composeRule.onNodeWithText("Retrospective Video").assertIsDisplayed()
        // …and never the duration badge, which only the wide video card renders.
        composeRule.onNodeWithText("3:45").assertDoesNotExist()
    }

    @Test
    fun accentedRetrospectiveTitleUsesTheAlbumFormatToo() {
        // The French home page titles the section "Rétrospective" — the accent
        // must match as well (ignoreCase does not normalize accents).
        content(section("Rétrospective"))

        composeRule.onNodeWithText("Retrospective Video").assertIsDisplayed()
        composeRule.onNodeWithText("3:45").assertDoesNotExist()
    }

    @Test
    fun otherVideoSectionsKeepTheVideoFormat() {
        content(section("Video picks"))

        composeRule.onNodeWithText("Retrospective Video").assertIsDisplayed()
        // The duration badge proves the wide video card is still used here.
        composeRule.onNodeWithText("3:45").assertIsDisplayed()
    }
}
