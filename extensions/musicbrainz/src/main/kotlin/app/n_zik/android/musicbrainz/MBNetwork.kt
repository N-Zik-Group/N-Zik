package app.n_zik.android.musicbrainz

import java.net.Proxy
import okhttp3.Authenticator

/**
 * Optional network configuration for the musicbrainz module.
 * The Android app sets [proxy] from the user's proxy preferences
 * (the module itself is JVM-only and cannot read SharedPreferences).
 */
object MBNetwork {
    internal data class ProxyConfiguration(val proxy: Proxy?, val authenticator: Authenticator)

    @Volatile
    internal var proxyConfiguration = ProxyConfiguration(null, Authenticator.NONE)
        private set

    var proxy: Proxy?
        get() = proxyConfiguration.proxy
        set(value) = configureProxy(value, Authenticator.NONE)

    fun configureProxy(proxy: Proxy?, authenticator: Authenticator) {
        proxyConfiguration = ProxyConfiguration(proxy, authenticator)
    }
}

/** Retires a client after the last request using it has consumed its response. */
internal class ProxyBoundClient<T>(private val create: (MBNetwork.ProxyConfiguration) -> T, private val close: (T) -> Unit) {
    private class Client<T>(val configuration: MBNetwork.ProxyConfiguration, val value: T, var users: Int = 0)

    private var current: Client<T>? = null

    private fun acquire(): Client<T> = synchronized(this) {
        val configuration = MBNetwork.proxyConfiguration
        val previous = current
        val client = if (previous != null && previous.configuration == configuration) {
            previous
        } else {
            Client(configuration, create(configuration)).also { replacement ->
                current = replacement
                if (previous != null && previous.users == 0) close(previous.value)
            }
        }
        client.users++
        client
    }

    suspend fun <R> use(block: suspend (T) -> R): R {
        val client = acquire()
        try {
            return block(client.value)
        } finally {
            synchronized(this) {
                client.users--
                if (client !== current && client.users == 0) close(client.value)
            }
        }
    }
}
