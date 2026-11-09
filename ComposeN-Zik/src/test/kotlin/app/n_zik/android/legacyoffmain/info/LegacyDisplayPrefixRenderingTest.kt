package app.n_zik.android.legacyoffmain.info

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import it.fast4x.innertube.models.VideoOrSongInfo
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoResult
import app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoScreen
import app.it.fast4x.rimusic.ui.screens.info.loadVideoOrSongInfo
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistTable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w400dp-h800dp")
class LegacyDisplayPrefixRenderingTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null

    @Before
    fun isolateProfile() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("legacy_display_prefix_test", context)
        context.preferences.edit().clear().putBoolean(glassEnabledKey, false).commit()
        GlassStore(context).reset()
    }

    @After
    fun restoreProfile() {
        unmockkAll()
        context.preferences.edit().clear().commit()
        GlassStore(context).reset()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test
    fun `information rows hide local metadata prefixes without changing loader input or playback`() {
        val rawArtist = "modified:local:Test artist"
        mockkStatic("app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoScreenKt")
        coEvery { loadVideoOrSongInfo(any(), any()) } returns VideoOrSongInfoResult(
            info = null,
            finalArtists = listOf("artist-id" to rawArtist),
        )
        var playCount = 0
        showContent {
            VideoOrSongInfoScreen(
                videoId = "prefix-info-local",
                songTitle = "modified:e:Local title: subtitle",
                songArtist = rawArtist,
                albumId = "album-id",
                albumTitle = "modified:account:Test album",
                onNavigateUp = {},
                onClose = {},
                onPlay = { playCount++ },
            )
        }

        waitForText("Local title: subtitle")
        composeRule.onNodeWithText("Test artist").assertIsDisplayed()
        composeRule.onNodeWithText("Test album").assertIsDisplayed()
        composeRule.onNodeWithText("modified:e:Local title: subtitle").assertDoesNotExist()
        composeRule.onNodeWithText(rawArtist).assertDoesNotExist()
        composeRule.onNodeWithText("modified:account:Test album").assertDoesNotExist()
        composeRule.onNodeWithText("Local title: subtitle").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, playCount) }
        coVerify(exactly = 1) { loadVideoOrSongInfo("prefix-info-local", rawArtist) }
    }

    @Test
    fun `information rows clean loaded titles and multiple artists while retaining ordinary colon text`() {
        mockkStatic("app.it.fast4x.rimusic.ui.screens.info.VideoOrSongInfoScreenKt")
        coEvery { loadVideoOrSongInfo(any(), any()) } returns VideoOrSongInfoResult(
            info = VideoOrSongInfo(videoId = "prefix-info-online", title = "e:modified:Online title"),
            finalArtists = listOf("one" to "modified:First artist", "two" to "Artist: second"),
        )
        showContent {
            VideoOrSongInfoScreen(
                videoId = "prefix-info-online",
                songTitle = "Local fallback",
                albumId = "album-id",
                albumTitle = "Album: deluxe",
                onNavigateUp = {},
                onClose = {},
            )
        }

        waitForText("Online title")
        composeRule.onNodeWithText("First artist").assertIsDisplayed()
        composeRule.onNodeWithText("Artist: second").assertIsDisplayed()
        composeRule.onNodeWithText("Album: deluxe").assertIsDisplayed()
        composeRule.onNodeWithText("e:modified:Online title").assertDoesNotExist()
        composeRule.onNodeWithText("Local fallback").assertDoesNotExist()
    }

    @Test
    fun `playlist selector hides stacked prefixes and preserves a marker inside a real name`() {
        val previews = listOf(
            PlaylistPreview(Playlist(id = 1, name = "pinned:modified:Favorites"), songCount = 1),
            PlaylistPreview(Playlist(id = 2, name = "modified:account:Local picks"), songCount = 2),
            PlaylistPreview(Playlist(id = 3, name = "Mix pinned:After hours"), songCount = 3),
        )
        val table = mockk<PlaylistTable>()
        mockkObject(Database)
        every { Database.playlistTable } returns table
        every { table.sortPreviewsByName(any()) } returns flowOf(previews)
        val navController = mockk<NavController>(relaxed = true)
        showContent {
            PlaylistsMenu.init(
                navController = navController,
                mediaItems = { emptyList() },
                onFailure = { error, _ -> throw error },
                finalAction = {},
            ).MenuComponent()
        }

        waitForText("Favorites")
        composeRule.onNodeWithText("Local picks").assertIsDisplayed()
        composeRule.onNodeWithText("Mix pinned:After hours").assertIsDisplayed()
        composeRule.onNodeWithText("modified:Favorites").assertDoesNotExist()
        composeRule.onNodeWithText("modified:account:Local picks").assertDoesNotExist()
        composeRule.onNodeWithText("After hours").assertDoesNotExist()
    }

    private fun showContent(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, content = content)
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(10_000) {
            // The DATA dispatcher completes independently of the frozen Compose clock.
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
