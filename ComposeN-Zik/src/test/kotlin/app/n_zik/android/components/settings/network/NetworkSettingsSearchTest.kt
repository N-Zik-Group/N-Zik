package app.n_zik.android.components.settings.network

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsCards
import app.n_zik.android.components.settings.common.searchSettingsEntries
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.settings.other.proxyModeLabel
import java.net.Proxy
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NetworkSettingsSearchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `proxy connection type is indexed under its displayed label and source fallback`() {
        val czech = contextForLanguage("cs")
        val fallback = contextForLanguage("qaa")
        assertFalse(czech.getString(R.string.network_proxy_mode_direct) == Proxy.Type.DIRECT.name)
        assertEquals(Proxy.Type.DIRECT.name, fallback.getString(R.string.network_proxy_mode_direct))
        for (localized in listOf(czech, fallback)) {
            val label = proxyModeLabel(Proxy.Type.DIRECT, localized::getString)
            assertTrue(searchSettingsEntries(localized, label).any { it.id == "other.proxy.proxy_mode" })
        }
    }

    private fun contextForLanguage(languageTag: String): Context =
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) },
        )

    @Test fun `network search retains localized entry keywords and no result behavior`() {
        listOf(
            R.string.bridge_server_description to R.string.bridge_server,
            R.string.settings_network_data_saver_description to R.string.network_settings,
            R.string.settings_network_offline_mode_description to R.string.settings_network_offline_mode_label,
            R.string.settings_enable_autodownload_song_when_album_bookmarked to R.string.downloads_title,
            R.string.settings_restore_default_settings to R.string.settings_reset,
        ).forEach { (keyword, card) ->
            assertTrue(visibleCardTitleRes(context, 6, context.getString(keyword)).contains(card))
        }
        assertTrue(visibleCardTitleRes(context, 6, "zzzz-no-network-setting-zzzz").isEmpty())
    }

    @Test fun `registry keeps the source cards and download quality split in order and both focus destinations`() {
        val cards = SettingsCards.filter { it.tab == 6 }
        assertEquals(listOf(R.string.settings_network_quality_title, R.string.settings_download_quality, R.string.network_settings, R.string.settings_network_offline_mode_label, R.string.bridge_server, R.string.downloads_title, R.string.settings_reset), cards.map { it.titleRes })
        assertEquals(listOf("offline", "pcserver"), cards.map { it.focusId }.filter { it.isNotBlank() })
    }
}
