package app.n_zik.android.playback.services.automotive.browse

import android.app.Application
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import io.mockk.mockkObject
import io.mockk.every
import android.content.Context
import android.net.Uri
import app.n_zik.android.playback.services.ArtworkContentProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.handlers.BrowseHandler
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.searchPage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoBrowseProfilePublicationTest {
    private lateinit var context: Context
    private lateinit var tree: AutoBrowseTree
    private lateinit var handler: BrowseHandler
    private val parent = "album/shared-parent"

    @Before
    fun setUp() {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        switchProfile("A")
        tree = AutoBrowseTree(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        handler = mockk()
        every { handler.handles(parent) } returns true
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply {
            isAccessible = true
        }.set(tree, listOf(handler))
    }

    @After
    fun tearDown() {
        context.profilePreferences.edit().remove(activeProfileKey).commit()
        unmockkAll()
    }

    @Test
    fun `late publication cannot replace another profile page or a newer generation of the same profile`() {
        coEvery { handler.getChildren(parent, context, Database, any(), null) } answers {
            rows(getActiveProfile(context))
        }
        // The suspend browse API is exercised from a synchronous JUnit test.
        runBlocking {
            val shownA = tree.getChildren(parent, 0, null)
            val generationA = generation()
            assertNotNull(tree.selectionStore.resolve(shownA.first().mediaId))

            switchProfile("B")
            val shownB = tree.getChildren(parent, 0, null)
            assertFalse(tree.publishCached("A", generationA, parent, shownA))
            val pageB = tree.getChildren(AutoMediaIdContract.page(parent, 1), -1, null)
            assertEquals((200 until 205).map { "B title $it" }, pageB.map { it.mediaMetadata.title.toString() })
            assertEquals(List(5) { "B artist" }, pageB.map { it.mediaMetadata.artist.toString() })
            assertNull(tree.selectionStore.resolve(shownA.first().mediaId))
            assertEquals("B-200", tree.selectionStore.resolve(pageB.first().mediaId)?.items?.get(200)?.mediaId)

            switchProfile("A")
            tree.getChildren(parent, 0, null)
            assertTrue(generation() > generationA)
            assertFalse(tree.publishCached("A", generationA, parent, shownA))
            val pageA = tree.getChildren(AutoMediaIdContract.page(parent, 1), -1, null)
            assertEquals((200 until 205).map { "A title $it" }, pageA.map { it.mediaMetadata.title.toString() })
            assertNotNull(tree.selectionStore.resolve(pageA.first().mediaId))
            assertNull(tree.selectionStore.resolve(shownA.first().mediaId))
            assertNull(tree.selectionStore.resolve(shownB.first().mediaId))
        }
        coVerify(exactly = 3) { handler.getChildren(parent, context, Database, any(), null) }
    }

    @Test
    fun `profile change while loading rejects the old capture before publishing any rows`() {
        coEvery { handler.getChildren(parent, context, Database, any(), null) } answers {
            rows("A").also { switchProfile("B") }
        }
        // The suspend browse API is exercised from a synchronous JUnit test.
        runBlocking {
            assertTrue(tree.getChildren(parent, 0, null).isEmpty())
            assertNull(tree.selectionStore.itemsForParent(parent))

            coEvery { handler.getChildren(parent, context, Database, any(), null) } returns rows("B")
            val pageB = tree.getChildren(AutoMediaIdContract.page(parent, 1), -1, null)
            assertEquals((200 until 205).map { "B title $it" }, pageB.map { it.mediaMetadata.title.toString() })
            val selection = requireNotNull(tree.selectionStore.resolve(pageB.first().mediaId))
            assertEquals(200, selection.startIndex)
            assertEquals((0 until 205).map { "B-$it" }, selection.items.map { it.mediaId })
        }
    }

    @Test
    fun `late handler after A B A cannot mutate current captures or legacy parent lookup`() {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        coEvery { handler.getChildren(parent, context, Database, any(), null) } coAnswers {
            if (requests++ == 0) {
                started.complete(Unit)
                release.await()
                rows("stale-A")
            } else rows(if (getActiveProfile(context) == "A") "fresh-A" else "B")
        }
        // JUnit is synchronous; async lets the real suspend handler outlive two profile switches.
        runBlocking {
            val old = async(start = CoroutineStart.UNDISPATCHED) { tree.getChildren(parent, 0, null) }
            try {
                started.await()
                switchProfile("B")
                tree.getChildren(parent, 0, null)
                switchProfile("A")
                val current = tree.getChildren(parent, 0, null)
                val token = current.first().mediaId
                release.complete(Unit)
                assertTrue(old.await().isEmpty())
                assertEquals("fresh-A-0", tree.selectionStore.itemsForParent(parent)?.first()?.mediaId)
                assertNotNull(tree.selectionStore.resolve(token))
                val cached = tree.getChildren(AutoMediaIdContract.page(parent, 0), -1, null)
                assertEquals("fresh-A title 0", cached.first().mediaMetadata.title.toString())
            } finally {
                release.complete(Unit)
                old.cancel()
            }
        }
    }
    @Test
    fun `new search reset rejects late same query without replacing fresh capture or cache`() {
        tree = AutoBrowseTree(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        mockkStatic("it.fast4x.innertube.requests.SearchPageKt")
        val searchParent = "${AutoSessionConstants.ID_SEARCH_SONGS}/same/query"
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        fun response(id: String): Result<Innertube.ItemsPage<Innertube.Item>?>? {
            val song = Innertube.SongItem(
                info = Innertube.Info(name = id, endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)),
                authors = emptyList(), album = null, durationText = "3:00", thumbnail = null,
            )
            // Nullable inline Result suspend APIs use the same boxing fixture as the handler tests.
            @Suppress("UNCHECKED_CAST")
            return Result.success(Result.success(Innertube.ItemsPage<Innertube.Item>(listOf(song), null)))
                as Result<Innertube.ItemsPage<Innertube.Item>?>?
        }
        coEvery { Innertube.searchPage<Innertube.Item>("same/query", Innertube.SearchFilter.Song.value, any()) } coAnswers {
            if (requests++ == 0) {
                started.complete(Unit)
                release.await()
                response("stale-song")
            } else response("fresh-song")
        }
        // JUnit is synchronous; suspend the real provider boundary while a second search publishes.
        runBlocking {
            val old = async(start = CoroutineStart.UNDISPATCHED) { tree.getChildren(searchParent, 0, null) }
            try {
                started.await()
                val retained = tree.selectionStore.capture(parent, rows("retained")).first().mediaId
                tree.clearCache()
                assertNotNull(tree.selectionStore.resolve(retained))
                val current = tree.getChildren(searchParent, 0, null)
                val token = current.single().mediaId
                assertEquals("fresh-song", tree.selectionStore.itemsForParent(searchParent)?.single()?.mediaId)
                release.complete(Unit)
                assertTrue(old.await().isEmpty())
                assertEquals("fresh-song", tree.selectionStore.itemsForParent(searchParent)?.single()?.mediaId)
                assertNotNull(tree.selectionStore.resolve(token))
                assertNotNull(tree.selectionStore.resolve(retained))
                val cached = tree.getChildren(AutoMediaIdContract.page(searchParent, 0), -1, null)
                assertEquals(listOf(token), cached.map { it.mediaId })
                assertEquals("fresh-song", cached.single().mediaMetadata.title.toString())
            } finally {
                release.complete(Unit)
                old.cancel()
            }
        }
        coVerify(exactly = 2) {
            Innertube.searchPage<Innertube.Item>("same/query", Innertube.SearchFilter.Song.value, any())
        }
    }

    @Test
    fun `offline browsable artist album and playlist art never exposes a direct network URI`() {
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns false
        tree = AutoBrowseTree(context, Database, MyDownloadHelper)
        AutoBrowseTree::class.java.getDeclaredField("handlers").apply { isAccessible = true }.set(tree, listOf(handler))
        val sources = listOf("https://images.example.test/artist=w1200-h1200", "https://images.example.test/album=w1200-h1200",
            "https://i.ytimg.com/pl_c/example/cover.jpg")
        coEvery { handler.getChildren(parent, context, Database, any(), null) } returns sources.mapIndexed { index, source ->
            MediaItem.Builder().setMediaId("container-$index").setMediaMetadata(MediaMetadata.Builder()
                .setIsBrowsable(true).setIsPlayable(false).setArtworkUri(Uri.parse(source)).build()).build()
        }
        // JUnit is synchronous; exercise the real suspend tree publication boundary.
        runBlocking {
            val published = tree.getChildren(parent, -1, null)
            assertEquals(sources.size, published.size)
            published.forEachIndexed { index, item ->
                val uri = requireNotNull(item.mediaMetadata.artworkUri)
                assertEquals("content", uri.scheme)
                assertEquals(ArtworkContentProvider.AUTHORITY, uri.authority)
                assertEquals(sources[index], ArtworkContentProvider.remoteSource(uri, "A")?.first)
            }
        }
    }

    private fun switchProfile(profile: String) {
        check(context.profilePreferences.edit().putString(activeProfileKey, profile).commit())
    }

    private fun generation(): Long = AutoBrowseTree::class.java.getDeclaredField("profileGeneration").run {
        isAccessible = true
        getLong(tree)
    }

    private fun rows(profile: String): List<MediaItem> = (0 until 205).map { index ->
        MediaItem.Builder()
            .setMediaId("$parent/$profile-$index")
            .setUri("https://example.test/$profile/$index")
            .setCustomCacheKey("$profile-$index")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("$profile title $index")
                    .setArtist("$profile artist")
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
    }
}
