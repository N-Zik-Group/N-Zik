package app.n_zik.android.playback.services.automotive.browse.handlers

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.playEventsTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.settings.accounts.isYouTubeLoggedIn
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.EventTable
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.isLocal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.relatedPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
class QuickPicksBrowseHandlerConnectivityTest {
    private lateinit var context: Application
    private lateinit var prefs: SharedPreferences
    private lateinit var originalPreferences: Map<String, *>
    private lateinit var originalCapabilities: NetworkCapabilities
    private val eventTable = mockk<EventTable>()
    private val downloads = MyDownloadHelper
    private var originalDownloads: MutableStateFlow<Map<String, Download>>? = null
    private val local = Song.makePlaceholder("local:quick-pick").copy(title = "Local candidate")
    private val localItem = MediaItem.Builder().setMediaId("QUICK_PICKS/${local.id}").build()
    private val preferenceKeys = listOf(fullOfflineModeKey, playEventsTypeKey, parentalControlEnabledKey)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns context
        Dependencies.init(mainApplication)
        prefs = context.preferences
        originalPreferences = prefs.all.toMap()
        prefs.edit().apply { preferenceKeys.forEach(::remove) }.commit()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        originalCapabilities = checkNotNull(manager.getNetworkCapabilities(manager.activeNetwork))
        setPhysicalOnline(true)

        mockkObject(Database, CancellableAutoMediaItemMapper)
        every { Database.eventTable } returns eventTable
        every { eventTable.findSongsMostPlayedBetween(any(), any(), any()) } returns flowOf(listOf(local))
        originalDownloads = downloads.downloads
        downloads.downloads = MutableStateFlow(emptyMap())
        mockkStatic("app.n_zik.android.components.settings.accounts.AccountsSettingsKt")
        every { isYouTubeLoggedIn() } returns false
        mockkStatic("it.fast4x.innertube.requests.RelatedPageKt")
        coEvery { Innertube.relatedPage(any(), any()) } returns emptyRelatedPage()
        coEvery {
            CancellableAutoMediaItemMapper.mapSongToMediaItem(local, AutoSessionConstants.ID_QUICK_PICKS, true)
        } returns localItem
    }

    @After fun tearDown() {
        prefs.edit().apply {
            preferenceKeys.forEach { key ->
                when (val value = originalPreferences[key]) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    null -> remove(key)
                    else -> error("Unexpected preference type for $key")
                }
            }
        }.commit()
        originalDownloads?.let { downloads.downloads = it }
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        shadowOf(manager).setNetworkCapabilities(checkNotNull(manager.activeNetwork), originalCapabilities)
        unmockkAll()
    }

    private fun setPhysicalOnline(online: Boolean) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).apply {
            addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            if (online) {
                addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        shadowOf(manager).setNetworkCapabilities(checkNotNull(manager.activeNetwork), capabilities)
    }

    // MockK's nullable suspend Result needs the same extra wrapper as QuickPicksBrowseHandlerTest.
    @Suppress("UNCHECKED_CAST")
    private fun emptyRelatedPage(): Result<Innertube.RelatedPage?>? =
        Result.success(Result.success(Innertube.RelatedPage(songs = emptyList()))) as Result<Innertube.RelatedPage?>?

    private suspend fun fetch(): List<MediaItem> = QuickPicksBrowseHandler().getChildren(
        AutoSessionConstants.ID_QUICK_PICKS, context, Database, downloads, null,
    )

    private suspend fun assertLocalStillEligibleOffline() {
        assertTrue(local.isLocal)
        assertTrue(localItem.isLocal)
        assertEquals(listOf(localItem), AutoAccessPolicy(context, Database, downloads, null)
            .eligible(listOf(localItem)).map { it.value })
    }

    // JUnit is synchronous; bridge the suspend handler with a bounded coroutine scope.
    @Test(timeout = 15_000)
    fun `online discovery keeps the local candidate and Lucky Shuffle`() = runBlocking {
        withTimeout(5_000) {
            assertTrue(NetworkQualityHelper.isNetworkConnected(context))
            assertEquals(listOf(AutoSessionConstants.ID_LUCKY_SHUFFLE, localItem.mediaId), fetch().map { it.mediaId })
            coVerify(exactly = 1) { Innertube.relatedPage(videoId = local.id, setLogin = false) }
            coVerify(exactly = 1) {
                CancellableAutoMediaItemMapper.mapSongToMediaItem(local, AutoSessionConstants.ID_QUICK_PICKS, true)
            }
        }
    }

    // JUnit is synchronous; bridge the gated provider without sleeping or creating an unowned scope.
    @Test(timeout = 15_000)
    fun `physical connectivity loss during provider fetch drops even eligible local discovery before artwork`() = runBlocking {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            coEvery { Innertube.relatedPage(videoId = local.id, setLogin = false) } coAnswers {
                assertTrue(NetworkQualityHelper.isNetworkConnected(context))
                entered.complete(Unit)
                release.await()
                emptyRelatedPage()
            }
            val result = async { fetch() }
            entered.await()
            assertFalse(result.isCompleted)
            setPhysicalOnline(false)
            assertFalse(NetworkQualityHelper.isNetworkConnected(context))
            assertLocalStillEligibleOffline()
            release.complete(Unit)
            assertTrue(result.await().isEmpty())
            coVerify(exactly = 1) { Innertube.relatedPage(videoId = local.id, setLogin = false) }
            coVerify(exactly = 0) {
                CancellableAutoMediaItemMapper.mapSongToMediaItem(any(), any<String>(), any())
            }
        }
    }

    // JUnit is synchronous; bridge the gated artwork mapper with structured, bounded cancellation.
    @Test(timeout = 15_000)
    fun `forced offline during local artwork mapping drops discovery despite physical connectivity`() = runBlocking {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            coEvery {
                CancellableAutoMediaItemMapper.mapSongToMediaItem(local, AutoSessionConstants.ID_QUICK_PICKS, true)
            } coAnswers {
                assertTrue(NetworkQualityHelper.isNetworkConnected(context))
                entered.complete(Unit)
                release.await()
                localItem
            }
            val result = async { fetch() }
            entered.await()
            assertFalse(result.isCompleted)
            prefs.edit().putBoolean(fullOfflineModeKey, true).commit()
            assertFalse(NetworkQualityHelper.isNetworkConnected(context))
            assertLocalStillEligibleOffline()
            release.complete(Unit)
            assertTrue(result.await().isEmpty())
            prefs.edit().remove(fullOfflineModeKey).commit()
            assertTrue(NetworkQualityHelper.isNetworkConnected(context))
            coVerify(exactly = 1) {
                CancellableAutoMediaItemMapper.mapSongToMediaItem(local, AutoSessionConstants.ID_QUICK_PICKS, true)
            }
        }
    }
}
