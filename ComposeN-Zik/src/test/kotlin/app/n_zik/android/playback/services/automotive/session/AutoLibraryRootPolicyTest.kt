package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.os.Bundle
import android.service.media.MediaBrowserService.BrowserRoot
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.AutoSuggestedQueueLoader
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoLibraryRootPolicyTest {
    private lateinit var callback: AutoSessionCallback

    @Before
    fun setUp() {
        callback = AutoSessionCallback(
            RuntimeEnvironment.getApplication(), Database, mockk<MyDownloadHelper>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        callback.release()
    }

    @Test
    fun `standard suggested hint selects recommendations for any browser`() {
        val params = LibraryParams.Builder().setSuggested(true).build()
        listOf(GOOGLE_SEARCH, GEARHEAD, "example.browser").forEach {
            assertRoot(it, params, AutoSuggestedQueueLoader.ROOT_ID)
        }
    }

    @Test
    fun `Google search without hints selects recommendations`() {
        assertRoot(GOOGLE_SEARCH, null, AutoSuggestedQueueLoader.ROOT_ID)
        assertRoot(GOOGLE_SEARCH, LibraryParams.Builder().build(), AutoSuggestedQueueLoader.ROOT_ID)
    }

    @Test
    fun `Google fallback advertises suggestions in legacy root extras and preserves input hints`() {
        val extras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 8)
            putBoolean(BrowserRoot.EXTRA_SUGGESTED, false)
        }
        val params = LibraryParams.Builder().setExtras(extras).build()
        val response = requireNotNull(assertRoot(GOOGLE_SEARCH, params, AutoSuggestedQueueLoader.ROOT_ID))
        assertEquals(8, response.extras.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT))
        assertEquals(false, params.isSuggested)
        assertEquals(false, params.extras.getBoolean(BrowserRoot.EXTRA_SUGGESTED))
        response.extras.putInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, 4)
        assertEquals(8, params.extras.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT))
    }

    @Test
    fun `normal Android Auto and unknown browsers keep normal browsing`() {
        listOf(GEARHEAD, "example.browser", "$GOOGLE_SEARCH.untrusted", "").forEach {
            assertRoot(it, null, PlayerServiceModern.ROOT)
            assertRoot(it, LibraryParams.Builder().build(), PlayerServiceModern.ROOT)
        }
    }

    @Test
    fun `Google search recent and offline requests do not use the compatibility fallback`() {
        listOf(
            LibraryParams.Builder().setRecent(true).build(),
            LibraryParams.Builder().setOffline(true).build(),
            LibraryParams.Builder().setRecent(true).setOffline(true).build(),
        ).forEach { assertRoot(GOOGLE_SEARCH, it, PlayerServiceModern.ROOT) }
    }

    @Test
    fun `explicit suggested hint retains precedence over recent and offline`() {
        val params = LibraryParams.Builder().setSuggested(true).setRecent(true).setOffline(true).build()
        listOf(GOOGLE_SEARCH, GEARHEAD, "example.browser").forEach {
            assertRoot(it, params, AutoSuggestedQueueLoader.ROOT_ID)
        }
    }

    private fun assertRoot(packageName: String, params: LibraryParams?, expectedId: String): LibraryParams? {
        val browser = mockk<MediaSession.ControllerInfo>()
        every { browser.packageName } returns packageName
        val result = callback.onGetLibraryRoot(mockk<MediaLibrarySession>(), browser, params).get()
        assertEquals(0, result.resultCode)
        assertEquals(expectedId, result.value?.mediaId)
        assertEquals(true, result.value?.mediaMetadata?.isBrowsable)
        assertEquals(false, result.value?.mediaMetadata?.isPlayable)
        val expectedSuggested = expectedId == AutoSuggestedQueueLoader.ROOT_ID
        val response = result.params
        if (expectedSuggested && params?.isSuggested != true) {
            assertEquals(true, response?.isSuggested)
            assertEquals(params?.isRecent == true, response?.isRecent)
            assertEquals(params?.isOffline == true, response?.isOffline)
        } else {
            assertSame(params, response)
        }
        val conversion = Class.forName("androidx.media3.session.LegacyConversions")
            .getDeclaredMethod("convertToRootHints", LibraryParams::class.java).apply { isAccessible = true }
        val legacyExtras = conversion.invoke(null, response) as? Bundle
        assertEquals(expectedSuggested, legacyExtras?.getBoolean(BrowserRoot.EXTRA_SUGGESTED) == true)
        return response
    }

    private companion object {
        const val GOOGLE_SEARCH = "com.google.android.googlequicksearchbox"
        const val GEARHEAD = "com.google.android.projection.gearhead"
    }
}
