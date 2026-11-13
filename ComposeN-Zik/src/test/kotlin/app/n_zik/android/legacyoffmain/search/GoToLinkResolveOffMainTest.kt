package app.n_zik.android.legacyoffmain.search

import android.net.Uri
import androidx.navigation.NavController
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.ui.screens.search.resolveGoToLink
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.requests.playlistPage
import io.mockk.every
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Issue #606 M12 (Lot 2, AD-4 tier 2 legacy in-place patch) -- `GoToLink.kt`'s deep-link resolution
 * used to run entirely inside `coroutineScope.launch(Dispatchers.Main)`, so the 2 network calls it
 * makes (`Innertube.playlistPage`, `Innertube.song`) blocked Main even though the eventual
 * `navController.navigate`/`forcePlay` calls are the only part that actually needs Main. It is now
 * extracted, unchanged, to [resolveGoToLink] (declared `internal` in `GoToLink.kt`,
 * `app.n_zik.android.*`, legacy), launched on `NzikDispatchers.DATA`, with each
 * `navController.navigate` call individually wrapped in `withContext(NzikDispatchers.UI)`.
 *
 * This test file itself lives under `app.n_zik.android.*` -- not the legacy package -- per the
 * gh-606 Lot 2 spec's "no new file under app.n_zik.android.*" boundary; `internal` visibility
 * is module-scoped in Kotlin, not package-scoped, so it still resolves [resolveGoToLink] from here.
 *
 * `Dispatchers.setMain` installs a real dedicated single thread standing in for
 * `NzikDispatchers.UI` (`== Dispatchers.Main`), so each test can assert that the thread
 * `navController.navigate` actually runs on is that Main stand-in thread, not whatever thread the
 * surrounding `withContext(NzikDispatchers.DATA)` used -- proving the individual `withContext(UI)`
 * wraps around `navigate` are load-bearing, not just present in the source. `Uri`/`NavController`
 * are mocked directly (same technique `PlayerQueueOffMainTest` and
 * `ChangeCoverDialogSaveCoverArtOffMainTest`, Lot 1, use for their legacy dependencies) so this
 * stays a fast, deterministic JVM unit test with no Robolectric needed.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class GoToLinkResolveOffMainTest {

    private val mainThreadDispatcher = newSingleThreadContext("go-to-link-test-main")

    /** The actual thread name backing [mainThreadDispatcher], probed once rather than assumed --
     *  `newSingleThreadContext`'s exact naming scheme isn't part of its public contract. */
    private lateinit var mainThreadName: String

    /** Strips the coroutines-debug `" @coroutine#N"` suffix `Thread.currentThread().name` gets
     *  when read from inside a coroutine (with the debug agent active, as under a JVM test run) --
     *  the suffix's number differs per coroutine even on the same real OS thread, so comparing raw
     *  names would flag two observations of the *same* thread as different. */
    private fun baseThreadName(name: String?) = name?.substringBefore(" @coroutine")

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(mainThreadDispatcher)
        mainThreadName = baseThreadName(
            runBlocking { withContext(mainThreadDispatcher) { Thread.currentThread().name } }
        )!!
        mockkStatic("app.n_zik.innertube.requests.PlaylistPageKt")
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
        mainThreadDispatcher.close()
        unmockkAll()
    }

    private fun mockUri(pathSegments: List<String>, queryParams: Map<String, String> = emptyMap(), lastPathSegment: String? = null): Uri {
        val uri = mockk<Uri>()
        every { uri.pathSegments } returns pathSegments
        every { uri.lastPathSegment } returns (lastPathSegment ?: pathSegments.lastOrNull())
        every { uri.host } returns null
        queryParams.forEach { (key, value) -> every { uri.getQueryParameter(key) } returns value }
        return uri
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> resultAnswerFor(value: Result<T>): Result<T> = Result.success(value) as Result<T>

    @Test
    fun `resolveGoToLink navigates to the artist route on Main for a channel deep link`() = runBlocking {
        val uri = mockUri(pathSegments = listOf("channel", "UC123"), lastPathSegment = "UC123")
        val navController = mockk<NavController>(relaxed = true)
        var navigateThreadName: String? = null
        every { navController.navigate(route = "${NavRoutes.artist.name}/UC123") } answers {
            navigateThreadName = Thread.currentThread().name
        }

        withContext(NzikDispatchers.DATA) {
            resolveGoToLink(uri, navController, binder = null)
        }

        verify(exactly = 1) { navController.navigate(route = "${NavRoutes.artist.name}/UC123") }
        assertEquals(mainThreadName, baseThreadName(navigateThreadName))
    }

    @Test
    fun `resolveGoToLink navigates to the search results route on Main for a search deep link`() = runBlocking {
        val uri = mockUri(pathSegments = listOf("search"), queryParams = mapOf("q" to "hello"))
        val navController = mockk<NavController>(relaxed = true)
        var navigateThreadName: String? = null
        every { navController.navigate(route = "${NavRoutes.searchResults.name}/hello") } answers {
            navigateThreadName = Thread.currentThread().name
        }

        withContext(NzikDispatchers.DATA) {
            resolveGoToLink(uri, navController, binder = null)
        }

        verify(exactly = 1) { navController.navigate(route = "${NavRoutes.searchResults.name}/hello") }
        assertEquals(mainThreadName, baseThreadName(navigateThreadName))
    }

    @Test
    fun `resolveGoToLink navigates to the playlist route on Main for a non-album playlist id`() = runBlocking {
        val uri = mockUri(pathSegments = listOf("playlist"), queryParams = mapOf("list" to "PL999"))
        val navController = mockk<NavController>(relaxed = true)
        var navigateThreadName: String? = null
        every { navController.navigate(route = "${NavRoutes.playlist.name}/VLPL999") } answers {
            navigateThreadName = Thread.currentThread().name
        }

        withContext(NzikDispatchers.DATA) {
            resolveGoToLink(uri, navController, binder = null)
        }

        verify(exactly = 1) { navController.navigate(route = "${NavRoutes.playlist.name}/VLPL999") }
        assertEquals(mainThreadName, baseThreadName(navigateThreadName))
    }

    @Test
    fun `resolveGoToLink resolves an OLAK5uy_ playlist id to its album route on Main`() = runBlocking {
        val uri = mockUri(pathSegments = listOf("playlist"), queryParams = mapOf("list" to "OLAK5uy_abc"))
        val navController = mockk<NavController>(relaxed = true)
        var navigateThreadName: String? = null
        var dataThreadNameDuringNetworkCall: String? = null
        every { navController.navigate(route = "${NavRoutes.album.name}/albumBrowseId") } answers {
            navigateThreadName = Thread.currentThread().name
        }
        val songItem = Innertube.SongItem(
            info = null,
            authors = null,
            album = Innertube.Info(name = null, endpoint = NavigationEndpoint.Endpoint.Browse(browseId = "albumBrowseId")),
            durationText = null,
            thumbnail = null,
        )
        val page = Innertube.PlaylistOrAlbumPage(
            title = null,
            authors = null,
            year = null,
            thumbnail = null,
            url = null,
            songsPage = Innertube.ItemsPage(items = listOf(songItem), continuation = null),
            otherVersions = null,
            description = null,
            otherInfo = null,
        )
        coEvery { Innertube.playlistPage(browseId = "VLOLAK5uy_abc") } answers {
            dataThreadNameDuringNetworkCall = Thread.currentThread().name
            resultAnswerFor(Result.success(page))
        }

        withContext(NzikDispatchers.DATA) {
            resolveGoToLink(uri, navController, binder = null)
        }

        verify(exactly = 1) { navController.navigate(route = "${NavRoutes.album.name}/albumBrowseId") }
        assertEquals(mainThreadName, baseThreadName(navigateThreadName))
        assertNotEquals(
            "the network call itself must stay on the surrounding NzikDispatchers.DATA context, not Main",
            mainThreadName,
            baseThreadName(dataThreadNameDuringNetworkCall)
        )
    }
}
