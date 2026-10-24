package app.n_zik.android.core.network

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.core.network.client.NetworkGate
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/**
 * Robolectric tests for the per-profile "full offline mode" flag (spec-full-offline-mode):
 * the 4-state matrix of mode x real connectivity through both predicate read sites
 * ([NetworkQualityHelper.isNetworkConnected] and [NetworkGate.isOnline]), plus the live
 * behavior of the [OfflineMode.enabledFlow] / [NetworkQualityHelper.observeConnection]
 * composition when the toggle flips at runtime.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OfflineModeTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setMode(modeOn: Boolean) {
        app.preferences.edit().putBoolean(fullOfflineModeKey, modeOn).apply()
    }

    /**
     * Same shadow pattern as NetworkGateTest: the default shadow state holds an active
     * MOBILE network whose capabilities only expose TRANSPORT_CELLULAR (offline); swap
     * those capabilities to flip connectivity.
     */
    private fun setOnline(online: Boolean) {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = shadowOf(cm)
        val network = checkNotNull(cm.activeNetwork)
        val caps = ShadowNetworkCapabilities.newInstance()
        val shadowCaps = shadowOf(caps)
        if (online) {
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        }
        shadow.setNetworkCapabilities(network, caps)
    }

    @Test
    fun `enabled defaults to off on a fresh profile`() {
        assertFalse(OfflineMode.enabled(app))
    }

    @Test
    fun `enabled reflects the written toggle`() {
        setMode(true)
        assertTrue(OfflineMode.enabled(app))
        setMode(false)
        assertFalse(OfflineMode.enabled(app))
    }

    @Test
    fun `predicate matrix - mode off + network online is online`() {
        setMode(false)
        setOnline(true)
        assertTrue(NetworkQualityHelper.isNetworkConnected(app))
        assertTrue(NetworkGate.isOnline())
    }

    @Test
    fun `predicate matrix - mode off + network offline is offline`() {
        setMode(false)
        setOnline(false)
        assertFalse(NetworkQualityHelper.isNetworkConnected(app))
        assertFalse(NetworkGate.isOnline())
    }

    @Test
    fun `predicate matrix - mode on + network online is effectively offline`() {
        setMode(true)
        setOnline(true)
        assertFalse(NetworkQualityHelper.isNetworkConnected(app))
        assertFalse(NetworkGate.isOnline())
    }

    @Test
    fun `predicate matrix - mode on + network offline is offline`() {
        setMode(true)
        setOnline(false)
        assertFalse(NetworkQualityHelper.isNetworkConnected(app))
        assertFalse(NetworkGate.isOnline())
    }

    @Test
    fun `enabledFlow emits the initial value and reacts to a live flip`() {
        setMode(false)
        setOnline(true)
        val values = mutableListOf<Boolean>()
        runBlocking {
            val collector = launch {
                OfflineMode.enabledFlow(app).collect { values.add(it) }
            }
            withTimeout(5_000) { while (values.size < 1) delay(10) }
            setMode(true)
            withTimeout(5_000) { while (values.size < 2) delay(10) }
            collector.cancel()
        }
        assertEquals(listOf(false, true), values)
    }

    @Test
    fun `observeConnection flips to offline live when the mode turns on`() {
        setMode(false)
        setOnline(true)
        val values = mutableListOf<Boolean>()
        runBlocking {
            val collector = launch {
                NetworkQualityHelper.observeConnection(app).collect { values.add(it) }
            }
            withTimeout(5_000) { while (values.isEmpty()) delay(10) }
            setMode(true)
            withTimeout(5_000) { while (values.size < 2) delay(10) }
            collector.cancel()
        }
        assertEquals(listOf(true, false), values)
    }
}
