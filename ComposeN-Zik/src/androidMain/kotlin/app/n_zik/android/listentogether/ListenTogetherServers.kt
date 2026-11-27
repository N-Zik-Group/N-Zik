/** Metrolist Project (C) 2026 — Licensed under GPL-3.0 | See git history for contributors */

package app.n_zik.android.listentogether

import androidx.annotation.StringRes
import app.n_zik.android.R
import okhttp3.HttpUrl
import okhttp3.Request

data class ListenTogetherServer(
    val name: String,
    val url: String,
    @StringRes val locationRes: Int,
    val operator: String
)

object ListenTogetherServers {
    private const val LEGACY_SERVER_HOST = "metroserver.meowery.eu"

    val servers: List<ListenTogetherServer> = listOf(
        ListenTogetherServer(
            name = "The Meowery",
            url = "wss://metroserverx.meowery.eu/ws",
            locationRes = R.string.listen_together_server_location_poland,
            operator = "Nyx",
        ),
    )

    val defaultServerUrl: String
        get() = servers.first().url

    fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }

    internal fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return defaultServerUrl
        return migrateLegacyHost(trimmed, parseUrl(trimmed)?.host)
    }

    internal fun customUrlForSave(input: String): String? {
        val url = input.trim()
        val parsed = parseUrl(url) ?: return null
        return migrateLegacyHost(url, parsed.host)
    }

    internal fun connectionUrl(configuredUrl: String): HttpUrl? = parseUrl(normalizeUrl(configuredUrl))

    private fun migrateLegacyHost(url: String, host: String?): String =
        if (host == LEGACY_SERVER_HOST) defaultServerUrl else url

    private fun parseUrl(url: String): HttpUrl? =
        runCatching { Request.Builder().url(url).build().url }.getOrNull()
}
