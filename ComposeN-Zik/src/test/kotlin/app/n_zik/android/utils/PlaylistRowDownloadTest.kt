package app.n_zik.android.utils

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.media3.common.MediaItem
import androidx.media3.datasource.cache.Cache
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.core.utils.Toaster
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlaylistRowDownloadTest {
    @Test
    fun `swiping to download in full offline mode preserves the playback cache`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = checkNotNull(connectivity.activeNetwork)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        with(shadowOf(capabilities)) {
            addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        shadowOf(connectivity).setNetworkCapabilities(activeNetwork, capabilities)

        val song = MediaItem.Builder().setMediaId("abc123def45").build()
        val cache = mockk<Cache>(relaxed = true)
        mockkObject(Toaster)
        try {
            every { Toaster.noInternet() } just Runs
            app.preferences.edit().putBoolean(fullOfflineModeKey, true).commit()
            startPlaylistRowDownload(app, song, cache)
            verify(exactly = 1) { Toaster.noInternet() }
            verify(exactly = 0) { cache.removeResource(song.mediaId) }
        } finally {
            app.preferences.edit().remove(fullOfflineModeKey).commit()
            unmockkObject(Toaster)
        }
    }
}
