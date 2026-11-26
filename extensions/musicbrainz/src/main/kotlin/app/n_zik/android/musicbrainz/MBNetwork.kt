package app.n_zik.android.musicbrainz

import java.net.Proxy

/**
 * Optional network configuration for the musicbrainz module.
 * The Android app sets [proxy] from the user's proxy preferences
 * (the module itself is JVM-only and cannot read SharedPreferences).
 */
object MBNetwork {
    @Volatile
    var proxy: Proxy? = null
}

/** Retires a client after the last request using it has consumed its response. */
internal class ProxyBoundClient<T>(private val create: (Proxy?) -> T, private val close: (T) -> Unit) {
    private class Client<T>(val proxy: Proxy?, val value: T, var users: Int = 0)

    private var current: Client<T>? = null

    private fun acquire(): Client<T> = synchronized(this) {
        val proxy = MBNetwork.proxy
        val previous = current
        val client = if (previous != null && previous.proxy == proxy) {
            previous
        } else {
            Client(proxy, create(proxy)).also { replacement ->
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
