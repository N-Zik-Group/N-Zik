package app.n_zik.android.extensions.youtubelogin

import android.app.Application
import android.content.Context

import app.n_zik.android.utils.ytCookieKey
import app.n_zik.android.utils.ytDataSyncIdKey
import app.n_zik.android.core.network.client.Store
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.AccountInfo
import app.n_zik.innertube.parseYouTubeChannels
import app.n_zik.innertube.YouTubeSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class YouTubeSessionPreferencesTest {
    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `discovery keeps a shared channel in its first Google account and preserves add order`() {
        val prefs = context.getSharedPreferences("browser-channel-ownership", Context.MODE_PRIVATE)
        val first = newYouTubeBrowserProfileName()
        val second = newYouTubeBrowserProfileName()
        val firstChannels = parseYouTubeChannels("""
            {"accountItem":{"accountName":{"simpleText":"Original"},"channelHandle":{"simpleText":"@original"}}}
        """)
        val secondChannels = parseYouTubeChannels("""
            {"choices":[
              {"accountItem":{"accountName":{"simpleText":"New"},"channelHandle":{"simpleText":"@new"}}},
              {"accountItem":{"accountName":{"simpleText":"Original"},"channelHandle":{"simpleText":"@ORIGINAL"}}}
            ]}
        """)
        rememberYouTubeBrowserAccount(prefs, first, firstChannels)
        val roster = rememberYouTubeBrowserAccount(prefs, second, secondChannels)
        assertEquals(listOf(first, second), roster.map { it.profileName })
        assertEquals(listOf("@original"), roster[0].channels.map { it.handle })
        assertEquals(listOf("@new"), roster.uniqueDiscoveredChannels(second, secondChannels).map { it.handle })
        assertEquals(listOf("@new"), roster[1].channels.map { it.handle })
        val refreshed = rememberYouTubeBrowserAccount(prefs, first, firstChannels)
        assertEquals(listOf(first, second), refreshed.map { it.profileName })
    }

    @Test
    fun `discovered channels persist before an account is selected`() {
        val prefs = context.getSharedPreferences("discovered-browser-accounts", Context.MODE_PRIVATE)
        val first = newYouTubeBrowserProfileName()
        val second = newYouTubeBrowserProfileName()
        val channel = parseYouTubeChannels("""
            {"accountItem":{"accountName":{"simpleText":"Second channel"},"channelHandle":{"simpleText":"@second"}}}
        """)
        prefs.edit().apply {
            putYouTubeBrowserAccount(first, AccountInfo("First", "first@example.org", null, null), emptyList())
        }.apply()
        rememberYouTubeBrowserAccount(prefs, second, channel)

        val roster = readYouTubeBrowserAccounts(prefs)
        assertEquals(listOf(first, second), roster.map { it.profileName })
        assertEquals("@second", roster[1].channels.single().handle)
        assertEquals(first, readActiveYouTubeBrowserAccount(prefs))
    }

    @Test
    fun `adding and forgetting one browser account preserves the others`() {
        val prefs = context.getSharedPreferences("browser-accounts", Context.MODE_PRIVATE)
        val first = newYouTubeBrowserProfileName()
        val second = newYouTubeBrowserProfileName()
        prefs.edit().apply {
            putYouTubeBrowserAccount(first, AccountInfo("First", "first@example.org", null, null), emptyList())
        }.apply()
        prefs.edit().apply {
            putYouTubeBrowserAccount(second, AccountInfo("Second", "second@example.org", null, null), readYouTubeBrowserAccounts(prefs))
        }.apply()

        assertEquals(listOf(first, second), readYouTubeBrowserAccounts(prefs).map { it.profileName })
        assertEquals(second, readActiveYouTubeBrowserAccount(prefs))
        removeYouTubeBrowserAccount(prefs, first)
        assertEquals(listOf(second), readYouTubeBrowserAccounts(prefs).map { it.profileName })
        assertEquals(second, readActiveYouTubeBrowserAccount(prefs))
    }

    @Test
    fun `replacement persists selected channel and leaves other profile isolated`() {
        val personal = context.getSharedPreferences("personal", Context.MODE_PRIVATE)
        val other = context.getSharedPreferences("other", Context.MODE_PRIVATE)
        val first = YouTubeSession("SAPISID=fixture", "visitor-1", "channel-1", "0")
        val second = YouTubeSession("SAPISID=fixture", "visitor-2", "channel-2", "2")
        personal.edit().apply { putYouTubeSession(first) }.apply()
        personal.edit().apply { putYouTubeSession(second) }.apply()

        assertEquals(second, context.getSharedPreferences("personal", Context.MODE_PRIVATE).readYouTubeSession())
        assertEquals(YouTubeSession(null, null, null, null), other.readYouTubeSession())

        val initialRuntime = Innertube.youtubeSessionSnapshot()
        try {
            Store.setSession(personal.readYouTubeSession())
            assertEquals(second, Innertube.youtubeSessionSnapshot())
            Store.setSession(other.readYouTubeSession())
            assertNull(Innertube.youtubeSessionSnapshot().cookie)
            assertNull(Innertube.youtubeSessionSnapshot().dataSyncId)
        } finally {
            Store.setSession(initialRuntime)
        }
    }

    @Test
    fun `legacy credentials restore without an explicit account index`() {
        val prefs = context.getSharedPreferences("legacy", Context.MODE_PRIVATE)
        prefs.edit().putString(ytCookieKey, "SAPISID=fixture").putString(ytDataSyncIdKey, "legacy-channel").apply()

        val restored = prefs.readYouTubeSession()
        assertEquals("SAPISID=fixture", restored.cookie)
        assertEquals("legacy-channel", restored.dataSyncId)
        assertNull(restored.authUser)
    }

    @Test
    fun `removing the cookie also drops orphaned channel routing`() {
        val prefs = context.getSharedPreferences("signed-out", Context.MODE_PRIVATE)
        prefs.edit().apply { putYouTubeSession(YouTubeSession("SAPISID=fixture", null, "channel", "2")) }.apply()
        prefs.edit().remove(ytCookieKey).apply()

        assertEquals(YouTubeSession(null, null, null, null), prefs.readYouTubeSession())
    }
}
