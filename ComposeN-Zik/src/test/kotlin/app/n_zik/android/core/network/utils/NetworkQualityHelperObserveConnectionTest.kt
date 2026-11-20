package app.n_zik.android.core.network.utils

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewContext
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.profilePreferences
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Robolectric tests for the [NetworkQualityHelper.observeConnection] emission
 * contract: the flow must track the VALIDATED connectivity state, never a raw
 * "network present" signal. [ConnectivityManager.NetworkCallback.onAvailable]
 * fires before NET_CAPABILITY_VALIDATED is set (captive portals), so it must
 * re-read the validated state instead of emitting a raw true.
 *
 * Robolectric's shadow [ConnectivityManager] does not re-fire callbacks on
 * capability changes, so the tests flip the shadow capabilities and then fire
 * the registered callback explicitly — the same callback the production flow
 * registers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NetworkQualityHelperObserveConnectionTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    private val cm: ConnectivityManager
        get() = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val activeNetwork: Network
        get() = checkNotNull(cm.activeNetwork) { "the shadow must hold a default active network" }

    /**
     * Swaps the active network's capabilities in the shadow (the same pattern
     * as `NetworkGateTest.setOnline`). Returns the capabilities so the test can
     * hand them to the callback it fires.
     */
    private fun setCapabilities(validated: Boolean): NetworkCapabilities {
        val caps = ShadowNetworkCapabilities.newInstance()
        val shadowCaps = shadowOf(caps)
        shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) {
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
        return caps
    }

    private fun registeredCallback(): ConnectivityManager.NetworkCallback =
        checkNotNull(shadowOf(cm).getNetworkCallbacks().firstOrNull()) {
            "no network callback registered — the flow was not collected"
        }

    /**
     * Collects [NetworkQualityHelper.observeConnection] and drives the scenario.
     * runBlocking is forced by the synchronous test harness (AGENTS.md
     * runBlocking exception); the collector coroutine shares its event loop
     * with the scenario, so settling must use `delay` — a Thread.sleep would
     * block the very loop the collector resumes on.
     */
    private fun <T> runObserving(block: suspend (emissions: MutableList<Boolean>) -> T): T = runBlocking {
        val emissions = mutableListOf<Boolean>()
        val collector = launch {
            NetworkQualityHelper.observeConnection(app).collect { emissions.add(it) }
        }
        // Bounded wait: a regression that drops the initial emission must fail
        // the test, not hang the task.
        val deadline = System.currentTimeMillis() + 5_000
        while (emissions.isEmpty() && System.currentTimeMillis() < deadline) {
            delay(25)
        }
        assertTrue("initial emission not received within 5000ms", emissions.isNotEmpty())
        try {
            block(emissions)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `onAvailable on an unvalidated network emits the validated state, not a raw true`() {
        runObserving { emissions ->
            // The default shadow network exposes no INTERNET capability, so the
            // initial emission is offline.
            assertEquals(listOf(false), emissions)
            setCapabilities(validated = false) // INTERNET present, VALIDATED absent
            registeredCallback().onAvailable(activeNetwork)
            delay(300) // settle: let any (wrong) emission be delivered
            assertEquals(
                "an unvalidated network must stay offline (no raw true on onAvailable)",
                listOf(false),
                emissions,
            )
        }
    }

    @Test
    fun `network becomes online only once the capabilities are validated`() {
        runObserving { emissions ->
            assertEquals(listOf(false), emissions)
            // The production sequence: the network appears (onAvailable, before
            // validation)...
            setCapabilities(validated = false)
            registeredCallback().onAvailable(activeNetwork)
            delay(300)
            assertEquals("pre-validation the flow must stay offline", listOf(false), emissions)
            // ...then validation completes (onCapabilitiesChanged).
            val validated = setCapabilities(validated = true)
            registeredCallback().onCapabilitiesChanged(activeNetwork, validated)
            val deadline = System.currentTimeMillis() + 5_000
            while (emissions.size < 2 && System.currentTimeMillis() < deadline) {
                delay(25)
            }
            assertTrue("validated emission not received within 5000ms", emissions.size >= 2)
            assertEquals(listOf(false, true), emissions)
        }
    }

    @Test
    fun `app collectors share a callback and restart with the current state`() = runBlocking {
        // runBlocking bridges the synchronous Robolectric harness to Flow collection.
        val profiles = app.profilePreferences
        val originalProfile = profiles.getString(activeProfileKey, null)
        profiles.edit().putString(activeProfileKey, "network-observer-offline").commit()
        val offlinePrefs = app.preferences
        offlinePrefs.edit().putBoolean(fullOfflineModeKey, true).commit()
        profiles.edit().putString(activeProfileKey, "network-observer-online").commit()
        setCapabilities(validated = true)

        val firstValues = Channel<Boolean>(Channel.UNLIMITED)
        val secondValues = Channel<Boolean>(Channel.UNLIMITED)
        val first = launch { NetworkQualityHelper.observeConnection(app).collect { firstValues.send(it) } }
        val second = launch {
            NetworkQualityHelper.observeConnection(ContextWrapper(app)).collect { secondValues.send(it) }
        }
        try {
            withTimeout(5_000) {
                assertEquals(true, firstValues.receive())
                assertEquals(true, secondValues.receive())
                assertEquals(1, shadowOf(cm).getNetworkCallbacks().size)

                profiles.edit().putString(activeProfileKey, "network-observer-offline").commit()
                assertEquals(false, firstValues.receive())
                assertEquals(false, secondValues.receive())

                first.cancelAndJoin()
                assertEquals(1, shadowOf(cm).getNetworkCallbacks().size)
                offlinePrefs.edit().putBoolean(fullOfflineModeKey, false).commit()
                assertEquals(true, secondValues.receive())

                second.cancelAndJoin()
                assertTrue(shadowOf(cm).getNetworkCallbacks().isEmpty())
                setCapabilities(validated = false)
                assertEquals(false, NetworkQualityHelper.observeConnection(app).first())
                assertTrue(shadowOf(cm).getNetworkCallbacks().isEmpty())
            }
        } finally {
            first.cancelAndJoin()
            second.cancelAndJoin()
            offlinePrefs.edit().remove(fullOfflineModeKey).commit()
            profiles.edit().putString(activeProfileKey, originalProfile).commit()
        }
    }

    @Test
    fun `preview preferences and overridden services stay local to their contexts`() = runBlocking {
        // runBlocking bridges the synchronous Robolectric harness to Flow collection.
        setCapabilities(validated = true)
        val prefs = app.getSharedPreferences("network-observer-preview", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(fullOfflineModeKey, true).commit()
        val preview = ThemePreviewContext(app, prefs)
        val withoutConnectivity = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? =
                if (name == Context.CONNECTIVITY_SERVICE) null else super.getSystemService(name)
        }
        val values = Channel<Boolean>(Channel.UNLIMITED)
        val collector = launch { NetworkQualityHelper.observeConnection(app).collect { values.send(it) } }
        try {
            withTimeout(5_000) {
                assertEquals(true, values.receive())
                assertEquals(false, NetworkQualityHelper.observeConnection(preview).first())
                assertEquals(false, NetworkQualityHelper.observeConnection(withoutConnectivity).first())
                assertEquals(1, shadowOf(cm).getNetworkCallbacks().size)
            }
        } finally {
            collector.cancelAndJoin()
            prefs.edit().clear().commit()
        }
    }
}
