package app.n_zik.android.core.network.client

import android.content.Context
import android.content.SharedPreferences
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.settings.readProxyPort
import app.n_zik.android.core.settings.validProxyPorts
import app.n_zik.android.musicbrainz.MBNetwork
import app.n_zik.android.utils.encryptedPreferencesForProfile
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.isProxyEnabledKey
import app.n_zik.android.utils.proxyHostnameKey
import app.n_zik.android.utils.proxyModeKey
import app.n_zik.android.utils.proxyPasswordEncryptedKey
import app.n_zik.android.utils.proxyPasswordKey
import app.n_zik.android.utils.proxyUsernameKey
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber

internal enum class ProfileProxyError { InvalidPort, InvalidHostname }

/** Reapplies the active profile's proxy after startup, a profile switch or a settings change. */
@Synchronized
internal fun applyProfileProxy(context: Context): ProfileProxyError? {
    val profileId = getActiveProfile(context)
    val preferences = profileDataPrefs(context, profileId)
    val enabled = preferences.getBoolean(isProxyEnabledKey, false)
    val mode = preferences.getEnum(proxyModeKey, Proxy.Type.HTTP)
    val host = preferences.getString(proxyHostnameKey, "").orEmpty()
    val port = preferences.readProxyPort()
    val proxy = if (enabled) profileProxyEndpoint(mode, host, port) else null
    val username = preferences.getString(proxyUsernameKey, "").orEmpty()
    val needsAuthentication = proxy?.type() == Proxy.Type.HTTP && username.isNotBlank()
    val password = if (needsAuthentication || preferences.contains(proxyPasswordKey)) {
        runCatching { context.encryptedPreferencesForProfile(profileId) }.getOrNull()?.let { encrypted ->
            readAndMigrateProxyPassword(preferences, encrypted)
        }
    } else null
    val authenticator = if (needsAuthentication && !password.isNullOrBlank()) {
        HttpProxyAuthenticator(checkNotNull(proxy), Credentials.basic(username, password))
    } else Authenticator.NONE

    if (getActiveProfile(context) != profileId) return null
    NetworkClientFactory.configure(
        proxy = proxy,
        cacheDir = NetworkClientFactory.profileCacheDirectory(context, profileId),
        proxyAuthenticator = authenticator,
    )
    MBNetwork.configureProxy(proxy, authenticator)
    if (enabled && proxy == null) {
        Timber.tag("ProfileProxy").w("Invalid proxy endpoint")
        return if (port in validProxyPorts) ProfileProxyError.InvalidHostname else ProfileProxyError.InvalidPort
    }
    return null
}

internal fun profileProxyEndpoint(mode: Proxy.Type, host: String, port: Int): Proxy? {
    if (mode == Proxy.Type.DIRECT) return Proxy.NO_PROXY
    if (port !in validProxyPorts) return null
    val canonicalHost = runCatching {
        HttpUrl.Builder().scheme("http").host(host.trim()).build().host
    }.getOrNull() ?: return null
    return Proxy(mode, InetSocketAddress.createUnresolved(canonicalHost, port))
}

internal fun readAndMigrateProxyPassword(
    preferences: SharedPreferences,
    encrypted: SharedPreferences,
): String? {
    val current = encrypted.getString(proxyPasswordEncryptedKey, null)
    val legacy = preferences.getString(proxyPasswordKey, null) ?: return current
    if (current == null && legacy.isNotEmpty() &&
        !encrypted.edit().putString(proxyPasswordEncryptedKey, legacy).commit()
    ) {
        Timber.tag("ProfileProxy").w("Could not persist migrated proxy credentials")
        return null
    }
    if (!preferences.edit().remove(proxyPasswordKey).commit()) {
        Timber.tag("ProfileProxy").w("Could not remove legacy proxy credentials")
    }
    return current ?: legacy
}

internal class HttpProxyAuthenticator(
    private val proxy: Proxy,
    private val authorization: String,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (route?.proxy != proxy || response.code != 407 ||
            response.request.header("Proxy-Authorization") != null ||
            response.challenges().none { it.scheme.equals("Basic", ignoreCase = true) }
        ) return null
        return response.request.newBuilder().header("Proxy-Authorization", authorization).build()
    }

    override fun equals(other: Any?): Boolean =
        other is HttpProxyAuthenticator && proxy == other.proxy && authorization == other.authorization

    override fun hashCode(): Int = 31 * proxy.hashCode() + authorization.hashCode()
}
