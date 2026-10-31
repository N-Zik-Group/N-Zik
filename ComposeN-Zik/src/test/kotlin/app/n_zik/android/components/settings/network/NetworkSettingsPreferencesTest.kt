package app.n_zik.android.components.settings.network

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NetworkSettingsPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun store(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Before fun setUp() {
        listOf("preferences", "preferences_network-test", "preferences_untouched", "app_settings").forEach {
            store(it).edit().clear().commit()
        }
        setActiveProfile("network-test", context)
    }
    @After fun tearDown() { setActiveProfile("default", context) }

    private val clients = listOf("web_remix", "android_vr", "visionos", "tv_embedded", "tv_simply", "tv_html5", "android_creator", "android", "ios", "ipados", "web", "web_creator", "mobile")
    private val defaults: Map<String, Any> = mapOf(
        "isConnectionMeteredEnabled" to false,
        "autoDownloadSong" to false,
        "autoDownloadSongWhenLiked" to false,
        "autoDownloadSongWhenAlbumBookmarked" to false,
        "audioQualityFormat" to "Auto",
        "downloadQualityFormat" to "Auto",
        "imageQualityFormat" to "Auto",
        "fullOfflineModeEnabled" to false,
        "stream_client_restart_needed" to false,
        "stream_client_web_remix" to true,
        "stream_client_android_vr" to true,
    )

    @Test fun `reset preserves the original profile and legacy client store split`() {
        val editor = context.preferences.edit()
        defaults.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, !value)
                is String -> editor.putString(key, "changed")
            }
        }
        editor.putString("navigationBarPosition", "Left").putString("unrelated", "keep").commit()
        store("preferences_untouched").edit().putString("audioQualityFormat", "High").commit()
        val legacyEditor = store("preferences").edit()
        clients.forEach { legacyEditor.putBoolean("stream_client_$it", false) }
        legacyEditor.putString("streamClientsOrder", "[]")
            .putString("preferred_stream_client", "changed")
            .putString("legacy-unrelated", "keep").commit()
        val globalBefore = store("app_settings").all.toMap()
        applyNetworkDefaults(context)
        defaults.forEach { (key, expected) -> assertEquals(key, expected, context.preferences.all[key]) }
        clients.forEach { assertEquals(it, true, store("preferences").all["stream_client_$it"]) }
        assertEquals(clients.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }, store("preferences").getString("streamClientsOrder", null))
        assertEquals(false, context.preferences.getBoolean("fullOfflineModeEnabled", true))
        assertEquals("Left", context.preferences.getString("navigationBarPosition", null))
        assertEquals("keep", context.preferences.getString("unrelated", null))
        assertEquals("High", store("preferences_untouched").getString("audioQualityFormat", null))
        assertEquals(globalBefore, store("app_settings").all)
        assertEquals("WEB_REMIX", store("preferences").getString("preferred_stream_client", null))
        assertEquals("keep", store("preferences").getString("legacy-unrelated", null))
        assertTrue(!context.preferences.contains("streamClientsOrder"))
        assertTrue(!context.preferences.contains("preferred_stream_client"))
        val legacyFirst = store("preferences").all.toMap()
        val first = context.preferences.all.toMap()
        applyNetworkDefaults(context)
        assertEquals(first, context.preferences.all)
        assertEquals(legacyFirst, store("preferences").all)
    }

    @Test fun `stable targets preserve card order and focus aliases`() {
        assertEquals(listOf("server", "status", "quality", "connection", "offline", "download", "reset"), NetworkSettingTargets.cards.map { it.id.removePrefix("network.card.") })
        assertEquals(listOf("pcserver", "offline"), NetworkSettingTargets.cards.mapNotNull { it.focusId })
        assertEquals(NetworkSettingTargets.ids.size, NetworkSettingTargets.ids.distinct().size)
        assertTrue(NetworkSettingTargets.ids.contains("network.card.offline.setting.fullOfflineModeEnabled"))
    }
}
