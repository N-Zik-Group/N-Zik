package app.n_zik.android.components.settings.network

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SettingsCards
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NetworkSettingsSearchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `network search retains localized entry keywords and no result behavior`() {
        listOf(
            R.string.bridge_server_description to R.string.bridge_server,
            R.string.info_enable_connection_metered to R.string.connection_settings,
            R.string.offline_mode_description to R.string.offline_mode,
            R.string.settings_enable_autodownload_song_when_album_bookmarked to R.string.download,
            R.string.settings_restore_default_settings to R.string.settings_reset,
        ).forEach { (keyword, card) ->
            assertTrue(visibleCardTitleRes(context, 6, context.getString(keyword)).contains(card))
        }
        assertTrue(visibleCardTitleRes(context, 6, "zzzz-no-network-setting-zzzz").isEmpty())
    }

    @Test fun `registry keeps the seven cards in order and both focus destinations`() {
        val cards = SettingsCards.filter { it.tab == 6 }
        assertEquals(listOf(R.string.bridge_server, R.string.network_status, R.string.quality, R.string.connection_settings, R.string.offline_mode, R.string.download, R.string.settings_reset), cards.map { it.titleRes })
        assertEquals(listOf("pcserver", "offline"), cards.map { it.focusId }.filter { it.isNotBlank() })
    }
}
