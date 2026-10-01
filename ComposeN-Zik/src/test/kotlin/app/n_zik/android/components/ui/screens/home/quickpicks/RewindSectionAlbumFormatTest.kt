package app.n_zik.android.components.ui.screens.home.quickpicks

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
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

/**
 * Compose test for the Quick Picks "Rétrospective" (rewind) section ([RewindSection]) —
 * user request 2026-09-30: each generated rewind-* playlist card must use the album
 * format (single square cover + the localized name below), not the four-track video
 * collage rendered by the generic [app.it.fast4x.rimusic.ui.items.PlaylistItem].
 *
 * JUnit 4 + Robolectric with the plain [Application] (MainApplication.onCreate needs
 * AndroidKeyStore, which is unavailable on the Robolectric JVM); the Room DB is the
 * shared [DatabaseInitializer.Instance], same pattern as the automotive browse tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class RewindSectionAlbumFormatTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    /**
     * The shared [DatabaseInitializer.Instance] has no `allowMainThreadQueries()`,
     * so every DAO call must run off the main thread. runBlocking is forced by the
     * synchronous test setup API (AGENTS.md runBlocking exception).
     */
    private suspend fun <T> onDatabase(block: suspend () -> T): T = withContext(NzikDispatchers.DATA) { block() }

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        // One monthly rewind playlist with five mapped tracks (thumbnails +
        // playtime): enough for the old four-corner collage to have rendered
        // four video cells.
        runBlocking {
            onDatabase {
                Database.playlistTable.insertIgnore(Playlist(id = 7, name = "rewind-monthly:202609"))
                repeat(5) { i ->
                    val song = Song(
                        id = "rw-$i",
                        title = "Song $i",
                        durationText = null,
                        thumbnailUrl = "https://i.ytimg.com/vi/v$i/hqdefault.jpg",
                        likedAt = null,
                        totalPlayTimeMs = (i + 1) * 1000L,
                    )
                    Database.songTable.upsert(song)
                    Database.songPlaylistMapTable.mapAtPosition(song.id, 7, i)
                }
            }
        }
    }

    @After
    fun tearDown() {
        runBlocking { onDatabase { DatabaseInitializer.Instance.clearAllTables() } }
    }

    @Test
    fun rewindCardIsAnAlbumCardWithTheLocalizedNameAndNoVideoCollage() {
        val preview = PlaylistPreview(Playlist(id = 7, name = "rewind-monthly:202609"), songCount = 5)
        // No marquee animation frames to pump: the text is asserted as-is.
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                RewindSection(
                    showRewind = true,
                    rewindPlaylists = listOf(preview),
                    navController = mockk<NavController>(relaxed = true),
                    endPaddingValues = PaddingValues(),
                    sectionTextModifier = Modifier,
                    itemInHorizontalGridWidth = 100.dp,
                    playlistThumbnailSizePx = 0,
                    playlistThumbnailSizeDp = 100.dp,
                    disableScrollingText = true,
                )
            }
        }
        composeRule.waitForIdle()

        // The card keeps the localized playlist name below the cover.
        composeRule.onNodeWithText("Rewind September 2026").assertIsDisplayed()

        // Album format: the four-track video collage is gone (the generic
        // PlaylistItem renders its collage cells with the corner alignments
        // as content descriptions).
        listOf("TopStart", "TopEnd", "BottomStart", "BottomEnd").forEach { corner ->
            assertTrue(
                "video collage cell $corner must not be rendered in the album format",
                composeRule.onAllNodes(hasContentDescription(corner)).fetchSemanticsNodes().isEmpty()
            )
        }
    }
}
