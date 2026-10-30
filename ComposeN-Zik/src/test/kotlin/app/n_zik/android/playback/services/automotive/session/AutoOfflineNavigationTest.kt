package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.ArtistTable
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.browse.handlers.QuickPicksBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.RootBrowseHandler
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoOfflineNavigationTest {
    private lateinit var app: Application
    private lateinit var callback: AutoSessionCallback
    private val downloads = mockk<MyDownloadHelper>()
    private val database = mockk<Database>()
    private val session = mockk<MediaLibrarySession>(relaxed = true)
    private val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
        app.preferences.edit().remove(fullOfflineModeKey).commit()
        callback = AutoSessionCallback(app, database, downloads)
        setPhysicalOnline(true)
    }

    @After fun cleanup() {
        callback.release()
        app.preferences.edit().remove(fullOfflineModeKey).commit()
        setPhysicalOnline(true)
        unmockkAll()
    }

    private fun setPhysicalOnline(online: Boolean) {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = ShadowNetworkCapabilities.newInstance()
        val shadow = shadowOf(capabilities)
        shadow.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        if (online) {
            shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadow.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        shadowOf(manager).setNetworkCapabilities(checkNotNull(manager.activeNetwork), capabilities)
    }

    private fun assertSearch(commands: SessionCommands, online: Boolean) {
        assertEquals(online, commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
        assertEquals(online, commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT))
        assertEquals(online, commands.contains(AutoSessionConstants.CommandSearch))
        assertTrue(commands.contains(AutoSessionConstants.CommandToggleLike))
    }

    // JUnit is synchronous; bridge the suspend browse handler without touching playback.
    private fun rootIds() = runBlocking {
        RootBrowseHandler().getChildren(PlayerServiceModern.ROOT, app, database, downloads, null).map { it.mediaId }
    }

    private fun assertLocalRoots() {
        assertEquals(listOf(PlayerServiceModern.SONG, PlayerServiceModern.ARTIST,
            PlayerServiceModern.ALBUM, PlayerServiceModern.PLAYLIST), rootIds())
    }

    @Test fun `physical offline removes discovery and reconnect restores root and library search commands`() =
        assertPolicyRoundTrip(forced = false)

    @Test fun `forced offline with validated wifi removes discovery and disabling restores capabilities`() =
        assertPolicyRoundTrip(forced = true)

    private fun assertPolicyRoundTrip(forced: Boolean) {
        assertTrue(NetworkQualityHelper.isNetworkConnected(app))
        assertTrue(rootIds().contains(AutoSessionConstants.ID_QUICK_PICKS))
        assertSearch(callback.onConnect(session, controller).availableSessionCommands, true)
        if (forced) app.preferences.edit().putBoolean(fullOfflineModeKey, true).commit()
        else setPhysicalOnline(false)
        assertEquals(forced, OfflineMode.enabled(app))
        assertFalse(NetworkQualityHelper.isNetworkConnected(app))
        assertLocalRoots()
        callback.invalidateAccess(session)
        verify { session.setAvailableCommands(controller, match { commands ->
            !commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH) &&
                !commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT) &&
                !commands.contains(AutoSessionConstants.CommandSearch)
        }, any()) }
        verify { session.notifyChildrenChanged(PlayerServiceModern.ROOT, 4, null) }
        assertSearch(callback.onConnect(session, controller).availableSessionCommands, false)
        assertEquals(SessionError.ERROR_NOT_SUPPORTED, callback.onSearch(session, controller, "queen", null).get().resultCode)
        assertTrue(callback.onGetSearchResult(session, controller, "queen", 0, 20, null).get().value.orEmpty().isEmpty())
        verify(exactly = 0) { session.notifySearchResultChanged(any(), any(), any(), any()) }
        if (forced) app.preferences.edit().putBoolean(fullOfflineModeKey, false).commit()
        else setPhysicalOnline(true)
        callback.invalidateAccess(session)
        verify { session.setAvailableCommands(controller, match { commands ->
            commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH) &&
                commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT) &&
                commands.contains(AutoSessionConstants.CommandSearch)
        }, any()) }
        verify { session.notifyChildrenChanged(PlayerServiceModern.ROOT, 5, null) }
        assertSearch(callback.onConnect(session, controller).availableSessionCommands, true)
        assertTrue(rootIds().contains(AutoSessionConstants.ID_QUICK_PICKS))
    }

    @Test fun `artwork refresh derives offline counts after online root browse and recovers online`() {
        val root = callback.onGetChildren(session, controller, PlayerServiceModern.ROOT, 0, 20, null)
            .get(15, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(5, root.value?.size)
        // Retain a previously browsed Quick Picks count without fetching provider content.
        val parents = AutoSessionCallback::class.java.getDeclaredField("browsedParents").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        (parents.get(callback) as MutableMap<String, Int>)[AutoSessionConstants.ID_QUICK_PICKS] = 8
        setPhysicalOnline(false)
        callback.invalidateAccess(session)
        clearMocks(session, answers = false)
        callback.refreshArtwork(session)
        verify(exactly = 1) { session.notifyChildrenChanged(PlayerServiceModern.ROOT, 4, null) }
        verify(exactly = 1) { session.notifyChildrenChanged(AutoSessionConstants.ID_QUICK_PICKS, 0, null) }
        verify(exactly = 0) { session.notifyChildrenChanged(PlayerServiceModern.ROOT, 5, null) }
        setPhysicalOnline(true)
        clearMocks(session, answers = false)
        callback.refreshArtwork(session)
        verify(exactly = 1) { session.notifyChildrenChanged(PlayerServiceModern.ROOT, 5, null) }
    }

    @Test fun `offline callback Quick Picks zero becomes unknown on online access and artwork refresh`() {
        assertOfflineQuickPicksCount(AutoSessionConstants.ID_QUICK_PICKS)
    }

    @Test fun `offline callback paged Quick Picks zero becomes unknown on online artwork refresh`() {
        assertOfflineQuickPicksCount(AutoMediaIdContract.page(AutoSessionConstants.ID_QUICK_PICKS, 0))
    }

    private fun assertOfflineQuickPicksCount(parent: String) {
        // The full browse tree reads offline eligibility even for an empty handler result.
        // Supply its real mutable download state; the direct-handler tests below retain
        // strict mocks to prove their earlier exit before any download access.
        callback.release()
        val originalDownloads = MyDownloadHelper.downloads
        MyDownloadHelper.downloads = MutableStateFlow(emptyMap())
        callback = AutoSessionCallback(app, database, MyDownloadHelper)
        try {
            setPhysicalOnline(false)
            val result = callback.onGetChildren(session, controller, parent, 0, 20, null)
                .get(15, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue(result.value.orEmpty().isEmpty())
            callback.refreshArtwork(session)
            verify { session.notifyChildrenChanged(parent, 0, null) }
            setPhysicalOnline(true)
            clearMocks(session, answers = false)
            // Even an art refresh before the access observer fires must not repeat offline zero.
            callback.refreshArtwork(session)
            verify(exactly = 1) { session.notifyChildrenChanged(parent, Int.MAX_VALUE, null) }
            verify(exactly = 0) { session.notifyChildrenChanged(parent, 0, null) }
            clearMocks(session, answers = false)
            callback.invalidateAccess(session)
            callback.refreshArtwork(session)
            verify(exactly = 2) { session.notifyChildrenChanged(parent, Int.MAX_VALUE, null) }
            verify(exactly = 0) { session.notifyChildrenChanged(parent, 0, null) }
            verify { database wasNot Called }
        } finally {
            MyDownloadHelper.downloads = originalDownloads
        }
    }

    private fun artistsWithEmptySources(): ArtistTable = mockk<ArtistTable>().also { artists ->
        every { database.artistTable } returns artists
        every { artists.allInLibrary() } returns flowOf(emptyList())
        every { artists.allFollowing() } returns flowOf(emptyList())
        every { artists.allDisliked() } returns flowOf(emptyList())
    }

    private fun browseArtists() = callback.onGetChildren(session, controller, PlayerServiceModern.ARTIST, 0, 20, null)
        .get(15, TimeUnit.SECONDS)

    @Test fun `access invalidation announces unknown for actual previously browsed artist categories`() {
        artistsWithEmptySources()
        val count = browseArtists().value.orEmpty().size
        assertTrue("Actual artist category handler must publish a known nonzero count", count > 0)
        callback.refreshArtwork(session)
        verify(exactly = 1) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, count, null) }
        clearMocks(session, answers = false)

        setPhysicalOnline(false)
        callback.invalidateAccess(session)
        callback.refreshArtwork(session)
        verify(exactly = 2) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, Int.MAX_VALUE, null) }
        verify(exactly = 0) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, count, null) }
        verify(exactly = 0) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, 0, null) }
    }

    @Test fun `invalidated delayed artist browse cannot replace unknown count with rejected empty result`() {
        val artists = artistsWithEmptySources()
        val count = browseArtists().value.orEmpty().size
        assertTrue(count > 0)
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        every { artists.allInLibrary() } returns flow {
            entered.countDown()
            release.await()
            emit(emptyList())
        }
        val pending = callback.onGetChildren(session, controller, PlayerServiceModern.ARTIST, 0, 20, null)
        try {
            assertTrue("Artist source did not reach gate", entered.await(15, TimeUnit.SECONDS))
            assertFalse(pending.isDone)
            setPhysicalOnline(false)
            callback.invalidateAccess(session)
            verify { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, Int.MAX_VALUE, null) }
            // Return online before completing the old source; this also proves that
            // a policy round trip does not restore the old request's publication rights.
            setPhysicalOnline(true)
            callback.invalidateAccess(session)
            release.complete(Unit)
            val rejected = pending.get(15, TimeUnit.SECONDS)
            assertEquals(SessionError.ERROR_BAD_VALUE, rejected.resultCode)
            assertEquals(null, rejected.value)
            clearMocks(session, answers = false)
            callback.refreshArtwork(session)
            verify(exactly = 1) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, Int.MAX_VALUE, null) }
            verify(exactly = 0) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, 0, null) }

            assertEquals(count, browseArtists().value.orEmpty().size)
            clearMocks(session, answers = false)
            callback.refreshArtwork(session)
            verify(exactly = 1) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, count, null) }
        } finally {
            release.complete(Unit)
            pending.cancel(true)
        }
    }

    @Test fun `first in flight artist parent is notified unknown and rejects stale completion before fresh retry`() {
        val artists = artistsWithEmptySources()
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        every { artists.allInLibrary() } returns flow {
            entered.countDown()
            release.await()
            emit(emptyList())
        }
        val pending = callback.onGetChildren(session, controller, PlayerServiceModern.ARTIST, 0, 20, null)
        try {
            // Registration is synchronous at callback invocation, before source success.
            callback.refreshArtwork(session)
            verify { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, Int.MAX_VALUE, null) }
            assertTrue("First artist source did not reach gate", entered.await(15, TimeUnit.SECONDS))
            clearMocks(session, answers = false)
            callback.invalidateAccess(session)
            verify(exactly = 1) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, Int.MAX_VALUE, null) }
            release.complete(Unit)
            val rejected = pending.get(15, TimeUnit.SECONDS)
            assertEquals(SessionError.ERROR_BAD_VALUE, rejected.resultCode)
            assertEquals(null, rejected.value)
            clearMocks(session, answers = false)
            callback.refreshArtwork(session)
            verify(exactly = 1) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, Int.MAX_VALUE, null) }
            verify(exactly = 0) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, 0, null) }

            val count = browseArtists().value.orEmpty().size
            assertTrue(count > 0)
            clearMocks(session, answers = false)
            callback.refreshArtwork(session)
            verify(exactly = 1) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, count, null) }
        } finally {
            release.complete(Unit)
            pending.cancel(true)
        }
    }

    @Test fun `access change after nonempty tree publication rejects callback rows and preserves unknown count`() {
        artistsWithEmptySources()
        val treeField = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply { isAccessible = true }
        val tree = treeField.get(callback) as AutoBrowseTree
        val gatedTree = mockk<AutoBrowseTree>()
        every { gatedTree.invalidateAccess() } answers { tree.invalidateAccess() }
        every { gatedTree.refreshArtwork() } answers { tree.refreshArtwork() }
        every { gatedTree.selectionStore } returns tree.selectionStore
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        coEvery { gatedTree.getChildren(PlayerServiceModern.ARTIST, -1, null) } coAnswers {
            // Complete the actual handler, snapshot capture and pagination first,
            // then hold the already published nonempty result at the callback boundary.
            val loaded = tree.getChildren(PlayerServiceModern.ARTIST, -1, null)
            assertTrue(loaded.isNotEmpty())
            entered.countDown()
            release.await()
            loaded
        }
        treeField.set(callback, gatedTree)
        val pending = callback.onGetChildren(session, controller, PlayerServiceModern.ARTIST, 0, 20, null)
        try {
            assertTrue("Real tree did not publish its nonempty result", entered.await(15, TimeUnit.SECONDS))
            callback.invalidateAccess(session)
            verify { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, Int.MAX_VALUE, null) }
            release.complete(Unit)
            val rejected = pending.get(15, TimeUnit.SECONDS)
            assertEquals(SessionError.ERROR_BAD_VALUE, rejected.resultCode)
            assertEquals(null, rejected.value)
            clearMocks(session, answers = false)
            callback.refreshArtwork(session)
            verify(exactly = 1) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, Int.MAX_VALUE, null) }
            verify(exactly = 0) { session.notifyChildrenChanged(PlayerServiceModern.ARTIST, 0, null) }
        } finally {
            release.complete(Unit)
            pending.cancel(true)
            treeField.set(callback, tree)
        }
    }

    @Test fun `stale direct quick picks does no database or download work during physical offline`() =
        assertStaleQuickPicks(forced = false)

    @Test fun `stale direct quick picks does no database or download work during forced offline`() =
        assertStaleQuickPicks(forced = true)

    // The handler must exit before local trending, account/provider access or artwork mapping.
    private fun assertStaleQuickPicks(forced: Boolean) = runBlocking {
        if (forced) app.preferences.edit().putBoolean(fullOfflineModeKey, true).commit()
        else setPhysicalOnline(false)
        assertTrue(QuickPicksBrowseHandler().getChildren(AutoSessionConstants.ID_QUICK_PICKS,
            app, database, downloads, null).isEmpty())
        verify { database wasNot Called }
        verify { downloads wasNot Called }
    }
}
