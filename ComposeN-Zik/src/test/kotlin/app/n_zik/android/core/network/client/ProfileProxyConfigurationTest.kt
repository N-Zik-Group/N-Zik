package app.n_zik.android.core.network.client

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.proxyPasswordEncryptedKey
import app.n_zik.android.utils.proxyPasswordKey
import io.mockk.every
import io.mockk.mockk
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ProfileProxyConfigurationTest {
    @Test
    fun `legacy password migrates from the right profile without replacing an encrypted choice`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plain = context.getSharedPreferences("profile-test-plain", Context.MODE_PRIVATE)
        val encrypted = context.getSharedPreferences("profile-test-encrypted", Context.MODE_PRIVATE)
        val otherProfile = context.getSharedPreferences("other-profile-test-plain", Context.MODE_PRIVATE)
        plain.edit().putString(proxyPasswordKey, "old-test-value").commit()
        otherProfile.edit().putString(proxyPasswordKey, "other-test-value").commit()

        assertEquals("old-test-value", readAndMigrateProxyPassword(plain, encrypted))
        assertEquals("old-test-value", encrypted.getString(proxyPasswordEncryptedKey, null))
        assertFalse(plain.contains(proxyPasswordKey))
        assertEquals("other-test-value", otherProfile.getString(proxyPasswordKey, null))

        encrypted.edit().putString(proxyPasswordEncryptedKey, "").commit()
        plain.edit().putString(proxyPasswordKey, "stale-test-value").commit()
        assertEquals("", readAndMigrateProxyPassword(plain, encrypted))
        assertFalse(plain.contains(proxyPasswordKey))
    }

    @Test
    fun `proxy endpoint accepts DNS and leaves resolution to the client`() {
        val configured = checkNotNull(profileProxyEndpoint(Proxy.Type.HTTP, " Proxy.Example ", 3128))
        assertEquals(InetSocketAddress.createUnresolved("proxy.example", 3128), configured.address())
        assertNull(profileProxyEndpoint(Proxy.Type.HTTP, "https://proxy.example", 3128))
        assertNull(profileProxyEndpoint(Proxy.Type.SOCKS, "proxy.example", 65536))
        assertSame(Proxy.NO_PROXY, profileProxyEndpoint(Proxy.Type.DIRECT, "", 0))
    }

    @Test
    fun `only a matching proxy challenge carries credentials and it is not retried`() {
        val endpoint = checkNotNull(profileProxyEndpoint(Proxy.Type.HTTP, "proxy.example", 3128))
        val route = mockk<Route> { every { proxy } returns endpoint }
        val unrelatedRoute = mockk<Route> { every { proxy } returns Proxy.NO_PROXY }
        val authenticator = HttpProxyAuthenticator(endpoint, "Basic dGVzdA==")
        val initial = Request.Builder().url("https://origin.example").build()
        val challenge = Response.Builder()
            .request(initial)
            .protocol(Protocol.HTTP_1_1)
            .code(407)
            .message("Proxy authentication required")
            .header("Proxy-Authenticate", "Basic realm=\"proxy\"")
            .build()

        val authenticated = checkNotNull(authenticator.authenticate(route, challenge))
        assertEquals("Basic dGVzdA==", authenticated.header("Proxy-Authorization"))
        assertNull(initial.header("Proxy-Authorization"))
        assertNull(authenticator.authenticate(unrelatedRoute, challenge))
        assertNull(authenticator.authenticate(route, challenge.newBuilder().request(authenticated).build()))
        assertNull(authenticator.authenticate(route, challenge.newBuilder().code(401).build()))
    }
}
