/** Metrolist Project (C) 2026 — Licensed under GPL-3.0 | See git history for contributors */

package app.n_zik.android.listentogether

import androidx.annotation.StringRes
import app.n_zik.android.R

data class ListenTogetherServer(
    val name: String,
    val url: String,
    @StringRes val locationRes: Int,
    val operator: String
)

object ListenTogetherServers {
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
}
