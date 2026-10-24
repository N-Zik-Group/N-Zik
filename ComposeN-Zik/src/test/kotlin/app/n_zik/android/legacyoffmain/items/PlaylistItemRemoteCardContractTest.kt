package app.n_zik.android.legacyoffmain.items

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.items.PlaylistItem
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose contract tests for the remote (Innertube) [PlaylistItem] overload:
 * the default is `showSongsCount = false` — remote cards render the playlist
 * name but neither the song-count badge nor the paired full-cover scrim (both
 * share the same gate in the base composable; the badge is the observable
 * half). The absence test renders WITHOUT the parameter so the production
 * default itself is pinned. Tests of legacy code live under the
 * `legacyoffmain` namespace per the project convention.
 *
 * JUnit 4 + Robolectric with the plain [Application] (MainApplication.onCreate
 * needs AndroidKeyStore, which is unavailable on the Robolectric JVM).
 * `isBookmarked = false` is passed so the card skips the local-playlist DB
 * lookup entirely.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlaylistItemRemoteCardContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        // The card's thumbnail pipeline dereferences Dependencies.application
        // (lateinit) — init it or the class fails 2/2 in an isolated run.
        val app = ApplicationProvider.getApplicationContext<Application>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private val remotePlaylist = Innertube.PlaylistItem(
        info = Innertube.Info("Remote Playlist", NavigationEndpoint.Endpoint.Browse(browseId = "RDPL-REMOTE")),
        channel = Innertube.Info("Some Channel", null),
        songCount = 5,
        isEditable = null,
        description = null,
        thumbnail = Thumbnail("https://i.ytimg.com/vi/pl1/hqdefault.jpg", null, null),
    )

    @Composable
    private fun renderRemoteCard(showSongsCount: Boolean) {
        CompositionLocalProvider(LocalAppearance provides appearance) {
            PlaylistItem(
                playlist = remotePlaylist,
                thumbnailSizePx = 0,
                thumbnailSizeDp = 100.dp,
                showSongsCount = showSongsCount,
                disableScrollingText = true,
                isBookmarked = false,
            )
        }
    }

    /** Renders through the production default — the parameter is omitted. */
    @Composable
    private fun renderRemoteCardWithDefault() {
        CompositionLocalProvider(LocalAppearance provides appearance) {
            PlaylistItem(
                playlist = remotePlaylist,
                thumbnailSizePx = 0,
                thumbnailSizeDp = 100.dp,
                disableScrollingText = true,
                isBookmarked = false,
            )
        }
    }

    @Test
    fun `remote card renders the name without the song count badge by default`() {
        composeRule.setContent { renderRemoteCardWithDefault() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Remote Playlist").assertIsDisplayed()
        // Absence check: onAllNodesWithText never throws (unlike onNodeWithText).
        assertTrue(
            "remote cards must not show the song-count badge by default",
            composeRule.onAllNodesWithText("5").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `remote card shows the song count badge when showSongsCount is forced on`() {
        composeRule.setContent { renderRemoteCard(showSongsCount = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("5").assertIsDisplayed()
    }
}
