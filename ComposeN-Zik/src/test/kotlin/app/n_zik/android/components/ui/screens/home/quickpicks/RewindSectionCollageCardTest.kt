package app.n_zik.android.components.ui.screens.home.quickpicks

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.PlaylistPreview
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.items.FOUR_CORNERS
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Compose test for the Quick Picks "Rétrospective" (rewind) section ([RewindSection]) —
 * user request 2026-10-24: each generated rewind-* playlist card must use the same card
 * as the library's local playlist list. This test pins what that card renders —
 * the four-track cover collage, the localized playlist name and the song-count
 * badge of the generic [app.it.fast4x.rimusic.ui.items.PlaylistItem] (the origin
 * icon travels with the same card) — replacing the earlier album-format
 * single-cover card.
 *
 * JUnit 4 + Robolectric with the plain [Application] (MainApplication.onCreate needs
 * AndroidKeyStore, which is unavailable on the Robolectric JVM); the Room DB is the
 * shared [DatabaseInitializer.Instance], same pattern as the automotive browse tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class RewindSectionCollageCardTest {

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

    /**
     * Polls [condition] while advancing BOTH the frozen Compose main clock and the
     * Robolectric main looper — the collage arrives through the Room flow on a
     * background dispatcher, so clock advances alone are not guaranteed to pump the
     * resulting recomposition (extends the `OfflineContentTest.composeWaitUntil`
     * rationale to background-emitted state). [label] is reported on timeout so a
     * failure names which expectation (canary vs. corner cells) was not met.
     */
    private fun composeWaitUntil(label: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            composeRule.waitForIdle()
            if (condition()) return
            Thread.sleep(25)
        }
        assertTrue("[$label] not met within ${timeoutMs}ms", condition())
    }

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        // One monthly rewind playlist with five mapped tracks (thumbnails +
        // playtime): enough for the base card's four-corner collage to render
        // four cover cells.
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
    fun rewindCardUsesTheLibraryCollageCardWithTheCountBadge() {
        val preview = PlaylistPreview(Playlist(id = 7, name = "rewind-monthly:202609"), songCount = 5)
        // Frozen main clock: state emitted from a background dispatcher only
        // recomposes on a pumped frame. The canary below observes the same Room
        // flow as the base card and stays DEPENDENCY-OF-THE-COMPOSITION (read as
        // the effect key), so its background update invalidates the composition
        // and schedules the frame that flushes the card's pending collage state.
        // The value copy happens in the effect, not in the composition body.
        val canarySize = mutableStateOf(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val flowSize by remember {
                Database.songPlaylistMapTable.sortSongsByPlayTime(7).map { it.size }
            }.collectAsStateWithLifecycle(0, context = NzikDispatchers.DATA)
            CompositionLocalProvider(LocalAppearance provides appearance) {
                LaunchedEffect(flowSize) {
                    canarySize.value = flowSize
                }
                RewindSection(
                    showRewind = true,
                    rewindPlaylists = listOf(preview),
                    navController = mockk<NavController>(relaxed = true),
                    endPaddingValues = PaddingValues(),
                    sectionTextModifier = Modifier,
                    playlistThumbnailSizePx = 0,
                    playlistThumbnailSizeDp = 100.dp,
                    disableScrollingText = true,
                )
            }
        }
        // The canary reaches the full track count once the Compose-side flow
        // pipeline (Room -> DATA dispatcher -> observed state) has delivered.
        composeWaitUntil("canary observed the mapped track count", 10_000) { canarySize.value >= 4 }

        // The base card renders its collage cells with the corner alignments as
        // content descriptions ([FOUR_CORNERS] toString — the base card's own
        // semantics contract): wait for all four.
        val cornerDescriptions = FOUR_CORNERS.map { it.toString() }
        composeWaitUntil("all four collage corner cells rendered", 5_000) {
            cornerDescriptions.all { desc ->
                composeRule.onAllNodes(hasContentDescription(desc)).fetchSemanticsNodes().isNotEmpty()
            }
        }

        // The card keeps the localized playlist name below the cover.
        composeRule.onNodeWithText("Rewind September 2026").assertIsDisplayed()

        // The song-count badge travels with the collage (library card contract).
        composeRule.onNodeWithText("5").assertIsDisplayed()
    }
}
