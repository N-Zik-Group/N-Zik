package app.n_zik.android.listentogether

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ListenTogetherServerSettingsTest {
    @Test
    fun `custom server accepts the connection schemes and preserves the endpoint`() {
        for (scheme in listOf("ws", "wss", "http", "https")) {
            val url = "$scheme://example.com:8443/rooms"
            assertEquals(url, ListenTogetherServers.customUrlForSave("  $url  "))
        }
        assertEquals(
            ListenTogetherServers.defaultServerUrl,
            ListenTogetherServers.customUrlForSave("wss://metroserver.meowery.eu/ws"),
        )
    }

    @Test
    fun `migration matches only the legacy server host`() {
        val legacy = "wss://METROSERVER.MEOWERY.EU/ws"
        assertEquals(ListenTogetherServers.defaultServerUrl, ListenTogetherServers.normalizeUrl(legacy))
        assertEquals(ListenTogetherServers.defaultServerUrl, ListenTogetherServers.customUrlForSave(legacy))

        for (url in listOf(
            "wss://example.com/metroserver.meowery.eu",
            "https://example.com/ws?relay=metroserver.meowery.eu",
            "ws://metroserver.meowery.eu.example.com/ws",
        )) {
            assertEquals(url, ListenTogetherServers.normalizeUrl(url))
            assertEquals(url, ListenTogetherServers.customUrlForSave(url))
        }
    }

    @Test
    fun `custom server rejects malformed addresses before normalization`() {
        for (input in listOf("", "abc", "wss://", "ftp://example.com", "invalid metroserver.meowery.eu")) {
            assertNull(ListenTogetherServers.customUrlForSave(input), input)
        }
    }
}
