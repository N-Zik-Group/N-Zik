package app.n_zik.android.core.network.client

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.MainApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the root [NetworkGate] interceptor (spec-offline-load-state):
 * offline (shadow [ConnectivityManager] without capabilities) short-circuits the request
 * with a synthetic 503 without ever calling [Interceptor.Chain.proceed]; online
 * (INTERNET + VALIDATED) the request proceeds to the network.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NetworkGateTest {

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

    /**
     * Drives the real [NetworkQualityHelper.isNetworkConnected] read through Robolectric's
     * shadow [ConnectivityManager]: offline = no active network; online = a WIFI network
     * exposing the INTERNET + VALIDATED capabilities.
     */
    private fun setOnline(online: Boolean) {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadow = shadowOf(cm)
        if (online) {
            val info = NetworkInfo()
            info.type = ConnectivityManager.TYPE_WIFI
            info.typeName = "Wi-Fi"
            shadow.setActiveNetworkInfo(info)
            val caps = NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()
            val network = checkNotNull(cm.activeNetwork)
            shadow.setNetworkCapabilities(network, caps)
        } else {
            shadow.setActiveNetworkInfo(null)
        }
    }

    private fun gateRequest() = Request.Builder().url("https://example.invalid/api").build()

    @Test
    fun `offline - request is short-circuited with a synthetic 503 and never proceeds`() {
        setOnline(false)
        val request = gateRequest()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request

        val response = NetworkGate.interceptor.intercept(chain)

        assertEquals(503, response.code)
        assertEquals("No network connection", response.message)
        assertEquals(request, response.request)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `online - request proceeds to the network and the upstream response is returned`() {
        setOnline(true)
        val request = gateRequest()
        val upstream = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns upstream

        val response = NetworkGate.interceptor.intercept(chain)

        verify(exactly = 1) { chain.proceed(request) }
        assertSame(upstream, response)
    }
}
