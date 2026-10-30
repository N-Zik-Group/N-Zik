package app.n_zik.android.playback.services.automotive.browse

import android.app.Application
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoBrowseProfilePublicationTest {
    private lateinit var context: Context
    private lateinit var tree: AutoBrowseTree
    private lateinit var handler: BrowseHandler
    private val parent = "album/shared-parent"

    @Before
    fun setUp() {
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
