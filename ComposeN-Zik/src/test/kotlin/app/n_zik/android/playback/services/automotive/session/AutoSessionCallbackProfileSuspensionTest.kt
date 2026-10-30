package app.n_zik.android.playback.services.automotive.session

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.app.Application
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.DislikedExclusion
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Pins profile ownership across the suspend search-exclusion read in the public callback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoSessionCallbackProfileSuspensionTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var tree: AutoBrowseTree
    private val parent = "${AutoSessionConstants.ID_SEARCH_SONGS}/query"
    private val releaseExclusions = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        switchProfile("A")
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        tree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply {
            isAccessible = true
        }.get(callback) as AutoBrowseTree
        mockkObject(DislikedExclusion)
    }

    @After
    fun tearDown() {
        releaseExclusions.complete(Unit)
        callback.release()
        context.profilePreferences.edit().remove(activeProfileKey).commit()
        unmockkObject(DislikedExclusion)
        unmockkStatic("app.n_zik.android.GlobalVarsKt")
    }

    @Test
    fun `profile change during exclusion read rejects old row and permits fresh profile selection`() {
        assertProfileChangeRejects(action = false)
    }

    @Test
    fun `profile change during exclusion read rejects old scoped action and permits fresh profile selection`() {
        assertProfileChangeRejects(action = true)
    }

    @Test
    fun `unchanged profile preserves selected duplicate occurrence after suspended exclusion filtering`() {
        val entered = pauseExclusions(setOf("excluded"))
        val shown = tree.selectionStore.capture(parent, rows(listOf("duplicate", "excluded", "duplicate", "tail")))
        val pending = select(shown[2])
        assertTrue("Callback did not reach the exclusion read", entered.await(15, TimeUnit.SECONDS))
        assertTrue("Callback must remain suspended before exclusions return", !pending.isDone)
        releaseExclusions.complete(Unit)

        val result = pending.get(15, TimeUnit.SECONDS)
        assertEquals(listOf("duplicate", "duplicate", "tail"), result.mediaItems.map { it.mediaId })
        assertEquals(1, result.startIndex)
        assertEquals("row-2", result.mediaItems[result.startIndex].mediaMetadata.title.toString())
        assertEquals(1234L, result.startPositionMs)
    }

    private fun assertProfileChangeRejects(action: Boolean) {
        val entered = pauseExclusions(emptySet())
        val original = rows(listOf("private-A-before", "private-A-selected"))
        val shown = tree.selectionStore.capture(parent, if (action) {
            listOf(AutoSessionConstants.playAllItem(context, "${AutoSessionConstants.ID_SEARCH_SONGS}_PLAY_ALL")) + original
        } else original)
        val pending = select(if (action) shown.first() else shown[1])
        assertTrue("Callback did not reach the exclusion read", entered.await(15, TimeUnit.SECONDS))
        assertTrue("Callback must remain suspended before exclusions return", !pending.isDone)

        // No store operation observes B before the suspended callback resumes.
        switchProfile("B")
        releaseExclusions.complete(Unit)
        assertTrue(pending.get(15, TimeUnit.SECONDS).mediaItems.isEmpty())

        coEvery { DislikedExclusion.excludedSongIds(context, Database) } returns emptySet()
        val fresh = tree.selectionStore.capture(parent, rows(listOf("public-B-before", "public-B-selected")))
        val result = select(fresh[1]).get(15, TimeUnit.SECONDS)
        assertEquals(listOf("public-B-before", "public-B-selected"), result.mediaItems.map { it.mediaId })
        assertEquals(1, result.startIndex)
    }

    private fun pauseExclusions(excluded: Set<String>): CountDownLatch {
        val entered = CountDownLatch(1)
        coEvery { DislikedExclusion.excludedSongIds(context, Database) } coAnswers {
            entered.countDown()
            releaseExclusions.await()
            excluded
        }
        return entered
    }

    private fun select(item: MediaItem) = callback.onSetMediaItems(
        mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(), mutableListOf(item), 0, 1234L
    )

    private fun switchProfile(profile: String) {
        check(context.profilePreferences.edit().putString(activeProfileKey, profile).commit())
    }

    private fun rows(ids: List<String>): List<MediaItem> = ids.mapIndexed { index, id ->
        MediaItem.Builder()
            .setMediaId("${AutoSessionConstants.ID_SEARCH_SONGS}/$id")
            .setUri("https://example.test/$id")
            .setCustomCacheKey(id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle("row-$index").setIsPlayable(true).setIsBrowsable(false).build())
            .build()
    }
}
